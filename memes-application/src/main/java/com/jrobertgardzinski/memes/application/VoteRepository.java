package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.RankedMeme;
import com.jrobertgardzinski.memes.domain.VoteDirection;

import java.util.List;

/**
 * Port for tallying votes on memes. One vote per voter per meme: casting again replaces the
 * voter's previous vote (voting UP twice is still one up-vote; switching direction changes their
 * mind). A score is up-voters minus down-voters.
 */
public interface VoteRepository {

    void castVote(String memeId, String voter, VoteDirection direction);

    int scoreOf(String memeId);

    /** Every meme that has received a vote, with its current score (unordered). */
    List<RankedMeme> allScores();
}
