package com.jrobertgardzinski.memes.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * The {@code meme_events_outbox} table (V5): fully built, ready-to-send event records that share
 * the transaction of the teardown announcing them. {@link #append} participates in whatever
 * transaction is open on the calling thread — a rollback of the delete/purge takes the outbox row
 * with it, which is the whole point. {@link KafkaMemeEvents} marks a row published only after the
 * broker CONFIRMED the send; {@link MemeEventsOutboxRepublisher} re-sends whatever stayed
 * unpublished for too long (crash between commit and send, broker outage).
 */
@Component
class MemeEventsOutbox {

    private static final Logger LOG = LoggerFactory.getLogger(MemeEventsOutbox.class);

    /**
     * What the payload column was sized for originally (varchar(1024), V5 — widened to TEXT in
     * V6). Nothing truncates any more; crossing this line is merely unexpected for the event
     * types known so far, so {@link #append} logs a canary instead of failing.
     */
    static final int EXPECTED_MAX_PAYLOAD_CHARS = 1024;

    /** One ready-to-send event: everything the producer needs, captured at announce time. */
    record Pending(String id, String topic, String key, String cid, String payload) {
    }

    private final JdbcClient jdbc;
    private final Clock clock;

    MemeEventsOutbox(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Writes the event in the CURRENT transaction (or immediately, outside one). */
    Pending append(String id, String topic, String type, String key, String cid, String payload) {
        if (payload.length() > EXPECTED_MAX_PAYLOAD_CHARS) {
            // TEXT (V6) absorbs it whole — this is a canary, not a guard rail: an event type
            // suddenly this fat is worth a look before it becomes routine
            LOG.warn("outbox payload for {} event {} is {} chars — wider than any event type"
                            + " known so far ({} was the old column limit); stored intact, but check what grew",
                    type, id, payload.length(), EXPECTED_MAX_PAYLOAD_CHARS);
        }
        jdbc.sql("INSERT INTO meme_events_outbox"
                        + " (id, topic, event_type, event_key, cid, payload, created_at, published)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, FALSE)")
                .params(id, topic, type, key, cid, payload, Timestamp.from(clock.instant()))
                .update();
        return new Pending(id, topic, key, cid, payload);
    }

    /** Called only after the broker acknowledged the send — never optimistically. */
    void markPublished(String id) {
        jdbc.sql("UPDATE meme_events_outbox SET published = TRUE WHERE id = ?").params(id).update();
    }

    /**
     * How many rows one retention statement removes. Small enough that the transaction it runs in
     * is short (a handful of pages, locks held for milliseconds), large enough that a normal pass
     * — a day's deletions on a busy gallery — is one or two statements.
     */
    static final int RETENTION_BATCH_ROWS = 500;

    /**
     * Retention: drops PUBLISHED rows older than {@code retention}, in batches of
     * {@link #RETENTION_BATCH_ROWS} and at most {@code maxBatches} of them, returning how many
     * rows went. A delivered event has no value once its consumers absorbed it — the row existed
     * only to guarantee the delivery that has demonstrably happened — so without this the table
     * grows forever, one row per deleted meme. Unpublished rows are never touched: however old,
     * they still carry an obligation.
     *
     * <p>Why batched and capped, when one statement would read better: the FIRST pass after this
     * feature ships meets the entire accumulated history of published events. One
     * {@code DELETE ... WHERE published} over that is a single huge transaction on the scheduler
     * thread — long lock hold, a write-ahead log spike, and a pass that does not return for
     * however long the delete takes (blocking the re-send leg that shares this pass). The cap
     * bounds a pass at {@code maxBatches} &times; {@link #RETENTION_BATCH_ROWS} rows; what does
     * not fit waits 15s for the next one, and a backlog drains over a few minutes instead of one
     * stall. Steady state is unaffected — the first batch comes back short and the loop stops.
     *
     * <p>The {@code IN (SELECT ... LIMIT n)} shape is the portable one: PostgreSQL rejects LIMIT
     * on DELETE itself but allows it in the subquery, and H2 (the tests' PostgreSQL-mode
     * database) accepts exactly the same statement — one SQL string for both, like every other
     * query in this adapter.
     */
    int deletePublishedOlderThan(Duration retention, int maxBatches) {
        Timestamp cutoff = Timestamp.from(clock.instant().minus(retention));
        int deleted = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            int gone = jdbc.sql("DELETE FROM meme_events_outbox WHERE id IN ("
                            + " SELECT id FROM meme_events_outbox"
                            + " WHERE published = TRUE AND created_at <= ?"
                            + " ORDER BY created_at, id LIMIT " + RETENTION_BATCH_ROWS + ")")
                    .params(cutoff)
                    .update();
            deleted += gone;
            if (gone < RETENTION_BATCH_ROWS) {
                return deleted;   // the table ran out before the cap — nothing left to reap
            }
        }
        LOG.info("retention stopped at its cap of {} batch(es) ({} rows) — more published rows are"
                        + " past {}, the next pass takes them", maxBatches, deleted, retention);
        return deleted;
    }

    /**
     * Unpublished events older than {@code minAge} — old enough that the after-commit send has
     * clearly either crashed or failed, so re-sending cannot race a first attempt still in flight.
     */
    List<Pending> pendingOlderThan(Duration minAge) {
        return jdbc.sql("SELECT id, topic, event_key, cid, payload FROM meme_events_outbox"
                        + " WHERE published = FALSE AND created_at <= ? ORDER BY created_at, id")
                .params(Timestamp.from(clock.instant().minus(minAge)))
                .query((rs, n) -> new Pending(rs.getString("id"), rs.getString("topic"),
                        rs.getString("event_key"), rs.getString("cid"), rs.getString("payload")))
                .list();
    }
}
