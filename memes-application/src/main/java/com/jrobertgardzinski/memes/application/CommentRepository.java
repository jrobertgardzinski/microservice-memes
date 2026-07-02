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

    /** Replace this author's name on every comment (account deletion keeps texts, drops identity). */
    void anonymizeAuthor(String author, String replacement);
}
