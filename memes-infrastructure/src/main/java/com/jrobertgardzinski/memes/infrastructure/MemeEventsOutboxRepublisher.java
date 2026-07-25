package com.jrobertgardzinski.memes.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "memes.kafka-enabled", havingValue = "true")
class MemeEventsOutboxRepublisher {

    private static final Logger LOG = LoggerFactory.getLogger(MemeEventsOutboxRepublisher.class);

    static final Duration MIN_AGE = Duration.ofSeconds(30);

    private final MemeEventsOutbox outbox;
    private final KafkaMemeEvents events;

    MemeEventsOutboxRepublisher(MemeEventsOutbox outbox, KafkaMemeEvents events) {
        this.outbox = outbox;
        this.events = events;
    }

    @Scheduled(fixedDelay = 15_000)
    void republish() {
        List<MemeEventsOutbox.Pending> overdue = outbox.pendingOlderThan(MIN_AGE);
        if (overdue.isEmpty()) {
            return;
        }
        LOG.warn("re-sending {} unconfirmed meme event(s) from the outbox", overdue.size());
        for (MemeEventsOutbox.Pending event : overdue) {
            events.publish(event);   // confirmed-then-marked, same rule as the first attempt
        }
    }
}
