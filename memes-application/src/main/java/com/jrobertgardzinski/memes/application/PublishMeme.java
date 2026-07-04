package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Meme;
import com.jrobertgardzinski.memes.image.OptimizedImage;
import com.jrobertgardzinski.memes.image.WebImageOptimizer;

import java.util.UUID;

/**
 * Publishes a meme from a raw upload: optimises the image to a browser-friendly format, then
 * claims the content atomically — if that exact image is already stored (or a simultaneous upload
 * beat us to it), the existing meme's id is returned and nothing new is stored; otherwise the new
 * meme is saved under the claimed id.
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

    public String execute(byte[] rawImage, String author) {
        OptimizedImage optimized = optimizer.optimize(rawImage);
        String candidate = UUID.randomUUID().toString();
        String owner = contentIndex.claim(optimized.data(), candidate);
        if (!owner.equals(candidate)) {
            return owner;               // the picture is already up — nothing new is stored
        }
        repository.save(new Meme(candidate, author, optimized.format(), optimized.data()));
        return candidate;
    }
}
