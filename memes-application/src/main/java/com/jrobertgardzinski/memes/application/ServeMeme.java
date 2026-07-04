package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Meme;

import java.util.Optional;

/**
 * Serves a meme's image in the best format the caller accepts: WebP when they asked for it and the
 * encoder can produce it (cached in the {@link ObjectStore} under a per-meme WebP key, so it is
 * encoded once), otherwise the stored PNG. The decision — negotiate, cache, fall back — lives here,
 * not in the web boundary.
 */
public class ServeMeme {

    /** The bytes to serve and the content type they carry. */
    public record Image(byte[] data, String contentType) {}

    private final MemeRepository memes;
    private final ObjectStore objects;
    private final ImageEncoder encoder;

    public ServeMeme(MemeRepository memes, ObjectStore objects, ImageEncoder encoder) {
        this.memes = memes;
        this.objects = objects;
        this.encoder = encoder;
    }

    public Optional<Image> execute(String memeId, boolean wantsWebp) {
        Optional<Meme> meme = memes.find(memeId);
        if (meme.isEmpty()) {
            return Optional.empty();
        }
        byte[] png = meme.get().data();
        if (!wantsWebp) {
            return Optional.of(new Image(png, "image/png"));
        }
        String webpKey = memeId + ".webp";
        Optional<byte[]> cached = objects.get(webpKey);
        if (cached.isPresent()) {
            return Optional.of(new Image(cached.get(), "image/webp"));
        }
        return encoder.toWebp(png)
                .map(webp -> {
                    objects.put(webpKey, webp);        // encode once, then serve from cache
                    return new Image(webp, "image/webp");
                })
                .or(() -> Optional.of(new Image(png, "image/png")));   // encoder down: PNG still works
    }
}
