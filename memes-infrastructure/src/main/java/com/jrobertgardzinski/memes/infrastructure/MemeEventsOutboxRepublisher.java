package com.jrobertgardzinski.memes.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * The outbox's second leg: every 15s, re-send whatever {@link KafkaMemeEvents} wrote but never
 * confirmed — a crash between the delete's commit and the send, or a broker outage during it.
 * Only rows older than {@link #MIN_AGE} qualify, so a fresh after-commit attempt still waiting
 * for its ack is never raced; a duplicate remains possible (confirmed send, crash before the
 * mark) and is harmless — the payload's eventId is deterministic (the row key) and comments'
 * thread-drop is idempotent. {@code @EnableScheduling} lives here on purpose: the scheduler
 * only exists where this bean does — with a broker ({@code memes.kafka-enabled}), not in tests.
 *
 * <p>The same pass also enforces retention: published rows older than
 * {@code memes.outbox.retention-hours} (env {@code MEMES_OUTBOX_RETENTION_HOURS}, default 24h,
 * refused at boot if not at least 1) are deleted — a delivered event has no value once its
 * consumers absorbed it, and without a reaper the table grows by one row per deleted meme,
 * forever. The reaping is batched and capped ({@link #RETENTION_BATCHES_PER_PASS}), so the first
 * pass on an already-large table drains it over several passes instead of holding one long
 * transaction on the scheduler thread.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "memes.kafka-enabled", havingValue = "true")
class MemeEventsOutboxRepublisher {

    private static final Logger LOG = LoggerFactory.getLogger(MemeEventsOutboxRepublisher.class);

    static final Duration MIN_AGE = Duration.ofSeconds(30);

    /**
     * At most this many retention batches per pass — 4 &times; 500 = 2000 rows. The ceiling that
     * keeps the first pass after a long-running deployment (the whole accumulated history of
     * published events) from turning into one long transaction that also delays this pass's
     * re-send leg. A backlog drains at 2000 rows / 15s; steady state never reaches the cap.
     */
    static final int RETENTION_BATCHES_PER_PASS = 4;

    private final MemeEventsOutbox outbox;
    private final KafkaMemeEvents events;
    private final Duration retention;

    MemeEventsOutboxRepublisher(MemeEventsOutbox outbox, KafkaMemeEvents events,
                                @Value("${memes.outbox.retention-hours:24}") long retentionHours) {
        this.outbox = outbox;
        this.events = events;
        this.retention = Duration.ofHours(requirePositive(retentionHours));
    }

    /**
     * Range-check on the dial, refusing the boot rather than the surprise: a retention of 0 (or
     * less) would reap every delivered row the instant this pass runs — including the one a
     * concurrent first attempt is about to mark published — and quietly cost the outbox the
     * forensic window its whole point rests on. Same contract as the other dials in this service
     * (ImageLimits, ThumbnailSize, PurgeRule) and as the offboarding/collections env checks: NAME
     * the property and ECHO the value, because the operator who set it reads the message, not
     * this code.
     */
    private static long requirePositive(long retentionHours) {
        if (retentionHours <= 0) {
            throw new IllegalArgumentException("memes.outbox.retention-hours must be at least 1"
                    + " (a retention of " + retentionHours + " would reap delivered events as fast"
                    + " as they are marked), was " + retentionHours);
        }
        return retentionHours;
    }

    @Scheduled(fixedDelay = 15_000)
    void republish() {
        // retention first, resend second: a row this pass delivers keeps its mark until the NEXT
        // pass finds it aged out — never reaped in the same breath it was confirmed in
        int reaped = outbox.deletePublishedOlderThan(retention, RETENTION_BATCHES_PER_PASS);
        if (reaped > 0) {
            LOG.info("dropped {} delivered meme event(s) older than {} from the outbox", reaped, retention);
        }
        List<MemeEventsOutbox.Pending> overdue = outbox.pendingOlderThan(MIN_AGE);
        if (overdue.isEmpty()) {
            return;
        }
        LOG.warn("re-sending {} unconfirmed meme event(s) from the outbox", overdue.size());
        for (MemeEventsOutbox.Pending event : overdue) {
            events.publish(event);   // blocking, confirmed-then-marked — the guarantee lives here
        }
    }
}
