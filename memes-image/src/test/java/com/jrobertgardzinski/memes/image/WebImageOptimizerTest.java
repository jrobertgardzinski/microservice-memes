package com.jrobertgardzinski.memes.image;

import com.jrobertgardzinski.memes.config.ImageLimits;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Epic("Image")
@Feature("Optimisation")
class WebImageOptimizerTest {

    private final WebImageOptimizer optimizer = new WebImageOptimizer(new ImageLimits(1024));

    @Test
    @DisplayName("turns a BMP into a PNG, unchanged when within the limit")
    void turns_a_bmp_into_a_png() throws Exception {
        OptimizedImage optimized = optimizer.optimize(image("bmp", 4, 3));

        assertEquals("png", optimized.format());
        assertEquals((byte) 0x89, optimized.data()[0]); // PNG magic
        BufferedImage back = read(optimized);
        assertEquals(4, back.getWidth());
        assertEquals(3, back.getHeight());
    }

    @Test
    @DisplayName("scales an over-sized image down so its longest side fits the limit")
    void scales_down_an_oversized_image() throws Exception {
        OptimizedImage optimized = optimizer.optimize(image("png", 2000, 1000));

        BufferedImage back = read(optimized);
        assertEquals(1024, back.getWidth());     // 2000 -> 1024
        assertEquals(512, back.getHeight());     // 1000 * (1024/2000) = 512
        assertTrue(back.getWidth() <= 1024 && back.getHeight() <= 1024);
    }

    private static byte[] image(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static BufferedImage read(OptimizedImage optimized) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(optimized.data()));
    }
}
