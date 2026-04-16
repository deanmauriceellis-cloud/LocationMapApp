/*
 * LocationMapApp v1.5 — Phase 9X.7 (Session 133)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Admin endpoints for Witch Trials content (articles, NPC bios, newspapers).
 * All routes are mounted under /admin/salem/witch-trials/* and gated by the
 * /admin Basic Auth middleware (see lib/admin-auth.js).
 *
 * Routes:
 *   GET    /admin/salem/witch-trials/articles          — list all articles
 *   GET    /admin/salem/witch-trials/articles/:id       — single article
 *   PUT    /admin/salem/witch-trials/articles/:id       — partial update
 *
 *   GET    /admin/salem/witch-trials/bios               — list all bios
 *   GET    /admin/salem/witch-trials/bios/:id           — single bio
 *   PUT    /admin/salem/witch-trials/bios/:id           — partial update
 *
 *   GET    /admin/salem/witch-trials/newspapers          — list all newspapers
 *   GET    /admin/salem/witch-trials/newspapers/:id      — single newspaper
 *   PUT    /admin/salem/witch-trials/newspapers/:id      — partial update
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module admin-witch-trials.js';

// ─── Field whitelists ───────────────────────────────────────────────────────

const ARTICLE_UPDATABLE = [
  'title', 'period_label', 'teaser', 'body',
  'related_npc_ids', 'related_event_ids', 'related_newspaper_dates',
  'data_source', 'confidence', 'verified_date', 'generator_model',
];

const BIO_UPDATABLE = [
  'name', 'display_name', 'tier', 'role', 'faction',
  'born_year', 'died_year', 'age_in_1692', 'historical_outcome',
  'bio',
  'related_npc_ids', 'related_event_ids', 'related_newspaper_dates',
  'portrait_asset',
  'data_source', 'confidence', 'verified_date', 'generator_model',
];

const NEWSPAPER_UPDATABLE = [
  'summary', 'lede', 'body_points', 'tts_full_text',
  'headline', 'headline_summary',
  'events_referenced',
  'data_source', 'confidence', 'verified_date', 'generator_model',
];

const JSONB_FIELDS = new Set([
  'related_npc_ids', 'related_event_ids', 'related_newspaper_dates',
  'body_points', 'events_referenced',
]);

// ─── Helpers ────────────────────────────────────────────────────────────────

function getId(req, res) {
  const id = req.params.id;
  if (typeof id !== 'string' || !id.trim()) {
    res.status(400).json({ error: 'id is required' });
    return null;
  }
  return id;
}

function buildUpdateClause(body, whitelist) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) {
    return { error: 'request body must be a JSON object' };
  }
  const setParts = [];
  const values = [];
  let idx = 1;

  for (const field of whitelist) {
    if (!Object.prototype.hasOwnProperty.call(body, field)) continue;
    let value = body[field];
    if (JSONB_FIELDS.has(field) && typeof value !== 'string') {
      value = JSON.stringify(value);
    }
    setParts.push(`${field} = $${idx}`);
    values.push(value);
    idx++;
  }

  if (setParts.length === 0) {
    return { error: 'no updatable fields provided' };
  }

  return { setSql: setParts.join(', '), values };
}

// ─── Route registration ─────────────────────────────────────────────────────

module.exports = function adminWitchTrials(app, deps) {
  const { pool } = deps;

  // ── Articles ──────────────────────────────────────────────────────────────

  app.get('/admin/salem/witch-trials/articles', async (req, res) => {
    try {
      const result = await pool.query(
        'SELECT * FROM salem_witch_trials_articles ORDER BY tile_order ASC'
      );
      res.json({ count: result.rows.length, articles: result.rows });
    } catch (err) {
      console.error('[admin-wt] articles list error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  app.get('/admin/salem/witch-trials/articles/:id', async (req, res) => {
    const id = getId(req, res);
    if (!id) return;
    try {
      const result = await pool.query(
        'SELECT * FROM salem_witch_trials_articles WHERE id = $1', [id]
      );
      if (result.rows.length === 0) return res.status(404).json({ error: 'not found' });
      res.json(result.rows[0]);
    } catch (err) {
      console.error('[admin-wt] article get error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  app.put('/admin/salem/witch-trials/articles/:id', async (req, res) => {
    const id = getId(req, res);
    if (!id) return;
    const { setSql, values, error } = buildUpdateClause(req.body, ARTICLE_UPDATABLE);
    if (error) return res.status(400).json({ error });
    try {
      values.push(id);
      const result = await pool.query(
        `UPDATE salem_witch_trials_articles SET ${setSql} WHERE id = $${values.length} RETURNING *`,
        values
      );
      if (result.rows.length === 0) return res.status(404).json({ error: 'not found' });
      res.json(result.rows[0]);
    } catch (err) {
      console.error('[admin-wt] article update error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ── NPC Bios ──────────────────────────────────────────────────────────────

  app.get('/admin/salem/witch-trials/bios', async (req, res) => {
    try {
      const result = await pool.query(
        'SELECT * FROM salem_witch_trials_npc_bios ORDER BY tier ASC, name ASC'
      );
      res.json({ count: result.rows.length, bios: result.rows });
    } catch (err) {
      console.error('[admin-wt] bios list error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  app.get('/admin/salem/witch-trials/bios/:id', async (req, res) => {
    const id = getId(req, res);
    if (!id) return;
    try {
      const result = await pool.query(
        'SELECT * FROM salem_witch_trials_npc_bios WHERE id = $1', [id]
      );
      if (result.rows.length === 0) return res.status(404).json({ error: 'not found' });
      res.json(result.rows[0]);
    } catch (err) {
      console.error('[admin-wt] bio get error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  app.put('/admin/salem/witch-trials/bios/:id', async (req, res) => {
    const id = getId(req, res);
    if (!id) return;
    const { setSql, values, error } = buildUpdateClause(req.body, BIO_UPDATABLE);
    if (error) return res.status(400).json({ error });
    try {
      values.push(id);
      const result = await pool.query(
        `UPDATE salem_witch_trials_npc_bios SET ${setSql} WHERE id = $${values.length} RETURNING *`,
        values
      );
      if (result.rows.length === 0) return res.status(404).json({ error: 'not found' });
      res.json(result.rows[0]);
    } catch (err) {
      console.error('[admin-wt] bio update error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ── Newspapers ────────────────────────────────────────────────────────────

  app.get('/admin/salem/witch-trials/newspapers', async (req, res) => {
    try {
      const result = await pool.query(
        'SELECT * FROM salem_witch_trials_newspapers ORDER BY date ASC'
      );
      res.json({ count: result.rows.length, newspapers: result.rows });
    } catch (err) {
      console.error('[admin-wt] newspapers list error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  app.get('/admin/salem/witch-trials/newspapers/:id', async (req, res) => {
    const id = getId(req, res);
    if (!id) return;
    try {
      const result = await pool.query(
        'SELECT * FROM salem_witch_trials_newspapers WHERE id = $1', [id]
      );
      if (result.rows.length === 0) return res.status(404).json({ error: 'not found' });
      res.json(result.rows[0]);
    } catch (err) {
      console.error('[admin-wt] newspaper get error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  app.put('/admin/salem/witch-trials/newspapers/:id', async (req, res) => {
    const id = getId(req, res);
    if (!id) return;
    const { setSql, values, error } = buildUpdateClause(req.body, NEWSPAPER_UPDATABLE);
    if (error) return res.status(400).json({ error });
    try {
      values.push(id);
      const result = await pool.query(
        `UPDATE salem_witch_trials_newspapers SET ${setSql} WHERE id = $${values.length} RETURNING *`,
        values
      );
      if (result.rows.length === 0) return res.status(404).json({ error: 'not found' });
      res.json(result.rows[0]);
    } catch (err) {
      console.error('[admin-wt] newspaper update error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  console.log('AdminWT: GET/PUT /admin/salem/witch-trials/{articles,bios,newspapers}[/:id] (Basic Auth)');
};
