package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Comment;

import java.util.Optional;
import java.util.UUID;

/**
 * Adds a comment to a meme. Returns the stored comment, or empty if there is no such meme to comment
 * on.
 */
public class AddComment {

    private final MemeRepository memeRepository;
    private final CommentRepository commentRepository;

    public AddComment(MemeRepository memeRepository, CommentRepository commentRepository) {
        this.memeRepository = memeRepository;
        this.commentRepository = commentRepository;
    }

    public Optional<Comment> execute(String memeId, String author, String text) {
        if (memeRepository.find(memeId).isEmpty()) {
            return Optional.empty();
        }
        Comment comment = new Comment(UUID.randomUUID().toString(), memeId, author, text);
        commentRepository.save(comment);
        return Optional.of(comment);
    }
}
