# Aura Music

A dedicated Spotify client engineered for Android TV and Google TV, built with Kotlin and
Jetpack Compose. Sign in with your own Spotify account and the app drives its home shelf,
search, radio-style queues, and library from your taste profile — on the big screen, with a
D-pad.

**Current release: 1.0** (versionCode 1). `minSdk 26`, `targetSdk 36`.

> Aura Music is an independent project. It is not affiliated with, endorsed by, or sponsored by
> Spotify. The Spotify name, marks and content are the property of Spotify AB, and every request
> the app makes is made with *your* account and under *your* responsibility. Playback features
> depend on Spotify's own service terms and on your subscription tier.

## What it does

- **Spotify at the core.** Search, home recommendations, new releases, streaming audio and
  library syncing come from the native Spotify ecosystem, with a licensed fallback for the few
  tracks Spotify cannot serve.
- **Android TV optimized.** Built completely around D-pad navigation, with vector launcher and
  interface icons, an animated splash screen, and an immersive full-screen playback view.
- **Material You aesthetics.** Deep tonal palettes, rounded geometry and Material-style vector
  icons.
- **Zero setup friction.** Log in with your Spotify account inside the app — no developer
  dashboard, no client IDs, no configuration.
- **Smart queue generation.** Continuous radio-like queues built from your taste profile,
  cross-referencing top tracks, genre similarity and listening history.
- **Seamless library sync.** Saved playlists, liked songs and saved albums without leaving
  the couch.

## Getting a build onto a TV

### Requirements

| | |
|---|---|
| JDK | 21 |
| Android SDK | platform 36, build-tools matching `app/build.gradle.kts` |
| Device | Android TV / Google TV on API 26 or newer |

Clone, then point Gradle at your SDK:

```bash
git clone <this repo> aura-music && cd aura-music
cp local.properties.sample local.properties     # edit sdk.dir
```

### Build

```bash
./gradlew :app:assembleFossRelease      # unsigned-check build; see Signing below
./gradlew :app:assembleFossDebug        # debug package id, no signing config needed
```

Output lands in `app/build/outputs/apk/<flavour>/<type>/`.

Three product flavours share the same sources:

| Flavour | Package | Notes |
|---|---|---|
| `foss` | `com.aura.music` | No proprietary Google dependencies. This is what ships. |
| `gms` | — | Google Play Services integrations. |
| `izzy` | — | IzzyOnDroid-style distribution variant. |

Debug builds install side by side as `com.aura.music.debug`.

### Signing a release

`assembleFossRelease` reads `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`
and `RELEASE_KEY_PASSWORD` from `local.properties` (or the equivalent environment variables) and
falls back to `app/keystore/release.keystore`. That directory is Git-ignored on purpose: the
private key and the ability to upgrade an installed v1.0 are the same thing, and once a key is in
Git history it cannot be taken out without rewriting the repository. Generate your own key for
any fork.

## Repository layout

```
app/src/main/kotlin/com/aura/music/
  ui/aura/          the design-system primitives (hero card, shelf, rail row, pill, tokens)
  ui/screens/       one package per destination: home, search, library, artist, settings…
  spotify/          Spotify auth, web API and GraphQL clients
  playback/         Media3 session, queue and source selection
  db/  data/        Room entities and DataStore preferences
innertube/          included-in-build client for the fallback audio source
```

The UI layer is deliberately composed from `ui/aura` primitives rather than Material's stock TV
templates, so a change to the visual language lands in one file.

## Development notes

`AGENTS.md` at the repo root documents the working conventions for this codebase — where
user-visible strings live, how the flavour matrix interacts with build commands, and what has
already been measured on real hardware. Read it before changing anything.

## License

Aura Music is distributed under the **GNU General Public License v3.0 only**
([`LICENSE`](LICENSE)).

Aura Music is a reworking of the upstream **Metrolist** project. Most source files retain a
per-file provenance header naming the upstream project and its GPL-3.0 licence; those headers are
part of the licence chain and are not to be stripped. The visible product, its identity and its
interface are Aura Music's own.

Third-party trademarks, service names and catalog content belong to their respective owners and
are used only to describe the services this app talks to.
