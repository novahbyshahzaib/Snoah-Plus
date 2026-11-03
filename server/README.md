# Snoah Plus server (optional)

A tiny Express API for syncing playlists and favourites. It stores
everything in memory only — restart the server and the data is gone.

It is **not needed** for the Android app or for the GitHub Actions APK build.

```sh
npm install
npm start
```

Endpoints: `GET /health`, `GET/POST /api/playlists`, `GET/POST /api/favorites`.
