package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.config.ImageLimits;
import com.jrobertgardzinski.memes.domain.Meme;
import com.jrobertgardzinski.memes.image.WebImageOptimizer;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Epic("Use case")
@Feature("Publish meme")
class PublishMemeTest {

    private final Map<String, Meme> store = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, String> idByContent = new java.util.concurrent.ConcurrentHashMap<>();

    private final MemeRepository repository = new MemeRepository() {
        public void save(Meme meme) {
            store.put(meme.id(), meme);
        }

        public Optional<Meme> find(String id) {
            return Optional.ofNullable(store.get(id));
        }

        public List<String> allIds() {
            return List.copyOf(store.keySet());
        }

        public List<String> findIdsByAuthor(String author) {
            return store.values().stream().filter(m -> m.author().equals(author)).map(Meme::id).toList();
        }

        public void deleteById(String memeId) {
            store.remove(memeId);
        }

        public void reassignAuthor(String memeId, String newAuthor) {
            store.computeIfPresent(memeId, (id, m) -> new Meme(m.id(), newAuthor, m.format(), m.data()));
        }
    };
    private final MemeContentIndex contentIndex = new MemeContentIndex() {
        public String claim(byte[] data, String candidateId) {
            String earlier = idByContent.putIfAbsent(key(data), candidateId);
            return earlier != null ? earlier : candidateId;
        }

        public void remove(String memeId) {
            idByContent.values().removeIf(memeId::equals);
        }

        private String key(byte[] data) {
            return Base64.getEncoder().encodeToString(data);
        }
    };
    private final PublishMeme publishMeme =
            new PublishMeme(new WebImageOptimizer(new ImageLimits(1024)), repository, contentIndex);

    @Test
    @DisplayName("publishes an optimized meme")
    void publishes_an_optimized_meme() throws Exception {
        String id = publishMeme.execute(bmp(), "alice@example.com");

        Meme stored = store.get(id);
        assertEquals("png", stored.format());
        assertTrue(stored.data().length > 8);
        assertEquals((byte) 0x89, stored.data()[0]); // PNG magic
    }

    @Test
    @DisplayName("publishing the same image twice reuses the meme (dedup)")
    void deduplicates_identical_uploads() throws Exception {
        byte[] image = bmp();

        String first = publishMeme.execute(image, "alice@example.com");
        String second = publishMeme.execute(image, "alice@example.com");

        assertEquals(first, second);
        assertEquals(1, store.size());
    }

    private static byte[] bmp() throws Exception {
        BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "bmp", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("two simultaneous uploads of the same picture store exactly one meme")
    void simultaneous_duplicates_store_one_meme() throws Exception {
        byte[] image = bmp();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var gate = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<String> upload = () -> {
            gate.await();
            return publishMeme.execute(image, "racer@example.com");
        };
        var first = executor.submit(upload);
        var second = executor.submit(upload);
        gate.countDown();
        String a = first.get();
        String b = second.get();
        executor.shutdown();

        assertEquals(a, b, "both uploaders end up holding the same meme");
        assertEquals(1, store.size(), "no orphaned copy is stored, even in a dead heat");
    }
}
