package com.jrobertgardzinski.memes.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebImageOptimizerTest {

    private final WebImageOptimizer optimizer = new WebImageOptimizer();

    @Test
    void turns_a_bmp_into_a_png() throws Exception {
        byte[] bmp = bmp(4, 3);

        OptimizedImage optimized = optimizer.optimize(bmp);

        assertEquals("png", optimized.format());
        // PNG magic number: 0x89 'P' 'N' 'G'
        assertTrue(optimized.data().length > 8);
        assertEquals((byte) 0x89, optimized.data()[0]);
        assertEquals('P', optimized.data()[1]);
        assertEquals('N', optimized.data()[2]);
        assertEquals('G', optimized.data()[3]);
        // and it round-trips back to a readable image of the same size
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(optimized.data()));
        assertNotNull(back);
        assertEquals(4, back.getWidth());
        assertEquals(3, back.getHeight());
    }

    private static byte[] bmp(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.getGraphics().setColor(Color.RED);
        image.getGraphics().fillRect(0, 0, width, height);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "bmp", out);
        return out.toByteArray();
    }
}
