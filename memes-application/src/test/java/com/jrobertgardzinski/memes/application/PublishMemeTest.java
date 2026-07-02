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

    private final Map<String, Meme> store = new HashMap<>();
    private final Map<String, String> idByContent = new HashMap<>();

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
    };
    private final MemeContentIndex contentIndex = new MemeContentIndex() {
        public Optional<String> findIdByContent(byte[] data) {
            return Optional.ofNullable(idByContent.get(key(data)));
        }

        public void index(byte[] data, String memeId) {
            idByContent.put(key(data), memeId);
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
}
