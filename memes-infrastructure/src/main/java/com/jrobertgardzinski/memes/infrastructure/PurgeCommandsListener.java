package com.jrobertgardzinski.memes.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrobertgardzinski.memes.application.PurgeUserContent;
import com.jrobertgardzinski.memes.config.ContentPurgePolicy;
import com.jrobertgardzinski.memes.config.PurgeRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * The meme service's side of the account-deletion saga: a PURGE_USER_CONTENT command (from
 * microservice-security's outbox) purges the user's content and the confirmation goes back on
 * {@code memes-events}. The purge is idempotent, so at-least-once delivery needs no extra dedup.
 * Enabled only where a broker exists (compose sets KAFKA_ENABLED) — tests exercise the use case
 * directly and the whole loop runs in the workspace smoke test.
 */
@Component
@ConditionalOnProperty(name = "memes.kafka-enabled", havingValue = "true")
class PurgeCommandsListener {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeCommandsListener.class);

    private final PurgeUserContent purgeUserContent;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    PurgeCommandsListener(PurgeUserContent purgeUserContent, KafkaTemplate<String, String> kafka,
                          ObjectMapper mapper) {
        this.purgeUserContent = purgeUserContent;
        this.kafka = kafka;
        this.mapper = mapper;
    }

    /**
     * The leaver's wizard choice, when the command carries one; unparseable rules fall back to
     * the deployment default (logged) rather than wedging the saga.
     */
    private java.util.Optional<ContentPurgePolicy> requestedPolicy(JsonNode command) {
        JsonNode policy = command.path("policy");
        if (policy.isMissingNode()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(new ContentPurgePolicy(
                    PurgeRule.parse(policy.path("memes").asText()),
                    PurgeRule.parse(policy.path("comments").asText())));
        } catch (IllegalArgumentException invalid) {
            LOG.warn("ignoring invalid purge policy in command ({}), using the default", invalid.getMessage());
            return java.util.Optional.empty();
        }
    }

    @KafkaListener(topics = "memes-commands", groupId = "memes")
    void receive(String payload) throws Exception {
        JsonNode command;
        try {
            command = mapper.readTree(payload);
        } catch (Exception malformed) {
            LOG.warn("dropping malformed memes command: {}", payload);
            return;
        }
        if (!"PURGE_USER_CONTENT".equals(command.path("type").asText())) {
            return;
        }
        String email = command.path("email").asText();
        purgeUserContent.execute(email, requestedPolicy(command));
        LOG.info("purged content of {} (saga {})", email, command.path("sagaId").asText());
        kafka.send("memes-events", email, mapper.writeValueAsString(mapper.createObjectNode()
                .put("type", "USER_CONTENT_PURGED")
                .put("sagaId", command.path("sagaId").asText())
                .put("email", email)));
    }
}
