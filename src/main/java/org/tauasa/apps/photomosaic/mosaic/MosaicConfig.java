package org.tauasa.apps.photomosaic.mosaic;

/**
 * Mosaic generation settings.
 *
 * @param columns       number of tiles across
 * @param rows          number of tiles down
 * @param cellWidth     rendered pixel width of each tile cell
 * @param cellHeight    rendered pixel height of each tile cell
 * @param blend         0..1 alpha of the target colour painted over each tile
 *                      (0 = pure tiles, higher = more faithful to the original)
 * @param repeatPenalty discourages reusing tiles; 0 disables it
 */
public record MosaicConfig(
        int columns,
        int rows,
        int cellWidth,
        int cellHeight,
        float blend,
        double repeatPenalty
) {
    public MosaicConfig {
        if (columns <= 0 || rows <= 0) throw new IllegalArgumentException("grid must be positive");
        if (cellWidth <= 0 || cellHeight <= 0) throw new IllegalArgumentException("cell size must be positive");
        if (blend < 0f || blend > 1f) throw new IllegalArgumentException("blend must be 0..1");
    }

    public int outputWidth() {
        return columns * cellWidth;
    }

    public int outputHeight() {
        return rows * cellHeight;
    }

    /** Sensible starting point. */
    public static MosaicConfig defaults() {
        return new MosaicConfig(80, 80, 32, 32, 0.25f, 250.0);
    }
}
