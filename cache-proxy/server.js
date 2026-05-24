/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Destructive AI Gurus, LLC, 2026 - Module server.js';

// S223: load cache-proxy/.env so `npm start` works after a reboot without the
// spawning shell having to `set -a; source .env; set +a` first. Prior to this,
// admin-lint.js crashed on first DB query because DATABASE_URL was unset.
require('dotenv').config({ path: require('path').resolve(__dirname, '.env') });

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
app.use(express.json({ limit: '1mb' }));

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
const adminAuth = require('./lib/admin-auth');

// Validate admin auth config at startup. We do not refuse to start the proxy if
// ADMIN_USER/ADMIN_PASS are missing — public routes still work — but every
// /admin/* and /cache/clear request will return 503 until they're set.
const adminAuthConfigured = adminAuth.ensureConfigured();

// Mount Basic Auth on the admin namespace before any /admin/* routes register.
// /cache/clear (the historical unauthenticated admin route from S96 survey)
// is gated explicitly inside lib/admin.js via the requireBasicAuth dep below.
app.use('/admin', adminAuth.requireBasicAuth);

// /admin/ping smoke-test route — proves the middleware is mounted correctly.
app.get('/admin/ping', (req, res) => {
  res.json({ ok: true, route: '/admin/ping' });
});

// /admin/whoami (S290) — lets the web admin discover which role authenticated
// (full 'admin' vs restricted 'historian') so it can gate tabs + fields. Sits
// behind requireBasicAuth above, so req.adminRole is already set.
app.get('/admin/whoami', (req, res) => {
  res.json({ role: req.adminRole });
});

// Historian role-gate (S290) — block the restricted 'historian' login from every
// admin feature except POI content editing. Mounted here (after requireBasicAuth,
// before the route modules register) so it runs first on these prefixes. The
// 'admin' role passes through untouched. POI + category routes are intentionally
// NOT blocked wholesale — they carry finer per-route guards in their own modules
// (admin-pois.js scopes the historian's editable fields; admin-categories.js
// leaves GET open for the dropdowns but guards taxonomy writes).
[
  '/admin/salem/tours',
  '/admin/salem/collections',
  '/admin/salem/witch-trials',
  '/admin/salem/lint',
  '/admin/salem/auto-categorize',
  '/admin/salem/audit',
  '/admin/salem/field-edits',
  '/admin/splash',
  '/admin/super-admin',
  '/admin/tiles',
  '/admin/burst-photos',
  '/cache/stats',
  '/cache/clear',
].forEach((prefix) => app.use(prefix, adminAuth.requireFullAdmin));

// ── Build shared deps object ─────────────────────────────────────────────────
const deps = {
  pgPool,
  requirePg,
  io,
  // Admin auth
  requireBasicAuth: adminAuth.requireBasicAuth,
  requireFullAdmin: adminAuth.requireFullAdmin,
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

// Overpass (needs cache + POI cache + scan cells; returns queue length accessor).
// S242: per `feedback_no_osm_use_local_geo.md`, OSM is build-time only. Android
// has zero runtime callers (verified S242 code audit). Gated behind
// ENABLE_OVERPASS=true so the route can't accidentally be re-introduced into a
// runtime path. Flip the env var to revive admin-side OSM probing.
let overpassModule = null;
const OVERPASS_ENABLED = String(process.env.ENABLE_OVERPASS || '').toLowerCase() === 'true';
if (OVERPASS_ENABLED) {
  overpassModule = require('./lib/overpass')(app, deps);
  deps.getOverpassQueueLength = overpassModule.getOverpassQueueLength;
} else {
  // Stub queue-length accessor so admin code that reads it doesn't crash.
  deps.getOverpassQueueLength = () => 0;
}

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

// Chat is a post-V1 feature (S163 2026-04-23). Keep the module off by default
// so it can't throw "relation chat_rooms does not exist" at boot on a V1 DB
// that doesn't carry the chat schema. Flip ENABLE_CHAT=true to opt in.
let chatModule = null;
const CHAT_ENABLED = String(process.env.ENABLE_CHAT || '').toLowerCase() === 'true';
if (CHAT_ENABLED) {
  chatModule = require('./lib/chat')(app, deps);
}

// Salem content (backward compatible — all routes under /salem/*)
require('./lib/salem')(app, deps);

// Salem walking router (S177 P4) — same SQLite bundle the APK ships,
// optional ?source=live fall-through to TigerLine for verification.
// Capture the module's return so admin-tours can call the bundle router
// in-process (S183 — admin "Compute Route" tool for per-leg tour polylines).
const salemRouterModule = require('./lib/salem-router')(app, deps);
deps.salemRoute = salemRouterModule._route;
deps.salemBundle = salemRouterModule._bundle;
deps.salemRouteDiag = salemRouterModule._routeDiag;
deps.salemSnapEdge = salemRouterModule._snapEdge;
// S215 — edge-point routing: takes stop objects {lat, lng, edge_id?, edge_fraction?}.
deps.salemRouteEx = salemRouterModule._routeEx;

// Admin (depends on import + overpass state)
require('./lib/admin')(app, deps);

// Admin Mass-Edit endpoints (S241) — registered BEFORE admin-pois.js so the
// exact-match /admin/salem/pois/export-spreadsheet route wins over admin-pois's
// generic GET /admin/salem/pois/:id pattern (Express tries handlers in
// registration order).
require('./lib/admin-mass-edit')(app, deps);

// Admin POI write endpoints (Phase 9P.4) — gated by /admin Basic Auth
require('./lib/admin-pois')(app, deps);

// Admin Tour write endpoints (S174) — gated by /admin Basic Auth
require('./lib/admin-tours')(app, deps);

// Admin Collection endpoints (S268) — operator-tunable filters that generate
// per-user Katrina's Collections replacing the four stops-based tour UIs.
require('./lib/admin-collection')(app, deps);

// Admin Witch Trials content endpoints (Phase 9X.7) — gated by /admin Basic Auth
require('./lib/admin-witch-trials')(app, deps);

// Admin Lint endpoints (S187) — gated by /admin Basic Auth
require('./lib/admin-lint')(app, deps);

// Admin auto-categorize endpoints (S205) — SalemIntelligence-driven
// (cat, subcategory) suggestions for commercial POIs. Gated by /admin Basic Auth.
require('./lib/admin-suggest-categorization')(app, deps);

// Admin Audit endpoints (S196) — surfaces salem_audit_log + revert path
require('./lib/admin-audit')(app, deps);

// Admin POI taxonomy endpoints (S190) — categories + subcategories CRUD
require('./lib/admin-categories')(app, deps);

// Admin tile server (S160) — serves Witchy/Mapnik/Esri offline tiles out of
// tools/tile-bake/dist/salem_tiles.sqlite so the admin map matches what the
// phone app sees. Gated by /admin Basic Auth.
require('./lib/admin-tiles')(app, deps);

// TigerBase tile server (S252, post-V1) — serves CONUS TIGER/Line basemap
// out of layered PMTiles archives at tools/tigerbase/out/. Not gated by
// admin auth; consumed by web admin preview + (post-V1) the Android app
// when outside Salem geofence with internet.
require('./lib/tigerbase-tiles')(app, deps);

// Admin burst-photos endpoint (S229) — surfaces the GPS-burst camera album
// at /mnt/sdb-images/LMASalemPictures/ to the web admin so the operator can
// pin every shot on the map and spot-check POI / path alignment.
require('./lib/admin-burst-photos')(app, deps);

// Admin field-edits inbox (S229 Part C) — reads JSONL files written by the
// Android FieldEditManager, presents an Accept/Reject inbox, applies via the
// existing /admin/salem/pois/:id endpoints. The web admin is the gate — no
// edit hits the DB until the operator accepts it.
require('./lib/admin-field-edits')(app, deps);

// Admin splash-tree authoring (S231) — GET/PUT/POST /admin/splash/* for the
// decision-tree editor. Storage at cache-proxy/data/splash-tree-v1.json.
require('./lib/admin-splash-tree')(app, deps);

// SuperAdmin metadata endpoint (S248) — drives the post-V1 service console
// in the web admin. Pure metadata; the tab itself calls the existing
// /weather, /mbta/*, /aircraft, /metar, /webcams, /tfrs routes via the
// Vite /api proxy. Gated by /admin Basic Auth.
require('./lib/admin-super')(app, deps);

// ── Start ───────────────────────────────────────────────────────────────────

server.listen(PORT, '0.0.0.0', () => {
  if (chatModule) chatModule.ensureGlobalRoom();
  console.log(`Cache proxy listening on http://0.0.0.0:${PORT}`);
  if (overpassModule) {
    console.log('Routes: POST /overpass, GET /earthquakes, GET /nws-alerts, GET /metar, GET /aircraft, GET /webcams, GET /weather, GET /tfrs');
  } else {
    console.log('Routes: GET /earthquakes, GET /nws-alerts, GET /metar, GET /aircraft, GET /webcams, GET /weather, GET /tfrs');
    console.log('        /overpass disabled (S242 contract — set ENABLE_OVERPASS=true to opt in)');
  }
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
  if (chatModule) {
    console.log('Chat:   GET /chat/rooms, POST /chat/rooms, GET /chat/rooms/:id/messages, Socket.IO');
  } else {
    console.log('Chat:   disabled (post-V1 feature — set ENABLE_CHAT=true to opt in)');
  }
  console.log('Social: GET/POST /comments/:osm_type/:osm_id, POST /comments/:id/vote, DELETE /comments/:id');
  console.log(`        JWT: ${process.env.JWT_SECRET ? 'secret configured' : 'WARNING — using random secret'}`);
  console.log('Scan:   GET /scan-cells');
  console.log('Salem:  GET /salem/pois, /salem/businesses, /salem/figures, /salem/timeline, /salem/sources, /salem/tours, /salem/events, /salem/sync, /salem/stats');
  console.log('Router: GET /salem/route?from_lat&from_lng&to_lat&to_lng[&source=live], POST /salem/route-multi, GET /salem/route/meta');
  console.log('Admin:  GET /cache/stats, POST /cache/clear (Basic Auth), GET /admin/ping (Basic Auth)');
  console.log('AdminPOI: GET /admin/salem/pois?kind=tour|business|narration, GET /admin/salem/pois/duplicates?radius=, GET/PUT/DELETE /admin/salem/pois/:kind/:id, POST .../move, POST .../restore (Basic Auth)');
  console.log(`        Admin auth: ${adminAuthConfigured ? 'configured' : 'NOT CONFIGURED — set ADMIN_USER and ADMIN_PASS'}`);
});

// ── Global error handlers ───────────────────────────────────────────────────
// Express 4.x catches sync throws and async-await rejections inside route
// handlers, but ONLY when there is a 4-arg error middleware mounted. Without
// this, a route that throws past its try/catch returns no response and ties up
// the socket until the client times out.
app.use((err, req, res, _next) => {
  console.error('[Express] Unhandled route error:', err && err.stack ? err.stack : err);
  if (res.headersSent) return;
  res.status(500).json({ error: 'internal_error' });
});

// Last-line-of-defense for promise rejections that escape Express (e.g. an
// async background task that no route owns). We log loudly but do NOT kill the
// process — the cache-proxy is single-tenant on a dev box and crashing on a
// single bad rejection leaves the operator with a dead admin tool.
process.on('unhandledRejection', (reason, promise) => {
  console.error('[Process] Unhandled promise rejection:', reason && reason.stack ? reason.stack : reason);
});
process.on('uncaughtException', (err) => {
  console.error('[Process] Uncaught exception:', err && err.stack ? err.stack : err);
  // Don't exit — same reasoning as above.
});

// ── Graceful shutdown ───────────────────────────────────────────────────────
// SIGTERM (systemd / pm2 restart) and SIGINT (Ctrl-C) drain in-flight HTTP,
// close the PG pool so queued queries finish, then exit cleanly. Without this
// pgPool.end() never fires and queries can be lost on restart.
let shuttingDown = false;
async function gracefulShutdown(signal) {
  if (shuttingDown) return;
  shuttingDown = true;
  console.log(`[Server] ${signal} received — graceful shutdown starting`);

  const forceExitTimer = setTimeout(() => {
    console.error('[Server] Forced exit after 10s — connections did not drain');
    process.exit(1);
  }, 10_000);
  forceExitTimer.unref();

  try {
    await new Promise((resolve) => server.close(() => resolve()));
    console.log('[Server] HTTP closed');
  } catch (err) {
    console.error('[Server] HTTP close error:', err.message);
  }

  if (pgPool) {
    try {
      await pgPool.end();
      console.log('[Server] PG pool drained');
    } catch (err) {
      console.error('[Server] PG pool drain error:', err.message);
    }
  }

  clearTimeout(forceExitTimer);
  process.exit(0);
}
process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));
