package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.MemeErasure;
import com.jrobertgardzinski.memes.domain.MemeMetadata;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Watches the erasure backlog — and does nothing about it on purpose.
 *
 * <p>A meme is marked {@code PENDING_ERASURE} by the saga's first, reversible step, and only the
 * orchestrator's closure command turns that mark into a delete. So a mark that has stood for longer
 * than any saga can legitimately last means the closure command never arrived: the participant's
 * retry budget was spent (a broker outage outliving it), or the record was dropped. The content is
 * invisible to the world, which is what the leaver asked for, but it is still on disk, which is not
 * what the GDPR asked for — and nobody would ever find out, because the failure is silent by
 * construction: nothing is broken, nothing throws, a query simply returns fewer rows for ever.
 *
 * <p><strong>Why this class does not just erase them.</strong> It is the same question the ADR
 * settles: deleting on the passage of time means guessing what the orchestrator decided, and the
 * guess is wrong exactly when it is expensive — a saga stuck for an hour because a sibling
 * participant is down is a saga that may still COMPENSATE, and content erased on a timer cannot come
 * back. The state of the saga is knowable; this service just is not the one that knows it. So the
 * backlog becomes an alarm an operator acts on (re-drive the closure, or compensate), never an
 * expiry.
 *
 * <p>The gauge {@code memes_erasure_backlog} is the alert's source, and it is deliberately a gauge
 * and not a counter: the question is "how many obligations am I sitting on right now", and it must
 * fall back to zero on its own the moment the closure command finally lands.
 *
 * <p>The schedule hangs off {@code @EnableScheduling}, which this service switches on together with
 * the broker (the outbox config). That is the right coupling rather than an accident: without a
 * broker there is no saga, so there are no marks and nothing to watch.
 */
@Component
class StuckErasureWatch {

    private static final Logger LOG = LoggerFactory.getLogger(StuckErasureWatch.class);

    /**
     * How old a mark has to be before it is evidence of a lost command rather than of a saga still
     * running. Derived, not guessed: the orchestrator gives a purge {@code OFFBOARDING_PURGE_TIMEOUT_SEC}
     * (120s by default) and re-commands it {@code OFFBOARDING_PURGE_RETRIES} times (3), so the whole
     * case is decided within about eight minutes; thirty leaves room for a slow deployment's
     * backlog and still turns a genuinely lost closure into an alarm the same shift.
     */
    static final Duration DEFAULT_STUCK_AFTER = Duration.ofMinutes(30);

    private final MemeErasure erasure;
    private final Clock clock;
    private final Duration stuckAfter;
    private final AtomicLong backlog = new AtomicLong();

    @Autowired
    StuckErasureWatch(MemeErasure erasure, Clock clock, MeterRegistry meters,
                      @Value("${memes.erasure.stuck-after-seconds:1800}") long stuckAfterSeconds) {
        this(erasure, clock, meters, Duration.ofSeconds(stuckAfterSeconds));
    }

    StuckErasureWatch(MemeErasure erasure, Clock clock, MeterRegistry meters, Duration stuckAfter) {
        this.erasure = erasure;
        this.clock = clock;
        this.stuckAfter = stuckAfter;
        meters.gauge("memes.erasure.backlog", backlog, AtomicLong::get);
    }

    @Scheduled(fixedDelayString = "${memes.erasure.watch-interval-ms:60000}")
    void watch() {
        List<MemeMetadata> stuck;
        try {
            stuck = erasure.pendingSince(clock.instant().minus(stuckAfter));
        } catch (DataAccessException unreadable) {
            // the same judgement as PendingBlobDeleteSweep's: a register we cannot read is loud,
            // never fatal. Leaving the gauge where it was is the honest move — reporting zero would
            // turn a failed read into "the backlog is clear"
            LOG.error("could not read the erasure backlog — the gauge keeps its last value", unreadable);
            return;
        }
        backlog.set(stuck.size());
        if (stuck.isEmpty()) {
            return;   // the normal case: sagas close within minutes
        }
        // the OLDEST mark is what an operator needs to judge the severity, and an age is not PII —
        // the authors of these memes are exactly the people this service is trying to forget
        Instant oldest = stuck.getFirst().markedForErasureAt();
        LOG.warn("{} meme(s) have been marked for erasure for longer than {} — the oldest for {}."
                        + " Their account-deletion saga never sent its closure command, so this"
                        + " content is hidden but NOT erased. Nothing here will delete it on a"
                        + " timer: re-drive the saga from microservice-offboarding, or compensate it",
                stuck.size(), stuckAfter, Duration.between(oldest, clock.instant()));
    }
}
