package com.jrobertgardzinski.memes.application;

import java.util.List;
import java.util.Optional;

/**
 * Lists the comments on a meme, each with its current score; a signed-in viewer also sees which
 * way they themselves voted on each comment.
 */
public class ListComments {

    private final CommentRepository commentRepository;
    private final CommentVoteRepository commentVoteRepository;

    public ListComments(CommentRepository commentRepository, CommentVoteRepository commentVoteRepository) {
        this.commentRepository = commentRepository;
        this.commentVoteRepository = commentVoteRepository;
    }

    public List<CommentWithScore> execute(String memeId, Optional<String> viewer) {
        return commentRepository.findByMeme(memeId).stream()
                .map(comment -> new CommentWithScore(
                        comment,
                        commentVoteRepository.scoreOf(comment.id()),
                        viewer.flatMap(v -> commentVoteRepository.voteOf(comment.id(), v))))
                .toList();
    }
}
