package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Comment;

/** A comment together with its current vote score — what the listing shows. */
public record CommentWithScore(Comment comment, int score) {
}
