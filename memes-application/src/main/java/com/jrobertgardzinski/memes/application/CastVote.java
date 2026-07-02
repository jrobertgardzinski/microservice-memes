package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.voting.VoteDirection;
import com.jrobertgardzinski.voting.VoteTally;
import com.jrobertgardzinski.voting.Voting;

import java.util.Optional;

/**
 * Casts a voter's vote on a meme — the toggle semantics come from the voting library; this use
 * case only anchors them to an existing meme. Returns the meme's new tally, or empty if there is
 * no such meme.
 */
public class CastVote {

    private final MemeRepository memeRepository;
    private final Voting voting;

    public CastVote(MemeRepository memeRepository, VoteRepository voteRepository) {
        this.memeRepository = memeRepository;
        this.voting = new Voting(voteRepository);
    }

    public Optional<VoteTally> execute(String memeId, String voter, VoteDirection direction) {
        if (memeRepository.find(memeId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(voting.toggle(memeId, voter, direction));
    }
}
