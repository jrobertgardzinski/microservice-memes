package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Meme;

import java.util.Optional;

/**
 * Take one meme down completely: its votes, its content-index claim, its tags, the row itself, and
 * an announcement so the comment service drops the thread. WHO may do this (the author, or a
 * moderator acting on someone else's) is the boundary's call; this use case is the teardown and
 * whether there was anything to tear down.
 */
public class DeleteMeme {

    public enum Status { DELETED, NO_SUCH_MEME }

    public record Result(Status status, String author) {}

    private final MemeRepository memes;
    private final VoteRepository votes;
    private final MemeContentIndex contentIndex;
    private final TagRepository tags;
    private final MemeEvents memeEvents;

    public DeleteMeme(MemeRepository memes, VoteRepository votes, MemeContentIndex contentIndex,
                      TagRepository tags, MemeEvents memeEvents) {
        this.memes = memes;
        this.votes = votes;
        this.contentIndex = contentIndex;
        this.tags = tags;
        this.memeEvents = memeEvents;
    }

    public Result execute(String memeId) {
        Optional<Meme> meme = memes.find(memeId);
        if (meme.isEmpty()) {
            return new Result(Status.NO_SUCH_MEME, null);
        }
        votes.purgeMeme(memeId);
        contentIndex.remove(memeId);
        tags.removeMeme(memeId);
        memes.deleteById(memeId);
        memeEvents.memeDeleted(memeId);
        return new Result(Status.DELETED, meme.get().author());
    }
}
