package org.tauasa.apps.photomosaic.mosaic;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Builds a photomosaic.
 *
 * Approach:
 *   1. Shrink the target image down to columns x rows with high-quality progressive
 *      scaling. Each resulting pixel approximates the average colour of one cell, which
 *      is exactly the signature we want to match a tile against.
 *   2. For each cell, pick the nearest tile by colour (with anti-repeat) and blit its
 *      pre-scaled render into the output canvas.
 *   3. Optionally wash each cell with a translucent layer of the target colour so the
 *      mosaic reads more faithfully from a distance.
 */
public final class MosaicEngine {

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int completedCells, int totalCells);
    }

    public BufferedImage generate(BufferedImage target,
                                  TileLibrary library,
                                  MosaicConfig cfg,
                                  ProgressListener progress) {
        if (library.size() == 0) {
            throw new IllegalStateException("no tiles to build a mosaic from");
        }

        final int cols = cfg.columns();
        final int rows = cfg.rows();
        final int cw = cfg.cellWidth();
        final int ch = cfg.cellHeight();

        library.resetUsage();
        library.prepare(cw, ch);

        // One pixel per cell = average colour of that cell.
        BufferedImage cellColors = ColorAnalysis.scale(ColorAnalysis.toRgb(target), cols, rows);

        BufferedImage out = new BufferedImage(cfg.outputWidth(), cfg.outputHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();

        final int total = cols * rows;
        int done = 0;

        for (int ry = 0; ry < rows; ry++) {
            for (int cx = 0; cx < cols; cx++) {
                int argb = cellColors.getRGB(cx, ry);
                float[] avg = {
                        (argb >> 16) & 0xFF,
                        (argb >> 8) & 0xFF,
                        argb & 0xFF
                };

                Tile tile = library.nearest(avg, cfg.repeatPenalty());

                int px = cx * cw;
                int py = ry * ch;
                g.setComposite(AlphaComposite.SrcOver);
                g.drawImage(tile.scaled(cw, ch), px, py, null);

                if (cfg.blend() > 0f) {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, cfg.blend()));
                    g.setColor(new Color((int) avg[0], (int) avg[1], (int) avg[2]));
                    g.fillRect(px, py, cw, ch);
                }

                done++;
                if (progress != null && (done % cols == 0 || done == total)) {
                    progress.onProgress(done, total);
                }
            }
        }

        g.dispose();
        return out;
    }
}
