# Snoah Plus 🎵

A stylish, modern music player for Android — by Shahzaib Muzaffar.

Dark-first design with violet / pink / cyan gradients, a full-screen player,
and everything a modern music app needs. **Snoah Plus plays audio files
already on your Android device.** It does not stream, download, or upload
music, and it needs no account.

## Features

- **Play local music** from MediaStore: songs, albums, and artists
- **Full player:** play / pause, next / previous, seek bar, queue / Up Next
- **Shuffle** and **repeat** (off / all / one)
- **Playback speed:** 0.5x – 2x
- **Sleep timer:** 15 / 30 / 60 minutes, stops playback automatically
- **Favourites:** tap the heart, saved on the device
- **Playlists:** create, rename, delete, add and remove songs
- **Search** by title, artist, or album
- **Sort** by title, artist, most recent, or longest
- **Recently played** row on Home
- **Albums and Artists** grouping, with play-all
- **Mini-player** above the bottom navigation, always in sync
- **Media session service:** notification, lock-screen and Bluetooth controls
- **Settings:** dark / light theme, accent colour, equalizer preset picker
- **Professional UI:** Material 3, gradient headers and artwork, rounded cards

## Screenshots

Add screenshots here after installing the debug APK on a phone.

## Tech stack

- Kotlin, Jetpack Compose, Material 3, Navigation Compose
- Media3 / ExoPlayer + MediaSessionService
- DataStore / SharedPreferences for favourites, playlists and recently played
- Coil for album artwork
- Gradle 8.7, Android Gradle Plugin 8.5.2, Kotlin 2.0.20, compileSdk 34, minSdk 26

This is a complete Android app (UI + playback + local data) plus an
optional tiny companion API in `server/` for playlist / favourite sync.
The Android build does not need the server.

## Build locally

Open the project in Android Studio (Hedgehog or newer) and run on a
device or emulator with Android 8.0 (API 26) or newer.

Or from the command line with Gradle 8.7 installed:

```sh
gradle assembleDebug
```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Get the APK from GitHub Actions

1. Open this repo on GitHub.
2. Go to **Actions** → **Android CI** → the latest run on `main`.
3. Wait until it is green, then download the artifact
   **SnoahPlus-debug-apk** and install it on your Android phone.
   You may need to allow “Install unknown apps” for your browser / Files app.

## Permissions

- `READ_MEDIA_AUDIO` (Android 13+) / `READ_EXTERNAL_STORAGE` (older):
  read audio files already on the device.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`:
  keep music playing with a media notification.
- `POST_NOTIFICATIONS`: show the now-playing notification.

## Optional server

`server/` is a small optional Express API (in-memory only) with
`GET /health`, `GET/POST /api/playlists`, and `GET/POST /api/favorites`.
It is not required for the app or the APK. See `server/README.md`.

## License

MIT — see `LICENSE`.
