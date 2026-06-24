package org.tauasa.apps.photomosaic.provider.google;

import com.google.api.client.auth.oauth2.Credential;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;
import org.tauasa.apps.photomosaic.Theme;
import org.tauasa.apps.photomosaic.provider.Dialogs;
import org.tauasa.apps.photomosaic.provider.Fx;
import org.tauasa.apps.photomosaic.provider.PhotoProvider;
import org.tauasa.apps.photomosaic.provider.PhotoRef;
import org.tauasa.apps.photomosaic.provider.ProgressSink;
import org.tauasa.apps.photomosaic.provider.db.PhotoStore;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lets the user pick photos from their Google Photos account (via the Picker API) and saves a
 * small copy of each to Postgres, so the same set can be reloaded later through {@link
 * org.tauasa.apps.photomosaic.provider.db.PostgresPhotoProvider} without going back to Google.
 *
 * <p>Why persist bytes rather than a Google link: the Picker API only returns temporary URLs
 * tied to the picking session, so a saved URL would be dead later. Tiles only need a small
 * image anyway, so we download at {@link #DOWNLOAD_PX} px and store those bytes.
 *
 * <p>Setup (one-time): enable the Photos Picker API in a Google Cloud project, create a
 * Desktop OAuth client, and put its {@code client_secret.json} at
 * {@code ~/.photomosaic/client_secret.json}. See the README.
 */
public final class GooglePhotoProvider implements PhotoProvider {

    private static final int DOWNLOAD_PX = 256;

    private final PhotoStore store;
    private final Consumer<String> browser; // opens a URL in the user's browser
    private Credential credential;           // cached after first sign-in

    public GooglePhotoProvider(PhotoStore store, Consumer<String> browser) {
        this.store = store;
        this.browser = browser;
    }

    @Override
    public String displayName() {
        return "Google Photos";
    }

    @Override
    public List<PhotoRef> select(Window owner, ProgressSink progress) throws Exception {
        if (!store.isConfigured()) {
            Dialogs.warn(owner, "No database configured",
                    "Google Photos picks are saved to Postgres for reuse, so a database is required. "
                            + "Set PHOTOMOSAIC_DB_URL / _USER / _PASSWORD or ~/.photomosaic/db.properties. See the README.");
            return List.of();
        }
        File home = new File(System.getProperty("user.home"), ".photomosaic");
        File clientSecret = new File(home, "client_secret.json");
        if (!clientSecret.isFile()) {
            Dialogs.warn(owner, "Google sign-in isn't set up",
                    "Place your Desktop OAuth client_secret.json at:\n" + clientSecret + "\n\nSee the README for the one-time Google Cloud setup.");
            return List.of();
        }

        store.init();

        progress.status("Signing in to Google\u2026");
        if (credential == null) {
            credential = new GoogleAuth().authorize(clientSecret, new File(home, "tokens"));
        }

        PhotosPickerClient picker = new PhotosPickerClient(credential);
        PhotosPickerClient.PickingSession session = picker.createSession();

        progress.status("Opening the Google Photos picker in your browser\u2026");
        Fx.run(() -> browser.accept(session.pickerUri()));

        try {
            progress.status("Waiting for you to choose photos\u2026");
            picker.pollUntilReady(session.id(),
                    ms -> progress.status("Waiting for your selection\u2026 " + (ms / 1000) + "s"));

            List<PickedPhoto> photos = picker.listMediaItems(session.id());
            if (photos.isEmpty()) {
                return List.of();
            }

            String defaultName = "google " + LocalDate.now();
            String collection = Fx.call(() -> {
                TextInputDialog dialog = new TextInputDialog(defaultName);
                if (owner != null) {
                    dialog.initOwner(owner);
                }
                dialog.setTitle("Photomosaic");
                dialog.setHeaderText("Save these photos to Postgres");
                dialog.setContentText("Collection name:");
                Theme.apply(dialog.getDialogPane());
                return dialog.showAndWait().orElse(null);
            });
            if (collection == null || collection.isBlank()) {
                collection = defaultName;
            }

            List<PhotoRef> refs = new ArrayList<>();
            int n = photos.size();
            for (int i = 0; i < n; i++) {
                if (Thread.currentThread().isInterrupted()) {
                    break; // cancelled
                }
                PickedPhoto photo = photos.get(i);
                if (photo.isImage()) {
                    try {
                        byte[] bytes = picker.downloadBytes(photo, DOWNLOAD_PX, DOWNLOAD_PX);
                        String id = store.save(collection, photo.filename(), photo.mimeType(), bytes);
                        refs.add(store.ref(id, photo.filename()));
                    } catch (InterruptedException cancelled) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception perPhoto) {
                        // skip a photo that fails to download/save
                    }
                }
                progress.progress((double) (i + 1) / n);
                progress.status("Saving to Postgres\u2026 " + (i + 1) + "/" + n);
            }
            return refs;
        } finally {
            picker.deleteSession(session.id());
        }
    }
}
