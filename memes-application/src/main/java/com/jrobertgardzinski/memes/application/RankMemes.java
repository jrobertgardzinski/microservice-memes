package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.RankedMeme;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Ranks voted memes by HOTNESS: the score cooled by age, Reddit-style — {@code score /
 * (ageInHours + 2)^GRAVITY}. A fresh meme with a handful of votes beats last week's champion,
 * which is what a "hot" page is for; with equal ages the plain score still decides. The score
 * itself is reported untouched — decay only orders the page.
 */
public class RankMemes {

    /** How hard age pulls a meme down the page; the classic Reddit exponent. */
    static final double GRAVITY = 1.5;

    private final VoteRepository voteRepository;
    private final PublicationLog publicationLog;
    private final Clock clock;

    public RankMemes(VoteRepository voteRepository, PublicationLog publicationLog, Clock clock) {
        this.voteRepository = voteRepository;
        this.publicationLog = publicationLog;
        this.clock = clock;
    }

    public List<RankedMeme> execute() {
        Instant now = clock.instant();
        return voteRepository.allScores().stream()
                .sorted(Comparator.comparingDouble((RankedMeme meme) -> hotness(meme, now)).reversed())
                .toList();
    }

    private double hotness(RankedMeme meme, Instant now) {
        double ageHours = publicationLog.publishedAt(meme.memeId())
                .map(published -> Math.max(0.0, Duration.between(published, now).toMillis() / 3_600_000.0))
                .orElse(0.0);
        return meme.score() / Math.pow(ageHours + 2.0, GRAVITY);
    }
}
