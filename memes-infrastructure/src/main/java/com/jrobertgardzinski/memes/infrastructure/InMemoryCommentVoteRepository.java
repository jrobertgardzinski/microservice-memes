package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.CommentVoteRepository;
import com.jrobertgardzinski.memes.domain.VoteDirection;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link CommentVoteRepository}: one remembered vote per voter per comment. */
@Component
class InMemoryCommentVoteRepository implements CommentVoteRepository {

    private final Map<String, Map<String, VoteDirection>> votesByComment = new ConcurrentHashMap<>();

    @Override
    public void castVote(String commentId, String voter, VoteDirection direction) {
        votesByComment.computeIfAbsent(commentId, id -> new ConcurrentHashMap<>()).put(voter, direction);
    }

    @Override
    public int scoreOf(String commentId) {
        return votesByComment.getOrDefault(commentId, Map.of()).values().stream()
                .mapToInt(d -> d == VoteDirection.UP ? 1 : -1).sum();
    }
}
