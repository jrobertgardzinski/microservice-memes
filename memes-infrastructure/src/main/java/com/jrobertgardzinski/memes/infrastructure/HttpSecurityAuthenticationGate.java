package com.jrobertgardzinski.memes.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Optional;

/**
 * Production {@link SecurityAuthenticationGate}: asks {@code microservice-security}'s protected
 * {@code GET /me} who the token belongs to (200 → the e-mail; 401/unreachable → empty). The same
 * pattern security itself uses towards microservice-email — services trust each other over HTTP,
 * never by sharing a database.
 */
@Component
class HttpSecurityAuthenticationGate implements SecurityAuthenticationGate {

    private final RestClient securityService;

    HttpSecurityAuthenticationGate(@Value("${security.url}") String securityUrl) {
        this.securityService = RestClient.create(securityUrl);
    }

    @Override
    public Optional<String> emailFor(String accessToken) {
        try {
            Map<?, ?> body = securityService.get().uri("/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(Map.class);
            return Optional.ofNullable(body == null ? null : (String) body.get("email"));
        } catch (RestClientException invalidTokenOrServiceDown) {
            return Optional.empty();
        }
    }
}
