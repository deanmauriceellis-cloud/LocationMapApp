/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module pois.js';

const fs = require('fs');

module.exports = function(app, deps) {
  const { radiusHints, poiCache, getRadiusHint, adjustRadiusHint, POI_CACHE_FILE } = deps;

  // ── Radius hint endpoints ───────────────────────────────────────────────

  app.get('/radius-hint', (req, res) => {
    const { lat, lon } = req.query;
    if (!lat || !lon) return res.status(400).json({ error: 'lat and lon required' });
    const radius = getRadiusHint(lat, lon);
    res.json({ radius });
  });

  app.post('/radius-hint', (req, res) => {
    const { lat, lon, resultCount, error, capped } = req.body;
    if (!lat || !lon) return res.status(400).json({ error: 'lat and lon required' });
    const isError = !!error;
    const isCapped = !!capped;
    const count = typeof resultCount === 'number' ? resultCount : 0;
    const radius = adjustRadiusHint(lat, lon, count, isError, isCapped);
    res.json({ radius });
  });

  app.get('/radius-hints', (req, res) => {
    const hints = {};
    for (const [key, value] of radiusHints) {
      hints[key] = value;
    }
    res.json({ count: radiusHints.size, hints });
  });

  // ── POI cache endpoints ──────────────────────────────────────────────────────

  app.get('/pois/stats', (req, res) => {
    let diskSize = 0;
    try {
      if (fs.existsSync(POI_CACHE_FILE)) {
        diskSize = fs.statSync(POI_CACHE_FILE).size;
      }
    } catch (_) {}
    res.json({
      count: poiCache.size,
      diskSizeMB: (diskSize / 1024 / 1024).toFixed(2),
    });
  });

  app.get('/pois/export', (req, res) => {
    const pois = [];
    for (const [key, value] of poiCache) {
      pois.push({ key, ...value });
    }
    res.json({ count: pois.length, pois });
  });

  app.get('/pois/bbox', (req, res) => {
    const { s, w, n, e } = req.query;
    if (!s || !w || !n || !e) return res.status(400).json({ error: 'Must specify s, w, n, e bounds' });
    const south = parseFloat(s), west = parseFloat(w), north = parseFloat(n), east = parseFloat(e);
    const elements = [];
    for (const [, value] of poiCache) {
      const el = value.element;
      const lat = el.lat ?? el.center?.lat;
      const lon = el.lon ?? el.center?.lon;
      if (lat == null || lon == null) continue;
      if (lat >= south && lat <= north && lon >= west && lon <= east) {
        elements.push(el);
      }
    }
    res.json({ count: elements.length, elements });
  });

  app.get('/poi/:type/:id', (req, res) => {
    const key = `poi:${req.params.type}:${req.params.id}`;
    const entry = poiCache.get(key);
    if (!entry) return res.status(404).json({ error: 'POI not found', key });
    res.json(entry);
  });
};
