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

        public List<String> findIdsByAuthor(String author) {
            return memes.values().stream().filter(m -> m.author().equals(author)).map(Meme::id).toList();
        }

        public void deleteById(String memeId) {
            memes.remove(memeId);
        }

        public void anonymizeAuthor(String author, String replacement) {
            memes.replaceAll((id, m) -> m.author().equals(author)
                    ? new Meme(m.id(), replacement, m.format(), m.data()) : m);
        }
    };
    private final VoteRepository voteRepository = new VoteRepository() {
        public void castVote(String memeId, String voter, VoteDirection direction) {
            votes.computeIfAbsent(memeId, id -> new HashMap<>()).put(voter, direction);
        }

        public void retractVote(String memeId, String voter) {
            votes.getOrDefault(memeId, Map.of()).remove(voter);
        }

        public Optional<VoteDirection> voteOf(String memeId, String voter) {
            return Optional.ofNullable(votes.getOrDefault(memeId, Map.of()).get(voter));
        }

        public int scoreOf(String memeId) {
            return votes.getOrDefault(memeId, Map.of()).values().stream()
                    .mapToInt(d -> d == VoteDirection.UP ? 1 : -1).sum();
        }

        public List<RankedMeme> allScores() {
            return votes.keySet().stream().map(id -> new RankedMeme(id, scoreOf(id))).toList();
        }

        public void purgeMeme(String memeId) {
            votes.remove(memeId);
        }

        public void purgeVoter(String voter) {
            votes.values().forEach(v -> v.remove(voter));
        }
    };
    private final CastVote castVote = new CastVote(memeRepository, voteRepository);

    @Test
    @DisplayName("distinct voters raise the score; the opposite direction switches a voter's mind")
    void distinct_voters_raise_the_score() {
        memes.put("m1", new Meme("m1", "alice@example.com", "png", new byte[]{1}));

        assertEquals(tally(1, VoteDirection.UP), castVote.execute("m1", "alice", VoteDirection.UP));
        assertEquals(tally(2, VoteDirection.UP), castVote.execute("m1", "bob", VoteDirection.UP));
        assertEquals(tally(0, VoteDirection.DOWN), castVote.execute("m1", "bob", VoteDirection.DOWN));
    }

    @Test
    @DisplayName("repeating the same vote retracts it (a toggle, never stacking)")
    void repeating_the_same_vote_retracts_it() {
        memes.put("m1", new Meme("m1", "alice@example.com", "png", new byte[]{1}));

        assertEquals(tally(1, VoteDirection.UP), castVote.execute("m1", "alice", VoteDirection.UP));
        assertEquals(Optional.of(new VoteTally(0, Optional.empty())),
                castVote.execute("m1", "alice", VoteDirection.UP)); // retracted
        assertEquals(tally(1, VoteDirection.UP), castVote.execute("m1", "alice", VoteDirection.UP));
    }

    private static Optional<VoteTally> tally(int score, VoteDirection mine) {
        return Optional.of(new VoteTally(score, Optional.of(mine)));
    }

    @Test
    @DisplayName("refuses to vote on a missing meme")
    void refuses_missing_meme() {
        assertTrue(castVote.execute("nope", "alice", VoteDirection.UP).isEmpty());
        assertTrue(votes.isEmpty());
    }
}
