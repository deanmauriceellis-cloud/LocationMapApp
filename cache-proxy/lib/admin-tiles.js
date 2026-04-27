/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Admin tile endpoint (Phase 9Y admin tooling, S160).
 *
 * Serves tiles out of tools/tile-bake/dist/salem_tiles.sqlite (the Witchy
 * tile bake, S158) so the web admin tool can render the same three offline
 * providers the phone app sees: Salem-Custom (Witchy), Mapnik, Esri-WorldImagery.
 *
 * Route:
 *   GET /admin/tiles/:provider/:z/:x/:y
 *     → 200 image/png | image/webp | image/jpeg (sniffed from magic bytes)
 *     → 404 when no tile exists at that (provider, z, x, y)
 *
 * Gated by the /admin Basic Auth middleware mounted in server.js.
 *
 * Storage schema (from tools/tile-bake/dist/salem_tiles.sqlite):
 *   CREATE TABLE tiles (key INTEGER, provider TEXT, tile BLOB,
 *                       PRIMARY KEY (key, provider));
 *
 * Key encoding: osmdroid's SqliteArchive format —
 *     key = (z << (z + z)) | (x << z) | y
 *   using BigInt because at z=19 the shift exceeds JS's safe-integer range.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module admin-tiles.js';

const path = require('path');
const fs = require('fs');
const Database = require('better-sqlite3');
const sharp = require('sharp');

// ─── Overzoom (S188) ─────────────────────────────────────────────────────────
// When a requested (z, x, y) tile isn't in the bundle, walk UP the pyramid
// (z-1, z-2, …) to find an ancestor that exists, then crop+resize the right
// sub-region to fill in. Then walk DOWN (z+1) and stitch 4 children if
// no ancestor is available — used when zoomed out beyond what was baked.
//
// Operator rule (S188): only Witchy/bundled tiles, no online fallback. If we
// have a tile at any zoom that covers the requested area, use it.

const OVERZOOM_MAX_UP = 8;     // walk up to 8 levels (1024× scale)
const OVERZOOM_MAX_DOWN = 2;   // walk down up to 2 levels (16 children stitched)
const OVERZOOM_CACHE_MAX = 500; // LRU max entries (resized tiles)
const OVERZOOM_CACHE = new Map(); // key="provider:z:x:y" → { buf, contentType }

function lruGet(key) {
  if (!OVERZOOM_CACHE.has(key)) return null;
  const v = OVERZOOM_CACHE.get(key);
  OVERZOOM_CACHE.delete(key);
  OVERZOOM_CACHE.set(key, v);
  return v;
}
function lruSet(key, value) {
  if (OVERZOOM_CACHE.has(key)) OVERZOOM_CACHE.delete(key);
  OVERZOOM_CACHE.set(key, value);
  if (OVERZOOM_CACHE.size > OVERZOOM_CACHE_MAX) {
    const oldest = OVERZOOM_CACHE.keys().next().value;
    OVERZOOM_CACHE.delete(oldest);
  }
}

const TILE_DB_PATH = path.resolve(__dirname, '../../tools/tile-bake/dist/salem_tiles.sqlite');

// S188 — operator rule: Witchy is the only basemap. Mapnik/Esri removed.
const ALLOWED_PROVIDERS = new Set(['Salem-Custom']);

function osmdroidKey(z, x, y) {
  const zb = BigInt(z);
  return (zb << (zb + zb)) | (BigInt(x) << zb) | BigInt(y);
}

function sniffContentType(buf) {
  if (!buf || buf.length < 12) return 'application/octet-stream';
  // PNG: 89 50 4E 47 0D 0A 1A 0A
  if (buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4E && buf[3] === 0x47) {
    return 'image/png';
  }
  // JPEG: FF D8 FF
  if (buf[0] === 0xFF && buf[1] === 0xD8 && buf[2] === 0xFF) {
    return 'image/jpeg';
  }
  // WebP: "RIFF" .... "WEBP"
  if (buf[0] === 0x52 && buf[1] === 0x49 && buf[2] === 0x46 && buf[3] === 0x46
      && buf[8] === 0x57 && buf[9] === 0x45 && buf[10] === 0x42 && buf[11] === 0x50) {
    return 'image/webp';
  }
  return 'application/octet-stream';
}

let _db = null;
let _stmt = null;
let _openFailed = false;

function openDb() {
  if (_db || _openFailed) return _db;
  try {
    if (!fs.existsSync(TILE_DB_PATH)) {
      console.warn(`[admin-tiles] tile archive not found at ${TILE_DB_PATH} — /admin/tiles/* will return 503 until it is baked`);
      _openFailed = true;
      return null;
    }
    _db = new Database(TILE_DB_PATH, { readonly: true, fileMustExist: true });
    // Use BigInt-aware binding because the key exceeds safe-integer range at z>=13.
    _db.defaultSafeIntegers(true);
    _stmt = _db.prepare('SELECT tile FROM tiles WHERE key = ? AND provider = ?');
    console.log(`[admin-tiles] opened ${TILE_DB_PATH}`);
  } catch (err) {
    console.error('[admin-tiles] failed to open tile archive:', err.message);
    _openFailed = true;
  }
  return _db;
}

function lookupExactTile(provider, z, x, y) {
  if (z < 0 || z > 25 || x < 0 || y < 0) return null;
  const max = 1 << z;
  if (x >= max || y >= max) return null;
  const key = osmdroidKey(z, x, y);
  let row;
  try { row = _stmt.get(key, provider); } catch { return null; }
  if (!row || !row.tile) return null;
  const buf = Buffer.isBuffer(row.tile) ? row.tile : Buffer.from(row.tile);
  return buf;
}

// Walk UP the pyramid (lower zoom = wider area per tile). When a higher-zoom
// tile is missing, an ancestor at z-dz covers the same area; we crop the
// matching sub-region and resize to 256×256.
async function overzoomFromAncestor(provider, z, x, y) {
  for (let dz = 1; dz <= OVERZOOM_MAX_UP && z - dz >= 0; dz++) {
    const az = z - dz;
    const ax = Math.floor(x / (1 << dz));
    const ay = Math.floor(y / (1 << dz));
    const ancestor = lookupExactTile(provider, az, ax, ay);
    if (!ancestor) continue;

    // Sub-region within the ancestor that corresponds to (x, y, z):
    //   tilesPerSide = 1 << dz   (each ancestor tile covers a tilesPerSide × tilesPerSide grid at zoom z)
    //   subSize      = 256 >> dz (pixel size of that sub-region in the ancestor)
    //   subX, subY   = local position of (x, y) within the ancestor's grid
    const tilesPerSide = 1 << dz;
    const subSize = 256 / tilesPerSide;
    const subX = (x % tilesPerSide) * subSize;
    const subY = (y % tilesPerSide) * subSize;

    try {
      // Sharp's extract requires integer coords; subSize is integer for dz<=8 (256/256=1).
      const sub = Math.max(1, Math.floor(subSize));
      const buf = await sharp(ancestor)
        .extract({ left: Math.floor(subX), top: Math.floor(subY), width: sub, height: sub })
        .resize(256, 256, { kernel: dz <= 2 ? 'lanczos3' : 'nearest' })
        .png({ compressionLevel: 6 })
        .toBuffer();
      return { buf, contentType: 'image/png', source: `overzoom-up-${dz}` };
    } catch (err) {
      console.warn(`[admin-tiles] overzoom-up dz=${dz} for ${provider}/${z}/${x}/${y} failed:`, err.message);
      // try next ancestor
    }
  }
  return null;
}

// Walk DOWN: stitch 2^dz × 2^dz children from a higher zoom into one tile.
// Only useful when we have detailed zoom but the operator zoomed out beyond
// what was baked at the requested (lower) zoom level.
async function overzoomFromChildren(provider, z, x, y) {
  for (let dz = 1; dz <= OVERZOOM_MAX_DOWN && z + dz <= 25; dz++) {
    const cz = z + dz;
    const grid = 1 << dz;
    const baseX = x * grid;
    const baseY = y * grid;
    // Probe one corner first; if no children exist there either, skip dz.
    if (!lookupExactTile(provider, cz, baseX, baseY)) continue;

    const childSize = Math.floor(256 / grid);
    const composites = [];
    let anyMissing = false;
    for (let cy = 0; cy < grid; cy++) {
      for (let cx = 0; cx < grid; cx++) {
        const child = lookupExactTile(provider, cz, baseX + cx, baseY + cy);
        if (!child) { anyMissing = true; continue; }
        try {
          const resized = await sharp(child)
            .resize(childSize, childSize, { kernel: 'lanczos3' })
            .png()
            .toBuffer();
          composites.push({ input: resized, top: cy * childSize, left: cx * childSize });
        } catch (err) {
          console.warn(`[admin-tiles] child resize failed:`, err.message);
        }
      }
    }
    if (composites.length === 0) continue;
    try {
      const buf = await sharp({
        create: { width: 256, height: 256, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } },
      })
        .composite(composites)
        .png({ compressionLevel: 6 })
        .toBuffer();
      return { buf, contentType: 'image/png', source: `overzoom-down-${dz}${anyMissing ? '-partial' : ''}` };
    } catch (err) {
      console.warn(`[admin-tiles] overzoom-down dz=${dz} stitch failed:`, err.message);
    }
  }
  return null;
}

module.exports = function(app /*, deps */) {
  app.get('/admin/tiles/:provider/:z/:x/:y', async (req, res) => {
    const { provider } = req.params;
    if (!ALLOWED_PROVIDERS.has(provider)) {
      return res.status(400).json({ error: `unknown provider '${provider}'; allowed: ${[...ALLOWED_PROVIDERS].join(', ')}` });
    }

    const z = Number.parseInt(req.params.z, 10);
    const x = Number.parseInt(req.params.x, 10);
    const yRaw = req.params.y;
    const y = Number.parseInt(String(yRaw).replace(/\.[a-z0-9]+$/i, ''), 10);

    if (!Number.isFinite(z) || z < 0 || z > 25 || !Number.isFinite(x) || x < 0 || !Number.isFinite(y) || y < 0) {
      return res.status(400).json({ error: 'z, x, y must be non-negative integers with z <= 25' });
    }

    const db = openDb();
    if (!db) {
      return res.status(503).json({ error: 'tile archive unavailable' });
    }

    // 1) Exact-zoom hit.
    const exact = lookupExactTile(provider, z, x, y);
    if (exact) {
      res.setHeader('Content-Type', sniffContentType(exact));
      res.setHeader('Cache-Control', 'public, max-age=86400, immutable');
      res.setHeader('Content-Length', String(exact.length));
      res.setHeader('X-Tile-Source', 'exact');
      return res.send(exact);
    }

    // 2) Cached overzoom result.
    const cacheKey = `${provider}:${z}:${x}:${y}`;
    const cached = lruGet(cacheKey);
    if (cached) {
      res.setHeader('Content-Type', cached.contentType);
      res.setHeader('Cache-Control', 'public, max-age=86400, immutable');
      res.setHeader('Content-Length', String(cached.buf.length));
      res.setHeader('X-Tile-Source', `${cached.source}-cached`);
      return res.send(cached.buf);
    }

    // 3) Overzoom — walk UP first (most common: zoomed in past what was baked).
    let result = await overzoomFromAncestor(provider, z, x, y);
    // 4) Then walk DOWN (zoomed out past what was baked).
    if (!result) result = await overzoomFromChildren(provider, z, x, y);

    if (!result) return res.status(404).end();

    lruSet(cacheKey, { buf: result.buf, contentType: result.contentType, source: result.source });
    res.setHeader('Content-Type', result.contentType);
    res.setHeader('Cache-Control', 'public, max-age=86400, immutable');
    res.setHeader('Content-Length', String(result.buf.length));
    res.setHeader('X-Tile-Source', result.source);
    res.send(result.buf);
  });
};

module.exports.MODULE_ID = MODULE_ID;
module.exports.osmdroidKey = osmdroidKey;
module.exports.ALLOWED_PROVIDERS = ALLOWED_PROVIDERS;
