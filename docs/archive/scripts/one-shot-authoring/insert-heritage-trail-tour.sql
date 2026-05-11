-- insert-heritage-trail-tour.sql — Session 124 Phase 9R.0
-- Creates the Salem Heritage Trail tour (~3.6 mi walking loop, 10 unique anchor POIs)
-- from operator-supplied YellowLine.txt intersections + salem_pois anchor data.
--
-- This is idempotent: re-running replaces the tour and its stops.
-- Safe to re-run after narration content updates.

BEGIN;

-- Clean any prior attempt so we can re-run freely
DELETE FROM salem_tour_stops WHERE tour_id = 'tour_salem_heritage_trail';
DELETE FROM salem_tours WHERE id = 'tour_salem_heritage_trail';

-- Insert the tour
INSERT INTO salem_tours (
  id, name, theme, description, estimated_minutes, distance_km,
  stop_count, difficulty, seasonal, sort_order,
  data_source, confidence, verified_date
) VALUES (
  'tour_salem_heritage_trail',
  'Salem Heritage Trail',
  'HERITAGE_TRAIL',
  E'The official ~2.7-mile Salem Heritage Trail (Yellow Line) — a historical walking loop starting and ending at the Regional Visitor Center and passing the Old Jail, Salem Common, Maritime National Historic Site, Custom House, Old Town Hall, Witch Trials Memorial, Bewitched statue, the Witch House, and the McIntire Historic District. In Historical Mode the app hides modern POIs and narrates only the historical sites along the route.',
  78,    -- estimated minutes (OSRM foot profile walking time)
  5.8,   -- 3.6 miles = 5.8 km
  10,    -- 10 unique anchor POIs (stop 11 is a return-to-start in the route JSON)
  'easy',
  false,
  1,     -- sort to the top of the tour list
  'operator_heritage_trail_2026_04_13',
  1.0,
  NOW()
);

-- Insert the 10 ordered tour stops (anchor POIs)
INSERT INTO salem_tour_stops (
  tour_id, poi_id, stop_order, transition_narration,
  walking_minutes_from_prev, distance_m_from_prev,
  data_source, confidence
) VALUES
  ('tour_salem_heritage_trail', 'national_park_service_visitor_center', 1,
   E'Welcome to the Salem Heritage Trail. You''re standing at the National Park Service Regional Visitor Center, our starting point. Grab a map inside if you haven''t already, then head north on Liberty Street toward Bridge Street to begin the loop.',
   0, 0, 'operator_heritage_trail_2026_04_13', 1.0),

  ('tour_salem_heritage_trail', 'salem_jail_site', 2,
   E'We''re now approaching the Old Salem Jail Site near Bridge Street and St. Peter Street — the outer edge of the historic downtown, and a place where the 1692 trials reached their darkest hour.',
   5, 380, 'operator_heritage_trail_2026_04_13', 1.0),

  ('tour_salem_heritage_trail', 'salem_common', 3,
   E'Heading south, we''re entering Salem Common at Brown Street and Washington Square. This open green has been the town''s gathering place since the 1630s.',
   5, 366, 'operator_heritage_trail_2026_04_13', 1.0),

  ('tour_salem_heritage_trail', 'salem_maritime_national_historical_park', 4,
   E'South on Hawthorne Boulevard toward Derby Street — we''re crossing into Salem Maritime National Historic Site, the waterfront that once made Salem the wealthiest port in America.',
   7, 540, 'operator_heritage_trail_2026_04_13', 1.0),

  ('tour_salem_heritage_trail', 'custom_house', 5,
   E'The 1819 Custom House sits ahead at Derby Street and Orange Street, where Nathaniel Hawthorne worked as a surveyor and where ship captains paid their duties.',
   4, 312, 'operator_heritage_trail_2026_04_13', 1.0),

  ('tour_salem_heritage_trail', 'old_town_hall', 6,
   E'Turning west on Derby Street, we arrive at Old Town Hall in Derby Square — Salem''s early-19th-century civic heart and market.',
   5, 329, 'operator_heritage_trail_2026_04_13', 1.0),

  ('tour_salem_heritage_trail', 'salem_witch_trials_memorial', 7,
   E'Up Liberty Street to Charter Street — the site of the Salem Witch Trials Memorial and the adjoining Old Burying Point, Salem''s oldest cemetery.',
   3, 201, 'operator_heritage_trail_2026_04_13', 1.0),

  ('tour_salem_heritage_trail', 'bewitched_sculpture_samantha_statue', 8,
   E'West on Essex Street to Washington Street — the Bewitched statue marks the heart of Salem''s pedestrian mall, where the 1960s TV show once filmed on location.',
   6, 411, 'operator_heritage_trail_2026_04_13', 1.0),

  ('tour_salem_heritage_trail', 'the_witch_house', 9,
   E'Continue west along Essex to North Street — ahead is The Witch House, home of Judge Jonathan Corwin and the only structure still standing with direct ties to the 1692 trials.',
   6, 451, 'operator_heritage_trail_2026_04_13', 1.0),

  ('tour_salem_heritage_trail', 'chestnut_street', 10,
   E'Heading south on Summer Street to Chestnut Street — we''re entering the McIntire Historic District, a federal-style neighborhood of mansions built by Salem''s merchant princes and designed largely by Samuel McIntire.',
   4, 310, 'operator_heritage_trail_2026_04_13', 1.0);

-- Sanity check
SELECT COUNT(*) AS tour_stops_inserted FROM salem_tour_stops WHERE tour_id = 'tour_salem_heritage_trail';
SELECT id, name, theme, stop_count FROM salem_tours WHERE id = 'tour_salem_heritage_trail';

COMMIT;
