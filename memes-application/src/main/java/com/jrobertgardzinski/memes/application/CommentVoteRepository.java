package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.VoteDirection;

/**
 * Port for tallying votes on comments — the same one-vote-per-voter rule as
 * {@link VoteRepository}, kept as a separate store so meme rankings never mix with comment scores.
 */
public interface CommentVoteRepository {

    void castVote(String commentId, String voter, VoteDirection direction);

    int scoreOf(String commentId);
}
