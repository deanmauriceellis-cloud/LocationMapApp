-- S196 — admin audit trigger.
-- One audit row per real change on the listed tables. Captures BEFORE+AFTER
-- snapshots, the changed-fields list, and (when set by the caller) the
-- actor + source. Idempotent: re-runnable.

-- ── Audit table ──────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS salem_audit_log (
  id              BIGSERIAL PRIMARY KEY,
  recorded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  table_name      TEXT        NOT NULL,
  entity_id       TEXT        NOT NULL,
  action          TEXT        NOT NULL CHECK (action IN ('INSERT','UPDATE','DELETE')),
  changed_fields  TEXT[]      NOT NULL DEFAULT '{}',
  old_values      JSONB,
  new_values      JSONB,
  actor           TEXT,
  source          TEXT,
  reverted        BOOLEAN     NOT NULL DEFAULT false,
  reverted_at     TIMESTAMPTZ,
  reverted_by     TEXT,
  revert_audit_id BIGINT      REFERENCES salem_audit_log(id),
  CONSTRAINT chk_revert CHECK (
    (reverted = false AND reverted_at IS NULL AND revert_audit_id IS NULL)
    OR (reverted = true)
  )
);

CREATE INDEX IF NOT EXISTS idx_audit_recorded_desc       ON salem_audit_log (recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_entity              ON salem_audit_log (table_name, entity_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_actor               ON salem_audit_log (actor, recorded_at DESC) WHERE actor IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_audit_source              ON salem_audit_log (source, recorded_at DESC) WHERE source IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_audit_open                ON salem_audit_log (reverted, recorded_at DESC) WHERE reverted = false;
CREATE INDEX IF NOT EXISTS idx_audit_changed_fields_gin  ON salem_audit_log USING GIN (changed_fields);

-- ── Helper: pick the row's id column (most tables use 'id', a few use composites) ─
-- Falls back to '(unknown)' if the row has no recognizable id field.
CREATE OR REPLACE FUNCTION salem_audit_extract_entity_id(rec JSONB) RETURNS TEXT
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE
  v TEXT;
BEGIN
  IF rec IS NULL THEN RETURN '(null)'; END IF;
  v := rec->>'id';
  IF v IS NOT NULL THEN RETURN v; END IF;
  v := rec->>'tour_id';
  IF v IS NOT NULL THEN RETURN v; END IF;
  v := rec->>'poi_id';
  IF v IS NOT NULL THEN RETURN v; END IF;
  v := rec->>'leg_id';
  IF v IS NOT NULL THEN RETURN v; END IF;
  v := rec->>'category_id';
  IF v IS NOT NULL THEN RETURN v; END IF;
  v := rec->>'article_id';
  IF v IS NOT NULL THEN RETURN v; END IF;
  RETURN '(unknown)';
END;
$$;

-- ── Trigger function ─────────────────────────────────────────────────────────
-- Skips meta-only changes (admin_dirty, admin_dirty_at, updated_at) since
-- those flip on every operator save and would dominate the log without
-- carrying signal. If a real field changed alongside a meta field, the
-- meta field IS still recorded as a changed_field (so the timeline shows
-- it correctly) — we only suppress when the diff is meta-only.
CREATE OR REPLACE FUNCTION salem_audit_trigger_fn() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
  old_j JSONB;
  new_j JSONB;
  changed TEXT[] := '{}';
  meta_only BOOLEAN;
  k TEXT;
  v_actor TEXT;
  v_source TEXT;
  entity TEXT;
  meta_fields TEXT[] := ARRAY['admin_dirty','admin_dirty_at','updated_at'];
BEGIN
  -- Caller sets these via `SET LOCAL "app.actor" = '...'` and `SET LOCAL "app.source" = '...'`.
  -- Both are NULL for direct PG operations with no context (manual psql, scripts
  -- that haven't been instrumented yet) — those rows still get audited but
  -- without actor/source attribution. NULLIF treats empty string as NULL.
  v_actor  := NULLIF(current_setting('app.actor',  true), '');
  v_source := NULLIF(current_setting('app.source', true), '');

  IF TG_OP = 'INSERT' THEN
    new_j := to_jsonb(NEW);
    entity := salem_audit_extract_entity_id(new_j);
    INSERT INTO salem_audit_log (table_name, entity_id, action, changed_fields, old_values, new_values, actor, source)
    VALUES (TG_TABLE_NAME, entity, 'INSERT', ARRAY(SELECT jsonb_object_keys(new_j)), NULL, new_j, v_actor, v_source);
    RETURN NEW;

  ELSIF TG_OP = 'UPDATE' THEN
    old_j := to_jsonb(OLD);
    new_j := to_jsonb(NEW);
    -- Diff: collect keys whose values differ
    FOR k IN SELECT jsonb_object_keys(old_j) UNION SELECT jsonb_object_keys(new_j)
    LOOP
      IF (old_j -> k) IS DISTINCT FROM (new_j -> k) THEN
        changed := array_append(changed, k);
      END IF;
    END LOOP;
    -- Skip the row if NOTHING changed (rare but possible)
    IF array_length(changed, 1) IS NULL THEN
      RETURN NEW;
    END IF;
    -- Skip if only meta fields changed (admin_dirty/admin_dirty_at/updated_at)
    meta_only := true;
    FOREACH k IN ARRAY changed
    LOOP
      IF NOT (k = ANY(meta_fields)) THEN
        meta_only := false;
        EXIT;
      END IF;
    END LOOP;
    IF meta_only THEN
      RETURN NEW;
    END IF;
    entity := salem_audit_extract_entity_id(new_j);
    INSERT INTO salem_audit_log (table_name, entity_id, action, changed_fields, old_values, new_values, actor, source)
    VALUES (TG_TABLE_NAME, entity, 'UPDATE', changed, old_j, new_j, v_actor, v_source);
    RETURN NEW;

  ELSIF TG_OP = 'DELETE' THEN
    old_j := to_jsonb(OLD);
    entity := salem_audit_extract_entity_id(old_j);
    INSERT INTO salem_audit_log (table_name, entity_id, action, changed_fields, old_values, new_values, actor, source)
    VALUES (TG_TABLE_NAME, entity, 'DELETE', ARRAY(SELECT jsonb_object_keys(old_j)), old_j, NULL, v_actor, v_source);
    RETURN OLD;
  END IF;
  RETURN NULL;
END;
$$;

-- ── Attach to admin tables ───────────────────────────────────────────────────
DO $$
DECLARE
  t TEXT;
  tables TEXT[] := ARRAY[
    'salem_pois',
    'salem_tours',
    'salem_tour_legs',
    'salem_tour_stops',
    'salem_poi_categories',
    'salem_poi_subcategories',
    'salem_witch_trials_articles',
    'salem_witch_trials_newspapers',
    'salem_witch_trials_npc_bios',
    'salem_geocode_blacklist'
  ];
BEGIN
  FOREACH t IN ARRAY tables LOOP
    -- Only attach if the table exists
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = t) THEN
      EXECUTE format('DROP TRIGGER IF EXISTS audit_%I ON %I', t, t);
      EXECUTE format('CREATE TRIGGER audit_%I AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION salem_audit_trigger_fn()', t, t);
      RAISE NOTICE 'Attached audit trigger to %', t;
    ELSE
      RAISE NOTICE 'Skipped (table missing): %', t;
    END IF;
  END LOOP;
END $$;

SELECT 'salem_audit_log' AS table, COUNT(*) AS rows FROM salem_audit_log
UNION ALL
SELECT trigger_name || ' on ' || event_object_table, NULL FROM information_schema.triggers WHERE trigger_name LIKE 'audit_salem_%' ORDER BY 1;
