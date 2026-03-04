/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module proxy-get.js';

module.exports = function(app, deps) {
  const { cacheGet, cacheSet, cache, stats, log } = deps;

  function proxyGet(route, upstreamUrl, cacheKey, ttlMs, extraHeaders = {}) {
    app.get(route, async (req, res) => {
      const cached = cacheGet(cacheKey, ttlMs);
      if (cached) {
        stats.hits++;
        log(route, true);
        res.set('Content-Type', cached.headers['content-type'] || 'application/json');
        return res.send(cached.data);
      }

      stats.misses++;
      try {
        const t0 = Date.now();
        const upstream = await fetch(upstreamUrl, { headers: extraHeaders });
        const elapsed = Date.now() - t0;
        const body = await upstream.text();
        const contentType = upstream.headers.get('content-type') || 'application/json';

        log(route, false, elapsed);

        if (upstream.ok) {
          cacheSet(cacheKey, body, { 'content-type': contentType });
        }

        res.status(upstream.status).set('Content-Type', contentType).send(body);
      } catch (err) {
        console.error(`[${route} upstream error]`, err.message);
        res.status(502).json({ error: 'Upstream request failed', detail: err.message });
      }
    });
  }

  // ── USGS Earthquakes (2h TTL) ───────────────────────────────────────────────

  proxyGet(
    '/earthquakes',
    'https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_week.geojson',
    'earthquakes',
    2 * 60 * 60 * 1000
  );

  // ── NWS Weather Alerts (1h TTL) ─────────────────────────────────────────────

  proxyGet(
    '/nws-alerts',
    'https://api.weather.gov/alerts/active?status=actual&message_type=alert',
    'nws-alerts',
    60 * 60 * 1000,
    { 'User-Agent': 'LocationMapApp/1.1 contact@example.com' }
  );
};
