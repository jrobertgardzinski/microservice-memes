package com.jrobertgardzinski.memes.domain;

/**
 * A {@link Meme} without its image: the identifier, who uploaded it, and the format its bytes are
 * stored in. This is what the questions ABOUT a meme need — "is there such a meme?", "who may
 * delete it?", "whose tags are these?" — and none of them needs the picture.
 *
 * <p>The type exists because {@link Meme} made the bytes unavoidable: every authorisation check
 * dragged the full image out of object storage just to read one e-mail address off it, and a meme
 * whose bytes were missing from the active store answered "no such meme" to its own author. A row
 * and its picture are two different facts; this record is the first one alone.
 */
public record MemeMetadata(String id, String author, String format) {
}
