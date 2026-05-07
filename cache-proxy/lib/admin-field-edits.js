/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Admin field-edits inbox endpoint (S229 Part C).
 *
 * Reads the JSONL files written on-device by FieldEditManager.kt and pulled
 * to local disk by tools/pull-field-edits.py. Each line is one atomic edit
 * (move + category + subcategory + note + photo refs). Surface them as a
 * triage inbox: list + per-edit Apply / Reject. Apply routes to the existing
 * /admin/salem/pois/:id endpoints (move + update). Reject is a sidecar
 * dismissal so the same edit stops surfacing on subsequent fetches.
 *
 * Routes (all gated by /admin Basic Auth):
 *   GET    /admin/field-edits                              — list every edit + state
 *   POST   /admin/field-edits/sync                         — adb-pull fresh JSONLs from device
 *   POST   /admin/field-edits/:editKey/apply               — write change to salem_pois
 *   POST   /admin/field-edits/:editKey/reject              — mark dismissed (sidecar file)
 *   GET    /admin/field-edits/photo/:session/:filename     — stream a referenced recon photo
 *
 * editKey scheme: `<session_ts>__<line_index>` (line_index = 0-based position
 * inside the JSONL). Stable across re-pulls because the JSONL is append-only.
 *
 * State sidecar: per-session `state.json` co-located with the JSONL:
 *   { applied: { "<line_index>": { ts, result } }, dismissed: { "<line_index>": { ts, reason } } }
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module admin-field-edits.js';

const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const ROOT_DIR = process.env.LMA_FIELD_EDITS_ROOT || '/mnt/sdb-images/LMASalemFieldEdits';
const PULL_SCRIPT = path.resolve(__dirname, '..', '..', 'tools', 'pull-field-edits.py');

function listSessionDirs() {
  if (!fs.existsSync(ROOT_DIR)) return [];
  return fs.readdirSync(ROOT_DIR, { withFileTypes: true })
    .filter(d => d.isDirectory() && d.name.startsWith('edits-'))
    .map(d => d.name)
    .sort()
    .reverse(); // newest first
}

function findJsonlInSession(sessionDir) {
  const dir = path.join(ROOT_DIR, sessionDir);
  if (!fs.existsSync(dir)) return null;
  const jsonls = fs.readdirSync(dir).filter(n => n.startsWith('edits-') && n.endsWith('.jsonl'));
  return jsonls[0] ? path.join(dir, jsonls[0]) : null;
}

function readSessionState(sessionDir) {
  const file = path.join(ROOT_DIR, sessionDir, 'state.json');
  if (!fs.existsSync(file)) return { applied: {}, dismissed: {} };
  try {
    const obj = JSON.parse(fs.readFileSync(file, 'utf8'));
    return { applied: obj.applied || {}, dismissed: obj.dismissed || {} };
  } catch (_e) {
    return { applied: {}, dismissed: {} };
  }
}

function writeSessionState(sessionDir, state) {
  const file = path.join(ROOT_DIR, sessionDir, 'state.json');
  fs.writeFileSync(file, JSON.stringify(state, null, 2));
}

function parseSessionTs(sessionDir) {
  // edits-20260506-210909-1edits → 20260506-210909
  const m = /^edits-(\d{8}-\d{6})-/.exec(sessionDir);
  return m ? m[1] : sessionDir;
}

function loadEdits(sessionDir) {
  const jsonl = findJsonlInSession(sessionDir);
  if (!jsonl) return [];
  const lines = fs.readFileSync(jsonl, 'utf8').split('\n');
  const out = [];
  let idx = 0;
  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;
    try {
      const obj = JSON.parse(line);
      out.push({ index: idx, edit: obj });
    } catch (_e) {
      // Skip malformed lines; keep idx aligned with file positions.
    }
    idx++;
  }
  return out;
}

function decodeEditKey(editKey) {
  const parts = String(editKey).split('__');
  if (parts.length < 2) return null;
  const lineIndex = parseInt(parts.pop(), 10);
  const sessionTs = parts.join('__');
  if (!Number.isFinite(lineIndex) || !sessionTs) return null;
  // Find the session directory whose name carries this sessionTs.
  const dirs = listSessionDirs();
  const dir = dirs.find(d => d.startsWith(`edits-${sessionTs}-`));
  if (!dir) return null;
  return { sessionDir: dir, sessionTs, lineIndex };
}

module.exports = function(app, deps) {
  const { pgPool, requirePg } = deps;

  app.get('/admin/field-edits', (_req, res) => {
    try {
      const sessions = listSessionDirs().map(sessionDir => {
        const sessionTs = parseSessionTs(sessionDir);
        const state = readSessionState(sessionDir);
        const items = loadEdits(sessionDir).map(({ index, edit }) => {
          const editKey = `${sessionTs}__${index}`;
          let status = 'pending';
          if (state.applied[index]) status = 'applied';
          else if (state.dismissed[index]) status = 'dismissed';
          return {
            editKey,
            sessionDir,
            sessionTs,
            lineIndex: index,
            status,
            applied: state.applied[index] || null,
            dismissed: state.dismissed[index] || null,
            edit,
          };
        });
        return { sessionDir, sessionTs, count: items.length, items };
      });
      const totalPending = sessions.reduce(
        (n, s) => n + s.items.filter(i => i.status === 'pending').length, 0
      );
      res.json({ root: ROOT_DIR, totalPending, sessions });
    } catch (err) {
      console.error('[admin-field-edits] list failed:', err);
      res.status(500).json({ error: String(err.message || err) });
    }
  });

  // POST /admin/field-edits/sync — runs tools/pull-field-edits.py to refresh
  // local data from the connected Lenovo. Returns the script's stdout/stderr.
  app.post('/admin/field-edits/sync', (req, res) => {
    if (!fs.existsSync(PULL_SCRIPT)) {
      return res.status(500).json({ error: `pull script missing: ${PULL_SCRIPT}` });
    }
    const args = [PULL_SCRIPT];
    if (req.body && req.body.wipe === true) args.push('--wipe');
    const proc = spawn('python3', args, { timeout: 60_000 });
    let stdout = '';
    let stderr = '';
    proc.stdout.on('data', chunk => { stdout += chunk; });
    proc.stderr.on('data', chunk => { stderr += chunk; });
    proc.on('close', code => {
      res.json({ ok: code === 0, exit_code: code, stdout, stderr });
    });
    proc.on('error', err => {
      res.status(500).json({ ok: false, error: String(err.message || err) });
    });
  });

  // POST /admin/field-edits/:editKey/apply — writes the proposed change to
  // salem_pois via the existing admin paths (shares the audit log, admin_dirty
  // stamp, etc.). Marks the edit applied in the session sidecar so it stops
  // surfacing as pending.
  app.post('/admin/field-edits/:editKey/apply', requirePg, async (req, res) => {
    const decoded = decodeEditKey(req.params.editKey);
    if (!decoded) return res.status(400).json({ error: 'bad editKey' });
    const items = loadEdits(decoded.sessionDir);
    const found = items.find(i => i.index === decoded.lineIndex);
    if (!found) return res.status(404).json({ error: 'edit not found' });
    const e = found.edit;

    const poiId = String(e.poi_id || '');
    if (!poiId) return res.status(400).json({ error: 'edit has no poi_id' });

    const existing = await pgPool.query(
      `SELECT id, lat, lng, category, subcategory, deleted_at
         FROM salem_pois WHERE id = $1`,
      [poiId]
    );
    if (!existing.rows.length) {
      return res.status(404).json({ error: `POI ${poiId} not found in salem_pois` });
    }
    if (existing.rows[0].deleted_at) {
      return res.status(409).json({ error: `POI ${poiId} is soft-deleted` });
    }

    const applied = [];
    try {
      // 1. Move (lat/lng) — only if both proposed coords are present.
      if (e.proposed_lat != null && e.proposed_lng != null) {
        await pgPool.query(
          `UPDATE salem_pois
              SET lat = $1, lng = $2, admin_dirty = TRUE, admin_dirty_at = NOW()
            WHERE id = $3`,
          [Number(e.proposed_lat), Number(e.proposed_lng), poiId]
        );
        applied.push({
          field: 'location',
          from: { lat: existing.rows[0].lat, lng: existing.rows[0].lng },
          to: { lat: Number(e.proposed_lat), lng: Number(e.proposed_lng) },
        });
      }

      // 2. Category — uppercase canonical id from the picker.
      let newCategory = null;
      if (e.proposed_category && e.proposed_category !== existing.rows[0].category) {
        const cat = String(e.proposed_category).toUpperCase();
        await pgPool.query(
          `UPDATE salem_pois
              SET category = $1, admin_dirty = TRUE, admin_dirty_at = NOW()
            WHERE id = $2`,
          [cat, poiId]
        );
        applied.push({ field: 'category', from: existing.rows[0].category, to: cat });
        newCategory = cat;
      }

      // 3. Subcategory — Android picker sends a label (PoiSubtype.label),
      // resolve to canonical id via salem_poi_subcategories within the
      // effective parent category. If proposed_category was also set, use it;
      // otherwise the existing category.
      if (e.proposed_subcategory != null) {
        const parent = (newCategory || existing.rows[0].category || '').toUpperCase();
        const lookup = await pgPool.query(
          `SELECT id, label FROM salem_poi_subcategories
            WHERE category_id = $1 AND lower(label) = lower($2) LIMIT 1`,
          [parent, String(e.proposed_subcategory)]
        );
        let subId = null;
        if (lookup.rows.length) {
          subId = lookup.rows[0].id;
        } else {
          // No exact label match — leave the resolved id null and report so
          // the operator knows the apply was partial. Keep the rest of the
          // applied changes since we don't roll back here.
          applied.push({
            field: 'subcategory',
            warning: `no subcategory in ${parent} matches label "${e.proposed_subcategory}"`,
            resolved: null,
          });
        }
        if (subId) {
          await pgPool.query(
            `UPDATE salem_pois
                SET subcategory = $1, admin_dirty = TRUE, admin_dirty_at = NOW()
              WHERE id = $2`,
            [subId, poiId]
          );
          applied.push({
            field: 'subcategory',
            from: existing.rows[0].subcategory,
            to: subId,
            resolved_from_label: String(e.proposed_subcategory),
          });
        }
      }

      // Mark edit applied in the session sidecar.
      const state = readSessionState(decoded.sessionDir);
      state.applied[String(decoded.lineIndex)] = {
        ts: new Date().toISOString(),
        result: applied,
      };
      writeSessionState(decoded.sessionDir, state);

      res.json({ ok: true, applied });
    } catch (err) {
      console.error('[admin-field-edits] apply failed:', err);
      res.status(500).json({ error: String(err.message || err), partial: applied });
    }
  });

  // POST /admin/field-edits/:editKey/reject — sidecar dismiss. No DB write.
  app.post('/admin/field-edits/:editKey/reject', (req, res) => {
    const decoded = decodeEditKey(req.params.editKey);
    if (!decoded) return res.status(400).json({ error: 'bad editKey' });
    const reason = (req.body && req.body.reason) ? String(req.body.reason) : null;
    const state = readSessionState(decoded.sessionDir);
    state.dismissed[String(decoded.lineIndex)] = {
      ts: new Date().toISOString(),
      reason,
    };
    writeSessionState(decoded.sessionDir, state);
    res.json({ ok: true });
  });

  // GET /admin/field-edits/photo/:session/:filename — serve a recon photo
  // attached to the edit. Path-traversal guarded.
  app.get('/admin/field-edits/photo/:session/:filename', (req, res) => {
    const sessionDir = String(req.params.session);
    const filename = String(req.params.filename);
    if (!sessionDir.startsWith('edits-') || filename.includes('/') || filename.includes('..')) {
      return res.status(400).json({ error: 'bad path' });
    }
    const full = path.join(ROOT_DIR, sessionDir, 'photos', filename);
    const rootResolved = path.resolve(ROOT_DIR);
    const resolved = path.resolve(full);
    if (!resolved.startsWith(rootResolved + path.sep)) {
      return res.status(400).json({ error: 'path traversal' });
    }
    if (!fs.existsSync(resolved)) return res.status(404).json({ error: 'not found' });
    res.setHeader('Content-Type', 'image/jpeg');
    res.setHeader('Cache-Control', 'private, max-age=300');
    fs.createReadStream(resolved).pipe(res);
  });
};
