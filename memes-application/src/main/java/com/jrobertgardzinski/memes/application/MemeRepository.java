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

    /**
     * Whether a meme with this id is currently stored — a light check that loads no image bytes
     * (adapters override it with a row lookup; this fallback keeps hand-rolled fakes working).
     */
    default boolean exists(String id) {
        return find(id).isPresent();
    }

    /** Ids of every stored meme, newest first. */
    List<String> allIds();

    List<String> findIdsByAuthor(String author);

    void deleteById(String memeId);

    /** Replace one meme's author (account deletion may keep the meme, never the identity). */
    void reassignAuthor(String memeId, String newAuthor);
}
