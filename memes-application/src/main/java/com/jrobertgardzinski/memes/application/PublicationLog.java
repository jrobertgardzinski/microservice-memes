package com.jrobertgardzinski.memes.application;

import java.time.Instant;
import java.util.Optional;

/**
 * When a meme went up — the fact the hot ranking decays by. Kept as its own port so the ranking
 * does not need the whole {@link MemeRepository}; the store that persists memes answers it.
 * An unknown meme reads as empty and is treated as brand new (no decay), which fails safe.
 */
public interface PublicationLog {

    Optional<Instant> publishedAt(String memeId);
}
