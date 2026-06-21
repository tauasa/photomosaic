package org.tauasa.apps.photomosaic.provider;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Tiny bridge for running UI work on the JavaFX application thread from a background thread.
 *
 * <p>Provider {@code select(...)} methods run off the FX thread (so slow network/DB work
 * doesn't freeze the UI), but dialogs and choosers must run on it. {@link #call} marshals a
 * value-returning action there and blocks until it completes; {@link #run} fires a no-result
 * action.
 */
public final class Fx {

    private Fx() {
    }

    public static <T> T call(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return action.call();
        }
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        try {
            return task.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(cause);
        }
    }

    public static void run(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
