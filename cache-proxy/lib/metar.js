/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module metar.js';

module.exports = function(app, deps) {
  const { cacheGet, cacheSet, stats, snapBbox, log } = deps;

  app.get('/metar', async (req, res) => {
    const { bbox } = req.query;
    if (!bbox) return res.status(400).json({ error: 'Must specify bbox (lat0,lon0,lat1,lon1)' });

    // Snap bbox to 0.01° grid for cache reuse across small scrolls
    const parts = bbox.split(',').map(Number);
    const snapped = snapBbox(parts[0], parts[1], parts[2], parts[3], 2);
    const snappedBbox = `${snapped.s},${snapped.w},${snapped.n},${snapped.e}`;

    const cacheKey = `metar:${snappedBbox}`;
    const ttlMs = 60 * 60 * 1000;
    const cached = cacheGet(cacheKey, ttlMs);
    if (cached) {
      stats.hits++;
      log('/metar', true);
      res.set('Content-Type', cached.headers['content-type'] || 'application/json');
      return res.send(cached.data);
    }

    stats.misses++;
    try {
      const upstreamUrl = `https://aviationweather.gov/api/data/metar?format=json&hours=1&taf=false&bbox=${encodeURIComponent(snappedBbox)}`;
      const t0 = Date.now();
      const upstream = await fetch(upstreamUrl);
      const elapsed = Date.now() - t0;
      const body = await upstream.text();
      const contentType = upstream.headers.get('content-type') || 'application/json';

      log('/metar', false, elapsed);

      if (upstream.ok) {
        cacheSet(cacheKey, body, { 'content-type': contentType });
      }

      res.status(upstream.status).set('Content-Type', contentType).send(body);
    } catch (err) {
      console.error('[METAR upstream error]', err.message);
      res.status(502).json({ error: 'Upstream request failed', detail: err.message });
    }
  });
};
