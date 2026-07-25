package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.MemeEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes meme lifecycle events on {@code memes-events}; microservice-comments drops a deleted
 * meme's thread on MEME_DELETED. Active where a broker exists; the no-op stand-in serves tests.
 */
@Component
@ConditionalOnProperty(name = "memes.kafka-enabled", havingValue = "true")
class KafkaMemeEvents implements MemeEvents {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaMemeEvents.class);

    private final KafkaTemplate<String, String> kafka;

    KafkaMemeEvents(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void memeDeleted(String memeId) {
        // Parked until the delete/purge transaction commits (same mechanism as the blob deletes,
        // TransactionAwareDeletes): the use cases announce the deletion INSIDE the transactional
        // decorators, so sending eagerly meant a rollback after the send had comments drop the
        // thread of a meme that still exists. A rollback now simply discards the announcement;
        // outside a transaction it goes out immediately. This is NOT an outbox (todo.md): a crash
        // between commit and send still loses the event — but the rollback lie is gone.
        // build the record (and stamp the cid header from MDC) NOW, while the request's thread
        // context is certainly still around — only the send itself waits for the commit
        var record = KafkaTracing.withCid("memes-events", memeId,
                "{\"type\":\"MEME_DELETED\",\"memeId\":\"" + memeId + "\"}");
        TransactionAwareDeletes.afterCommitOrNow(() -> send(memeId, record));
    }

    private void send(String memeId, org.apache.kafka.clients.producer.ProducerRecord<String, String> record) {
        // send() is async — an ignored future means a broker outage silently loses the event and
        // the comment thread of a deleted meme lives on. Logging is the honest minimum here; a
        // real guarantee needs an outbox (noted in todo.md), not a bigger callback.
        kafka.send(record)
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        LOG.error("MEME_DELETED for {} was not published — the comments service "
                                + "will keep the thread", memeId, failure);
                    }
                });
    }
}
