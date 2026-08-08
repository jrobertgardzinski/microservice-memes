package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Meme;
import com.jrobertgardzinski.memes.domain.MemeMetadata;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Port for storing and retrieving memes. Implemented by the infrastructure (in-memory now, a real
 * store later).
 *
 * <p><strong>Every read here is a read of the GALLERY.</strong> A meme a running account-deletion
 * saga has marked ({@link com.jrobertgardzinski.memes.domain.MemeStatus#PENDING_ERASURE}) is
 * invisible through this port — not "usually", not "in the queries that remembered to say so": the
 * adapter reads from the {@code active_memes} view, which is where the filter is written down once.
 * A caller therefore cannot leak a leaver's content by forgetting a condition, because there is no
 * condition to forget. The one port that CAN see marked memes is {@link MemeErasure}, and its three
 * callers are the saga's own steps.
 */
public interface MemeRepository {

    void save(Meme meme);

    /**
     * The meme WITH its bytes — empty when either half is missing. Ask for this only when the
     * answer is the picture itself ({@link ServeMeme}, {@link MakeThumbnail}); every other question
     * belongs to {@link #exists} or {@link #findMetadata}, which cost no blob read and — the part
     * that bit us — do not turn a meme whose object went missing into a meme that does not exist.
     */
    Optional<Meme> find(String id);

    /**
     * Whether a meme with this id is currently stored — a light check that loads no image bytes
     * (adapters override it with a row lookup; this fallback keeps hand-rolled fakes working).
     */
    default boolean exists(String id) {
        return find(id).isPresent();
    }

    /**
     * A meme's row without its picture: {@link #exists} for callers that also need to know WHO
     * uploaded it (deletion and tagging are the author's privileges) or in what format it is kept.
     *
     * <p>Two reasons this is not {@code find(id).map(...)}. It loads no bytes, so an authorisation
     * check stops paying a full image transfer from object storage for one e-mail address. And it
     * answers about the ROW: {@code find} joins the row to its object, so a meme whose bytes are
     * absent from the ACTIVE store (a store switch left them behind, an operator removed the
     * object) used to look deleted to /meta and to DELETE, and its owner could not take it down —
     * the row was there, the API said 404, and no path led to the leftover bytes.
     *
     * <p>Adapters override it with a row-only lookup — they MUST, or the second guarantee is void;
     * this fallback exists to keep hand-rolled fakes compiling (same bargain as {@link #exists}).
     */
    default Optional<MemeMetadata> findMetadata(String id) {
        return find(id).map(meme -> new MemeMetadata(meme.id(), meme.author(), meme.format()));
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

    void deleteById(String memeId);

    /** Replace one meme's author (account deletion may keep the meme, never the identity). */
    void reassignAuthor(String memeId, String newAuthor);
}
