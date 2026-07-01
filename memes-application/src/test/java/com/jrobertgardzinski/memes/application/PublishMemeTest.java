package com.jrobertgardzinski.memes.application;

import com.jrobertgardzinski.memes.domain.Meme;
import com.jrobertgardzinski.memes.image.WebImageOptimizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishMemeTest {

    private final Map<String, Meme> store = new HashMap<>();
    private final MemeRepository repository = new MemeRepository() {
        @Override
        public void save(Meme meme) {
            store.put(meme.id(), meme);
        }

        @Override
        public Optional<Meme> find(String id) {
            return Optional.ofNullable(store.get(id));
        }
    };
    private final PublishMeme publishMeme = new PublishMeme(new WebImageOptimizer(), repository);

    @Test
    void publishes_an_optimized_meme() throws Exception {
        String id = publishMeme.execute(bmp());

        Meme stored = store.get(id);
        assertEquals("png", stored.format());
        assertTrue(stored.data().length > 8);
        assertEquals((byte) 0x89, stored.data()[0]); // PNG magic
    }

    private static byte[] bmp() throws Exception {
        BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "bmp", out);
        return out.toByteArray();
    }
}
