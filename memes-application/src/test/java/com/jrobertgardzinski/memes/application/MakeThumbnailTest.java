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
import java.util.Map;
import java.util.Optional;

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
    };
    private final MakeThumbnail makeThumbnail = new MakeThumbnail(
            memeRepository, new WebImageOptimizer(new ImageLimits(4096)), new ThumbnailSize(64));

    @Test
    @DisplayName("makes a small PNG thumbnail of a stored meme")
    void makes_a_thumbnail() throws Exception {
        memes.put("m1", new Meme("m1", "png", png(400, 200)));

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

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
