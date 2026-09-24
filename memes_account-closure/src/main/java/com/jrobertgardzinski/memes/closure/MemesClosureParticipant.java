package com.jrobertgardzinski.memes.closure;

import com.jrobertgardzinski.closure.ClosureCommand;
import com.jrobertgardzinski.closure.ClosureMessages;
import com.jrobertgardzinski.memes.application.MarkUserContentForErasure;
import com.jrobertgardzinski.memes.application.PurgeUserContent;
import com.jrobertgardzinski.memes.application.RestoreUserContent;
import com.jrobertgardzinski.purge.PurgeRule;
import com.jrobertgardzinski.memes.domain.Observation;
import com.jrobertgardzinski.observation.Observations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The meme service's side of the account-closure saga: a participant in TWO phases, which is what
 * makes the saga compensatable at all.
 *
 * <ul>
 *   <li>{@code PURGE_USER_CONTENT} — the reversible step: the leaver's memes are MARKED
 *       ({@link MarkUserContentForErasure}), which takes them out of every public read and
 *       destroys nothing, and a confirmation goes back. The name on the wire is unchanged on
 *       purpose: the orchestrator's contract with its participants is "make this leaver's content
 *       go away and tell me when". What changed is what "go away" costs to take back.</li>
 *   <li>{@code ERASE_USER_CONTENT} — the closure: every participant has confirmed, the saga
 *       cannot fail any more, and {@link PurgeUserContent} destroys what the rule says to destroy.
 *       This is the command that crosses the pivot.</li>
 *   <li>{@code RESTORE_USER_CONTENT} — the compensation: a sibling participant failed, so the
 *       marks come off ({@link RestoreUserContent}) and the memes are back in the gallery.</li>
 * </ul>
 *
 * <p>All three are idempotent, so at-least-once delivery needs no extra dedup. Only the first is
 * confirmed: the closure and the compensation are the orchestrator ENDING the case, and a
 * participant answering them would only tell it something it has already decided. What guards
 * them instead is retrying, and — for a closure lost beyond the retry budget — the backlog of
 * marks nobody closed being visible and alarmed on rather than silently swept. Both of those are
 * the carrier's job and live with the adapter.
 *
 * <p><strong>Why this is a module of its own and not a class in the adapter.</strong> Everything
 * above is true of the memes axis whether the commands arrive over Kafka or as a method call in a
 * single process. Nothing in here knows which it is, so both assemblies run the SAME decisions —
 * and the flow can be read, and tested, before anyone picks one.
 */
public final class MemesClosureParticipant {

    private static final Logger LOG = LoggerFactory.getLogger(MemesClosureParticipant.class);

    /** The reversible mark; its confirmation is what the orchestrator's quorum counts. */
    public static final String MARK = ClosureMessages.PURGE_USER_CONTENT;
    /** The closure: past this command the saga has nothing left to compensate with. */
    public static final String ERASE = ClosureMessages.ERASE_USER_CONTENT;
    /** The compensation: the marks come off and the content is public again. */
    public static final String RESTORE = ClosureMessages.RESTORE_USER_CONTENT;

    private final MarkUserContentForErasure markForErasure;
    private final RestoreUserContent restoreUserContent;
    private final PurgeUserContent purgeUserContent;
    private final ClosureConfirmations confirmations;
    private final Observations<Observation> observations;
    private final Atomically atomically;

    public MemesClosureParticipant(MarkUserContentForErasure markForErasure,
                                   RestoreUserContent restoreUserContent,
                                   PurgeUserContent purgeUserContent,
                                   ClosureConfirmations confirmations,
                                   Observations<Observation> observations,
                                   Atomically atomically) {
        this.markForErasure = markForErasure;
        this.restoreUserContent = restoreUserContent;
        this.purgeUserContent = purgeUserContent;
        this.confirmations = confirmations;
        this.observations = observations;
        this.atomically = atomically;
    }

    /** What this service does about one command of a closing account. */
    public ClosureOutcome handle(ClosureCommand command) {
        String type = command.type();
        if (!MARK.equals(type) && !ERASE.equals(type) && !RESTORE.equals(type)) {
            return new ClosureOutcome.NotOurs(type);
        }
        String sagaId = command.sagaId();
        if (!command.isAddressed()) {
            // a command keyed by NOBODY would "succeed" instantly — and for the mark it would
            // confirm a deletion that never happened, advancing the saga on a lie. Dropped WITHOUT
            // confirming: the command is malformed at the source, and the orchestrator's timeout
            // is the honest signal.
            LOG.warn("dropping {} without an email (saga {})", type, sagaId);
            return new ClosureOutcome.Unaddressed(type);
        }
        String email = command.email();
        return switch (type) {
            case MARK -> {
                int reserved = markAndConfirm(sagaId, email);
                // the saga id identifies the run in logs; the e-mail is PII and stays out of INFO
                // lines — writing it here would outlive the erasure this very line reports (logs
                // ship to Loki, which knows nothing about the saga's 30-day retention). The count
                // is not PII and is the difference between "it worked" and "it found nobody"
                LOG.info("marked {} of one leaver's memes for erasure (saga {})", reserved, sagaId);
                yield new ClosureOutcome.Reserved(reserved);
            }
            case ERASE -> {
                // the rule is resolved BEFORE the step opens: it is pure reading, it can say so
                // loudly about an unreadable rule, and none of that belongs inside the unit of
                // work that destroys things. It rides the CLOSURE, not the mark, because the rule
                // reads vote scores and the scores are only final once the leaver's own votes are
                // retracted — which is part of the erasure itself (see PurgeUserContent)
                Optional<PurgeRule> rule = requestedRule(command);
                atomically.run(() -> purgeUserContent.execute(email, rule));
                LOG.info("erased one leaver's marked memes on the saga's closure (saga {})", sagaId);
                yield new ClosureOutcome.Erased();
            }
            case RESTORE -> {
                atomically.run(() -> restoreUserContent.execute(email));
                LOG.info("restored one leaver's marked memes: the saga compensated (saga {})", sagaId);
                yield new ClosureOutcome.Restored();
            }
            default -> throw new IllegalStateException("unreachable: " + type);
        };
    }

    /**
     * The mark and the promise to report it, as ONE step. A failure anywhere inside propagates to
     * the caller, which is what lets the carrier retry the command — the mark being idempotent,
     * running the whole thing again is safe.
     *
     * <p>The confirmation is made INSIDE, not after: confirming after the step committed would
     * leave a window where the memes are hidden and nothing owes the orchestrator a word about it,
     * which is the failure mode that ends with the leaver holding a restored account and invisible
     * content.
     *
     * <p>Note what this atomicity is now worth: before the two phases, the same guarantee still
     * left the orchestrator holding a confirmation for content that was already destroyed, so a
     * LATER participant's failure had nothing to undo. Now the confirmation says "reserved", and
     * reserving is a thing that can be given back.
     *
     * <p><strong>And it says how much it reserved.</strong> The confirmation used to go out
     * unconditionally, so a mark that matched nothing was reported in exactly the same words as
     * one that took forty memes out of the gallery — which is how a leaver who had changed their
     * address got a completed deletion with every image still public. A zero raises
     * {@link Observation.PurgeReservedNothing} and a WARN, because this is the one thing the
     * service cannot resolve on its own: "I hold nothing of theirs" and "their rows are under the
     * address they had yesterday and the rename has not reached me yet" are the same observation
     * from in here, and the address on the command is all there is to go on.
     */
    private int markAndConfirm(String sagaId, String email) {
        AtomicInteger reserved = new AtomicInteger();
        atomically.run(() -> {
            int marked = markForErasure.execute(email);
            confirmations.confirm(sagaId, email, marked);
            reserved.set(marked);
        });
        if (reserved.get() == 0) {
            observations.record(new Observation.PurgeReservedNothing());
            LOG.warn("confirmed a purge that reserved NOTHING (saga {}): either this member never"
                    + " uploaded anything, or their memes are still keyed by an address they have"
                    + " changed and the rename has not been consumed yet", sagaId);
        }
        return reserved.get();
    }

    /**
     * The rule for THIS service's axis (the memes rule), and the one gate that is not a matter of
     * configuration.
     *
     * <p>A closure the OWNER asked for resolves to {@link PurgeRule.Delete}, stated rather than
     * left absent, and whatever the command carried is discarded. The difference matters because
     * absent means "decide for me" — it lets the operator's runtime override and then the
     * deployment default have their say (see {@code PurgeUserContent}), and an operator who has
     * dialled in "keep the popular ones" would then keep the content of somebody who asked to be
     * forgotten. There is no exception to that right for content the community happens to like, so
     * this is not a dial and not a default: it is the answer.
     *
     * <p>An administrator's closure is an ordinary business decision, so its rule is read from the
     * command as it always was; unparseable rules fall back to the deployment default (logged)
     * rather than wedging the saga.
     */
    private Optional<PurgeRule> requestedRule(ClosureCommand command) {
        // the command answers it, not a constant of ours: the one word that licenses conditions
        // is the agreement's, and its reading is deliberately not symmetrical — a missing field,
        // an empty one or a word this service has never heard of is a closure the OWNER asked for
        if (!command.allowsConditions()) {
            if (command.rule().isPresent()) {
                // a producer that states conditions on a self-closure is broken, not permissive:
                // say so loudly and destroy anyway — the alternative is a silent policy breach
                LOG.warn("a self-requested closure arrived carrying a memes purge rule; ignoring it"
                        + " and deleting — conditions are an administrator's to state, never the"
                        + " leaver's");
            }
            return Optional.of(new PurgeRule.Delete());
        }
        if (command.rule().isEmpty()) {
            return Optional.empty();
        }
        String text = command.rule().get();
        try {
            return Optional.of(PurgeRule.parse(text));
        } catch (IllegalArgumentException invalid) {
            // invalid.getMessage() is safe to log now, and that is the whole point of having moved
            // the vocabulary into the library: the refusal states the length and the SHAPE, never
            // the text, so a new caller cannot reintroduce the leak by logging the obvious thing
            LOG.warn("ignoring an unparseable memes purge rule, using the default: {}",
                    invalid.getMessage());
            return Optional.empty();
        }
    }
}
