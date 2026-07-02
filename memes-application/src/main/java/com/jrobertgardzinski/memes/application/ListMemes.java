package com.jrobertgardzinski.memes.application;

import java.util.List;

/**
 * Lists the ids of all stored memes, newest first — the public gallery view.
 */
public class ListMemes {

    private final MemeRepository repository;

    public ListMemes(MemeRepository repository) {
        this.repository = repository;
    }

    public List<String> execute() {
        return repository.allIds();
    }
}
