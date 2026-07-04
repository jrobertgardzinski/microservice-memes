package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.MemeContentIndex;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Postgres-backed {@link MemeContentIndex} (H2 in dev/tests): the SHA-256 is the primary key, so
 * the CLAIM is the database's own uniqueness — two simultaneous uploads of the same picture race
 * the constraint and exactly one insert wins; the loser reads the winner back.
 */
@Repository
class JdbcMemeContentIndex implements MemeContentIndex {

    private final JdbcClient jdbc;

    JdbcMemeContentIndex(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String claim(byte[] data, String candidateId) {
        String hash = sha256(data);
        try {
            jdbc.sql("INSERT INTO content_index (content_hash, meme_id) VALUES (?, ?)")
                    .params(hash, candidateId).update();
            return candidateId;
        } catch (DuplicateKeyException alreadyClaimed) {
            return jdbc.sql("SELECT meme_id FROM content_index WHERE content_hash = ?")
                    .params(hash).query((rs, n) -> rs.getString("meme_id")).single();
        }
    }

    @Override
    public void remove(String memeId) {
        jdbc.sql("DELETE FROM content_index WHERE meme_id = ?").params(memeId).update();
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 is always available
        }
    }
}
