/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module webcams.js';

module.exports = function(app, deps) {
  const { cacheGet, cacheSet, stats, snapBbox, log } = deps;

  const WINDY_API_KEY = 'VyQEq6OkmnafZMqEqbVrbH4MXQmS1xfw';

  app.get('/webcams', async (req, res) => {
    const { s, w, n, e, categories } = req.query;
    if (!s || !w || !n || !e) return res.status(400).json({ error: 'Must specify s, w, n, e bounds' });

    const catParam = categories || 'traffic';

    // Snap bbox to 0.01° grid for cache reuse across small scrolls
    const snapped = snapBbox(Number(s), Number(w), Number(n), Number(e), 2);
    const cacheKey = `webcams:${snapped.s},${snapped.w},${snapped.n},${snapped.e}:${catParam}`;
    const ttlMs = 10 * 60 * 1000;  // 10 minutes — matches image URL expiry

    const cached = cacheGet(cacheKey, ttlMs);
    if (cached) {
      stats.hits++;
      log('/webcams', true);
      res.set('Content-Type', 'application/json');
      return res.send(cached.data);
    }

    stats.misses++;
    try {
      const upstreamUrl = `https://api.windy.com/webcams/api/v3/webcams?bbox=${snapped.n},${snapped.e},${snapped.s},${snapped.w}&category=${encodeURIComponent(catParam)}&limit=50&include=images,location,categories,player,urls`;
      const t0 = Date.now();
      const upstream = await fetch(upstreamUrl, {
        headers: { 'x-windy-api-key': WINDY_API_KEY },
      });
      const elapsed = Date.now() - t0;

      if (!upstream.ok) {
        const errBody = await upstream.text();
        console.error(`[Webcams] Upstream HTTP ${upstream.status}: ${errBody.substring(0, 300)}`);
        return res.status(upstream.status).json({ error: 'Webcams upstream error', status: upstream.status });
      }

      const raw = await upstream.json();
      const webcams = (raw.webcams || []).map(wc => ({
        id: wc.webcamId || wc.id,
        title: wc.title || '',
        lat: wc.location?.latitude ?? wc.position?.latitude ?? 0,
        lon: wc.location?.longitude ?? wc.position?.longitude ?? 0,
        categories: (wc.categories || []).map(c => c.id || c),
        previewUrl: wc.images?.current?.preview || wc.images?.daylight?.preview || '',
        thumbnailUrl: wc.images?.current?.thumbnail || wc.images?.daylight?.thumbnail || '',
        playerUrl: wc.player?.lifetime || wc.player?.day || '',
        detailUrl: wc.urls?.detail || '',
        status: wc.status || 'active',
        lastUpdated: wc.lastUpdatedOn || null,
      }));

      const body = JSON.stringify(webcams);
      log('/webcams', false, elapsed);
      cacheSet(cacheKey, body, { 'content-type': 'application/json' });
      res.set('Content-Type', 'application/json').send(body);
    } catch (err) {
      console.error('[Webcams upstream error]', err.message);
      res.status(502).json({ error: 'Upstream request failed', detail: err.message });
    }
  });
};
