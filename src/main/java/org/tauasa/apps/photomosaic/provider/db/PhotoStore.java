package org.tauasa.apps.photomosaic.provider.db;

import org.tauasa.apps.photomosaic.provider.PhotoRef;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Postgres-backed store of tile images. Each row holds a small image (the bytes downloaded at
 * tile size) under a named <em>collection</em>, so a set imported once can be reloaded later.
 *
 * <p>Images are stored as bytes — not as Google references — because the Picker API's URLs are
 * temporary. Connections are opened per operation via {@link DriverManager}; the PostgreSQL
 * driver registers itself through the JDBC service loader when it's on the classpath.
 */
public final class PhotoStore {

    private final DbConfig config;

    public PhotoStore(DbConfig config) {
        this.config = config;
    }

    public boolean isConfigured() {
        return config != null && config.isComplete();
    }

    private Connection connect() throws SQLException {
        if (!isConfigured()) {
            throw new SQLException("No database configured.");
        }
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }

    public void init() throws SQLException {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS tile_photo (
                        id          TEXT PRIMARY KEY,
                        collection  TEXT NOT NULL,
                        filename    TEXT,
                        mime        TEXT,
                        data        BYTEA NOT NULL,
                        created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_tile_photo_collection ON tile_photo(collection)");
        }
    }

    /** Persist one image and return its generated id. */
    public String save(String collection, String filename, String mime, byte[] data) throws SQLException {
        String id = UUID.randomUUID().toString();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO tile_photo (id, collection, filename, mime, data) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, collection);
            ps.setString(3, filename);
            ps.setString(4, mime);
            ps.setBytes(5, data);
            ps.executeUpdate();
        }
        return id;
    }

    public List<Collection> collections() throws SQLException {
        List<Collection> out = new ArrayList<>();
        try (Connection c = connect();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT collection, count(*) FROM tile_photo GROUP BY collection ORDER BY collection")) {
            while (rs.next()) {
                out.add(new Collection(rs.getString(1), rs.getInt(2)));
            }
        }
        return out;
    }

    /** References for a collection, or every saved photo when {@code collection} is null/blank. */
    public List<PhotoRef> refs(String collection) throws SQLException {
        boolean all = collection == null || collection.isBlank();
        String sql = all
                ? "SELECT id, filename FROM tile_photo ORDER BY created_at"
                : "SELECT id, filename FROM tile_photo WHERE collection = ? ORDER BY created_at";
        List<PhotoRef> out = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (!all) {
                ps.setString(1, collection);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StoredPhotoRef(this, rs.getString(1), rs.getString(2)));
                }
            }
        }
        return out;
    }

    /** Build a reference for an already-saved row. */
    public PhotoRef ref(String id, String filename) {
        return new StoredPhotoRef(this, id, filename);
    }

    /** Fetch the stored bytes for one photo. Package-private: used by {@link StoredPhotoRef}. */
    byte[] data(String id) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT data FROM tile_photo WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes(1);
                }
            }
        }
        throw new SQLException("No stored photo with id " + id);
    }

    public record Collection(String name, int count) {
    }
}
