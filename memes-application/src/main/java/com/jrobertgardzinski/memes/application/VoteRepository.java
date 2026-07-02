package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.RankedMeme;
import com.jrobertgardzinski.memes.domain.VoteDirection;

import java.util.List;
import java.util.Optional;

/**
 * Port for tallying votes on memes. Each voter holds at most one vote per meme; the use case
 * decides whether a cast replaces, retracts or adds. A score is up-voters minus down-voters.
 */
public interface VoteRepository {

    void castVote(String memeId, String voter, VoteDirection direction);

    void retractVote(String memeId, String voter);

    Optional<VoteDirection> voteOf(String memeId, String voter);

    int scoreOf(String memeId);

    /** Every meme that has received a vote, with its current score (unordered). */
    List<RankedMeme> allScores();

    void purgeMeme(String memeId);

    void purgeVoter(String voter);
}
