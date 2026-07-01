package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.CommentRepository;
import com.jrobertgardzinski.memes.domain.Comment;
import org.springframework.stereotype.Component;

import java.util.List;
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
}
