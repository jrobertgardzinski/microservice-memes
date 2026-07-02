package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.VoteRepository;
import com.jrobertgardzinski.memes.domain.RankedMeme;
import com.jrobertgardzinski.memes.domain.VoteDirection;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link VoteRepository}: remembers each voter's current vote per meme, so re-voting
 * replaces instead of stacking. Score = up-voters minus down-voters.
 */
@Component
class InMemoryVoteRepository implements VoteRepository {

    private final Map<String, Map<String, VoteDirection>> votesByMeme = new ConcurrentHashMap<>();

    @Override
    public void castVote(String memeId, String voter, VoteDirection direction) {
        votesByMeme.computeIfAbsent(memeId, id -> new ConcurrentHashMap<>()).put(voter, direction);
    }

    @Override
    public int scoreOf(String memeId) {
        return score(votesByMeme.getOrDefault(memeId, Map.of()));
    }

    @Override
    public List<RankedMeme> allScores() {
        return votesByMeme.entrySet().stream()
                .map(entry -> new RankedMeme(entry.getKey(), score(entry.getValue())))
                .toList();
    }

    private static int score(Map<String, VoteDirection> votes) {
        return votes.values().stream().mapToInt(d -> d == VoteDirection.UP ? 1 : -1).sum();
    }
}
