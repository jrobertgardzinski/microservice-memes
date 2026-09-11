package com.jrobertgardzinski.memes.domain;

import java.time.Duration;

/**
 * Something this service has noticed about itself and considers worth saying out loud — a domain
 * probe, in the sense Pete Hodgson gave the term: the code states a fact in the language of the
 * business, and whatever is watching translates it into its own.
 *
 * <p>The catalogue is sealed because it is a vocabulary, not an extension point: every fact here is
 * a sentence about account deletion that <strong>no tool could derive on its own</strong>. A tracing
 * agent knows a method took 40ms; nothing outside this service can know that memes are sitting
 * marked-but-not-erased, because that is a conclusion drawn from this service's own rules.
 *
 * <p>That is also the line this type is FOR. Timings, spans, retries, queue depths and error rates
 * are the watching tool's business and never appear here — they arrive by instrumentation, without
 * a line of ours, and they are spelled in whatever vocabulary this year's tool uses. What is in
 * here is what survives replacing that tool.
 */
public sealed interface Observation {

    /**
     * Memes reserved by a deletion saga whose closure never came: hidden from every reader, still
     * on disk. {@code oldest} is how long the oldest such mark has stood — an age, and therefore
     * not personal data, which matters because the people behind these memes are exactly the ones
     * this service is trying to forget.
     *
     * <p>Stated on EVERY pass, zero included: the question it answers is "how many obligations am I
     * sitting on right now", so silence would leave a stale answer standing.
     */
    record ErasureBacklog(int marked, Duration oldest) implements Observation {

        public static final ErasureBacklog NONE = new ErasureBacklog(0, Duration.ZERO);
    }

    /**
     * One saga command this service will now never carry out: the retry budget ran out with the
     * database or the broker still unreachable. Whether that is survivable depends on which command
     * it was — a lost mark ends in the orchestrator compensating, a lost closure ends in content
     * hidden for ever — and the service cannot tell which, so it says what it knows.
     */
    record SagaCommandDropped(String topic) implements Observation {
    }
}
