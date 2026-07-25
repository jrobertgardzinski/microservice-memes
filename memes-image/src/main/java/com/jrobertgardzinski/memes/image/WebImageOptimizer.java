package com.jrobertgardzinski.memes.image;

import com.jrobertgardzinski.memes.config.ImageLimits;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;

/**
 * Re-encodes any image ImageIO can read (BMP, JPEG, GIF, …) into a browser-friendly PNG, scaling it
 * down so its longest side fits a maximum dimension. PNG re-encoding also drops non-pixel metadata
 * such as EXIF. Heavier optimisation (WebP) can grow here later.
 */
public class WebImageOptimizer {

    private static final String TARGET_FORMAT = "png";

    // Decompression-bomb guard: ImageIO.read allocates ~4 bytes per pixel BEFORE we can downscale,
    // so a tiny file declaring huge dimensions (a 10000x10000 uniform PNG compresses to a few kB)
    // would cost ~400 MB of heap per upload. 8000px per side (~256 MB worst case, once) is far
    // beyond any legitimate meme yet small enough that one hostile upload cannot take the JVM down.
    private static final int MAX_DECODE_DIMENSION = 8000;

    private final ImageLimits limits;

    public WebImageOptimizer(ImageLimits limits) {
        this.limits = limits;
    }

    /** Optimise to a stored meme, bounded by the configured {@link ImageLimits}. */
    public OptimizedImage optimize(byte[] input) {
        return toPngWithin(input, limits.maxDimension());
    }

    /** Re-encode to a PNG whose longest side is at most {@code maxDimension} (used for thumbnails too). */
    public OptimizedImage toPngWithin(byte[] input, int maxDimension) {
        try {
            rejectDeclaredDimensionsAbove(input, MAX_DECODE_DIMENSION);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(input));
            if (image == null) {
                throw new InvalidImageException("unsupported or unreadable image");
            }
            BufferedImage bounded = downscaleWithin(image, maxDimension);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(bounded, TARGET_FORMAT, out)) {
                throw new IllegalStateException("no writer for " + TARGET_FORMAT);
            }
            return new OptimizedImage(out.toByteArray(), TARGET_FORMAT);
        } catch (IOException e) {
            // everything read here is the CALLER's bytes (both streams are in-memory), so an
            // IOException means their image died mid-parse — signal "bad input", not a raw
            // UncheckedIOException, which the web boundary rightly treats as a server fault
            throw new InvalidImageException("unreadable image: the bytes are truncated or corrupt", e);
        }
    }

    /**
     * Reads width/height from the image HEADER (no pixel decoding, so it is cheap regardless of
     * what the file claims) and refuses anything whose decode would blow the heap. Unreadable input
     * falls through — {@code ImageIO.read} then produces the usual "unsupported" rejection.
     */
    private static void rejectDeclaredDimensionsAbove(byte[] input, int maxDimension) throws IOException {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(input))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > maxDimension || height > maxDimension) {
                    throw new InvalidImageException("image dimensions too large: " + width + "x" + height
                            + " (max " + maxDimension + " per side)");
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage downscaleWithin(BufferedImage image, int maxDimension) {
        int longestSide = Math.max(image.getWidth(), image.getHeight());
        if (longestSide <= maxDimension) {
            return image;
        }
        double scale = (double) maxDimension / longestSide;
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
