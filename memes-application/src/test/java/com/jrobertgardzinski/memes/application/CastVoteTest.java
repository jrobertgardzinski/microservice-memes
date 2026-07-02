package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Meme;
import com.jrobertgardzinski.memes.domain.RankedMeme;
import com.jrobertgardzinski.voting.VoteDirection;
import com.jrobertgardzinski.voting.VoteTally;
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

        public void reassignAuthor(String memeId, String newAuthor) {
            memes.computeIfPresent(memeId, (id, m) -> new Meme(m.id(), newAuthor, m.format(), m.data()));
        }
    };
    private final VoteRepository voteRepository = new FakeVoteRepository(votes);
    private final CastVote castVote = new CastVote(memeRepository, voteRepository);

    @Test
    @DisplayName("the library's toggle applies, anchored to an existing meme")
    void toggles_on_an_existing_meme() {
        memes.put("m1", new Meme("m1", "alice@example.com", "png", new byte[]{1}));

        assertEquals(Optional.of(new VoteTally(1, Optional.of(VoteDirection.UP))),
                castVote.execute("m1", "alice", VoteDirection.UP));
        assertEquals(Optional.of(new VoteTally(0, Optional.empty())),
                castVote.execute("m1", "alice", VoteDirection.UP)); // retracted
    }

    @Test
    @DisplayName("refuses to vote on a missing meme")
    void refuses_missing_meme() {
        assertTrue(castVote.execute("nope", "alice", VoteDirection.UP).isEmpty());
        assertTrue(votes.isEmpty());
    }

    /** Shared in-memory {@link VoteRepository} fake for the use-case tests. */
    static class FakeVoteRepository implements VoteRepository {
        private final Map<String, Map<String, VoteDirection>> votes;

        FakeVoteRepository(Map<String, Map<String, VoteDirection>> votes) {
            this.votes = votes;
        }

        public void cast(String memeId, String voter, VoteDirection direction) {
            votes.computeIfAbsent(memeId, id -> new HashMap<>()).put(voter, direction);
        }

        public void retract(String memeId, String voter) {
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
    }
}
