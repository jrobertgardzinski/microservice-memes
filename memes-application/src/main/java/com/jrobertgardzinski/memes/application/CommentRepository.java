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

    void deleteByAuthor(String author);

    /** Replace this author's name on every comment (account deletion may keep texts, not identities). */
    void anonymizeAuthor(String author, String replacement);
}
