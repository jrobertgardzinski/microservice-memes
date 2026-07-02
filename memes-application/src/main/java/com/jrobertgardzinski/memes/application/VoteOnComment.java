package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.VoteDirection;

import java.util.Optional;

/**
 * Casts a voter's vote on a comment — the same toggle rule as {@link CastVote}. Returns the
 * comment's new tally, or empty when there is no such comment under that meme.
 */
public class VoteOnComment {

    private final CommentRepository commentRepository;
    private final CommentVoteRepository commentVoteRepository;

    public VoteOnComment(CommentRepository commentRepository, CommentVoteRepository commentVoteRepository) {
        this.commentRepository = commentRepository;
        this.commentVoteRepository = commentVoteRepository;
    }

    public Optional<VoteTally> execute(String memeId, String commentId, String voter, VoteDirection direction) {
        return commentRepository.find(commentId)
                .filter(comment -> comment.memeId().equals(memeId))
                .map(comment -> {
                    if (commentVoteRepository.voteOf(commentId, voter).filter(direction::equals).isPresent()) {
                        commentVoteRepository.retractVote(commentId, voter);
                    } else {
                        commentVoteRepository.castVote(commentId, voter, direction);
                    }
                    return new VoteTally(
                            commentVoteRepository.scoreOf(commentId), commentVoteRepository.voteOf(commentId, voter));
                });
    }
}
