package com.jrobertgardzinski.memes.infrastructure;

import java.util.Set;

/**
 * Who is making a request, as microservice-security sees them: the signed-in e-mail and the roles
 * it reports (from {@code GET /me}). A MODERATOR or ADMIN may act on other people's content; a
 * plain USER acts only on their own.
 */
record Caller(String email, Set<String> roles) {

    boolean isModerator() {
        return roles.contains("MODERATOR") || roles.contains("ADMIN");
    }
}
