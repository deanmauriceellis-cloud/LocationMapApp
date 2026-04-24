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

const TILE_DB_PATH = path.resolve(__dirname, '../../tools/tile-bake/dist/salem_tiles.sqlite');

const ALLOWED_PROVIDERS = new Set(['Salem-Custom', 'Mapnik', 'Esri-WorldImagery']);

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

module.exports = function(app /*, deps */) {
  app.get('/admin/tiles/:provider/:z/:x/:y', (req, res) => {
    const { provider } = req.params;
    if (!ALLOWED_PROVIDERS.has(provider)) {
      return res.status(400).json({ error: `unknown provider '${provider}'; allowed: ${[...ALLOWED_PROVIDERS].join(', ')}` });
    }

    const z = Number.parseInt(req.params.z, 10);
    const x = Number.parseInt(req.params.x, 10);
    const yRaw = req.params.y;
    // Strip any file extension (Leaflet sometimes appends `.png`).
    const y = Number.parseInt(String(yRaw).replace(/\.[a-z0-9]+$/i, ''), 10);

    if (!Number.isFinite(z) || z < 0 || z > 25 || !Number.isFinite(x) || x < 0 || !Number.isFinite(y) || y < 0) {
      return res.status(400).json({ error: 'z, x, y must be non-negative integers with z <= 25' });
    }

    const db = openDb();
    if (!db) {
      return res.status(503).json({ error: 'tile archive unavailable' });
    }

    const key = osmdroidKey(z, x, y);
    let row;
    try {
      row = _stmt.get(key, provider);
    } catch (err) {
      console.error('[admin-tiles] query failed:', err.message);
      return res.status(500).json({ error: 'tile query failed' });
    }

    if (!row || !row.tile) {
      return res.status(404).end();
    }

    const buf = Buffer.isBuffer(row.tile) ? row.tile : Buffer.from(row.tile);
    res.setHeader('Content-Type', sniffContentType(buf));
    res.setHeader('Cache-Control', 'public, max-age=86400, immutable');
    res.setHeader('Content-Length', String(buf.length));
    res.send(buf);
  });
};

module.exports.MODULE_ID = MODULE_ID;
module.exports.osmdroidKey = osmdroidKey;
module.exports.ALLOWED_PROVIDERS = ALLOWED_PROVIDERS;
