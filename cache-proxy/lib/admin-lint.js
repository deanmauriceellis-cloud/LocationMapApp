/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Admin Lint endpoints (S187).
 *
 * One-stop data-quality scan over the Salem dataset. Each check is an
 * isolated function returning { id, label, category, severity, count, items }.
 * Adding a new check is a one-function-plus-one-registry-entry change.
 *
 * Routes (all gated by /admin Basic Auth):
 *   GET  /admin/salem/lint
 *        Runs all instant checks in parallel.
 *
 *   POST /admin/salem/lint/address-geocode/run
 *        Kicks off background tiger.geocode() pass on POIs with addresses.
 *        Returns immediately with { state: 'running' }.
 *
 *   GET  /admin/salem/lint/address-geocode/status
 *        Returns { state, progress, total, items?, generated_at? }.
 *        State machine: idle | running | done | error.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module admin-lint.js';

const { Pool } = require('pg');
const { validateNarration } = require('./historical-narration-validator');

const SALEM_CENTER_LAT = 42.5223;
const SALEM_CENTER_LNG = -70.8950;
const OUTLIER_KM = 3.0;
const ITEM_CAP = 500;
const ADDR_GEOCODE_MISMATCH_M = 100;
const ADDR_GEOCODE_MAX_ITEMS = 300;

// ─── Tiger pool (peer auth, mirrors lib/salem-router.js) ────────────────────

let _tigerPool = null;
function tigerPool() {
  if (_tigerPool) return _tigerPool;
  const opts = process.env.TIGER_DATABASE_URL
    ? { connectionString: process.env.TIGER_DATABASE_URL, max: 2 }
    : { host: process.env.PGHOST || '/var/run/postgresql', database: 'tiger', max: 2 };
  _tigerPool = new Pool({ ...opts, connectionTimeoutMillis: 5000 });
  _tigerPool.on('error', (err) => console.error('[admin-lint] tiger pool error:', err.message));
  return _tigerPool;
}

// ─── Address normalization for tiger.geocode ────────────────────────────────
// Tiger's geocoder is picky: "10 Main St, Salem, MA 01970, USA" returns ZERO
// candidates because of the trailing ", USA". Same address without "USA"
// returns a perfect rating-0 match. Normalize before handing it off.
function normalizeAddressForTiger(raw) {
  if (!raw) return raw;
  let s = String(raw).trim();
  // Drop a trailing country tag.
  s = s.replace(/,?\s*(USA|United States(?: of America)?|U\.S\.A\.?|U\.S\.)\s*$/i, '');
  // Collapse whitespace + trailing comma.
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
async function geocodeOneAddress(client, rawAddress, sourceLat, sourceLng, limit) {
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

  let rows = [];
  const warnings = [];
  for (const v of variants) {
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
      if (r.rows.length > 0) {
        rows = r.rows;
        if (r.rows.some(x => x.rating < 90)) break;
      }
    } catch (e) {
      warnings.push(`Variant "${v}" timed out (>15s) — no street-level match in Tiger.`);
    }
  }
  const candidates = rows.map(r => ({
    rating: r.rating,
    lat: r.glat,
    lng: r.glng,
    normalized_address: r.normalized_address,
    distance_m: Math.round(haversineKm(sourceLat, sourceLng, r.glat, r.glng) * 1000),
  }));
  return { candidates, warnings };
}

// Across the whole cluster (focal + dupes), pick the geocode candidate that
// most strongly anchors the cluster: lowest Tiger rating wins (0 = exact
// house-number match, 100 = city centroid). Ties broken by smallest
// distance_m from that candidate's source POI — the geocode landed nearest
// to where the POI is actually stored, so address+coord agree most.
// Returns { source_poi_id, source_poi_name, rating, lat, lng, normalized_address, distance_m }
// or null if nothing matched anywhere.
function pickBestMatch(focalPoi, focalCandidates, duplicates) {
  const all = [];
  for (const c of focalCandidates) {
    all.push({ ...c, source_poi_id: focalPoi.id, source_poi_name: focalPoi.name });
  }
  for (const d of duplicates) {
    for (const c of (d.candidates || [])) {
      all.push({ ...c, source_poi_id: d.id, source_poi_name: d.name });
    }
  }
  if (all.length === 0) return null;
  all.sort((a, b) => a.rating - b.rating || a.distance_m - b.distance_m);
  return all[0];
}

// ─── Suppression filter ─────────────────────────────────────────────────────
// Each POI check appends a NOT IN clause that excludes operator-flagged false
// positives. Helper returns the SQL fragment and pushes the check_id onto
// the params array — caller substitutes it where the WHERE-clause needs it.
function suppressionClause(checkId, params, idColumn = 'id') {
  params.push(checkId);
  return ` AND ${idColumn} NOT IN (SELECT poi_id FROM salem_lint_suppressions WHERE check_id = $${params.length})`;
}

// ─── Item builders ───────────────────────────────────────────────────────────

function poiItem(row, message, fix_hint, extras = {}) {
  return {
    entity_type: 'poi',
    entity_id: row.id,
    entity_label: row.name,
    lat: row.lat,
    lng: row.lng,
    message,
    fix_hint,
    ...extras,
  };
}

function tourItem(row, message, fix_hint, extras = {}) {
  return {
    entity_type: 'tour',
    entity_id: row.id,
    entity_label: row.name,
    message,
    fix_hint,
    ...extras,
  };
}

// ─── Check implementations ───────────────────────────────────────────────────

async function checkNarrationTourMissing(pgPool) {
  const params = [ITEM_CAP];
  const suppress = suppressionClause('narration_tour_missing', params);
  const { rows } = await pgPool.query(`
    SELECT id, name, lat, lng, has_announce_narration,
           COALESCE(short_narration,'') AS short_narration,
           COALESCE(long_narration,'') AS long_narration,
           category
    FROM salem_pois
    WHERE deleted_at IS NULL
      AND is_tour_poi = true
      AND (
        has_announce_narration = false
        OR (COALESCE(short_narration,'')='' AND COALESCE(long_narration,'')='')
      )
      ${suppress}
    ORDER BY category, name
    LIMIT $1
  `, params);
  return rows.map(r => {
    const reasons = [];
    if (!r.has_announce_narration) reasons.push('has_announce_narration is false');
    if (!r.short_narration && !r.long_narration) reasons.push('no narration text');
    return poiItem(r,
      `Tour POI is silent — ${reasons.join(' and ')}.`,
      `Open the editor, fill in Short Narration on the Narration tab, and check "Has announce narration".`,
    );
  });
}

async function checkNarrationCivicMissing(pgPool) {
  const params = [ITEM_CAP];
  const suppress = suppressionClause('narration_civic_missing', params);
  const { rows } = await pgPool.query(`
    SELECT id, name, lat, lng, has_announce_narration,
           COALESCE(short_narration,'') AS short_narration,
           COALESCE(long_narration,'') AS long_narration
    FROM salem_pois
    WHERE deleted_at IS NULL
      AND is_civic_poi = true
      AND (
        has_announce_narration = false
        OR (COALESCE(short_narration,'')='' AND COALESCE(long_narration,'')='')
      )
      ${suppress}
    ORDER BY name
    LIMIT $1
  `, params);
  return rows.map(r => poiItem(r,
    `Civic POI has no narration — won't be heard when the "POIs Civic" Layers checkbox is on.`,
    `Open the editor, fill in Short Narration on the Narration tab, and check "Has announce narration".`,
  ));
}

async function checkTourGateMissingYear(pgPool) {
  const params = [ITEM_CAP];
  const suppress = suppressionClause('tour_gate_missing_year', params);
  const { rows } = await pgPool.query(`
    SELECT id, name, lat, lng, category
    FROM salem_pois
    WHERE deleted_at IS NULL
      AND is_tour_poi = true
      AND category IN ('HISTORICAL_BUILDINGS','ENTERTAINMENT','LODGING')
      AND year_established IS NULL
      ${suppress}
    ORDER BY category, name
    LIMIT $1
  `, params);
  return rows.map(r => poiItem(r,
    `Tour POI in ${r.category} but year_established is unset — won't qualify for the Historical Landmark override (requires year ≤ 1860).`,
    `Open the editor → Operational tab → set Year Established. If the building post-dates 1860 it won't narrate via Hist Landmark; consider authoring narration text directly.`,
  ));
}

async function checkHistBldgMissingYear(pgPool) {
  const params = [ITEM_CAP];
  const suppress = suppressionClause('hist_bldg_missing_year', params);
  const { rows } = await pgPool.query(`
    SELECT id, name, lat, lng, is_tour_poi, subcategory
    FROM salem_pois
    WHERE deleted_at IS NULL
      AND category = 'HISTORICAL_BUILDINGS'
      AND year_established IS NULL
      ${suppress}
    ORDER BY is_tour_poi DESC, name
    LIMIT $1
  `, params);
  return rows.map(r => poiItem(r,
    r.is_tour_poi
      ? `Historical Building has no year_established. Won't qualify for the Hist Landmark narration override (requires year ≤ 1860).`
      : `Historical Building has no year_established. Currently invisible to the Hist Landmark gate even if you flip on tour eligibility later.`,
    `Open the editor → Operational tab → set Year Established. Reference: SalemIntelligence, MHC inventory, or your own records.`,
  ));
}

async function checkHistBldgMissingNarration(pgPool) {
  const params = [ITEM_CAP];
  const suppress = suppressionClause('hist_bldg_missing_narration', params);
  const { rows } = await pgPool.query(`
    SELECT id, name, lat, lng, is_tour_poi, has_announce_narration,
           COALESCE(short_narration,'') AS short_narration,
           COALESCE(long_narration,'') AS long_narration
    FROM salem_pois
    WHERE deleted_at IS NULL
      AND category = 'HISTORICAL_BUILDINGS'
      AND COALESCE(short_narration,'') = ''
      AND COALESCE(long_narration,'') = ''
      ${suppress}
    ORDER BY is_tour_poi DESC, name
    LIMIT $1
  `, params);
  return rows.map(r => poiItem(r,
    `Historical Building has no narration text at all (short or long). Will be silent even if a tourist walks through its geofence with the right Layers checkbox on.`,
    `Open the editor → Narration tab → write Short Narration. Pull from SalemIntelligence at :8089 if useful.`,
  ));
}

async function checkHistCuratedNotTour(pgPool) {
  const params = [ITEM_CAP];
  const suppress = suppressionClause('hist_curated_not_tour', params);
  const { rows } = await pgPool.query(`
    SELECT id, name, lat, lng, data_source
    FROM salem_pois
    WHERE deleted_at IS NULL
      AND category = 'HISTORICAL_BUILDINGS'
      AND (data_source NOT LIKE '%massgis%' OR data_source IS NULL)
      AND is_tour_poi = false
      ${suppress}
    ORDER BY name
    LIMIT $1
  `, params);
  return rows.map(r => poiItem(r,
    `Curated Historical Building is not flagged is_tour_poi. Won't narrate during tour mode and won't appear under Tour POIs in the tree.`,
    `Open the editor → Operational tab → check "Tour POI". The MHC bulk-imported buildings stay off; only the hand-curated ones (data_source not "massgis") should be on.`,
  ));
}

async function checkHistPre1860NoHistoricalNarration(pgPool) {
  const params = [ITEM_CAP];
  const suppress = suppressionClause('hist_pre1860_no_historical_narration', params);
  const { rows } = await pgPool.query(`
    SELECT id, name, lat, lng, year_established
    FROM salem_pois
    WHERE deleted_at IS NULL
      AND category = 'HISTORICAL_BUILDINGS'
      AND year_established IS NOT NULL
      AND year_established < 1860
      AND (historical_narration IS NULL OR historical_narration = '')
      ${suppress}
    ORDER BY year_established, name
    LIMIT $1
  `, params);
  return rows.map(r => poiItem(r,
    `Pre-1860 Historical Building (${r.year_established}) has no historical_narration. Won't surface in Historical Tour mode.`,
    `Open the editor → Narration tab → write Historical narration (pre-1860 only). Or run the generator: \`node cache-proxy/scripts/generate-historical-narrations.js --ids=${r.id}\`.`,
  ));
}

async function checkHistoricalNarrationNeedsRefinement(pgPool) {
  const params = [];
  const suppress = suppressionClause('historical_narration_needs_refinement', params);
  const { rows } = await pgPool.query(`
    SELECT id, name, lat, lng, historical_narration
    FROM salem_pois
    WHERE deleted_at IS NULL
      AND historical_narration IS NOT NULL
      AND length(trim(historical_narration)) > 0
      ${suppress}
    ORDER BY name
  `, params);

  const items = [];
  for (const r of rows) {
    const result = validateNarration(r.historical_narration, r.name);
    if (result.critical.length === 0) continue;

    // Group reason codes by type for a compact, readable message.
    const grouped = {};
    for (const reason of result.critical) {
      const tag = reason.split(':')[0];
      grouped[tag] = (grouped[tag] || 0) + 1;
    }
    const summary = Object.entries(grouped)
      .map(([t, n]) => (n > 1 ? `${t}(${n})` : t))
      .join(', ');

    const sample = result.critical.slice(0, 3).join(' · ');

    items.push(poiItem(r,
      `Historical narration has quality issues: ${summary}. ${sample}`,
      `Open the editor → Narration tab → review and rewrite, or clear the field. Targeted regen: \`node cache-proxy/scripts/generate-historical-narrations.js --ids=${r.id} --force\`. Or click Suppress if this is a false positive.`,
      { reasons: result.critical, words: result.wordCount },
    ));

    if (items.length >= ITEM_CAP) break;
  }
  return items;
}

async function checkCivicFlagMismatch(pgPool) {
  const params = [ITEM_CAP];
  const suppress = suppressionClause('civic_flag_mismatch', params);
  const { rows } = await pgPool.query(`
    SELECT id, name, lat, lng, category
    FROM salem_pois
    WHERE deleted_at IS NULL
      AND is_civic_poi = true
      AND category <> 'CIVIC'
      ${suppress}
    ORDER BY category, name
    LIMIT $1
  `, params);
  return rows.map(r => poiItem(r,
    `is_civic_poi=true but category is "${r.category}", not CIVIC. Will appear under both the POIs Civic Layers checkbox and its real category bucket — usually unintended (S186 leftover after a category change).`,
    `Open the editor → Operational tab → uncheck "Civic POI". Or, if it really IS civic, change the category to CIVIC.`,
  ));
}

async function checkContentNoDescription(pgPool) {
  const params = [ITEM_CAP];
  const suppress = suppressionClause('content_no_description', params);
  const { rows } = await pgPool.query(`
    SELECT id, name, lat, lng, category
    FROM salem_pois
    WHERE deleted_at IS NULL
      AND COALESCE(description,'') = ''
      AND COALESCE(short_description,'') = ''
      ${suppress}
    ORDER BY is_tour_poi DESC, category, name
    LIMIT $1
  `, params);
  return rows.map(r => poiItem(r,
    `POI has no description and no short_description.`,
    `Open the editor → Content tab → write at least a Short Description.`,
  ));
}

async function checkContentNoImage(pgPool) {
  const params = [ITEM_CAP];
  const suppress = suppressionClause('content_no_image', params);
  const { rows } = await pgPool.query(`
    SELECT id, name, lat, lng, category, is_tour_poi
    FROM salem_pois
    WHERE deleted_at IS NULL
      AND COALESCE(image_asset,'') = ''
      AND is_tour_poi = true
      ${suppress}
    ORDER BY category, name
    LIMIT $1
  `, params);
  return rows.map(r => poiItem(r,
    `Tour POI has no image_asset — POI detail card will show a placeholder.`,
    `Generate or assign an image, then open the editor → Content tab → set Image Asset.`,
  ));
}

async function checkGeoOutlier(pgPool) {
  const params = [SALEM_CENTER_LAT, SALEM_CENTER_LNG, OUTLIER_KM, ITEM_CAP];
  const suppress = suppressionClause('geo_outlier', params);
  const { rows } = await pgPool.query(`
    SELECT id, name, lat, lng, category
    FROM salem_pois
    WHERE deleted_at IS NULL
      AND (
        lat = 0 OR lng = 0
        OR (
          6371.0 * 2.0 * ASIN(SQRT(
            POWER(SIN(RADIANS(lat - $1) / 2.0), 2) +
            COS(RADIANS($1)) * COS(RADIANS(lat)) *
            POWER(SIN(RADIANS(lng - $2) / 2.0), 2)
          )) > $3
        )
      )
      ${suppress}
    ORDER BY name
    LIMIT $4
  `, params);
  return rows.map(r => {
    if (r.lat === 0 || r.lng === 0) {
      return poiItem(r,
        `POI has lat=${r.lat} / lng=${r.lng} — coordinates were never set.`,
        `Open the editor and drag the map marker to the actual location, or paste correct lat/lng.`,
      );
    }
    const km = haversineKm(SALEM_CENTER_LAT, SALEM_CENTER_LNG, r.lat, r.lng);
    return poiItem(r,
      `POI is ${km.toFixed(2)} km from downtown Salem — coordinates probably wrong.`,
      `Open the editor and drag the map marker to the actual location.`,
    );
  });
}

async function checkGeoCentroidSnap(pgPool) {
  // Detect lat/lng collisions of 3+ POIs at the same point — strongest signal
  // of a geocoder that fell back to a city-level centroid.
  const params = [];
  const suppress = suppressionClause('geo_centroid_snap', params);
  const { rows } = await pgPool.query(`
    SELECT lat, lng, COUNT(*) AS n,
           ARRAY_AGG(id ORDER BY name) AS ids,
           ARRAY_AGG(name ORDER BY name) AS names
    FROM salem_pois
    WHERE deleted_at IS NULL
      ${suppress}
    GROUP BY lat, lng
    HAVING COUNT(*) >= 3
    ORDER BY n DESC
    LIMIT 50
  `, params);
  const items = [];
  for (const cluster of rows) {
    const sharedNames = cluster.names.slice(0, 3).join(', ') + (cluster.names.length > 3 ? `, +${cluster.names.length - 3} more` : '');
    for (let i = 0; i < cluster.ids.length && items.length < ITEM_CAP; i++) {
      items.push({
        entity_type: 'poi',
        entity_id: cluster.ids[i],
        entity_label: cluster.names[i],
        lat: cluster.lat,
        lng: cluster.lng,
        message: `${cluster.n} POIs share these exact coordinates (${cluster.lat.toFixed(5)}, ${cluster.lng.toFixed(5)}) — geocoder fallback. Cluster: ${sharedNames}.`,
        fix_hint: `Open the editor and drag the marker to the real address. Or fix the address field and re-run geocoding.`,
      });
    }
    if (items.length >= ITEM_CAP) break;
  }
  return items;
}

async function checkDuplicates(pgPool) {
  const radius = 15;
  const latBox = radius / 111000.0;
  const lngBox = radius / 60000.0;
  const params = [radius, latBox, lngBox, 'duplicates'];
  const { rows: pairs } = await pgPool.query(`
    SELECT
      a.id AS a_id, a.name AS a_name, a.lat AS a_lat, a.lng AS a_lng,
      b.id AS b_id, b.name AS b_name, b.lat AS b_lat, b.lng AS b_lng,
      6371000.0 * 2.0 * ASIN(SQRT(
        POWER(SIN(RADIANS(b.lat - a.lat) / 2.0), 2) +
        COS(RADIANS(a.lat)) * COS(RADIANS(b.lat)) *
        POWER(SIN(RADIANS(b.lng - a.lng) / 2.0), 2)
      )) AS distance_m
    FROM salem_pois a
    JOIN salem_pois b
      ON a.id < b.id
     AND ABS(a.lat - b.lat) <= $2
     AND ABS(a.lng - b.lng) <= $3
    WHERE a.deleted_at IS NULL AND b.deleted_at IS NULL
      AND a.id NOT IN (SELECT poi_id FROM salem_lint_suppressions WHERE check_id = $4)
      AND b.id NOT IN (SELECT poi_id FROM salem_lint_suppressions WHERE check_id = $4)
      AND 6371000.0 * 2.0 * ASIN(SQRT(
            POWER(SIN(RADIANS(b.lat - a.lat) / 2.0), 2) +
            COS(RADIANS(a.lat)) * COS(RADIANS(b.lat)) *
            POWER(SIN(RADIANS(b.lng - a.lng) / 2.0), 2)
          )) <= $1
    ORDER BY distance_m ASC
  `, params);

  // Union-find clustering
  const parent = new Map();
  const data = new Map();
  function find(x) {
    let r = x;
    while (parent.get(r) !== r) r = parent.get(r);
    return r;
  }
  function union(x, y) {
    const rx = find(x), ry = find(y);
    if (rx !== ry) parent.set(rx, ry);
  }
  function ensure(id, name, lat, lng) {
    if (!parent.has(id)) {
      parent.set(id, id);
      data.set(id, { id, name, lat: parseFloat(lat), lng: parseFloat(lng) });
    }
  }
  for (const p of pairs) {
    ensure(p.a_id, p.a_name, p.a_lat, p.a_lng);
    ensure(p.b_id, p.b_name, p.b_lat, p.b_lng);
    union(p.a_id, p.b_id);
  }
  const clusters = new Map();
  for (const k of parent.keys()) {
    const root = find(k);
    if (!clusters.has(root)) clusters.set(root, []);
    clusters.get(root).push(data.get(k));
  }

  const items = [];
  for (const members of clusters.values()) {
    if (members.length < 2) continue;
    // S195 — drop members sitting at geocoder-fallback points (3+ POIs sharing
    // exact lat/lng). Those POIs are flagged separately by geo_centroid_snap
    // with a clearer "geocoder fallback" message. Without this filter the
    // duplicates check listed unrelated co-located businesses (massage therapy,
    // pub, gift shop, etc.) under a "Duplicate POIs" header — confusing UX
    // because they're not duplicates of each other, they just collapsed onto
    // the same fallback centroid.
    const filtered = members.filter((m) => {
      const sameCoords = members.filter(
        (o) => o.lat === m.lat && o.lng === m.lng,
      ).length;
      return sameCoords < 3;
    });
    if (filtered.length < 2) continue;
    const sharedNames =
      filtered.map((m) => m.name).slice(0, 3).join(', ') +
      (filtered.length > 3 ? `, +${filtered.length - 3} more` : '');
    for (const m of filtered) {
      if (items.length >= ITEM_CAP) break;
      items.push({
        entity_type: 'poi',
        entity_id: m.id,
        entity_label: m.name,
        lat: m.lat,
        lng: m.lng,
        message: `${filtered.length} POIs share locations within 15m of each other: ${sharedNames}. (Possible duplicates or genuinely-distinct neighbors.)`,
        fix_hint: `Open each in the editor. If they're duplicates, pick the canonical record and Delete (soft) the losers. If they're genuinely-distinct neighbors at the same address, Suppress this flag.`,
      });
    }
    if (items.length >= ITEM_CAP) break;
  }
  return items;
}

async function checkCleanupDedupLosers(pgPool) {
  const params = [ITEM_CAP];
  const suppress = suppressionClause('cleanup_dedup_losers', params);
  const { rows } = await pgPool.query(`
    SELECT id, name, lat, lng, data_source, deleted_at
    FROM salem_pois
    WHERE (data_source ILIKE '%dedup%loser%'
           OR data_source ILIKE '%address-dedup%loser%')
      ${suppress}
    ORDER BY name
    LIMIT $1
  `, params);
  return rows.map(r => poiItem(r,
    `Soft-deleted dedup loser still in PG (${r.data_source}). Should be hard-deleted before AAB upload.`,
    `Run: DELETE FROM salem_pois WHERE id='${r.id}'; (verify zero FK refs first). Carry-forward from S185.`,
    { soft_deleted: true },
  ));
}

async function checkProvenanceGaps(pgPool) {
  const params = [ITEM_CAP];
  const suppress = suppressionClause('provenance_gaps', params);
  const { rows } = await pgPool.query(`
    SELECT id, name, lat, lng, data_source, confidence, verified_date
    FROM salem_pois
    WHERE deleted_at IS NULL
      AND is_tour_poi = true
      AND (
        data_source IS NULL
        OR data_source = 'unified_migration'
        OR verified_date IS NULL
        OR confidence < 0.5
      )
      ${suppress}
    ORDER BY confidence ASC, name
    LIMIT $1
  `, params);
  return rows.map(r => {
    const reasons = [];
    if (!r.data_source || r.data_source === 'unified_migration') reasons.push(`data_source = "${r.data_source || 'NULL'}"`);
    if (!r.verified_date) reasons.push('verified_date is NULL');
    if (r.confidence != null && r.confidence < 0.5) reasons.push(`confidence = ${r.confidence}`);
    return poiItem(r,
      `Tour POI has thin provenance: ${reasons.join('; ')}.`,
      `Open the editor → Provenance tab → set Data Source, Verified Date, and bump Confidence.`,
    );
  });
}

async function checkTourOrphanStops(pgPool) {
  const { rows } = await pgPool.query(`
    SELECT s.tour_id, s.stop_id, s.stop_order, s.poi_id,
           t.name AS tour_name,
           p.name AS poi_name, p.deleted_at AS poi_deleted_at
    FROM salem_tour_stops s
    JOIN salem_tours t ON t.id = s.tour_id
    LEFT JOIN salem_pois p ON p.id = s.poi_id
    WHERE s.poi_id IS NOT NULL
      AND (p.id IS NULL OR p.deleted_at IS NOT NULL)
    ORDER BY t.name, s.stop_order
    LIMIT $1
  `, [ITEM_CAP]);
  return rows.map(r => ({
    entity_type: 'tour',
    entity_id: r.tour_id,
    entity_label: r.tour_name,
    message: r.poi_name
      ? `Tour "${r.tour_name}" stop #${r.stop_order} references soft-deleted POI "${r.poi_name}" (${r.poi_id}).`
      : `Tour "${r.tour_name}" stop #${r.stop_order} references missing POI ${r.poi_id}.`,
    fix_hint: `Open the Tours view → ${r.tour_name} → remove or relink stop ${r.stop_order}. Or restore the POI from the POI editor.`,
  }));
}

async function checkTourEmpty(pgPool) {
  const { rows } = await pgPool.query(`
    SELECT t.id, t.name, t.stop_count AS claimed,
           (SELECT COUNT(*) FROM salem_tour_stops s WHERE s.tour_id = t.id) AS actual
    FROM salem_tours t
    ORDER BY t.name
  `);
  return rows
    .filter(r => Number(r.actual) === 0 || Number(r.actual) !== r.claimed)
    .slice(0, ITEM_CAP)
    .map(r => {
      const actual = Number(r.actual);
      if (actual === 0) {
        return tourItem(r,
          `Tour "${r.name}" has zero stops.`,
          `Open the Tours view → ${r.name} → add stops, or delete the tour if obsolete.`,
        );
      }
      return tourItem(r,
        `Tour "${r.name}" has ${actual} actual stops but stop_count column says ${r.claimed}.`,
        `Re-run cache-proxy/scripts/publish-tours.js to recompute stop_count, or update the tour via the admin tour panel.`,
      );
    });
}

async function checkTourLegsEmpty(pgPool) {
  const { rows } = await pgPool.query(`
    SELECT t.id AS tour_id, t.name AS tour_name,
           l.leg_order,
           CASE
             WHEN l.polyline_json IS NULL THEN 'NULL'
             WHEN jsonb_typeof(l.polyline_json) = 'object'
                  AND jsonb_array_length(COALESCE(l.polyline_json->'coordinates','[]'::jsonb)) = 0 THEN 'empty'
             WHEN jsonb_typeof(l.polyline_json) = 'array'
                  AND jsonb_array_length(l.polyline_json) = 0 THEN 'empty'
             ELSE 'ok'
           END AS state
    FROM salem_tour_legs l
    JOIN salem_tours t ON t.id = l.tour_id
    WHERE l.polyline_json IS NULL
       OR (jsonb_typeof(l.polyline_json) = 'object'
           AND jsonb_array_length(COALESCE(l.polyline_json->'coordinates','[]'::jsonb)) = 0)
       OR (jsonb_typeof(l.polyline_json) = 'array'
           AND jsonb_array_length(l.polyline_json) = 0)
    ORDER BY t.name, l.leg_order
    LIMIT $1
  `, [ITEM_CAP]);
  return rows.map(r => ({
    entity_type: 'tour',
    entity_id: r.tour_id,
    entity_label: r.tour_name,
    message: `Tour "${r.tour_name}" leg #${r.leg_order} polyline is ${r.state} — Compute Route was never run for this leg.`,
    fix_hint: `Open the Tours view → ${r.tour_name} → click Compute Route on the offending leg.`,
  }));
}

// ─── Registry ────────────────────────────────────────────────────────────────

const CHECKS = [
  { id: 'narration_tour_missing',    label: 'Tour POIs missing narration',                category: 'Narration',  severity: 'warn',  run: checkNarrationTourMissing },
  { id: 'narration_civic_missing',   label: 'Civic POIs missing narration',               category: 'Narration',  severity: 'info',  run: checkNarrationCivicMissing },
  { id: 'tour_gate_missing_year',    label: 'Tour-eligible POIs missing year_established',category: 'Tour gates', severity: 'warn',  run: checkTourGateMissingYear },
  { id: 'hist_bldg_missing_year',    label: 'Historical Buildings missing year_established',category: 'Historical Buildings', severity: 'warn',  run: checkHistBldgMissingYear },
  { id: 'hist_bldg_missing_narration', label: 'Historical Buildings with no narration text', category: 'Historical Buildings', severity: 'warn',  run: checkHistBldgMissingNarration },
  { id: 'hist_curated_not_tour',     label: 'Curated Historical Buildings missing tour flag', category: 'Historical Buildings', severity: 'warn',  run: checkHistCuratedNotTour },
  { id: 'hist_pre1860_no_historical_narration', label: 'Pre-1860 Historical Buildings missing historical narration', category: 'Historical Buildings', severity: 'warn', run: checkHistPre1860NoHistoricalNarration },
  { id: 'historical_narration_needs_refinement', label: 'Historical narration needs refinement (quality issues)', category: 'Historical Buildings', severity: 'warn', run: checkHistoricalNarrationNeedsRefinement },
  { id: 'civic_flag_mismatch',       label: 'is_civic_poi=true but category ≠ CIVIC',     category: 'Tour gates', severity: 'warn',  run: checkCivicFlagMismatch },
  { id: 'content_no_description',    label: 'POIs with no description text',              category: 'Content',    severity: 'info',  run: checkContentNoDescription },
  { id: 'content_no_image',          label: 'Tour POIs with no image',                    category: 'Content',    severity: 'info',  run: checkContentNoImage },
  { id: 'geo_outlier',               label: 'Outlier coordinates',                        category: 'Geography',  severity: 'error', run: checkGeoOutlier },
  { id: 'geo_centroid_snap',         label: 'Geocoder fallback (3+ POIs at same point)',  category: 'Geography',  severity: 'warn',  run: checkGeoCentroidSnap },
  { id: 'duplicates',                label: 'Co-located POIs (within 15m)',               category: 'Duplicates', severity: 'warn',  run: checkDuplicates },
  { id: 'cleanup_dedup_losers',      label: 'Soft-deleted dedup losers (pre-AAB cleanup)',category: 'Cleanup',    severity: 'info',  run: checkCleanupDedupLosers },
  { id: 'provenance_gaps',           label: 'Tour POIs with thin provenance',             category: 'Provenance', severity: 'info',  run: checkProvenanceGaps },
  { id: 'tour_orphan_stops',         label: 'Tour stops referencing deleted POIs',        category: 'Tour data',  severity: 'warn',  run: checkTourOrphanStops },
  { id: 'tour_empty',                label: 'Tours with zero or mismatched stops',        category: 'Tour data',  severity: 'warn',  run: checkTourEmpty },
  { id: 'tour_legs_empty',           label: 'Tour legs with empty polyline',              category: 'Tour data',  severity: 'warn',  run: checkTourLegsEmpty },
];

// ─── On-demand: address-geocode mismatch ─────────────────────────────────────

const addrGeocodeState = {
  state: 'idle',
  progress: 0,
  total: 0,
  startedAt: null,
  finishedAt: null,
  items: null,
  error: null,
};

async function runAddressGeocode(pgPool) {
  addrGeocodeState.state = 'running';
  addrGeocodeState.progress = 0;
  addrGeocodeState.total = 0;
  addrGeocodeState.startedAt = new Date().toISOString();
  addrGeocodeState.finishedAt = null;
  addrGeocodeState.items = null;
  addrGeocodeState.error = null;

  try {
    const { rows: pois } = await pgPool.query(`
      SELECT id, name, lat, lng, address
      FROM salem_pois
      WHERE deleted_at IS NULL
        AND COALESCE(address,'') <> ''
        AND length(address) >= 8
      ORDER BY name
    `);
    addrGeocodeState.total = pois.length;

    const tiger = tigerPool();
    const flagged = [];

    for (const poi of pois) {
      try {
        // tiger.geocode returns multiple candidates; take the best (lowest rating).
        const { rows: g } = await tiger.query(
          `SELECT rating,
                  ST_Y(geomout) AS glat,
                  ST_X(geomout) AS glng
           FROM tiger.geocode($1, 1)`,
          [normalizeAddressForTiger(poi.address)],
        );
        if (g.length === 0) {
          flagged.push({
            entity_type: 'poi',
            entity_id: poi.id,
            entity_label: poi.name,
            lat: poi.lat,
            lng: poi.lng,
            message: `Address "${poi.address}" did not geocode — Tiger returned no candidates.`,
            fix_hint: `Verify the address (street + number + city) is correct, or hand-place the marker.`,
            distance_m: null,
            rating: null,
          });
        } else {
          const best = g[0];
          const distM = haversineKm(poi.lat, poi.lng, best.glat, best.glng) * 1000;
          if (distM > ADDR_GEOCODE_MISMATCH_M) {
            flagged.push({
              entity_type: 'poi',
              entity_id: poi.id,
              entity_label: poi.name,
              lat: poi.lat,
              lng: poi.lng,
              message: `Stored coords are ${distM.toFixed(0)} m from where Tiger geocodes "${poi.address}" (rating ${best.rating}, lower is better).`,
              fix_hint: `Open the editor: either fix the address to match the marker, or drag the marker to (${best.glat.toFixed(5)}, ${best.glng.toFixed(5)}).`,
              distance_m: Math.round(distM),
              rating: best.rating,
              suggested_lat: best.glat,
              suggested_lng: best.glng,
            });
          }
        }
      } catch (e) {
        // Geocode failure on a single POI shouldn't kill the whole pass.
        console.warn(`[admin-lint] geocode "${poi.address}" failed:`, e.message);
      }
      addrGeocodeState.progress += 1;
    }

    flagged.sort((a, b) => (b.distance_m || 0) - (a.distance_m || 0));
    addrGeocodeState.items = flagged.slice(0, ADDR_GEOCODE_MAX_ITEMS);
    addrGeocodeState.state = 'done';
    addrGeocodeState.finishedAt = new Date().toISOString();
  } catch (err) {
    addrGeocodeState.state = 'error';
    addrGeocodeState.error = err.message;
    addrGeocodeState.finishedAt = new Date().toISOString();
    console.error('[admin-lint] address-geocode pass failed:', err);
  }
}

// ─── Module export ───────────────────────────────────────────────────────────

module.exports = function(app, deps) {
  const { pgPool, requirePg } = deps;

  // Idempotent schema bootstrap. salem_lint_suppressions tracks operator-flagged
  // false positives — POIs that look wrong to the lint but are correct by
  // design (e.g. Salem Lighthouse, no street address).
  pgPool.query(`
    CREATE TABLE IF NOT EXISTS salem_lint_suppressions (
      poi_id        TEXT        NOT NULL REFERENCES salem_pois(id) ON DELETE CASCADE,
      check_id      TEXT        NOT NULL,
      reason        TEXT,
      suppressed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      PRIMARY KEY (poi_id, check_id)
    );
    CREATE INDEX IF NOT EXISTS idx_salem_lint_suppressions_check_id
      ON salem_lint_suppressions (check_id);

    -- Operator-flagged "this geocode candidate is wrong" entries. Filtered
    -- out of the geocode-candidates response so the bad candidate stops
    -- showing up. Lat/lng rounded to 5 decimals (~1 m) so floating-point
    -- jitter from PostGIS doesn't defeat the PK.
    CREATE TABLE IF NOT EXISTS salem_geocode_blacklist (
      poi_id      TEXT        NOT NULL REFERENCES salem_pois(id) ON DELETE CASCADE,
      lat         NUMERIC(8,5) NOT NULL,
      lng         NUMERIC(8,5) NOT NULL,
      reason      TEXT,
      created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      PRIMARY KEY (poi_id, lat, lng)
    );
    CREATE INDEX IF NOT EXISTS idx_salem_geocode_blacklist_poi_id
      ON salem_geocode_blacklist (poi_id);
  `).catch(err => console.error('[admin-lint] schema bootstrap failed:', err.message));

  // GET /admin/salem/lint — run all instant checks
  // (Basic Auth is already enforced via app.use('/admin', requireBasicAuth) in server.js.)
  app.get('/admin/salem/lint', requirePg, async (req, res) => {
    const generated_at = new Date().toISOString();
    const results = await Promise.all(CHECKS.map(async (chk) => {
      try {
        const items = await chk.run(pgPool);
        return {
          id: chk.id,
          label: chk.label,
          category: chk.category,
          severity: chk.severity,
          count: items.length,
          items,
        };
      } catch (e) {
        console.error(`[admin-lint] check ${chk.id} failed:`, e.message);
        return {
          id: chk.id,
          label: chk.label,
          category: chk.category,
          severity: chk.severity,
          count: 0,
          items: [],
          error: e.message,
        };
      }
    }));

    // Append the on-demand address-geocode check as a metadata entry so the
    // tab knows to render it as a node even though it's not in CHECKS.
    res.json({
      generated_at,
      checks: results,
      on_demand: [
        {
          id: 'address_geocode_mismatch',
          label: 'Address ↔ geocode mismatch (deep scan)',
          category: 'Geography',
          severity: 'info',
          state: addrGeocodeState.state,
          count: addrGeocodeState.items ? addrGeocodeState.items.length : 0,
        },
      ],
    });
  });

  // POST /admin/salem/lint/address-geocode/run — kick off background pass
  app.post('/admin/salem/lint/address-geocode/run', requirePg, async (req, res) => {
    if (addrGeocodeState.state === 'running') {
      return res.json({ state: 'running', progress: addrGeocodeState.progress, total: addrGeocodeState.total });
    }
    // Fire-and-forget; don't await.
    void runAddressGeocode(pgPool);
    res.json({ state: 'running', progress: 0, total: 0 });
  });

  // GET /admin/salem/lint/poi/:id/geocode-candidates
  // Runs tiger.geocode() on the POI's stored address and returns up to N
  // candidates with rating + lat/lng + the address as Tiger normalized it +
  // the distance from the POI's current stored location. Used by the
  // Geocodes modal so the operator can validate or override the location.
  app.get('/admin/salem/lint/poi/:id/geocode-candidates', requirePg, async (req, res) => {
    try {
      const id = req.params.id;
      if (!id) return res.status(400).json({ error: 'id is required' });
      const limit = Math.min(parseInt(req.query.limit || '5', 10) || 5, 10);

      const { rows: poiRows } = await pgPool.query(
        `SELECT id, name, lat, lng, address, location_status, location_verified_at
         FROM salem_pois WHERE id = $1`,
        [id],
      );
      if (poiRows.length === 0) return res.status(404).json({ error: 'POI not found' });
      const poi = poiRows[0];

      // Sibling duplicates within 15m (same threshold as the dupes lint check),
      // excluding this POI AND soft-deleted ones. Operator rule (S188):
      // soft-deleted are footnotes — don't surface them as decision targets,
      // and don't spend Tiger budget geocoding their addresses.
      const DUPE_RADIUS_M = 15;
      const latBox = DUPE_RADIUS_M / 111000.0;
      const lngBox = DUPE_RADIUS_M / 60000.0;
      const { rows: dupeRows } = await pgPool.query(`
        SELECT id, name, lat, lng, address, location_status, deleted_at,
               6371000.0 * 2.0 * ASIN(SQRT(
                 POWER(SIN(RADIANS(lat - $2) / 2.0), 2) +
                 COS(RADIANS($2)) * COS(RADIANS(lat)) *
                 POWER(SIN(RADIANS(lng - $3) / 2.0), 2)
               )) AS distance_m
        FROM salem_pois
        WHERE id <> $1
          AND deleted_at IS NULL
          AND ABS(lat - $2) <= $4
          AND ABS(lng - $3) <= $5
          AND 6371000.0 * 2.0 * ASIN(SQRT(
                POWER(SIN(RADIANS(lat - $2) / 2.0), 2) +
                COS(RADIANS($2)) * COS(RADIANS(lat)) *
                POWER(SIN(RADIANS(lng - $3) / 2.0), 2)
              )) <= $6
        ORDER BY distance_m ASC
        LIMIT 25
      `, [id, poi.lat, poi.lng, latBox, lngBox, DUPE_RADIUS_M]);
      const duplicates = dupeRows.map(r => ({
        id: r.id,
        name: r.name,
        lat: parseFloat(r.lat),
        lng: parseFloat(r.lng),
        address: r.address || null,
        location_status: r.location_status || null,
        deleted_at: r.deleted_at || null,
        distance_m: Math.round(parseFloat(r.distance_m)),
      }));
      // Load operator-flagged "this candidate is wrong" entries for the focal
      // and every dupe so we can filter Tiger results before returning. Lat/lng
      // stored at 5-decimal precision; we match the same way.
      const blacklistIds = [poi.id, ...duplicates.map(d => d.id)];
      const { rows: blacklistRows } = await pgPool.query(
        `SELECT poi_id, lat::text AS lat, lng::text AS lng
           FROM salem_geocode_blacklist
          WHERE poi_id = ANY($1::text[])`,
        [blacklistIds],
      );
      const blacklistByPoi = new Map(); // poi_id → Set<"lat,lng">
      for (const b of blacklistRows) {
        const key = `${parseFloat(b.lat).toFixed(5)},${parseFloat(b.lng).toFixed(5)}`;
        if (!blacklistByPoi.has(b.poi_id)) blacklistByPoi.set(b.poi_id, new Set());
        blacklistByPoi.get(b.poi_id).add(key);
      }
      const filterBlacklisted = (sourcePoiId, cands) => {
        const set = blacklistByPoi.get(sourcePoiId);
        if (!set || set.size === 0) return cands;
        return cands.filter(c => !set.has(`${c.lat.toFixed(5)},${c.lng.toFixed(5)}`));
      };

      if (!poi.address || poi.address.length < 5) {
        return res.json({
          poi: { id: poi.id, name: poi.name, lat: poi.lat, lng: poi.lng, address: poi.address || null,
                 location_status: poi.location_status, location_verified_at: poi.location_verified_at },
          candidates: [],
          duplicates,
          warning: 'POI has no usable address — Tiger geocoder cannot run.',
        });
      }

      const tiger = tigerPool();
      let candidates = [];
      const tigerWarnings = [];
      const dupeWarnings = []; // collected across all dupe geocodes
      // Wall-clock budget for the whole geocode-candidates call. Once we burn
      // through this, remaining dupes are skipped with a stub warning so the
      // modal never hangs waiting on Tiger.
      const WALL_BUDGET_MS = 20_000;
      const startedAt = Date.now();
      const timeLeft = () => WALL_BUDGET_MS - (Date.now() - startedAt);
      try {
        // One pooled client for the focal POI + every duplicate. Tiger connect
        // overhead dominates each call so reusing the client is cheaper than
        // re-borrowing per address; statement_timeout is set once.
        const client = await tiger.connect();
        try {
          // Per-call cap dropped to 5s. With up to 25 dupes the focal+dupes
          // would otherwise run 26 × 15s = 6.5 minutes worst case.
          await client.query("SET statement_timeout = '5s'");

          const focalRes = await geocodeOneAddress(client, poi.address, poi.lat, poi.lng, limit);
          candidates = filterBlacklisted(poi.id, focalRes.candidates);
          tigerWarnings.push(...focalRes.warnings);

          // Geocode each dupe's address. Cap candidate count per dupe at 3 to
          // keep the modal scannable and the response under a few KB even
          // for a 5-way cluster.
          for (const d of duplicates) {
            if (!d.address || d.address.length < 5) {
              d.candidates = [];
              d.geocode_warning = 'no usable address';
              continue;
            }
            if (timeLeft() <= 1000) {
              d.candidates = [];
              d.geocode_warning = 'skipped — modal time budget exhausted';
              continue;
            }
            const r = await geocodeOneAddress(client, d.address, d.lat, d.lng, 3);
            d.candidates = filterBlacklisted(d.id, r.candidates);
            if (r.warnings.length > 0) {
              d.geocode_warning = `Tiger timed out on ${r.warnings.length} variant(s) — no street-level match.`;
              dupeWarnings.push(...r.warnings);
            }
          }
        } finally {
          try { await client.query("SET statement_timeout = 0"); } catch {}
          client.release();
        }
      } catch (e) {
        console.error('[admin-lint] geocode-candidates tiger query failed:', e.message);
        return res.status(502).json({
          error: `Tiger geocoder failed: ${e.message}`,
          poi: { id: poi.id, name: poi.name, lat: poi.lat, lng: poi.lng, address: poi.address },
          candidates: [],
          duplicates,
        });
      }

      // Best match across the cluster — lowest Tiger rating wins; ties
      // broken by smallest distance_m from that candidate's source POI
      // (i.e. the geocode landed nearest to where the POI is stored,
      // implying the address+stored coord agree most strongly).
      const bestMatch = pickBestMatch(poi, candidates, duplicates);

      res.json({
        poi: {
          id: poi.id,
          name: poi.name,
          lat: poi.lat,
          lng: poi.lng,
          address: poi.address,
          location_status: poi.location_status,
          location_verified_at: poi.location_verified_at,
        },
        candidates,
        duplicates,
        best_match: bestMatch,
        warning: (tigerWarnings.length + dupeWarnings.length) > 0
          ? `Tiger geocoder timed out on ${tigerWarnings.length + dupeWarnings.length} variant(s). Salem street data is loaded but house-number-level lookups can be slow when the exact number is missing from the addr table. You can validate the current location manually below.`
          : undefined,
      });
    } catch (err) {
      console.error('[admin-lint] geocode-candidates error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // POST /admin/salem/lint/poi/:id/mark-verified
  // Mark the POI's current lat/lng as the verified location without changing
  // the coordinates. Sets location_status='verified' and stamps
  // location_verified_at. Used by the Geocodes modal "Validate Current
  // Location" button.
  app.post('/admin/salem/lint/poi/:id/mark-verified', requirePg, async (req, res) => {
    try {
      const id = req.params.id;
      if (!id) return res.status(400).json({ error: 'id is required' });
      const { rows } = await pgPool.query(
        `UPDATE salem_pois
            SET location_status = 'verified',
                location_verified_at = NOW(),
                location_truth_of_record = TRUE,
                admin_dirty = TRUE,
                admin_dirty_at = NOW(),
                updated_at = NOW()
          WHERE id = $1 AND deleted_at IS NULL
          RETURNING id, lat, lng, location_status, location_verified_at`,
        [id],
      );
      if (rows.length === 0) return res.status(404).json({ error: 'POI not found or soft-deleted' });
      res.json(rows[0]);
    } catch (err) {
      console.error('[admin-lint] mark-verified error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── Geocode-candidate blacklist ───────────────────────────────────────────
  // POST /admin/salem/lint/poi/:id/blacklist-candidate  body: { lat, lng, reason? }
  // Marks one Tiger geocode candidate as wrong for this POI so it stops
  // appearing in the modal. Idempotent ON CONFLICT.
  app.post('/admin/salem/lint/poi/:id/blacklist-candidate', requirePg, async (req, res) => {
    try {
      const id = req.params.id;
      const { lat, lng, reason } = req.body || {};
      if (!id) return res.status(400).json({ error: 'id is required' });
      if (typeof lat !== 'number' || typeof lng !== 'number') {
        return res.status(400).json({ error: 'lat and lng (numbers) are required' });
      }
      const { rows } = await pgPool.query(
        `INSERT INTO salem_geocode_blacklist (poi_id, lat, lng, reason, created_at)
         VALUES ($1, ROUND($2::numeric, 5), ROUND($3::numeric, 5), $4, NOW())
         ON CONFLICT (poi_id, lat, lng)
           DO UPDATE SET reason = EXCLUDED.reason, created_at = NOW()
         RETURNING poi_id, lat, lng, reason, created_at`,
        [id, lat, lng, reason || null],
      );
      res.json(rows[0]);
    } catch (err) {
      console.error('[admin-lint] blacklist-candidate error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // DELETE /admin/salem/lint/poi/:id/blacklist-candidate  body: { lat, lng }
  // Restores a previously blacklisted candidate.
  app.delete('/admin/salem/lint/poi/:id/blacklist-candidate', requirePg, async (req, res) => {
    try {
      const id = req.params.id;
      const { lat, lng } = req.body || {};
      if (!id) return res.status(400).json({ error: 'id is required' });
      if (typeof lat !== 'number' || typeof lng !== 'number') {
        return res.status(400).json({ error: 'lat and lng (numbers) are required' });
      }
      const { rowCount } = await pgPool.query(
        `DELETE FROM salem_geocode_blacklist
          WHERE poi_id = $1
            AND lat = ROUND($2::numeric, 5)
            AND lng = ROUND($3::numeric, 5)`,
        [id, lat, lng],
      );
      res.json({ deleted: rowCount });
    } catch (err) {
      console.error('[admin-lint] unblacklist-candidate error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── Suppression endpoints ─────────────────────────────────────────────────
  // POST /admin/salem/lint/poi/:id/suppress  body: { check_id, reason? }
  // Marks a POI as a known false-positive for a given lint check so it stops
  // appearing on the lint tab. Idempotent (ON CONFLICT DO UPDATE).
  app.post('/admin/salem/lint/poi/:id/suppress', requirePg, async (req, res) => {
    try {
      const id = req.params.id;
      const { check_id, reason } = req.body || {};
      if (!id || !check_id) return res.status(400).json({ error: 'id and check_id are required' });
      const { rows } = await pgPool.query(
        `INSERT INTO salem_lint_suppressions (poi_id, check_id, reason, suppressed_at)
         VALUES ($1, $2, $3, NOW())
         ON CONFLICT (poi_id, check_id)
           DO UPDATE SET reason = EXCLUDED.reason, suppressed_at = NOW()
         RETURNING poi_id, check_id, reason, suppressed_at`,
        [id, check_id, reason || null],
      );
      res.json(rows[0]);
    } catch (err) {
      console.error('[admin-lint] suppress error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // DELETE /admin/salem/lint/poi/:id/suppress/:check_id
  // Removes a suppression — POI will start appearing in the check again.
  app.delete('/admin/salem/lint/poi/:id/suppress/:check_id', requirePg, async (req, res) => {
    try {
      const { id, check_id } = req.params;
      const { rowCount } = await pgPool.query(
        `DELETE FROM salem_lint_suppressions WHERE poi_id = $1 AND check_id = $2`,
        [id, check_id],
      );
      res.json({ deleted: rowCount });
    } catch (err) {
      console.error('[admin-lint] unsuppress error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // GET /admin/salem/lint/suppressions
  // Returns all current suppressions, optionally filtered by check_id.
  app.get('/admin/salem/lint/suppressions', requirePg, async (req, res) => {
    try {
      const { check_id } = req.query;
      const sql = check_id
        ? `SELECT s.poi_id, s.check_id, s.reason, s.suppressed_at, p.name AS poi_name, p.lat, p.lng
             FROM salem_lint_suppressions s LEFT JOIN salem_pois p ON p.id = s.poi_id
             WHERE s.check_id = $1
             ORDER BY s.suppressed_at DESC`
        : `SELECT s.poi_id, s.check_id, s.reason, s.suppressed_at, p.name AS poi_name, p.lat, p.lng
             FROM salem_lint_suppressions s LEFT JOIN salem_pois p ON p.id = s.poi_id
             ORDER BY s.check_id, s.suppressed_at DESC`;
      const params = check_id ? [check_id] : [];
      const { rows } = await pgPool.query(sql, params);
      res.json({ count: rows.length, suppressions: rows });
    } catch (err) {
      console.error('[admin-lint] list suppressions error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // GET /admin/salem/lint/address-geocode/status
  app.get('/admin/salem/lint/address-geocode/status', async (req, res) => {
    res.json({
      state: addrGeocodeState.state,
      progress: addrGeocodeState.progress,
      total: addrGeocodeState.total,
      started_at: addrGeocodeState.startedAt,
      finished_at: addrGeocodeState.finishedAt,
      error: addrGeocodeState.error,
      items: addrGeocodeState.state === 'done' ? addrGeocodeState.items : null,
    });
  });
};
