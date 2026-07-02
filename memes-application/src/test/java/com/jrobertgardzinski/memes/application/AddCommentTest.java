package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Comment;
import com.jrobertgardzinski.memes.domain.Meme;
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
@Feature("Add comment")
class AddCommentTest {

    private final Map<String, Meme> memes = new HashMap<>();
    private final List<Comment> comments = new ArrayList<>();

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
    };
    private final AddComment addComment = new AddComment(memeRepository, commentRepository);

    @Test
    @DisplayName("adds a comment to an existing meme")
    void adds_a_comment() {
        memes.put("m1", new Meme("m1", "png", new byte[]{1}));

        Optional<Comment> added = addComment.execute("m1", "alice", "great meme");

        assertTrue(added.isPresent());
        assertEquals("alice", added.get().author());
        assertEquals(1, commentRepository.findByMeme("m1").size());
    }

    @Test
    @DisplayName("refuses to comment on a missing meme")
    void refuses_missing_meme() {
        Optional<Comment> added = addComment.execute("nope", "alice", "hello");

        assertTrue(added.isEmpty());
        assertTrue(comments.isEmpty());
    }
}
