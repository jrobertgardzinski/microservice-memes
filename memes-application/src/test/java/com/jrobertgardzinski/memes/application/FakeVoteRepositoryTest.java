package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.voting.VoteDirection;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the stand-in promises the account closure. These three are not arbitrary: they are exactly
 * what {@code PurgeUserContent} leans on when it retracts the leaver's ballots and only then reads
 * each meme's score. A mock answers all three with zero and nothing turns red.
 */
@Epic("Architecture")
@Feature("A stand-in behaves like the adapter it stands in for")
class FakeVoteRepositoryTest {

    private final FakeVoteRepository votes = new FakeVoteRepository();

    @Test
    @DisplayName("the score is the ballots, and nothing else counts them")
    void score_is_the_ballots() {
        votes.cast("m1", "alice", VoteDirection.UP);
        votes.cast("m1", "bob", VoteDirection.UP);
        votes.cast("m1", "carol", VoteDirection.DOWN);

        assertEquals(1, votes.scoreOf("m1"));
        assertEquals(0, votes.scoreOf("never-voted-on"));
    }

    @Test
    @DisplayName("a purged meme keeps none of its ballots")
    void purging_a_meme_forgets_its_ballots() {
        votes.cast("m1", "alice", VoteDirection.UP);

        votes.purgeMeme("m1");

        assertEquals(0, votes.scoreOf("m1"));
        assertTrue(votes.voteOf("m1", "alice").isEmpty());
    }

    @Test
    @DisplayName("a purged voter leaves every meme at once, and the scores say so")
    void purging_a_voter_moves_every_score() {
        votes.cast("m1", "leaver", VoteDirection.UP);
        votes.cast("m2", "leaver", VoteDirection.UP);
        votes.cast("m2", "somebody", VoteDirection.UP);

        votes.purgeVoter("leaver");

        // the point of the ordering in PurgeUserContent: a leaver cannot buy his own meme's
        // survival with a ballot that is leaving with him
        assertEquals(0, votes.scoreOf("m1"));
        assertEquals(1, votes.scoreOf("m2"));
    }
}
