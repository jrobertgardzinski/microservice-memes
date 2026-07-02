package com.jrobertgardzinski.memes.infrastructure;

import com.jrobertgardzinski.memes.application.MakeThumbnail;
import com.jrobertgardzinski.memes.application.PublishMeme;
import com.jrobertgardzinski.memes.application.ViewMeme;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * Web boundary: upload a meme image (any format ImageIO reads) and serve it back optimised for the
 * browser (PNG).
 */
@RestController
@RequestMapping("/memes")
class MemeController {

    private final PublishMeme publishMeme;
    private final ViewMeme viewMeme;
    private final MakeThumbnail makeThumbnail;

    MemeController(PublishMeme publishMeme, ViewMeme viewMeme, MakeThumbnail makeThumbnail) {
        this.publishMeme = publishMeme;
        this.viewMeme = viewMeme;
        this.makeThumbnail = makeThumbnail;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        String id = publishMeme.execute(file.getBytes());
        return ResponseEntity.created(URI.create("/memes/" + id)).body(Map.of("id", id));
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
