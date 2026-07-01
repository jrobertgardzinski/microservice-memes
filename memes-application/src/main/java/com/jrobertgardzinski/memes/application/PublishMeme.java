package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Meme;
import com.jrobertgardzinski.memes.image.OptimizedImage;
import com.jrobertgardzinski.memes.image.WebImageOptimizer;

import java.util.Optional;
import java.util.UUID;

/**
 * Publishes a meme from a raw upload: optimises the image to a browser-friendly format; if that
 * exact image is already stored, returns the existing meme's id (deduplication); otherwise stores it
 * and returns the new id.
 */
public class PublishMeme {

    private final WebImageOptimizer optimizer;
    private final MemeRepository repository;
    private final MemeContentIndex contentIndex;

    public PublishMeme(WebImageOptimizer optimizer, MemeRepository repository, MemeContentIndex contentIndex) {
        this.optimizer = optimizer;
        this.repository = repository;
        this.contentIndex = contentIndex;
    }

    public String execute(byte[] rawImage) {
        OptimizedImage optimized = optimizer.optimize(rawImage);
        Optional<String> existing = contentIndex.findIdByContent(optimized.data());
        if (existing.isPresent()) {
            return existing.get();
        }
        Meme meme = new Meme(UUID.randomUUID().toString(), optimized.format(), optimized.data());
        repository.save(meme);
        contentIndex.index(optimized.data(), meme.id());
        return meme.id();
    }
}
