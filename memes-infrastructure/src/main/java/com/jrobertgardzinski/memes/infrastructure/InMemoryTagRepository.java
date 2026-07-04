package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.TagRepository;
import com.jrobertgardzinski.memes.tags.Tag;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link TagRepository}: both directions of the index, kept consistent on replace. */
@Component
class InMemoryTagRepository implements TagRepository {

    private final Map<String, Set<Tag>> tagsByMeme = new ConcurrentHashMap<>();
    private final Map<Tag, Set<String>> memesByTag = new ConcurrentHashMap<>();

    @Override
    public synchronized void replaceTags(String memeId, Set<Tag> tags) {
        removeMeme(memeId);
        tagsByMeme.put(memeId, Set.copyOf(tags));
        for (Tag tag : tags) {
            memesByTag.computeIfAbsent(tag, t -> ConcurrentHashMap.newKeySet()).add(memeId);
        }
    }

    @Override
    public Set<Tag> tagsOf(String memeId) {
        return tagsByMeme.getOrDefault(memeId, Set.of());
    }

    @Override
    public Set<String> memesTagged(Tag tag) {
        return Set.copyOf(memesByTag.getOrDefault(tag, Set.of()));
    }

    @Override
    public synchronized void removeMeme(String memeId) {
        Set<Tag> previous = tagsByMeme.remove(memeId);
        if (previous != null) {
            previous.forEach(tag -> memesByTag.getOrDefault(tag, Set.of()).remove(memeId));
        }
    }
}
