package com.jrobertgardzinski.memes.domain;

/**
 * A comment posted on a meme: its id, the meme it belongs to, the author and the text. Author and
 * text must not be blank.
 */
public record Comment(String id, String memeId, String author, String text) {

    public Comment {
        if (author == null || author.isBlank()) {
            throw new IllegalArgumentException("author must not be blank");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
    }
}
