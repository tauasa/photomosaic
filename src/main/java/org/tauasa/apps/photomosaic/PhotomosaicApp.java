package org.tauasa.apps.photomosaic;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Control;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.tauasa.apps.photomosaic.mosaic.MosaicConfig;
import org.tauasa.apps.photomosaic.mosaic.MosaicEngine;
import org.tauasa.apps.photomosaic.mosaic.Tile;
import org.tauasa.apps.photomosaic.mosaic.TileLibrary;
import org.tauasa.apps.photomosaic.provider.LocalPhotoProvider;
import org.tauasa.apps.photomosaic.provider.PhotoProvider;
import org.tauasa.apps.photomosaic.provider.PhotoRef;
import org.tauasa.apps.photomosaic.provider.ProgressSink;
import org.tauasa.apps.photomosaic.provider.db.DbConfig;
import org.tauasa.apps.photomosaic.provider.db.PhotoStore;
import org.tauasa.apps.photomosaic.provider.db.PostgresPhotoProvider;
import org.tauasa.apps.photomosaic.provider.google.GooglePhotoProvider;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Photomosaic generator. Pick a target image, gather tile images through a
 * {@link PhotoProvider}, tune the grid, and generate.
 */
public class PhotomosaicApp extends Application {

    private final MosaicEngine engine = new MosaicEngine();

    /** Tile sources, populated in {@link #start} (Google needs the host services). */
    private List<PhotoProvider> providers;
    private PhotoStore photoStore;

    // state
    private BufferedImage targetImage;
    private BufferedImage mosaicImage;
    private final TileLibrary library = new TileLibrary();

    // views
    private final ImageView targetView = new ImageView();
    private final ImageView mosaicView = new ImageView();
    private final Label status = new Label("Choose a target image to begin.");
    private final ProgressBar progress = new ProgressBar(0);

    // controls
    private final Spinner<Integer> colsSpinner = new Spinner<>(8, 400, 80, 4);
    private final Spinner<Integer> rowsSpinner = new Spinner<>(8, 400, 80, 4);
    private final Spinner<Integer> cellSpinner = new Spinner<>(8, 128, 32, 4);
    private final Slider blendSlider = new Slider(0, 1, 0.25);
    private final Slider repeatSlider = new Slider(0, 2000, 250);

    private Button generateBtn;
    private Button saveBtn;
    private Button cancelBtn;
    private final Label tileCount = new Label("Tiles: 0");

    @Override
    public void start(Stage stage) {
        photoStore = new PhotoStore(DbConfig.load());
        providers = List.of(
                new LocalPhotoProvider(),
                new GooglePhotoProvider(photoStore, getHostServices()::showDocument),
                new PostgresPhotoProvider(photoStore));

        targetView.setPreserveRatio(true);
        targetView.setFitWidth(640);
        mosaicView.setPreserveRatio(true);
        mosaicView.setFitWidth(640);

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar());
        root.setLeft(buildControls(stage));
        root.setCenter(buildPreview());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1060, 760);
        Theme.apply(scene);
        stage.setScene(scene);
        stage.setTitle("Photomosaic \u2014 org.tauasa.apps.photomosaic");
        stage.show();
    }

    // ---- UI construction ---------------------------------------------------

    private static final String PROJECT_URL = "https://tauasa.org/photomosaic";

    private MenuBar buildMenuBar() {
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> Platform.exit());
        Menu fileMenu = new Menu("File");
        fileMenu.getItems().add(exitItem);

        MenuItem clearItem = new MenuItem("Clear Tiles");
        clearItem.setOnAction(e -> clearTiles());
        Menu editMenu = new Menu("Edit");
        editMenu.getItems().add(clearItem);

        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showAbout());
        Menu helpMenu = new Menu("Help");
        helpMenu.getItems().add(aboutItem);

        MenuBar menuBar = new MenuBar(fileMenu, editMenu, helpMenu);
        // On macOS this hands the menu to the system bar; harmless elsewhere.
        menuBar.setUseSystemMenuBar(true);
        return menuBar;
    }

    private void clearTiles() {
        if (library.size() == 0) {
            status.setText("No tiles to clear.");
            return;
        }
        int n = library.size();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Photomosaic");
        confirm.setHeaderText("Clear all tiles?");
        confirm.setContentText("This removes the " + n + " loaded tile" + (n == 1 ? "" : "s")
                + " from the current session. Photos saved in Postgres are not affected.");
        confirm.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        Theme.apply(confirm.getDialogPane());
        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                library.clear();
                tileCount.setText("Tiles: 0");
                refreshGenerateEnabled();
                status.setText("Cleared " + n + " tile" + (n == 1 ? "" : "s") + ".");
            }
        });
    }

    private void showAbout() {
        Hyperlink link = new Hyperlink(PROJECT_URL);
        // HostServices opens the user's default browser without pulling in AWT Desktop.
        link.setOnAction(e -> getHostServices().showDocument(PROJECT_URL));
        link.setPadding(Insets.EMPTY);

        VBox content = new VBox(6,
                new Label("A JavaFX photomosaic generator."),
                new Label("\u00A9 2026 Tauasa Timoteo. Released under the MIT License."),
                link);
        content.setPadding(new Insets(4));

        Alert about = new Alert(Alert.AlertType.INFORMATION);
        about.setTitle("About Photomosaic");
        about.setHeaderText("Photomosaic 1.0.0");
        about.getDialogPane().setContent(content);
        Theme.apply(about.getDialogPane());
        about.showAndWait();
    }

    private VBox buildControls(Stage stage) {
        Button targetBtn = new Button("Choose target image\u2026");
        targetBtn.setMaxWidth(Double.MAX_VALUE);
        targetBtn.setOnAction(e -> chooseTarget(stage));

        // One button per registered photo provider.
        VBox providerButtons = new VBox(8);
        for (PhotoProvider provider : providers) {
            Button b = new Button("Add tiles from " + provider.displayName() + "\u2026");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> addTilesFrom(provider, stage));
            providerButtons.getChildren().add(b);
        }
        tileCount.getStyleClass().addAll("readout", "mono");

        colsSpinner.setEditable(true);
        rowsSpinner.setEditable(true);
        cellSpinner.setEditable(true);

        generateBtn = new Button("Generate mosaic");
        generateBtn.setMaxWidth(Double.MAX_VALUE);
        generateBtn.setDefaultButton(true);
        generateBtn.setDisable(true);
        generateBtn.getStyleClass().add("primary");
        generateBtn.setOnAction(e -> generate());

        saveBtn = new Button("Save mosaic\u2026");
        saveBtn.setMaxWidth(Double.MAX_VALUE);
        saveBtn.setDisable(true);
        saveBtn.getStyleClass().add("ghost");
        saveBtn.setOnAction(e -> save(stage));

        VBox box = new VBox(8,
                buildWordmark(),
                sectionHeader("01", "Source"),
                targetBtn,
                new Separator(),
                sectionHeader("02", "Tiles"),
                providerButtons, tileCount,
                new Separator(),
                sectionHeader("03", "Grid"),
                labelled("Columns", colsSpinner),
                labelled("Rows", rowsSpinner),
                labelled("Cell px", cellSpinner),
                sliderField("Blend", blendSlider, v -> Math.round(v * 100) + "%"),
                sliderField("Anti-repeat", repeatSlider, v -> String.valueOf(Math.round(v))),
                new Separator(),
                generateBtn, saveBtn);
        box.getStyleClass().add("rail");
        box.setPrefWidth(300);
        return box;
    }

    private Node buildWordmark() {
        GridPane mark = new GridPane();
        mark.setHgap(2);
        mark.setVgap(2);
        String[] colors = {"#ECEEF3", "#3BA7A0", "#3BA7A0", "#E8643C"};
        for (int i = 0; i < 4; i++) {
            Region tile = new Region();
            tile.getStyleClass().add("tessera");
            tile.setStyle("-fx-background-color: " + colors[i] + ";");
            mark.add(tile, i % 2, i / 2);
        }
        Label text = new Label("Photomosaic");
        text.getStyleClass().add("wordmark-text");
        HBox box = new HBox(11, mark, text);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(2, 0, 8, 0));
        return box;
    }

    private TabPane buildPreview() {
        Tab t1 = new Tab("Target", wrapScroll(targetView));
        Tab t2 = new Tab("Mosaic", wrapScroll(mosaicView));
        TabPane tabs = new TabPane(t1, t2);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return tabs;
    }

    private HBox buildStatusBar() {
        progress.setPrefWidth(220);
        status.getStyleClass().add("status-text");
        cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("ghost");
        cancelBtn.setVisible(false);
        cancelBtn.setManaged(false);
        HBox bar = new HBox(12, status, progress, cancelBtn);
        bar.getStyleClass().add("statusbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(8, 12, 8, 12));
        HBox.setHgrow(status, Priority.ALWAYS);
        status.setMaxWidth(Double.MAX_VALUE);
        return bar;
    }

    // ---- actions -----------------------------------------------------------

    private void chooseTarget(Stage stage) {
        File f = imageChooser("Choose target image").showOpenDialog(stage);
        if (f == null) return;
        try {
            targetImage = ImageIO.read(f);
            if (targetImage == null) throw new Exception("Unsupported image format.");
            targetView.setImage(toFx(targetImage));
            applyAspectToGrid(targetImage.getWidth(), targetImage.getHeight());
            refreshGenerateEnabled();
            status.setText("Target loaded: " + f.getName() + "  (" + targetImage.getWidth()
                    + "\u00d7" + targetImage.getHeight() + ")  \u2192 grid "
                    + colsSpinner.getValue() + "\u00d7" + rowsSpinner.getValue());
        } catch (Exception ex) {
            error("Could not load target image", ex);
        }
    }

    /**
     * Match the column:row grid to the target's width:height so the mosaic isn't stretched.
     * The longer side keeps the current density; the shorter side is scaled to suit.
     */
    private void applyAspectToGrid(int imgW, int imgH) {
        if (imgW <= 0 || imgH <= 0) return;
        int max = 400;
        int base = clamp(Math.max(colsSpinner.getValue(), rowsSpinner.getValue()), 8, max);
        int cols, rows;
        if (imgW >= imgH) {
            cols = base;
            rows = clamp((int) Math.round(base * (imgH / (double) imgW)), 8, max);
        } else {
            rows = base;
            cols = clamp((int) Math.round(base * (imgW / (double) imgH)), 8, max);
        }
        colsSpinner.getValueFactory().setValue(cols);
        rowsSpinner.getValueFactory().setValue(rows);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void addTilesFrom(PhotoProvider provider, Stage stage) {
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                ProgressSink sink = new ProgressSink() {
                    @Override
                    public void status(String message) {
                        updateMessage(message);
                    }

                    @Override
                    public void progress(double fraction) {
                        updateProgress(Math.max(0, Math.min(1, fraction)), 1);
                    }
                };

                updateMessage("Selecting\u2026");
                List<PhotoRef> refs = provider.select(stage, sink);
                if (refs.isEmpty()) {
                    return 0;
                }

                int added = 0;
                for (int i = 0; i < refs.size(); i++) {
                    if (isCancelled()) {
                        break;
                    }
                    PhotoRef ref = refs.get(i);
                    try {
                        BufferedImage img = ref.load();
                        if (img != null) {
                            library.add(new Tile(ref.id(), img));
                            added++;
                        }
                    } catch (Exception ignored) {
                        // skip unreadable images
                    }
                    updateProgress(i + 1, refs.size());
                    updateMessage("Loading tiles\u2026 " + (i + 1) + "/" + refs.size());
                }
                return added;
            }
        };
        runTask(task, () -> {
            tileCount.setText("Tiles: " + library.size());
            refreshGenerateEnabled();
            status.setText("Added " + task.getValue() + " tiles from " + provider.displayName() + ".");
        }, true);
    }

    private void generate() {
        if (targetImage == null || library.size() == 0) return;
        MosaicConfig cfg = new MosaicConfig(
                colsSpinner.getValue(),
                rowsSpinner.getValue(),
                cellSpinner.getValue(),
                cellSpinner.getValue(),
                (float) blendSlider.getValue(),
                repeatSlider.getValue());

        // The output canvas itself can blow the heap if the grid is cranked up
        // (e.g. 400×400 cells at 128 px ≈ a 50k-px image). Catch it before allocating.
        long outBytes = (long) cfg.outputWidth() * cfg.outputHeight() * 4L;
        if (outBytes > 1_200_000_000L) {
            warn("That mosaic would be too large",
                    "The current grid would render a " + cfg.outputWidth() + "\u00d7" + cfg.outputHeight()
                            + " px image (~" + (outBytes / 1_048_576L) + " MB in memory).\n\n"
                            + "Lower the columns, rows, or cell size and try again.");
            return;
        }

        Task<BufferedImage> task = new Task<>() {
            @Override
            protected BufferedImage call() {
                updateMessage("Building mosaic\u2026");
                return engine.generate(targetImage, library, cfg,
                        (done, total) -> updateProgress(done, total));
            }
        };
        runTask(task, () -> {
            mosaicImage = task.getValue();
            mosaicView.setImage(toFx(mosaicImage));
            saveBtn.setDisable(false);
            status.setText("Mosaic ready: " + cfg.outputWidth() + "\u00d7" + cfg.outputHeight()
                    + " px from " + library.size() + " tiles.");
        }, false);
    }

    private void save(Stage stage) {
        if (mosaicImage == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Save mosaic");
        fc.setInitialFileName("mosaic.png");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("JPEG", "*.jpg"));
        File f = fc.showSaveDialog(stage);
        if (f == null) return;
        try {
            String fmt = f.getName().toLowerCase().endsWith(".jpg") ? "jpg" : "png";
            ImageIO.write(mosaicImage, fmt, f);
            status.setText("Saved " + f.getAbsolutePath());
        } catch (Exception ex) {
            error("Could not save mosaic", ex);
        }
    }

    // ---- helpers -----------------------------------------------------------

    private void runTask(Task<?> task, Runnable onDone, boolean cancellable) {
        progress.progressProperty().bind(task.progressProperty());
        status.textProperty().bind(task.messageProperty());
        setControlsDisabled(true);
        if (cancellable) {
            cancelBtn.setOnAction(e -> task.cancel(true));
            showCancel(true);
        }

        task.setOnSucceeded(e -> {
            unbind();
            onDone.run();
        });
        task.setOnFailed(e -> {
            unbind();
            error("Something went wrong", task.getException());
        });
        task.setOnCancelled(e -> {
            unbind();
            status.setText("Cancelled.");
        });

        Thread th = new Thread(task, "photomosaic-worker");
        th.setDaemon(true);
        th.start();
    }

    private void showCancel(boolean on) {
        cancelBtn.setVisible(on);
        cancelBtn.setManaged(on);
    }

    private void unbind() {
        progress.progressProperty().unbind();
        status.textProperty().unbind();
        progress.setProgress(0);
        showCancel(false);
        setControlsDisabled(false);
        tileCount.setText("Tiles: " + library.size());
        refreshGenerateEnabled();
    }

    private void setControlsDisabled(boolean disabled) {
        generateBtn.setDisable(disabled);
        saveBtn.setDisable(disabled || mosaicImage == null);
    }

    private void refreshGenerateEnabled() {
        generateBtn.setDisable(targetImage == null || library.size() == 0);
    }

    private FileChooser imageChooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.tif", "*.tiff", "*.webp"));
        return fc;
    }

    private static HBox sectionHeader(String number, String title) {
        Label chip = new Label(number);
        chip.getStyleClass().add("chip");
        Label name = new Label(title.toUpperCase(Locale.ROOT));
        name.getStyleClass().add("section-title");
        HBox h = new HBox(9, chip, name);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    private static VBox labelled(String text, Node control) {
        if (control instanceof Control c) {
            c.setMaxWidth(Double.MAX_VALUE);
        }
        return new VBox(4, new Label(text), control);
    }

    /** A slider row with a live mono value readout, echoing the PWA's grid controls. */
    private static VBox sliderField(String text, Slider slider, java.util.function.DoubleFunction<String> fmt) {
        slider.setMaxWidth(Double.MAX_VALUE);
        Label name = new Label(text);
        Label value = new Label(fmt.apply(slider.getValue()));
        value.getStyleClass().add("mono");
        value.setStyle("-fx-text-fill: #3BA7A0; -fx-font-size: 12px;");
        slider.valueProperty().addListener((o, a, b) -> value.setText(fmt.apply(b.doubleValue())));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(name, spacer, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(4, row, slider);
    }

    private static ScrollPane wrapScroll(ImageView view) {
        ScrollPane sp = new ScrollPane(view);
        sp.getStyleClass().add("canvas-wrap");
        sp.setPannable(true);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        return sp;
    }

    private static Image toFx(BufferedImage img) {
        return SwingFXUtils.toFXImage(img, null);
    }

    private void error(String header, Throwable ex) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Photomosaic");
        a.setHeaderText(header);
        a.setContentText(ex == null ? "Unknown error" : String.valueOf(ex.getMessage()));
        Theme.apply(a.getDialogPane());
        a.showAndWait();
    }

    private void warn(String header, String body) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Photomosaic");
        a.setHeaderText(header);
        a.setContentText(body);
        Theme.apply(a.getDialogPane());
        a.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
