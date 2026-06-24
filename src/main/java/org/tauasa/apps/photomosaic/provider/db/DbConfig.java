package org.tauasa.apps.photomosaic.provider.db;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Postgres connection settings, resolved (in order) from environment variables
 * {@code PHOTOMOSAIC_DB_URL / _USER / _PASSWORD}, then {@code ~/.photomosaic/db.properties}
 * (keys {@code url}, {@code user}, {@code password}).
 *
 * <p>The app never hardcodes credentials; you supply your own database.
 */
public record DbConfig(String url, String user, String password) {

    public boolean isComplete() {
        return url != null && !url.isBlank();
    }

    public static DbConfig load() {
        String url = env("PHOTOMOSAIC_DB_URL");
        String user = env("PHOTOMOSAIC_DB_USER");
        String password = env("PHOTOMOSAIC_DB_PASSWORD");

        if (url == null) {
            Path file = Path.of(System.getProperty("user.home"), ".photomosaic", "db.properties");
            if (Files.isReadable(file)) {
                Properties p = new Properties();
                try (Reader r = Files.newBufferedReader(file)) {
                    p.load(r);
                    url = blankToNull(p.getProperty("url"));
                    user = blankToNull(p.getProperty("user"));
                    password = blankToNull(p.getProperty("password"));
                } catch (IOException ignored) {
                    // treated as unconfigured
                }
            }
        }
        return new DbConfig(url, user, password);
    }

    private static String env(String key) {
        return blankToNull(System.getenv(key));
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }
}
