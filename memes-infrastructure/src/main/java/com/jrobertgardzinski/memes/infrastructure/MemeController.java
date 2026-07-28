package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.ListMemes;
import com.jrobertgardzinski.memes.application.MakeThumbnail;
import com.jrobertgardzinski.memes.application.PublishMeme;
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

    private final PublishMeme publishMeme;
    private final MakeThumbnail makeThumbnail;
    private final ListMemes listMemes;
    private final com.jrobertgardzinski.memes.application.SearchMemesByTag searchMemesByTag;
    private final com.jrobertgardzinski.memes.application.ServeMeme serveMeme;
    private final com.jrobertgardzinski.memes.application.ViewMeme viewMeme;
    private final com.jrobertgardzinski.memes.application.DeleteMeme deleteMeme;
    private final com.jrobertgardzinski.memes.application.FlagMeme flagMeme;
    private final com.jrobertgardzinski.memes.application.ContentFlags contentFlags;
    private final com.jrobertgardzinski.memes.config.RateLimit uploadRate;
    private final UploadAdmission uploadAdmission;

    MemeController(PublishMeme publishMeme, MakeThumbnail makeThumbnail,
                   ListMemes listMemes,
                   com.jrobertgardzinski.memes.application.SearchMemesByTag searchMemesByTag,
                   com.jrobertgardzinski.memes.application.ServeMeme serveMeme,
                   com.jrobertgardzinski.memes.application.ViewMeme viewMeme,
                   com.jrobertgardzinski.memes.application.DeleteMeme deleteMeme,
                   com.jrobertgardzinski.memes.application.FlagMeme flagMeme,
                   com.jrobertgardzinski.memes.application.ContentFlags contentFlags,
                   com.jrobertgardzinski.memes.config.RateLimit uploadRate,
                   UploadAdmission uploadAdmission) {
        this.publishMeme = publishMeme;
        this.makeThumbnail = makeThumbnail;
        this.listMemes = listMemes;
        this.searchMemesByTag = searchMemesByTag;
        this.serveMeme = serveMeme;
        this.viewMeme = viewMeme;
        this.deleteMeme = deleteMeme;
        this.flagMeme = flagMeme;
        this.contentFlags = contentFlags;
        this.uploadRate = uploadRate;
        this.uploadAdmission = uploadAdmission;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                               @RequestAttribute(RequireSignInFilter.AUTHENTICATED_USER)
                                               String uploader) throws IOException {
        if (!uploadRate.tryAcquire(uploader)) {
            return ResponseEntity.status(429).header("Retry-After", "60")
                    .body(Map.of("status", "RATE_LIMITED", "detail", "you are uploading too fast"));
        }
        // getBytes() is where the heap is spent — up to spring.servlet.multipart.max-file-size per
        // request — and until now nothing bounded how many requests could stand here at once. The
        // permit is held across execute() as well, because the bytes stay reachable for its whole
        // duration; releasing before that would bound nothing.
        String id = uploadAdmission.admit(() -> {
            try {
                return publishMeme.execute(file.getBytes(), uploader);
            } catch (IOException unreadableUpload) {
                throw new UncheckedIOException(unreadableUpload);
            }
        });
        return ResponseEntity.created(URI.create("/memes/" + id)).body(Map.of("id", id));
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
        java.util.Set<String> nsfw = contentFlags.nsfwIds();
        if (tag == null || tag.isBlank()) {
            return ResponseEntity.ok(listMemes.execute(offset, limit).stream()
                    .map(id -> Map.of("id", id, "nsfw", nsfw.contains(id))).toList());
        }
        try {
            return ResponseEntity.ok(searchMemesByTag
                    .execute(com.jrobertgardzinski.memes.tags.Tag.of(tag), offset, limit)
                    .stream().map(id -> Map.of("id", id, "nsfw", nsfw.contains(id))).toList());
        } catch (IllegalArgumentException illegalTag) {
            return ResponseEntity.badRequest().body(Map.of("status", "INVALID_TAG"));
        }
    }

    @GetMapping("/{id}")
    ResponseEntity<byte[]> view(@PathVariable("id") String id,
                                @org.springframework.web.bind.annotation.RequestHeader(
                                        name = "Accept", required = false) String accept) {
        boolean wantsWebp = accept != null && accept.contains("image/webp");
        return serveMeme.execute(id, wantsWebp)
                .map(image -> ResponseEntity.ok()
                        .header("Content-Type", image.contentType())
                        .header("Vary", "Accept")
                        .body(image.data()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/thumbnail")
    ResponseEntity<byte[]> thumbnail(@PathVariable("id") String id) {
        return makeThumbnail.execute(id)
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
                        .cacheControl(org.springframework.http.CacheControl
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
                                             @RequestAttribute(name = RequireSignInFilter.AUTHENTICATED_USER,
                                                     required = false) String viewer) {
        return viewMeme.execute(id)
                .map(meme -> ResponseEntity.ok(Map.<String, Object>of(
                        "id", meme.id(),
                        "author", maskAuthor(meme.author()),
                        // the full author never leaves the service, so the UI cannot compare it
                        // against the signed-in user any more — "own" carries that answer instead
                        "own", meme.author().equals(viewer),
                        "nsfw", contentFlags.isNsfw(id))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * The public face of an uploader: first character, then {@code ***}, then the domain — the
     * same representation {@code CommentController.maskAuthor} gives a comment's author, so the
     * two halves of one dialog speak about people the same way. Internally (authorisation, the
     * account purge) the full e-mail still flows; only this representation masks. Non-e-mail
     * authors (the "deleted account" placeholder) pass through untouched.
     */
    private static String maskAuthor(String author) {
        int at = author.indexOf('@');
        if (at <= 0) {
            return author;
        }
        return author.charAt(0) + "***" + author.substring(at);
    }

    /** Flag a meme NSFW (or take the flag back): a MODERATOR-only judgement — authors may label
     *  their uploads editorially, but the gallery's blur trusts only the moderator's word. */
    @org.springframework.web.bind.annotation.PutMapping("/{id}/nsfw")
    ResponseEntity<?> flagNsfw(@PathVariable("id") String id,
                               @org.springframework.web.bind.annotation.RequestBody Map<String, Boolean> body,
                               @RequestAttribute(name = RequireSignInFilter.AUTHENTICATED_ROLES,
                                       required = false) java.util.Set<String> roles) {
        boolean moderator = roles != null && (roles.contains("MODERATOR") || roles.contains("ADMIN"));
        boolean nsfw = Boolean.TRUE.equals(body.get("nsfw"));
        return switch (flagMeme.execute(id, nsfw, moderator)) {
            case FLAGGED -> ResponseEntity.ok(Map.of("id", id, "nsfw", nsfw));
            case NOT_A_MODERATOR -> ResponseEntity.status(403).body(Map.of("status", "NOT_A_MODERATOR"));
            case NO_SUCH_MEME -> ResponseEntity.notFound().build();
        };
    }

    /** Take a meme down: its author may remove their own, a MODERATOR may remove anyone's. */
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    ResponseEntity<?> delete(@PathVariable("id") String id,
                             @RequestAttribute(RequireSignInFilter.AUTHENTICATED_USER) String caller,
                             @RequestAttribute(name = RequireSignInFilter.AUTHENTICATED_ROLES,
                                     required = false) java.util.Set<String> roles) {
        boolean moderator = roles != null && (roles.contains("MODERATOR") || roles.contains("ADMIN"));
        var meme = viewMeme.execute(id);
        if (meme.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!moderator && !meme.get().author().equals(caller)) {
            return ResponseEntity.status(403).body(Map.of("status", "NOT_YOURS",
                    "detail", "only the author or a moderator can delete this meme"));
        }
        deleteMeme.execute(id);
        return ResponseEntity.ok(Map.of("status", "DELETED", "id", id,
                "by", moderator && !meme.get().author().equals(caller) ? "MODERATOR" : "AUTHOR"));
    }
}
