package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.MemeRepository;
import com.jrobertgardzinski.memes.application.PublicationLog;
import com.jrobertgardzinski.memes.domain.Meme;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Postgres-backed {@link MemeRepository} (H2 in dev/tests): metadata and the image bytes in one
 * row, publication time recorded on save — which also makes this the {@link PublicationLog} the
 * hot ranking decays by.
 */
@Repository
class JdbcMemeRepository implements MemeRepository, PublicationLog {

    private final JdbcClient jdbc;

    JdbcMemeRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Meme meme) {
        jdbc.sql("INSERT INTO memes (id, author, format, data, published_at) VALUES (?, ?, ?, ?, ?)")
                .params(meme.id(), meme.author(), meme.format(), meme.data(),
                        Timestamp.from(Instant.now()))
                .update();
    }

    @Override
    public Optional<Meme> find(String id) {
        return jdbc.sql("SELECT id, author, format, data FROM memes WHERE id = ?")
                .params(id)
                .query((rs, n) -> new Meme(rs.getString("id"), rs.getString("author"),
                        rs.getString("format"), rs.getBytes("data")))
                .optional();
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
    public void deleteById(String memeId) {
        jdbc.sql("DELETE FROM memes WHERE id = ?").params(memeId).update();
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
