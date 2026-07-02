package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.VoteDirection;

import java.util.Optional;

/** A target's current score together with the acting voter's own choice (empty = not voted). */
public record VoteTally(int score, Optional<VoteDirection> voterChoice) {
}
