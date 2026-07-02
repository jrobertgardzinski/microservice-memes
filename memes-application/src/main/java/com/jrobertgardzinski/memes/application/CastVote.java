package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.VoteDirection;

import java.util.Optional;

/**
 * Casts a voter's vote on a meme — one vote per voter, re-voting replaces the previous one.
 * Returns the meme's new score, or empty if there is no such meme.
 */
public class CastVote {

    private final MemeRepository memeRepository;
    private final VoteRepository voteRepository;

    public CastVote(MemeRepository memeRepository, VoteRepository voteRepository) {
        this.memeRepository = memeRepository;
        this.voteRepository = voteRepository;
    }

    public Optional<Integer> execute(String memeId, String voter, VoteDirection direction) {
        if (memeRepository.find(memeId).isEmpty()) {
            return Optional.empty();
        }
        voteRepository.castVote(memeId, voter, direction);
        return Optional.of(voteRepository.scoreOf(memeId));
    }
}
