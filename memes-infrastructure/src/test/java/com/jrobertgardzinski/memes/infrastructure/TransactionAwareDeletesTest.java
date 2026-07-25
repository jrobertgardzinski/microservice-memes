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

    @Autowired
    javax.sql.DataSource dataSource;

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

    @Test
    @DisplayName("a delete issued from INSIDE an after-commit runnable executes — it must not park into the void")
    void a_registration_from_inside_after_commit_still_executes() {
        // The trap: Spring iterates a SNAPSHOT of the synchronizations, so a registration made
        // from within an afterCommit callback is never invoked and vanishes in the cleanup.
        // This is a real path, not a curiosity — the repository's after-commit variant re-sweep
        // calls ObjectStore.delete, which on filesystem/S3 parks itself again. The phase-aware
        // re-entry must run it inline instead of losing it silently.
        List<String> ran = new ArrayList<>();

        tx.executeWithoutResult(status ->
                TransactionAwareDeletes.afterCommitOrNow(() -> {
                    ran.add("outer");
                    TransactionAwareDeletes.afterCommitOrNow(() -> ran.add("inner"));
                }));

        assertEquals(List.of("outer", "inner"), ran,
                "the delete issued inside the after-commit phase must have executed, not parked forever");
    }

    @Test
    @DisplayName("even an Error from one runnable neither cuts down the others nor escapes tx.execute")
    void a_runnable_throwing_an_error_does_not_block_the_others() {
        // RuntimeException isolation alone is not enough: an Error (OOM while shovelling image
        // bytes, an assertion) thrown by the first synchronization would make Spring stop
        // invoking the REST — including the parked MEME_DELETED publication. After the commit
        // there is no caller left to tell, so the wrapper swallows Throwable and logs.
        List<String> ran = new ArrayList<>();

        assertDoesNotThrow(() -> tx.executeWithoutResult(status -> {
            TransactionAwareDeletes.afterCommitOrNow(() -> {
                ran.add("first");
                throw new AssertionError("catastrophe after commit");
            });
            TransactionAwareDeletes.afterCommitOrNow(() -> ran.add("second"));
        }));

        assertEquals(List.of("first", "second"), ran,
                "the second parked delete must run even though the first one threw an Error");
    }

    @Test
    @DisplayName("the pool hands connections back with autocommit ON — the after-commit sweeps depend on it")
    void the_datasource_keeps_hikaris_autocommit_default() {
        // The after-commit variant sweep (JdbcMemeRepository.deleteById) runs its DELETE on a
        // fresh pool connection OUTSIDE any Spring transaction: it only ever commits because
        // Hikari restores autocommit=true on checkout (spring.datasource.hikari.auto-commit
        // default). Were someone to flip that property, the sweep would roll back silently when
        // the connection returns to the pool — this pin makes such a change fail a test instead.
        assertTrue(dataSource instanceof com.zaxxer.hikari.HikariDataSource,
                "the pool the assumption is about — if this changes, re-audit the after-commit sweeps");
        assertTrue(((com.zaxxer.hikari.HikariDataSource) dataSource).isAutoCommit(),
                "auto-commit must stay true or every after-commit DELETE silently rolls back");
    }
}
