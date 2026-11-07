# Working with Aura Music

Aura Music is a Spotify client for Android TV and Google TV, built with Kotlin and Jetpack
Compose. It is a reworking of the upstream **Metrolist** project: the engine, the service clients
and the data layer largely come from there, and the entire presentation layer, the product
identity and the packaging are Aura's own.

- Package: `com.aura.music` (debug: `com.aura.music.debug`)
- Sources: `app/src/main/kotlin/com/aura/`
- Shipped flavour: `foss`. `gms` and `izzy` exist but are not what is distributed.
- Version is **pinned at `1.0` / versionCode 1**. Do not bump it.

## Rules for working on the project

1. **Do not commit, push, merge, rebase, tag or delete a branch unless a human explicitly asks
   you to, in this turn.** Authorization is per-action, not standing. Never force-push or rewrite
   history without an instruction to do so. Never modify Git config.
2. **Read-only device queries are fine; device mutations are not.** Installing, launching,
   sending key events, taps, `pm clear`, `cmd package compile` and deleting anything on the TV
   each need an explicit go-ahead. Ask before the mutation, not after.
3. **Do not bump the version**, change the `applicationId`, or edit the signing configuration.
   The release keystore and the ability to upgrade an installed v1.0 are the same object; it is
   Git-ignored and must stay that way.
4. **Do not edit the app's database schema** unless it is genuinely unavoidable; a schema change
   costs a migration and risks the installed user's library state.
5. **The scope is the app.** Do not chase, restart or "fix" launcher, system UI or Android TV
   shell behaviour. If a symptom turns out to live outside the app, report it and stop.
6. **No silent regressions.** Before changing something that already works and has been accepted
   on screen, state its cost in the reply — what gets slower, what moves, what disappears. If a
   change has a visual side effect, it is a design decision and belongs to the maintainer.
7. **Back up before bulk rewrites.** Copy the files you are about to replace into
   `notes/removed/<topic>-<date>/` first, and retire superseded files there rather than deleting
   them.
8. **Comments only where the *why* is non-obvious** — a hidden constraint, an invariant, a
   workaround for a specific device bug. Do not restate what the code does.
9. **Test on the device before reporting work as done**, or say plainly that you did not.
   Type-checking proves compilation, not that the screen is right.
10. **Follow Kotlin and Android best practices**, and weigh performance and maintainability in
   every change. This app runs on hardware where an avoided rebuild is worth more than a prettier
   abstraction.
11. If you have any doubt, ask a human. Never assume a requirement or an implementation detail.

## Where user-visible text lives

`app/src/main/res/values/strings.xml` is **Aura's file**. All new and changed Aura copy goes
here.

The per-locale `metrolist_strings.xml` files belong to upstream and hold upstream keys and
translations. Do not put Aura copy in them, and do not edit the other language files. Keys that
exist only in `values/strings.xml` resolve for every locale through the standard resource
fallback, which is what you want when the copy is English-only by design.

The About page (`ui/screens/settings/AboutScreen.kt`) is the reference example: each heading is
its own string resource and the paragraph under it is another, so copy can move without touching
layout. Bold lead-ins inside a bullet are one `Text` with `buildAnnotatedString` plus
`SpanStyle(fontWeight = Bold)` — two separate `Text` views would force the lead onto its own line.

## Spotify GQL hash management

The app uses Spotify's internal GraphQL API (via `api-partner.spotify.com`). Each GQL operation
requires a SHA-256 hash that Spotify rotates periodically. The hashes are defined in
`app/src/main/kotlin/com/aura/spotify/Spotify.kt`.

### Remote hash registry

A GitHub Actions workflow (`.github/workflows/spotify-hash-check.yml`) runs daily at 06:00 UTC. It:
1. Fetches the current Spotify web player JS bundle
2. Extracts all operation→hash mappings
3. Compares them with `docs/spotify-gql-hashes.json`
4. If any hash has rotated: updates the JSON (moving the old hash to `previous_hash`), commits, and deploys to GitHub Pages

The live JSON URL baked into the app points at **upstream's** Pages deployment, which is a
third-party address. Anyone shipping their own copy of this workflow must repoint that URL, and
the `CRASH_REPORT_REPO` default in `app/build.gradle.kts`, at a repository they own.

### JSON structure

Each entry in `operations` has:
- `hash` — the current valid SHA-256 hash
- `previous_hash` — the last known working hash (fallback if `hash` fails with 412)
- `status` — `verified` (found in bundle) or `not_in_bundle` (may break without warning)
- `last_verified` / `last_changed` — timestamps

### Tracked operations

The JSON tracks **only the operations currently used by the app** in `Spotify.kt`:
`profileAttributes`, `libraryV3`, `fetchPlaylist`, `fetchLibraryTracks`, `searchDesktop`,
`queryArtistOverview`, `getAlbum`, `queryWhatsNewFeed`, `addToPlaylist`, `removeFromPlaylist`,
`moveItemsInPlaylist`, `editPlaylistAttributes`, `addToLibrary`, `removeFromLibrary`.

### When adding new GQL operations

If you add a new GQL operation to `Spotify.kt`, you **must** also add a corresponding entry in `docs/spotify-gql-hashes.json` so the daily checker tracks it. The entry format is:
```json
"operationName": {
  "hash": "<the 64-char sha256 hash you're using>",
  "previous_hash": null,
  "type": "query or mutation",
  "status": "verified",
  "last_verified": "<current ISO timestamp>",
  "last_changed": null
}
```

### Reference documents

- `.github/scripts/check_spotify_hashes.py` — the hash checker script
- `docs/spotify-gql-hashes.json` — the registry itself

## Building

JDK 21, `foss` flavour:

```bash
JAVA_HOME=<jdk-21> ./gradlew :app:assembleFossRelease --console=plain --no-build-cache \
  > /tmp/build.log 2>&1; echo "GRADLE_EXIT=$?"
```

Do not pipe Gradle through `tail` — it hides the failure you need. A full release build with R8
and resource shrinking runs roughly 25 minutes; read the log afterwards.

A build that reports `BUILD SUCCESSFUL` but produces no newer
`app/build/outputs/apk/foss/release/app-foss-release.apk` did not produce anything. Check the APK
timestamp before installing; never install a stale artifact.

## Testing on a real TV

The reference device is a Google TV on API 30, `armeabi-v7a`, 2 GB RAM, reachable over
`adb connect <ip>:5555`.

The install routine, as one approved sequence:

```bash
adb install -r app/build/outputs/apk/foss/release/app-foss-release.apk
adb shell input keyevent 224                                   # WAKEUP, never 26
adb shell am force-stop com.aura.music
adb logcat -c
adb shell cmd package compile -m speed -f com.aura.music        # ~1 min, the AOT verify pass
adb shell am start -W -n com.aura.music/com.aura.music.MainActivityAlias
```

- **Never send `keyevent 26`.** On this device it powers the TV off and resets Network debugging
  ADB, which only a person at the TV can undo. `224` is WAKEUP.
- Launch `MainActivityAlias`, not `MainActivity`.
- Prefer `install -r`. An uninstall destroys the stored Spotify and Qobuz sessions.
- `INSTALL_FAILED_INSUFFICIENT_STORAGE` with a few hundred MB nominally free is expected: Android
  reserves ~5% of `/data`, and a release APK needs the staged copy plus the old APK plus the
  dexopt output. Clear your own capture files, never the user's `Download/`.
- The first two cold starts after a dexopt pass report inflated times; take a third sample before
  quoting a number or calling it a regression.

Verifying what is actually on screen:

```bash
adb shell uiautomator dump /sdcard/win.xml && adb shell cat /sdcard/win.xml   # bounds + focused
adb shell screencap -p /sdcard/shot.png
```

`adb pull` needs a Windows-style local path (`C:/Users/...`); a `/c/...` path fails. Use the
`focused` attribute and the node bounds to prove D-pad state — a screenshot alone cannot show
where the selector is.

## Performance rules that were measured, not guessed

This device interprets by default rather than AOT-compiling, so costs invisible on a phone are
large here.

- **Do not rebuild modifier lambdas per recomposition.** Inline focus/draw lambdas re-apply every
  tile's whole modifier chain on each recomposition; this was measured stealing frames during the
  splash reveal. Hoist into `remember`, and key remembered values on inputs that actually change —
  a cache invalidated on every call is worse than no cache.
- **DataStore collectors belong off `Dispatchers.Main`.** Each `dataStore.data` collector
  re-crosses to Main on every write. Reads of cached preferences go on IO.
- **Avoid `RenderEffect` / blur.** On API 30 TV hardware they are inert, and stroked "glow"
  approximations show as discrete bands. Use a Gaussian alpha ramp instead.
- **Window insets on TV are not what they look like.** The shell already clears the space under a
  top now-playing banner; applying `top` insets to a list under it opens a permanent void. Pinned
  headers work as `Column { TopAppBar (first child, pinned); content.weight(1f) }` with only
  horizontal and bottom insets on the scrolling child.
- **Anything expensive at cold start gets deferred.** Offload bitmap decoding, heavy composition
  and accessibility-tree work from the launch path; the a11y geometry walk alone starves the first
  frames.

## License and provenance

GPL-3.0 only; see `LICENSE`.

Most files carry an upstream `Metrolist Project (C) 2026` provenance header. Those headers are
part of the licence chain — do not strip them, and do not replace them with an Aura-only header on
files you did not write from scratch. New files written for Aura's design system use an Aura
header.

Do not echo instructions, transcripts or task numbers into user-visible strings. When the
maintainer supplies copy, that text is the spec.

