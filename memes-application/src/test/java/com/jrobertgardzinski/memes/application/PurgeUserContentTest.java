package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.config.PurgeRule;
import com.jrobertgardzinski.memes.domain.DeletedAccount;
import com.jrobertgardzinski.memes.domain.Meme;
import com.jrobertgardzinski.voting.VoteDirection;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Epic("Use case")
@Feature("Purge user content")
class PurgeUserContentTest {

    private final Map<String, Meme> memes = new HashMap<>();
    private final Map<String, Map<String, VoteDirection>> memeVotes = new HashMap<>();
    private final Map<String, String> contentIndex = new HashMap<>();
    private final List<String> announcedDeletions = new ArrayList<>();

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
    private final VoteRepository voteRepository = new CastVoteTest.FakeVoteRepository(memeVotes);
    private final MemeContentIndex index = new MemeContentIndex() {
        public String claim(byte[] data, String candidateId) {
            String earlier = contentIndex.putIfAbsent(new String(data), candidateId);
            return earlier != null ? earlier : candidateId;
        }

        public void remove(String memeId) {
            contentIndex.values().removeIf(memeId::equals);
        }
    };
    private final MemeEvents memeEvents = announcedDeletions::add;
    private final java.util.Map<String, java.util.Set<com.jrobertgardzinski.memes.tags.Tag>> tagIndex =
            new HashMap<>();
    private final TagRepository tagRepository = new TagRepository() {
        public void replaceTags(String memeId, java.util.Set<com.jrobertgardzinski.memes.tags.Tag> tags) {
            tagIndex.put(memeId, tags);
        }

        public java.util.Set<com.jrobertgardzinski.memes.tags.Tag> tagsOf(String memeId) {
            return tagIndex.getOrDefault(memeId, java.util.Set.of());
        }

        public java.util.Set<String> memesTagged(com.jrobertgardzinski.memes.tags.Tag tag) {
            return java.util.Set.of();
        }

        public void removeMeme(String memeId) {
            tagIndex.remove(memeId);
        }
    };

    private Optional<PurgeRule> adminOverride = Optional.empty();
    private final PurgePolicyOverride override = new PurgePolicyOverride() {
        public Optional<PurgeRule> current() {
            return adminOverride;
        }

        public void set(PurgeRule rule, String updatedBy) {
            adminOverride = Optional.of(rule);
        }

        public void clear() {
            adminOverride = Optional.empty();
        }
    };

    private final FakeMemeErasure erasure = new FakeMemeErasure(memes);
    private final java.time.Clock clock =
            java.time.Clock.fixed(java.time.Instant.parse("2026-08-08T10:00:00Z"), java.time.ZoneOffset.UTC);
    private final MarkUserContentForErasure mark = new MarkUserContentForErasure(erasure, clock);
    private final RestoreUserContent restore = new RestoreUserContent(erasure);
    private final PurgeUserContent purge = new PurgeUserContent(memeRepository, erasure, voteRepository, index, tagRepository, memeEvents, override, new PurgeRule.Delete());

    /**
     * The saga as the participant sees it when everything goes right: the reversible mark, then the
     * orchestrator's closure. Every test below that used to call the purge on its own goes through
     * both phases now, because the erasure acts on what the MARK reserved — a purge that arrives
     * without one has, correctly, nothing to do.
     */
    private void markThenErase(String author, Optional<PurgeRule> rule) {
        mark.execute(author);
        purge.execute(author, rule);
    }

    @Test
    @DisplayName("the leaver's memes disappear with their votes; the thread owner is told")
    void purges_memes_and_announces() {
        memes.put("leavers-meme", new Meme("leavers-meme", "leaver@example.com", "png", new byte[]{1}));
        contentIndex.put("leavers-content", "leavers-meme");
        memeVotes.put("leavers-meme", new HashMap<>(Map.of("somebody-else@example.com", VoteDirection.UP)));

        markThenErase("leaver@example.com", Optional.empty());

        assertTrue(memes.isEmpty());
        assertTrue(memeVotes.isEmpty());
        assertTrue(contentIndex.isEmpty()); // identical re-uploads must not dedup into a ghost
        assertEquals(List.of("leavers-meme"), announcedDeletions); // comments service drops the thread
    }

    @Test
    @DisplayName("KEEP_POPULAR: the community's favourites survive anonymised, the rest goes")
    void popularity_decides_per_meme() {
        memes.put("hit", new Meme("hit", "leaver@example.com", "png", new byte[]{1}));
        memes.put("flop", new Meme("flop", "leaver@example.com", "png", new byte[]{2}));
        memeVotes.put("hit", new HashMap<>(Map.of(
                "a@example.com", VoteDirection.UP, "b@example.com", VoteDirection.UP)));

        markThenErase("leaver@example.com", Optional.of(new PurgeRule.KeepPopularAnonymized(2)));

        assertEquals(DeletedAccount.AUTHOR, memes.get("hit").author());
        assertFalse(memes.containsKey("flop"));
        assertEquals(List.of("flop"), announcedDeletions); // only the deleted one is announced
    }

    @Test
    @DisplayName("KEEP_POPULAR: the leaver cannot vote for his own survival")
    void the_leavers_own_votes_do_not_count_towards_the_threshold() {
        memes.put("self-liked", new Meme("self-liked", "leaver@example.com", "png", new byte[]{3}));
        // two votes, one of them the leaver's own — and his is leaving with him, so the community's
        // verdict on this meme is ONE. Counting his made the threshold of two look met.
        memeVotes.put("self-liked", new HashMap<>(Map.of(
                "leaver@example.com", VoteDirection.UP, "a@example.com", VoteDirection.UP)));

        markThenErase("leaver@example.com", Optional.of(new PurgeRule.KeepPopularAnonymized(2)));

        assertFalse(memes.containsKey("self-liked"),
                "a meme kept only by the leaver's own vote is not what the community liked");
        assertEquals(List.of("self-liked"), announcedDeletions);
    }

    @Test
    @DisplayName("the admin's override beats the deployment default")
    void admin_override_beats_the_default() {
        adminOverride = Optional.of(new PurgeRule.AnonymizeAuthor());
        memes.put("kept", new Meme("kept", "leaver@example.com", "png", new byte[]{1}));

        markThenErase("leaver@example.com", Optional.empty());   // default says DELETE

        assertEquals(DeletedAccount.AUTHOR, memes.get("kept").author());
        assertTrue(announcedDeletions.isEmpty());
    }

    @Test
    @DisplayName("the leaver's wizard choice beats the admin's override")
    void wizard_choice_beats_the_override() {
        adminOverride = Optional.of(new PurgeRule.AnonymizeAuthor());
        memes.put("gone", new Meme("gone", "leaver@example.com", "png", new byte[]{1}));

        markThenErase("leaver@example.com", Optional.of(new PurgeRule.Delete()));

        assertTrue(memes.isEmpty());
        assertEquals(List.of("gone"), announcedDeletions);
    }

    @Test
    @DisplayName("every vote the leaver cast is retracted")
    void retracts_the_leavers_votes() {
        memes.put("other", new Meme("other", "someone@example.com", "png", new byte[]{2}));
        memeVotes.put("other", new HashMap<>(Map.of(
                "leaver@example.com", VoteDirection.UP, "stays@example.com", VoteDirection.UP)));

        markThenErase("leaver@example.com", Optional.empty());

        assertEquals(Map.of("stays@example.com", VoteDirection.UP), memeVotes.get("other"));
    }

    @Test
    @DisplayName("the mark alone destroys nothing — that is what makes the saga compensatable")
    void the_mark_is_reversible() {
        memes.put("reserved", new Meme("reserved", "leaver@example.com", "png", new byte[]{1}));
        contentIndex.put("leavers-content", "reserved");
        memeVotes.put("reserved", new HashMap<>(Map.of("fan@example.com", VoteDirection.UP)));

        mark.execute("leaver@example.com");

        assertTrue(erasure.isMarked("reserved"), "the meme is out of the gallery");
        assertTrue(memes.containsKey("reserved"), "...and still on disk");
        assertEquals(Map.of("fan@example.com", VoteDirection.UP), memeVotes.get("reserved"));
        assertFalse(contentIndex.isEmpty());
        assertTrue(announcedDeletions.isEmpty(), "nobody is told a meme was deleted, because none was");
    }

    @Test
    @DisplayName("the compensation puts the leaver's memes back exactly as they were")
    void restore_undoes_the_mark() {
        memes.put("reserved", new Meme("reserved", "leaver@example.com", "png", new byte[]{1}));
        memeVotes.put("reserved", new HashMap<>(Map.of("fan@example.com", VoteDirection.UP)));
        mark.execute("leaver@example.com");

        restore.execute("leaver@example.com");

        assertFalse(erasure.isMarked("reserved"), "back in the gallery");
        assertEquals("leaver@example.com", memes.get("reserved").author(), "and still theirs");
        assertEquals(Map.of("fan@example.com", VoteDirection.UP), memeVotes.get("reserved"));
    }

    @Test
    @DisplayName("a meme the rule KEEPS comes back to the gallery anonymised, not hidden for ever")
    void kept_memes_leave_the_reservation() {
        adminOverride = Optional.of(new PurgeRule.AnonymizeAuthor());
        memes.put("kept", new Meme("kept", "leaver@example.com", "png", new byte[]{1}));

        markThenErase("leaver@example.com", Optional.empty());

        assertEquals(DeletedAccount.AUTHOR, memes.get("kept").author());
        assertFalse(erasure.isMarked("kept"),
                "a meme nobody is erasing must not sit in the erasure backlog for ever");
    }

    @Test
    @DisplayName("every command arrives twice: marking, erasing and restoring are all idempotent")
    void the_three_commands_survive_redelivery() {
        memes.put("gone", new Meme("gone", "leaver@example.com", "png", new byte[]{1}));

        mark.execute("leaver@example.com");
        mark.execute("leaver@example.com");
        restore.execute("leaver@example.com");
        restore.execute("leaver@example.com");
        assertFalse(erasure.isMarked("gone"));

        mark.execute("leaver@example.com");
        purge.execute("leaver@example.com", Optional.empty());
        purge.execute("leaver@example.com", Optional.empty());

        assertTrue(memes.isEmpty());
        assertEquals(List.of("gone"), announcedDeletions,
                "the second closure finds nothing reserved, so it announces nothing");
    }

    @Test
    @DisplayName("a closure that arrives without a mark erases nothing")
    void the_closure_only_acts_on_what_the_mark_reserved() {
        memes.put("never-marked", new Meme("never-marked", "leaver@example.com", "png", new byte[]{1}));

        purge.execute("leaver@example.com", Optional.empty());

        assertTrue(memes.containsKey("never-marked"),
                "the erasure acts on the reservation, never on 'everything by that author'");
        assertTrue(announcedDeletions.isEmpty());
    }
}
