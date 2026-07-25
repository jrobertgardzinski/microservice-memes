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
     * Unpublished events older than {@code minAge} — old enough that the after-commit send has
     * clearly either crashed or failed, so re-sending cannot race a first attempt still in flight.
     */
    /**
     * Retention: drops PUBLISHED rows older than {@code retention}. A delivered event has no
     * value once its consumers absorbed it — the row existed only to guarantee the delivery that
     * has demonstrably happened — so without this the table grows forever, one row per deleted
     * meme. Unpublished rows are never touched: however old, they still carry an obligation.
     */
    int deletePublishedOlderThan(Duration retention) {
        return jdbc.sql("DELETE FROM meme_events_outbox WHERE published = TRUE AND created_at <= ?")
                .params(Timestamp.from(clock.instant().minus(retention)))
                .update();
    }

    List<Pending> pendingOlderThan(Duration minAge) {
        return jdbc.sql("SELECT id, topic, event_key, cid, payload FROM meme_events_outbox"
                        + " WHERE published = FALSE AND created_at <= ? ORDER BY created_at, id")
                .params(Timestamp.from(clock.instant().minus(minAge)))
                .query((rs, n) -> new Pending(rs.getString("id"), rs.getString("topic"),
                        rs.getString("event_key"), rs.getString("cid"), rs.getString("payload")))
                .list();
    }
}
