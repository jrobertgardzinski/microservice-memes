package com.jrobertgardzinski.memes.domain;

import com.jrobertgardzinski.identity.UserId;

import java.util.Optional;

/** {@code authorId} is empty only for a row that predates the id; the address is on its way out. */
public record Meme(String id, String author, Optional<UserId> authorId, String format, byte[] data) {

    public Meme(String id, String author, String format, byte[] data) {
        this(id, author, Optional.empty(), format, data);
    }
}
