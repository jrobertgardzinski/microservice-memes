package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.MemeEvents;
import com.jrobertgardzinski.outbox.OutboxEvent;
import com.jrobertgardzinski.outbox.spring.SpringOutbox;
import org.slf4j.MDC;

/**
 * Publishes meme lifecycle events on {@code memes-events}; microservice-comments drops a deleted
 * meme's thread on MEME_DELETED and microservice-user-collections drops every saved reference to
 * it. Active where a broker exists; the no-op stand-in serves tests.
 *
 * <p>The durability is a transactional outbox, and since round 10 that outbox is the shared kernel
 * library ({@code com.jrobertgardzinski:transactional-outbox} plus its Spring adapter) rather than
 * two classes of this service's own. It is the same code, not a same-shaped copy: the library was
 * extracted FROM this implementation, which is why every property rounds 5-9 hardened here still
 * has its test in this repository — a green suite is what proves the abstraction ate none of them.
 *
 * <p>What {@link SpringOutbox#announce} does with the event built below, in one call:
 * <ul>
 *   <li>writes the row in the SAME transaction as the delete/purge announcing it (the use cases
 *       announce from inside their transactional decorators, {@link TransactionalDeleteMeme} and
 *       {@link TransactionalPurgeUserContent}), so a rollback discards the announcement with the
 *       teardown — no row, no event;</li>
 *   <li>parks the FIRST delivery attempt on the commit, and makes it without waiting for the
 *       broker's acknowledgement — the mark rides the confirmation callback instead. A crash
 *       between commit and send, or a broker outage during it, leaves the row unpublished for the
 *       library's republisher (wired in {@link MemeOutboxConfig}), which waits for the
 *       acknowledgement before marking: delivered-first, and the republisher — not the first
 *       attempt — is the guarantee.</li>
 * </ul>
 *
 * <p>This class is left with exactly the two jobs the library cannot do for it. It <strong>names
 * the topic</strong> (pinned by {@link MemeDeletedTopicTest} and, across repositories, by the
 * MEME_DELETED pact), and it <strong>builds the payload around the event id the library minted
 * first</strong>. That second one is a documented division of labour: the library keeps the payload
 * opaque on purpose — never parsed, never re-serialised, so a redelivery is byte-identical to the
 * first attempt by construction — which is exactly why it cannot inject the id into the JSON
 * itself. And the id must be in there, because redelivery is possible by design (a confirmed send
 * whose mark did not land) and the consumers recognise the duplicate by it.
 *
 * @see KafkaMemeDispatch the send itself, and the two Kafka-side halves of the library's contract
 */
class KafkaMemeEvents implements MemeEvents {

    /**
     * Where the deletion cascade starts. Two other repositories subscribe to this literal — see
     * {@link MemeDeletedTopicTest}, which names them, before changing it.
     */
    static final String TOPIC = "memes-events";

    static final String MEME_DELETED = "MEME_DELETED";

    private final SpringOutbox outbox;

    KafkaMemeEvents(SpringOutbox outbox) {
        this.outbox = outbox;
    }

    @Override
    public void memeDeleted(String memeId) {
        outbox.announce(deletionOf(memeId));
    }

    /**
     * The event as it will be stored AND as it will be sent — the row is the record.
     *
     * <p>Everything is captured NOW, while the announcing thread's context is certainly still
     * around: the correlation id comes from the MDC here and is stored in the row, so a
     * republication hours later (scheduler thread, empty MDC) still carries the trace of the
     * request that deleted the meme.
     *
     * <p>The event id is minted BEFORE the payload and pasted INTO it, so the row's key and the
     * {@code eventId} a consumer deduplicates on are the same string, for the original attempt and
     * for every redelivery of that row. Package-private and static because the contract tests build
     * the real announcement from it without a database — the payload's shape is what they verify,
     * and a table is not part of that shape.
     */
    static OutboxEvent deletionOf(String memeId) {
        String eventId = OutboxEvent.newId();
        String payload = "{\"type\":\"" + MEME_DELETED + "\",\"memeId\":\"" + memeId
                + "\",\"eventId\":\"" + eventId + "\"}";
        // keyed by the meme, so its whole cascade stays on one partition: a consumer never sees a
        // later hop of the cascade before this one
        return new OutboxEvent(eventId, TOPIC, MEME_DELETED, memeId,
                MDC.get(CorrelationIdFilter.MDC_KEY), payload);
    }
}
