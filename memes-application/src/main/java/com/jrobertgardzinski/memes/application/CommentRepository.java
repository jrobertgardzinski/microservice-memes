package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Comment;

import java.util.List;

/**
 * Port for storing and listing comments, keyed by the meme they belong to.
 */
public interface CommentRepository {

    void save(Comment comment);

    List<Comment> findByMeme(String memeId);
}
