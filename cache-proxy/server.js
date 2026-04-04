/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module server.js';

const express = require('express');
const { Pool } = require('pg');
const http = require('http');
const { Server: SocketServer } = require('socket.io');

const cors = require('cors');
const app = express();
app.use(cors({ origin: true, credentials: true }));
const server = http.createServer(app);
const io = new SocketServer(server, { cors: { origin: '*' } });
const PORT = 4300;

// ── Parse request bodies ─────────────────────────────────────────────────────
app.use(express.urlencoded({ extended: true, limit: '1mb' }));
app.use(express.json({ limit: '16kb' }));

// ── PostgreSQL pool (optional — /db/* endpoints require DATABASE_URL) ────────
let pgPool = null;
if (process.env.DATABASE_URL) {
  pgPool = new Pool({
    connectionString: process.env.DATABASE_URL,
    max: 5,
    connectionTimeoutMillis: 5000,
  });
  pgPool.on('error', (err) => console.error('[PG Pool] Unexpected error:', err.message));
}

function requirePg(req, res, next) {
  if (!pgPool) return res.status(503).json({ error: 'Database not configured (set DATABASE_URL)' });
  next();
}

// ── Load foundational modules ────────────────────────────────────────────────
const config = require('./lib/config');
const cacheModule = require('./lib/cache');
const opensky = require('./lib/opensky');

// ── Build shared deps object ─────────────────────────────────────────────────
const deps = {
  pgPool,
  requirePg,
  io,
  // Config
  JWT_SECRET: config.JWT_SECRET,
  JWT_ACCESS_EXPIRY: config.JWT_ACCESS_EXPIRY,
  JWT_REFRESH_DAYS: config.JWT_REFRESH_DAYS,
  authLimiter: config.authLimiter,
  commentLimiter: config.commentLimiter,
  roomCreateLimiter: config.roomCreateLimiter,
  // Cache
  cache: cacheModule.cache,
  stats: cacheModule.stats,
  radiusHints: cacheModule.radiusHints,
  importBuffer: cacheModule.importBuffer,
  contentHashes: cacheModule.contentHashes,
  cacheGet: cacheModule.cacheGet,
  cacheSet: cacheModule.cacheSet,
  bufferOverpassElements: cacheModule.bufferOverpassElements,
  drainImportBuffer: cacheModule.drainImportBuffer,
  computeElementHash: cacheModule.computeElementHash,
  snapBbox: cacheModule.snapBbox,
  log: cacheModule.log,
  getRadiusHint: cacheModule.getRadiusHint,
  adjustRadiusHint: cacheModule.adjustRadiusHint,
  gridKey: cacheModule.gridKey,
  saveCache: cacheModule.saveCache,
  CACHE_FILE: cacheModule.CACHE_FILE,
  MAX_CACHE_ENTRIES: cacheModule.MAX_CACHE_ENTRIES,
  RADIUS_HINTS_FILE: cacheModule.RADIUS_HINTS_FILE,
  // OpenSky
  getOpenskyToken: opensky.getOpenskyToken,
  openskyConfigured: opensky.openskyConfigured,
  openskyRateState: opensky.openskyRateState,
  openskyCanRequest: opensky.openskyCanRequest,
  openskyRecordRequest: opensky.openskyRecordRequest,
  openskyRecord429: opensky.openskyRecord429,
  openskyRecordSuccess: opensky.openskyRecordSuccess,
  OPENSKY_EFFECTIVE_LIMIT: opensky.OPENSKY_EFFECTIVE_LIMIT,
  OPENSKY_MIN_INTERVAL_MS: opensky.OPENSKY_MIN_INTERVAL_MS,
};

// ── Register route modules (order matters) ───────────────────────────────────

// Simple proxy routes (no deps beyond cache/stats)
require('./lib/proxy-get')(app, deps);
require('./lib/metar')(app, deps);
require('./lib/geocode')(app, deps);
require('./lib/mbta')(app, deps);
require('./lib/webcams')(app, deps);
require('./lib/geofences')(app, deps);

// POI cache + radius hints
require('./lib/pois')(app, deps);

// Import (schedules timers, returns import state accessor)
const importModule = require('./lib/import')(app, deps);
deps.getImportState = importModule.getImportState;

// DB-backed endpoints
require('./lib/db-pois')(app, deps);
require('./lib/db-aircraft')(app, deps);

// Aircraft (needs opensky + cache)
require('./lib/aircraft')(app, deps);

// Quick-drain: import buffered POIs 2s after Overpass results arrive
// so POIs appear on map immediately instead of waiting 15 minutes
if (importModule.runImport) {
  let quickImportTimer = null;
  const origBuffer = deps.bufferOverpassElements;
  deps.bufferOverpassElements = function(jsonBody) {
    const result = origBuffer(jsonBody);
    if (result.buffered > 0) {
      clearTimeout(quickImportTimer);
      quickImportTimer = setTimeout(() => {
        console.log('[DB Import] Quick drain — importing buffered elements...');
        importModule.runImport().catch(err => console.error('[DB Import] Quick drain error:', err.message));
      }, 2000);
    }
    return result;
  };
}

// Scan cells (coverage tracking — must load before overpass)
const scanCellsModule = require('./lib/scan-cells')(app, deps);
deps.scanCells = scanCellsModule.scanCells;
deps.checkCoverage = scanCellsModule.checkCoverage;
deps.markScanned = scanCellsModule.markScanned;
deps.collectPoisInRadius = scanCellsModule.collectPoisInRadius;
deps.SCAN_CELLS_FILE = scanCellsModule.SCAN_CELLS_FILE;

// Overpass (needs cache + POI cache + scan cells; returns queue length accessor)
const overpassModule = require('./lib/overpass')(app, deps);
deps.getOverpassQueueLength = overpassModule.getOverpassQueueLength;

// Complex routes
require('./lib/tfr')(app, deps);
require('./lib/zones')(app, deps);
require('./lib/weather')(app, deps);

// Auth (must register before comments/chat — exports requireAuth middleware)
const authModule = require('./lib/auth')(app, deps);
deps.requireAuth = authModule.requireAuth;
deps.sanitizeText = authModule.sanitizeText;
deps.sanitizeMultiline = authModule.sanitizeMultiline;

// Social (depends on auth exports)
require('./lib/comments')(app, deps);
const chatModule = require('./lib/chat')(app, deps);

// Salem content (backward compatible — all routes under /salem/*)
require('./lib/salem')(app, deps);

// Admin (depends on import + overpass state)
require('./lib/admin')(app, deps);

// ── Start ───────────────────────────────────────────────────────────────────

server.listen(PORT, '0.0.0.0', () => {
  chatModule.ensureGlobalRoom();
  console.log(`Cache proxy listening on http://0.0.0.0:${PORT}`);
  console.log('Routes: POST /overpass, GET /earthquakes, GET /nws-alerts, GET /metar, GET /aircraft, GET /webcams, GET /weather, GET /tfrs');
  console.log('Zones:  GET /cameras, GET /schools, GET /flood-zones, GET /crossings');
  console.log('GeoDb:  GET /geofences/catalog, GET /geofences/database/:id/download');
  console.log('Radius: GET /radius-hint, POST /radius-hint, GET /radius-hints');
  console.log('POIs:   GET /pois/stats, GET /pois/export, GET /pois/bbox, GET /poi/:type/:id (PostgreSQL-backed)');
  console.log('DB:     GET /db/pois/search, /db/pois/nearby, /db/poi/:type/:id, /db/pois/stats, /db/pois/categories, /db/pois/coverage');
  console.log('Import: POST /db/import, GET /db/import/status');
  console.log(`        PostgreSQL: ${pgPool ? 'connected' : 'not configured (set DATABASE_URL)'}`);
  console.log(`        Auto-import: ${pgPool ? 'enabled — every 15min (initial in 30s)' : 'disabled (no DATABASE_URL)'}`);
  console.log(`        OpenSky:    ${opensky.openskyConfigured ? 'OAuth2 configured' : 'anonymous — set OPENSKY_CLIENT_ID + OPENSKY_CLIENT_SECRET'}`);
  console.log(`        OpenSky:    rate limiter active — ${opensky.OPENSKY_EFFECTIVE_LIMIT} req/day (${opensky.OPENSKY_DAILY_LIMIT} limit × ${opensky.OPENSKY_SAFETY_MARGIN} safety), min interval ${opensky.OPENSKY_MIN_INTERVAL_MS}ms`);
  console.log('Auth:   POST /auth/register, /auth/login, /auth/refresh, /auth/logout, GET /auth/me');
  console.log('Chat:   GET /chat/rooms, POST /chat/rooms, GET /chat/rooms/:id/messages, Socket.IO');
  console.log('Social: GET/POST /comments/:osm_type/:osm_id, POST /comments/:id/vote, DELETE /comments/:id');
  console.log(`        JWT: ${process.env.JWT_SECRET ? 'secret configured' : 'WARNING — using random secret'}`);
  console.log('Scan:   GET /scan-cells');
  console.log('Salem:  GET /salem/pois, /salem/businesses, /salem/figures, /salem/timeline, /salem/sources, /salem/tours, /salem/events, /salem/sync, /salem/stats');
  console.log('Admin:  GET /cache/stats, POST /cache/clear');
});
