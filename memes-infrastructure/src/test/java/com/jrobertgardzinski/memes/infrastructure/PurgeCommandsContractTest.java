package com.jrobertgardzinski.memes.infrastructure;

import au.com.dius.pact.consumer.MessagePactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.consumer.junit5.ProviderType;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.annotations.Pact;
import au.com.dius.pact.core.model.messaging.Message;
import au.com.dius.pact.core.model.messaging.MessagePact;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrobertgardzinski.memes.application.PurgeUserContent;
import com.jrobertgardzinski.memes.config.PurgeRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The consumer's half of the account-deletion saga contract: the pact states the exact shape of
 * the {@code content-commands} event this service acts on — with and without the leaver's explicit
 * policy — and proves it by driving the real listener with the pact's payload. The generated pact
 * (../pacts, committed) is verified against the REAL orchestrator by microservice-offboarding's
 * provider tests. Only the fields this consumer reads are in the contract; the producer may add
 * more (tolerant reader).
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "microservice-offboarding", providerType = ProviderType.ASYNCH,
        pactVersion = PactSpecVersion.V3)
class PurgeCommandsContractTest {

    private final PurgeUserContent purgeUserContent = mock(PurgeUserContent.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final PurgeCommandsListener listener =
            new PurgeCommandsListener(purgeUserContent, kafka, new ObjectMapper());

    @Pact(consumer = "microservice-memes")
    MessagePact purgeCommand(MessagePactBuilder builder) {
        return builder.expectsToReceive("a purge user content command")
                .withContent(new PactDslJsonBody()
                        .stringValue("type", "PURGE_USER_CONTENT")
                        .uuid("sagaId")
                        .stringType("email", "leaver@example.com"))
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "purgeCommand")
    void purgesWithTheDeploymentDefault(List<Message> messages) throws Exception {
        listener.receive(messages.get(0).contentsAsString(), null);
        verify(purgeUserContent).execute("leaver@example.com", Optional.empty());
    }

    @Pact(consumer = "microservice-memes")
    MessagePact purgeCommandWithPolicy(MessagePactBuilder builder) {
        return builder.expectsToReceive("a purge user content command with an explicit policy")
                .withContent(new PactDslJsonBody()
                        .stringValue("type", "PURGE_USER_CONTENT")
                        .uuid("sagaId")
                        .stringType("email", "leaver@example.com")
                        .object("policy")
                        .stringType("memes", "DELETE")
                        .closeObject())
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "purgeCommandWithPolicy")
    void purgesWithTheLeaversChoice(List<Message> messages) throws Exception {
        listener.receive(messages.get(0).contentsAsString(), null);
        verify(purgeUserContent).execute("leaver@example.com", Optional.of(new PurgeRule.Delete()));
    }
}
