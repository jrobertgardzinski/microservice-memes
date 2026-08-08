package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.MemeContentIndex;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

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
            // The claim is only worth honouring while the meme holding it is in the gallery — hence
            // active_memes, the same filter every other read in this service goes through. This is
            // the read path the erasure work nearly missed: a leaver's meme marked PENDING_ERASURE
            // still held its hash, so re-uploading that picture answered with the marked meme's id
            // — a 200 pointing at content the gallery refuses to show, and a way to prove by
            // experiment that a given image had been posted by the account being deleted.
            Optional<String> activeOwner = jdbc.sql(
                            "SELECT c.meme_id FROM content_index c "
                                    + "JOIN active_memes m ON m.id = c.meme_id WHERE c.content_hash = ?")
                    .params(hash).query((rs, n) -> rs.getString("meme_id")).optional();
            if (activeOwner.isPresent()) {
                return activeOwner.get();
            }
            // The holder is reserved (or gone without taking its claim along): the newcomer takes
            // the hash over, so the upload succeeds as its own meme. If the saga is compensated and
            // the old meme comes back, the two identical pictures simply both exist — dedup is a
            // storage economy, never a correctness rule, and one duplicate is a far smaller wrong
            // than either refusing the upload or pointing it at somebody else's erasure.
            jdbc.sql("UPDATE content_index SET meme_id = ? WHERE content_hash = ?")
                    .params(candidateId, hash).update();
            return candidateId;
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
