package com.jrobertgardzinski.memes.config;

import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Epic("Config")
@Feature("Rate limit")
class RateLimitTest {

    @Test
    @DisplayName("the ceiling is per key: one account is capped, another is free")
    void per_key_ceiling() {
        RateLimit limit = new RateLimit(2);
        assertTrue(limit.tryAcquire("alice"));
        assertTrue(limit.tryAcquire("alice"));
        assertFalse(limit.tryAcquire("alice"), "the third upload in a minute is refused");
        assertTrue(limit.tryAcquire("bob"), "a different account is unaffected");
    }

    @Test
    @DisplayName("zero disables the guard")
    void zero_disables() {
        RateLimit limit = new RateLimit(0);
        for (int i = 0; i < 50; i++) {
            assertTrue(limit.tryAcquire("anyone"));
        }
    }

    @Test
    @DisplayName("expired windows are evicted — the map does not remember every caller forever")
    void expired_windows_are_evicted() {
        SteppingClock clock = new SteppingClock();
        RateLimit limit = new RateLimit(2, clock);
        limit.tryAcquire("alice");
        limit.tryAcquire("bob");
        assertTrue(limit.trackedKeys() == 2, "both windows are live inside the minute");

        clock.advanceSeconds(61);
        limit.tryAcquire("carol");   // any call sweeps the expired windows out

        assertTrue(limit.trackedKeys() == 1, "only carol's fresh window survives the sweep");
    }

    @Test
    @DisplayName("after the window expires the same key starts a fresh count")
    void an_expired_window_resets_the_count() {
        SteppingClock clock = new SteppingClock();
        RateLimit limit = new RateLimit(1, clock);
        assertTrue(limit.tryAcquire("alice"));
        assertFalse(limit.tryAcquire("alice"), "the ceiling holds inside the minute");

        clock.advanceSeconds(61);

        assertTrue(limit.tryAcquire("alice"), "a new minute is a new allowance");
    }

    /** A clock the test moves by hand — expiry without actually waiting a minute. */
    private static final class SteppingClock extends java.time.Clock {
        private java.time.Instant now = java.time.Instant.parse("2026-01-01T12:00:00Z");

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public java.time.Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
