package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.MemeRepository;
import com.jrobertgardzinski.memes.domain.Meme;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link MemeRepository}. A real store (object storage for the bytes + a database for
 * metadata) will replace it.
 */
@Component
class InMemoryMemeRepository implements MemeRepository {

    private final Map<String, Meme> byId = new ConcurrentHashMap<>();
    private final List<String> insertionOrder = new CopyOnWriteArrayList<>();

    @Override
    public void save(Meme meme) {
        byId.put(meme.id(), meme);
        insertionOrder.add(meme.id());
    }

    @Override
    public Optional<Meme> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<String> allIds() {
        return List.copyOf(insertionOrder.reversed());
    }

    @Override
    public List<String> findIdsByAuthor(String author) {
        return byId.values().stream()
                .filter(meme -> meme.author().equals(author))
                .map(Meme::id)
                .toList();
    }

    @Override
    public void deleteById(String memeId) {
        byId.remove(memeId);
        insertionOrder.remove(memeId);
    }

    @Override
    public void reassignAuthor(String memeId, String newAuthor) {
        byId.computeIfPresent(memeId, (id, meme) -> new Meme(meme.id(), newAuthor, meme.format(), meme.data()));
    }
}
