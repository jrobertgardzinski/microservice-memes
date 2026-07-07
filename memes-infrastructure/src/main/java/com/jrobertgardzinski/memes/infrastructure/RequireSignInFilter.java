package com.jrobertgardzinski.memes.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Browsing is public, contributing requires signing in: every write ({@code POST} under
 * {@code /memes}) must carry a bearer token that {@code microservice-security} confirms; the
 * confirmed e-mail is published as the {@link #AUTHENTICATED_USER} request attribute (the comment
 * author, for one, comes from there — never from the request body). Reads pass through untouched —
 * except under {@code /admin/**}, where even reads require signing in (the controllers there
 * additionally demand the ADMIN role).
 */
@Component
class RequireSignInFilter extends OncePerRequestFilter {

    static final String AUTHENTICATED_USER = "authenticatedUser";
    static final String AUTHENTICATED_ROLES = "authenticatedRoles";

    private final SecurityAuthenticationGate gate;

    RequireSignInFilter(SecurityAuthenticationGate gate) {
        this.gate = gate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean admin = request.getRequestURI().startsWith("/admin");
        if (!request.getRequestURI().startsWith("/memes") && !admin) {
            chain.doFilter(request, response);
            return;
        }
        // resolve the identity whenever a token is presented (reads use it to show "your vote");
        // only writes REQUIRE it — and everything under /admin does, reads included
        Optional<Caller> caller = bearerToken(request).flatMap(gate::callerFor);
        caller.ifPresent(c -> {
            request.setAttribute(AUTHENTICATED_USER, c.email());
            request.setAttribute(AUTHENTICATED_ROLES, c.roles());
        });
        boolean write = admin || Set.of("POST", "PUT", "DELETE", "PATCH").contains(request.getMethod());
        if (write && caller.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"SIGN_IN_REQUIRED\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ")
                ? Optional.of(header.substring("Bearer ".length()))
                : Optional.empty();
    }
}
