package com.jrobertgardzinski.memes.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Refuses an upload that ANNOUNCES more than the service accepts, before anything is read.
 *
 * <p>Two reasons, and neither is the heap — the multipart is spooled to disk
 * ({@code fileSizeThreshold=0}), so the bytes are not on the heap until {@code getBytes()}, which
 * {@link UploadAdmission} now bounds.
 *
 * <p><strong>The answer was wrong.</strong> Exceeding {@code spring.servlet.multipart.max-file-size}
 * surfaced as a {@code MaxUploadSizeExceededException} out of the DispatcherServlet, which the
 * error handler had no case for — so a user who picked a large photo was told the server had an
 * internal error, with no hint of what to do. 413 with the limit in the body is the true answer,
 * and it is one the UI can act on.
 *
 * <p><strong>And the disk was unbounded.</strong> Spooling happens before any of this service's own
 * limits apply, so a caller could make the container write its declared size to the temp directory
 * regardless. Refusing on {@code Content-Length} costs nothing and happens before the first byte is
 * spooled. A request that lies about its length is not a problem here: the multipart parser's own
 * limit still applies to what actually arrives.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)   // before the multipart resolver, after the servlet basics
class RejectOversizedUploadFilter extends OncePerRequestFilter {

    private final long maxBytes;

    RejectOversizedUploadFilter(@Value("${spring.servlet.multipart.max-request-size:10MB}") String maxRequestSize) {
        this.maxBytes = DataSize.parse(maxRequestSize).toBytes();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long declared = request.getContentLengthLong();
        if (!"POST".equals(request.getMethod()) || declared <= maxBytes) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":\"TOO_LARGE\",\"detail\":\"the upload is "
                + declared + " bytes; this service accepts at most " + maxBytes + "\"}");
    }
}
