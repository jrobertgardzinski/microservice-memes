package com.jrobertgardzinski.memes.infrastructure;

import au.com.dius.pact.provider.MessageAndMetadata;
import au.com.dius.pact.provider.PactVerifyProvider;
import au.com.dius.pact.provider.junit5.MessageTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The deletion CASCADE's first hop, provider side: microservice-user-collections' committed pact
 * states which MEME_DELETED fields — and which TOPIC — its cascade consumer depends on, and this
 * test proves the REAL producer emits them. The record under verification is the one
 * {@link KafkaMemeEvents} actually hands to the broker, captured off the Kafka template; nothing
 * here is hand-written JSON.
 *
 * <p><b>Both halves of the record are verified,</b> which is the point of this file. The payload is
 * checked against the pact's body, and {@code record.topic()} is reported as the message's
 * {@code topic} metadata, which the pact also pins. The audit of 26.07 called an unasserted topic
 * name the system's most dangerous structural gap: rename the topic on one side and the events fall
 * into the void — nothing is deleted, nothing fails, CI stays green. Reporting the real
 * {@code topic()} into a pact that names the consumer's subscription closes that gap ACROSS the two
 * repositories, which no test inside either one can do alone.
 *
 * <p>Skipped, not failed, when the consumer repo is not checked out next to this one — the
 * workspace convention. The predicate looks for the pact FILE, not merely a directory: a directory
 * check is what let {@code SecurityOutcomePactProviderTest} in microservice-offboarding skip
 * silently for weeks after the workspace split, and a contract test that never runs is
 * indistinguishable from one that passes.
 */
@Provider("microservice-memes")
@PactFolder(MemeDeletedPactProviderTest.PACT_FOLDER)
@EnabledIf(value = "consumerPactCheckedOut",
        disabledReason = "microservice-user-collections' memes pact is not checked out next to this repo")
class MemeDeletedPactProviderTest {

    /** Relative to the MODULE directory, which is what surefire makes the working directory. */
    static final String PACT_FOLDER = "../../microservice-user-collections/pacts";

    static final String PACT_FILE = "microservice-user-collections-microservice-memes.json";

    static boolean consumerPactCheckedOut() {
        return Files.isRegularFile(Path.of(PACT_FOLDER, PACT_FILE));
    }

    @BeforeEach
    void target(PactVerificationContext context) {
        context.setTarget(new MessageTestTarget(List.of("com.jrobertgardzinski")));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void theAnnouncementShapeAndTopicTheCascadeReliesOn(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @PactVerifyProvider("a meme deleted announcement")
    public MessageAndMetadata aMemeDeletedAnnouncement() {
        ProducerRecord<String, String> record = realAnnouncement(UUID.randomUUID().toString());
        return new MessageAndMetadata(
                record.value().getBytes(StandardCharsets.UTF_8),
                // the topic is transport, so it belongs in the message's metadata rather than its
                // body — and it is the REAL record's, never the constant: a producer that starts
                // publishing elsewhere must fail here
                Map.of("contentType", "application/json", "topic", record.topic()));
    }

    /**
     * The record the real producer would hand the broker for a deleted meme.
     *
     * <p>Two collaborators stand in, for the same reason and in the same spirit as the
     * {@code KafkaTemplate} mock in {@link PurgeConfirmationPactProviderTest}: they are adapters,
     * not the code under test. The outbox is a database table, so it is stubbed to hand back the row
     * it was asked to store; the payload, key and topic in that row are the ones
     * {@link KafkaMemeEvents#memeDeleted} really built.
     */
    @SuppressWarnings("unchecked")
    static ProducerRecord<String, String> realAnnouncement(String memeId) {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        MemeEventsOutbox outbox = mock(MemeEventsOutbox.class);
        when(outbox.append(any(), any(), any(), any(), any(), any())).thenAnswer(stored ->
                new MemeEventsOutbox.Pending(stored.getArgument(0), stored.getArgument(1),
                        stored.getArgument(3), stored.getArgument(4), stored.getArgument(5)));

        new KafkaMemeEvents(kafka, outbox).memeDeleted(memeId);

        ArgumentCaptor<ProducerRecord<String, String>> sent =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sent.capture());
        return sent.getValue();
    }
}
