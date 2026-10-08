# Sheets

A minimal sheet music reader for an Android tablet with a pen: open PDFs from a folder, write on
them, and the notes stay put.

## Build and run

Uses the JDK and Android SDK that ship with Android Studio. From Git Bash:

```sh
source env.sh                 # puts java and adb on PATH for this shell
./gradlew installRelease      # "Sheets": the minified build for real use
./gradlew installDebug        # "Sheets Dev": a separate app for development
```

The two builds are separate apps with separate data. Sheets Dev keeps its folder copy of notes in
`.annotations-dev`, so development never touches the notes made in Sheets. Debug builds are slower
(Compose debug builds aren't representative), so judge performance on release.

```sh
./gradlew testDebugUnitTest            # JVM tests (scratch-out recognition, crop detection)
./gradlew connectedDebugAndroidTest    # tablet tests, run in Sheets Dev; leaves it installed
```

## How it works

- **Library** (`library/`): the user picks a folder once via the Storage Access Framework; every PDF
  in it (recursively) is listed.
- **Reader** (`reader/`, `pdf/`): pages are rendered with the platform `PdfRenderer`, fitted to the
  screen in a horizontal pager, and reopen where you left off. Tap the left/right third to turn
  pages, the middle to toggle the toolbar (a top bar, or a side rail when the page leaves room).
  Pinch to zoom; the visible part is re-rendered sharply once the pinch settles.
  Blank side margins are cropped away (`PageCrops`): each page's printed area is found once per
  document by scanning low-resolution renders. Notes beside the crop widen it, so they're never
  hidden.
- **Editing**: scribble back and forth over ink to erase it (`ScratchOut`, which ignores trills,
  hairpins, circles and scribbles over nothing). Two-finger tap undoes, three-finger tap redoes.
- **Ink** (`ink/`): `InkHostLayout` wraps the whole UI at the View level. Pen input that starts on a
  page is drawn as low-latency wet ink by Jetpack Ink's front-buffered `InProgressStrokesView` and
  never reaches Compose; fingers (and the pen anywhere else) work the UI as usual. `PalmGuard`
  ignores finger gestures that start while the pen is down or hovering. The device's own
  palm rejection, if it has one, can be enabled for the app as well.
- **Annotations** (`annotations/`): strokes are stored in PDF point coordinates, so they stay aligned
  at any size or orientation, keyed by a SHA-256 of the PDF's contents. PDFs are never modified.
  Each save goes to app storage and to a hidden `.annotations` folder inside the sheet music folder
  (once the app has write access; the library asks for it). The newest copy wins on load, so notes
  survive uninstalling: pick the same folder again and they're back. The file also records the
  order strokes were drawn in, so undo reaches back across sessions.
- **Pens** (`ink/Pens.kt`): a few colors and three widths, picked in the toolbar and remembered.
