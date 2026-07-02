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
    private final Map<String, Integer> scores = new HashMap<>();

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
        public void castVote(String memeId, VoteDirection direction) {
            scores.merge(memeId, direction == VoteDirection.UP ? 1 : -1, Integer::sum);
        }

        public int scoreOf(String memeId) {
            return scores.getOrDefault(memeId, 0);
        }

        public List<RankedMeme> allScores() {
            return scores.entrySet().stream().map(e -> new RankedMeme(e.getKey(), e.getValue())).toList();
        }
    };
    private final CastVote castVote = new CastVote(memeRepository, voteRepository);

    @Test
    @DisplayName("an up-vote raises an existing meme's score")
    void up_vote_raises_the_score() {
        memes.put("m1", new Meme("m1", "png", new byte[]{1}));

        assertEquals(Optional.of(1), castVote.execute("m1", VoteDirection.UP));
        assertEquals(Optional.of(0), castVote.execute("m1", VoteDirection.DOWN));
    }

    @Test
    @DisplayName("refuses to vote on a missing meme")
    void refuses_missing_meme() {
        assertTrue(castVote.execute("nope", VoteDirection.UP).isEmpty());
        assertTrue(scores.isEmpty());
    }
}
