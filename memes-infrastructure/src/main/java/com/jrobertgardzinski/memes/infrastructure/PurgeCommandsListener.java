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
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The meme service's side of the account-deletion saga: a PURGE_USER_CONTENT command (the command
 * microservice-offboarding publishes) purges the user's content and the confirmation goes back on
 * {@code memes-events}. The purge is idempotent, so at-least-once delivery needs no extra dedup.
 * Enabled only where a broker exists (compose sets KAFKA_ENABLED) — tests exercise the use case
 * directly and the whole loop runs in the workspace smoke test.
 *
 * <p><strong>The three guarantees this participant used to lack</strong> (the 26.07 audit's second
 * theme: one role, four implementations, four sets of promises). All three live around this class
 * now, and the reasoning for each is where it is enforced:
 * <ul>
 *   <li>a command is no longer lost to a one-second hiccup — {@link SagaRetryBudget} retries it with
 *       backoff for a budget derived from the orchestrator's own timeline, then drops it loudly and
 *       counted instead of silently in a millisecond;</li>
 *   <li>a stalled listener loop no longer hides behind a green process — {@link SagaListenersHealth}
 *       turns readiness red;</li>
 *   <li>the confirmation is no longer a bare fire-and-forget send — {@link PurgeConfirmations} writes
 *       it into the outbox, in the SAME transaction as the purge it confirms.</li>
 * </ul>
 *
 * <p>That last one is why this class opens a transaction: only here are the erasure and its
 * confirmation one unit of work. Either the leaver's memes are gone AND the outbox owes the
 * orchestrator a confirmation, or neither happened and the command comes back. The use case's own
 * transactional decorator joins this one (Spring's default propagation), exactly as
 * {@code MemesEventsListener} does for the cascade in microservice-comments.
 */
@Component
@ConditionalOnProperty(name = "memes.kafka-enabled", havingValue = "true")
class PurgeCommandsListener {

    private static final Logger LOG = LoggerFactory.getLogger(PurgeCommandsListener.class);

    private final PurgeUserContent purgeUserContent;
    private final PurgeConfirmations confirmations;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    PurgeCommandsListener(PurgeUserContent purgeUserContent, PurgeConfirmations confirmations,
                          ObjectMapper mapper, TransactionTemplate tx) {
        this.purgeUserContent = purgeUserContent;
        this.confirmations = confirmations;
        this.mapper = mapper;
        this.tx = tx;
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

    /**
     * The container id is spelled out (the group id is unchanged, {@code memes} — with a group id
     * present, the id names only the container): it is what {@link SagaListenersHealth} prints under
     * {@code /actuator/health}, and "memes-purge-commands is not polling" is an answer, while
     * "org.springframework.kafka.KafkaListenerEndpointContainer#0" is a riddle.
     */
    @KafkaListener(id = "memes-purge-commands", topics = "content-commands", groupId = "memes")
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
        // the policy is read OUTSIDE the transaction: it is pure parsing, and its WARN about an
        // unreadable rule must not be re-logged on every retry of the same command
        java.util.Optional<PurgeRule> policy = requestedPolicy(command);
        purgeAndConfirm(sagaId, email, policy);
        // the saga id identifies the run in logs; the e-mail is PII and stays out of INFO lines —
        // writing it here would outlive the erasure this very line reports (logs ship to Loki,
        // which knows nothing about the saga's 30-day retention)
        LOG.info("purged the memes of one leaver (saga {})", sagaId);
    }

    /**
     * The erasure and the promise to report it, as ONE transaction. A failure anywhere inside
     * propagates out of {@link #receive}, which is what makes the error handler retry the record with
     * backoff and — the purge being idempotent — run the whole thing again.
     *
     * <p>The confirmation is announced INSIDE, not after: announcing after the commit would leave a
     * window where the memes are gone and nothing owes the orchestrator a word about it, which is the
     * failure mode that ends with the leaver holding a restored account without their content. The
     * outbox library parks the actual send on the commit, so nothing is published before the erasure
     * is real.
     */
    private void purgeAndConfirm(String sagaId, String email, java.util.Optional<PurgeRule> policy) {
        tx.executeWithoutResult(status -> {
            purgeUserContent.execute(email, policy);
            confirmations.confirm(sagaId, email);
        });
    }
}
