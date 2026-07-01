package com.jrobertgardzinski.memes.domain;

/**
 * A stored meme: an identifier, the (browser-friendly) image format, and the image bytes. Raw
 * uploads are optimised before becoming a Meme, so {@code format} is always something a browser
 * renders (e.g. {@code png}).
 */
public record Meme(String id, String format, byte[] data) {
}
