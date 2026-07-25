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
 */
final class TransactionAwareDeletes {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionAwareDeletes.class);

    private TransactionAwareDeletes() {
    }

    static void afterCommitOrNow(Runnable delete) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // Best-effort, isolated — like PublishMeme's deleteUploadedBytesBestEffort:
                    // the DB commit has already happened, so a failing side effect must neither
                    // surface out of tx.execute nor cut down the OTHER parked runnables (Spring
                    // stops invoking synchronizations at the first exception). Log and move on.
                    try {
                        delete.run();
                    } catch (RuntimeException sideEffectFailed) {
                        LOG.warn("after-commit action failed — the transaction is committed, "
                                + "continuing with the remaining ones", sideEffectFailed);
                    }
                }
            });
            return;
        }
        delete.run();
    }
}
