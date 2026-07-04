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
