package com.jrobertgardzinski.memes.config;

/** Server policy on tagging: how many tags one meme may carry (folksonomy, not keyword spam). */
public record TagLimits(int maxPerMeme) {
}
