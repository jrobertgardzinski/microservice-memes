package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.RankedMeme;
import com.jrobertgardzinski.voting.Ballots;

import java.util.List;

/**
 * The voting context's {@link Ballots} store applied to memes, extended with what only this
 * service needs: the hot ranking and the account-deletion purges.
 */
public interface VoteRepository extends Ballots {

    /** Every meme that has received a vote, with its current score (unordered). */
    List<RankedMeme> allScores();

    void purgeMeme(String memeId);

    void purgeVoter(String voter);
}
