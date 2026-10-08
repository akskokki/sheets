# Sheets: agent rules

A sheet music reader for an Android tablet with a pen. For the architecture, read
[README.md](README.md) ("How it works"). This file holds the rules and the non-obvious parts. Keep
the README in sync when behavior changes.

If `AGENTS.local.md` exists, read it too. It isn't committed, and it describes this machine and
its test device.

## The user's notes and state

On the test device, the release app **Sheets** (`dev.axu.sheets`) holds the user's real notes.
Losing them is the one unrecoverable mistake. The debug build is a separate app, **Sheets Dev**
(`dev.axu.sheets.debug`), with its own data. Do all testing there.

- Never uninstall Sheets, clear its data, or touch its files. That rules out `adb uninstall`,
  `./gradlew uninstallAll`/`uninstallRelease`, and `pm clear`. It also covers the
  `<sheet folder>/.annotations/` copy of its notes: with `allowBackup="false"`, that copy is the
  only one outside the app. If an install fails with a signature mismatch, stop and ask.
- Never install a release build of an older commit, for example while bisecting. Use Sheets Dev
  for that. Builds before `NewerFormatException` existed set aside notes files they couldn't read.
- Don't change Sheets' settings, pen or reading positions. Opening a sheet and paging moves its
  remembered page.
- Notes files must stay readable forever. Changing the format means bumping
  `AnnotationCodec.VERSION` and still decoding every older version.
- These must never change meaning, or existing notes and state are orphaned:
  - `applicationId`, `NOTES_DIRECTORY`, the `.ink` extension and `AnnotationCodec`'s magic number
  - brush family IDs in `Pens` and the pinned `PressurePenVersion` (add a new ID; never remap one)
  - document keys (SHA-256 of the PDF bytes)
  - stroke coordinates: PDF points on the uncropped page, since crops are view-only
  - SharedPreferences file and key names, including pen colors and widths, which are stored by name
- Release builds are signed with the build machine's `~/.android/debug.keystore`, and installed
  copies only accept updates signed with the same key. Never delete or regenerate it, or change
  the signing setup.

## Verifying a change

```sh
source env.sh                        # Git Bash (the harness defaults to PowerShell)
./gradlew spotlessApply              # format; the only command that changes files
./gradlew check                      # formatting, lint, unit tests, compiles device tests
./gradlew connectedDebugAndroidTest  # device tests, in Sheets Dev
```

The tree is always formatted, so `spotlessApply` only touches lines you just wrote. Run it
yourself, then re-read the files before editing them again. `check` changes nothing. It fails on
any compiler or lint warning: fix warnings, or suppress one at its narrowest scope with a comment
saying why. With `-q`, Gradle prints nothing on success. Test failures are in
`app/build/test-results/` and `app/build/outputs/androidTest-results/` (XML).

Then check anything visible or touch-related in Sheets Dev on the device, using screenshots, and
screen recordings for timing. Judge performance and latency on a release build. A change is done
when all of the above pass and it's been seen working.

At the end of a session, run `./gradlew installRelease`. Release is minified, so open a sheet in
Sheets to check that it starts and renders. Don't draw there. If the session changed ink or
storage, ask the user to try it.

## Testing on a device

- Test on real sheets in Sheets Dev; its data is disposable. Launch it with
  `adb shell am start -n dev.axu.sheets.debug/dev.axu.sheets.MainActivity`.
- In Git Bash, prefix adb commands that take device paths with `MSYS_NO_PATHCONV=1`.
- Pen strokes: `adb shell "input stylus motionevent DOWN x y; input stylus motionevent MOVE x y; ...; input stylus motionevent UP x y"`.
  Pen input on a page never reaches Compose, so Compose tests can't draw. Production devices
  usually block `sendevent`, so multi-finger gestures need instrumented Compose tests.
- For frame-by-frame checks, record with `screenrecord` and extract frames with
  `ffmpeg -vsync passthrough`.
- Instrumented tests fail while the screen is off. Wake it with `input keyevent KEYCODE_WAKEUP`;
  if it's locked, ask the user to unlock it.
- To test rotation, `settings get` `accelerometer_rotation` and `user_rotation` first, then set
  them to `0` and `1`, then restore both.

## Architecture rules

- Design for pens without buttons or an eraser end, whose hover can't be relied on: the app can't
  tell when the pen is in hand.
- `pdf/` and `annotations/` stay free of Compose.
- `DocumentInk` must stay snapshot-backed. The path from a finished pen stroke to
  `DocumentInk.add` must also stay synchronous on the main thread, because `InkHostLayout` hands
  the stroke over and removes the wet ink in the same frame. Otherwise strokes flicker.
- No DI or navigation libraries. Wiring lives in `AppContainer`, and navigation is `Screen` in
  `MainActivity`.
- Other libraries are fine when they replace meaningfully more code than they cost. Add them to
  the version catalog, and justify them in the commit body.

## Testing

- Test what the app promises, named as the promise (`notesBesideTheMusicAreNeverCroppedAway`), not
  how it's built. Don't test wiring (settings, view models, folder access): such tests only
  restate the code through fakes.
- Pure logic goes in `app/src/test`, which runs on the PC in seconds. Anything that needs Ink
  strokes, `RectF` or other Android classes goes in `app/src/androidTest`, on the device. To keep
  logic on the PC, have it take plain values (`PalmGuard` takes an action and a time, not a
  `MotionEvent`). Android's int constants such as `MotionEvent.ACTION_DOWN` work on the PC.
- Device tests run in Sheets Dev, but still use fakes and in-memory files
  (`AnnotationStoreTest`), never `appContainer`, so they stay deterministic.
- Gesture detectors are tested in isolation with Compose's test rule and its virtual clock
  (`ReaderGesturesTest`). Pen drawing can't be reached from tests; check it on the device.
- Prove a new test guards something: temporarily break the code it covers, see it fail, restore.

## Tunable constants

- Changing a tunable must take an edit to its constant only. Tests derive their boundary cases
  from the constant, while fixtures for real-world shapes such as trills may rightly fail.
- Persisted or cached results that depend on tunables are keyed by them, for example
  `CONTENT_DETECTION_VERSION`.
- Check a new constant by temporarily setting it to other values, running the tests, and
  reverting.

## Gotchas

- Draw strokes only through `drawStrokes()`: `CanvasStrokeRenderer` doesn't apply its matrix to
  the canvas.
- `PageRenderer` cache keys include the region `RectF`, so never mutate a crop after creating it.

## Commits and releases

- Commit each verified change. Messages are semantic and lowercase, subject only unless a body is
  truly needed. Never push, amend or rewrite history without asking.
- For an APK given to someone else, bump `versionCode` and name the file
  `Sheets-<versionName>.apk`.
