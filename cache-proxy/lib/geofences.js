/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module geofences.js';

const fs = require('fs');
const path = require('path');

module.exports = function(app, deps) {
  const { log } = deps;

  app.get('/geofences/catalog', (req, res) => {
    const catalogPath = path.join(__dirname, '..', 'geofence-databases', 'catalog.json');
    try {
      if (!fs.existsSync(catalogPath)) {
        return res.json([]);
      }
      const catalog = JSON.parse(fs.readFileSync(catalogPath, 'utf-8'));
      // Enrich with actual file sizes
      const enriched = catalog.map(entry => {
        const dbPath = path.join(__dirname, '..', 'geofence-databases', `${entry.id}.db`);
        let fileSize = entry.fileSize || 0;
        try {
          const stat = fs.statSync(dbPath);
          fileSize = stat.size;
        } catch (_) {}
        return { ...entry, fileSize };
      });
      log('/geofences/catalog', false, 0, `${enriched.length} databases`);
      res.json(enriched);
    } catch (e) {
      console.error('Catalog error:', e.message);
      res.status(500).json({ error: e.message });
    }
  });

  app.get('/geofences/database/:id/download', (req, res) => {
    const { id } = req.params;
    // Sanitize: only allow alphanumeric, hyphens, underscores
    if (!/^[a-zA-Z0-9_-]+$/.test(id)) {
      return res.status(400).json({ error: 'Invalid database ID' });
    }
    const dbPath = path.join(__dirname, '..', 'geofence-databases', `${id}.db`);
    if (!fs.existsSync(dbPath)) {
      return res.status(404).json({ error: `Database not found: ${id}` });
    }
    const stat = fs.statSync(dbPath);
    res.setHeader('Content-Type', 'application/x-sqlite3');
    res.setHeader('Content-Disposition', `attachment; filename="${id}.db"`);
    res.setHeader('Content-Length', stat.size);
    log(`/geofences/database/${id}/download`, false, 0, `${stat.size} bytes`);
    fs.createReadStream(dbPath).pipe(res);
  });
};
