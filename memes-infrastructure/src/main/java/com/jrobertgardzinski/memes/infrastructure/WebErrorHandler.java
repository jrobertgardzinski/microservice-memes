package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.image.InvalidImageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.UncheckedIOException;
import java.util.Map;

/**
 * Sorts unhandled exceptions into "the caller sent something we refuse" (400) and "we broke" (500)
 * — and refuses to conflate the two. The image pipeline signals bad input with the dedicated
 * {@link InvalidImageException} (unreadable bytes, oversized dimensions), whose messages are
 * written for the uploader and safe to echo. A bare {@link UncheckedIOException} is the opposite:
 * it escapes the blob adapters when the DISK or bucket fails mid-read on a GET, so it maps to a
 * 500 with a generic body (the detail — internal paths included — goes to the log, not the wire).
 * A bare {@link IllegalArgumentException} stays a 400, but likewise with a generic body: those
 * messages were written for developers and may leak internals. Controllers that want a RICHER
 * refusal body (e.g. tagging's {@code {"status": "INVALID_TAG"}} contract) keep their local
 * catches — those run first, this advice is the uniform floor under everything else.
 */
@RestControllerAdvice
class WebErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(WebErrorHandler.class);

    @ExceptionHandler(InvalidImageException.class)
    ResponseEntity<Map<String, String>> refusedImage(InvalidImageException refused) {
        // the one exception whose message is meant for the caller — echo it as the explanation
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(refused.getMessage())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> refusedInput(IllegalArgumentException refused) {
        // still the caller's fault (an argument WE validated and rejected), but the raw message
        // was never vetted for the wire — keep the body generic, put the detail in the log
        LOG.warn("refusing request: {}", refused.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", "invalid request"));
    }

    @ExceptionHandler(UncheckedIOException.class)
    ResponseEntity<Map<String, String>> storageFailure(UncheckedIOException failure) {
        // NOT the caller's upload: input-decode failures surface as InvalidImageException above.
        // What reaches this handler is the blob store dying under a request (disk fault on a GET,
        // unreachable bucket) — a server error, answered without internal paths or messages.
        LOG.error("I/O failure while handling a request", failure);
        return ResponseEntity.internalServerError().body(Map.of("error", "internal storage error"));
    }
}
