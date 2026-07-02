package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.CommentVoteRepository;
import com.jrobertgardzinski.memes.domain.VoteDirection;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link CommentVoteRepository}: each voter's current vote per comment. */
@Component
class InMemoryCommentVoteRepository implements CommentVoteRepository {

    private final Map<String, Map<String, VoteDirection>> votesByComment = new ConcurrentHashMap<>();

    @Override
    public void castVote(String commentId, String voter, VoteDirection direction) {
        votesByComment.computeIfAbsent(commentId, id -> new ConcurrentHashMap<>()).put(voter, direction);
    }

    @Override
    public void retractVote(String commentId, String voter) {
        votesByComment.getOrDefault(commentId, Map.of()).remove(voter);
    }

    @Override
    public Optional<VoteDirection> voteOf(String commentId, String voter) {
        return Optional.ofNullable(votesByComment.getOrDefault(commentId, Map.of()).get(voter));
    }

    @Override
    public int scoreOf(String commentId) {
        return votesByComment.getOrDefault(commentId, Map.of()).values().stream()
                .mapToInt(d -> d == VoteDirection.UP ? 1 : -1).sum();
    }

    @Override
    public void purgeComment(String commentId) {
        votesByComment.remove(commentId);
    }

    @Override
    public void purgeVoter(String voter) {
        votesByComment.values().forEach(votes -> votes.remove(voter));
    }
}
