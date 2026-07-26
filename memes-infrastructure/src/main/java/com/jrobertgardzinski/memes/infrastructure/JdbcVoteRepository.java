package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.VoteRepository;
import com.jrobertgardzinski.memes.domain.ScoredMeme;
import com.jrobertgardzinski.voting.VoteDirection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Postgres-backed {@link VoteRepository} (H2 in dev/tests): one row per (meme, voter); cast is
 * delete-then-insert — the portable upsert this portfolio uses everywhere.
 */
@Repository
class JdbcVoteRepository implements VoteRepository {

    private final JdbcClient jdbc;

    JdbcVoteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void cast(String memeId, String voter, VoteDirection direction) {
        retract(memeId, voter);
        jdbc.sql("INSERT INTO meme_votes (meme_id, voter, direction) VALUES (?, ?, ?)")
                .params(memeId, voter, direction.name()).update();
    }

    @Override
    public void retract(String memeId, String voter) {
        jdbc.sql("DELETE FROM meme_votes WHERE meme_id = ? AND voter = ?")
                .params(memeId, voter).update();
    }

    @Override
    public Optional<VoteDirection> voteOf(String memeId, String voter) {
        return jdbc.sql("SELECT direction FROM meme_votes WHERE meme_id = ? AND voter = ?")
                .params(memeId, voter)
                .query((rs, n) -> VoteDirection.valueOf(rs.getString("direction")))
                .optional();
    }

    @Override
    public int scoreOf(String memeId) {
        return jdbc.sql("SELECT COALESCE(SUM(CASE WHEN direction = 'UP' THEN 1 ELSE -1 END), 0) "
                        + "FROM meme_votes WHERE meme_id = ?")
                .params(memeId)
                .query((rs, n) -> rs.getInt(1)).single();
    }

    @Override
    public List<ScoredMeme> allScores() {
        // published_at rides along on the aggregate instead of being fetched per meme: the hot
        // page needs score AND age, and asking for the age one meme at a time (from inside a
        // comparator, no less) cost more round-trips than the table has rows.
        //
        // LEFT, not INNER: a ballot whose meme row is gone still reaches the ranking, with no
        // publication time — the use case decides what an unknown age means, the adapter does
        // not quietly drop rows the ranking never learns about.
        return jdbc.sql("SELECT v.meme_id, "
                        + "SUM(CASE WHEN v.direction = 'UP' THEN 1 ELSE -1 END) AS score, "
                        + "m.published_at "
                        + "FROM meme_votes v LEFT JOIN memes m ON m.id = v.meme_id "
                        + "GROUP BY v.meme_id, m.published_at")
                .query((rs, n) -> {
                    Timestamp published = rs.getTimestamp("published_at");
                    return new ScoredMeme(rs.getString("meme_id"), rs.getInt("score"),
                            published == null ? null : published.toInstant());
                })
                .list();
    }

    @Override
    public Map<String, Integer> scoresOf(Collection<String> memeIds) {
        if (memeIds.isEmpty()) {
            return Map.of();   // "IN ()" is not valid SQL, and an empty question needs no round trip
        }
        // ONE aggregate for a whole page of tiles: the port's per-id default would be a query per
        // thumbnail. A meme with no ballot rows is not in this answer — the use case is what turns
        // that into a score of 0, and only for memes it has confirmed exist.
        return jdbc.sql("SELECT meme_id, SUM(CASE WHEN direction = 'UP' THEN 1 ELSE -1 END) AS score "
                        + "FROM meme_votes WHERE meme_id IN (:ids) GROUP BY meme_id")
                .param("ids", memeIds.stream().distinct().toList())
                .query((rs, n) -> Map.entry(rs.getString("meme_id"), rs.getInt("score")))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void purgeMeme(String memeId) {
        jdbc.sql("DELETE FROM meme_votes WHERE meme_id = ?").params(memeId).update();
    }

    @Override
    public void purgeVoter(String voter) {
        jdbc.sql("DELETE FROM meme_votes WHERE voter = ?").params(voter).update();
    }
}
