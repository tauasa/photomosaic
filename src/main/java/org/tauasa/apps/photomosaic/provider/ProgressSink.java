package org.tauasa.apps.photomosaic.provider;

/** A channel for a {@link PhotoProvider} to report progress during selection. */
public interface ProgressSink {

    void status(String message);

    default void progress(double fraction) {
    }

    /** No-op sink. */
    ProgressSink NONE = message -> {
    };
}
