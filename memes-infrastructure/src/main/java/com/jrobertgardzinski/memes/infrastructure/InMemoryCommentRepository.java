package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.CommentRepository;
import com.jrobertgardzinski.memes.domain.Comment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory {@link CommentRepository}. A real store will replace it. */
@Component
class InMemoryCommentRepository implements CommentRepository {

    private final List<Comment> comments = new CopyOnWriteArrayList<>();

    @Override
    public void save(Comment comment) {
        comments.add(comment);
    }

    @Override
    public List<Comment> findByMeme(String memeId) {
        return comments.stream().filter(comment -> comment.memeId().equals(memeId)).toList();
    }

    @Override
    public Optional<Comment> find(String commentId) {
        return comments.stream().filter(comment -> comment.id().equals(commentId)).findFirst();
    }

    @Override
    public void deleteByMeme(String memeId) {
        comments.removeIf(comment -> comment.memeId().equals(memeId));
    }

    @Override
    public List<Comment> findByAuthor(String author) {
        return comments.stream().filter(comment -> comment.author().equals(author)).toList();
    }

    @Override
    public void delete(String commentId) {
        comments.removeIf(comment -> comment.id().equals(commentId));
    }

    @Override
    public void reassignAuthor(String commentId, String newAuthor) {
        comments.replaceAll(comment -> comment.id().equals(commentId)
                ? new Comment(comment.id(), comment.memeId(), newAuthor, comment.text())
                : comment);
    }
}
