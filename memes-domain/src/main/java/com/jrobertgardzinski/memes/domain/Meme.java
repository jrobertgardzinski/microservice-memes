package com.jrobertgardzinski.memes.domain;

/**
 * A stored meme: an identifier, who uploaded it, the (browser-friendly) image format, and the
 * image bytes. Raw uploads are optimised before becoming a Meme, so {@code format} is always
 * something a browser renders (e.g. {@code png}). The author ties the meme to its uploader's
 * account — deleting that account purges the meme (and its comment thread) with it.
 */
public record Meme(String id, String author, String format, byte[] data) {
}
