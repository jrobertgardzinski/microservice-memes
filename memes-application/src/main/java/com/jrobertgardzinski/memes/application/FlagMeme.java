package com.jrobertgardzinski.memes.application;

/**
 * Flag (or unflag) a meme as NSFW. Unlike deletion — where the author may act on their own work —
 * the safe-for-work judgement belongs to moderators alone: an author self-labelling is fine
 * editorially but useless as a guarantee, so the gallery only trusts the moderator's flag. The
 * boundary decides who is a moderator (from the roles microservice-security reports).
 */
public class FlagMeme {

    public enum Result { FLAGGED, NOT_A_MODERATOR, NO_SUCH_MEME }

    private final MemeRepository memes;
    private final ContentFlags flags;

    public FlagMeme(MemeRepository memes, ContentFlags flags) {
        this.memes = memes;
        this.flags = flags;
    }

    public Result execute(String memeId, boolean nsfw, boolean callerIsModerator) {
        if (!callerIsModerator) {
            return Result.NOT_A_MODERATOR;
        }
        if (memes.find(memeId).isEmpty()) {
            return Result.NO_SUCH_MEME;
        }
        flags.setNsfw(memeId, nsfw);
        return Result.FLAGGED;
    }
}
