package org.tauasa.apps.photomosaic.provider.db;

import org.tauasa.apps.photomosaic.provider.PhotoRef;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.SQLException;

/** A {@link PhotoRef} whose bytes live in the Postgres {@link PhotoStore}. */
final class StoredPhotoRef implements PhotoRef {

    private final PhotoStore store;
    private final String dbId;
    private final String filename;

    StoredPhotoRef(PhotoStore store, String dbId, String filename) {
        this.store = store;
        this.dbId = dbId;
        this.filename = filename;
    }

    @Override
    public String id() {
        return filename != null ? filename : dbId;
    }

    @Override
    public BufferedImage load() throws IOException {
        byte[] bytes;
        try {
            bytes = store.data(dbId);
        } catch (SQLException e) {
            throw new IOException("Database read failed for " + id(), e);
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) {
            throw new IOException("Unreadable stored image: " + id());
        }
        return img;
    }
}
