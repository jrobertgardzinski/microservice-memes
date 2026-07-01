package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.MemeRepository;
import com.jrobertgardzinski.memes.domain.Meme;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link MemeRepository}. A real store (object storage for the bytes + a database for
 * metadata) will replace it.
 */
@Component
class InMemoryMemeRepository implements MemeRepository {

    private final Map<String, Meme> byId = new ConcurrentHashMap<>();

    @Override
    public void save(Meme meme) {
        byId.put(meme.id(), meme);
    }

    @Override
    public Optional<Meme> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }
}
