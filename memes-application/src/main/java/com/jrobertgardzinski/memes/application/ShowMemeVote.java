package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.voting.VoteTally;
import com.jrobertgardzinski.voting.Voting;

import java.util.Optional;

/**
 * Shows a meme's vote tally through a viewer's eyes (the voting library renders each viewer their
 * own choice, so the UI can press the right arrow). Empty if no such meme.
 */
public class ShowMemeVote {

    private final MemeRepository memeRepository;
    private final Voting voting;

    public ShowMemeVote(MemeRepository memeRepository, VoteRepository voteRepository) {
        this.memeRepository = memeRepository;
        this.voting = new Voting(voteRepository);
    }

    public Optional<VoteTally> execute(String memeId, Optional<String> viewer) {
        return memeRepository.find(memeId).map(meme -> voting.tally(memeId, viewer));
    }
}
