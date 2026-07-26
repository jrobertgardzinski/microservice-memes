package com.jrobertgardzinski.memes.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrobertgardzinski.memes.application.PurgeUserContent;
import com.jrobertgardzinski.memes.config.PurgeRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Header;
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
     * The leaver's wizard choice for THIS service's axis (the memes rule), when the command
     * carries one; unparseable rules fall back to the deployment default (logged) rather than
     * wedging the saga.
     */
    private java.util.Optional<PurgeRule> requestedPolicy(JsonNode command) {
        JsonNode rule = command.path("policy").path("memes");
        if (rule.isMissingNode()) {
            return java.util.Optional.empty();
        }
        String text = rule.asText();
        try {
            return java.util.Optional.of(PurgeRule.parse(text));
        } catch (IllegalArgumentException invalid) {
            // NOT invalid.getMessage(): parse() pastes the raw rule text from the wire into it,
            // and that text is whatever the leaver typed into the deletion request — kilobytes
            // of it, newlines included, i.e. forged log lines in Loki. A constant plus the
            // length and a vocabulary-only fragment is enough to investigate
            LOG.warn("ignoring an unparseable memes purge rule ({} chars, looks like '{}'), "
                    + "using the default", text.length(), sanitizedFragment(text));
            return java.util.Optional.empty();
        }
    }

    /**
     * The purge-rule VOCABULARY, whole tokens only — never the raw wire text (the same whitelist
     * the comments service arrived at, for the same reason). A per-character filter would keep
     * every digit and every uppercase letter, which is exactly the alphabet of phone numbers and
     * SHOUTED e-mail addresses; this inverts the burden of proof — only the three rule words
     * survive, with a popularity threshold (≤4 digits) accepted solely in its grammar position
     * after {@code KEEP_POPULAR_ANONYMIZED:}, because a free-standing number is not vocabulary.
     * Everything unrecognised collapses to a single {@code ?} per run, so the log shows the
     * rule's shape ("was it almost a rule?") and none of its content.
     */
    private static final java.util.regex.Pattern VOCABULARY = java.util.regex.Pattern.compile(
            "(?<![A-Z_0-9:])(?:KEEP_POPULAR_ANONYMIZED(?::\\d{1,4})?|ANONYMIZE_AUTHOR|DELETE)(?![A-Z_0-9:])");

    private static String sanitizedFragment(String text) {
        StringBuilder kept = new StringBuilder();
        java.util.regex.Matcher vocabulary = VOCABULARY.matcher(text);
        int consumedUpTo = 0;
        while (vocabulary.find()) {
            if (vocabulary.start() > consumedUpTo) {
                kept.append('?');   // one ? per unrecognised run, no matter how long or what it held
            }
            kept.append(vocabulary.group());
            consumedUpTo = vocabulary.end();
        }
        if (consumedUpTo < text.length() || text.isEmpty()) {
            kept.append('?');
        }
        return kept.length() <= 32 ? kept.toString() : kept.substring(0, 32) + "…";
    }

    @KafkaListener(topics = "content-commands", groupId = "memes")
    void receive(String payload,
                 @Header(name = KafkaTracing.HEADER, required = false) String cid) throws Exception {
        if (cid != null) {
            MDC.put("cid", cid);   // continue the trace the deletion request started in security
        }
        try {
            handle(payload);
        } finally {
            MDC.remove("cid");
        }
    }

    private void handle(String payload) throws Exception {
        JsonNode command;
        try {
            command = mapper.readTree(payload);
        } catch (Exception malformed) {
            // NOT the payload itself: a purge command carries the leaver's e-mail, and even a
            // malformed one may — PII stays out of the logs, the size is enough to investigate
            LOG.warn("dropping a malformed command ({} chars, not valid JSON)",
                    payload == null ? 0 : payload.length());
            return;
        }
        if (!"PURGE_USER_CONTENT".equals(command.path("type").asText())) {
            return;
        }
        String sagaId = command.path("sagaId").asText();
        String email = command.path("email").asText();
        if (email.isBlank()) {
            // a purge keyed by NOBODY would "succeed" instantly and confirm a deletion that never
            // happened — the saga would advance on a lie. Drop it WITHOUT confirming: the command
            // is malformed at the source, and the orchestrator's timeout is the honest signal.
            LOG.warn("dropping PURGE_USER_CONTENT without an email (saga {})", sagaId);
            return;
        }
        purgeUserContent.execute(email, requestedPolicy(command));
        // the saga id identifies the run in logs; the e-mail is PII and stays out of INFO lines —
        // writing it here would outlive the erasure this very line reports (logs ship to Loki,
        // which knows nothing about the saga's 30-day retention)
        LOG.info("purged the memes of one leaver (saga {})", sagaId);
        // forward the cid on the confirmation so security's listener continues the same trace
        kafka.send(KafkaTracing.withCid("memes-events", email, mapper.writeValueAsString(mapper.createObjectNode()
                .put("type", "USER_CONTENT_PURGED")
                .put("sagaId", sagaId)
                .put("email", email)
                // envelope version (workspace ADR 0004): fields only ever added within version 1
                .put("version", 1))));
    }
}
