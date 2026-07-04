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
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    @DisplayName("re-encoding strips embedded metadata — no EXIF (GPS, camera) survives an upload")
    void re_encoding_drops_exif_metadata() throws Exception {
        // a JPEG with an APP1 "Exif" segment carrying something nobody should publish by accident
        byte[] secret = "SecretGPSLocation 52.2297N 21.0122E".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] jpeg = image("jpg", 64, 64);
        byte[] payload = new byte[6 + secret.length];
        System.arraycopy("Exif\u0000\u0000".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, payload, 0, 6);
        System.arraycopy(secret, 0, payload, 6, secret.length);
        ByteArrayOutputStream tagged = new ByteArrayOutputStream();
        tagged.write(jpeg, 0, 2);                       // SOI
        tagged.write(0xFF); tagged.write(0xE1);         // APP1 marker
        int length = payload.length + 2;
        tagged.write((length >> 8) & 0xFF); tagged.write(length & 0xFF);
        tagged.write(payload);
        tagged.write(jpeg, 2, jpeg.length - 2);         // the rest of the original file
        byte[] withExif = tagged.toByteArray();
        assertTrue(contains(withExif, secret), "the crafted upload really carries the metadata");

        OptimizedImage optimized = optimizer.optimize(withExif);

        assertFalse(contains(optimized.data(), secret),
                "the stored image must not leak what the camera wrote");
        assertEquals(64, read(optimized).getWidth(), "and it is still the same picture");
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
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
