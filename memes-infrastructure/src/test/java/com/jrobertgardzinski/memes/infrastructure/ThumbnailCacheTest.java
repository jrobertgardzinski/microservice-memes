package com.jrobertgardzinski.memes.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jrobertgardzinski.memes.application.ObjectStore;
import com.jrobertgardzinski.memes.config.ImageLimits;
import com.jrobertgardzinski.memes.image.OptimizedImage;
import com.jrobertgardzinski.memes.image.WebImageOptimizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The thumbnail cache, end to end through the web boundary: the FIRST request decodes the full
 * image (under the decode semaphore — the step that used to run on EVERY request and let a burst
 * of gallery scrolls feed readers 429s), later requests are served from the {@code {id}.thumb}
 * variant without decoding at all; the response invites browsers to cache too (a thumbnail is
 * immutable per id); and a deleted meme takes its cached thumbnail along.
 */
@SpringBootTest(classes = {MemesApplication.class, TestAuthConfig.class,
        ThumbnailCacheTest.CountingDecodes.class})
@AutoConfigureMockMvc
class ThumbnailCacheTest {

    /** Bumped on every FULL-image decode for a thumbnail — the work the cache must avoid. */
    static final AtomicInteger thumbnailDecodes = new AtomicInteger();

    @TestConfiguration
    static class CountingDecodes {
        @Bean
        @Primary
        WebImageOptimizer countingOptimizer(ImageLimits limits) {
            return new WebImageOptimizer(limits) {
                @Override
                public OptimizedImage toPngWithin(byte[] input, int maxDimension) {
                    thumbnailDecodes.incrementAndGet();
                    return super.toPngWithin(input, maxDimension);
                }
            };
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ObjectStore objects;

    @Test
    @DisplayName("the second GET is served from the cache — one decode ever, and Cache-Control invites the browser to keep it")
    void second_get_does_not_decode_and_carries_cache_control() throws Exception {
        String id = upload();
        thumbnailDecodes.set(0);

        byte[] first = mockMvc.perform(get("/memes/{id}/thumbnail", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cache-Control", "max-age=3600, public"))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals(1, thumbnailDecodes.get(), "the first request pays the one decode");

        byte[] second = mockMvc.perform(get("/memes/{id}/thumbnail", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=3600, public"))
                .andReturn().getResponse().getContentAsByteArray();

        assertEquals(1, thumbnailDecodes.get(),
                "the second request must be served from the {id}.thumb variant — no decode, no permit");
        assertArrayEquals(first, second, "cache and fresh decode must serve the same bytes");
        assertTrue(objects.get(id + ".thumb").isPresent(), "the variant lives in the object store");
    }

    @Test
    @DisplayName("deleting the meme sweeps the cached thumbnail — and the endpoint answers 404, not a ghost image")
    void delete_takes_the_cached_thumbnail_along() throws Exception {
        String id = upload();
        mockMvc.perform(get("/memes/{id}/thumbnail", id)).andExpect(status().isOk());
        assertTrue(objects.get(id + ".thumb").isPresent(), "primed: the variant is cached");

        mockMvc.perform(delete("/memes/{id}", id)
                        .header("Authorization", "Bearer " + TestAuthConfig.VALID_TOKEN))
                .andExpect(status().isOk());

        assertTrue(objects.get(id + ".thumb").isEmpty(),
                "the delete must sweep the thumbnail variant exactly like the WebP one");
        mockMvc.perform(get("/memes/{id}/thumbnail", id)).andExpect(status().isNotFound());
    }

    /** Uploads a fresh, unique image as the signed-in test user and returns the meme id. */
    private String upload() throws Exception {
        BufferedImage image = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, (int) (Math.random() * 0xFFFFFF));   // distinct pixels beat the dedup index
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        MockMultipartFile file = new MockMultipartFile("file", "m.png", "image/png", out.toByteArray());
        String body = mockMvc.perform(multipart("/memes").file(file)
                        .header("Authorization", "Bearer " + TestAuthConfig.VALID_TOKEN))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }
}
