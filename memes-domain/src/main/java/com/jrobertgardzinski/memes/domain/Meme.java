package com.jrobertgardzinski.memes.domain;

/**
 * A stored meme: an identifier, who uploaded it, the (browser-friendly) image format, and the
 * image bytes. Raw uploads are optimised before becoming a Meme, so {@code format} is always
 * something a browser renders (e.g. {@code png}). The author ties the meme to its uploader's
 * account — deleting that account purges the meme (and its comment thread) with it.
 *
 * <p>The row's ERASURE state is not here but on {@link MemeMetadata}, and holding a Meme is proof
 * it did not apply: a meme whose account deletion is under way is {@link MemeStatus#PENDING_ERASURE}
 * and invisible to every read that could produce this record. See {@link MemeMetadata} for why the
 * status sits on the row rather than on the row-plus-picture.
 */
public record Meme(String id, String author, String format, byte[] data) {
}
