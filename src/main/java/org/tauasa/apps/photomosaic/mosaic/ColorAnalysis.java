package org.tauasa.apps.photomosaic.mosaic;

import net.coobird.thumbnailator.Thumbnails;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Colour and resize helpers for the mosaic. Isolated here so the imaging backend stays a
 * one-file swap.
 *
 * Averaging needs no library (an exact pixel sum). Scaling uses Thumbnailator, which does
 * proper progressive (multi-step) downscaling — important because a single Java2D
 * drawImage shrinking a large photo to a tiny tile aliases badly.
 */
public final class ColorAnalysis {

    private ColorAnalysis() {
    }

    /**
     * Exact average RGB of an image by summing every pixel.
     *
     * @return {r, g, b} each in 0..255
     */
    public static float[] averageRgb(BufferedImage image) {
        BufferedImage rgb = toRgb(image);
        int w = rgb.getWidth();
        int h = rgb.getHeight();
        long r = 0, g = 0, b = 0;
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            rgb.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int p = row[x];
                r += (p >> 16) & 0xFF;
                g += (p >> 8) & 0xFF;
                b += p & 0xFF;
            }
        }
        long n = (long) w * h;
        return new float[]{(float) r / n, (float) g / n, (float) b / n};
    }

    /**
     * Resize to an exact pixel size (aspect ratio is intentionally ignored so cells tile
     * cleanly). Thumbnailator applies high-quality progressive downscaling.
     */
    public static BufferedImage scale(BufferedImage src, int targetW, int targetH) {
        if (targetW <= 0 || targetH <= 0) {
            throw new IllegalArgumentException("target size must be positive");
        }
        try {
            return Thumbnails.of(src)
                    .forceSize(targetW, targetH)
                    .asBufferedImage();
        } catch (IOException e) {
            throw new UncheckedIOException("scaling failed", e);
        }
    }

    /** Force an image into TYPE_INT_RGB so getRGB() maths is predictable. */
    public static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }
}
