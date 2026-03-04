/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module tfr.js';

const cheerio = require('cheerio');
const { XMLParser } = require('fast-xml-parser');

module.exports = function(app, deps) {
  const { cacheGet, cacheSet, stats, log } = deps;

  const TFR_LIST_TTL = 5 * 60 * 1000;    // 5 min
  const TFR_DETAIL_TTL = 10 * 60 * 1000; // 10 min

  /** Parse FAA DMS coordinate like "383200N" or "0770305W" to decimal degrees. */
  function parseFaaDms(dms) {
    if (!dms || typeof dms !== 'string') return null;
    const m = dms.match(/^(\d{2,3})(\d{2})(\d{2})([NSEW])$/);
    if (!m) return null;
    const deg = parseInt(m[1], 10);
    const min = parseInt(m[2], 10);
    const sec = parseInt(m[3], 10);
    let dd = deg + min / 60 + sec / 3600;
    if (m[4] === 'S' || m[4] === 'W') dd = -dd;
    return dd;
  }

  /** Parse altitude string from AIXM (feet or FL). Returns feet. */
  function parseAltitude(val, uom) {
    if (val == null) return 0;
    const n = typeof val === 'string' ? parseInt(val, 10) : val;
    if (isNaN(n)) return 0;
    if (uom === 'FL') return n * 100;
    return n;
  }

  /** Generate circle polygon points from center + radius. */
  function circleToPolygon(latCenter, lonCenter, radiusNm, numPoints = 32) {
    const points = [];
    const radiusDeg = radiusNm / 60; // 1 nm ≈ 1 arcminute of lat
    for (let i = 0; i <= numPoints; i++) {
      const angle = (2 * Math.PI * i) / numPoints;
      const lat = latCenter + radiusDeg * Math.cos(angle);
      const lon = lonCenter + (radiusDeg / Math.cos(latCenter * Math.PI / 180)) * Math.sin(angle);
      points.push([lon, lat]);
    }
    return points;
  }

  /** Interpolate arc segment between two bearings around a center point. */
  function arcToPoints(latCenter, lonCenter, radiusNm, startBearing, endBearing, stepDeg = 5) {
    const points = [];
    const radiusDeg = radiusNm / 60;
    let b = startBearing;
    const dir = ((endBearing - startBearing + 360) % 360) <= 180 ? 1 : -1;
    for (let i = 0; i < 360 / stepDeg; i++) {
      const rad = (b * Math.PI) / 180;
      const lat = latCenter + radiusDeg * Math.cos(rad);
      const lon = lonCenter + (radiusDeg / Math.cos(latCenter * Math.PI / 180)) * Math.sin(rad);
      points.push([lon, lat]);
      if (Math.abs(((b - endBearing + 360) % 360)) < stepDeg) break;
      b = (b + dir * stepDeg + 360) % 360;
    }
    // Final point at exact end bearing
    const endRad = (endBearing * Math.PI) / 180;
    points.push([
      lonCenter + (radiusDeg / Math.cos(latCenter * Math.PI / 180)) * Math.sin(endRad),
      latCenter + radiusDeg * Math.cos(endRad)
    ]);
    return points;
  }

  /** Fetch and parse a single TFR detail page XML. Returns parsed shapes or null. */
  async function fetchTfrDetail(detailPath) {
    const cacheKey = `tfr_detail:${detailPath}`;
    const cached = cacheGet(cacheKey, TFR_DETAIL_TTL);
    if (cached) { stats.hits++; return JSON.parse(cached.data); }

    try {
      const url = `https://tfr.faa.gov${detailPath}`;
      const resp = await fetch(url, {
        headers: { 'User-Agent': 'LocationMapApp/1.5 cache-proxy' },
        signal: AbortSignal.timeout(15000)
      });
      if (!resp.ok) return null;
      const xml = await resp.text();

      const parser = new XMLParser({
        ignoreAttributes: false,
        attributeNamePrefix: '@_',
        isArray: (name) => ['Group', 'Avx'].includes(name)
      });
      const parsed = parser.parse(xml);

      // Navigate AIXM structure — handle various FAA XML formats
      let notamText = '';
      let tfrType = '';
      let facilityName = '';
      let stateName = '';
      let effectiveDate = '';
      let expireDate = '';

      // Shapes — look for Group elements with Avx (aviation vertex) arrays
      const shapes = [];
      function findGroups(obj) {
        if (!obj || typeof obj !== 'object') return;
        if (Array.isArray(obj)) { obj.forEach(findGroups); return; }
        if (obj.Group) {
          const groups = Array.isArray(obj.Group) ? obj.Group : [obj.Group];
          for (const g of groups) {
            const shape = parseGroup(g);
            if (shape) shapes.push(shape);
          }
        }
        // Recurse into all child objects
        for (const k of Object.keys(obj)) {
          if (k !== 'Group') findGroups(obj[k]);
        }
      }

      function parseGroup(group) {
        // Extract altitude
        const valDistVerLower = group.valDistVerLower ?? group['@_valDistVerLower'] ?? '0';
        const uomDistVerLower = group.uomDistVerLower ?? group['@_uomDistVerLower'] ?? 'FT';
        const valDistVerUpper = group.valDistVerUpper ?? group['@_valDistVerUpper'] ?? '99999';
        const uomDistVerUpper = group.uomDistVerUpper ?? group['@_uomDistVerUpper'] ?? 'FT';
        const floorAltFt = parseAltitude(valDistVerLower, uomDistVerLower);
        const ceilingAltFt = parseAltitude(valDistVerUpper, uomDistVerUpper);

        const avxList = group.Avx || group.avx;
        if (!avxList || !Array.isArray(avxList) || avxList.length === 0) {
          return null;
        }

        const points = [];
        let shapeType = 'polygon';
        let radiusNm = null;

        for (const avx of avxList) {
          const code = avx.codeType ?? avx['@_codeType'] ?? '';
          const lat = parseFaaDms(avx.geoLat ?? avx['@_geoLat'] ?? '');
          const lon = parseFaaDms(avx.geoLon ?? avx['@_geoLon'] ?? '');

          if (code === 'CWA' || code === 'CCA') {
            // Arc vertex — center + radius
            const arcLat = parseFaaDms(avx.geoLatArc ?? avx['@_geoLatArc'] ?? '');
            const arcLon = parseFaaDms(avx.geoLonArc ?? avx['@_geoLonArc'] ?? '');
            const r = parseFloat(avx.valRadiusArc ?? avx['@_valRadiusArc'] ?? '0');
            const rUom = avx.uomRadiusArc ?? avx['@_uomRadiusArc'] ?? 'NM';
            const rNm = rUom === 'KM' ? r * 0.539957 : r;

            if (arcLat != null && arcLon != null && rNm > 0 && lat != null && lon != null) {
              const startBearing = Math.atan2(
                Math.sin((points.length > 0 ? points[points.length-1][0] : lon) - arcLon) * Math.cos(arcLat),
                Math.cos(arcLat) * Math.sin(points.length > 0 ? points[points.length-1][1] : lat) -
                Math.sin(arcLat) * Math.cos(points.length > 0 ? points[points.length-1][1] : lat) *
                Math.cos((points.length > 0 ? points[points.length-1][0] : lon) - arcLon)
              ) * 180 / Math.PI;
              const endBearing = Math.atan2(
                Math.sin(lon - arcLon) * Math.cos(arcLat),
                Math.cos(arcLat) * Math.sin(lat) - Math.sin(arcLat) * Math.cos(lat) * Math.cos(lon - arcLon)
              ) * 180 / Math.PI;
              const arcPts = arcToPoints(arcLat, arcLon, rNm, (startBearing + 360) % 360, (endBearing + 360) % 360);
              for (const p of arcPts) points.push(p);
              shapeType = 'polyarc';
            }
          } else if (code === 'C') {
            // Circle center
            const r = parseFloat(avx.valRadiusArc ?? avx['@_valRadiusArc'] ?? '0');
            const rUom = avx.uomRadiusArc ?? avx['@_uomRadiusArc'] ?? 'NM';
            const rNm = rUom === 'KM' ? r * 0.539957 : r;
            if (lat != null && lon != null && rNm > 0) {
              const circlePts = circleToPolygon(lat, lon, rNm);
              for (const p of circlePts) points.push(p);
              shapeType = 'circle';
              radiusNm = rNm;
            }
          } else {
            // GRC (great circle) or regular point
            if (lat != null && lon != null) {
              points.push([lon, lat]);
            }
          }
        }

        if (points.length < 3) return null;

        // Close polygon if needed
        const first = points[0], last = points[points.length - 1];
        if (Math.abs(first[0] - last[0]) > 0.0001 || Math.abs(first[1] - last[1]) > 0.0001) {
          points.push([...first]);
        }

        return { type: shapeType, points, floorAltFt, ceilingAltFt, radiusNm };
      }

      findGroups(parsed);

      // Also try to extract metadata
      function findMeta(obj) {
        if (!obj || typeof obj !== 'object') return;
        if (Array.isArray(obj)) { obj.forEach(findMeta); return; }
        if (obj.txtDescrUSNS && !notamText) notamText = String(obj.txtDescrUSNS);
        if (obj.txtNameUSNS && !notamText) notamText = String(obj.txtNameUSNS);
        if (obj.txtDescrTraditional && !notamText) notamText = String(obj.txtDescrTraditional);
        if (obj.codeTimeZone && !tfrType) tfrType = String(obj.codeTimeZone);
        if (obj.txtNameFacility && !facilityName) facilityName = String(obj.txtNameFacility);
        if (obj.txtNameState && !stateName) stateName = String(obj.txtNameState);
        if (obj.dateEffective && !effectiveDate) effectiveDate = String(obj.dateEffective);
        if (obj.dateExpire && !expireDate) expireDate = String(obj.dateExpire);
        for (const k of Object.keys(obj)) findMeta(obj[k]);
      }
      findMeta(parsed);

      const result = { shapes, notamText, tfrType, facilityName, stateName, effectiveDate, expireDate };
      stats.misses++;
      cacheSet(cacheKey, JSON.stringify(result), { 'content-type': 'application/json' });
      return result;
    } catch (err) {
      console.error(`[TFR detail error] ${detailPath}: ${err.message}`);
      return null;
    }
  }

  app.get('/tfrs', async (req, res) => {
    const bbox = req.query.bbox;
    if (!bbox) return res.status(400).json({ error: 'bbox required (s,w,n,e)' });
    const [bboxS, bboxW, bboxN, bboxE] = bbox.split(',').map(Number);
    if ([bboxS, bboxW, bboxN, bboxE].some(isNaN)) {
      return res.status(400).json({ error: 'Invalid bbox — expected s,w,n,e as numbers' });
    }

    const t0 = Date.now();

    try {
      // Step 1: Fetch TFR list page (5-min cache)
      const listCacheKey = 'tfr_list';
      let tfrLinks;
      const cachedList = cacheGet(listCacheKey, TFR_LIST_TTL);
      if (cachedList) {
        stats.hits++;
        tfrLinks = JSON.parse(cachedList.data);
      } else {
        stats.misses++;
        const listResp = await fetch('https://tfr.faa.gov/tfr2/list.jsp', {
          headers: { 'User-Agent': 'LocationMapApp/1.5 cache-proxy' },
          signal: AbortSignal.timeout(15000)
        });
        if (!listResp.ok) {
          return res.status(502).json({ error: `FAA TFR list returned ${listResp.status}` });
        }
        const listHtml = await listResp.text();
        const $ = cheerio.load(listHtml);
        tfrLinks = [];
        $('a[href*="save_pages"]').each((_, el) => {
          const href = $(el).attr('href');
          if (href && href.includes('.xml')) {
            const text = $(el).text().trim();
            const row = $(el).closest('tr');
            const cells = row.find('td');
            const notam = text || $(cells[0]).text().trim();
            const type = cells.length > 1 ? $(cells[1]).text().trim() : '';
            const description = cells.length > 2 ? $(cells[2]).text().trim() : '';
            const state = cells.length > 3 ? $(cells[3]).text().trim() : '';
            tfrLinks.push({ href: href.startsWith('/') ? href : `/${href}`, notam, type, description, state });
          }
        });
        cacheSet(listCacheKey, JSON.stringify(tfrLinks), { 'content-type': 'application/json' });
        log('/tfrs', false, Date.now() - t0, `${tfrLinks.length} TFRs listed`);
      }

      // Step 2: Fetch detail XML for each TFR (parallel, each cached 10 min)
      const detailPromises = tfrLinks.map(async (link) => {
        const detail = await fetchTfrDetail(link.href);
        if (!detail || !detail.shapes || detail.shapes.length === 0) return null;

        // Filter shapes against bbox
        const filteredShapes = detail.shapes.filter(shape => {
          if (!shape.points || shape.points.length === 0) return false;
          let minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
          for (const [lon, lat] of shape.points) {
            if (lat < minLat) minLat = lat;
            if (lat > maxLat) maxLat = lat;
            if (lon < minLon) minLon = lon;
            if (lon > maxLon) maxLon = lon;
          }
          // Intersects bbox?
          return !(maxLat < bboxS || minLat > bboxN || maxLon < bboxW || minLon > bboxE);
        });

        if (filteredShapes.length === 0) return null;

        const id = link.href.replace(/.*detail_/, '').replace('.xml', '');
        return {
          id,
          notam: link.notam || detail.notamText || id,
          type: link.type || detail.tfrType || 'TFR',
          description: link.description || detail.notamText || '',
          effectiveDate: detail.effectiveDate || '',
          expireDate: detail.expireDate || '',
          facility: detail.facilityName || '',
          state: link.state || detail.stateName || '',
          shapes: filteredShapes
        };
      });

      const results = (await Promise.all(detailPromises)).filter(Boolean);
      const elapsed = Date.now() - t0;
      log('/tfrs', !!cachedList, elapsed, `${results.length} TFRs in bbox`);
      res.json(results);
    } catch (err) {
      console.error(`[TFR error] ${err.message}`);
      res.status(502).json({ error: 'TFR fetch failed', detail: err.message });
    }
  });
};
