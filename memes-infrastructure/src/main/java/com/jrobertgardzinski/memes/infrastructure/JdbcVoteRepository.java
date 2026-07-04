package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.VoteRepository;
import com.jrobertgardzinski.memes.domain.RankedMeme;
import com.jrobertgardzinski.voting.VoteDirection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
    public List<RankedMeme> allScores() {
        return jdbc.sql("SELECT meme_id, SUM(CASE WHEN direction = 'UP' THEN 1 ELSE -1 END) AS score "
                        + "FROM meme_votes GROUP BY meme_id")
                .query((rs, n) -> new RankedMeme(rs.getString("meme_id"), rs.getInt("score")))
                .list();
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
