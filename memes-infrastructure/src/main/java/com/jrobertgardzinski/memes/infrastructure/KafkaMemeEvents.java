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
 * once — WITHOUT waiting for the ack: the attempt fires the record and returns, and the broker's
 * confirmation callback marks the row published. A crash between commit and send (or a broker
 * outage) leaves the row unpublished for {@link MemeEventsOutboxRepublisher} to re-send — the
 * republisher, not the first attempt, is the durability mechanism, and IT still waits for the
 * ack before marking (delivered-first). The payload's eventId is the row key, so a redelivery
 * repeats the exact same event; comments' thread-drop is idempotent on duplicates.
 *
 * <p>Why the first attempt must not block: it used to {@code Future.get(5s)} per event, on the
 * announcing thread. A purge of N memes with the broker down would then hold the Kafka LISTENER
 * thread (PurgeCommandsListener drives the purge) for N&times;5s — long enough to blow
 * {@code max.poll.interval.ms} and trigger a consumer-group rebalance, turning a broker outage
 * into a purge-saga outage.
 */
@Component
@ConditionalOnProperty(name = "memes.kafka-enabled", havingValue = "true")
class KafkaMemeEvents implements MemeEvents {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaMemeEvents.class);

    static final String TOPIC = "memes-events";

    /**
     * How long the REPUBLISHER'S attempt waits for the broker's ack before leaving the row for
     * the next pass. Only the scheduler thread ever holds this wait — the first, after-commit
     * attempt is callback-based and never blocks (see {@link #publishFirstAttempt}).
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
        TransactionAwareDeletes.afterCommitOrNow(() -> publishFirstAttempt(event));
    }

    /**
     * The FIRST delivery attempt, fire-and-mark-later: hand the record to the producer and
     * return immediately — the announcing thread (a request, or worse the Kafka listener thread
     * driving a purge) never waits for the broker. The completion callback marks the row
     * published on a confirmed send and only logs a failed one; either way the row is the
     * safety net and the republisher the guarantee.
     *
     * <p>The callback runs on the PRODUCER'S I/O thread, not ours — it must not touch the
     * announcing thread's (already committed, still bound) transaction. {@link
     * MemeEventsOutbox#markPublished} is safe there: a single short UPDATE through a freshly
     * borrowed autocommit connection, no transaction anywhere near it. If the mark itself fails,
     * the event was still DELIVERED — the republisher will redeliver a duplicate, which the
     * deterministic eventId makes recognizable and comments' thread-drop absorbs.
     */
    void publishFirstAttempt(MemeEventsOutbox.Pending event) {
        try {
            kafka.send(toRecord(event)).whenComplete((ack, notConfirmed) -> {
                if (notConfirmed != null) {
                    LOG.error("event {} ({}) was not confirmed by the broker — the outbox keeps it,"
                            + " the republisher will retry", event.id(), event.key(), notConfirmed);
                    return;
                }
                try {
                    outbox.markPublished(event.id());
                } catch (RuntimeException markFailed) {
                    LOG.warn("event {} ({}) was delivered but could not be marked published — the"
                                    + " republisher may redeliver a harmless duplicate",
                            event.id(), event.key(), markFailed);
                }
            });
        } catch (RuntimeException sendRefused) {
            // KafkaTemplate.send can also fail synchronously (producer closed, buffer full with
            // block.on.buffer.full=false, serialization) — same contract: log, row stays, retry
            LOG.error("event {} ({}) could not even be handed to the producer — the outbox keeps"
                    + " it, the republisher will retry", event.id(), event.key(), sendRefused);
        }
    }

    /**
     * The REPUBLISHER'S delivery attempt: send, wait for the broker's ack, and only then mark
     * the row published (delivered-first — the guarantee lives here). Any failure is logged and
     * otherwise swallowed — the row simply stays unpublished and the next pass retries it.
     * Blocking is fine on the scheduler thread; it is the listener/request threads that must
     * never wait (see {@link #publishFirstAttempt}).
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
