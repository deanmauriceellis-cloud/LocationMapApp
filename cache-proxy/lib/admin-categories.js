/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Admin endpoints for the POI taxonomy tables (S190).
 *
 * Surfaces salem_poi_categories and salem_poi_subcategories so the web
 * admin tool can drive POI Category / Subcategory dropdowns from the
 * canonical DB tables (rather than inferring values from observed POI
 * rows) and so the operator can add new entries inline from the POI
 * editor.
 *
 * Auth: gated by /admin Basic Auth middleware in cache-proxy/server.js.
 *
 * Routes:
 *   GET    /admin/salem/categories
 *   POST   /admin/salem/categories
 *   GET    /admin/salem/subcategories
 *   POST   /admin/salem/subcategories
 *
 * No PUT/DELETE in S190 — operator-side edits/removals are out of scope
 * (operator chose inline-create-only). The FK from salem_pois to both
 * tables means delete is non-trivial anyway.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module admin-categories.js';

// Default color for operator-created categories. Slate-blue, distinct from
// the existing palette so newly-added categories are visible at a glance
// until the operator picks a custom color.
const DEFAULT_NEW_CATEGORY_COLOR = '#607D8B';

// Validate / normalize a category id. Must be UPPERCASE_SNAKE_CASE,
// 2-40 chars, leading letter. Mirrors the convention used by the 20
// canonical categories.
function normalizeCategoryId(raw) {
  if (typeof raw !== 'string') return null;
  const trimmed = raw.trim().toUpperCase().replace(/[^A-Z0-9_]/g, '_');
  if (!/^[A-Z][A-Z0-9_]{1,39}$/.test(trimmed)) return null;
  return trimmed;
}

// Build a slug for a subcategory from its label. Lowercase, spaces and
// punctuation collapsed to single underscores, leading/trailing _ stripped.
function makeSlug(label) {
  if (typeof label !== 'string') return null;
  const slug = label
    .trim()
    .toLowerCase()
    .replace(/&/g, ' and ')
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
  if (!slug || slug.length > 40) return null;
  return slug;
}

// Validate a 6-digit hex color, falling back to a default. Accepts upper
// or lower case; normalizes to uppercase.
function normalizeColor(raw) {
  if (typeof raw !== 'string') return DEFAULT_NEW_CATEGORY_COLOR;
  const m = raw.trim().match(/^#?([0-9A-Fa-f]{6})$/);
  if (!m) return DEFAULT_NEW_CATEGORY_COLOR;
  return `#${m[1].toUpperCase()}`;
}

module.exports = function(app, deps) {
  const { pgPool, requirePg } = deps;

  // ─── GET /admin/salem/categories ─────────────────────────────────────────
  app.get('/admin/salem/categories', requirePg, async (_req, res) => {
    try {
      const { rows } = await pgPool.query(
        `SELECT id, label, pref_key, color,
                default_enabled, historic_tour_default,
                display_order, source, updated_at
           FROM salem_poi_categories
           ORDER BY display_order ASC, id ASC`
      );
      res.json({ categories: rows });
    } catch (e) {
      console.error('[admin-categories] GET categories failed:', e.message);
      res.status(500).json({ error: e.message });
    }
  });

  // ─── POST /admin/salem/categories ────────────────────────────────────────
  // Body: { id?, label, color? }
  // - id is optional — derived from label (uppercased, non-alnum→_) if absent.
  // - color is optional — defaults to DEFAULT_NEW_CATEGORY_COLOR.
  // - display_order is auto-assigned to MAX+1.
  // - pref_key is derived as `poi_<lower(id)>_on` to match the existing
  //   convention; it is only consumed by Android prefs, so an admin-tool-
  //   created category will not have a pref toggle until that side is
  //   updated. That's intentional — the operator can flip is_civic_poi /
  //   is_tour_poi flags to control visibility for new categories.
  app.post('/admin/salem/categories', requirePg, async (req, res) => {
    const body = req.body || {};
    const label = typeof body.label === 'string' ? body.label.trim() : '';
    if (!label || label.length > 60) {
      return res.status(400).json({ error: 'label must be a non-empty string ≤60 chars' });
    }
    const id = normalizeCategoryId(body.id || label);
    if (!id) {
      return res.status(400).json({
        error: 'could not derive a valid category id (need 2–40 chars, letters/digits/underscore, leading letter)',
      });
    }
    const color = normalizeColor(body.color);
    const prefKey = `poi_${id.toLowerCase()}_on`;

    try {
      // Already exists?
      const existing = await pgPool.query(
        `SELECT id FROM salem_poi_categories WHERE id = $1`,
        [id],
      );
      if (existing.rowCount > 0) {
        return res.status(409).json({ error: `category "${id}" already exists` });
      }

      const orderRow = await pgPool.query(
        `SELECT COALESCE(MAX(display_order), 0) AS m FROM salem_poi_categories`,
      );
      const displayOrder = (orderRow.rows[0]?.m ?? 0) + 1;

      const inserted = await pgPool.query(
        `INSERT INTO salem_poi_categories
           (id, label, pref_key, tags, color,
            default_enabled, historic_tour_default,
            display_order, source, updated_at)
         VALUES ($1, $2, $3, '[]'::jsonb, $4,
                 false, false,
                 $5, 'admin-tool', NOW())
         RETURNING id, label, pref_key, color,
                   default_enabled, historic_tour_default,
                   display_order, source, updated_at`,
        [id, label, prefKey, color, displayOrder],
      );
      res.status(201).json({ category: inserted.rows[0] });
    } catch (e) {
      console.error('[admin-categories] POST category failed:', e.message);
      res.status(500).json({ error: e.message });
    }
  });

  // ─── GET /admin/salem/subcategories ──────────────────────────────────────
  // Optional ?category_id=<X> filter. Without it, returns all rows so the
  // admin tool can build a full lookup map in one fetch.
  app.get('/admin/salem/subcategories', requirePg, async (req, res) => {
    const categoryId = typeof req.query.category_id === 'string'
      ? req.query.category_id.trim()
      : '';
    try {
      const params = [];
      let where = '';
      if (categoryId) {
        params.push(categoryId);
        where = `WHERE category_id = $1`;
      }
      const { rows } = await pgPool.query(
        `SELECT id, category_id, label, slug, display_order, source, updated_at
           FROM salem_poi_subcategories
           ${where}
           ORDER BY category_id ASC, display_order ASC, id ASC`,
        params,
      );
      res.json({ subcategories: rows });
    } catch (e) {
      console.error('[admin-categories] GET subcategories failed:', e.message);
      res.status(500).json({ error: e.message });
    }
  });

  // ─── POST /admin/salem/subcategories ─────────────────────────────────────
  // Body: { category_id, label }
  // - id is derived as `${category_id}__${slug}`, slug from label.
  // - display_order is MAX+1 within that category.
  app.post('/admin/salem/subcategories', requirePg, async (req, res) => {
    const body = req.body || {};
    const categoryId = typeof body.category_id === 'string' ? body.category_id.trim() : '';
    const label = typeof body.label === 'string' ? body.label.trim() : '';
    if (!categoryId) {
      return res.status(400).json({ error: 'category_id is required' });
    }
    if (!label || label.length > 60) {
      return res.status(400).json({ error: 'label must be a non-empty string ≤60 chars' });
    }
    const slug = makeSlug(label);
    if (!slug) {
      return res.status(400).json({ error: 'could not derive a valid slug from label' });
    }
    const id = `${categoryId}__${slug}`;

    try {
      const parent = await pgPool.query(
        `SELECT id FROM salem_poi_categories WHERE id = $1`,
        [categoryId],
      );
      if (parent.rowCount === 0) {
        return res.status(400).json({ error: `parent category "${categoryId}" does not exist` });
      }

      const dupe = await pgPool.query(
        `SELECT id FROM salem_poi_subcategories
          WHERE category_id = $1 AND slug = $2`,
        [categoryId, slug],
      );
      if (dupe.rowCount > 0) {
        return res.status(409).json({
          error: `subcategory "${id}" already exists in ${categoryId}`,
        });
      }

      const orderRow = await pgPool.query(
        `SELECT COALESCE(MAX(display_order), 0) AS m
           FROM salem_poi_subcategories
          WHERE category_id = $1`,
        [categoryId],
      );
      const displayOrder = (orderRow.rows[0]?.m ?? 0) + 1;

      const inserted = await pgPool.query(
        `INSERT INTO salem_poi_subcategories
           (id, category_id, label, slug, tags,
            display_order, source, updated_at)
         VALUES ($1, $2, $3, $4, '[]'::jsonb,
                 $5, 'admin-tool', NOW())
         RETURNING id, category_id, label, slug, display_order, source, updated_at`,
        [id, categoryId, label, slug, displayOrder],
      );
      res.status(201).json({ subcategory: inserted.rows[0] });
    } catch (e) {
      console.error('[admin-categories] POST subcategory failed:', e.message);
      res.status(500).json({ error: e.message });
    }
  });

  console.log(`[admin-categories] registered (id=${MODULE_ID})`);
};
