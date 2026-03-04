/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module db-aircraft.js';

function toSighting(row) {
  return {
    id: row.id, icao24: row.icao24, callsign: row.callsign,
    originCountry: row.origin_country,
    firstSeen: row.first_seen, lastSeen: row.last_seen,
    firstLat: row.first_lat, firstLon: row.first_lon,
    firstAltitude: row.first_altitude, firstHeading: row.first_heading,
    lastLat: row.last_lat, lastLon: row.last_lon,
    lastAltitude: row.last_altitude, lastHeading: row.last_heading,
    lastVelocity: row.last_velocity, lastVerticalRate: row.last_vertical_rate,
    squawk: row.squawk, onGround: row.on_ground,
    durationSec: row.duration_sec != null ? parseInt(row.duration_sec) : undefined,
    distanceKm: row.distance_km != null ? parseFloat(row.distance_km) : undefined,
  };
}

module.exports = function(app, deps) {
  const { pgPool, requirePg } = deps;

  // GET /db/aircraft/search — filtered search (callsign, icao24, country, bbox, time)
  app.get('/db/aircraft/search', requirePg, async (req, res) => {
    try {
      const { q, icao24, callsign, country, s, w, n, e, since, until, on_ground, limit, offset } = req.query;
      const conditions = [];
      const params = [];
      let pi = 1;

      if (q) {
        conditions.push(`(callsign ILIKE $${pi} OR icao24 ILIKE $${pi})`);
        params.push(`%${q}%`);
        pi++;
      }
      if (icao24) {
        conditions.push(`icao24 = $${pi}`);
        params.push(icao24.toLowerCase());
        pi++;
      }
      if (callsign) {
        conditions.push(`callsign ILIKE $${pi}`);
        params.push(`%${callsign}%`);
        pi++;
      }
      if (country) {
        conditions.push(`origin_country ILIKE $${pi}`);
        params.push(`%${country}%`);
        pi++;
      }
      if (s && w && n && e) {
        conditions.push(`last_lat BETWEEN $${pi} AND $${pi + 1}`);
        params.push(parseFloat(s), parseFloat(n));
        pi += 2;
        conditions.push(`last_lon BETWEEN $${pi} AND $${pi + 1}`);
        params.push(parseFloat(w), parseFloat(e));
        pi += 2;
      }
      if (since) {
        conditions.push(`last_seen >= $${pi}`);
        params.push(since);
        pi++;
      }
      if (until) {
        conditions.push(`first_seen <= $${pi}`);
        params.push(until);
        pi++;
      }
      if (on_ground != null) {
        conditions.push(`on_ground = $${pi}`);
        params.push(on_ground === 'true');
        pi++;
      }

      const where = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';
      const lim = Math.min(parseInt(limit) || 100, 500);
      const off = parseInt(offset) || 0;

      const sql = `SELECT *, EXTRACT(EPOCH FROM last_seen - first_seen) AS duration_sec
        FROM aircraft_sightings ${where}
        ORDER BY last_seen DESC LIMIT ${lim} OFFSET ${off}`;
      const result = await pgPool.query(sql, params);
      res.json({ count: result.rows.length, sightings: result.rows.map(toSighting) });
    } catch (err) {
      console.error('[/db/aircraft/search]', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // GET /db/aircraft/recent — most recently seen aircraft (deduplicated by icao24)
  app.get('/db/aircraft/recent', requirePg, async (req, res) => {
    try {
      const lim = Math.min(parseInt(req.query.limit) || 50, 200);
      const sql = `SELECT DISTINCT ON (icao24) *, EXTRACT(EPOCH FROM last_seen - first_seen) AS duration_sec
        FROM aircraft_sightings ORDER BY icao24, last_seen DESC`;
      const result = await pgPool.query(sql);
      // Sort by last_seen descending after dedup, then limit
      const sorted = result.rows
        .sort((a, b) => new Date(b.last_seen) - new Date(a.last_seen))
        .slice(0, lim);
      res.json({ count: sorted.length, totalAircraft: result.rows.length, sightings: sorted.map(toSighting) });
    } catch (err) {
      console.error('[/db/aircraft/recent]', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // GET /db/aircraft/stats — aircraft analytics overview
  // NOTE: must be defined before /:icao24 to avoid matching "stats" as a param
  app.get('/db/aircraft/stats', requirePg, async (req, res) => {
    try {
      const [total, unique, countries, topCallsigns, timeRange, altitudes] = await Promise.all([
        pgPool.query('SELECT COUNT(*) AS n FROM aircraft_sightings'),
        pgPool.query('SELECT COUNT(DISTINCT icao24) AS n FROM aircraft_sightings'),
        pgPool.query('SELECT origin_country, COUNT(DISTINCT icao24) AS n FROM aircraft_sightings WHERE origin_country IS NOT NULL GROUP BY origin_country ORDER BY n DESC LIMIT 20'),
        pgPool.query(`SELECT callsign, COUNT(*) AS n FROM aircraft_sightings WHERE callsign IS NOT NULL AND callsign != '' GROUP BY callsign ORDER BY n DESC LIMIT 20`),
        pgPool.query('SELECT MIN(first_seen) AS earliest, MAX(last_seen) AS latest FROM aircraft_sightings'),
        pgPool.query(`SELECT
          COUNT(*) FILTER (WHERE last_altitude IS NULL OR on_ground = true) AS ground,
          COUNT(*) FILTER (WHERE last_altitude < 1524 AND on_ground = false) AS below_5k,
          COUNT(*) FILTER (WHERE last_altitude BETWEEN 1524 AND 6096) AS ft5k_20k,
          COUNT(*) FILTER (WHERE last_altitude > 6096) AS above_20k
          FROM aircraft_sightings`),
      ]);
      res.json({
        totalSightings: parseInt(total.rows[0].n),
        uniqueAircraft: parseInt(unique.rows[0].n),
        topCountries: countries.rows.map(r => ({ country: r.origin_country, aircraft: parseInt(r.n) })),
        topCallsigns: topCallsigns.rows.map(r => ({ callsign: r.callsign, sightings: parseInt(r.n) })),
        timeRange: timeRange.rows[0],
        altitudeDistribution: {
          ground: parseInt(altitudes.rows[0].ground),
          below5kFt: parseInt(altitudes.rows[0].below_5k),
          ft5k_20k: parseInt(altitudes.rows[0].ft5k_20k),
          above20kFt: parseInt(altitudes.rows[0].above_20k),
        },
      });
    } catch (err) {
      console.error('[/db/aircraft/stats]', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // GET /db/aircraft/:icao24 — all sightings for one aircraft
  app.get('/db/aircraft/:icao24', requirePg, async (req, res) => {
    try {
      const icao = req.params.icao24.toLowerCase();
      const result = await pgPool.query(
        `SELECT *, EXTRACT(EPOCH FROM last_seen - first_seen) AS duration_sec
         FROM aircraft_sightings WHERE icao24 = $1 ORDER BY first_seen DESC`,
        [icao]
      );
      if (result.rows.length === 0) return res.status(404).json({ error: 'No sightings found', icao24: icao });

      // Build flight path from all sightings (first→last positions)
      const path = result.rows.map(r => ({
        firstLat: r.first_lat, firstLon: r.first_lon,
        lastLat: r.last_lat, lastLon: r.last_lon,
        firstSeen: r.first_seen, lastSeen: r.last_seen,
        altitude: r.last_altitude, heading: r.last_heading,
      })).reverse();

      res.json({
        icao24: icao,
        callsigns: [...new Set(result.rows.map(r => r.callsign).filter(Boolean))],
        originCountry: result.rows[0].origin_country,
        totalSightings: result.rows.length,
        firstSeen: result.rows[result.rows.length - 1].first_seen,
        lastSeen: result.rows[0].last_seen,
        sightings: result.rows.map(toSighting),
        path,
      });
    } catch (err) {
      console.error('[/db/aircraft/:icao24]', err.message);
      res.status(500).json({ error: err.message });
    }
  });
};
