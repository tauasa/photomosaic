package org.tauasa.apps.photomosaic.mosaic;

import java.awt.image.BufferedImage;

/**
 * A single source image used as a mosaic tile.
 *
 * <p>The full-resolution import is <em>not</em> retained. A tile is only ever drawn at cell
 * size, so at construction we downscale once to a small "master" (capped at {@link #MASTER}
 * px, the maximum cell size the UI allows) and keep only that. The original BufferedImage
 * becomes eligible for garbage collection immediately, which is what keeps memory flat as
 * hundreds or thousands of photos are imported. Holding the originals was costing tens of
 * megabytes per photo.
 */
public final class Tile {

    /** Master cap in px. Must be >= the largest cell size the UI can request. */
    private static final int MASTER = 128;

    private final String id;
    private final BufferedImage master;   // small, capped render — all we ever need
    private final float[] avgRgb;

    private BufferedImage scaled;         // cache for the current cell size
    private int scaledW = -1;
    private int scaledH = -1;

    /** running count of how many times this tile has been placed (anti-repeat). */
    int uses = 0;

    public Tile(String id, BufferedImage source) {
        this.id = id;
        // Scale straight from the source (any image type) to the small master, then drop
        // the source. avgRgb is taken from the master — the colour the tile actually shows.
        this.master = ColorAnalysis.scale(source, MASTER, MASTER);
        this.avgRgb = ColorAnalysis.averageRgb(this.master);
    }

    public String id() {
        return id;
    }

    public float[] avgRgb() {
        return avgRgb;
    }

    /** Return this tile rendered at the requested cell size, caching the result. */
    public BufferedImage scaled(int w, int h) {
        // The master already is the max cell size; never upscale past it.
        if (w >= MASTER && h >= MASTER) {
            return master;
        }
        if (scaled == null || scaledW != w || scaledH != h) {
            scaled = ColorAnalysis.scale(master, w, h);
            scaledW = w;
            scaledH = h;
        }
        return scaled;
    }
}
