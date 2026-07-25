package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.config.ImageLimits;
import com.jrobertgardzinski.memes.config.ThumbnailSize;
import com.jrobertgardzinski.memes.domain.Meme;
import com.jrobertgardzinski.memes.image.WebImageOptimizer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Epic("Use case")
@Feature("Make thumbnail")
class MakeThumbnailTest {

    private final Map<String, Meme> memes = new HashMap<>();
    private final MemeRepository memeRepository = new MemeRepository() {
        public void save(Meme meme) {
            memes.put(meme.id(), meme);
        }

        public Optional<Meme> find(String id) {
            return Optional.ofNullable(memes.get(id));
        }

        public List<String> allIds() {
            return List.copyOf(memes.keySet());
        }

        public List<String> findIdsByAuthor(String author) {
            return memes.values().stream().filter(m -> m.author().equals(author)).map(Meme::id).toList();
        }

        public void deleteById(String memeId) {
            memes.remove(memeId);
        }

        public void reassignAuthor(String memeId, String newAuthor) {
            memes.computeIfPresent(memeId, (id, m) -> new Meme(m.id(), newAuthor, m.format(), m.data()));
        }
    };
    private final Map<String, byte[]> blobs = new HashMap<>();
    private final ObjectStore objects = new ObjectStore() {
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
    private final java.util.concurrent.atomic.AtomicInteger decodes = new java.util.concurrent.atomic.AtomicInteger();
    private final WebImageOptimizer countingOptimizer = new WebImageOptimizer(new ImageLimits(4096)) {
        @Override
        public com.jrobertgardzinski.memes.image.OptimizedImage toPngWithin(byte[] input, int maxDimension) {
            decodes.incrementAndGet();
            return super.toPngWithin(input, maxDimension);
        }
    };
    private final MakeThumbnail makeThumbnail = new MakeThumbnail(
            memeRepository, objects, countingOptimizer, new ThumbnailSize(64));

    @Test
    @DisplayName("makes a small PNG thumbnail of a stored meme")
    void makes_a_thumbnail() throws Exception {
        memes.put("m1", new Meme("m1", "alice@example.com", "png", png(400, 200)));

        Optional<byte[]> thumb = makeThumbnail.execute("m1");

        assertTrue(thumb.isPresent());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(thumb.get()));
        assertEquals(64, image.getWidth());   // 400 -> 64
        assertEquals(32, image.getHeight());  // 200 * (64/400) = 32
    }

    @Test
    @DisplayName("no thumbnail for a missing meme")
    void none_for_missing_meme() {
        assertTrue(makeThumbnail.execute("nope").isEmpty());
    }

    @Test
    @DisplayName("the second request is served from the {id}.thumb cache — one decode, ever")
    void second_request_does_not_decode_again() throws Exception {
        memes.put("m1", new Meme("m1", "alice@example.com", "png", png(400, 200)));

        byte[] first = makeThumbnail.execute("m1").orElseThrow();
        byte[] second = makeThumbnail.execute("m1").orElseThrow();

        assertEquals(1, decodes.get(),
                "the full image must be decoded exactly once — later requests read the cached variant");
        assertArrayEquals(first, second, "the cache must serve the same thumbnail bytes");
        assertArrayEquals(first, blobs.get("m1.thumb"), "the variant lives under {id}.thumb");
    }

    @Test
    @DisplayName("a thumbnail cached while the meme was being deleted is removed by the re-check")
    void cache_write_racing_a_delete_is_taken_back() throws Exception {
        // the delete-race guard, same as ServeMeme's WebP: the store hands out the put, then the
        // meme turns out to be gone — the freshly written variant must not stay orphaned
        memes.put("racy", new Meme("racy", "alice@example.com", "png", png(100, 100)));
        ObjectStore vanishingStore = new ObjectStore() {
            public void put(String key, byte[] data) {
                blobs.put(key, data);
                memes.remove("racy");   // the delete lands exactly between the put and the re-check
            }

            public Optional<byte[]> get(String key) {
                return Optional.ofNullable(blobs.get(key));
            }

            public void delete(String key) {
                blobs.remove(key);
            }
        };
        MakeThumbnail racing = new MakeThumbnail(
                memeRepository, vanishingStore, countingOptimizer, new ThumbnailSize(64));

        assertTrue(racing.execute("racy").isPresent(), "this request still gets its thumbnail");
        assertTrue(!blobs.containsKey("racy.thumb"),
                "the re-check must remove the variant it just wrote for a deleted meme");
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
