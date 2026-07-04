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

    private final PurgeUserContent purge = new PurgeUserContent(memeRepository, voteRepository, index, tagRepository, memeEvents, new PurgeRule.Delete());

    @Test
    @DisplayName("the leaver's memes disappear with their votes; the thread owner is told")
    void purges_memes_and_announces() {
        memes.put("leavers-meme", new Meme("leavers-meme", "leaver@example.com", "png", new byte[]{1}));
        contentIndex.put("leavers-content", "leavers-meme");
        memeVotes.put("leavers-meme", new HashMap<>(Map.of("somebody-else@example.com", VoteDirection.UP)));

        purge.execute("leaver@example.com", Optional.empty());

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

        purge.execute("leaver@example.com", Optional.of(new PurgeRule.KeepPopularAnonymized(2)));

        assertEquals(DeletedAccount.AUTHOR, memes.get("hit").author());
        assertFalse(memes.containsKey("flop"));
        assertEquals(List.of("flop"), announcedDeletions); // only the deleted one is announced
    }

    @Test
    @DisplayName("every vote the leaver cast is retracted")
    void retracts_the_leavers_votes() {
        memes.put("other", new Meme("other", "someone@example.com", "png", new byte[]{2}));
        memeVotes.put("other", new HashMap<>(Map.of(
                "leaver@example.com", VoteDirection.UP, "stays@example.com", VoteDirection.UP)));

        purge.execute("leaver@example.com", Optional.empty());

        assertEquals(Map.of("stays@example.com", VoteDirection.UP), memeVotes.get("other"));
    }
}
