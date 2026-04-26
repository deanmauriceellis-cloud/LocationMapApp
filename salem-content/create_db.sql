-- Room-compatible SQLite schema for salem_content.db
-- Must match the Room entity definitions exactly (version 2 with provenance)

CREATE TABLE IF NOT EXISTS tour_pois (
  id TEXT NOT NULL PRIMARY KEY,
  name TEXT NOT NULL,
  lat REAL NOT NULL,
  lng REAL NOT NULL,
  address TEXT NOT NULL,
  category TEXT NOT NULL,
  subcategories TEXT,
  short_narration TEXT,
  long_narration TEXT,
  description TEXT,
  historical_period TEXT,
  admission_info TEXT,
  hours TEXT,
  phone TEXT,
  website TEXT,
  image_asset TEXT,
  geofence_radius_m INTEGER NOT NULL DEFAULT 50,
  requires_transportation INTEGER NOT NULL DEFAULT 0,
  wheelchair_accessible INTEGER NOT NULL DEFAULT 1,
  seasonal INTEGER NOT NULL DEFAULT 0,
  priority INTEGER NOT NULL DEFAULT 3,
  data_source TEXT NOT NULL DEFAULT 'manual_curated',
  confidence REAL NOT NULL DEFAULT 1.0,
  verified_date TEXT,
  created_at INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL DEFAULT 0,
  stale_after INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS salem_businesses (
  id TEXT NOT NULL PRIMARY KEY,
  name TEXT NOT NULL,
  lat REAL NOT NULL,
  lng REAL NOT NULL,
  address TEXT NOT NULL,
  business_type TEXT NOT NULL,
  cuisine_type TEXT,
  price_range TEXT,
  hours TEXT,
  phone TEXT,
  website TEXT,
  description TEXT,
  historical_note TEXT,
  tags TEXT,
  rating REAL,
  image_asset TEXT,
  data_source TEXT NOT NULL DEFAULT 'manual_curated',
  confidence REAL NOT NULL DEFAULT 1.0,
  verified_date TEXT,
  created_at INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL DEFAULT 0,
  stale_after INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS historical_figures (
  id TEXT NOT NULL PRIMARY KEY,
  name TEXT NOT NULL,
  first_name TEXT NOT NULL,
  surname TEXT NOT NULL,
  born TEXT,
  died TEXT,
  age_in_1692 INTEGER,
  role TEXT NOT NULL,
  faction TEXT,
  short_bio TEXT NOT NULL,
  full_bio TEXT,
  narration_script TEXT,
  appearance_description TEXT,
  role_in_crisis TEXT,
  historical_outcome TEXT,
  key_quotes TEXT,
  family_connections TEXT,
  primary_poi_id TEXT,
  data_source TEXT NOT NULL DEFAULT 'salem_project',
  confidence REAL NOT NULL DEFAULT 1.0,
  verified_date TEXT,
  created_at INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL DEFAULT 0,
  stale_after INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS historical_facts (
  id TEXT NOT NULL PRIMARY KEY,
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  date TEXT,
  date_precision TEXT,
  category TEXT,
  subcategory TEXT,
  poi_id TEXT,
  figure_id TEXT,
  source_citation TEXT,
  narration_script TEXT,
  confidentiality TEXT NOT NULL DEFAULT 'public',
  tags TEXT,
  data_source TEXT NOT NULL DEFAULT 'salem_project',
  confidence REAL NOT NULL DEFAULT 1.0,
  verified_date TEXT,
  created_at INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL DEFAULT 0,
  stale_after INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS timeline_events (
  id TEXT NOT NULL PRIMARY KEY,
  name TEXT NOT NULL,
  date TEXT NOT NULL,
  crisis_phase TEXT,
  description TEXT NOT NULL,
  poi_id TEXT,
  figures_involved TEXT,
  narration_script TEXT,
  is_anchor INTEGER NOT NULL DEFAULT 0,
  data_source TEXT NOT NULL DEFAULT 'salem_project',
  confidence REAL NOT NULL DEFAULT 1.0,
  verified_date TEXT,
  created_at INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL DEFAULT 0,
  stale_after INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS primary_sources (
  id TEXT NOT NULL PRIMARY KEY,
  title TEXT NOT NULL,
  source_type TEXT NOT NULL,
  author TEXT,
  date TEXT,
  full_text TEXT,
  excerpt TEXT,
  figure_id TEXT,
  poi_id TEXT,
  narration_script TEXT,
  citation TEXT,
  data_source TEXT NOT NULL DEFAULT 'salem_project',
  confidence REAL NOT NULL DEFAULT 1.0,
  verified_date TEXT,
  created_at INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL DEFAULT 0,
  stale_after INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tours (
  id TEXT NOT NULL PRIMARY KEY,
  name TEXT NOT NULL,
  theme TEXT NOT NULL,
  description TEXT NOT NULL,
  estimated_minutes INTEGER NOT NULL,
  distance_km REAL NOT NULL,
  stop_count INTEGER NOT NULL,
  difficulty TEXT NOT NULL DEFAULT 'moderate',
  seasonal INTEGER NOT NULL DEFAULT 0,
  icon_asset TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  data_source TEXT NOT NULL DEFAULT 'manual_curated',
  confidence REAL NOT NULL DEFAULT 1.0,
  verified_date TEXT,
  created_at INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL DEFAULT 0,
  stale_after INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS tour_stops (
  tour_id TEXT NOT NULL,
  poi_id TEXT NOT NULL,
  stop_order INTEGER NOT NULL,
  transition_narration TEXT,
  walking_minutes_from_prev INTEGER,
  distance_m_from_prev INTEGER,
  data_source TEXT NOT NULL DEFAULT 'manual_curated',
  confidence REAL NOT NULL DEFAULT 1.0,
  created_at INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL DEFAULT 0,
  stale_after INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (tour_id, poi_id)
);

CREATE TABLE IF NOT EXISTS tour_legs (
  tour_id TEXT NOT NULL,
  from_stop_order INTEGER NOT NULL,
  to_stop_order INTEGER NOT NULL,
  from_poi_id TEXT NOT NULL,
  to_poi_id TEXT NOT NULL,
  distance_m REAL NOT NULL,
  duration_s REAL NOT NULL,
  edge_count INTEGER NOT NULL,
  geometry TEXT NOT NULL,
  data_source TEXT NOT NULL DEFAULT 'router_bake_v1',
  confidence REAL NOT NULL DEFAULT 1.0,
  created_at INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL DEFAULT 0,
  stale_after INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (tour_id, from_stop_order)
);
CREATE INDEX IF NOT EXISTS idx_tour_legs_tour ON tour_legs(tour_id, from_stop_order);

CREATE TABLE IF NOT EXISTS events_calendar (
  id TEXT NOT NULL PRIMARY KEY,
  name TEXT NOT NULL,
  venue_poi_id TEXT,
  event_type TEXT NOT NULL,
  description TEXT,
  start_date TEXT,
  end_date TEXT,
  hours TEXT,
  admission TEXT,
  website TEXT,
  recurring INTEGER NOT NULL DEFAULT 0,
  recurrence_pattern TEXT,
  seasonal_month INTEGER,
  data_source TEXT NOT NULL DEFAULT 'manual_curated',
  confidence REAL NOT NULL DEFAULT 1.0,
  verified_date TEXT,
  created_at INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL DEFAULT 0,
  stale_after INTEGER NOT NULL DEFAULT 0
);

-- Room metadata table (required for createFromAsset)
CREATE TABLE IF NOT EXISTS room_master_table (
  id INTEGER PRIMARY KEY,
  identity_hash TEXT
);
