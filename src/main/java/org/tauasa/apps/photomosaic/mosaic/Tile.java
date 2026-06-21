package org.tauasa.apps.photomosaic.mosaic;

import java.awt.image.BufferedImage;

/**
 * A single source image used as a mosaic tile. Holds the original, its average RGB
 * (the signature used for matching) and a lazily-built copy scaled to the current
 * cell size.
 */
public final class Tile {

    private final String id;
    private final BufferedImage source;
    private final float[] avgRgb;

    private BufferedImage scaled;
    private int scaledW = -1;
    private int scaledH = -1;

    /** running count of how many times this tile has been placed (anti-repeat). */
    int uses = 0;

    public Tile(String id, BufferedImage source) {
        this.id = id;
        this.source = ColorAnalysis.toRgb(source);
        this.avgRgb = ColorAnalysis.averageRgb(this.source);
    }

    public String id() {
        return id;
    }

    public float[] avgRgb() {
        return avgRgb;
    }

    /** Return this tile rendered at the requested cell size, caching the result. */
    public BufferedImage scaled(int w, int h) {
        if (scaled == null || scaledW != w || scaledH != h) {
            scaled = ColorAnalysis.scale(source, w, h);
            scaledW = w;
            scaledH = h;
        }
        return scaled;
    }
}
