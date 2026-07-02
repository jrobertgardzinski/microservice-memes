package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.VoteDirection;

import java.util.Optional;

/**
 * Casts a voter's vote on a meme, Reddit-style toggle: a first vote counts, repeating the same
 * direction retracts it, the opposite direction switches it. Returns the meme's new tally
 * (score + the voter's resulting choice), or empty if there is no such meme.
 */
public class CastVote {

    private final MemeRepository memeRepository;
    private final VoteRepository voteRepository;

    public CastVote(MemeRepository memeRepository, VoteRepository voteRepository) {
        this.memeRepository = memeRepository;
        this.voteRepository = voteRepository;
    }

    public Optional<VoteTally> execute(String memeId, String voter, VoteDirection direction) {
        if (memeRepository.find(memeId).isEmpty()) {
            return Optional.empty();
        }
        if (voteRepository.voteOf(memeId, voter).filter(direction::equals).isPresent()) {
            voteRepository.retractVote(memeId, voter);
        } else {
            voteRepository.castVote(memeId, voter, direction);
        }
        return Optional.of(new VoteTally(voteRepository.scoreOf(memeId), voteRepository.voteOf(memeId, voter)));
    }
}
