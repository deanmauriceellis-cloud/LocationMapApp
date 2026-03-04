/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module cache.js';

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

// ── In-memory cache with disk persistence ───────────────────────────────────

const CACHE_FILE = path.join(__dirname, '..', 'cache-data.json');
const cache = new Map();   // key → { data, headers, timestamp }
let stats = { hits: 0, misses: 0 };
let savePending = false;

// ── Radius hints ────────────────────────────────────────────────────────────

const RADIUS_HINTS_FILE = path.join(__dirname, '..', 'radius-hints.json');
const radiusHints = new Map();   // "LAT3:LON3" → { radius, updatedAt }
const DEFAULT_RADIUS = 3000;
const MIN_RADIUS = 100;
const MAX_RADIUS = 15000;
const RADIUS_SHRINK = 0.70;
const RADIUS_GROW   = 1.30;
const MIN_USEFUL_POI = 5;
let hintSavePending = false;

function loadRadiusHints() {
  try {
    if (!fs.existsSync(RADIUS_HINTS_FILE)) return;
    const raw = fs.readFileSync(RADIUS_HINTS_FILE, 'utf8');
    const entries = JSON.parse(raw);
    for (const [key, value] of entries) {
      radiusHints.set(key, value);
    }
    console.log(`Loaded ${radiusHints.size} radius hints from disk`);
  } catch (err) {
    console.error('Failed to load radius hints from disk:', err.message);
  }
}

function saveRadiusHints() {
  if (hintSavePending) return;
  hintSavePending = true;
  setTimeout(() => {
    hintSavePending = false;
    try {
      const entries = [...radiusHints.entries()];
      fs.writeFileSync(RADIUS_HINTS_FILE, JSON.stringify(entries));
      console.log(`Saved ${entries.length} radius hints to disk`);
    } catch (err) {
      console.error('Failed to save radius hints to disk:', err.message);
    }
  }, 2000);
}

function gridKey(lat, lon) {
  return `${parseFloat(lat).toFixed(3)}:${parseFloat(lon).toFixed(3)}`;
}

function getRadiusHint(lat, lon) {
  const key = gridKey(lat, lon);
  const hint = radiusHints.get(key);
  if (hint) return hint.radius;

  // Fuzzy: find nearest hint within 20km (~0.1798°)
  const latF = parseFloat(lat);
  const lonF = parseFloat(lon);
  const TWENTY_KM_DEG = 0.1798;
  let nearest = null;
  let nearestDist = Infinity;

  for (const [k, v] of radiusHints) {
    const parts = k.split(':');
    const hLat = parseFloat(parts[0]);
    const hLon = parseFloat(parts[1]);
    const dLat = hLat - latF;
    const dLon = (hLon - lonF) * Math.cos(latF * Math.PI / 180);
    const dist = Math.sqrt(dLat * dLat + dLon * dLon);
    if (dist <= TWENTY_KM_DEG && dist < nearestDist) {
      nearest = v;
      nearestDist = dist;
    }
  }

  if (nearest) {
    const km = (nearestDist / 0.008993).toFixed(1);
    console.log(`[Radius] Fuzzy hit for ${key} — nearest hint ${km}km away → ${nearest.radius}m`);
    return nearest.radius;
  }

  return DEFAULT_RADIUS;
}

function adjustRadiusHint(lat, lon, resultCount, error, capped) {
  const key = gridKey(lat, lon);
  const current = radiusHints.get(key);
  let radius = current ? current.radius : DEFAULT_RADIUS;

  if (error) {
    // Errors (504, 429) are transient — don't change the radius hint
    console.log(`[Radius] ${key} error — keeping at ${radius}m (transient)`);
    return radius;
  }

  if (capped) {
    radius = Math.round(radius * 0.5);
    console.log(`[Radius] ${key} CAPPED (${resultCount} POIs) → halve to ${radius}m`);
  } else if (resultCount < MIN_USEFUL_POI) {
    radius = Math.round(radius * RADIUS_GROW);
    console.log(`[Radius] ${key} only ${resultCount} POIs → grow to ${radius}m`);
  } else {
    console.log(`[Radius] ${key} ${resultCount} POIs — confirmed at ${radius}m`);
  }

  radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
  radiusHints.set(key, { radius, updatedAt: Date.now() });
  saveRadiusHints();
  return radius;
}

function loadCache() {
  try {
    if (!fs.existsSync(CACHE_FILE)) return;
    const raw = fs.readFileSync(CACHE_FILE, 'utf8');
    const entries = JSON.parse(raw);
    for (const [key, value] of entries) {
      cache.set(key, value);
    }
    console.log(`Loaded ${cache.size} cache entries from disk`);
  } catch (err) {
    console.error('Failed to load cache from disk:', err.message);
  }
}

function saveCache() {
  if (savePending) return;
  savePending = true;
  // Debounce: write 2s after the last change to batch rapid inserts
  setTimeout(() => {
    savePending = false;
    try {
      const entries = [...cache.entries()];
      fs.writeFileSync(CACHE_FILE, JSON.stringify(entries));
      console.log(`Saved ${entries.length} cache entries to disk (${(fs.statSync(CACHE_FILE).size / 1024 / 1024).toFixed(1)}MB)`);
    } catch (err) {
      console.error('Failed to save cache to disk:', err.message);
    }
  }, 2000);
}

function cacheGet(key, ttlMs) {
  const entry = cache.get(key);
  if (!entry) return null;
  if (Date.now() - entry.timestamp > ttlMs) {
    cache.delete(key);
    saveCache();
    return null;
  }
  return entry;
}

function cacheSet(key, data, headers) {
  cache.set(key, { data, headers, timestamp: Date.now() });
  saveCache();
}

// ── Individual POI cache ─────────────────────────────────────────────────────

const POI_CACHE_FILE = path.join(__dirname, '..', 'poi-cache.json');
const poiCache = new Map();   // "poi:TYPE:ID" → { element, firstSeen, lastSeen }
let poiSavePending = false;

function loadPoiCache() {
  try {
    if (!fs.existsSync(POI_CACHE_FILE)) return;
    const raw = fs.readFileSync(POI_CACHE_FILE, 'utf8');
    const entries = JSON.parse(raw);
    for (const [key, value] of entries) {
      poiCache.set(key, value);
    }
    console.log(`Loaded ${poiCache.size} individual POIs from disk`);
  } catch (err) {
    console.error('Failed to load POI cache from disk:', err.message);
  }
}

function savePoiCache() {
  if (poiSavePending) return;
  poiSavePending = true;
  setTimeout(() => {
    poiSavePending = false;
    try {
      const entries = [...poiCache.entries()];
      fs.writeFileSync(POI_CACHE_FILE, JSON.stringify(entries));
      console.log(`Saved ${entries.length} POIs to disk (${(fs.statSync(POI_CACHE_FILE).size / 1024 / 1024).toFixed(1)}MB)`);
    } catch (err) {
      console.error('Failed to save POI cache to disk:', err.message);
    }
  }, 2000);
}

function cacheIndividualPois(jsonBody) {
  try {
    const parsed = JSON.parse(jsonBody);
    if (!parsed.elements || !Array.isArray(parsed.elements)) return { added: 0, updated: 0 };

    const now = Date.now();
    let added = 0;
    let updated = 0;

    for (const element of parsed.elements) {
      if (!element.type || !element.id) continue;
      const key = `poi:${element.type}:${element.id}`;
      const existing = poiCache.get(key);

      if (existing) {
        existing.element = element;
        existing.lastSeen = now;
        updated++;
      } else {
        poiCache.set(key, { element, firstSeen: now, lastSeen: now });
        added++;
      }
    }

    console.log(`[POI Cache] +${added} new, ${updated} updated (${poiCache.size} total)`);
    if (added > 0 || updated > 0) savePoiCache();
    return { added, updated };
  } catch (err) {
    console.error('[POI Cache] Failed to parse response:', err.message);
    return { added: 0, updated: 0 };
  }
}

// ── Bbox snapping (improve cache hit rate for scroll-heavy layers) ───────────

/**
 * Round bbox coordinates to a grid so small scrolls reuse the same cache key.
 * precision=2 → snap to 0.01° (~1.1 km), precision=1 → snap to 0.1° (~11 km).
 * South/west snap down, north/east snap up to ensure the snapped bbox fully
 * contains the original viewport.
 */
function snapBbox(s, w, n, e, precision = 2) {
  const f = Math.pow(10, precision);
  return {
    s: Math.floor(s * f) / f,
    w: Math.floor(w * f) / f,
    n: Math.ceil(n * f) / f,
    e: Math.ceil(e * f) / f,
  };
}

// ── Logging ─────────────────────────────────────────────────────────────────

function log(endpoint, hit, upstreamMs, extra) {
  const ts = new Date().toISOString();
  const status = hit ? 'HIT' : 'MISS';
  const timing = hit ? '' : ` upstream=${upstreamMs}ms`;
  const suffix = extra ? ` [${extra}]` : '';
  console.log(`[${ts}] ${endpoint} ${status}${timing} (${cache.size} cached)${suffix}`);
}

// ── Content hash for delta detection ────────────────────────────────────────

/**
 * Compute a fast content hash of Overpass elements for delta detection.
 * Hash is based on sorted element type+id pairs — stable across re-fetches of identical data.
 */
function computeElementHash(jsonBody) {
  try {
    const parsed = JSON.parse(jsonBody);
    if (!parsed.elements || !Array.isArray(parsed.elements)) return null;
    const ids = parsed.elements
      .filter(e => e.type && e.id)
      .map(e => `${e.type}:${e.id}`)
      .sort();
    return crypto.createHash('md5').update(ids.join(',')).digest('hex');
  } catch (_) {
    return null;
  }
}

// Content hash store — maps cache key to last known element hash
const contentHashes = new Map();

// ── Load all caches from disk ────────────────────────────────────────────────

loadCache();
loadRadiusHints();
loadPoiCache();

module.exports = {
  cache,
  stats,
  radiusHints,
  poiCache,
  contentHashes,
  cacheGet,
  cacheSet,
  cacheIndividualPois,
  computeElementHash,
  snapBbox,
  log,
  getRadiusHint,
  adjustRadiusHint,
  gridKey,
  saveCache,
  CACHE_FILE,
  POI_CACHE_FILE,
  RADIUS_HINTS_FILE,
};
