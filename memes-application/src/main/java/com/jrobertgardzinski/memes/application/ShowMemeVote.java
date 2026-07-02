package com.jrobertgardzinski.memes.application;

import java.util.Optional;

/**
 * Shows a meme's vote tally through a viewer's eyes: the score, and — when the viewer is signed
 * in — their own current choice, so the UI can render the pressed arrow. Empty if no such meme.
 */
public class ShowMemeVote {

    private final MemeRepository memeRepository;
    private final VoteRepository voteRepository;

    public ShowMemeVote(MemeRepository memeRepository, VoteRepository voteRepository) {
        this.memeRepository = memeRepository;
        this.voteRepository = voteRepository;
    }

    public Optional<VoteTally> execute(String memeId, Optional<String> viewer) {
        return memeRepository.find(memeId).map(meme -> new VoteTally(
                voteRepository.scoreOf(memeId),
                viewer.flatMap(v -> voteRepository.voteOf(memeId, v))));
    }
}
