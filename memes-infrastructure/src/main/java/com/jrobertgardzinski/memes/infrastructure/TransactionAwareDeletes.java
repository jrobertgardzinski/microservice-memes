package com.jrobertgardzinski.memes.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Blob stores that live OUTSIDE the database (filesystem, S3) cannot join the DB transaction the
 * delete/purge seam opens ({@link TransactionalDeleteMeme}, {@link TransactionalPurgeUserContent}).
 * Deleting their object eagerly inside that transaction would be wrong in the rollback case: the
 * meme row comes back, its bytes do not. So a delete issued while a transaction is active is
 * parked and runs after the commit; a rollback simply drops it — the object stays with its row.
 * Outside a transaction (e.g. the WebP cache) the delete runs immediately, as before.
 *
 * <p>The same parking serves every commit-dependent side effect of a teardown, not only blob
 * deletes: {@link JdbcMemeRepository} re-sweeps the WebP variant here, and {@link KafkaMemeEvents}
 * parks the MEME_DELETED publication so a rollback cannot un-delete a meme whose event already
 * left the building.
 *
 * <p>One trap this class must dodge itself: a parked runnable may call back into
 * {@link #afterCommitOrNow} — the repository's after-commit variant re-sweep calls
 * {@code objects.delete}, and on filesystem/S3 that delete parks again. Spring iterates a
 * SNAPSHOT of the registered synchronizations, so a registration made from inside an afterCommit
 * callback is never invoked (and vanishes silently in the cleanup). The {@link #IN_AFTER_COMMIT}
 * flag marks the window where our own callbacks run; a delete issued inside it executes inline —
 * the commit has already happened, which is exactly the state "after commit" promises.
 */
final class TransactionAwareDeletes {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionAwareDeletes.class);

    /** True while THIS thread is inside one of our afterCommit callbacks — see the class doc. */
    private static final ThreadLocal<Boolean> IN_AFTER_COMMIT = ThreadLocal.withInitial(() -> false);

    private TransactionAwareDeletes() {
    }

    static void afterCommitOrNow(Runnable delete) {
        if (IN_AFTER_COMMIT.get()) {
            // registering now would be a silent no-op (Spring already snapshotted the list), and
            // the commit is a fact — run inline; the enclosing callback's isolation catch keeps
            // a failure here from cutting down the other parked runnables
            delete.run();
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // Best-effort, isolated — like PublishMeme's deleteUploadedBytesBestEffort:
                    // the DB commit has already happened, so a failing side effect must neither
                    // surface out of tx.execute nor cut down the OTHER parked runnables (Spring
                    // stops invoking synchronizations at the first exception). Throwable, not
                    // RuntimeException: an Error (OOM in an image buffer, an assertion) would
                    // otherwise abort the remaining synchronizations too — including the parked
                    // MEME_DELETED publication. Swallowing an Error is normally taboo, but after
                    // the commit there is no caller left to tell; isolation wins, the log gets
                    // the truth, and a JVM sick enough for the Error to matter will fail the
                    // next request on its own.
                    IN_AFTER_COMMIT.set(true);
                    try {
                        delete.run();
                    } catch (Throwable sideEffectFailed) {
                        LOG.warn("after-commit action failed — the transaction is committed, "
                                + "continuing with the remaining ones", sideEffectFailed);
                    } finally {
                        IN_AFTER_COMMIT.remove();
                    }
                }
            });
            return;
        }
        delete.run();
    }
}
