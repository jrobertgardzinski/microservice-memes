package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Meme;

import java.util.Optional;

/**
 * Retrieves a stored meme by id for display.
 */
public class ViewMeme {

    private final MemeRepository repository;

    public ViewMeme(MemeRepository repository) {
        this.repository = repository;
    }

    public Optional<Meme> execute(String id) {
        return repository.find(id);
    }
}
