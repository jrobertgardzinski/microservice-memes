package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.MemeRepository;
import com.jrobertgardzinski.memes.application.ObjectStore;
import com.jrobertgardzinski.memes.domain.Meme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deleting a meme must also take its cached WebP variant along ({@code {id}.webp}, written by
 * ServeMeme) — otherwise the encoded copy of a "deleted" image lives in the object store forever,
 * which for a purge is a data-retention hole, not just litter.
 */
@SpringBootTest(classes = MemesApplication.class)
class MemeDeletionCleanupTest {

    @Autowired
    MemeRepository memes;

    @Autowired
    ObjectStore objects;

    @Autowired
    org.springframework.transaction.support.TransactionTemplate tx;

    @Test
    @DisplayName("deleteById removes the stored bytes AND the cached WebP variant")
    void delete_takes_the_webp_variant_along() {
        memes.save(new Meme("doomed", "author@example.com", "png", new byte[]{1, 2, 3}));
        objects.put("doomed.webp", "RIFF....WEBP".getBytes());

        memes.deleteById("doomed");

        assertTrue(objects.get("doomed").isEmpty(), "the original bytes are gone");
        assertTrue(objects.get("doomed.webp").isEmpty(), "the cached WebP variant is gone too");
    }

    @Test
    @DisplayName("exists() follows the row through save and delete — ServeMeme's orphan guard relies on it")
    void exists_follows_save_and_delete() {
        assertFalse(memes.exists("fleeting"), "never saved — must not exist");

        memes.save(new Meme("fleeting", "author@example.com", "png", new byte[]{9}));
        assertTrue(memes.exists("fleeting"));

        memes.deleteById("fleeting");
        assertFalse(memes.exists("fleeting"),
                "after the delete the light row check must say gone, or a racing WebP cache write"
                        + " would believe the meme still exists and leave its variant orphaned");
    }

    @Test
    @DisplayName("a WebP variant cached DURING the delete transaction is swept after its commit")
    void variant_written_inside_the_delete_window_is_swept_after_commit() throws Exception {
        // The race on the default DB store: deleteById's in-transaction sweep runs, THEN a
        // concurrent ServeMeme finishes encoding and caches the variant (its exists() re-check
        // still sees the uncommitted row, so it keeps the variant), THEN the delete commits.
        // Before the after-commit re-sweep this variant was orphaned forever. The interleaving is
        // pinned with a TransactionTemplate: the variant lands from a second thread (its own
        // autocommit connection) after the deleteById steps but before the commit.
        memes.save(new Meme("raced", "author@example.com", "png", new byte[]{4, 5, 6}));
        var cacheWriter = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            tx.executeWithoutResult(status -> {
                memes.deleteById("raced");   // joins this transaction; in-tx sweep sees no variant
                try {
                    cacheWriter.submit(() -> objects.put("raced.webp", "RIFF....WEBP".getBytes()))
                            .get(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception raceSetupFailed) {
                    throw new IllegalStateException("could not stage the racing cache write", raceSetupFailed);
                }
            });
        } finally {
            cacheWriter.shutdown();
        }

        assertTrue(objects.get("raced.webp").isEmpty(),
                "the after-commit re-sweep must take the variant written inside the delete window");
    }

    @Test
    @DisplayName("deleteById tolerates a meme that never got a WebP variant")
    void delete_without_a_variant_is_fine() {
        memes.save(new Meme("plain", "author@example.com", "png", new byte[]{1}));

        memes.deleteById("plain");   // no {id}.webp was ever cached — must not throw

        assertTrue(objects.get("plain").isEmpty());
    }
}
