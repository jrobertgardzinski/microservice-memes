package com.jrobertgardzinski.memes.infrastructure;

import java.util.Optional;

/**
 * Boundary gate to the standalone {@code microservice-security}: resolves an access token to the
 * signed-in user's e-mail, or empty when the token is missing, invalid or expired. Implemented by
 * an HTTP adapter in production and a stub in tests.
 */
interface SecurityAuthenticationGate {

    Optional<String> emailFor(String accessToken);
}
