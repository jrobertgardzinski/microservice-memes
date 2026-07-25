package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Meme;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Epic("Use case")
@Feature("Serve meme")
class ServeMemeTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F'};

    private final MemeRepository memes = new MemeRepository() {
        public void save(Meme meme) { }

        public Optional<Meme> find(String id) {
            return "cat".equals(id)
                    ? Optional.of(new Meme("cat", "a@example.com", "png", PNG))
                    : Optional.empty();
        }

        public List<String> allIds() { return List.of("cat"); }
        public List<String> findIdsByAuthor(String author) { return List.of(); }
        public void deleteById(String memeId) { }
        public void reassignAuthor(String memeId, String newAuthor) { }
    };

    /** A store whose writes always fail — the disk-full / S3-blip case. */
    private final ObjectStore brokenStore = new ObjectStore() {
        public void put(String key, byte[] data) {
            throw new IllegalStateException("store is full");
        }

        public Optional<byte[]> get(String key) { return Optional.empty(); }
        public void delete(String key) { }
    };

    /** A plain in-memory store, so the tests can look at what actually ended up cached. */
    private static ObjectStore mapStore(java.util.Map<String, byte[]> blobs) {
        return new ObjectStore() {
            public void put(String key, byte[] data) {
                blobs.put(key, data);
            }

            public Optional<byte[]> get(String key) {
                return Optional.ofNullable(blobs.get(key));
            }

            public void delete(String key) {
                blobs.remove(key);
            }
        };
    }

    @Test
    @DisplayName("a failed cache write does not cost the caller their WebP")
    void serves_webp_even_when_the_cache_write_fails() {
        ServeMeme serve = new ServeMeme(memes, brokenStore, png -> Optional.of(WEBP));

        Optional<ServeMeme.Image> served = serve.execute("cat", true);

        assertTrue(served.isPresent());
        assertEquals("image/webp", served.get().contentType(),
                "the encoded bytes are in hand — a cache hiccup must not downgrade the response");
        assertArrayEquals(WEBP, served.get().data());
    }

    @Test
    @DisplayName("the encoded WebP is cached for the next request (happy path)")
    void caches_the_encoded_webp() {
        java.util.Map<String, byte[]> blobs = new java.util.HashMap<>();
        ServeMeme serve = new ServeMeme(memes, mapStore(blobs), png -> Optional.of(WEBP));

        Optional<ServeMeme.Image> served = serve.execute("cat", true);

        assertEquals("image/webp", served.orElseThrow().contentType());
        assertArrayEquals(WEBP, blobs.get("cat.webp"),
                "the variant stays cached — the meme still exists, so deleteById will sweep it later");
    }

    @Test
    @DisplayName("a WebP cached for a meme deleted mid-flight is removed again — no orphan")
    void removes_the_variant_cached_for_a_just_deleted_meme() {
        // the race: serve read the meme's bytes, then a concurrent delete removed the meme AND its
        // variants, then serve wrote the freshly encoded WebP — deleteById will never come back for
        // it, so without the re-check the variant would sit in the store forever
        MemeRepository deletedMidFlight = new MemeRepository() {
            public Optional<Meme> find(String id) {
                // the read that started this request — the meme was still there
                return Optional.of(new Meme("cat", "a@example.com", "png", PNG));
            }

            public boolean exists(String id) {
                return false;   // by the time the cache write lands, the delete has committed
            }

            public void save(Meme meme) { }
            public List<String> allIds() { return List.of(); }
            public List<String> findIdsByAuthor(String author) { return List.of(); }
            public void deleteById(String memeId) { }
            public void reassignAuthor(String memeId, String newAuthor) { }
        };
        java.util.Map<String, byte[]> blobs = new java.util.HashMap<>();
        ServeMeme serve = new ServeMeme(deletedMidFlight, mapStore(blobs), png -> Optional.of(WEBP));

        Optional<ServeMeme.Image> served = serve.execute("cat", true);

        assertEquals("image/webp", served.orElseThrow().contentType(),
                "the caller still gets the bytes they were promised");
        assertTrue(blobs.isEmpty(), "but the cache write is undone — nothing will ever delete it otherwise");
    }
}
