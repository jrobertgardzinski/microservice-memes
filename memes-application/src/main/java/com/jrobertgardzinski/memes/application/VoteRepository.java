package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.RankedMeme;
import com.jrobertgardzinski.memes.domain.VoteDirection;

import java.util.List;

/**
 * Port for tallying votes on memes. A score is up-votes minus down-votes.
 */
public interface VoteRepository {

    void castVote(String memeId, VoteDirection direction);

    int scoreOf(String memeId);

    /** Every meme that has received a vote, with its current score (unordered). */
    List<RankedMeme> allScores();
}
