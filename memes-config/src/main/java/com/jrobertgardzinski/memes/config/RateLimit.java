package com.jrobertgardzinski.memes.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A fixed per-key, per-minute window — server policy against abuse. Keyed by the caller (here the
 * uploader's e-mail), so one account cannot flood the gallery while everyone else uploads freely.
 * Uploads are heavier than comments (decode + optimise), which is exactly why they get a ceiling.
 * Zero disables the guard. Pure logic; no framework.
 */
public final class RateLimit {

    private record Window(Instant start, int count) {}

    private final int perMinute;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimit(int perMinute) {
        this(perMinute, Clock.systemUTC());
    }

    /** The clock is injectable so expiry (and the eviction it drives) is testable without waiting. */
    public RateLimit(int perMinute, Clock clock) {
        this.perMinute = perMinute;
        this.clock = clock;
    }

    /** Record one action for the key; false when it exceeds the ceiling this minute. */
    public boolean tryAcquire(String key) {
        if (perMinute <= 0) {
            return true;
        }
        Instant now = clock.instant();
        // evict windows that already expired: without this, every uploader e-mail ever seen stays
        // in the map for the life of the process — a slow leak, and a cheap DoS via made-up keys.
        // Linear over the map, but the map only holds keys active in the last minute.
        windows.values().removeIf(window -> expired(window, now));
        Window updated = windows.compute(key, (k, current) ->
                current == null || expired(current, now)
                        ? new Window(now, 1)
                        : new Window(current.start(), current.count() + 1));
        return updated.count() <= perMinute;
    }

    /** How many callers are currently tracked — exposed for the eviction pin, not for policy. */
    int trackedKeys() {
        return windows.size();
    }

    private static boolean expired(Window window, Instant now) {
        return Duration.between(window.start(), now).toSeconds() >= 60;
    }
}
