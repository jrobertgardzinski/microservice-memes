package com.jrobertgardzinski.memes.infrastructure;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * The outbox's durability promise, exercised end to end against the real table: a committed
 * delete whose send fails keeps its row until the republisher delivers it (same payload, same
 * eventId — the row key, so the redelivery is a recognizable duplicate for comments' idempotent
 * thread-drop); a happy-path event is published exactly once, because the republisher touches
 * neither published rows nor fresh ones still owed a first attempt.
 */
@SpringBootTest(classes = MemesApplication.class)
class MemeEventsOutboxTest {

    @Autowired
    TransactionTemplate tx;

    @Autowired
    MemeEventsOutbox outbox;

    @Autowired
    JdbcClient jdbc;

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

    private KafkaMemeEvents events;
    private MemeEventsOutboxRepublisher republisher;

    @BeforeEach
    void freshOutbox() {
        events = new KafkaMemeEvents(kafka, outbox);
        republisher = new MemeEventsOutboxRepublisher(outbox, events);
        jdbc.sql("DELETE FROM meme_events_outbox").update();
    }

    private void backdateBeyondMinAge(String memeId) {
        // the row is seconds old; the republisher only touches rows older than MIN_AGE, so age
        // it artificially — in production the 30s pass by themselves
        jdbc.sql("UPDATE meme_events_outbox SET created_at = DATEADD('SECOND', -60, created_at)"
                + " WHERE event_key = ?").params(memeId).update();
    }

    private boolean published(String memeId) {
        return jdbc.sql("SELECT published FROM meme_events_outbox WHERE event_key = ?")
                .params(memeId).query(Boolean.class).single();
    }

    @Test
    @DisplayName("commit + failed send: the republisher delivers the SAME event later and marks it published")
    void republisher_delivers_what_the_first_attempt_could_not() {
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        tx.executeWithoutResult(status -> events.memeDeleted("orphan-thread"));

        ArgumentCaptor<ProducerRecord<String, String>> firstTry = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(firstTry.capture());
        assertFalse(published("orphan-thread"), "the failed send must leave the row unpublished");

        // the broker comes back; the row comes of age; the republisher takes over
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        backdateBeyondMinAge("orphan-thread");
        republisher.republish();

        ArgumentCaptor<ProducerRecord<String, String>> redelivered = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka, times(2)).send(redelivered.capture());
        assertEquals(firstTry.getValue().value(), redelivered.getValue().value(),
                "the redelivery must be the SAME event — same payload, same deterministic eventId");
        assertTrue(published("orphan-thread"), "confirmed redelivery must mark the row");

        republisher.republish();
        verifyNoMoreInteractions(kafka);   // delivered means done — no third send
    }

    @Test
    @DisplayName("happy path: exactly one publication — the republisher does not double a marked row")
    void happy_path_publishes_exactly_once() {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        tx.executeWithoutResult(status -> events.memeDeleted("gone"));

        verify(kafka, times(1)).send(any(ProducerRecord.class));
        assertTrue(published("gone"));

        backdateBeyondMinAge("gone");   // even old enough to qualify, a published row is not re-sent
        republisher.republish();
        verifyNoMoreInteractions(kafka);
    }

    @Test
    @DisplayName("a fresh unpublished row is left alone — its first after-commit attempt may still be in flight")
    void republisher_leaves_fresh_rows_to_the_first_attempt() {
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));
        tx.executeWithoutResult(status -> events.memeDeleted("just-now"));
        verify(kafka, times(1)).send(any(ProducerRecord.class));

        republisher.republish();   // no backdating: the row is seconds old

        verifyNoMoreInteractions(kafka);
        assertFalse(published("just-now"), "still waiting for the republisher, once it is old enough");
    }
}
