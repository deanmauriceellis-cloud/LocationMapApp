/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module admin.js';

const fs = require('fs');

module.exports = function(app, deps) {
  const { cache, stats, radiusHints, importBuffer, MAX_CACHE_ENTRIES,
          CACHE_FILE, RADIUS_HINTS_FILE,
          scanCells, SCAN_CELLS_FILE,
          openskyRateState, openskyConfigured,
          OPENSKY_EFFECTIVE_LIMIT, OPENSKY_MIN_INTERVAL_MS,
          getImportState, getOverpassQueueLength,
          requireBasicAuth } = deps;

  app.get('/cache/stats', (req, res) => {
    const memUsage = process.memoryUsage();
    const now = Date.now();
    const cutoff24h = now - 24 * 60 * 60 * 1000;
    const recentRequests = openskyRateState.requests.filter(t => t > cutoff24h);
    const importState = getImportState();
    res.json({
      entries: cache.size,
      maxCacheEntries: MAX_CACHE_ENTRIES,
      radiusHints: radiusHints.size,
      importBufferPending: importBuffer.length,
      scanCells: scanCells ? scanCells.size : 0,
      dbImport: {
        running: importState.dbImportRunning,
        intervalMinutes: importState.DB_IMPORT_INTERVAL_MS / 60000,
        ...importState.dbImportStats,
      },
      overpassQueue: getOverpassQueueLength(),
      hits: stats.hits,
      misses: stats.misses,
      hitRate: stats.hits + stats.misses > 0
        ? ((stats.hits / (stats.hits + stats.misses)) * 100).toFixed(1) + '%'
        : 'N/A',
      memoryMB: (memUsage.heapUsed / 1024 / 1024).toFixed(1),
      opensky: {
        requestsLast24h: recentRequests.length,
        dailyLimit: OPENSKY_EFFECTIVE_LIMIT,
        remaining: OPENSKY_EFFECTIVE_LIMIT - recentRequests.length,
        minIntervalMs: OPENSKY_MIN_INTERVAL_MS,
        backoffUntil: openskyRateState.backoffUntil > now ? new Date(openskyRateState.backoffUntil).toISOString() : null,
        backoffSeconds: openskyRateState.consecutive429s > 0 ? openskyRateState.backoffSeconds : 0,
        consecutive429s: openskyRateState.consecutive429s,
        authenticated: openskyConfigured,
      },
      keys: [...cache.keys()],
    });
  });

  // /cache/clear is a destructive admin operation. Per Phase 9P.3 it is gated
  // behind Basic Auth (the same middleware that protects /admin/*). This was a
  // latent unauthenticated route flagged in the Session 96 survey.
  app.post('/cache/clear', requireBasicAuth, (req, res) => {
    const count = cache.size;
    const hintCount = radiusHints.size;
    const bufferCount = importBuffer.length;
    const scanCellCount = scanCells ? scanCells.size : 0;
    cache.clear();
    radiusHints.clear();
    importBuffer.length = 0;
    if (scanCells) scanCells.clear();
    stats.hits = 0;
    stats.misses = 0;
    try { fs.unlinkSync(CACHE_FILE); } catch (_) {}
    try { fs.unlinkSync(RADIUS_HINTS_FILE); } catch (_) {}
    if (SCAN_CELLS_FILE) { try { fs.unlinkSync(SCAN_CELLS_FILE); } catch (_) {} }
    console.log(`[${new Date().toISOString()}] Cache cleared (${count} entries + ${hintCount} hints + ${bufferCount} buffered + ${scanCellCount} scan cells, disk files removed)`);
    res.json({ cleared: count, hintsCleared: hintCount, bufferCleared: bufferCount, scanCellsCleared: scanCellCount });
  });
};
