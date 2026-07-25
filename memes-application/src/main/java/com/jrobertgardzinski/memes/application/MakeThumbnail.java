package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.config.ThumbnailSize;
import com.jrobertgardzinski.memes.image.InvalidImageException;
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
                .map(meme -> decodeStored(memeId, meme.data()))
                .map(OptimizedImage::data);
    }

    /**
     * These are STORED bytes, already validated by the optimizer at upload — if they no longer
     * decode, the server's data went bad, not the caller's request. InvalidImageException would
     * ride the web boundary's "bad upload" mapping down to a 400 blaming the caller; re-signal it
     * as the server fault it is (WebErrorHandler's floor answers a 500 with a generic body).
     */
    private OptimizedImage decodeStored(String memeId, byte[] stored) {
        try {
            return optimizer.toPngWithin(stored, thumbnailSize.maxDimension());
        } catch (InvalidImageException corruptedInStore) {
            throw new IllegalStateException(
                    "stored image no longer decodes — data corruption for meme " + memeId,
                    corruptedInStore);
        }
    }
}
