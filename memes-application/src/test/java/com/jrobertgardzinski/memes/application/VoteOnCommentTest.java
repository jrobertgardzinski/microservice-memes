package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Comment;
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
@Feature("Vote on comment")
class VoteOnCommentTest {

    private final List<Comment> comments = new ArrayList<>();
    private final Map<String, Map<String, VoteDirection>> votes = new HashMap<>();

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
    private final CommentVoteRepository commentVoteRepository = new CommentVoteRepository() {
        public void castVote(String commentId, String voter, VoteDirection direction) {
            votes.computeIfAbsent(commentId, id -> new HashMap<>()).put(voter, direction);
        }

        public void retractVote(String commentId, String voter) {
            votes.getOrDefault(commentId, Map.of()).remove(voter);
        }

        public Optional<VoteDirection> voteOf(String commentId, String voter) {
            return Optional.ofNullable(votes.getOrDefault(commentId, Map.of()).get(voter));
        }

        public int scoreOf(String commentId) {
            return votes.getOrDefault(commentId, Map.of()).values().stream()
                    .mapToInt(d -> d == VoteDirection.UP ? 1 : -1).sum();
        }

        public void purgeComment(String commentId) {
            votes.remove(commentId);
        }

        public void purgeVoter(String voter) {
            votes.values().forEach(v -> v.remove(voter));
        }
    };
    private final VoteOnComment voteOnComment = new VoteOnComment(commentRepository, commentVoteRepository);

    @Test
    @DisplayName("votes on a comment: toggle retracts, the opposite direction switches")
    void votes_on_a_comment() {
        comments.add(new Comment("c1", "m1", "alice", "great"));

        assertEquals(Optional.of(new VoteTally(1, Optional.of(VoteDirection.UP))),
                voteOnComment.execute("m1", "c1", "bob", VoteDirection.UP));
        assertEquals(Optional.of(new VoteTally(0, Optional.empty())),
                voteOnComment.execute("m1", "c1", "bob", VoteDirection.UP)); // retracted
        assertEquals(Optional.of(new VoteTally(-1, Optional.of(VoteDirection.DOWN))),
                voteOnComment.execute("m1", "c1", "bob", VoteDirection.DOWN));
    }

    @Test
    @DisplayName("refuses a vote on a missing comment or one under another meme")
    void refuses_missing_or_misplaced_comment() {
        comments.add(new Comment("c1", "m1", "alice", "great"));

        assertTrue(voteOnComment.execute("m1", "nope", "bob", VoteDirection.UP).isEmpty());
        assertTrue(voteOnComment.execute("other-meme", "c1", "bob", VoteDirection.UP).isEmpty());
        assertTrue(votes.isEmpty());
    }
}
