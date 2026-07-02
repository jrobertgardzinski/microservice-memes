package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.RankedMeme;
import com.jrobertgardzinski.memes.domain.VoteDirection;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Epic("Use case")
@Feature("Rank memes")
class RankMemesTest {

    @Test
    @DisplayName("ranks memes by score, highest first")
    void ranks_by_score_desc() {
        VoteRepository voteRepository = new VoteRepository() {
            public void castVote(String memeId, String voter, VoteDirection direction) {
            }

            public void retractVote(String memeId, String voter) {
            }

            public java.util.Optional<VoteDirection> voteOf(String memeId, String voter) {
                return java.util.Optional.empty();
            }

            public int scoreOf(String memeId) {
                return 0;
            }

            public List<RankedMeme> allScores() {
                return List.of(new RankedMeme("low", 1), new RankedMeme("high", 5), new RankedMeme("mid", 3));
            }
        };

        List<RankedMeme> ranked = new RankMemes(voteRepository).execute();

        assertEquals(List.of("high", "mid", "low"), ranked.stream().map(RankedMeme::memeId).toList());
    }
}
