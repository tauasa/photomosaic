# Packaging Photomosaic for macOS

Two ways to get a double-clickable **Photomosaic.app**. Both must be run **on a Mac**.

| | `package-mac.sh` | `build-quick-app.sh` |
|---|---|---|
| Output | `build/mac/Photomosaic.app` (+ optional `.dmg`) | `build/mac-quick/Photomosaic.app` |
| Bundles a Java runtime | **Yes** (self-contained) | No — uses the Mac's installed Java |
| Needs | JDK 17+ (for `jpackage`) + Maven | A JDK 17+ on the user's machine + Maven |
| Best for | Sharing / distributing | Quick local use |

---

## Option A — self-contained app (recommended)

Uses the JDK's `jpackage` to bundle a trimmed runtime inside the app, so whoever
runs it doesn't need Java installed.

```bash
./packaging/mac/package-mac.sh            # → build/mac/Photomosaic.app
./packaging/mac/package-mac.sh --dmg      # also build a Photomosaic-1.0.0.dmg
./packaging/mac/package-mac.sh --rebuild  # force a clean mvn package first
```

The icon (`Photomosaic.icns`) is generated from `Photomosaic.png` automatically via
`make-icns.sh` if it isn't already present.

---

## Option B — quick launcher (no bundled JRE)

Assembles a small `.app` whose launcher script finds the Java already on the machine
and runs the fat jar. Lighter, but the user needs a JDK 17+ installed.

```bash
./packaging/mac/build-quick-app.sh        # → build/mac-quick/Photomosaic.app
```

---

## Gatekeeper (unsigned builds)

Both options are **unsigned** by default, so on first launch macOS may say the app
"can't be opened." Either:

- **Right-click the app → Open** (then confirm), or
- `xattr -dr com.apple.quarantine /path/to/Photomosaic.app`

To sign with a Developer ID instead:

```bash
./packaging/mac/package-mac.sh --dmg --sign "Developer ID Application: Your Name (TEAMID)"
```

(For distribution outside your own machines you'll also want to *notarize* the app/dmg
with `xcrun notarytool` — outside the scope of these scripts.)

---

## Files here

```
packaging/mac/
├── package-mac.sh        self-contained .app + .dmg via jpackage
├── build-quick-app.sh    quick .app using the system Java
├── make-icns.sh          Photomosaic.png → Photomosaic.icns (sips + iconutil)
├── Photomosaic.png       1024×1024 source icon (tesserae motif)
├── Photomosaic.icns      prebuilt icon
└── app-skeleton/
    └── Photomosaic.app/  bundle template for the quick launcher
```
