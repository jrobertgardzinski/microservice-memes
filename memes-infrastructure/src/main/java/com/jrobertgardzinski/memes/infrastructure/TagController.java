package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.tags.TagService;
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
 * The tagging boundary: the author curates their meme's tag set (PUT-like replace via POST),
 * everyone reads it. Search rides on the gallery listing ({@code GET /memes?tag=...}).
 */
@RestController
@RequestMapping("/memes/{memeId}/tags")
class TagController {

    record TagsRequest(List<String> tags) {}

    private final TagService tags;

    TagController(TagService tags) {
        this.tags = tags;
    }

    @PostMapping
    ResponseEntity<?> tag(@PathVariable("memeId") String memeId,
                          @RequestAttribute(RequireSignInFilter.AUTHENTICATED_USER_ID)
                          com.jrobertgardzinski.identity.UserId caller,
                          @RequestBody TagsRequest request) {
        return switch (tags.tag(memeId, caller, request.tags())) {
            case TagService.Tagging.Tagged tagged -> ResponseEntity.ok(Map.of("tags", tagged.tags()));
            case TagService.Tagging.TagsRequired required ->
                    ResponseEntity.badRequest().body(Map.of("status", "TAGS_REQUIRED"));
            case TagService.Tagging.InvalidTag invalid -> ResponseEntity.badRequest()
                    .body(Map.of("status", "INVALID_TAG", "detail", invalid.detail()));
            case TagService.Tagging.NoSuchMeme none -> ResponseEntity.notFound().build();
            case TagService.Tagging.NotTheAuthor notTheAuthor -> ResponseEntity.status(403).body(Map.of("status", "NOT_THE_AUTHOR",
                    "detail", "the uploader curates the tags of their own meme"));
            case TagService.Tagging.TooManyTags tooMany ->
                    ResponseEntity.badRequest().body(Map.of("status", "TOO_MANY_TAGS"));
        };
    }

    @GetMapping
    List<String> tags(@PathVariable("memeId") String memeId) {
        return tags.tags(memeId);
    }
}
