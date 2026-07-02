package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Meme;
import com.jrobertgardzinski.memes.domain.RankedMeme;
import com.jrobertgardzinski.memes.domain.VoteDirection;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Epic("Use case")
@Feature("Cast vote")
class CastVoteTest {

    private final Map<String, Meme> memes = new HashMap<>();
    private final Map<String, Map<String, VoteDirection>> votes = new HashMap<>();

    private final MemeRepository memeRepository = new MemeRepository() {
        public void save(Meme meme) {
            memes.put(meme.id(), meme);
        }

        public Optional<Meme> find(String id) {
            return Optional.ofNullable(memes.get(id));
        }

        public List<String> allIds() {
            return List.copyOf(memes.keySet());
        }
    };
    private final VoteRepository voteRepository = new VoteRepository() {
        public void castVote(String memeId, String voter, VoteDirection direction) {
            votes.computeIfAbsent(memeId, id -> new HashMap<>()).put(voter, direction);
        }

        public int scoreOf(String memeId) {
            return votes.getOrDefault(memeId, Map.of()).values().stream()
                    .mapToInt(d -> d == VoteDirection.UP ? 1 : -1).sum();
        }

        public List<RankedMeme> allScores() {
            return votes.keySet().stream().map(id -> new RankedMeme(id, scoreOf(id))).toList();
        }
    };
    private final CastVote castVote = new CastVote(memeRepository, voteRepository);

    @Test
    @DisplayName("distinct voters raise an existing meme's score; a voter can change their mind")
    void distinct_voters_raise_the_score() {
        memes.put("m1", new Meme("m1", "png", new byte[]{1}));

        assertEquals(Optional.of(1), castVote.execute("m1", "alice", VoteDirection.UP));
        assertEquals(Optional.of(2), castVote.execute("m1", "bob", VoteDirection.UP));
        assertEquals(Optional.of(0), castVote.execute("m1", "bob", VoteDirection.DOWN)); // changed mind
    }

    @Test
    @DisplayName("re-voting the same way does not stack")
    void revoting_does_not_stack() {
        memes.put("m1", new Meme("m1", "png", new byte[]{1}));

        castVote.execute("m1", "alice", VoteDirection.UP);
        castVote.execute("m1", "alice", VoteDirection.UP);
        castVote.execute("m1", "alice", VoteDirection.UP);

        assertEquals(Optional.of(1), castVote.execute("m1", "alice", VoteDirection.UP));
    }

    @Test
    @DisplayName("refuses to vote on a missing meme")
    void refuses_missing_meme() {
        assertTrue(castVote.execute("nope", "alice", VoteDirection.UP).isEmpty());
        assertTrue(votes.isEmpty());
    }
}
