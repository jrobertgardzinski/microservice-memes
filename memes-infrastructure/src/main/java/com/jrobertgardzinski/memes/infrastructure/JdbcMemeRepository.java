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
        // ServeMeme caches a WebP variant under {id}.webp and MakeThumbnail a thumbnail under
        // {id}.thumb; without this sweep the derived copies of a deleted image would sit in the
        // store forever. Every adapter's delete is a no-op on a missing key, so "never cached"
        // costs nothing.
        String variantKey = memeId + ".webp";
        String thumbKey = memeId + ".thumb";
        objects.delete(variantKey);
        objects.delete(thumbKey);
        // On the DB store the sweeps above run INSIDE this transaction, so they miss a variant
        // that a concurrent ServeMeme/MakeThumbnail writes between them and our commit — and
        // that writer's own exists() re-check still sees our uncommitted row, so it leaves the
        // variant in place too. Sweep once more AFTER the commit: any variant put whose commit
        // lands before ours is caught here; one landing after ours is caught by the writer's
        // re-check (the row is then visibly gone). On filesystem/S3 the store's delete PARKS itself again from inside
        // our after-commit callback — TransactionAwareDeletes delivers such late registrations
        // in the afterCompletion phase (a plain afterCommit-only registration would silently
        // never run, and the second sweep would vanish without a trace).
        //
        // On the DB store this after-commit DELETE does NOT get a fresh connection: the
        // just-committed transaction's connection is STILL BOUND to the thread during the
        // after-phases (Spring unbinds it only in the cleanup), so JdbcClient reuses it with
        // autocommit still off — the DELETE becomes durable only when Hikari restores
        // autocommit=true as the connection returns to the pool, which implicitly commits it
        // (probed in round 3). That restore is exactly why spring.datasource.hikari.auto-commit
        // must stay true (the default, pinned by TransactionAwareDeletesTest): with
        // auto-commit=false the pending DELETE would roll back silently instead.
        // two separate parked runnables, not one: TransactionAwareDeletes isolates failures per
        // runnable, so a store hiccup on the WebP sweep cannot cost the thumbnail its sweep
        TransactionAwareDeletes.afterCommitOrNow(() -> objects.delete(variantKey));
        TransactionAwareDeletes.afterCommitOrNow(() -> objects.delete(thumbKey));
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
