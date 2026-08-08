package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.MemeMetadata;

import java.time.Clock;
import java.time.Instant;

/**
 * The meme service's REVERSIBLE step of an account deletion: every meme the leaver still has in the
 * gallery is marked {@link com.jrobertgardzinski.memes.domain.MemeStatus#PENDING_ERASURE}. Nothing
 * is destroyed here — no row, no blob, no vote, not even the authorship — and that is the entire
 * point: this is the step the orchestrator can take back when a LATER participant of the same saga
 * fails ({@link RestoreUserContent}).
 *
 * <p>What the outside world sees immediately is nevertheless the full effect of a deletion: the
 * memes are gone from the gallery, from the tag search, from the ranking, from the dedup index's
 * answers and from every {@code /meta} and image URL — because all of those read through
 * {@link MemeRepository}, which cannot see a marked meme. The leaver is not asked to wait for the
 * saga to finish before their content disappears.
 *
 * <p><strong>Idempotent</strong>, as every saga command must be (workspace ADR 0006): the command
 * arrives at least once, and the second delivery finds nothing left to mark — a meme that is
 * already marked keeps its ORIGINAL instant, so re-commanding never rejuvenates an obligation the
 * erasure backlog is watching.
 *
 * <p>The rule (delete / anonymise / keep the popular ones) is deliberately NOT consulted here. It
 * reads vote scores, and the leaver's own votes are retracted as part of the erasure, so applying
 * it before the votes go would measure the community's judgement against a tally that includes the
 * departing voter (P18 poz. 39). The whole decision therefore happens once, at
 * {@link PurgeUserContent}, when the saga has committed to it.
 */
public class MarkUserContentForErasure {

    private final MemeErasure erasure;
    private final Clock clock;

    public MarkUserContentForErasure(MemeErasure erasure, Clock clock) {
        this.erasure = erasure;
        this.clock = clock;
    }

    public void execute(String author) {
        Instant at = Instant.now(clock);
        for (MemeMetadata meme : erasure.activeOf(author)) {
            // the transition is the aggregate's, never a setter and never an UPDATE spelled out
            // here: the record decides what "marked" means (including keeping the first instant on
            // a redelivery), and the port only stores the answer
            erasure.store(meme.markForErasure(at));
        }
    }
}
