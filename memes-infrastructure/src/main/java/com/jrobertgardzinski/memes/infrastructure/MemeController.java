package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.ListMemes;
import com.jrobertgardzinski.memes.application.MakeThumbnail;
import com.jrobertgardzinski.memes.application.PublishMeme;
import com.jrobertgardzinski.memes.application.ViewMeme;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Web boundary: upload a meme image (any format ImageIO reads; signed-in users only, enforced by
 * {@link RequireSignInFilter}), browse the gallery and serve memes back optimised for the browser
 * (PNG) — reads are public.
 */
@RestController
@RequestMapping("/memes")
class MemeController {

    private final PublishMeme publishMeme;
    private final ViewMeme viewMeme;
    private final MakeThumbnail makeThumbnail;
    private final ListMemes listMemes;
    private final com.jrobertgardzinski.memes.application.SearchMemesByTag searchMemesByTag;
    private final com.jrobertgardzinski.memes.config.RateLimit uploadRate;

    MemeController(PublishMeme publishMeme, ViewMeme viewMeme, MakeThumbnail makeThumbnail,
                   ListMemes listMemes,
                   com.jrobertgardzinski.memes.application.SearchMemesByTag searchMemesByTag,
                   com.jrobertgardzinski.memes.config.RateLimit uploadRate) {
        this.publishMeme = publishMeme;
        this.viewMeme = viewMeme;
        this.makeThumbnail = makeThumbnail;
        this.listMemes = listMemes;
        this.searchMemesByTag = searchMemesByTag;
        this.uploadRate = uploadRate;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
                                               @RequestAttribute(RequireSignInFilter.AUTHENTICATED_USER)
                                               String uploader) throws IOException {
        if (!uploadRate.tryAcquire(uploader)) {
            return ResponseEntity.status(429).header("Retry-After", "60")
                    .body(Map.of("status", "RATE_LIMITED", "detail", "you are uploading too fast"));
        }
        String id = publishMeme.execute(file.getBytes(), uploader);
        return ResponseEntity.created(URI.create("/memes/" + id)).body(Map.of("id", id));
    }

    @GetMapping
    ResponseEntity<?> all(@org.springframework.web.bind.annotation.RequestParam(name = "tag",
            required = false) String tag) {
        if (tag == null || tag.isBlank()) {
            return ResponseEntity.ok(listMemes.execute().stream().map(id -> Map.of("id", id)).toList());
        }
        try {
            return ResponseEntity.ok(searchMemesByTag.execute(com.jrobertgardzinski.memes.tags.Tag.of(tag))
                    .stream().map(id -> Map.of("id", id)).toList());
        } catch (IllegalArgumentException illegalTag) {
            return ResponseEntity.badRequest().body(Map.of("status", "INVALID_TAG"));
        }
    }

    @GetMapping("/{id}")
    ResponseEntity<byte[]> view(@PathVariable("id") String id) {
        return viewMeme.execute(id)
                .map(meme -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(meme.data()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/thumbnail")
    ResponseEntity<byte[]> thumbnail(@PathVariable("id") String id) {
        return makeThumbnail.execute(id)
                .map(bytes -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
