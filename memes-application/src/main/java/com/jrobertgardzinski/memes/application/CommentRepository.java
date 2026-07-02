package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Comment;

import java.util.List;
import java.util.Optional;

/**
 * Port for storing and listing comments, keyed by the meme they belong to.
 */
public interface CommentRepository {

    void save(Comment comment);

    List<Comment> findByMeme(String memeId);

    Optional<Comment> find(String commentId);

    void deleteByMeme(String memeId);

    List<Comment> findByAuthor(String author);

    void delete(String commentId);

    /** Replace one comment's author (account deletion may keep the text, never the identity). */
    void reassignAuthor(String commentId, String newAuthor);
}
