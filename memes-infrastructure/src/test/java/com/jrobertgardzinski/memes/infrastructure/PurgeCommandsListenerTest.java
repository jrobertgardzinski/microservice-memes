package com.jrobertgardzinski.memes.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrobertgardzinski.memes.application.PurgeUserContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The listener's own guardrails (the happy paths are pinned by the Pact contract tests): a purge
 * command that names nobody must be dropped — no purge, and above all NO confirmation, because
 * confirming a deletion that never happened would advance the saga on a lie.
 */
class PurgeCommandsListenerTest {

    private final PurgeUserContent purgeUserContent = mock(PurgeUserContent.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final PurgeCommandsListener listener =
            new PurgeCommandsListener(purgeUserContent, kafka, new ObjectMapper());

    @Test
    @DisplayName("a command with no email is dropped: no purge, no confirmation")
    void missing_email_is_dropped_without_confirmation() throws Exception {
        listener.receive("{\"type\":\"PURGE_USER_CONTENT\","
                + "\"sagaId\":\"7d9f9e2a-1f0a-4f6e-9a1b-2c3d4e5f6a7b\"}", null);

        verifyNoInteractions(purgeUserContent);
        verifyNoInteractions(kafka);
    }

    @Test
    @DisplayName("a command with a blank email is dropped the same way")
    void blank_email_is_dropped_without_confirmation() throws Exception {
        listener.receive("{\"type\":\"PURGE_USER_CONTENT\",\"email\":\"\","
                + "\"sagaId\":\"7d9f9e2a-1f0a-4f6e-9a1b-2c3d4e5f6a7b\"}", null);

        verifyNoInteractions(purgeUserContent);
        verifyNoInteractions(kafka);
    }
}
