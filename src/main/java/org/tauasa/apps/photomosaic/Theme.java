package org.tauasa.apps.photomosaic;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.text.Font;

import java.io.InputStream;

/**
 * Loads the bundled fonts (Archivo + JetBrains Mono, both SIL OFL) and the theme
 * stylesheet. Fonts are registered with the JavaFX toolkit so the CSS can refer to them
 * by family name; if a face is missing the UI falls back to a system font.
 */
public final class Theme {

    private static final String[] FONTS = {
            "fonts/Archivo-Regular.ttf",
            "fonts/Archivo-Bold.ttf",
            "fonts/JetBrainsMono-Regular.ttf",
            "fonts/JetBrainsMono-Bold.ttf",
    };

    private static boolean fontsLoaded;

    private Theme() {
    }

    public static void loadFonts() {
        if (fontsLoaded) {
            return;
        }
        for (String path : FONTS) {
            try (InputStream in = Theme.class.getResourceAsStream(path)) {
                if (in != null) {
                    Font.loadFont(in, 12);
                }
            } catch (Exception ignored) {
                // fall back to system fonts
            }
        }
        fontsLoaded = true;
    }

    public static String stylesheet() {
        var url = Theme.class.getResource("theme.css");
        return url == null ? null : url.toExternalForm();
    }

    public static void apply(Scene scene) {
        loadFonts();
        String css = stylesheet();
        if (css != null) {
            scene.getStylesheets().add(css);
        }
    }

    public static void apply(DialogPane pane) {
        loadFonts();
        String css = stylesheet();
        if (css != null) {
            pane.getStylesheets().add(css);
        }
    }
}
