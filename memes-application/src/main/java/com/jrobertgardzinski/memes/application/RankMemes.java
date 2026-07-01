package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.RankedMeme;

import java.util.Comparator;
import java.util.List;

/**
 * Ranks voted memes by score, highest first ("hot").
 */
public class RankMemes {

    private final VoteRepository voteRepository;

    public RankMemes(VoteRepository voteRepository) {
        this.voteRepository = voteRepository;
    }

    public List<RankedMeme> execute() {
        return voteRepository.allScores().stream()
                .sorted(Comparator.comparingInt(RankedMeme::score).reversed())
                .toList();
    }
}
