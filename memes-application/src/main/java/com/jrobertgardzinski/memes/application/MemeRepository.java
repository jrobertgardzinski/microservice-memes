package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Meme;

import java.util.Collection;
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

    /**
     * Which of these ids this service still has a meme for — {@link #exists} asked about a whole
     * set, in one read. The answer is the caller's licence to speak about those memes at all: an id
     * that does not come back is one nothing is known about, and {@link ShowMemeScores} turns that
     * silence into "no tally" instead of a zero. Order and duplicates are not promised.
     *
     * <p>Adapters override it with a single lookup; this fallback keeps hand-rolled fakes working
     * (same bargain as {@link #exists}).
     */
    default List<String> existingOf(Collection<String> ids) {
        return ids.stream().distinct().filter(this::exists).toList();
    }

    /** Ids of every stored meme, newest first. */
    List<String> allIds();

    /**
     * One page of {@link #allIds()}, newest first — the read the public gallery actually needs.
     * Adapters override it with LIMIT/OFFSET so an unbounded listing never leaves the database;
     * this in-memory fallback keeps hand-rolled fakes working (same bargain as {@link #exists}).
     */
    default List<String> allIds(long offset, int limit) {
        return allIds().stream().skip(Math.max(0, offset)).limit(Math.max(0, limit)).toList();
    }

    List<String> findIdsByAuthor(String author);

    void deleteById(String memeId);

    /** Replace one meme's author (account deletion may keep the meme, never the identity). */
    void reassignAuthor(String memeId, String newAuthor);
}
