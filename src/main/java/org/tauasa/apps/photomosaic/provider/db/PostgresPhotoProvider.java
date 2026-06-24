package org.tauasa.apps.photomosaic.provider.db;

import javafx.scene.control.ChoiceDialog;
import javafx.stage.Window;
import org.tauasa.apps.photomosaic.Theme;
import org.tauasa.apps.photomosaic.provider.Dialogs;
import org.tauasa.apps.photomosaic.provider.Fx;
import org.tauasa.apps.photomosaic.provider.PhotoProvider;
import org.tauasa.apps.photomosaic.provider.PhotoRef;
import org.tauasa.apps.photomosaic.provider.ProgressSink;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reloads tiles previously saved to Postgres (e.g. by {@link
 * org.tauasa.apps.photomosaic.provider.google.GooglePhotoProvider}). The user picks a saved
 * collection — or all of them — and those images come back as tiles, no network required.
 */
public final class PostgresPhotoProvider implements PhotoProvider {

    private static final String ALL = "All saved photos";

    private final PhotoStore store;

    public PostgresPhotoProvider(PhotoStore store) {
        this.store = store;
    }

    @Override
    public String displayName() {
        return "Saved (Postgres)";
    }

    @Override
    public List<PhotoRef> select(Window owner, ProgressSink progress) throws Exception {
        if (!store.isConfigured()) {
            Dialogs.warn(owner, "No database configured",
                    "Set PHOTOMOSAIC_DB_URL / _USER / _PASSWORD, or create ~/.photomosaic/db.properties. "
                            + "See the README.");
            return List.of();
        }
        store.init();

        List<PhotoStore.Collection> collections = store.collections();
        if (collections.isEmpty()) {
            Dialogs.info(owner, "Nothing saved yet",
                    "Import photos from Google Photos first — they're saved here automatically for reuse.");
            return List.of();
        }

        int total = collections.stream().mapToInt(PhotoStore.Collection::count).sum();
        Map<String, String> labelToCollection = new LinkedHashMap<>();
        List<String> choices = new ArrayList<>();
        String allLabel = ALL + " (" + total + ")";
        choices.add(allLabel);
        labelToCollection.put(allLabel, null);
        for (PhotoStore.Collection c : collections) {
            String label = c.name() + " (" + c.count() + ")";
            choices.add(label);
            labelToCollection.put(label, c.name());
        }

        String chosen = Fx.call(() -> {
            ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(0), choices);
            if (owner != null) {
                dialog.initOwner(owner);
            }
            dialog.setTitle("Photomosaic");
            dialog.setHeaderText("Load saved photos");
            dialog.setContentText("Collection:");
            Theme.apply(dialog.getDialogPane());
            return dialog.showAndWait().orElse(null);
        });
        if (chosen == null) {
            return List.of();
        }

        progress.status("Loading saved photos\u2026");
        return store.refs(labelToCollection.get(chosen));
    }
}
