/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module zones.js';

module.exports = function(app, deps) {
  const { cacheGet, cacheSet, stats, snapBbox, log } = deps;

  // ── Speed / Red-Light Cameras (Overpass) ─────────────────────────────────────

  const CAMERA_TTL = 24 * 60 * 60 * 1000;  // 24h

  app.get('/cameras', async (req, res) => {
    const bbox = req.query.bbox;
    if (!bbox) return res.status(400).json({ error: 'bbox required (s,w,n,e)' });
    const parts = bbox.split(',').map(Number);
    if (parts.length !== 4 || parts.some(isNaN)) return res.status(400).json({ error: 'Invalid bbox' });
    const snap = snapBbox(parts[0], parts[1], parts[2], parts[3]);
    const cacheKey = `cameras:${snap.s},${snap.w},${snap.n},${snap.e}`;
    const cached = cacheGet(cacheKey, CAMERA_TTL);
    if (cached) { stats.hits++; log('/cameras', true, 0); return res.json(JSON.parse(cached.data)); }
    stats.misses++;
    const t0 = Date.now();
    try {
      const query = `[out:json][timeout:30];(node["highway"="speed_camera"](${snap.s},${snap.w},${snap.n},${snap.e});node["man_made"="surveillance"]["surveillance:type"="camera"]["surveillance"="enforcement"](${snap.s},${snap.w},${snap.n},${snap.e}););out body;`;
      const resp = await fetch('https://overpass-api.de/api/interpreter', {
        method: 'POST', body: 'data=' + encodeURIComponent(query),
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        signal: AbortSignal.timeout(30000)
      });
      if (!resp.ok) return res.status(502).json({ error: `Overpass returned ${resp.status}` });
      const data = await resp.json();
      const results = (data.elements || []).map(el => ({
        id: `camera_${el.id}`,
        lat: el.lat,
        lon: el.lon,
        cameraType: el.tags?.['camera:type'] || el.tags?.['surveillance:type'] || 'speed_camera',
        maxspeed: el.tags?.maxspeed || null,
        direction: el.tags?.direction || null,
        operator: el.tags?.operator || null,
        name: el.tags?.name || el.tags?.description || null,
      }));
      cacheSet(cacheKey, JSON.stringify(results), { 'content-type': 'application/json' });
      log('/cameras', false, Date.now() - t0, `${results.length} cameras`);
      res.json(results);
    } catch (err) {
      console.error(`[cameras error] ${err.message}`);
      res.status(502).json({ error: 'Camera fetch failed', detail: err.message });
    }
  });

  // ── School Zones (Overpass) ──────────────────────────────────────────────────

  const SCHOOL_TTL = 24 * 60 * 60 * 1000;  // 24h

  app.get('/schools', async (req, res) => {
    const bbox = req.query.bbox;
    if (!bbox) return res.status(400).json({ error: 'bbox required (s,w,n,e)' });
    const parts = bbox.split(',').map(Number);
    if (parts.length !== 4 || parts.some(isNaN)) return res.status(400).json({ error: 'Invalid bbox' });
    const snap = snapBbox(parts[0], parts[1], parts[2], parts[3]);
    const cacheKey = `schools:${snap.s},${snap.w},${snap.n},${snap.e}`;
    const cached = cacheGet(cacheKey, SCHOOL_TTL);
    if (cached) { stats.hits++; log('/schools', true, 0); return res.json(JSON.parse(cached.data)); }
    stats.misses++;
    const t0 = Date.now();
    try {
      const query = `[out:json][timeout:30];(node["amenity"="school"](${snap.s},${snap.w},${snap.n},${snap.e});way["amenity"="school"](${snap.s},${snap.w},${snap.n},${snap.e});relation["amenity"="school"](${snap.s},${snap.w},${snap.n},${snap.e}););out body; >; out skel qt;`;
      const resp = await fetch('https://overpass-api.de/api/interpreter', {
        method: 'POST', body: 'data=' + encodeURIComponent(query),
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        signal: AbortSignal.timeout(30000)
      });
      if (!resp.ok) return res.status(502).json({ error: `Overpass returned ${resp.status}` });
      const data = await resp.json();
      // Build node lookup for way/relation geometry
      const nodeLookup = {};
      for (const el of data.elements || []) {
        if (el.type === 'node' && !el.tags) nodeLookup[el.id] = { lat: el.lat, lon: el.lon };
      }
      const results = [];
      for (const el of data.elements || []) {
        if (!el.tags || el.tags.amenity !== 'school') continue;
        const entry = {
          id: `school_${el.type}_${el.id}`,
          name: el.tags.name || 'School',
          lat: el.lat || null,
          lon: el.lon || null,
          grades: el.tags['school:grades'] || el.tags.isced || null,
          operator: el.tags.operator || null,
          isPolygon: false,
          points: null,
        };
        if (el.type === 'way' && el.nodes) {
          const pts = el.nodes.map(nid => nodeLookup[nid]).filter(Boolean);
          if (pts.length >= 3) {
            entry.isPolygon = true;
            entry.points = pts.map(p => [p.lon, p.lat]);
            // Compute centroid for lat/lon
            const sumLat = pts.reduce((s, p) => s + p.lat, 0);
            const sumLon = pts.reduce((s, p) => s + p.lon, 0);
            entry.lat = sumLat / pts.length;
            entry.lon = sumLon / pts.length;
          }
        }
        if (entry.lat != null && entry.lon != null) results.push(entry);
      }
      cacheSet(cacheKey, JSON.stringify(results), { 'content-type': 'application/json' });
      log('/schools', false, Date.now() - t0, `${results.length} schools`);
      res.json(results);
    } catch (err) {
      console.error(`[schools error] ${err.message}`);
      res.status(502).json({ error: 'School fetch failed', detail: err.message });
    }
  });

  // ── Flood Zones (FEMA NFHL ArcGIS Layer 28) ─────────────────────────────────

  const FLOOD_TTL = 30 * 24 * 60 * 60 * 1000;  // 30 days

  app.get('/flood-zones', async (req, res) => {
    const bbox = req.query.bbox;
    if (!bbox) return res.status(400).json({ error: 'bbox required (s,w,n,e)' });
    const parts = bbox.split(',').map(Number);
    if (parts.length !== 4 || parts.some(isNaN)) return res.status(400).json({ error: 'Invalid bbox' });
    const snap = snapBbox(parts[0], parts[1], parts[2], parts[3]);
    const cacheKey = `flood:${snap.s},${snap.w},${snap.n},${snap.e}`;
    const cached = cacheGet(cacheKey, FLOOD_TTL);
    if (cached) { stats.hits++; log('/flood-zones', true, 0); return res.json(JSON.parse(cached.data)); }
    stats.misses++;
    const t0 = Date.now();
    try {
      const baseUrl = 'https://hazards.fema.gov/gis/nfhl/rest/services/public/NFHL/MapServer/28/query';
      const envelope = `${snap.w},${snap.s},${snap.e},${snap.n}`;
      const allResults = [];
      let offset = 0;
      const pageSize = 2000;
      while (true) {
        const params = new URLSearchParams({
          where: "SFHA_TF='T'",
          geometry: envelope,
          geometryType: 'esriGeometryEnvelope',
          spatialRel: 'esriSpatialRelIntersects',
          outFields: 'FLD_ZONE,ZONE_SUBTY,STATIC_BFE',
          f: 'geojson',
          resultOffset: String(offset),
          resultRecordCount: String(pageSize),
          inSR: '4326',
          outSR: '4326',
        });
        const resp = await fetch(`${baseUrl}?${params}`, { signal: AbortSignal.timeout(30000) });
        if (!resp.ok) return res.status(502).json({ error: `FEMA returned ${resp.status}` });
        const geoJson = await resp.json();
        const features = geoJson.features || [];
        for (const f of features) {
          if (!f.geometry || f.geometry.type !== 'Polygon') continue;
          allResults.push({
            id: `flood_${offset + allResults.length}`,
            zoneCode: f.properties?.FLD_ZONE || '',
            zoneSubtype: f.properties?.ZONE_SUBTY || '',
            bfe: f.properties?.STATIC_BFE || null,
            rings: f.geometry.coordinates,
          });
        }
        if (features.length < pageSize) break;
        offset += pageSize;
      }
      cacheSet(cacheKey, JSON.stringify(allResults), { 'content-type': 'application/json' });
      log('/flood-zones', false, Date.now() - t0, `${allResults.length} flood zones`);
      res.json(allResults);
    } catch (err) {
      console.error(`[flood-zones error] ${err.message}`);
      res.status(502).json({ error: 'Flood zone fetch failed', detail: err.message });
    }
  });

  // ── Railroad Crossings (Overpass — railway=level_crossing) ───────────────────

  const CROSSING_TTL = 7 * 24 * 60 * 60 * 1000;  // 7 days

  app.get('/crossings', async (req, res) => {
    const bbox = req.query.bbox;
    if (!bbox) return res.status(400).json({ error: 'bbox required (s,w,n,e)' });
    const parts = bbox.split(',').map(Number);
    if (parts.length !== 4 || parts.some(isNaN)) return res.status(400).json({ error: 'Invalid bbox' });
    const snap = snapBbox(parts[0], parts[1], parts[2], parts[3]);
    const cacheKey = `crossings:${snap.s},${snap.w},${snap.n},${snap.e}`;
    const cached = cacheGet(cacheKey, CROSSING_TTL);
    if (cached) { stats.hits++; log('/crossings', true, 0); return res.json(JSON.parse(cached.data)); }
    stats.misses++;
    const t0 = Date.now();
    try {
      const query = `[out:json][timeout:30];(node["railway"="level_crossing"](${snap.s},${snap.w},${snap.n},${snap.e});node["railway"="crossing"](${snap.s},${snap.w},${snap.n},${snap.e}););out body;`;
      const resp = await fetch('https://overpass-api.de/api/interpreter', {
        method: 'POST', body: 'data=' + encodeURIComponent(query),
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        signal: AbortSignal.timeout(30000)
      });
      if (!resp.ok) return res.status(502).json({ error: `Overpass returned ${resp.status}` });
      const data = await resp.json();
      const results = (data.elements || []).map(el => ({
        id: `crossing_${el.id}`,
        lat: el.lat,
        lon: el.lon,
        crossingId: String(el.id),
        railroad: el.tags?.operator || el.tags?.['network'] || '',
        street: el.tags?.name || el.tags?.['addr:street'] || '',
        warningDevices: el.tags?.['crossing:barrier'] || el.tags?.['crossing:bell'] ? 'gates/bells' : el.tags?.['crossing:light'] ? 'lights' : '',
        crossingType: el.tags?.railway || 'level_crossing',
      }));
      cacheSet(cacheKey, JSON.stringify(results), { 'content-type': 'application/json' });
      log('/crossings', false, Date.now() - t0, `${results.length} crossings`);
      res.json(results);
    } catch (err) {
      console.error(`[crossings error] ${err.message}`);
      res.status(502).json({ error: 'Crossing fetch failed', detail: err.message });
    }
  });
};
