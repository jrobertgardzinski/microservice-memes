package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.MemeEvents;
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

    private final KafkaTemplate<String, String> kafka;

    KafkaMemeEvents(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void memeDeleted(String memeId) {
        kafka.send("memes-events", memeId, "{\"type\":\"MEME_DELETED\",\"memeId\":\"" + memeId + "\"}");
    }
}
