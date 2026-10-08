# Sheets

A minimal sheet music reader for an Android tablet with a pen: open PDFs from a folder, write on
them, and the notes stay put.

## Build and run

Uses the JDK and Android SDK that ship with Android Studio. From Git Bash:

```sh
source env.sh                 # puts java and adb on PATH for this shell
./gradlew installRelease      # minified build signed with the debug key, best for real use
./gradlew installDebug        # debuggable build (slower; Compose debug builds aren't representative)
```

Debug and release share a signing key, so either can be installed over the other without losing
annotations. Annotations live in app storage: uninstalling the app (or clearing its data) deletes
them.

```sh
./gradlew testDebugUnitTest            # JVM tests (scratch-out recognition)
./gradlew connectedDebugAndroidTest    # gesture tests on the tablet; leaves the app installed
```

## How it works

- **Library** (`library/`): the user picks a folder once via the Storage Access Framework; every PDF
  in it (recursively) is listed.
- **Reader** (`reader/`, `pdf/`): pages are rendered with the platform `PdfRenderer`, fitted to the
  screen in a horizontal pager, and reopen where you left off. Tap the left/right third to turn
  pages, the middle to toggle the toolbar (a top bar, or a side rail when the page leaves room).
  Pinch to zoom; the visible part is re-rendered sharply once the pinch settles.
- **Editing**: scribble back and forth over ink to erase it (`ScratchOut`, which ignores trills,
  hairpins, circles and scribbles over nothing). Two-finger tap undoes, three-finger tap redoes.
- **Ink** (`ink/`): `InkHostLayout` wraps the whole UI at the View level. Pen input that starts on a
  page is drawn as low-latency wet ink by Jetpack Ink's front-buffered `InProgressStrokesView` and
  never reaches Compose; fingers (and the pen anywhere else) work the UI as usual. `PalmGuard`
  ignores finger gestures that start while the pen is down or hovering. The device's own
  palm rejection, if it has one, can be enabled for the app as well.
- **Annotations** (`annotations/`): strokes are stored in PDF point coordinates, so they stay aligned
  at any size or orientation, in app storage keyed by a SHA-256 of the PDF's contents. PDFs are
  never modified.
