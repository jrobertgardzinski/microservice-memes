package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.VoteRepository;
import com.jrobertgardzinski.memes.domain.RankedMeme;
import com.jrobertgardzinski.memes.domain.VoteDirection;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link VoteRepository}: keeps a running score (up-votes minus down-votes) per meme. */
@Component
class InMemoryVoteRepository implements VoteRepository {

    private final Map<String, Integer> scoreByMeme = new ConcurrentHashMap<>();

    @Override
    public void castVote(String memeId, VoteDirection direction) {
        scoreByMeme.merge(memeId, direction == VoteDirection.UP ? 1 : -1, Integer::sum);
    }

    @Override
    public int scoreOf(String memeId) {
        return scoreByMeme.getOrDefault(memeId, 0);
    }

    @Override
    public List<RankedMeme> allScores() {
        return scoreByMeme.entrySet().stream()
                .map(entry -> new RankedMeme(entry.getKey(), entry.getValue()))
                .toList();
    }
}
