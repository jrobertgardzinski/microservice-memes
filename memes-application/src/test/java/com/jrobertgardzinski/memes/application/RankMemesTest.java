package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.RankedMeme;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Epic("Use case")
@Feature("Rank memes")
class RankMemesTest {

    private static final Instant NOW = Instant.parse("2026-07-04T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private RankMemes ranker(Map<String, Instant> published, RankedMeme... scores) {
        var votes = new HashMap<String, Map<String, com.jrobertgardzinski.voting.VoteDirection>>();
        var repository = new CastVoteTest.FakeVoteRepository(votes) {
            @Override
            public List<RankedMeme> allScores() {
                return List.of(scores);
            }
        };
        PublicationLog log = memeId -> Optional.ofNullable(published.get(memeId));
        return new RankMemes(repository, log, CLOCK);
    }

    @Test
    @DisplayName("with equal ages the plain score still decides")
    void equal_age_ranks_by_score() {
        var published = Map.of("low", NOW, "high", NOW, "mid", NOW);
        List<RankedMeme> ranked = ranker(published,
                new RankedMeme("low", 1), new RankedMeme("high", 5), new RankedMeme("mid", 3)).execute();
        assertEquals(List.of("high", "mid", "low"), ranked.stream().map(RankedMeme::memeId).toList());
    }

    @Test
    @DisplayName("hot cools with age: a fresh contender outranks last week's champion")
    void freshness_beats_a_stale_high_score() {
        var published = Map.of(
                "lastWeeks-champion", NOW.minusSeconds(7 * 24 * 3600),
                "fresh-contender", NOW.minusSeconds(3600));
        List<RankedMeme> ranked = ranker(published,
                new RankedMeme("lastWeeks-champion", 40), new RankedMeme("fresh-contender", 5)).execute();
        assertEquals("fresh-contender", ranked.get(0).memeId(),
                "40 points from a week ago cool below 5 points from this hour");
        assertEquals(40, ranked.get(1).score(), "the reported score itself is untouched");
    }

    @Test
    @DisplayName("a meme the log does not know is treated as brand new, not buried")
    void unknown_age_fails_safe() {
        List<RankedMeme> ranked = ranker(Map.of("known-old", NOW.minusSeconds(48 * 3600)),
                new RankedMeme("known-old", 10), new RankedMeme("unknown", 2)).execute();
        assertEquals("unknown", ranked.get(0).memeId());
    }
}
