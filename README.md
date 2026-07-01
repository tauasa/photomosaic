# Photomosaic Generator

A JavaFX desktop app that builds **photomosaics** — recreating a target image out of a grid of smaller photos. Tile images are supplied through a pluggable **`PhotoProvider`**; the default implementation picks them from your **local filesystem**. Tiles are scaled with **Thumbnailator**; **TwelveMonkeys ImageIO** widens the range of source formats the app can decode.

![Target image](target.png)
![Mosaic image](mosaic.png)

---

## What it does

1. Pick a **target image** (the picture you want to recreate).
2. Gather **tile images** from a `PhotoProvider` — a **local folder**, your **Google Photos**, or photos you **previously saved to Postgres**.
3. Tune the grid (columns × rows, cell size, colour blend, anti-repeat).
4. **Generate** and **save** the mosaic as PNG/JPEG.

---

## Quick start

The simplest way to launch — these build the fat jar on first run, then start it (and skip the build next time):

```bash
./run.sh              # macOS / Linux
run.bat               # Windows
```

Add `--rebuild` to force a clean rebuild, and set `PHOTOMOSAIC_JAVA_OPTS` (e.g. `-Xmx4g`) for more heap with very large tile libraries.

Or drive Maven directly:

```bash
mvn clean javafx:run        # run from source
# or
mvn clean package           # build a fat jar
java -jar target/photomosaic.jar
```

Requires JDK 17+. JavaFX is pulled in by Maven, so no separate SDK install is needed. The **Local files** source needs no setup at all — click *“Add tiles from Local files”*, point it at a folder, and go. The Google Photos and Postgres sources need the one-time setup below.

---

## Tile sources (`PhotoProvider`)

Tile sources are pluggable. The contract splits choosing from loading so the UI stays responsive — `select(...)` runs on a background thread (marshalling any dialog to the FX thread and reporting progress), and `load()` does the per-image I/O:

```java
public interface PhotoProvider {
    String displayName();
    List<PhotoRef> select(Window owner, ProgressSink progress) throws Exception;
}

public interface PhotoRef {
    String id();                               // e.g. a filename
    BufferedImage load() throws IOException;   // off the FX thread; does the I/O
}
```

Three providers ship today:

- **Local files** (`LocalPhotoProvider`) — folder chooser; every image becomes a tile. Use `new LocalPhotoProvider(true)` to recurse into sub-folders.
- **Google Photos** (`GooglePhotoProvider`) — opens Google's photo picker; each pick is downloaded at tile size and **saved to Postgres**, then used as a tile.
- **Saved (Postgres)** (`PostgresPhotoProvider`) — reloads a previously saved collection (or all of them) straight from the database, no network needed.

### Why Google picks are stored as image bytes

Google removed library listing/search in April 2025; apps must use the **Picker API**, which hands back only **temporary** URLs tied to the picking session. A saved URL would be dead later — so to make "use again later" actually work, `GooglePhotoProvider` downloads a small (256 px) copy of each pick and stores the bytes in Postgres under a named *collection*. `PostgresPhotoProvider` reads them back. (Tiles are capped at 128 px anyway, so the stored
copies are tiny.)

### Google Photos setup (one-time)

1. In the [Google Cloud Console](https://console.cloud.google.com): create/select a project.
2. **Enable the Google Photos Picker API** (APIs & Services → Library).
3. Configure the **OAuth consent screen** (External is fine; add yourself as a test user).
4. Create an **OAuth client ID** of type **Desktop app** and download the JSON.
5. Save it as `~/.photomosaic/client_secret.json`.

The requested scope is read-only (`photospicker.mediaitems readonly`); the refresh token is cached under `~/.photomosaic/tokens` so you consent only once. Google handles sign-in in your browser — no password is entered into the app.

### Postgres setup (one-time)

Point the app at any Postgres database via environment variables:

```bash
export PHOTOMOSAIC_DB_URL="jdbc:postgresql://localhost:5432/photomosaic"
export PHOTOMOSAIC_DB_USER="postgres"
export PHOTOMOSAIC_DB_PASSWORD="..."
```

…or a `~/.photomosaic/db.properties` file with `url` / `user` / `password` keys. The app creates its `tile_photo` table automatically on first use. Credentials are never hardcoded — you supply your own database.

### Adding another source

Implement `PhotoProvider`, then register it in `PhotomosaicApp`'s `providers` list. The UI builds one *“Add tiles from …”* button per provider automatically — nothing else changes.

---

## Project layout

```
org.tauasa.apps.photomosaic
├── Launcher                  plain main() → starts JavaFX cleanly from a fat jar
├── PhotomosaicApp            the JavaFX UI; registers PhotoProviders
├── Theme                     loads bundled fonts + applies the stylesheet
├── mosaic
│   ├── ColorAnalysis         average colour (pixel sum) + Thumbnailator resize
│   ├── Tile                  a source image + its colour signature + cached render
│   ├── TileLibrary           nearest-colour matching (redmean) + anti-repeat
│   ├── MosaicConfig          grid / cell / blend settings
│   └── MosaicEngine          the algorithm
└── provider
    ├── PhotoProvider         pluggable tile source (interface)
    ├── PhotoRef              a lazily-loadable photo handle (interface)
    ├── ProgressSink          status/progress callback for selection
    ├── Fx                    run UI work on the FX thread from a worker
    ├── Dialogs               themed warning/info popups
    ├── LocalPhotoProvider    pick a folder from the local filesystem
    ├── google
    │   ├── GooglePhotoProvider   pick via Google Photos → save to Postgres
    │   ├── GoogleAuth            OAuth2 installed-app (loopback) flow
    │   ├── PhotosPickerClient    Picker REST: session → poll → list → download
    │   └── PickedPhoto           a picked media item
    └── db
        ├── PostgresPhotoProvider reload a saved collection
        ├── PhotoStore            Postgres repository (tile_photo table)
        ├── StoredPhotoRef        PhotoRef backed by a DB row
        └── DbConfig              connection settings (env / properties)

resources/org/tauasa/apps/photomosaic
├── theme.css                 "tesserae" dark theme (matches the PWA)
├── checker.png               transparency checker behind the preview
└── fonts/                    Archivo + JetBrains Mono (SIL OFL, bundled)
```

---

## Look & feel

The desktop UI shares the PWA's **"tesserae"** identity: a darkroom-ink workspace, two accent colours (ceramic vermilion + glass teal) used sparingly, numbered section chips, a gradient *Generate* button, mono numeric readouts, and a transparency-checker preview.

It's all driven by `theme.css` (JavaFX CSS) plus two bundled open-source typefaces — **Archivo** for display/body and **JetBrains Mono** for data — both under the SIL Open Font License (see `resources/.../fonts/OFL-*.txt`). `Theme.loadFonts()` registers them at startup, and the CSS falls back to system fonts if a face is ever unavailable.

---

## How the algorithm works

The target is shrunk to `columns × rows` with high-quality progressive scaling, so each resulting pixel approximates the average colour of one cell. For every cell we pick the nearest tile by a **redmean-weighted** colour distance (cheap perceptual approximation), with an optional penalty that discourages reusing the same tile. The chosen tile — pre-scaled once to the cell size — is blitted in, and an optional translucent wash of the cell's true colour nudges the result toward the original.

### Memory

Imported photos are downscaled to a small per-tile "master" (capped at the maximum cell size) the moment they're added, and the full-resolution originals are released. That keeps memory roughly flat with the number of tiles — a few dozen KB each — so libraries of hundreds or thousands of photos no longer exhaust the heap. The generator also refuses grid settings that would allocate an unreasonably large output image.

### Ideas for later
- **Lab / CIEDE2000** matching for more faithful colour.
- **k-d tree** over tile signatures (linear scan is fine for hundreds of tiles, less so for 10k+).
- Multiple **sub-cell samples** per tile for edge-aware placement.
- More `PhotoProvider`s (cloud albums, a URL list, a stock-photo API).

---
`org.tauasa.apps.photomosaic` · MIT License · Tauasa Timoteo