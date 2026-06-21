package org.tauasa.apps.photomosaic;

/**
 * Plain entry point. Launching JavaFX from a non-Application main class avoids the
 * "JavaFX runtime components are missing" error when running a shaded/classpath jar.
 */
public final class Launcher {
    public static void main(String[] args) {
        PhotomosaicApp.main(args);
    }
}
