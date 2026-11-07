# Aura Music

A dedicated Spotify client engineered specifically for Android TV

## What is Aura Music?

Aura Music is a dedicated Spotify client built from the ground up for Android TV. Inspired by the frictionless authentication of Meld, Aura takes a completely different path: it is a Spotify-exclusive client reworked to bring world-class audio personalization directly to the big screen, wrapped in a striking, TV-first Material You interface.

## Why Aura Music?

- **Designed for the Living Room** — Built exclusively around D-pad navigation with a custom full-screen interactive playback view designed for Google TV.
- **Pure Spotify Experience** — No hybrid catalogs or platform mixing in what you browse. Every search result, home screen recommendation, playlist and saved album is natively driven by the Spotify ecosystem.
- **No Setup Friction** — Log in with your Spotify account directly in the app. No developer dashboards, no Client IDs, and no complex configurations required.
- **Modern Aesthetics** — Ditching the standard TV UI for fluid Material You design principles, featuring deep tonal color palettes and dynamic scaling.

## Features

### 📺 Android TV Optimized

- **Native D-Pad Navigation**: Fluid, snag-free remote navigation utilizing custom side navigation rails.
- **Immersive Playback**: An interactive, full-screen playback view that brings album art and metadata to life on large displays.
- **Custom Assets**: Custom adaptive launcher icons generated for every screen density, a TV banner asset, and smooth splash screen animations designed specifically for Google TV.

### 🎨 Material You Design

- **Tonal Palettes**: UI colors dynamically adapt using deep tonal palettes that feel modern and cinematic.
- **Fluid Geometry**: Softened, rounded geometry and variable Material Symbols replace rigid, legacy TV design patterns.

### 🎧 Core Spotify Integration

- **Spotify as the Sole Source**: Search results, home screen tracks, top artists, and new releases are populated entirely from your Spotify profile.
- **Smart Queue Generation**: A custom built-in recommendation engine builds continuous, radio-like queues based on your Spotify taste profile, cross-referencing top tracks, genre similarity, and your listening history.
- **Seamless Library Sync**: Instantly access your saved Spotify playlists, liked songs, and saved albums without leaving the couch.
- **Profile Caching**: A hybrid data strategy caches your profile locally for instant home screen loading on app restart, bypassing aggressive rate limits.

## Download and Installation

1. Go to the [Releases](../../releases) page.
2. Download the latest `AuraMusic.apk` file.
3. Transfer the APK to your Android TV or Google TV device (using a USB drive or an app like Send Files to TV).
4. Sideload the installation using a file manager on your TV. (Note: You may need to enable "Install from unknown sources" in your Android TV settings).

## How the Integration Works Under the Hood

Aura connects to your Spotify account through a built-in WebView login, meaning you don't need a premium account or a developer API key to get started.

- **Authentication**: You log in with your regular Spotify credentials directly inside the app. Aura extracts the session cookies and generates access tokens using TOTP, keeping you logged in without manual token management.
- **Data Layer**: Aura communicates with Spotify primarily through GraphQL endpoints (for playlists, liked songs, artist details, albums, new releases, and search) with REST API fallbacks for top tracks and top artists. This avoids the aggressive rate limits that affect standard REST endpoints.
- **Queue Generation**: When you initiate playback, Aura's recommendation engine builds a queue by fetching top tracks from the song's artists, finding genre-similar artists from your taste profile, mixing in tracks from your personal top pool, and scoring candidates by affinity, recency, and genre overlap.
- **Performance**: Your Spotify profile data (top tracks, top artists with images) is persisted locally in a database and served instantly on app restart. Background network refreshes only occur when the cache is stale, keeping the TV interface highly responsive even on lower-end smart TV hardware.

## License

Aura Music is licensed under GPL-3.0-only. It is a reworking of the upstream **Metrolist** project, and the per-file provenance headers carried through this repository record that origin. They are part of the attribution for that work and are not to be stripped.
