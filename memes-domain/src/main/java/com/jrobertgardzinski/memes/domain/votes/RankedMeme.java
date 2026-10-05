package com.jrobertgardzinski.memes.domain.votes;

/** A meme with its current vote score, for ranking ("hot"). */
public record RankedMeme(String memeId, int score) {
}
