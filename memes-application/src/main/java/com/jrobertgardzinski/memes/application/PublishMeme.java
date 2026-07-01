package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Meme;
import com.jrobertgardzinski.memes.image.OptimizedImage;
import com.jrobertgardzinski.memes.image.WebImageOptimizer;

import java.util.UUID;

/**
 * Publishes a meme from a raw upload: optimises the image to a browser-friendly format, then stores
 * it and returns its id.
 */
public class PublishMeme {

    private final WebImageOptimizer optimizer;
    private final MemeRepository repository;

    public PublishMeme(WebImageOptimizer optimizer, MemeRepository repository) {
        this.optimizer = optimizer;
        this.repository = repository;
    }

    public String execute(byte[] rawImage) {
        OptimizedImage optimized = optimizer.optimize(rawImage);
        Meme meme = new Meme(UUID.randomUUID().toString(), optimized.format(), optimized.data());
        repository.save(meme);
        return meme.id();
    }
}
