const express = require("express");
const app = express();
app.use(express.json());

let playlists = [];
let favorites = [];

app.get("/health", (req, res) => res.json({ ok: true, app: "Snoah Plus" }));

app.get("/api/playlists", (req, res) => res.json(playlists));
app.post("/api/playlists", (req, res) => {
  const item = req.body || {};
  item.id = item.id || String(Date.now());
  playlists.push(item);
  res.status(201).json(item);
});

app.get("/api/favorites", (req, res) => res.json(favorites));
app.post("/api/favorites", (req, res) => {
  favorites = Array.isArray(req.body) ? req.body : favorites;
  res.json(favorites);
});

const port = process.env.PORT || 3000;
app.listen(port, () => console.log("Snoah Plus server on " + port));
