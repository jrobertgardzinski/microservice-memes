package com.jrobertgardzinski.memes.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Browsing is public, contributing requires signing in: every write ({@code POST} under
 * {@code /memes}) must carry a bearer token that {@code microservice-security} confirms; the
 * confirmed e-mail is published as the {@link #AUTHENTICATED_USER} request attribute (the comment
 * author, for one, comes from there — never from the request body). Reads pass through untouched.
 */
@Component
class RequireSignInFilter extends OncePerRequestFilter {

    static final String AUTHENTICATED_USER = "authenticatedUser";

    private final SecurityAuthenticationGate gate;

    RequireSignInFilter(SecurityAuthenticationGate gate) {
        this.gate = gate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"POST".equals(request.getMethod()) || !request.getRequestURI().startsWith("/memes")) {
            chain.doFilter(request, response);
            return;
        }
        Optional<String> user = bearerToken(request).flatMap(gate::emailFor);
        if (user.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":\"SIGN_IN_REQUIRED\"}");
            return;
        }
        request.setAttribute(AUTHENTICATED_USER, user.get());
        chain.doFilter(request, response);
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ")
                ? Optional.of(header.substring("Bearer ".length()))
                : Optional.empty();
    }
}
