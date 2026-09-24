package com.jrobertgardzinski.memes.closure;

import java.util.Optional;

/**
 * One command of the account-closure saga, as this service needs to read it — already parsed, so
 * that nothing below this point knows what carried it.
 *
 * <p>The fields are the agreement's ({@code com.jrobertgardzinski.closure.ClosureMessages.Field}),
 * not this service's: a participant that renamed them would still work and would still be wrong.
 * {@code memesRule} is the one field that IS ours — the leaver's stored choice for this axis —
 * and it arrives as text because the deployment, not the sender, has the last word on what an
 * unreadable rule means.
 *
 * @param type        the command's name on the wire
 * @param sagaId      the orchestrator's handle on this closure; the only safe thing to log
 * @param email       whose account is closing — PII, and never written to a log
 * @param initiatedBy who asked, as a word; decides whether conditions may be honoured at all
 * @param memesRule   the purge rule stated for the memes axis, if the command carried one
 */
public record ClosureCommand(String type, String sagaId, String email, String initiatedBy,
                             Optional<String> memesRule) {
}
