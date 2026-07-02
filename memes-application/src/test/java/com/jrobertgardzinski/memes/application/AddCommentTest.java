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

        public List<String> findIdsByAuthor(String author) {
            return memes.values().stream().filter(m -> m.author().equals(author)).map(Meme::id).toList();
        }

        public void deleteById(String memeId) {
            memes.remove(memeId);
        }

        public void anonymizeAuthor(String author, String replacement) {
            memes.replaceAll((id, m) -> m.author().equals(author)
                    ? new Meme(m.id(), replacement, m.format(), m.data()) : m);
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

        public List<Comment> findByAuthor(String author) {
            return comments.stream().filter(c -> c.author().equals(author)).toList();
        }

        public void deleteByAuthor(String author) {
            comments.removeIf(c -> c.author().equals(author));
        }

        public void anonymizeAuthor(String author, String replacement) {
            comments.replaceAll(c -> c.author().equals(author)
                    ? new Comment(c.id(), c.memeId(), replacement, c.text()) : c);
        }
    };
    private final AddComment addComment = new AddComment(memeRepository, commentRepository);

    @Test
    @DisplayName("adds a comment to an existing meme")
    void adds_a_comment() {
        memes.put("m1", new Meme("m1", "alice@example.com", "png", new byte[]{1}));

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
