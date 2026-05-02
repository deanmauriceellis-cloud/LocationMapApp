/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Shared TigerLine geocoding helpers (S218).
 *
 * Extracted from admin-lint.js so admin-pois.js (per-POI Validate via
 * TigerLine) and admin-lint.js (cluster + deep-scan flows) share the same
 * pool, address-normalization rules, and variant ladder.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module tiger-geocode.js';

const { Pool } = require('pg');

// ─── Tiger pool (peer auth, mirrors lib/salem-router.js) ────────────────────

let _tigerPool = null;
function tigerPool() {
  if (_tigerPool) return _tigerPool;
  const opts = process.env.TIGER_DATABASE_URL
    ? { connectionString: process.env.TIGER_DATABASE_URL, max: 2 }
    : { host: process.env.PGHOST || '/var/run/postgresql', database: 'tiger', max: 2 };
  _tigerPool = new Pool({ ...opts, connectionTimeoutMillis: 5000 });
  _tigerPool.on('error', (err) => console.error('[tiger-geocode] pool error:', err.message));
  return _tigerPool;
}

// ─── Address normalization for tiger.geocode ────────────────────────────────
// Tiger's geocoder is picky: "10 Main St, Salem, MA 01970, USA" returns ZERO
// candidates because of the trailing ", USA". Same address without "USA"
// returns a perfect rating-0 match. Normalize before handing it off.
function normalizeAddressForTiger(raw) {
  if (!raw) return raw;
  let s = String(raw).trim();
  s = s.replace(/,?\s*(USA|United States(?: of America)?|U\.S\.A\.?|U\.S\.)\s*$/i, '');
  s = s.replace(/\s*,\s*$/g, '').replace(/\s+/g, ' ').trim();
  return s;
}

// ─── Haversine (km) ──────────────────────────────────────────────────────────

function haversineKm(lat1, lng1, lat2, lng2) {
  const toRad = (d) => d * Math.PI / 180;
  const R = 6371;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
            Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.asin(Math.sqrt(a));
}

// ─── Tiger geocoder helpers ─────────────────────────────────────────────────
// Run tiger.geocode() on one address against an already-borrowed client
// (caller manages SET statement_timeout). Tries the same variant ladder as
// the original geocode-candidates path: as-is, abbreviated street types, and
// w/o ZIP. Returns { candidates, warnings }.
async function geocodeOneAddress(client, rawAddress, sourceLat, sourceLng, limit, logTag = '[tiger-geocode]') {
  const baseAddress = normalizeAddressForTiger(rawAddress);
  const expanded = baseAddress
    .replace(/\bStreet\b/gi, 'St')
    .replace(/\bAvenue\b/gi, 'Ave')
    .replace(/\bRoad\b/gi, 'Rd')
    .replace(/\bDrive\b/gi, 'Dr')
    .replace(/\bSquare\b/gi, 'Sq')
    .replace(/\bCourt\b/gi, 'Ct')
    .replace(/\bLane\b/gi, 'Ln')
    .replace(/\bBoulevard\b/gi, 'Blvd');
  const noZip = expanded.replace(/\b\d{5}(?:-\d{4})?\b/g, '').replace(/\s+,/g, ',').trim();
  const variants = Array.from(new Set([baseAddress, expanded, noZip].filter(Boolean)));

  console.log(`${logTag} input raw="${rawAddress}" base="${baseAddress}" variants=${JSON.stringify(variants)} limit=${limit} sourceLat=${sourceLat} sourceLng=${sourceLng}`);

  let rows = [];
  const warnings = [];
  for (const v of variants) {
    const t0 = Date.now();
    try {
      const r = await client.query(
        `SELECT rating,
                ST_Y(geomout) AS glat,
                ST_X(geomout) AS glng,
                pprint_addy(addy) AS normalized_address
         FROM tiger.geocode($1, $2)
         ORDER BY rating ASC`,
        [v, limit],
      );
      const dt = Date.now() - t0;
      console.log(`${logTag} variant "${v}" → ${r.rows.length} rows in ${dt}ms`);
      for (const row of r.rows) {
        console.log(`${logTag}   r${row.rating} (${Number(row.glat).toFixed(5)}, ${Number(row.glng).toFixed(5)}) "${row.normalized_address}"`);
      }
      if (r.rows.length > 0) {
        rows = r.rows;
        if (r.rows.some(x => x.rating < 90)) {
          console.log(`${logTag} variant "${v}" produced rating<90 — accepting, stopping ladder`);
          break;
        } else {
          console.log(`${logTag} variant "${v}" all ratings ≥90 — keeping rows but trying next variant`);
        }
      }
    } catch (e) {
      const dt = Date.now() - t0;
      console.log(`${logTag} variant "${v}" FAILED in ${dt}ms: ${e.message}`);
      warnings.push(`Variant "${v}" timed out (>15s) — no street-level match in Tiger.`);
    }
  }
  const candidates = rows.map(r => ({
    rating: r.rating,
    lat: r.glat,
    lng: r.glng,
    normalized_address: r.normalized_address,
    distance_m: sourceLat != null && sourceLng != null
      ? Math.round(haversineKm(sourceLat, sourceLng, r.glat, r.glng) * 1000)
      : null,
  }));
  console.log(`${logTag} final candidate count=${candidates.length}`);
  return { candidates, warnings };
}

// ─── Snap-to-edge ──────────────────────────────────────────────────────────
// Given a candidate point, find the nearest tiger.edges centerline within
// `radiusM` meters and return the closest point ON that edge. This puts the
// proposed location literally on the road (S218 operator rule: POIs ordered
// right on street edges) instead of wherever Tiger's house-number
// interpolation landed (typically 2–8 m off-center toward the right curb).
//
// Returns { lat, lng, edge_tlid, edge_fullname, snap_distance_m } on hit,
// or null if no edge sits within the radius (tiny lakeside POI, off-grid).
async function snapToNearestEdge(client, lat, lng, radiusM = 50, logTag = '[snap-edge]') {
  // tiger.edges.the_geom is SRID 4269 (NAD83); input lat/lng is 4326 (WGS84).
  // Transform once into 4269 for the geometric snap, transform the closest
  // point back to 4326 for the response. Distance uses ::geography on the
  // 4326 sides so we get true meters.
  const r = await client.query(
    `WITH p AS (
       SELECT ST_SetSRID(ST_MakePoint($2, $1), 4326) AS pt_4326
     ),
     pt AS (
       SELECT pt_4326,
              ST_Transform(pt_4326, 4269) AS pt_4269
         FROM p
     )
     SELECT e.tlid,
            e.fullname,
            ST_Y(ST_Transform(ST_ClosestPoint(e.the_geom, pt.pt_4269), 4326)) AS slat,
            ST_X(ST_Transform(ST_ClosestPoint(e.the_geom, pt.pt_4269), 4326)) AS slng,
            ST_Distance(
              ST_Transform(ST_ClosestPoint(e.the_geom, pt.pt_4269), 4326)::geography,
              pt.pt_4326::geography
            ) AS dist_m
       FROM tiger.edges e, pt
      WHERE e.mtfcc LIKE 'S%'
        AND ST_DWithin(ST_Transform(e.the_geom, 4326)::geography, pt.pt_4326::geography, $3)
      ORDER BY e.the_geom <-> pt.pt_4269
      LIMIT 1`,
    [lat, lng, radiusM],
  );
  console.log(`${logTag} input (${lat.toFixed(5)}, ${lng.toFixed(5)}) radius=${radiusM}m → ${r.rows.length} edge(s) within radius`);
  if (r.rows.length === 0) {
    console.log(`${logTag} no edge within ${radiusM}m of input — caller will use raw Tiger coords without snap`);
    return null;
  }
  const row = r.rows[0];
  console.log(`${logTag} snapped to TLID ${row.tlid} "${row.fullname}" — moved ${Math.round(row.dist_m)}m to (${Number(row.slat).toFixed(5)}, ${Number(row.slng).toFixed(5)})`);
  return {
    lat: row.slat,
    lng: row.slng,
    edge_tlid: row.tlid,
    edge_fullname: row.fullname,
    snap_distance_m: Math.round(row.dist_m),
  };
}

// ─── Street-name fallback ──────────────────────────────────────────────────
// When tiger.geocode() can't land on a Salem-area candidate (Sea Level Oyster
// Bar at "94 Wharf St" — Tiger has Wharf St in zip 01970 but its addr table
// only goes 1–72, so the fuzzy fallback matched Wharf streets in other
// towns), search tiger.edges for an edge whose `fullname` matches the street
// name + within `radiusM` of the POI's current coords. Returns the closest
// point ON that edge so the operator gets a useful proposal anyway.
//
// `streetPattern` is an ILIKE pattern (caller decides whether to wildcard).
// Returns { lat, lng, edge_tlid, edge_fullname, snap_distance_m, name_match_distance_m }
// or null if no edge matches.
async function findEdgeByStreetName(client, streetPattern, nearLat, nearLng, radiusM = 2000, logTag = '[street-fallback]') {
  // Use KNN on tiger.edges.the_geom (4269) so the GIST index actually fires.
  // We grab the 5 nearest streets matching the name pattern, then post-filter
  // by the configured radius using haversine in JS (cheaper than per-row
  // ST_Transform + ST_DWithin against an unindexed expression). Returns
  // the closest match within the radius, or null.
  const r = await client.query(
    `SELECT e.tlid,
            e.fullname,
            ST_Y(ST_Transform(ST_ClosestPoint(e.the_geom,
              ST_Transform(ST_SetSRID(ST_MakePoint($3, $2), 4326), 4269)
            ), 4326)) AS slat,
            ST_X(ST_Transform(ST_ClosestPoint(e.the_geom,
              ST_Transform(ST_SetSRID(ST_MakePoint($3, $2), 4326), 4269)
            ), 4326)) AS slng
       FROM tiger.edges e
      WHERE e.mtfcc LIKE 'S%'
        AND e.fullname ILIKE $1
      ORDER BY e.the_geom <-> ST_Transform(ST_SetSRID(ST_MakePoint($3, $2), 4326), 4269)
      LIMIT 5`,
    [streetPattern, nearLat, nearLng],
  );
  console.log(`${logTag} pattern="${streetPattern}" near (${nearLat.toFixed(5)}, ${nearLng.toFixed(5)}) radius=${radiusM}m → ${r.rows.length} KNN candidate(s)`);
  if (r.rows.length === 0) {
    console.log(`${logTag} NO Tiger edges match name pattern — fallback exhausted`);
    return null;
  }
  // Pick the closest within radius (haversine in JS — micro work).
  let best = null;
  for (const row of r.rows) {
    const distM = haversineKm(nearLat, nearLng, row.slat, row.slng) * 1000;
    const inRadius = distM <= radiusM;
    console.log(`${logTag}   TLID ${row.tlid} "${row.fullname}" closest pt (${Number(row.slat).toFixed(5)}, ${Number(row.slng).toFixed(5)}) → ${Math.round(distM)}m ${inRadius ? '✓' : '✗ outside radius'}`);
    if (!inRadius) continue;
    if (!best || distM < best.snap_distance_m) {
      best = {
        lat: row.slat,
        lng: row.slng,
        edge_tlid: row.tlid,
        edge_fullname: row.fullname,
        snap_distance_m: Math.round(distM),
        name_match_distance_m: Math.round(distM),
      };
    }
  }
  if (best) {
    console.log(`${logTag} BEST: TLID ${best.edge_tlid} "${best.edge_fullname}" at ${best.snap_distance_m}m`);
  } else {
    console.log(`${logTag} all KNN candidates were outside ${radiusM}m radius — fallback exhausted`);
  }
  return best;
}

// Pull the street component out of a free-form address. Strips leading
// house number ("94 Wharf St" → "Wharf St"). Cuts off at the first comma
// so city/state/zip don't pollute the pattern. Returns null if the
// remainder is empty (e.g. address was just "Salem, MA").
function extractStreetFromAddress(raw) {
  if (!raw) return null;
  const beforeComma = String(raw).split(',')[0].trim();
  const m = beforeComma.match(/^\s*\d+(?:[a-zA-Z]|-\d+)?\s+(.+?)\s*\.?\s*$/);
  const street = m ? m[1].trim() : beforeComma.trim();
  return street.length > 0 ? street : null;
}

module.exports = {
  tigerPool,
  normalizeAddressForTiger,
  haversineKm,
  geocodeOneAddress,
  snapToNearestEdge,
  findEdgeByStreetName,
  extractStreetFromAddress,
  MODULE_ID,
};
