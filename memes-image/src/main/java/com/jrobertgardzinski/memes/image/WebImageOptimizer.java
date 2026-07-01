package com.jrobertgardzinski.memes.image;

import com.jrobertgardzinski.memes.config.ImageLimits;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Re-encodes any image ImageIO can read (BMP, JPEG, GIF, …) into a browser-friendly PNG, scaling it
 * down so its longest side fits the configured {@link ImageLimits}. PNG re-encoding also drops
 * non-pixel metadata such as EXIF. Heavier optimisation (WebP) can grow here later.
 */
public class WebImageOptimizer {

    private static final String TARGET_FORMAT = "png";

    private final ImageLimits limits;

    public WebImageOptimizer(ImageLimits limits) {
        this.limits = limits;
    }

    public OptimizedImage optimize(byte[] input) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(input));
            if (image == null) {
                throw new IllegalArgumentException("unsupported or unreadable image");
            }
            BufferedImage bounded = downscaleWithinLimits(image);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(bounded, TARGET_FORMAT, out)) {
                throw new IllegalStateException("no writer for " + TARGET_FORMAT);
            }
            return new OptimizedImage(out.toByteArray(), TARGET_FORMAT);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private BufferedImage downscaleWithinLimits(BufferedImage image) {
        int longestSide = Math.max(image.getWidth(), image.getHeight());
        if (longestSide <= limits.maxDimension()) {
            return image;
        }
        double scale = (double) limits.maxDimension() / longestSide;
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(image, 0, 0, width, height, null);
        graphics.dispose();
        return scaled;
    }
}
