package com.jrobertgardzinski.memes.infrastructure;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Optional;
import java.util.Set;

/**
 * Test double for the security integration: the gate accepts one well-known token and maps it to
 * one well-known user, so tests need no running microservice-security. The real HTTP gate is
 * exercised end to end by the workspace's compose smoke test.
 */
@TestConfiguration
public class TestAuthConfig {

    public static final String VALID_TOKEN = "test-token";
    public static final String SIGNED_IN_USER = "alice@example.com";
    /**
     * The same person as {@link #SIGNED_IN_USER}, signing in after security confirmed a change of
     * address — a new token, because the old sessions are revoked by the move, and a new subject,
     * because the subject IS the address.
     */
    public static final String RENAMED_TOKEN = "test-token-alice-renamed";
    public static final String RENAMED_USER = "alice.new@example.com";
    public static final String SECOND_TOKEN = "test-token-bob";
    public static final String SECOND_USER = "bob@example.com";
    public static final String MODERATOR_TOKEN = "test-token-mod";
    public static final String MODERATOR_USER = "mod@example.com";
    public static final String ADMIN_TOKEN = "test-token-admin";
    public static final String ADMIN_USER = "admin@example.com";
    /** Tokens minted after the cutover: the subject is the id, the address is a claim. */
    public static final com.jrobertgardzinski.identity.UserId ALICE_ID =
            com.jrobertgardzinski.identity.UserId.of("6f1d2c3b-4a59-4e8f-9b0c-1d2e3f4a5b6c");
    public static final String ALICE_ID_TOKEN = "test-token-alice-id";
    /** The same id after a change of address. */
    public static final String ALICE_ID_RENAMED_TOKEN = "test-token-alice-id-renamed";
    /** Alice's old address under a brand-new account. */
    public static final String IMPOSTOR_TOKEN = "test-token-impostor";

    @Bean
    @Primary
    SecurityAuthenticationGate stubSecurityAuthenticationGate() {
        return token -> switch (token == null ? "" : token) {
            case VALID_TOKEN -> Optional.of(new Caller(SIGNED_IN_USER, Set.of("USER")));
            case RENAMED_TOKEN -> Optional.of(new Caller(RENAMED_USER, Set.of("USER")));
            case SECOND_TOKEN -> Optional.of(new Caller(SECOND_USER, Set.of("USER")));
            case MODERATOR_TOKEN -> Optional.of(new Caller(MODERATOR_USER, Set.of("USER", "MODERATOR")));
            case ADMIN_TOKEN -> Optional.of(new Caller(ADMIN_USER, Set.of("USER", "ADMIN")));
            case ALICE_ID_TOKEN -> Optional.of(new Caller(SIGNED_IN_USER, Optional.of(ALICE_ID), Set.of("USER")));
            case ALICE_ID_RENAMED_TOKEN -> Optional.of(new Caller(RENAMED_USER, Optional.of(ALICE_ID), Set.of("USER")));
            case IMPOSTOR_TOKEN -> Optional.of(new Caller(SIGNED_IN_USER,
                    Optional.of(com.jrobertgardzinski.identity.UserId.random()), Set.of("USER")));
            default -> Optional.empty();
        };
    }
}
