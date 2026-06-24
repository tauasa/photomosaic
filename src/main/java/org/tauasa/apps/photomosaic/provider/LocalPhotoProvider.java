package org.tauasa.apps.photomosaic.provider;

import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Default {@link PhotoProvider}: the user picks a folder and every image in it becomes a tile.
 *
 * <p>Recognised by extension; decoding goes through {@link ImageIO}, so any format with a
 * registered reader works (the TwelveMonkeys plugins on the classpath widen this to include
 * the awkward JPEG/TIFF variants the stock JDK reader chokes on).
 */
public final class LocalPhotoProvider implements PhotoProvider {

    private static final List<String> EXTENSIONS =
            List.of(".jpg", ".jpeg", ".png", ".bmp", ".gif", ".tif", ".tiff", ".webp");

    private final boolean recursive;

    public LocalPhotoProvider() {
        this(false);
    }

    /** @param recursive whether to descend into sub-folders */
    public LocalPhotoProvider(boolean recursive) {
        this.recursive = recursive;
    }

    @Override
    public String displayName() {
        return "Local files";
    }

    @Override
    public List<PhotoRef> select(Window owner, ProgressSink progress) throws Exception {
        File dir = Fx.call(() -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Folder of tile images");
            return chooser.showDialog(owner);
        });
        if (dir == null) {
            return List.of();
        }
        progress.status("Scanning " + dir.getName() + "\u2026");
        List<PhotoRef> refs = new ArrayList<>();
        collect(dir, refs);
        return refs;
    }

    private void collect(File dir, List<PhotoRef> out) {
        File[] entries = dir.listFiles();
        if (entries == null) {
            return;
        }
        Arrays.sort(entries);
        for (File f : entries) {
            if (f.isDirectory()) {
                if (recursive) {
                    collect(f, out);
                }
            } else if (isImage(f)) {
                out.add(new FilePhotoRef(f));
            }
        }
    }

    private static boolean isImage(File f) {
        String name = f.getName().toLowerCase(Locale.ROOT);
        return EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    /** A photo backed by a file on disk. */
    private record FilePhotoRef(File file) implements PhotoRef {

        @Override
        public String id() {
            return file.getName();
        }

        @Override
        public BufferedImage load() throws IOException {
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                throw new IOException("No image reader for " + file.getName());
            }
            return img;
        }
    }
}
