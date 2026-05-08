/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Admin burst-photos endpoint (S229).
 *
 * Surfaces the GPS-burst camera album organized by tools/pull-and-organize-
 * burst-photos.py at /mnt/sdb-images/LMASalemPictures/session-*\/burst_*.jpg
 * to the web admin tool so the operator can pin every shot on the map and
 * spot-check POI / path alignment against real-world imagery.
 *
 * Routes (all gated by /admin Basic Auth):
 *   GET    /admin/burst-photos             — list { id, lat, lon, ts, session, name }
 *   GET    /admin/burst-photos/:id/image   — stream the full JPEG
 *   GET    /admin/burst-photos/:id/thumb   — sharp-resized 256-wide JPEG (disk-cached)
 *   GET    /admin/burst-photos/:id/exif    — parsed EXIF as JSON
 *   DELETE /admin/burst-photos/:id         — move to <session>/.deleted/ (recoverable)
 *
 * Identifier scheme: base64url(`<session>/<filename>`). Path traversal is
 * blocked by validating that the resolved file path stays under ROOT_DIR.
 *
 * Filename pattern (produced by GpsBurstCameraManager.kt, S228):
 *   burst_YYYYMMDD-HHMMSS_<lat>_<lon>.jpg   (negatives use 'n')
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module admin-burst-photos.js';

const fs = require('fs');
const path = require('path');
const exifr = require('exifr');
const sharp = require('sharp');

const ROOT_DIR = process.env.LMA_BURST_PHOTOS_ROOT || '/mnt/sdb-images/LMASalemPictures';
const THUMB_WIDTH_PX = 320;
const THUMB_QUALITY  = 70;

const FNAME_RE = /^burst_(\d{8})-(\d{6})_([n\d.]+)_([n\d.]+)\.jpg$/;

function parseFilename(name) {
  const m = FNAME_RE.exec(name);
  if (!m) return null;
  const [, dateS, timeS, latS, lonS] = m;
  const yr  = +dateS.slice(0, 4);
  const mo  = +dateS.slice(4, 6);
  const day = +dateS.slice(6, 8);
  const hh  = +timeS.slice(0, 2);
  const mm  = +timeS.slice(2, 4);
  const ss  = +timeS.slice(4, 6);
  const ts  = Date.UTC(yr, mo - 1, day, hh, mm, ss);
  const lat = Number(latS.replace('n', '-'));
  const lon = Number(lonS.replace('n', '-'));
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null;
  return { ts, lat, lon };
}

function encodeId(session, name) {
  return Buffer.from(`${session}/${name}`).toString('base64url');
}

function decodeId(id) {
  try {
    const decoded = Buffer.from(String(id), 'base64url').toString('utf8');
    const idx = decoded.indexOf('/');
    if (idx < 0) return null;
    const session = decoded.slice(0, idx);
    const name = decoded.slice(idx + 1);
    if (!session.startsWith('session-')) return null;
    if (!FNAME_RE.test(name)) return null;
    const full = path.resolve(ROOT_DIR, session, name);
    const rootResolved = path.resolve(ROOT_DIR);
    if (!full.startsWith(rootResolved + path.sep)) return null; // traversal guard
    return { session, name, full };
  } catch (_e) {
    return null;
  }
}

// ── Listing cache ───────────────────────────────────────────────────────────
// Filesystem scan over ~770 photos is cheap (~30 ms cold), but the listing is
// requested every time the operator opens the Photos tab. 5 s TTL is plenty
// for an admin tool with one user.
let LIST_CACHE = null;     // { ts, body }
const LIST_CACHE_TTL_MS = 5000;

function buildList() {
  if (!fs.existsSync(ROOT_DIR)) return { sessions: [], photos: [] };
  const sessions = [];
  const photos = [];
  const sessionDirs = fs.readdirSync(ROOT_DIR, { withFileTypes: true })
    .filter(d => d.isDirectory() && d.name.startsWith('session-'))
    .map(d => d.name)
    .sort();
  for (const session of sessionDirs) {
    const dir = path.join(ROOT_DIR, session);
    let count = 0;
    let bbox = null;
    let firstTs = null, lastTs = null;
    for (const name of fs.readdirSync(dir)) {
      if (!name.endsWith('.jpg')) continue;
      if (name.startsWith('.')) continue; // skip dotfiles / .thumbs/
      const meta = parseFilename(name);
      if (!meta) continue;
      photos.push({
        id: encodeId(session, name),
        session,
        name,
        ts: meta.ts,
        lat: meta.lat,
        lon: meta.lon,
      });
      count++;
      bbox = bbox
        ? [Math.min(bbox[0], meta.lat), Math.min(bbox[1], meta.lon),
           Math.max(bbox[2], meta.lat), Math.max(bbox[3], meta.lon)]
        : [meta.lat, meta.lon, meta.lat, meta.lon];
      firstTs = firstTs == null ? meta.ts : Math.min(firstTs, meta.ts);
      lastTs  = lastTs  == null ? meta.ts : Math.max(lastTs, meta.ts);
    }
    sessions.push({ session, count, bbox, firstTs, lastTs });
  }
  return { sessions, photos, root: ROOT_DIR };
}

function getCachedList() {
  const now = Date.now();
  if (LIST_CACHE && (now - LIST_CACHE.ts) < LIST_CACHE_TTL_MS) {
    return LIST_CACHE.body;
  }
  const body = buildList();
  LIST_CACHE = { ts: now, body };
  return body;
}

function invalidateCache() {
  LIST_CACHE = null;
}

module.exports = function(app, _deps) {
  app.get('/admin/burst-photos', (_req, res) => {
    try {
      const body = getCachedList();
      res.json(body);
    } catch (err) {
      console.error('[admin-burst-photos] list failed:', err);
      res.status(500).json({ error: String(err.message || err) });
    }
  });

  app.get('/admin/burst-photos/:id/image', (req, res) => {
    const decoded = decodeId(req.params.id);
    if (!decoded) return res.status(400).json({ error: 'bad id' });
    if (!fs.existsSync(decoded.full)) return res.status(404).json({ error: 'not found' });
    res.setHeader('Content-Type', 'image/jpeg');
    res.setHeader('Cache-Control', 'private, max-age=300');
    fs.createReadStream(decoded.full).pipe(res);
  });

  app.get('/admin/burst-photos/:id/thumb', async (req, res) => {
    const decoded = decodeId(req.params.id);
    if (!decoded) return res.status(400).json({ error: 'bad id' });
    if (!fs.existsSync(decoded.full)) return res.status(404).json({ error: 'not found' });
    const thumbDir = path.join(ROOT_DIR, decoded.session, '.thumbs');
    const thumbPath = path.join(thumbDir, decoded.name);
    try {
      if (!fs.existsSync(thumbPath)) {
        if (!fs.existsSync(thumbDir)) fs.mkdirSync(thumbDir, { recursive: true });
        await sharp(decoded.full)
          .rotate()
          .resize({ width: THUMB_WIDTH_PX, withoutEnlargement: true })
          .jpeg({ quality: THUMB_QUALITY, mozjpeg: true })
          .toFile(thumbPath);
      }
      res.setHeader('Content-Type', 'image/jpeg');
      res.setHeader('Cache-Control', 'private, max-age=86400');
      fs.createReadStream(thumbPath).pipe(res);
    } catch (err) {
      console.error('[admin-burst-photos] thumb failed:', err);
      res.status(500).json({ error: String(err.message || err) });
    }
  });

  app.get('/admin/burst-photos/:id/exif', async (req, res) => {
    const decoded = decodeId(req.params.id);
    if (!decoded) return res.status(400).json({ error: 'bad id' });
    if (!fs.existsSync(decoded.full)) return res.status(404).json({ error: 'not found' });
    try {
      const exif = await exifr.parse(decoded.full, {
        gps: true,
        ifd0: true,
        exif: true,
        userComment: true,
      }) || {};
      const stat = fs.statSync(decoded.full);
      res.json({
        id: req.params.id,
        session: decoded.session,
        name: decoded.name,
        size_bytes: stat.size,
        mtime: stat.mtime.toISOString(),
        exif,
      });
    } catch (err) {
      console.error('[admin-burst-photos] exif parse failed:', err);
      res.status(500).json({ error: String(err.message || err) });
    }
  });

  app.delete('/admin/burst-photos/:id', (req, res) => {
    const decoded = decodeId(req.params.id);
    if (!decoded) return res.status(400).json({ error: 'bad id' });
    if (!fs.existsSync(decoded.full)) return res.status(404).json({ error: 'not found' });
    try {
      // Soft-delete: move source + thumb into <session>/.deleted/ (and
      // .deleted/.thumbs/) so the operator can recover. Permanent cleanup is
      // a separate `rm -rf <session>/.deleted` step performed manually when
      // they're confident. The list scanner skips dotfiles, so .deleted/
      // never reappears in the admin overlay.
      const trashDir   = path.join(ROOT_DIR, decoded.session, '.deleted');
      const trashThumb = path.join(trashDir, '.thumbs');
      if (!fs.existsSync(trashDir)) fs.mkdirSync(trashDir, { recursive: true });
      // Avoid clobber if a same-name file was previously trashed.
      const uniqName = (target) => {
        if (!fs.existsSync(target)) return target;
        const ext = path.extname(target);
        const base = target.slice(0, -ext.length);
        const stamp = new Date().toISOString().replace(/[:.]/g, '-');
        return `${base}.${stamp}${ext}`;
      };
      fs.renameSync(decoded.full, uniqName(path.join(trashDir, decoded.name)));
      const thumbPath = path.join(ROOT_DIR, decoded.session, '.thumbs', decoded.name);
      if (fs.existsSync(thumbPath)) {
        if (!fs.existsSync(trashThumb)) fs.mkdirSync(trashThumb, { recursive: true });
        try { fs.renameSync(thumbPath, uniqName(path.join(trashThumb, decoded.name))); }
        catch (_e) { /* best-effort */ }
      }
      invalidateCache();
      res.json({ ok: true, deleted: { session: decoded.session, name: decoded.name, trashedTo: '.deleted/' } });
    } catch (err) {
      console.error('[admin-burst-photos] delete failed:', err);
      res.status(500).json({ error: String(err.message || err) });
    }
  });
};
