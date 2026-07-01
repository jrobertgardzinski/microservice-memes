package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.VoteDirection;

import java.util.Optional;

/**
 * Casts a vote on a meme. Returns the meme's new score, or empty if there is no such meme.
 */
public class CastVote {

    private final MemeRepository memeRepository;
    private final VoteRepository voteRepository;

    public CastVote(MemeRepository memeRepository, VoteRepository voteRepository) {
        this.memeRepository = memeRepository;
        this.voteRepository = voteRepository;
    }

    public Optional<Integer> execute(String memeId, VoteDirection direction) {
        if (memeRepository.find(memeId).isEmpty()) {
            return Optional.empty();
        }
        voteRepository.castVote(memeId, direction);
        return Optional.of(voteRepository.scoreOf(memeId));
    }
}
