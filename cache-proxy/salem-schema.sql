/*
 * WickedSalemWitchCityTour — Salem Content Schema
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * Extends the LocationMapApp PostgreSQL database with Salem-specific
 * content tables. Backward compatible — no changes to existing tables.
 *
 * Usage: psql -U postgres -d locationmapapp -f salem-schema.sql
 */

-- ════════════════════════════════════════════════════════════════════
-- Provenance extension for existing pois table (backward compatible)
-- ════════════════════════════════════════════════════════════════════

ALTER TABLE pois ADD COLUMN IF NOT EXISTS data_source TEXT DEFAULT 'overpass_import';
ALTER TABLE pois ADD COLUMN IF NOT EXISTS confidence REAL DEFAULT 0.8;
ALTER TABLE pois ADD COLUMN IF NOT EXISTS verified_date TIMESTAMPTZ;
ALTER TABLE pois ADD COLUMN IF NOT EXISTS stale_after TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_pois_data_source ON pois (data_source);
CREATE INDEX IF NOT EXISTS idx_pois_stale_after ON pois (stale_after) WHERE stale_after IS NOT NULL;

-- ════════════════════════════════════════════════════════════════════
-- Legacy POI tables — REMOVED in Phase 9U (Session 126, 2026-04-15)
-- ════════════════════════════════════════════════════════════════════
-- The three pre-unification tables — salem_tour_pois, salem_businesses,
-- salem_narration_points — were merged into salem_pois in Phase 9U
-- (Session 117, see below) and dropped from PG. Their CREATE statements
-- and indexes have been removed from this file. The old importer
-- scripts (cache-proxy/scripts/import-narration-points.js,
-- import-tour-pois-and-businesses.js, migrate-to-unified-pois.js) are
-- preserved in git history; do not re-run them.
-- ════════════════════════════════════════════════════════════════════

-- ════════════════════════════════════════════════════════════════════
-- Historical Figures
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_historical_figures (
  id                     TEXT PRIMARY KEY,
  name                   TEXT NOT NULL,
  first_name             TEXT NOT NULL,
  surname                TEXT NOT NULL,
  born                   TEXT,
  died                   TEXT,
  age_in_1692            INTEGER,
  role                   TEXT NOT NULL,
  faction                TEXT,
  short_bio              TEXT NOT NULL,
  full_bio               TEXT,
  narration_script       TEXT,
  appearance_description TEXT,
  role_in_crisis         TEXT,
  historical_outcome     TEXT,
  key_quotes             JSONB DEFAULT '[]',
  family_connections     JSONB DEFAULT '{}',
  primary_poi_id         TEXT REFERENCES salem_pois(id),
  -- Provenance & Staleness
  data_source            TEXT NOT NULL DEFAULT 'salem_project',
  confidence             REAL NOT NULL DEFAULT 1.0,
  verified_date          TIMESTAMPTZ,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after            TIMESTAMPTZ
);

-- ════════════════════════════════════════════════════════════════════
-- Historical Facts
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_historical_facts (
  id               TEXT PRIMARY KEY,
  title            TEXT NOT NULL,
  description      TEXT NOT NULL,
  date             TEXT,
  date_precision   TEXT,
  category         TEXT,
  subcategory      TEXT,
  poi_id           TEXT REFERENCES salem_pois(id),
  figure_id        TEXT REFERENCES salem_historical_figures(id),
  source_citation  TEXT,
  narration_script TEXT,
  confidentiality  TEXT DEFAULT 'public',
  tags             JSONB DEFAULT '[]',
  -- Provenance & Staleness
  data_source      TEXT NOT NULL DEFAULT 'salem_project',
  confidence       REAL NOT NULL DEFAULT 1.0,
  verified_date    TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after      TIMESTAMPTZ
);

-- ════════════════════════════════════════════════════════════════════
-- Timeline Events
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_timeline_events (
  id               TEXT PRIMARY KEY,
  name             TEXT NOT NULL,
  date             TEXT NOT NULL,
  crisis_phase     TEXT,
  description      TEXT NOT NULL,
  poi_id           TEXT REFERENCES salem_pois(id),
  figures_involved JSONB DEFAULT '[]',
  narration_script TEXT,
  is_anchor        BOOLEAN DEFAULT FALSE,
  -- Provenance & Staleness
  data_source      TEXT NOT NULL DEFAULT 'salem_project',
  confidence       REAL NOT NULL DEFAULT 1.0,
  verified_date    TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after      TIMESTAMPTZ
);

-- ════════════════════════════════════════════════════════════════════
-- Primary Sources
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_primary_sources (
  id               TEXT PRIMARY KEY,
  title            TEXT NOT NULL,
  source_type      TEXT NOT NULL,
  author           TEXT,
  date             TEXT,
  full_text        TEXT,
  excerpt          TEXT,
  figure_id        TEXT REFERENCES salem_historical_figures(id),
  poi_id           TEXT REFERENCES salem_pois(id),
  narration_script TEXT,
  citation         TEXT,
  -- Provenance & Staleness
  data_source      TEXT NOT NULL DEFAULT 'salem_project',
  confidence       REAL NOT NULL DEFAULT 1.0,
  verified_date    TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after      TIMESTAMPTZ
);

-- ════════════════════════════════════════════════════════════════════
-- Tours
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_tours (
  id                TEXT PRIMARY KEY,
  name              TEXT NOT NULL,
  theme             TEXT NOT NULL,
  description       TEXT NOT NULL,
  estimated_minutes INTEGER NOT NULL,
  distance_km       REAL NOT NULL,
  stop_count        INTEGER NOT NULL,
  difficulty        TEXT DEFAULT 'moderate',
  seasonal          BOOLEAN DEFAULT FALSE,
  icon_asset        TEXT,
  sort_order        INTEGER DEFAULT 0,
  -- Provenance & Staleness
  data_source       TEXT NOT NULL DEFAULT 'manual_curated',
  confidence        REAL NOT NULL DEFAULT 1.0,
  verified_date     TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after       TIMESTAMPTZ
);

-- ════════════════════════════════════════════════════════════════════
-- Tour Stops (join table)
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_tour_stops (
  stop_id                 BIGSERIAL PRIMARY KEY,
  tour_id                 TEXT NOT NULL REFERENCES salem_tours(id),
  -- Nullable so a tour stop can be a free-floating waypoint (no POI link).
  poi_id                  TEXT REFERENCES salem_pois(id),
  stop_order              INTEGER NOT NULL,
  -- Per-tour coord override. Effective coord = COALESCE(stop.lat, poi.lat).
  lat                     DOUBLE PRECISION,
  lng                     DOUBLE PRECISION,
  -- Label override for free-floating waypoints or per-tour rename.
  name                    TEXT,
  transition_narration    TEXT,
  walking_minutes_from_prev INTEGER,
  distance_m_from_prev    INTEGER,
  -- Provenance & Staleness
  data_source             TEXT NOT NULL DEFAULT 'manual_curated',
  confidence              REAL NOT NULL DEFAULT 1.0,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after             TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS salem_tour_stops_tour_order_idx
  ON salem_tour_stops (tour_id, stop_order);

-- ════════════════════════════════════════════════════════════════════
-- Tour Legs (S183) — precomputed walking polylines between consecutive
-- stops. Authored once via the admin "Compute Route" tool against the
-- same on-device router the APK ships, persisted as content, and baked
-- into salem_content.db so the runtime never has to route at all.
-- One row per consecutive (stop_order n → n+1) pair.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_tour_legs (
  tour_id        TEXT    NOT NULL REFERENCES salem_tours(id) ON DELETE CASCADE,
  leg_order      INTEGER NOT NULL,
  from_stop_id   BIGINT  NOT NULL REFERENCES salem_tour_stops(stop_id) ON DELETE CASCADE,
  to_stop_id     BIGINT  NOT NULL REFERENCES salem_tour_stops(stop_id) ON DELETE CASCADE,
  -- Polyline as a JSONB array of [lat, lng] pairs (matches the bundle
  -- router's geometry output and the Android renderer's input format).
  polyline_json  JSONB   NOT NULL,
  distance_m     REAL    NOT NULL,
  duration_s     REAL    NOT NULL,
  -- Bundle identifier so we can detect stale legs after a router refresh.
  router_version TEXT,
  -- Null until an operator hand-edits vertices in the admin tool. When
  -- set, "recompute" leaves the row alone unless force-recompute is asked.
  manual_edits   JSONB,
  computed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (tour_id, leg_order)
);
CREATE INDEX IF NOT EXISTS salem_tour_legs_from_idx
  ON salem_tour_legs (from_stop_id);
CREATE INDEX IF NOT EXISTS salem_tour_legs_to_idx
  ON salem_tour_legs (to_stop_id);

-- ════════════════════════════════════════════════════════════════════
-- Events Calendar
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_events_calendar (
  id                 TEXT PRIMARY KEY,
  name               TEXT NOT NULL,
  venue_poi_id       TEXT REFERENCES salem_pois(id),
  event_type         TEXT NOT NULL,
  description        TEXT,
  start_date         TEXT,
  end_date           TEXT,
  hours              TEXT,
  admission          TEXT,
  website            TEXT,
  recurring          BOOLEAN DEFAULT FALSE,
  recurrence_pattern TEXT,
  seasonal_month     INTEGER,
  -- Provenance & Staleness
  data_source        TEXT NOT NULL DEFAULT 'manual_curated',
  confidence         REAL NOT NULL DEFAULT 1.0,
  verified_date      TIMESTAMPTZ,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after        TIMESTAMPTZ
);

-- ════════════════════════════════════════════════════════════════════
-- Content Sync Tracking — tracks what the app has synced from the server
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_sync_state (
  table_name   TEXT PRIMARY KEY,
  last_sync_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  row_count    INTEGER DEFAULT 0,
  checksum     TEXT
);

-- ════════════════════════════════════════════════════════════════════
-- POI Taxonomy (Phase 9P.B+, Session 114)
-- ════════════════════════════════════════════════════════════════════
--
-- Two lookup tables mirroring the canonical POI taxonomy defined in
--   app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/menu/PoiCategories.kt
--   web/src/config/poiCategories.ts              (line-aligned mirror)
--
-- 22 categories x ~176 subcategories (exact count comes from the seed
-- script `cache-proxy/scripts/sync-poi-taxonomy.js` — rerun that script
-- whenever poiCategories.ts changes).
--
-- Subcategory PK format: `{CATEGORY_ID}__{slug}` — e.g. `FOOD_DRINK__restaurants`.
-- This lets POI tables FK directly to the subcategory row without needing
-- a separate category_id column on the POI side.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_poi_categories (
  id                     TEXT PRIMARY KEY,            -- e.g. 'FOOD_DRINK' — matches PoiLayerId
  label                  TEXT NOT NULL,               -- e.g. 'Food & Drink'
  pref_key               TEXT NOT NULL,               -- e.g. 'poi_food_drink_on'
  tags                   JSONB NOT NULL DEFAULT '[]', -- flat OSM tag list, key=value form
  color                  TEXT NOT NULL,               -- e.g. '#BF360C'
  default_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
  historic_tour_default  BOOLEAN NOT NULL DEFAULT FALSE,
  display_order          INTEGER NOT NULL,            -- 1..N from POI_CATEGORIES array order
  source                 TEXT NOT NULL DEFAULT 'poiCategories.ts',
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS salem_poi_subcategories (
  id                     TEXT PRIMARY KEY,            -- e.g. 'FOOD_DRINK__restaurants'
  category_id            TEXT NOT NULL REFERENCES salem_poi_categories(id) ON DELETE RESTRICT,
  label                  TEXT NOT NULL,               -- e.g. 'Restaurants'
  slug                   TEXT NOT NULL,               -- e.g. 'restaurants' (unique within category)
  tags                   JSONB NOT NULL DEFAULT '[]', -- per-subtype OSM tag list
  display_order          INTEGER NOT NULL,            -- 1..N from subtypes array order
  source                 TEXT NOT NULL DEFAULT 'poiCategories.ts',
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (category_id, slug)
);

CREATE INDEX IF NOT EXISTS idx_salem_poi_subcat_category ON salem_poi_subcategories (category_id);

-- ════════════════════════════════════════════════════════════════════
-- Unified Salem POIs — Phase 9U (Session 117)
--
-- Single canonical table for every Salem POI — restaurant, witch shop,
-- dentist, park, historic house. Merged from the three pre-unification
-- tables (salem_tour_pois 45 + salem_businesses 861 + salem_narration_points
-- 817) which were dropped in S126. Superset schema + SalemIntelligence
-- BCS enrichment columns from the S118 import.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_pois (
  -- ── Core identity ───────────────────────────────────────────────
  id                     TEXT PRIMARY KEY,
  name                   TEXT NOT NULL,
  lat                    DOUBLE PRECISION NOT NULL,
  lng                    DOUBLE PRECISION NOT NULL,
  address                TEXT,
  status                 TEXT DEFAULT 'open',           -- open / temporarily_closed / seasonal / unknown

  -- ── Taxonomy (FK-enforced) ──────────────────────────────────────
  category               TEXT REFERENCES salem_poi_categories(id),
  subcategory            TEXT REFERENCES salem_poi_subcategories(id),

  -- ── Narration layer (null for non-narrated POIs) ────────────────
  short_narration        TEXT,
  long_narration         TEXT,
  geofence_radius_m      INTEGER DEFAULT 40,
  geofence_shape         TEXT DEFAULT 'circle',         -- 'circle' | 'corridor'
  corridor_points        TEXT,                           -- serialized polyline for corridor shape
  priority               INTEGER DEFAULT 3,
  wave                   INTEGER,
  voice_clip_asset       TEXT,
  custom_voice_asset     TEXT,

  -- ── Business layer (null for parks/public art/etc) ──────────────
  cuisine_type           TEXT,
  price_range            TEXT,
  rating                 REAL,
  merchant_tier          INTEGER DEFAULT 0,              -- 0=none, 1=basic, 2=premium, 3=featured
  ad_priority            INTEGER DEFAULT 0,

  -- ── Historical/tour layer (null for non-historic POIs) ──────────
  historical_period      TEXT,
  historical_note        TEXT,
  admission_info         TEXT,
  requires_transportation BOOLEAN DEFAULT false,
  wheelchair_accessible  BOOLEAN DEFAULT true,
  seasonal               BOOLEAN DEFAULT false,

  -- ── Contact/hours ───────────────────────────────────────────────
  phone                  TEXT,
  email                  TEXT,
  website                TEXT,
  hours                  JSONB,                          -- structured JSON (upgrade from text)
  hours_text             TEXT,                            -- preserve legacy freeform hours strings
  menu_url               TEXT,
  reservations_url       TEXT,
  order_url              TEXT,

  -- ── Content ─────────────────────────────────────────────────────
  description            TEXT,
  short_description      TEXT,
  custom_description     TEXT,
  origin_story           TEXT,
  image_asset            TEXT,
  custom_icon_asset      TEXT,
  action_buttons         JSONB DEFAULT '[]',

  -- ── SalemIntelligence enrichment ────────────────────────────────
  intel_entity_id        TEXT,                            -- BCS entity_id FK for v2 online features
  secondary_categories   JSONB DEFAULT '[]',
  specialties            JSONB DEFAULT '[]',
  owners                 JSONB DEFAULT '[]',
  year_established       INTEGER,
  amenities              JSONB DEFAULT '{}',
  district               TEXT,

  -- ── Relations ───────────────────────────────────────────────────
  related_figure_ids     JSONB DEFAULT '[]',
  related_fact_ids       JSONB DEFAULT '[]',
  related_source_ids     JSONB DEFAULT '[]',
  source_id              TEXT,
  source_categories      JSONB DEFAULT '[]',
  tags                   JSONB DEFAULT '[]',

  -- ── Provenance ──────────────────────────────────────────────────
  data_source            TEXT NOT NULL DEFAULT 'unified_migration',
  confidence             REAL NOT NULL DEFAULT 0.8,
  verified_date          TIMESTAMPTZ,
  stale_after            TIMESTAMPTZ,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at             TIMESTAMPTZ,                    -- soft delete (NULL = active)

  -- ── Flags ───────────────────────────────────────────────────────
  is_tour_poi            BOOLEAN DEFAULT false,          -- the 45 curated 1692 stops
  is_narrated            BOOLEAN DEFAULT false,          -- has narration content
  default_visible        BOOLEAN DEFAULT true,           -- per-POI visibility override

  -- ── Legacy audit (dropped after Phase 9U verification) ──────────
  legacy_narration_category TEXT,
  legacy_business_type   TEXT,
  legacy_tour_category   TEXT
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_spois_category        ON salem_pois (category);
CREATE INDEX IF NOT EXISTS idx_spois_lat_lng         ON salem_pois (lat, lng);
CREATE INDEX IF NOT EXISTS idx_spois_data_source     ON salem_pois (data_source);
CREATE INDEX IF NOT EXISTS idx_spois_active          ON salem_pois (id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_spois_merchant_tier   ON salem_pois (merchant_tier) WHERE merchant_tier > 0;
CREATE INDEX IF NOT EXISTS idx_spois_stale           ON salem_pois (stale_after) WHERE stale_after IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_spois_wave            ON salem_pois (wave) WHERE wave IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_spois_intel_entity    ON salem_pois (intel_entity_id) WHERE intel_entity_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_spois_tour            ON salem_pois (id) WHERE is_tour_poi = true;
CREATE INDEX IF NOT EXISTS idx_spois_district        ON salem_pois (district) WHERE district IS NOT NULL;

-- ════════════════════════════════════════════════════════════════════
-- The Salem Witch Trials feature (Phase 9X, S127)
--
-- Three tables backing the new top-level "Salem Witch Trials" panel:
--   salem_witch_trials_articles   — 16 history tile articles
--                                   (intro + 12 months of 1692 + fallout +
--                                    closing + epilogue)
--   salem_witch_trials_npc_bios   — ~49 Tier 1+2 NPC biographies
--   salem_witch_trials_newspapers — 202 1692-era newspaper articles
--                                   (sourced from ~/Development/Salem)
--
-- All three follow the salem_pois pattern: admin_dirty + soft-delete
-- + provenance fields (data_source / confidence / verified_date), so
-- the admin web tool can edit drafts and the publish script can bake
-- non-deleted rows into bundled JSON assets in the APK.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_witch_trials_articles (
  -- Identity
  id                     TEXT PRIMARY KEY,                       -- e.g. 'pre_1692', 'jan_1692', ..., 'fallout_1693', 'closing', 'epilogue'
  tile_order             INTEGER NOT NULL,                       -- 1..16 (display order in 4x4 grid)
  tile_kind              TEXT NOT NULL,                          -- 'intro' | 'month' | 'fallout' | 'closing' | 'epilogue'
  -- Content
  title                  TEXT NOT NULL,                          -- e.g. 'March 1692 — The Examinations Begin'
  period_label           TEXT,                                   -- e.g. 'March 1, 1692 → March 31, 1692'
  teaser                 TEXT NOT NULL,                          -- ~80 word card-face summary
  body                   TEXT NOT NULL,                          -- 500-1000 word article body, may contain [[entity_id]] markup
  -- Cross-link metadata
  related_npc_ids        JSONB NOT NULL DEFAULT '[]',            -- NPC ids referenced in body
  related_event_ids      JSONB NOT NULL DEFAULT '[]',            -- event ids referenced in body
  related_newspaper_dates JSONB NOT NULL DEFAULT '[]',           -- newspaper YYYY-MM-DD strings referenced
  -- Provenance
  data_source            TEXT NOT NULL DEFAULT 'salem_oracle',   -- 'salem_oracle' | 'claude_generated' | 'human_authored'
  confidence             REAL NOT NULL DEFAULT 0.7,
  verified_date          TIMESTAMPTZ,                            -- NULL until human-reviewed
  generator_model        TEXT,                                   -- e.g. 'gemma3:27b'
  generator_prompt_hash  TEXT,                                   -- so we can detect prompt drift
  -- Editorial workflow
  admin_dirty            BOOLEAN NOT NULL DEFAULT FALSE,         -- flipped TRUE by admin tool edits
  admin_dirty_at         TIMESTAMPTZ,
  -- Lifecycle
  created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at             TIMESTAMPTZ                             -- soft delete (NULL = active)
);

CREATE INDEX IF NOT EXISTS idx_swta_order         ON salem_witch_trials_articles (tile_order);
CREATE INDEX IF NOT EXISTS idx_swta_kind          ON salem_witch_trials_articles (tile_kind);
CREATE INDEX IF NOT EXISTS idx_swta_active        ON salem_witch_trials_articles (id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_swta_admin_dirty   ON salem_witch_trials_articles (admin_dirty) WHERE admin_dirty = TRUE;
CREATE INDEX IF NOT EXISTS idx_swta_unverified    ON salem_witch_trials_articles (verified_date) WHERE verified_date IS NULL;


CREATE TABLE IF NOT EXISTS salem_witch_trials_npc_bios (
  -- Identity (id matches Salem corpus NPC id, e.g. 'rebecca_nurse')
  id                     TEXT PRIMARY KEY,
  name                   TEXT NOT NULL,                          -- 'Rebecca Nurse'
  display_name           TEXT,                                   -- 'Rebecca Towne Nurse' (longer form for header)
  -- Salem-corpus metadata snapshot (for fast reads w/o hitting JSON)
  tier                   INTEGER NOT NULL,                       -- 1 or 2 (V1 ships Tier 1+2 only)
  role                   TEXT NOT NULL,                          -- 'Lead judge of the Court of Oyer and Terminer'
  faction                TEXT,                                   -- 'Authority (hardliner)' | 'Accused' | 'Accuser' etc
  born_year              INTEGER,
  died_year              INTEGER,
  age_in_1692            INTEGER,
  historical_outcome     TEXT,                                   -- 1-2 sentence outcome summary (e.g. 'Executed July 19, 1692. Pardoned 1711.')
  -- Generated content
  bio                    TEXT NOT NULL,                          -- ~500 word LLM bio, may contain [[entity_id]] markup
  -- Cross-link metadata
  related_npc_ids        JSONB NOT NULL DEFAULT '[]',
  related_event_ids      JSONB NOT NULL DEFAULT '[]',
  related_newspaper_dates JSONB NOT NULL DEFAULT '[]',
  -- Portrait (Phase 6)
  portrait_asset         TEXT,                                   -- 'witch_trials/portraits/rebecca_nurse.jpg' (NULL until Phase 6)
  -- Provenance
  data_source            TEXT NOT NULL DEFAULT 'salem_oracle',
  confidence             REAL NOT NULL DEFAULT 0.7,
  verified_date          TIMESTAMPTZ,
  generator_model        TEXT,
  generator_prompt_hash  TEXT,
  -- Editorial workflow
  admin_dirty            BOOLEAN NOT NULL DEFAULT FALSE,
  admin_dirty_at         TIMESTAMPTZ,
  -- Lifecycle
  created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at             TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_swtnb_tier         ON salem_witch_trials_npc_bios (tier);
CREATE INDEX IF NOT EXISTS idx_swtnb_faction      ON salem_witch_trials_npc_bios (faction);
CREATE INDEX IF NOT EXISTS idx_swtnb_active       ON salem_witch_trials_npc_bios (id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_swtnb_admin_dirty  ON salem_witch_trials_npc_bios (admin_dirty) WHERE admin_dirty = TRUE;
CREATE INDEX IF NOT EXISTS idx_swtnb_unverified   ON salem_witch_trials_npc_bios (verified_date) WHERE verified_date IS NULL;


CREATE TABLE IF NOT EXISTS salem_witch_trials_newspapers (
  -- Identity (id matches Salem corpus, e.g. 'salem_oracle_1692-03-01')
  id                     TEXT PRIMARY KEY,
  date                   TEXT NOT NULL,                          -- ISO YYYY-MM-DD (e.g. '1692-03-01')
  day_of_week            TEXT,                                   -- 'Tuesday'
  long_date              TEXT,                                   -- 'March 1, 1692'
  crisis_phase           INTEGER NOT NULL DEFAULT 0,             -- 0=pre-crisis .. 6=aftermath
  -- Content (denormalized for fast read)
  summary                TEXT,                                   -- 1-2 sentence summary (card face)
  lede                   TEXT,                                   -- opening paragraph
  tts_full_text          TEXT NOT NULL,                          -- full Oracle Newspaper article: drives BOTH map-mode TTS narration and the in-app newspaper page render (S198)
  -- Source attribution
  events_referenced      JSONB DEFAULT '[]',
  event_count            INTEGER DEFAULT 0,
  fact_count             INTEGER DEFAULT 0,
  primary_source_count   INTEGER DEFAULT 0,
  -- Provenance
  data_source            TEXT NOT NULL DEFAULT 'salem_corpus',   -- bundled from ~/Development/Salem newspapers
  confidence             REAL NOT NULL DEFAULT 0.85,
  verified_date          TIMESTAMPTZ,
  generator_model        TEXT,                                   -- 'salem-village (gemma3:27b)' from corpus
  generated_at           TIMESTAMPTZ,                            -- as produced by Salem corpus
  -- Editorial workflow
  admin_dirty            BOOLEAN NOT NULL DEFAULT FALSE,
  admin_dirty_at         TIMESTAMPTZ,
  -- Lifecycle
  created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at             TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_swtn_date          ON salem_witch_trials_newspapers (date);
CREATE INDEX IF NOT EXISTS idx_swtn_phase         ON salem_witch_trials_newspapers (crisis_phase);
CREATE INDEX IF NOT EXISTS idx_swtn_active        ON salem_witch_trials_newspapers (id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_swtn_admin_dirty   ON salem_witch_trials_newspapers (admin_dirty) WHERE admin_dirty = TRUE;

-- ════════════════════════════════════════════════════════════════════
-- Katrina's Collection (S268; renamed S274) — operator-tunable filters that generate per-user
-- lists of POIs to "stamp" as the user walks past or taps each one.
--
-- Two tables (S274 rename — "Katrina's Collection" rebrand of S268 Passport):
--   salem_collections          — one row per operator-defined collection
--                                 (Default Salem Walking, Heritage Trail, …).
--                                 tour_id NULL = global collection; non-NULL =
--                                 per-tour collection bound to that tour.
--   salem_collection_entries   — derived per-collection POI list, populated by
--                                 cache-proxy/scripts/publish-poi-collection.js
--                                 from each collection's parameters against
--                                 salem_pois. Baked into the Room
--                                 collection_entry table at v22.
--
-- The user's actual visit log lives in user_data.db (poi_visit table,
-- Room v4) and is keyed by poi_id only — never by collection_id. Per-tour
-- collections just render the global visit log against their filtered list.
-- HIST_BLDG entries are rendered as "ghosts" in the Android UI; other
-- entries render as plain visited POIs.
--
-- Pre-S274 names: salem_passport_filters → salem_collections;
-- salem_passport_pois → salem_collection_entries (filter_id → collection_id).
-- Live PG was renamed via ALTER TABLE in S274; fresh installs use the
-- CREATE TABLE blocks below. Idempotent post-S274 forward-rename block
-- at the bottom handles any stragglers that still have old names.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_collections (
  id                            TEXT PRIMARY KEY,                          -- slug, e.g. 'default_salem_walking', 'heritage_trail'
  name                          TEXT NOT NULL,                              -- 'Default Salem Walking', 'Heritage Trail'
  tour_id                       TEXT REFERENCES salem_tours(id) ON DELETE CASCADE,  -- NULL = global collection
  categories                    TEXT[] NOT NULL DEFAULT '{}',               -- e.g. {HISTORICAL_BUILDINGS,HISTORICAL_LANDMARKS,WORSHIP,CIVIC}
  require_historical_narration  BOOLEAN NOT NULL DEFAULT TRUE,
  min_geofence_radius_m         INTEGER NOT NULL DEFAULT 0,
  min_year_established          INTEGER,                                    -- NULL = no floor
  max_year_established          INTEGER,                                    -- NULL = no ceiling
  sort_order                    INTEGER NOT NULL DEFAULT 0,
  -- S269 — when FALSE, publish-poi-collection.js preserves the existing
  -- salem_collection_entries rows for this collection instead of regenerating
  -- from the category/year/etc parameters. Drives the "Build Collection from
  -- Walk" flow in the Tour editor: candidates are derived from the tour's
  -- polyline + each POI's geofence radius, edited by the operator, and
  -- written directly to salem_collection_entries. The publish bake still copies
  -- those rows into the SQLite collection_entry table.
  auto_bake                     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- S269 — idempotent forward-migration so existing dev installs pick up
-- auto_bake without needing a fresh psql run of this file. Cache-proxy
-- module init (admin-collection.js) ALSO runs this ALTER on startup so
-- developers who never re-source schema files still get the column.
ALTER TABLE salem_collections
  ADD COLUMN IF NOT EXISTS auto_bake BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_salem_collections_tour
  ON salem_collections (tour_id) WHERE tour_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS salem_collection_entries (
  collection_id  TEXT NOT NULL REFERENCES salem_collections(id) ON DELETE CASCADE,
  poi_id         TEXT NOT NULL REFERENCES salem_pois(id) ON DELETE CASCADE,
  display_order  INTEGER NOT NULL,
  PRIMARY KEY (collection_id, poi_id)
);

CREATE INDEX IF NOT EXISTS idx_salem_collection_entries_collection
  ON salem_collection_entries (collection_id, display_order);
CREATE INDEX IF NOT EXISTS idx_salem_collection_entries_poi
  ON salem_collection_entries (poi_id);

-- S274 — idempotent forward-rename block. If an older install still has
-- the pre-S274 salem_passport_filters / salem_passport_pois tables,
-- rename them in place to preserve data. Safe to re-run; no-ops once the
-- rename has happened. The admin-collection.js module init runs the
-- equivalent block on startup so devs who never re-source this file still
-- get migrated.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='salem_passport_filters') THEN
    EXECUTE 'ALTER TABLE salem_passport_filters RENAME TO salem_collections';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_tables WHERE schemaname='public' AND tablename='salem_passport_pois') THEN
    EXECUTE 'ALTER TABLE salem_passport_pois RENAME TO salem_collection_entries';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns
             WHERE table_schema='public' AND table_name='salem_collection_entries' AND column_name='filter_id') THEN
    EXECUTE 'ALTER TABLE salem_collection_entries RENAME COLUMN filter_id TO collection_id';
  END IF;
END $$;
