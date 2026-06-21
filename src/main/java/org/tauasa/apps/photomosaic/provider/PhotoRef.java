package org.tauasa.apps.photomosaic.provider;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * A reference to a single source photo that a {@link PhotoProvider} has offered up.
 *
 * <p>References are intentionally lightweight: selecting hundreds of photos should be cheap,
 * with the actual pixel decoding deferred to {@link #load()}. {@code load()} is expected to be
 * called off the JavaFX application thread (it may do disk or network I/O).
 */
public interface PhotoRef {

    /** Stable, human-readable identifier for this photo (e.g. a filename). */
    String id();

    /**
     * Decode and return the image. May be slow; safe to call from a background thread.
     *
     * @throws IOException if the photo can't be read or decoded
     */
    BufferedImage load() throws IOException;
}
