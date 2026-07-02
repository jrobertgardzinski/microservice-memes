package com.jrobertgardzinski.memes.application;

import java.util.List;

/**
 * Lists the comments on a meme, each with its current vote score.
 */
public class ListComments {

    private final CommentRepository commentRepository;
    private final CommentVoteRepository commentVoteRepository;

    public ListComments(CommentRepository commentRepository, CommentVoteRepository commentVoteRepository) {
        this.commentRepository = commentRepository;
        this.commentVoteRepository = commentVoteRepository;
    }

    public List<CommentWithScore> execute(String memeId) {
        return commentRepository.findByMeme(memeId).stream()
                .map(comment -> new CommentWithScore(comment, commentVoteRepository.scoreOf(comment.id())))
                .toList();
    }
}
