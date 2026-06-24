package org.tauasa.apps.photomosaic.provider;

import javafx.stage.Window;

import java.util.List;

/**
 * A pluggable source of tile photos for the mosaic.
 *
 * <p>{@link #select(Window, ProgressSink)} is invoked on a <em>background</em> thread so that
 * slow work (network, database) doesn't freeze the UI. Implementations that need to show a
 * chooser or dialog marshal it to the JavaFX thread with {@link Fx}, and report progress
 * through the supplied {@link ProgressSink}. Selection returns cheap {@link PhotoRef} handles;
 * the caller then calls {@link PhotoRef#load()} on each, also off-thread.
 *
 * <p>Adding a new source is just a new implementation; nothing else in the app changes.
 */
public interface PhotoProvider {

    /** Short label shown in the UI, e.g. "Local files". */
    String displayName();

    /**
     * Choose photos. Runs off the JavaFX application thread.
     *
     * @param owner    window to parent any dialogs to (may be {@code null})
     * @param progress channel for status/progress updates
     * @return references to the chosen photos, or an empty list if cancelled / unavailable
     * @throws Exception if selection fails
     */
    List<PhotoRef> select(Window owner, ProgressSink progress) throws Exception;
}
