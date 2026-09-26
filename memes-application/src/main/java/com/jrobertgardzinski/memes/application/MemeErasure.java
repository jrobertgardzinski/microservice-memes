package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.MemeMetadata;

import com.jrobertgardzinski.identity.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The erasure-aware side of meme storage: the only port in this service that can see a meme which
 * is not {@link com.jrobertgardzinski.memes.domain.MemeStatus#ACTIVE}.
 *
 * <p><strong>Why this is a second port and not four more methods on {@link MemeRepository}.</strong>
 * {@link MemeRepository} is the gallery's world, and its promise is absolute: nothing it returns is
 * pending erasure, because its adapter reads from the {@code active_memes} view and never from the
 * table. A port that could answer both questions would make that promise a matter of which method
 * you happened to call — and would leave a build-time guard nothing to check. The split is the
 * reason {@code MemeReadFilterTest} can be one short rule instead of a code review.
 *
 * <p>Everything here is used by exactly three callers, all of them steps of the account-deletion
 * saga: the mark, its compensation, and the erasure the orchestrator's closure command triggers.
 */
public interface MemeErasure {

    /** The leaver's memes that are still in the gallery — what a mark has left to do. */
    List<MemeMetadata> activeOf(String author);

    /**
     * The leaver's memes a running saga has already reserved — what a compensation restores and
     * what the erasure destroys. Both act on THIS set rather than on "everything by the author",
     * so neither can touch a meme the saga never marked (one uploaded after the mark, say).
     */
    List<MemeMetadata> pendingOf(String author);

    /** The same two halves keyed by identity: rows whose author_id is this one. */
    List<MemeMetadata> activeOf(UserId author);

    List<MemeMetadata> pendingOf(UserId author);

    /**
     * The leaver's active rows during the dual period: by id when the closure carries one, plus
     * the rows under their address that have no id yet (the backfill has not reached them). A row
     * with another id under the same address is somebody else's.
     */
    default List<MemeMetadata> activeOf(String author, Optional<UserId> authorId) {
        return ofLeaver(author, authorId, activeOf(author), authorId.map(this::activeOf));
    }

    default List<MemeMetadata> pendingOf(String author, Optional<UserId> authorId) {
        return ofLeaver(author, authorId, pendingOf(author), authorId.map(this::pendingOf));
    }

    private static List<MemeMetadata> ofLeaver(String author, Optional<UserId> authorId,
                                               List<MemeMetadata> byAddress,
                                               Optional<List<MemeMetadata>> byId) {
        if (byId.isEmpty()) {
            return byAddress;
        }
        List<MemeMetadata> rows = new ArrayList<>(byId.get());
        for (MemeMetadata row : byAddress) {
            if (row.authorId().isEmpty()) {
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * Persist the erasure state the aggregate computed — {@code status} and
     * {@code markedForErasureAt}, nothing else on the row. This is the write half of "transitions
     * are methods, not setters": the decision was made by
     * {@link MemeMetadata#markForErasure(Instant)} / {@link MemeMetadata#restore()}, and all that is
     * left here is to store it. A row that no longer exists is not an error — a concurrent
     * moderator deletion is a legitimate way for a marked meme to disappear.
     */
    void store(MemeMetadata state);

    /**
     * Every meme marked before {@code cutoff} and still not erased — the reaper's query, and the
     * whole "structure" this feature has: a status and an instant, both on the row. Used to WATCH
     * the backlog (a mark older than any saga can legitimately last means a closure command was
     * lost), never to erase anything: nothing in this service deletes content because time passed.
     */
    List<MemeMetadata> pendingSince(Instant cutoff);
}
