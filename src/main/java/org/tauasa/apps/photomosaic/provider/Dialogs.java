package org.tauasa.apps.photomosaic.provider;

import javafx.scene.control.Alert;
import javafx.stage.Window;
import org.tauasa.apps.photomosaic.Theme;

/** Small themed notification dialogs that can be invoked from any thread. */
public final class Dialogs {

    private Dialogs() {
    }

    public static void warn(Window owner, String header, String body) {
        show(Alert.AlertType.WARNING, owner, header, body);
    }

    public static void info(Window owner, String header, String body) {
        show(Alert.AlertType.INFORMATION, owner, header, body);
    }

    private static void show(Alert.AlertType type, Window owner, String header, String body) {
        Fx.run(() -> {
            Alert a = new Alert(type);
            if (owner != null) {
                a.initOwner(owner);
            }
            a.setTitle("Photomosaic");
            a.setHeaderText(header);
            a.setContentText(body);
            Theme.apply(a.getDialogPane());
            a.showAndWait();
        });
    }
}
