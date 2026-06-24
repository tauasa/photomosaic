package org.tauasa.apps.photomosaic.mosaic;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * The pool of source images. Matching is nearest-colour in a perceptually weighted RGB
 * space, with an optional penalty that discourages reusing the same tile too often.
 *
 * Lookup is a linear scan: fine for the hundreds of photos a typical folder holds.
 * For very large libraries a k-d tree on the colour signature would be the next step.
 */
public final class TileLibrary {

    private final List<Tile> tiles = new ArrayList<>();

    public void add(Tile tile) {
        tiles.add(tile);
    }

    public int size() {
        return tiles.size();
    }

    public List<Tile> tiles() {
        return tiles;
    }

    public void resetUsage() {
        for (Tile t : tiles) {
            t.uses = 0;
        }
    }

    /** Remove every tile from the library. */
    public void clear() {
        tiles.clear();
    }

    /**
     * Find the closest tile to the target colour.
     *
     * @param target        {r, g, b} 0..255
     * @param repeatPenalty extra squared-distance added per prior use of a tile
     *                      (0 = ignore repetition)
     */
    public Tile nearest(float[] target, double repeatPenalty) {
        if (tiles.isEmpty()) {
            throw new IllegalStateException("tile library is empty");
        }
        Tile best = null;
        double bestScore = Double.MAX_VALUE;
        for (Tile t : tiles) {
            double d = weightedDistanceSq(target, t.avgRgb()) + t.uses * repeatPenalty;
            if (d < bestScore) {
                bestScore = d;
                best = t;
            }
        }
        best.uses++;
        return best;
    }

    /**
     * Redmean-weighted squared distance: cheap approximation of perceptual colour
     * difference that beats plain Euclidean RGB without the cost of a full Lab convert.
     */
    private static double weightedDistanceSq(float[] a, float[] b) {
        double rMean = (a[0] + b[0]) / 2.0;
        double dr = a[0] - b[0];
        double dg = a[1] - b[1];
        double db = a[2] - b[2];
        return (2 + rMean / 256.0) * dr * dr
                + 4 * dg * dg
                + (2 + (255 - rMean) / 256.0) * db * db;
    }

    /** Pre-render every tile to the given cell size so generation is just blits. */
    public void prepare(int cellW, int cellH) {
        for (Tile t : tiles) {
            t.scaled(cellW, cellH);
        }
    }

    public static Tile fromImage(String id, BufferedImage img) {
        return new Tile(id, img);
    }
}
