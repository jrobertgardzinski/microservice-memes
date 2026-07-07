package com.jrobertgardzinski.memes.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.slf4j.MDC;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Production {@link SecurityAuthenticationGate}: asks {@code microservice-security}'s protected
 * {@code GET /me} who the token belongs to (200 → the e-mail; 401/unreachable → empty). The same
 * pattern security itself uses towards microservice-email — services trust each other over HTTP,
 * never by sharing a database.
 */
@Component
@ConditionalOnProperty(name = "security.verify", havingValue = "introspect", matchIfMissing = true)
class HttpSecurityAuthenticationGate implements SecurityAuthenticationGate {

    private final RestClient securityService;

    HttpSecurityAuthenticationGate(@Value("${security.url}") String securityUrl) {
        this.securityService = RestClient.create(securityUrl);
    }

    @Override
    public Optional<Caller> callerFor(String accessToken) {
        try {
            String cid = MDC.get("cid");
            Map<?, ?> body = securityService.get().uri("/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .headers(h -> { if (cid != null) h.add("X-Correlation-Id", cid); })   // trace across services
                    .retrieve().body(Map.class);
            String email = body == null ? null : (String) body.get("email");
            if (email == null) {
                return Optional.empty();
            }
            Set<String> roles = body.get("roles") instanceof Collection<?> raw
                    ? raw.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet())
                    : Set.of("USER");
            // the MFA floor: an under-enrolled privileged account acts as a plain USER (fail-closed
            // when the field is missing — an old security that doesn't report it withholds nothing
            // from ordinary users, only from privileged ones)
            boolean mfaCompliant = Boolean.TRUE.equals(body.get("mfaCompliant"));
            return Optional.of(new Caller(email, Caller.withMfaFloor(roles, mfaCompliant)));
        } catch (RestClientException invalidTokenOrServiceDown) {
            return Optional.empty();
        }
    }
}
