package com.jrobertgardzinski.memes.application.core;

import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.memes.application.UploadGate;
import com.jrobertgardzinski.memes.config.core.RateLimit;
import com.jrobertgardzinski.memes.domain.core.ContentFlags;
import com.jrobertgardzinski.memes.system.core.DeleteMeme;
import com.jrobertgardzinski.memes.system.core.FlagMeme;
import com.jrobertgardzinski.memes.system.core.ListMemes;
import com.jrobertgardzinski.memes.system.core.MakeThumbnail;
import com.jrobertgardzinski.memes.system.core.PublishMeme;
import com.jrobertgardzinski.memes.system.core.ServeMeme;
import com.jrobertgardzinski.memes.system.core.ViewMeme;
import com.jrobertgardzinski.memes.system.tags.SearchMemesByTag;
import com.jrobertgardzinski.memes.tags.Tag;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A meme from the outside: publishing one, listing and showing them, flagging and deleting one.
 * Who the caller is arrives already proven; what they may do with it is decided here.
 */
public final class MemeService {

    private final PublishMeme publishMeme;
    private final ListMemes listMemes;
    private final SearchMemesByTag searchMemesByTag;
    private final ServeMeme serveMeme;
    private final MakeThumbnail makeThumbnail;
    private final ViewMeme viewMeme;
    private final FlagMeme flagMeme;
    private final DeleteMeme deleteMeme;
    private final ContentFlags contentFlags;
    private final RateLimit uploadRate;
    private final UploadGate uploadGate;

    public MemeService(PublishMeme publishMeme, ListMemes listMemes, SearchMemesByTag searchMemesByTag,
                       ServeMeme serveMeme, MakeThumbnail makeThumbnail, ViewMeme viewMeme, FlagMeme flagMeme,
                       DeleteMeme deleteMeme, ContentFlags contentFlags, RateLimit uploadRate, UploadGate uploadGate) {
        this.publishMeme = publishMeme;
        this.listMemes = listMemes;
        this.searchMemesByTag = searchMemesByTag;
        this.serveMeme = serveMeme;
        this.makeThumbnail = makeThumbnail;
        this.viewMeme = viewMeme;
        this.flagMeme = flagMeme;
        this.deleteMeme = deleteMeme;
        this.contentFlags = contentFlags;
        this.uploadRate = uploadRate;
        this.uploadGate = uploadGate;
    }

    /**
     * Publishes the uploaded bytes. The rate is asked first and costs nothing; only an upload that
     * passes it waits at the gate, where its bytes are read.
     */
    public Upload publish(UserId uploader, Supplier<byte[]> bytes) {
        if (!uploadRate.tryAcquire(uploader.toString())) {
            return new Upload.RateLimited();
        }
        return new Upload.Published(uploadGate.admit(() -> publishMeme.execute(bytes.get(), uploader)));
    }

    /** One page of the gallery, narrowed to a tag when one is given; each id says whether it is NSFW. */
    public Listing list(String tag, long offset, int limit) {
        Set<String> nsfw = contentFlags.nsfwIds();
        List<String> ids;
        if (tag == null || tag.isBlank()) {
            ids = listMemes.execute(offset, limit);
        } else {
            Tag asked;
            try {
                asked = Tag.of(tag);
            } catch (IllegalArgumentException illegalTag) {
                return new Listing.InvalidTag();
            }
            ids = searchMemesByTag.execute(asked, offset, limit);
        }
        return new Listing.Page(ids.stream().map(id -> new Listed(id, nsfw.contains(id))).toList());
    }

    public Optional<ServeMeme.Image> serve(String id, boolean wantsWebp) {
        return serveMeme.execute(id, wantsWebp);
    }

    public Optional<byte[]> thumbnail(String id) {
        return makeThumbnail.execute(id);
    }

    /** What a page shows about a meme; {@code viewer} is null for somebody not signed in. */
    public Optional<MemeView> view(String id, UserId viewer) {
        return viewMeme.execute(id).map(meme -> new MemeView(meme.id(), meme.authorId(),
                viewer != null && meme.isOwnedBy(viewer), contentFlags.isNsfw(id)));
    }

    /** Marks a meme NSFW or clears the mark — a moderator's call. */
    public Flagging flag(String id, Boolean nsfw, Set<String> roles) {
        // NOT STATED is not the same as false. A missing key, an explicit null or a misspelled one
        // used to fold into the unflag command, whose adapter runs an unconditional DELETE — so a
        // flag-ON that lost its field in transit was answered {"nsfw": false}, the blur came off
        // the tile, and the moderator read the answer as "marked".
        if (nsfw == null) {
            return new Flagging.MissingFlag();
        }
        return switch (flagMeme.execute(id, nsfw, isModerator(roles))) {
            case FLAGGED -> new Flagging.Flagged(nsfw);
            case NOT_A_MODERATOR -> new Flagging.NotAModerator();
            case NO_SUCH_MEME -> new Flagging.NoSuchMeme();
        };
    }

    /** Deletes a meme — its author's or a moderator's call, and the answer says whose it was. */
    public Deletion delete(String id, UserId caller, Set<String> roles) {
        boolean moderator = isModerator(roles);
        var meme = viewMeme.execute(id);
        if (meme.isEmpty()) {
            return new Deletion.NoSuchMeme();
        }
        boolean own = meme.get().isOwnedBy(caller);
        if (!moderator && !own) {
            return new Deletion.NotYours();
        }
        deleteMeme.execute(id);
        return new Deletion.Deleted(moderator && !own);
    }

    private static boolean isModerator(Set<String> roles) {
        return roles != null && (roles.contains("MODERATOR") || roles.contains("ADMIN"));
    }

    public record Listed(String id, boolean nsfw) {}

    public record MemeView(String id, Optional<UserId> authorId, boolean own, boolean nsfw) {}

    public sealed interface Upload {
        record Published(String id) implements Upload {}

        record RateLimited() implements Upload {}
    }

    public sealed interface Listing {
        record Page(List<Listed> memes) implements Listing {}

        record InvalidTag() implements Listing {}
    }

    public sealed interface Flagging {
        record Flagged(boolean nsfw) implements Flagging {}

        record MissingFlag() implements Flagging {}

        record NotAModerator() implements Flagging {}

        record NoSuchMeme() implements Flagging {}
    }

    public sealed interface Deletion {
        /** {@code byModerator}: a moderator took down somebody else's meme. */
        record Deleted(boolean byModerator) implements Deletion {}

        record NotYours() implements Deletion {}

        record NoSuchMeme() implements Deletion {}
    }
}
