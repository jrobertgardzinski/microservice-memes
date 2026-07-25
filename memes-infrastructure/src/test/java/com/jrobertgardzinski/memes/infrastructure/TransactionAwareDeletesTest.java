package com.jrobertgardzinski.memes.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the parking rules of {@link TransactionAwareDeletes}: inside a transaction a side effect
 * waits for the commit (and a rollback drops it), outside one it runs immediately — and once the
 * commit has happened every parked runnable is best-effort in isolation: one blowing up must
 * neither abort the others nor surface out of {@code tx.execute}.
 */
@SpringBootTest(classes = MemesApplication.class)
class TransactionAwareDeletesTest {

    @Autowired
    TransactionTemplate tx;

    @Test
    @DisplayName("inside a transaction the delete is parked until the commit, not run eagerly")
    void inside_a_transaction_the_delete_waits_for_the_commit() {
        List<String> ran = new ArrayList<>();

        tx.executeWithoutResult(status -> {
            TransactionAwareDeletes.afterCommitOrNow(() -> ran.add("delete"));
            assertTrue(ran.isEmpty(), "the delete must not run before the transaction commits");
        });

        assertEquals(List.of("delete"), ran, "after the commit the parked delete has run");
    }

    @Test
    @DisplayName("a rollback drops the parked delete — the object stays with its restored row")
    void a_rollback_drops_the_parked_delete() {
        List<String> ran = new ArrayList<>();

        tx.executeWithoutResult(status -> {
            TransactionAwareDeletes.afterCommitOrNow(() -> ran.add("delete"));
            status.setRollbackOnly();
        });

        assertTrue(ran.isEmpty(), "a rolled-back transaction must not fire its parked deletes");
    }

    @Test
    @DisplayName("one after-commit runnable blowing up neither cuts down the next nor escapes tx.execute")
    void a_failing_runnable_does_not_block_the_others_nor_the_caller() {
        List<String> ran = new ArrayList<>();

        assertDoesNotThrow(() -> tx.executeWithoutResult(status -> {
            TransactionAwareDeletes.afterCommitOrNow(() -> {
                ran.add("first");
                throw new IllegalStateException("store hiccup after commit");
            });
            TransactionAwareDeletes.afterCommitOrNow(() -> ran.add("second"));
        }), "the transaction IS committed — a failed after-commit cleanup must not look like a failed delete");

        assertEquals(List.of("first", "second"), ran,
                "the second parked delete must run even though the first one threw");
    }

    @Test
    @DisplayName("outside a transaction the delete runs immediately")
    void outside_a_transaction_the_delete_runs_now() {
        List<String> ran = new ArrayList<>();

        TransactionAwareDeletes.afterCommitOrNow(() -> ran.add("delete"));

        assertEquals(List.of("delete"), ran);
    }
}
