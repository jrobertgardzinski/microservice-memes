package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Comment;

import java.util.List;

/**
 * Lists the comments on a meme.
 */
public class ListComments {

    private final CommentRepository commentRepository;

    public ListComments(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    public List<Comment> execute(String memeId) {
        return commentRepository.findByMeme(memeId);
    }
}
