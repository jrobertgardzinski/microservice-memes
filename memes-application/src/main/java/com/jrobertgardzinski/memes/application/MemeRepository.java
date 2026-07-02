package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Meme;

import java.util.List;
import java.util.Optional;

/**
 * Port for storing and retrieving memes. Implemented by the infrastructure (in-memory now, a real
 * store later).
 */
public interface MemeRepository {

    void save(Meme meme);

    Optional<Meme> find(String id);

    /** Ids of every stored meme, newest first. */
    List<String> allIds();
}
