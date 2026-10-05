package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.core.MemeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Web boundary: upload a meme image (any format ImageIO reads; signed-in users only, enforced by
 * {@link RequireSignInFilter}), browse the gallery and serve memes back optimised for the browser
 * (PNG) — reads are public.
 */
@RestController
@RequestMapping("/memes")
class MemeController {

    /**
     * Server policy: one page of the wall is at most this many memes — the same ceiling the
     * comment thread uses. The gallery is public and unauthenticated, so "give me everything"
     * has to be a request the server declines: one viral week is all it takes for the listing
     * to become the most expensive anonymous call in the product.
     */
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 50;

    private final MemeService memes;
    private final com.jrobertgardzinski.authors.AuthorDirectory authors;

    MemeController(MemeService memes, com.jrobertgardzinski.authors.AuthorDirectory authors) {
        this.memes = memes;
        this.authors = authors;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                               @RequestAttribute(RequireSignInFilter.AUTHENTICATED_USER_ID)
                                               com.jrobertgardzinski.identity.UserId uploaderId)
            throws IOException {
        // getBytes() is where the heap is spent — up to spring.servlet.multipart.max-file-size per
        // request — so it is read only behind the service's upload gate, which holds its permit
        // across the publish as well: the bytes stay reachable for its whole duration.
        var outcome = memes.publish(uploaderId, () -> {
            try {
                return file.getBytes();
            } catch (IOException unreadableUpload) {
                throw new UncheckedIOException(unreadableUpload);
            }
        });
        return switch (outcome) {
            case MemeService.Upload.RateLimited limited -> ResponseEntity.status(429).header("Retry-After", "60")
                    .body(Map.of("status", "RATE_LIMITED", "detail", "you are uploading too fast"));
            case MemeService.Upload.Published published -> ResponseEntity
                    .created(URI.create("/memes/" + published.id())).body(Map.of("id", published.id()));
        };
    }

    @GetMapping
    ResponseEntity<?> all(@RequestParam(name = "tag", required = false) String tag,
                          @RequestParam(name = "page", defaultValue = "0") int page,
                          @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        int limit = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        // long arithmetic on purpose: page * limit in ints overflows for an absurd page number,
        // and a NEGATIVE offset reaches the database as a broken statement (a bare 500) instead
        // of the empty page an out-of-range page honestly is
        long offset = (long) Math.max(0, page) * limit;
        return switch (memes.list(tag, offset, limit)) {
            case MemeService.Listing.Page listed -> ResponseEntity.ok(listed.memes().stream()
                    .map(meme -> Map.of("id", meme.id(), "nsfw", meme.nsfw())).toList());
            case MemeService.Listing.InvalidTag invalid ->
                    ResponseEntity.badRequest().body(Map.of("status", "INVALID_TAG"));
        };
    }

    @GetMapping("/{id}")
    ResponseEntity<byte[]> view(@PathVariable("id") String id,
                                @org.springframework.web.bind.annotation.RequestHeader(
                                        name = "Accept", required = false) String accept) {
        boolean wantsWebp = accept != null && accept.contains("image/webp");
        return memes.serve(id, wantsWebp)
                .map(image -> ResponseEntity.ok()
                        .header("Content-Type", image.contentType())
                        .header("Vary", "Accept")
                        // the SAME policy as the thumbnail below, and stated for the same reason:
                        // a meme is immutable per id, and saying nothing here does not mean "do
                        // not cache" — it means "whatever heuristic this browser or proxy picks",
                        // which is an unwritten stale window on the LARGER payload under the same
                        // erasure duty. One hour, so the two halves of one picture decay together;
                        // the reasoning, and what to change should a purge ever need to be
                        // harder, is written out once at #thumbnail.
                        // the SAME policy as the thumbnail below, and stated for the same reason:
                        // a meme is immutable per id, and saying nothing here does not mean "do
                        // not cache" — it means "whatever heuristic this browser or proxy picks",
                        // which is an unwritten stale window on the LARGER payload under the same
                        // erasure duty. One hour, so the two halves of one picture decay together;
                        // the reasoning, and what to change should a purge ever need to be
                        // harder, is written out once at #thumbnail.
                        .cacheControl(org.springframework.http.CacheControl
                                .maxAge(java.time.Duration.ofHours(1)).cachePublic())
                        .body(image.data()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** The query the favourites wall marks its thumbnail requests with — see {@link #thumbnail}. */
    static final String FAVOURITES_WALL = "favourites";

    /**
     * A meme's thumbnail. Cached for an hour for the gallery, and NOT cached at all for the
     * favourites wall — that difference is the only reason the {@code wall} parameter exists.
     *
     * <p>The wall renders a deleted meme as a KEEPSAKE: the tile 404s, the img's {@code onError}
     * fires, and the user gets a tile they can still unfavourite. That read-repair is what covers
     * the window between a meme's deletion and the MEME_DELETED sweep reaching user-collections, and
     * it only happens if the browser actually ASKS.
     *
     * <p>The UI already sent a distinct URL for it, with a comment promising "a distinct URL forces
     * a real answer" — while every thumbnail response, query included, was stamped
     * {@code public, max-age=3600}. A distinct URL buys a distinct CACHE ENTRY and nothing more:
     * open the wall at 12:00, delete the meme at 12:05, come back at 12:10, and the tile is painted
     * from cache without a single request, so onError never fires and clicking it opens a dialog
     * whose every fetch 404s. The hour of caching that makes a scrolling gallery cheap is exactly
     * wrong for a wall whose job is to notice that something is gone.
     *
     * @param wall which surface is asking — absent for the gallery, {@code favourites} for the wall
     */
    @GetMapping("/{id}/thumbnail")
    ResponseEntity<byte[]> thumbnail(@PathVariable("id") String id,
                                     @RequestParam(name = "wall", required = false) String wall) {
        boolean mustAsk = FAVOURITES_WALL.equals(wall);
        return memes.thumbnail(id)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        // a thumbnail is immutable per id (ids never return to circulation), so
                        // browsers and proxies may hold it a long while — an hour keeps even a
                        // sweeping delete's stale window bounded.
                        //
                        // RODO/GDPR, stated plainly because this is where the policy lives:
                        // "public, max-age=3600" means a delete or an account purge does NOT
                        // reach copies already handed out — the thumbnail of an erased meme can
                        // survive in browser caches and in any intermediary (CDN, corporate
                        // proxy) for up to an hour after the server forgot it. That is a
                        // deliberate trade: the gallery scrolls over thumbnails, and an hour of
                        // caching is what keeps it cheap. The erasure obligation is met at the
                        // source (row, blob and both variants go in the delete's transaction);
                        // what remains is a bounded, decaying tail. Should a purge ever need to
                        // be harder than that, this is the line to change — "private" keeps
                        // shared caches out of it, and no-store makes erasure immediate at the
                        // cost of a decode-free but round-trip-per-tile gallery.
                        //
                        // The OTHER half of the same obligation, and the sharper one: an ORPHAN
                        // AT REST. MakeThumbnail's self-healing is triggered BY A REQUEST — it
                        // refuses to serve a .thumb whose meme is gone and sweeps it on the way
                        // out. A thumbnail nobody ever asks for again is therefore never swept:
                        // it sits in MinIO indefinitely. The code guarantees that an erased
                        // meme's thumbnail will not be HANDED OUT; it does not guarantee that it
                        // has been DELETED, and under GDPR those are two different duties (the
                        // delete's own transaction covers the normal path — this is only about
                        // the variant a CRASH stranded between the cache write and its re-check).
                        // Accepted knowingly: closing it means a periodic sweep that ENUMERATES
                        // the bucket to find keys with no row, which is a listing of the whole
                        // store against a window measured in "how often does a JVM die at that
                        // exact instant". Revisit if that stops being hypothetical — a crash
                        // that leaves orphans behind, an audit that asks for proof of erasure,
                        // or a store big enough that stray objects cost real money. todo.md
                        // carries the same entry.
                        //
                        // …and none of that applies to the favourites wall, which is asking in
                        // order to find out whether the meme is still there. no-store, so the
                        // question is actually put to the server. It costs one request per tile on
                        // one small, per-user surface; the gallery below keeps its hour.
                        .cacheControl(mustAsk
                                ? org.springframework.http.CacheControl.noStore()
                                : org.springframework.http.CacheControl
                                        .maxAge(java.time.Duration.ofHours(1)).cachePublic())
                        .body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * A meme's public metadata — the gallery uses it to offer the uploader the delete control on
     * their own memes. The bytes are served separately.
     *
     * <p>This endpoint is PUBLIC (reads need no token), so it answers the question the UI actually
     * asks ("is this mine?") with a boolean computed from the caller's token, and shows the
     * uploader only masked. It used to return the raw e-mail: two anonymous GETs — the gallery
     * listing, then one {@code /meta} per id — were a complete address dump of everyone who ever
     * uploaded. A signed-out caller simply gets {@code own: false}.
     */
    @GetMapping("/{id}/meta")
    ResponseEntity<Map<String, Object>> meta(@PathVariable("id") String id,
                                             @RequestAttribute(name = RequireSignInFilter.AUTHENTICATED_USER_ID,
                                                     required = false)
                                             com.jrobertgardzinski.identity.UserId viewerId) {
        return memes.view(id, viewerId)
                .map(meme -> ResponseEntity.ok(Map.<String, Object>of(
                        "id", meme.id(),
                        "author", nameOf(meme.authorId()),
                        // the full author never leaves the service, so the UI cannot compare it
                        // against the signed-in user any more — "own" carries that answer instead
                        "own", meme.own(),
                        "nsfw", meme.nsfw())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** The name security shows for the author's id; a row without one, or with one security no longer knows, is a deleted account. */
    private String nameOf(Optional<com.jrobertgardzinski.identity.UserId> author) {
        return author
                .map(id -> authors.namesOf(java.util.List.of(id)).getOrDefault(id,
                        new com.jrobertgardzinski.authors.AuthorName(DELETED_ACCOUNT)).display())
                .orElse(DELETED_ACCOUNT);
    }

    private static final String DELETED_ACCOUNT = "deleted account";


    /** Flag a meme NSFW (or take the flag back): a MODERATOR-only judgement — authors may label
     *  their uploads editorially, but the gallery's blur trusts only the moderator's word. */
    @org.springframework.web.bind.annotation.PutMapping("/{id}/nsfw")
    ResponseEntity<?> flagNsfw(@PathVariable("id") String id,
                               @org.springframework.web.bind.annotation.RequestBody Map<String, Boolean> body,
                               @RequestAttribute(name = RequireSignInFilter.AUTHENTICATED_ROLES,
                                       required = false) java.util.Set<String> roles) {
        return switch (memes.flag(id, body.get("nsfw"), roles)) {
            case MemeService.Flagging.Flagged flagged -> ResponseEntity.ok(Map.of("id", id, "nsfw", flagged.nsfw()));
            case MemeService.Flagging.MissingFlag missing -> ResponseEntity.badRequest().body(Map.of("status", "MISSING_FLAG",
                    "detail", "expected {\"nsfw\": true|false}"));
            case MemeService.Flagging.NotAModerator notAModerator ->
                    ResponseEntity.status(403).body(Map.of("status", "NOT_A_MODERATOR"));
            case MemeService.Flagging.NoSuchMeme none -> ResponseEntity.notFound().build();
        };
    }

    /** Take a meme down: its author may remove their own, a MODERATOR may remove anyone's. */
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    ResponseEntity<?> delete(@PathVariable("id") String id,
                             @RequestAttribute(RequireSignInFilter.AUTHENTICATED_USER_ID)
                             com.jrobertgardzinski.identity.UserId callerId,
                             @RequestAttribute(name = RequireSignInFilter.AUTHENTICATED_ROLES,
                                     required = false) java.util.Set<String> roles) {
        return switch (memes.delete(id, callerId, roles)) {
            case MemeService.Deletion.Deleted deleted -> ResponseEntity.ok(Map.of("status", "DELETED", "id", id,
                    "by", deleted.byModerator() ? "MODERATOR" : "AUTHOR"));
            case MemeService.Deletion.NotYours notYours -> ResponseEntity.status(403).body(Map.of("status", "NOT_YOURS",
                    "detail", "only the author or a moderator can delete this meme"));
            case MemeService.Deletion.NoSuchMeme none -> ResponseEntity.notFound().build();
        };
    }
}
