package com.jrobertgardzinski.memes.application;

import java.util.Optional;

/**
 * Port for content-based deduplication: maps the bytes of a stored meme to its id, so uploading the
 * same image twice reuses the existing meme instead of storing a copy.
 */
public interface MemeContentIndex {

    Optional<String> findIdByContent(byte[] data);

    void index(byte[] data, String memeId);
}
