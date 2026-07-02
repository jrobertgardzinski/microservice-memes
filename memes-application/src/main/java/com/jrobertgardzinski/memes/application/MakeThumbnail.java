package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.config.ThumbnailSize;
import com.jrobertgardzinski.memes.image.OptimizedImage;
import com.jrobertgardzinski.memes.image.WebImageOptimizer;

import java.util.Optional;

/**
 * Makes a thumbnail (small PNG) of a stored meme on demand. Empty if there is no such meme.
 */
public class MakeThumbnail {

    private final MemeRepository memeRepository;
    private final WebImageOptimizer optimizer;
    private final ThumbnailSize thumbnailSize;

    public MakeThumbnail(MemeRepository memeRepository, WebImageOptimizer optimizer, ThumbnailSize thumbnailSize) {
        this.memeRepository = memeRepository;
        this.optimizer = optimizer;
        this.thumbnailSize = thumbnailSize;
    }

    public Optional<byte[]> execute(String memeId) {
        return memeRepository.find(memeId)
                .map(meme -> optimizer.toPngWithin(meme.data(), thumbnailSize.maxDimension()))
                .map(OptimizedImage::data);
    }
}
