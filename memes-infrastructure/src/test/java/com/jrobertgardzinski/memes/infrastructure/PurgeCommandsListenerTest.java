package com.jrobertgardzinski.memes.infrastructure;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrobertgardzinski.memes.application.MarkUserContentForErasure;
import com.jrobertgardzinski.memes.application.PurgeUserContent;
import com.jrobertgardzinski.memes.application.RestoreUserContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The listener's own guardrails (the happy paths are pinned by the Pact contract tests): a purge
 * command that names nobody must be dropped — no purge, and above all NO confirmation, because
 * confirming a deletion that never happened would advance the saga on a lie.
 *
 * <p>And what the listener WRITES DOWN while erasing someone. This is the service's GDPR path: a
 * line that names the leaver would outlive the erasure it reports (logs ship to Loki, which knows
 * nothing about the saga's retention), and a line that quotes the command back would let the
 * person being deleted dictate the operator's log file. Both are pinned here with a real appender.
 */
class PurgeCommandsListenerTest {

    private static final String LEAVER = "leaver@example.com";
    private static final String SAGA = "7d9f9e2a-1f0a-4f6e-9a1b-2c3d4e5f6a7b";

    private final MarkUserContentForErasure markForErasure = mock(MarkUserContentForErasure.class);
    private final RestoreUserContent restoreUserContent = mock(RestoreUserContent.class);
    private final PurgeUserContent purgeUserContent = mock(PurgeUserContent.class);
    private final PurgeConfirmations confirmations = mock(PurgeConfirmations.class);
    private final PurgeCommandsListener listener = new PurgeCommandsListener(markForErasure,
            restoreUserContent, purgeUserContent, confirmations, new ObjectMapper(),
            NoTransactions.template());

    private final Logger listenerLog = (Logger) LoggerFactory.getLogger(PurgeCommandsListener.class);
    private final ListAppender<ILoggingEvent> written = new ListAppender<>();

    @BeforeEach
    void captureTheLog() {
        written.start();
        listenerLog.addAppender(written);
    }

    @AfterEach
    void releaseTheLog() {
        listenerLog.detachAppender(written);
        written.stop();
    }

    @Test
    @DisplayName("a command with no email is dropped: no purge, no confirmation")
    void missing_email_is_dropped_without_confirmation() throws Exception {
        listener.receive("{\"type\":\"PURGE_USER_CONTENT\",\"sagaId\":\"" + SAGA + "\"}", null);

        verifyNoInteractions(markForErasure, restoreUserContent, purgeUserContent);
        verifyNoInteractions(confirmations);
    }

    @Test
    @DisplayName("a command with a blank email is dropped the same way")
    void blank_email_is_dropped_without_confirmation() throws Exception {
        listener.receive("{\"type\":\"PURGE_USER_CONTENT\",\"email\":\"\","
                + "\"sagaId\":\"" + SAGA + "\"}", null);

        verifyNoInteractions(markForErasure, restoreUserContent, purgeUserContent);
        verifyNoInteractions(confirmations);
    }

    @Test
    @DisplayName("a completed mark is logged by saga id — the leaver's address never reaches the log")
    void the_leavers_address_stays_out_of_the_log() throws Exception {
        listener.receive("{\"type\":\"PURGE_USER_CONTENT\",\"email\":\"" + LEAVER + "\","
                + "\"sagaId\":\"" + SAGA + "\"}", null);

        verify(markForErasure).execute(LEAVER);
        assertNothingLoggedContains(LEAVER);
        assertTrue(logLines().stream().anyMatch(line -> line.contains(SAGA)),
                "the saga id is what identifies the run in the log: " + logLines());
    }

    @Test
    @DisplayName("a completed mark confirms the SAME saga it was commanded for — and erases nothing")
    void a_completed_purge_confirms_its_own_saga() throws Exception {
        listener.receive("{\"type\":\"PURGE_USER_CONTENT\",\"email\":\"" + LEAVER + "\","
                + "\"sagaId\":\"" + SAGA + "\"}", null);

        InOrder order = inOrder(markForErasure, confirmations);
        // the mark first, the promise to report it second, both inside one transaction: a
        // confirmation announced before the mark would be a lie the outbox then made durable
        order.verify(markForErasure).execute(LEAVER);
        order.verify(confirmations).confirm(SAGA, LEAVER);
        // and the point of the whole two-phase design: the command the orchestrator can still take
        // back destroys nothing
        verifyNoInteractions(purgeUserContent);
    }

    @Test
    @DisplayName("the closure erases, and is NOT confirmed — the orchestrator has already decided")
    void the_closure_erases_what_the_mark_reserved() throws Exception {
        listener.receive("{\"type\":\"ERASE_USER_CONTENT\",\"email\":\"" + LEAVER + "\","
                + "\"sagaId\":\"" + SAGA + "\"}", null);

        verify(purgeUserContent).execute(LEAVER, Optional.empty());
        verifyNoInteractions(markForErasure, restoreUserContent);
        verifyNoInteractions(confirmations);
        assertNothingLoggedContains(LEAVER);
    }

    @Test
    @DisplayName("the compensation restores, erases nothing and is not confirmed either")
    void the_compensation_restores() throws Exception {
        listener.receive("{\"type\":\"RESTORE_USER_CONTENT\",\"email\":\"" + LEAVER + "\","
                + "\"sagaId\":\"" + SAGA + "\"}", null);

        verify(restoreUserContent).execute(LEAVER);
        verifyNoInteractions(markForErasure, purgeUserContent);
        verifyNoInteractions(confirmations);
        assertNothingLoggedContains(LEAVER);
    }

    @Test
    @DisplayName("a command type this participant does not know is ignored, not guessed at")
    void an_unknown_command_type_is_ignored() throws Exception {
        listener.receive("{\"type\":\"SOMETHING_ELSE\",\"email\":\"" + LEAVER + "\","
                + "\"sagaId\":\"" + SAGA + "\"}", null);

        verifyNoInteractions(markForErasure, restoreUserContent, purgeUserContent);
        verifyNoInteractions(confirmations);
    }

    @Test
    @DisplayName("a mark that fails confirms nothing and lets the failure out — so Kafka redelivers")
    void a_failed_purge_confirms_nothing() {
        doThrow(new IllegalStateException("the store is down"))
                .when(markForErasure).execute(LEAVER);

        assertThrows(IllegalStateException.class, () ->
                listener.receive("{\"type\":\"PURGE_USER_CONTENT\",\"email\":\"" + LEAVER + "\","
                        + "\"sagaId\":\"" + SAGA + "\"}", null));

        // the failure must reach the container: that is what makes SagaRetryBudget retry the record
        // instead of the offset being committed over a purge that did not happen
        verifyNoInteractions(confirmations);
        assertNothingLoggedContains(LEAVER);
    }

    @Test
    @DisplayName("an unparseable rule is logged by shape and size — never quoted back from the wire")
    void an_unparseable_rule_cannot_write_into_the_log() throws Exception {
        // the rule text is whatever the leaver typed into their deletion request: the wizard is
        // one client of that API, curl is another. Newlines in it used to become NEW LOG LINES,
        // so an operator could be shown a fabricated "ERROR" from the memes service.
        String forged = "DELETE\nERROR memes-service: seized by " + LEAVER + "\n";
        String rule = forged.repeat(100);
        // the policy rides the CLOSURE now — that is the command whose use case reads the rule
        listener.receive("{\"type\":\"ERASE_USER_CONTENT\",\"email\":\"" + LEAVER + "\","
                + "\"sagaId\":\"" + SAGA + "\",\"policy\":{\"memes\":\""
                + rule.replace("\n", "\\n") + "\"}}", null);

        // the erasure still runs, on the deployment default — an unreadable rule must not wedge the saga
        verify(purgeUserContent).execute(LEAVER, Optional.empty());
        assertNothingLoggedContains(LEAVER);
        assertNothingLoggedContains("seized by");
        assertFalse(logLines().stream().anyMatch(line -> line.contains("\n")),
                "no log line may carry a newline from the wire: " + logLines());
        assertTrue(logLines().stream().anyMatch(line -> line.contains(String.valueOf(rule.length()))),
                "the size is what an investigator gets instead of the text: " + logLines());
    }

    @Test
    @DisplayName("a malformed command is logged by its size only — the payload carries an address")
    void a_malformed_command_is_logged_by_size_only() throws Exception {
        String broken = "{not json, but it still names " + LEAVER;
        listener.receive(broken, null);

        verifyNoInteractions(markForErasure, restoreUserContent, purgeUserContent);
        verifyNoInteractions(confirmations);
        assertNothingLoggedContains(LEAVER);
        assertTrue(logLines().stream().anyMatch(line -> line.contains(String.valueOf(broken.length()))),
                "the size is enough to investigate: " + logLines());
    }

    private List<String> logLines() {
        return written.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private void assertNothingLoggedContains(String forbidden) {
        assertFalse(logLines().stream().anyMatch(line -> line.contains(forbidden)),
                "\"" + forbidden + "\" must not appear in the log: " + logLines());
    }
}
