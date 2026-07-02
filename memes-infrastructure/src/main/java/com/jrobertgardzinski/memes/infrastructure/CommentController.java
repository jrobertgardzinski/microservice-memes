package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.AddComment;
import com.jrobertgardzinski.memes.application.ListComments;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Web boundary for comments on a meme: post a comment (signed-in users only — the author is the
 * identity confirmed by {@code microservice-security}, published by {@link RequireSignInFilter},
 * never a field of the request), list the comments (public). Raw input is validated here
 * (blank/missing text → 400), so the domain never sees nulls (ADR 0001).
 */
@RestController
@RequestMapping("/memes/{memeId}/comments")
class CommentController {

    /** What a client posts to comment on a meme; the author comes from the session, not the body. */
    record CommentRequest(String text) {}

    private final AddComment addComment;
    private final ListComments listComments;

    CommentController(AddComment addComment, ListComments listComments) {
        this.addComment = addComment;
        this.listComments = listComments;
    }

    @PostMapping
    ResponseEntity<?> add(@PathVariable("memeId") String memeId,
                          @RequestAttribute(RequireSignInFilter.AUTHENTICATED_USER) String author,
                          @RequestBody CommentRequest request) {
        if (isBlank(request.text())) {
            return ResponseEntity.badRequest().body(Map.of("status", "INVALID_COMMENT"));
        }
        return addComment.execute(memeId, author, request.text())
                .<ResponseEntity<?>>map(comment ->
                        ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", comment.id())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    List<Map<String, Object>> list(@PathVariable("memeId") String memeId) {
        return listComments.execute(memeId).stream()
                .map(comment -> Map.<String, Object>of(
                        "id", comment.id(), "author", comment.author(), "text", comment.text()))
                .toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
