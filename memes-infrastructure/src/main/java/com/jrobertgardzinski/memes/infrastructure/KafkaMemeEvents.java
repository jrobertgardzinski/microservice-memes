package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.MemeEvents;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Publishes meme lifecycle events on {@code memes-events}; microservice-comments drops a deleted
 * meme's thread on MEME_DELETED. Active where a broker exists; the no-op stand-in serves tests.
 *
 * <p>This is a real outbox now (the todo that stood since round 3): the event row goes into
 * {@link MemeEventsOutbox} in the SAME transaction as the delete/purge announcing it, so a
 * rollback discards the announcement with the teardown. After the commit the send is attempted
 * once, and the row is marked published only after the broker CONFIRMED delivery — a crash
 * between commit and send (or a broker outage) leaves the row unpublished for
 * {@link MemeEventsOutboxRepublisher} to re-send. The payload's eventId is the row key, so a
 * redelivery repeats the exact same event; comments' thread-drop is idempotent on duplicates.
 */
@Component
@ConditionalOnProperty(name = "memes.kafka-enabled", havingValue = "true")
class KafkaMemeEvents implements MemeEvents {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaMemeEvents.class);

    static final String TOPIC = "memes-events";

    /**
     * How long the after-commit attempt waits for the broker's ack before leaving the row to the
     * republisher. It briefly holds the request thread (and its still-bound DB connection), so it
     * must stay short — the republisher, not a longer wait, is the durability mechanism.
     */
    private static final Duration CONFIRMATION_PATIENCE = Duration.ofSeconds(5);

    private final KafkaTemplate<String, String> kafka;
    private final MemeEventsOutbox outbox;

    KafkaMemeEvents(KafkaTemplate<String, String> kafka, MemeEventsOutbox outbox) {
        this.kafka = kafka;
        this.outbox = outbox;
    }

    @Override
    public void memeDeleted(String memeId) {
        // Everything the send needs is captured NOW, while the request thread's context is
        // certainly still around (the cid from MDC, same as the old parked-record approach), and
        // written into the outbox — INSIDE the delete/purge transaction, since the use cases
        // announce the deletion from within the transactional decorators. Rollback = no row =
        // no event. Only the publication attempt waits for the commit (TransactionAwareDeletes);
        // outside a transaction the row commits by itself and the attempt runs immediately.
        String eventId = UUID.randomUUID().toString();
        String payload = "{\"type\":\"MEME_DELETED\",\"memeId\":\"" + memeId
                + "\",\"eventId\":\"" + eventId + "\"}";
        MemeEventsOutbox.Pending event = outbox.append(eventId, TOPIC, "MEME_DELETED", memeId,
                MDC.get(CorrelationIdFilter.MDC_KEY), payload);
        TransactionAwareDeletes.afterCommitOrNow(() -> publish(event));
    }

    /**
     * One delivery attempt: send, wait for the broker's ack, and only then mark the row
     * published. Any failure is logged and otherwise swallowed — the row simply stays
     * unpublished and the republisher retries it. Shared with the republisher, so both paths
     * mark rows under the same "confirmed first" rule.
     */
    void publish(MemeEventsOutbox.Pending event) {
        try {
            kafka.send(toRecord(event)).get(CONFIRMATION_PATIENCE.toMillis(), TimeUnit.MILLISECONDS);
            outbox.markPublished(event.id());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOG.error("interrupted before {} ({}) was confirmed — the outbox keeps it,"
                    + " the republisher will retry", event.id(), event.key(), interrupted);
        } catch (Exception notConfirmed) {
            LOG.error("event {} ({}) was not confirmed by the broker — the outbox keeps it,"
                    + " the republisher will retry", event.id(), event.key(), notConfirmed);
        }
    }

    private static ProducerRecord<String, String> toRecord(MemeEventsOutbox.Pending event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(event.topic(), event.key(), event.payload());
        if (event.cid() != null) {
            // the header KafkaTracing would have stamped — but from the STORED cid, not the MDC:
            // a republish hours later must still carry the trace of the request that deleted
            record.headers().add(KafkaTracing.HEADER, event.cid().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }
}
