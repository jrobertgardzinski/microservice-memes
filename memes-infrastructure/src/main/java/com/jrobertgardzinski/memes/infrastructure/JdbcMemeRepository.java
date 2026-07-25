package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.MemeRepository;
import com.jrobertgardzinski.memes.application.ObjectStore;
import com.jrobertgardzinski.memes.application.PublicationLog;
import com.jrobertgardzinski.memes.domain.Meme;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Postgres-backed {@link MemeRepository} (H2 in dev/tests): metadata in the meme row, the image
 * bytes delegated to the {@link ObjectStore} — written and read together, so a meme and its
 * object never drift. Recording publication time on save also makes this the {@link
 * PublicationLog} the hot ranking decays by.
 */
@Repository
class JdbcMemeRepository implements MemeRepository, PublicationLog {

    private final JdbcClient jdbc;
    private final ObjectStore objects;

    JdbcMemeRepository(JdbcClient jdbc, ObjectStore objects) {
        this.jdbc = jdbc;
        this.objects = objects;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void save(Meme meme) {
        jdbc.sql("INSERT INTO memes (id, author, format, published_at) VALUES (?, ?, ?, ?)")
                .params(meme.id(), meme.author(), meme.format(), Timestamp.from(Instant.now()))
                .update();
        objects.put(meme.id(), meme.data());
    }

    @Override
    public Optional<Meme> find(String id) {
        return jdbc.sql("SELECT id, author, format FROM memes WHERE id = ?")
                .params(id)
                .query((rs, n) -> new Object[]{rs.getString("id"), rs.getString("author"), rs.getString("format")})
                .optional()
                .flatMap(row -> objects.get((String) row[0]).map(bytes ->
                        new Meme((String) row[0], (String) row[1], (String) row[2], bytes)));
    }

    @Override
    public boolean exists(String id) {
        // row lookup only — the port's default would drag the blob out of the ObjectStore just to
        // throw it away; ServeMeme calls this on every WebP cache write to spot a concurrent delete
        return jdbc.sql("SELECT 1 FROM memes WHERE id = ?")
                .params(id)
                .query((rs, n) -> 1)
                .optional()
                .isPresent();
    }

    @Override
    public List<String> allIds() {
        return jdbc.sql("SELECT id FROM memes ORDER BY published_at DESC, id DESC")
                .query((rs, n) -> rs.getString("id")).list();
    }

    @Override
    public List<String> findIdsByAuthor(String author) {
        return jdbc.sql("SELECT id FROM memes WHERE author = ?")
                .params(author)
                .query((rs, n) -> rs.getString("id")).list();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void deleteById(String memeId) {
        jdbc.sql("DELETE FROM memes WHERE id = ?").params(memeId).update();
        objects.delete(memeId);
        // ServeMeme caches a WebP variant under this derived key (see its webpKey); without this
        // the encoded copy of a deleted image would sit in the store forever. Every adapter's
        // delete is a no-op on a missing key, so "never encoded" costs nothing.
        String variantKey = memeId + ".webp";
        objects.delete(variantKey);
        // On the DB store the sweep above runs INSIDE this transaction, so it misses a variant
        // that a concurrent ServeMeme writes between it and our commit — and that serve's own
        // exists() re-check still sees our uncommitted row, so it leaves the variant in place
        // too. Sweep once more AFTER the commit: any variant put whose commit lands before ours
        // is caught here; one landing after ours is caught by ServeMeme's re-check (the row is
        // then visibly gone). On filesystem/S3 the store's delete would normally PARK itself —
        // but called from inside our afterCommit it runs inline instead (the phase-aware
        // re-entry in TransactionAwareDeletes; a plain re-registration would silently never
        // run, and the second sweep would vanish without a trace).
        //
        // On the DB store this after-commit DELETE runs on a FRESH pool connection outside any
        // Spring transaction, so it only commits because Hikari hands connections out with
        // autocommit restored — spring.datasource.hikari.auto-commit stays true (the default,
        // pinned by TransactionAwareDeletesTest). With auto-commit=false this sweep would roll
        // back silently when the connection returns to the pool.
        TransactionAwareDeletes.afterCommitOrNow(() -> objects.delete(variantKey));
    }

    @Override
    public void reassignAuthor(String memeId, String newAuthor) {
        jdbc.sql("UPDATE memes SET author = ? WHERE id = ?").params(newAuthor, memeId).update();
    }

    @Override
    public Optional<Instant> publishedAt(String memeId) {
        return jdbc.sql("SELECT published_at FROM memes WHERE id = ?")
                .params(memeId)
                .query((rs, n) -> rs.getTimestamp("published_at").toInstant())
                .optional();
    }
}
