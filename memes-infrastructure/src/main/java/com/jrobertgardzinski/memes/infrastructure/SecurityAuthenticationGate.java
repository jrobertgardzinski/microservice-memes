package com.jrobertgardzinski.memes.infrastructure;

import java.util.Optional;

/**
 * Boundary gate to the standalone {@code microservice-security}: resolves an access token to the
 * {@link Caller} (e-mail + roles), or empty when the token is missing, invalid or expired.
 * Implemented by an HTTP adapter in production and a stub in tests.
 */
interface SecurityAuthenticationGate {

    Optional<Caller> callerFor(String accessToken);
}
