package com.jrobertgardzinski.memes.infrastructure;

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
    List<Pending> pendingOlderThan(Duration minAge) {
        return jdbc.sql("SELECT id, topic, event_key, cid, payload FROM meme_events_outbox"
                        + " WHERE published = FALSE AND created_at <= ? ORDER BY created_at, id")
                .params(Timestamp.from(clock.instant().minus(minAge)))
                .query((rs, n) -> new Pending(rs.getString("id"), rs.getString("topic"),
                        rs.getString("event_key"), rs.getString("cid"), rs.getString("payload")))
                .list();
    }
}
