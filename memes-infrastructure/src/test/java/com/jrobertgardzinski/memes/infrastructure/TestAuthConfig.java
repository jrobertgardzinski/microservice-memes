package com.jrobertgardzinski.memes.infrastructure;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Optional;

/**
 * Test double for the security integration: the gate accepts one well-known token and maps it to
 * one well-known user, so tests need no running microservice-security. The real HTTP gate is
 * exercised end to end by the workspace's compose smoke test.
 */
@TestConfiguration
public class TestAuthConfig {

    public static final String VALID_TOKEN = "test-token";
    public static final String SIGNED_IN_USER = "alice@example.com";

    @Bean
    @Primary
    SecurityAuthenticationGate stubSecurityAuthenticationGate() {
        return token -> VALID_TOKEN.equals(token) ? Optional.of(SIGNED_IN_USER) : Optional.empty();
    }
}
