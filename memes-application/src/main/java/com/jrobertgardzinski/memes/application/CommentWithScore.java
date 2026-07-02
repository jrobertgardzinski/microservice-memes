package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Comment;
import com.jrobertgardzinski.memes.domain.VoteDirection;

import java.util.Optional;

/** A comment with its current score and — for a signed-in viewer — that viewer's own vote. */
public record CommentWithScore(Comment comment, int score, Optional<VoteDirection> viewerVote) {
}
