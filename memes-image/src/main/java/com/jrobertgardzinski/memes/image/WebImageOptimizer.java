package com.jrobertgardzinski.memes.image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Re-encodes any image ImageIO can read (BMP, JPEG, GIF, …) into a browser-friendly format (PNG).
 * This is where heavier optimisation (resizing, WebP, EXIF stripping) will grow; PNG re-encoding
 * already drops non-pixel metadata such as EXIF.
 */
public class WebImageOptimizer {

    private static final String TARGET_FORMAT = "png";

    public OptimizedImage optimize(byte[] input) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(input));
            if (image == null) {
                throw new IllegalArgumentException("unsupported or unreadable image");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, TARGET_FORMAT, out)) {
                throw new IllegalStateException("no writer for " + TARGET_FORMAT);
            }
            return new OptimizedImage(out.toByteArray(), TARGET_FORMAT);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
