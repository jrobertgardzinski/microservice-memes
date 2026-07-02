package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Comment;
import com.jrobertgardzinski.memes.domain.Meme;
import com.jrobertgardzinski.memes.domain.RankedMeme;
import com.jrobertgardzinski.memes.domain.VoteDirection;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@Epic("Use case")
@Feature("Purge user content")
class PurgeUserContentTest {

    private final Map<String, Meme> memes = new HashMap<>();
    private final List<Comment> comments = new ArrayList<>();
    private final Map<String, Map<String, VoteDirection>> memeVotes = new HashMap<>();
    private final Map<String, Map<String, VoteDirection>> commentVotes = new HashMap<>();
    private final Map<String, String> contentIndex = new HashMap<>();

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
    };
    private final CommentRepository commentRepository = new CommentRepository() {
        public void save(Comment comment) {
            comments.add(comment);
        }

        public List<Comment> findByMeme(String memeId) {
            return comments.stream().filter(c -> c.memeId().equals(memeId)).toList();
        }

        public Optional<Comment> find(String commentId) {
            return comments.stream().filter(c -> c.id().equals(commentId)).findFirst();
        }

        public void deleteByMeme(String memeId) {
            comments.removeIf(c -> c.memeId().equals(memeId));
        }

        public void anonymizeAuthor(String author, String replacement) {
            comments.replaceAll(c -> c.author().equals(author)
                    ? new Comment(c.id(), c.memeId(), replacement, c.text()) : c);
        }
    };
    private final VoteRepository voteRepository = new VoteRepository() {
        public void castVote(String memeId, String voter, VoteDirection direction) {
            memeVotes.computeIfAbsent(memeId, id -> new HashMap<>()).put(voter, direction);
        }

        public void retractVote(String memeId, String voter) {
            memeVotes.getOrDefault(memeId, Map.of()).remove(voter);
        }

        public Optional<VoteDirection> voteOf(String memeId, String voter) {
            return Optional.ofNullable(memeVotes.getOrDefault(memeId, Map.of()).get(voter));
        }

        public int scoreOf(String memeId) {
            return memeVotes.getOrDefault(memeId, Map.of()).size();
        }

        public List<RankedMeme> allScores() {
            return List.of();
        }

        public void purgeMeme(String memeId) {
            memeVotes.remove(memeId);
        }

        public void purgeVoter(String voter) {
            memeVotes.values().forEach(v -> v.remove(voter));
        }
    };
    private final CommentVoteRepository commentVoteRepository = new CommentVoteRepository() {
        public void castVote(String commentId, String voter, VoteDirection direction) {
            commentVotes.computeIfAbsent(commentId, id -> new HashMap<>()).put(voter, direction);
        }

        public void retractVote(String commentId, String voter) {
            commentVotes.getOrDefault(commentId, Map.of()).remove(voter);
        }

        public Optional<VoteDirection> voteOf(String commentId, String voter) {
            return Optional.ofNullable(commentVotes.getOrDefault(commentId, Map.of()).get(voter));
        }

        public int scoreOf(String commentId) {
            return commentVotes.getOrDefault(commentId, Map.of()).size();
        }

        public void purgeComment(String commentId) {
            commentVotes.remove(commentId);
        }

        public void purgeVoter(String voter) {
            commentVotes.values().forEach(v -> v.remove(voter));
        }
    };
    private final MemeContentIndex index = new MemeContentIndex() {
        public Optional<String> findIdByContent(byte[] data) {
            return Optional.ofNullable(contentIndex.get(new String(data)));
        }

        public void index(byte[] data, String memeId) {
            contentIndex.put(new String(data), memeId);
        }

        public void remove(String memeId) {
            contentIndex.values().removeIf(memeId::equals);
        }
    };

    private final PurgeUserContent purge = new PurgeUserContent(
            memeRepository, commentRepository, voteRepository, commentVoteRepository, index);

    @Test
    @DisplayName("the leaver's memes disappear with their whole comment threads and votes")
    void purges_memes_with_their_threads() {
        memes.put("leavers-meme", new Meme("leavers-meme", "leaver@example.com", "png", new byte[]{1}));
        contentIndex.put("leavers-content", "leavers-meme");
        comments.add(new Comment("c1", "leavers-meme", "somebody-else@example.com", "nice"));
        memeVotes.put("leavers-meme", new HashMap<>(Map.of("somebody-else@example.com", VoteDirection.UP)));
        commentVotes.put("c1", new HashMap<>(Map.of("third@example.com", VoteDirection.UP)));

        purge.execute("leaver@example.com");

        assertTrue(memes.isEmpty());
        assertTrue(comments.isEmpty());
        assertTrue(memeVotes.isEmpty());
        assertTrue(commentVotes.isEmpty());
        assertTrue(contentIndex.isEmpty()); // identical re-uploads must not dedup into a ghost
    }

    @Test
    @DisplayName("the leaver's comments under other memes stay, signed 'deleted account'")
    void anonymizes_comments_elsewhere() {
        memes.put("other", new Meme("other", "someone@example.com", "png", new byte[]{2}));
        comments.add(new Comment("c2", "other", "leaver@example.com", "Lorem ipsum"));

        purge.execute("leaver@example.com");

        assertEquals(1, comments.size());
        assertEquals(Comment.DELETED_ACCOUNT_AUTHOR, comments.get(0).author());
        assertEquals("Lorem ipsum", comments.get(0).text()); // the text survives, the identity does not
        assertTrue(memes.containsKey("other"));
    }

    @Test
    @DisplayName("every vote the leaver cast is retracted")
    void retracts_the_leavers_votes() {
        memes.put("other", new Meme("other", "someone@example.com", "png", new byte[]{2}));
        memeVotes.put("other", new HashMap<>(Map.of(
                "leaver@example.com", VoteDirection.UP, "stays@example.com", VoteDirection.UP)));
        commentVotes.put("c3", new HashMap<>(Map.of("leaver@example.com", VoteDirection.DOWN)));

        purge.execute("leaver@example.com");

        assertEquals(Map.of("stays@example.com", VoteDirection.UP), memeVotes.get("other"));
        assertTrue(commentVotes.get("c3").isEmpty());
    }
}
