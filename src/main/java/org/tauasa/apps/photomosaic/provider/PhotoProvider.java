package org.tauasa.apps.photomosaic.provider;

import javafx.stage.Window;

import java.util.List;

/**
 * A pluggable source of tile photos for the mosaic.
 *
 * <p>The contract deliberately splits selection from loading so the UI stays responsive:
 * <ul>
 *   <li>{@link #select(Window)} runs the (usually interactive) choosing step on the JavaFX
 *       application thread and returns cheap {@link PhotoRef} handles.</li>
 *   <li>The caller then invokes {@link PhotoRef#load()} on each handle from a background
 *       thread to decode the actual images.</li>
 * </ul>
 *
 * <p>Adding a new source (a cloud service, a URL list, a stock-photo API, ...) is just a new
 * implementation of this interface; nothing else in the app needs to change.
 */
public interface PhotoProvider {

    /** Short label shown in the UI, e.g. "Local files". */
    String displayName();

    /**
     * Interactively choose photos. Call on the JavaFX application thread, as implementations
     * may show a chooser dialog.
     *
     * @param owner the window to parent any dialogs to (may be {@code null})
     * @return references to the chosen photos, or an empty list if the user cancelled
     * @throws Exception if selection fails (e.g. a remote provider can't connect)
     */
    List<PhotoRef> select(Window owner) throws Exception;
}
