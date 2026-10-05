package com.jrobertgardzinski.memes.application.votes;

import com.jrobertgardzinski.identity.UserId;
import com.jrobertgardzinski.memes.domain.votes.RankedMeme;
import com.jrobertgardzinski.memes.system.votes.CastVote;
import com.jrobertgardzinski.memes.system.votes.RankMemes;
import com.jrobertgardzinski.memes.system.votes.ShowMemeScores;
import com.jrobertgardzinski.memes.system.votes.ShowMemeVote;
import com.jrobertgardzinski.voting.VoteDirection;
import com.jrobertgardzinski.voting.VoteTally;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Votes on memes: casting one, a meme's tally, the hot list, the scores of a batch. */
public final class VoteService {

    /** The most memes one question about scores may name. */
    public static final int MAX_IDS = 100;

    private final CastVote castVote;
    private final ShowMemeVote showMemeVote;
    private final RankMemes rankMemes;
    private final ShowMemeScores showMemeScores;

    public VoteService(CastVote castVote, ShowMemeVote showMemeVote, RankMemes rankMemes,
                       ShowMemeScores showMemeScores) {
        this.castVote = castVote;
        this.showMemeVote = showMemeVote;
        this.rankMemes = rankMemes;
        this.showMemeScores = showMemeScores;
    }

    public Vote vote(String memeId, UserId voter, String direction) {
        VoteDirection parsed;
        try {
            parsed = VoteDirection.valueOf(String.valueOf(direction).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return new Vote.InvalidDirection();
        }
        // the ballot is keyed by the voter's id, in its wire form
        return castVote.execute(memeId, voter.toString(), parsed)
                .<Vote>map(Vote.Counted::new)
                .orElseGet(Vote.NoSuchMeme::new);
    }

    /** A meme's tally, and the viewer's own vote in it when there is a viewer. */
    public Optional<VoteTally> tally(String memeId, UserId viewer) {
        return showMemeVote.execute(memeId, Optional.ofNullable(viewer).map(Object::toString));
    }

    public List<RankedMeme> hot() {
        return rankMemes.execute();
    }

    public Scores scores(List<String> ids) {
        List<String> asked = (ids == null ? List.<String>of() : ids).stream().filter(id -> !id.isBlank()).toList();
        if (asked.size() > MAX_IDS) {
            return new Scores.TooManyIds(MAX_IDS);
        }
        return new Scores.Scored(showMemeScores.execute(asked));
    }

    public sealed interface Vote {
        record Counted(VoteTally tally) implements Vote {}

        record InvalidDirection() implements Vote {}

        record NoSuchMeme() implements Vote {}
    }

    public sealed interface Scores {
        record Scored(Map<String, Integer> scores) implements Scores {}

        record TooManyIds(int max) implements Scores {}
    }
}
