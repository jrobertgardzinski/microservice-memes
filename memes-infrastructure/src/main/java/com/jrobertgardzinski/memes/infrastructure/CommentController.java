package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.AddComment;
import com.jrobertgardzinski.memes.application.ListComments;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Web boundary for comments on a meme: post a comment, list the comments.
 */
@RestController
@RequestMapping("/memes/{memeId}/comments")
class CommentController {

    private final AddComment addComment;
    private final ListComments listComments;

    CommentController(AddComment addComment, ListComments listComments) {
        this.addComment = addComment;
        this.listComments = listComments;
    }

    @PostMapping
    ResponseEntity<?> add(@PathVariable("memeId") String memeId, @RequestBody Map<String, Object> body) {
        String author = asString(body.get("author"));
        String text = asString(body.get("text"));
        if (author.isBlank() || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("status", "INVALID_COMMENT"));
        }
        return addComment.execute(memeId, author, text)
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

    private static String asString(Object value) {
        return value == null ? "" : value.toString();
    }
}
