package com.jrobertgardzinski.memes.infrastructure;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MEME_DELETED must respect the delete/purge transaction it is announced from: the use cases call
 * {@code memeEvents.memeDeleted} INSIDE the transactional decorators, so an eager send let a
 * rollback un-delete the meme after the comments service had already dropped its thread. The event
 * now goes out only after the commit; a rollback discards it. (Not an outbox — a crash between
 * commit and send still loses the event; that todo stands.)
 */
@SpringBootTest(classes = MemesApplication.class)
class KafkaMemeEventsTransactionTest {

    @Autowired
    TransactionTemplate tx;

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final KafkaMemeEvents events = new KafkaMemeEvents(kafka);

    @Test
    @DisplayName("a rolled-back delete transaction publishes NO event — comments keep the living meme's thread")
    void rollback_discards_the_event() {
        tx.executeWithoutResult(status -> {
            events.memeDeleted("still-alive");
            status.setRollbackOnly();
        });

        verifyNoInteractions(kafka);
    }

    @Test
    @DisplayName("inside a transaction the event waits for the commit, then goes out")
    void commit_releases_the_event() {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        tx.executeWithoutResult(status -> {
            events.memeDeleted("gone-for-good");
            verifyNoInteractions(kafka);   // not yet — the transaction could still roll back
        });

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sent.capture());
        assertEquals("memes-events", sent.getValue().topic());
        assertEquals("gone-for-good", sent.getValue().key());
        assertTrue(sent.getValue().value().contains("MEME_DELETED"));
    }

    @Test
    @DisplayName("outside a transaction (no seam in sight) the event is published immediately")
    void outside_a_transaction_the_event_goes_out_now() {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        events.memeDeleted("already-committed");

        verify(kafka).send(any(ProducerRecord.class));
    }
}
