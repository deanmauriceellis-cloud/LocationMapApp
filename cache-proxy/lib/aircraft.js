/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module aircraft.js';

module.exports = function(app, deps) {
  const { pgPool, cache, cacheGet, cacheSet, stats, snapBbox, log,
          getOpenskyToken, openskyRateState, openskyCanRequest,
          openskyRecordRequest, openskyRecord429, openskyRecordSuccess } = deps;

  // ── Aircraft sighting tracker (real-time DB writes) ──────────────────────

  const activeSightings = new Map();  // icao24 → { id, lastSeen }
  const SIGHTING_GAP_MS = 5 * 60 * 1000;  // 5 min gap = new sighting

  async function trackAircraftSightings(body) {
    if (!pgPool) return;
    try {
      const data = JSON.parse(body);
      const states = data.states || [];
      if (states.length === 0) return;

      const now = new Date();
      let inserted = 0, updated = 0;

      for (const s of states) {
        const icao24 = s[0];
        const callsign = s[1]?.trim() || null;
        const origin = s[2] || null;
        const lon = s[5], lat = s[6];
        const baro = s[7], onGround = s[8];
        const velocity = s[9], heading = s[10];
        const vertRate = s[11], squawk = s[14];

        if (!icao24 || lat == null || lon == null) continue;

        const active = activeSightings.get(icao24);
        if (active && (now - active.lastSeen) < SIGHTING_GAP_MS) {
          // Update existing sighting
          await pgPool.query(
            `UPDATE aircraft_sightings SET
              last_seen=$1, last_lat=$2, last_lon=$3, last_altitude=$4,
              last_heading=$5, last_velocity=$6, last_vertical_rate=$7,
              squawk=$8, on_ground=$9, callsign=COALESCE($10, callsign)
            WHERE id=$11`,
            [now, lat, lon, baro, heading, velocity, vertRate, squawk, !!onGround, callsign, active.id]
          );
          active.lastSeen = now;
          updated++;
        } else {
          // New sighting
          const res = await pgPool.query(
            `INSERT INTO aircraft_sightings
              (icao24, callsign, origin_country, first_seen, last_seen,
               first_lat, first_lon, first_altitude, first_heading,
               last_lat, last_lon, last_altitude, last_heading,
               last_velocity, last_vertical_rate, squawk, on_ground)
            VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17)
            RETURNING id`,
            [icao24, callsign, origin, now, now,
             lat, lon, baro, heading,
             lat, lon, baro, heading,
             velocity, vertRate, squawk, !!onGround]
          );
          activeSightings.set(icao24, { id: res.rows[0].id, lastSeen: now });
          inserted++;
        }
      }

      if (inserted > 0 || updated > 0) {
        console.log(`[Aircraft DB] +${inserted} new sightings, ${updated} updated (${activeSightings.size} active)`);
      }
    } catch (err) {
      console.error('[Aircraft DB]', err.message);
    }
  }

  // Purge stale entries from active map every 10 min
  setInterval(() => {
    const cutoff = Date.now() - SIGHTING_GAP_MS;
    let purged = 0;
    for (const [icao, entry] of activeSightings) {
      if (entry.lastSeen < cutoff) {
        activeSightings.delete(icao);
        purged++;
      }
    }
    if (purged > 0) console.log(`[Aircraft DB] Purged ${purged} stale sightings (${activeSightings.size} active)`);
  }, 10 * 60 * 1000);

  // ── OpenSky Aircraft (15s TTL, bbox passthrough) ─────────────────────────

  app.get('/aircraft', async (req, res) => {
    const { bbox, icao24 } = req.query;
    if (!bbox && !icao24) return res.status(400).json({ error: 'Must specify bbox (south,west,north,east) or icao24' });

    // Snap bbox to 0.1° grid for cache reuse (aircraft have 15s TTL, coarse snap is fine)
    let snappedBbox = bbox;
    if (bbox && !icao24) {
      const parts = bbox.split(',').map(Number);
      const snapped = snapBbox(parts[0], parts[1], parts[2], parts[3], 1);
      snappedBbox = `${snapped.s},${snapped.w},${snapped.n},${snapped.e}`;
    }

    const cacheKey = icao24 ? `aircraft:icao:${icao24}` : `aircraft:${snappedBbox}`;
    const ttlMs = 15 * 1000;
    const cached = cacheGet(cacheKey, ttlMs);
    if (cached) {
      stats.hits++;
      log('/aircraft', true);
      trackAircraftSightings(cached.data);  // track on cache hits too
      res.set('Content-Type', cached.headers['content-type'] || 'application/json');
      return res.send(cached.data);
    }

    // Rate limit check — serve stale cache or 429 if we can't hit upstream
    const rateCheck = openskyCanRequest();
    if (!rateCheck.allowed) {
      // Try to serve stale cache (ignore TTL)
      const stale = cache.get(cacheKey);
      if (stale) {
        stats.hits++;
        log('/aircraft', true, 0, `stale (${rateCheck.reason})`);
        trackAircraftSightings(stale.data);
        res.set('Content-Type', stale.headers['content-type'] || 'application/json');
        res.set('X-Cache', 'STALE');
        res.set('X-Rate-Limit-Reason', rateCheck.reason);
        return res.send(stale.data);
      }
      log('/aircraft', false, 0, `throttled (${rateCheck.reason})`);
      return res.status(429)
        .set('Retry-After', String(rateCheck.retryAfter || 30))
        .json({ error: 'Rate limited', reason: rateCheck.reason, retryAfter: rateCheck.retryAfter });
    }

    stats.misses++;
    try {
      let upstreamUrl;
      if (icao24) {
        upstreamUrl = `https://opensky-network.org/api/states/all?icao24=${icao24.toLowerCase()}`;
      } else {
        const parts = snappedBbox.split(',');
        upstreamUrl = `https://opensky-network.org/api/states/all?lamin=${parts[0]}&lomin=${parts[1]}&lamax=${parts[2]}&lomax=${parts[3]}`;
      }
      const token = await getOpenskyToken();
      const fetchOpts = token ? { headers: { Authorization: `Bearer ${token}` } } : {};
      const t0 = Date.now();
      openskyRecordRequest();  // record BEFORE the request
      const upstream = await fetch(upstreamUrl, fetchOpts);
      const elapsed = Date.now() - t0;
      const body = await upstream.text();
      const contentType = upstream.headers.get('content-type') || 'application/json';

      if (upstream.status === 429) {
        openskyRecord429();
        // Try stale cache before returning 429
        const stale = cache.get(cacheKey);
        if (stale) {
          log('/aircraft', true, elapsed, 'stale (upstream 429)');
          trackAircraftSightings(stale.data);
          res.set('Content-Type', stale.headers['content-type'] || 'application/json');
          res.set('X-Cache', 'STALE');
          return res.send(stale.data);
        }
        log('/aircraft', false, elapsed, 'upstream 429');
        return res.status(429)
          .set('Retry-After', String(openskyRateState.backoffSeconds))
          .json({ error: 'Rate limited by OpenSky', retryAfter: openskyRateState.backoffSeconds });
      }

      openskyRecordSuccess();
      log('/aircraft', false, elapsed);

      if (upstream.ok) {
        cacheSet(cacheKey, body, { 'content-type': contentType });
        trackAircraftSightings(body);  // fire-and-forget DB write
      }

      res.status(upstream.status).set('Content-Type', contentType).send(body);
    } catch (err) {
      console.error('[Aircraft upstream error]', err.message);
      res.status(502).json({ error: 'Upstream request failed', detail: err.message });
    }
  });
};
