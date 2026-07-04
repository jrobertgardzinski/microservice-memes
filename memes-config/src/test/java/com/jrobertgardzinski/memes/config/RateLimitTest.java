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
}
