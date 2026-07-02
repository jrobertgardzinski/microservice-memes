package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.VoteDirection;

import java.util.Optional;

/**
 * Port for tallying votes on comments — the same one-vote-per-voter storage as
 * {@link VoteRepository}, kept separate so meme rankings never mix with comment scores.
 */
public interface CommentVoteRepository {

    void castVote(String commentId, String voter, VoteDirection direction);

    void retractVote(String commentId, String voter);

    Optional<VoteDirection> voteOf(String commentId, String voter);

    int scoreOf(String commentId);

    void purgeComment(String commentId);

    void purgeVoter(String voter);
}
