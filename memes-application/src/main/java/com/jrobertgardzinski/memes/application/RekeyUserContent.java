package com.jrobertgardzinski.memes.application;

/**
 * A member changed their e-mail address, and their content follows them.
 *
 * <p>This service stores the author as the address the token carried at upload time, and votes as
 * the address the voter had when they cast them. Nothing used to rewrite either, so a confirmed
 * address change made a person a stranger to their own work: the gallery showed {@code own:false},
 * a delete came back 403, and — the part that cost more than a wrong flag — an account deletion
 * marked nothing, confirmed the erasure anyway and left the images in the public gallery, where the
 * next registrant of the freed old address inherited the authorship and the rights that come with
 * it. Re-keying the rows is what makes the address a NAME for the person rather than the person
 * themselves.
 *
 * <p><strong>Idempotent</strong> by arithmetic rather than by bookkeeping, which is why there is no
 * dedup table here: the second delivery of the same rename finds no row under the old address and
 * moves nothing. The fact is at-least-once, like every other fact in this estate, and this is what
 * it costs to absorb that — one UPDATE that matches nothing.
 *
 * <p><strong>What it deliberately does NOT do: wait, or check.</strong> There is no verification
 * that the new address is free of rows, because there cannot be any: microservice-security refuses
 * a move onto a registered address, and a deletion takes its owner's rows with it. And there is no
 * ordering promise against the deletion saga — the rename travels on {@code security-events} while
 * the purge commands travel on {@code content-commands}, so a deletion requested seconds after a
 * rename can still overtake this. That window is the producer's documented, accepted limit (the
 * durable answer is keying the estate on a stable user id), and what this service does about it is
 * to stop CLAIMING the erasure happened — see {@code PurgeCommandsListener}.
 */
public class RekeyUserContent {

    private final UserContentRekey rekey;

    public RekeyUserContent(UserContentRekey rekey) {
        this.rekey = rekey;
    }

    /** Returns how many rows moved — for the log line; nothing branches on it. */
    public int execute(String oldEmail, String newEmail) {
        return rekey.moveTo(oldEmail, newEmail);
    }
}
