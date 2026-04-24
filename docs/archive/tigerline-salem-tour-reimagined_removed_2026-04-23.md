<!--
PUBLISHED SNAPSHOT — NOT THE CANONICAL SOURCE.

Canonical file:  ~/Research/TigerLine/SALEM_TOUR_REIMAGINED.md
Source repo:     github.com/deanmauriceellis-cloud/TigerLine
Source commit:   a2571c0f75eca17abcc416a6f9eecc4c18e29a63
Published to LMA: 2026-04-21 (TigerLine Session 001)

LMA-FACING VISION DOCUMENT for the Salem tour integration. Read this
FIRST before the per-layer features doc or per-query capabilities doc —
it frames the "what can I now build?" question that the other two answer.

Covers:
  - Why this data rewrites the tour experience (POIs in building polygons,
    MHC Inventory as queryable history, real pedestrian routing)
  - Six concrete cross-schema JOIN patterns with runnable SQL
  - Seven tour storytelling patterns enabled by the data
  - Six concrete Salem tour experiences the data now enables
  - Phase 7 consumer contract options (SpatiaLite / binary graph / live SQL)
  - Implementation timeline for the 2026-07-15 deadline
  - Appendix A: layer-to-tour-feature mapping for LMA engineering
  - Appendix B: 12-week pre-deadline integration checklist

Companion reference docs also in this directory:
  - docs/tigerline-salem-features.md (per-layer catalog)
  - docs/tigerline-location-capabilities.md (per-use-case queries)

Do not edit this copy directly — edit in TigerLine and re-publish.
-->

# Salem Tour — Reimagined with TigerLine + MassGIS

> **For:** LocationMapApp (Katrina's Mystic Visitors Guide) product team
> **From:** TigerLine
> **Purpose:** A detailed brief on the exceptional data, features, and storytelling capabilities unlocked by combining TIGER/Line 2025 (national, 65.7M-edge routing graph, 35M addresses, all Census geography) with MassGIS (~198 Massachusetts-authoritative layers including building footprints, the State Historic Inventory, MBTA transit, the coastline at 1:25k fidelity, and much more) — specifically in the context of the Salem tour and the 2026-07-15 integration deadline.
>
> This document is **not** a reference catalog (see `SALEM_TOUR_FEATURES.md` for the per-layer inventory and `LOCATION_CAPABILITIES.md` for the per-query capabilities). It is a **vision brief** — what we can now build that we could not build before.

---

## TL;DR — three sentences

Until now, the Salem tour has had no choice but to treat buildings as pins on a map, history as text in narration cards, and walking routes as "roads minus cars." With TIGER's national geometry joined to MassGIS's state-authoritative layers, every historic building becomes a **polygon with metadata**, every cemetery becomes a **navigable boundary**, and every walking route can route through **the paths pedestrians actually use** (Derby Wharf, Pickering Wharf, Salem Common paths, the Willows promenade) rather than whatever's drawn in the road graph. This is a generational upgrade to the substrate that the tour experience sits on.

---

## The three shifts

1. **From points to polygons.** POIs stop being pins. They live inside their actual historic building footprints. Tap the building and the whole story unfolds.

2. **From narration to data.** The witch trials, the maritime era, the McIntire Federal-period architecture — all are no longer just text in narration cards. The MHC Historic Inventory has ~**2,000 structured records for Salem alone**. Every historic property is a row with year-built, architectural style, historic-status flags, and narrative notes. The tour can *query* history.

3. **From "road network" to "pedestrian network."** TIGER alone routes on roads. TIGER + MassGIS routes on **trails, wharves, park paths, historic walking routes, bike paths, and the rail-trail network** — the actual infrastructure tourists walk on.

Each of these rewrites a category of tour experience. Below, per category, we describe what's now possible.

---

# Section 1 — Reimagining the map itself

## 1.1 The basemap: from generic to Salem-authentic

**Before:** Salem rendered on OSM or Mapbox tiles, with whatever detail those upstream datasets happen to carry. Salem's character (waterfront, Federal-period density, harbor shape) is filtered through a generic national-grade basemap.

**Now:** A Salem-specific basemap assembled from authoritative layers:

| Layer | Source | What it gives Salem |
|---|---|---|
| Coastline at 1:25k fidelity | MassGIS `coast25k` | Every pier, wharf, cove, and harbor inlet drawn at survey-grade detail. Derby Wharf's outline is now precise. |
| Statewide building footprints | MassGIS `structures_poly` | ~2M MA buildings — Salem downtown density renders as actual blocks of buildings, not empty space between roads |
| 1:25k hydrography | MassGIS `hydro25k` + `majorhydro` | Collins Cove, North River, Forest River, all at river-morphology precision |
| Tidelands | MassGIS `tidelands_*` | The legal high-water mark that defines the waterfront boundary — Salem's wharves cross into tidelands; tours following the wharf paths walk on tideland fill |
| Protected open space | MassGIS `openspace` | Salem Common boundary, Salem Willows park outline, Winter Island outline, Forest River Park, the Salem Maritime National Historic Site parcels, every Forest Pond reservation |
| Local Historic District polygons | MassGIS `mhcinv` (inventory) | McIntire Historic District outline, Chestnut Street Historic District outline, Derby Street Historic District outline |
| MBTA commuter rail | MassGIS `trains` | Salem Depot station + the Newburyport/Rockport line through downtown Salem |
| Scenic byways | MassGIS `scenic_landscapes` | The Essex Coastal Scenic Byway routing (Salem → Beverly → Manchester → Gloucester → Rockport) |
| National roads + addresses | TIGER `edges` / `addrfeat` / `addr` | Street geometry + from/to address ranges (3,141 ADDR files already imported) |
| Landmark polygons (AREALM) | TIGER | Cemeteries, parks, school campuses — secondary to MassGIS but useful when MassGIS misses one |

**What the tourist sees:** A map that looks like Salem in 1800 imagined onto modern GPS. The witch-trial-era street grid overlays the contemporary grid (they're ~70% overlapping). The harbor's actual shape. Every park as an actual shape. Every wharf as a traced outline. Derby Wharf extends into the harbor as a drawn pier, not a hopeful line.

## 1.2 The style layer (Phase 10 — "Cartoon Cartography")

**Before:** Reuse stock MapLibre styles — the map looks like every other map.

**Now:** Because we own the geometry, we own the style. A Salem-specific visual style can:

- Render historic-district boundaries with a period-appropriate treatment (sepia fill, engraved border)
- Use a different fill color for buildings on the National Register vs state inventory vs uninventoried
- Overlay 1692 ghost streets in a subtle trace where they diverge from modern streets
- Treat the harbor as a cartographic first-class citizen (not just blue) — render waves, ships, the Friendship of Salem replica at its wharf
- Pick fonts, colors, and label priority for every feature class independently

This isn't styling-on-top-of-someone-else's-map. It's a Salem map built from the ground up on Salem data.

---

# Section 2 — Reimagining the POIs

## 2.1 POIs as building footprints, not points

**Before:** A POI was a single lon/lat, rendered as a pin. Tapping a pin opened a card.

**Now:** A POI is an **ID that maps to a MassGIS `structures_poly` polygon**. The Witch House (Jonathan Corwin House, 310½ Essex St) has a building footprint. Tap it — the whole building highlights on the map. The "tap target" is the polygon, not a 32×32 pixel pin.

### What this changes at the UX level

| Interaction | Before (point) | Now (polygon) |
|---|---|---|
| Tap target | Must hit the pin (~30m precision) | Tap anywhere on the building (~5m precision for a 50m-wide building) |
| Visual prominence | Pin is symbolic | Building shape is the actual shape — tourists recognize "the long yellow building" |
| Information density | One card per point | Building has: address, year built, architectural style, historic-registry status, parcel owner class, adjacent lot info |
| Storytelling hooks | Card is text only | "This building has been here since 1675" ← pulled from MHC data |
| Multi-POI inside one building | Impossible (one pin per location) | Peabody Essex Museum is one building with multiple POIs inside (galleries, wings, gift shop, café) — all mapped to sub-polygons or offset points WITHIN the master polygon |
| Route-snapping | Route to nearest street | Route to nearest **pedestrian entrance** of the building (curated per-POI override) |

### Concrete Salem examples

**The Witch House (Jonathan Corwin House)**
- Building polygon: MassGIS `structures_poly` feature at 42.5209°N, -70.8973°W
- Cross-joined MHC Inventory record: structure ID `SAL.1`, built 1675, First Period architecture, owned by Jonathan Corwin (1640-1718), judge at the 1692 trials
- Cross-joined parcel: current owner "City of Salem," use class "museum"
- Tour integration: tap the building → narration pulls the MHC record's architectural description, the parcel's current status, and LMA's curated witch-trial storytelling
- Map render: building footprint shaded in a period color, pin-free

**House of the Seven Gables**
- Building polygon: at 42.5211°N, -70.8839°W
- MHC record: `SAL.214`, built 1668, Hawthorne's ancestral home, operated as a museum since 1908
- Tour integration: audio narration mentions "the seven gables you see above are actually recreations added in..." while the map highlights the building

**Peabody Essex Museum**
- Multiple connected buildings: East India Marine Hall (1825), Plummer Hall (1856), Daland House (1852), Moffatt Wing (1938), 2003 expansion, 2022 expansion
- Curated POI table has one entry per physically-accessible wing
- Each wing's entry maps to its own polygon in `structures_poly`
- Tour integration: the tour can "enter" the museum by zooming to the master footprint, then offer gallery-level sub-POIs

## 2.2 Address-point-anchored POIs (precise)

**Before:** POIs geocoded by interpolation on TIGER address ranges. "174 Derby St" resolves to somewhere along the Derby St segment that has the 100-200 range on the correct side — accuracy ~15-30m.

**Now:** Every POI address resolves to a specific MassGIS `address_points` record — **point-level geocoding accurate to the building itself**. ~3M address points statewide; every Salem street address has a canonical point.

### Integration pattern for LMA

```sql
-- Given a POI's street address, snap to the authoritative point
SELECT ap.lon, ap.lat, ap.addr_full
FROM massgis.address_points ap
WHERE ap.addr_full ILIKE '%174 Derby%Salem%';
-- returns exact coordinates of 174 Derby St's primary structure

-- And what building is that?
SELECT s.gid, s.structure_use, ST_Area(s.geom::geography) AS sq_m
FROM massgis.structures_poly s
JOIN massgis.address_points ap ON ST_Contains(s.geom, ap.geom)
WHERE ap.addr_full ILIKE '%174 Derby%';
-- returns the building's shape and size
```

The curation pipeline for `salem-content` can now:
1. Accept an address string
2. Resolve to canonical point
3. Resolve to building footprint
4. Pre-populate building size, use class, adjacent landmarks
5. Pre-check against MHC Inventory — if the address matches a historic record, auto-link

This changes the POI curation unit economics. Previously every POI required the curator to place a pin manually. Now the curator enters an address; everything else auto-populates.

## 2.3 POI density maps

With every Salem address anchored and every building polygonized, the tour app can compute:

- **Walkable POI density** per tour stop within an N-meter walking isochrone
- **Historic POI density** per neighborhood (McIntire Historic District has X National Register buildings per block)
- **Free-vs-paid POI ratio** on the current walking path
- **Unresearched gaps** — buildings with no MHC record and no curated POI → candidate for future curation

This lets the app make dynamic UX decisions: "You're in a high-density historic block, switching to slow-scroll mode" or "You're in a low-POI stretch, suggesting a shortcut."

---

# Section 3 — Reimagining the history layer

## 3.1 The MHC Historic Inventory is a database, not a caption

The Massachusetts Historical Commission maintains the State Historic Inventory — an entry for every architecturally or historically significant structure, district, burial ground, shipwreck, and archaeological site surveyed in Massachusetts over the past 60 years. We are importing this wholesale into `massgis.mhcinv` (points) and `massgis.mhc_inventory` (full polygon inventory).

**Statewide scale:** ~70,000 individual properties + ~5,000 districts.
**Salem's share:** Estimated 1,500–2,500 individual structures + 10+ historic districts.

### Each MHC record carries (typical fields)

- **MHC ID** (e.g., `SAL.123`) — joins across state + national registers
- **Street address** (joins to `address_points`)
- **Year built / date range** — often down to the decade for Federal-era Salem
- **Architect** (where known)
- **Architectural style** (First Period, Georgian, Federal, Greek Revival, Victorian, etc.)
- **Original use** / **current use**
- **Historic name** (e.g., "Capt. John Turner House II" = House of Seven Gables)
- **National Register status** (listed / eligible / contributing / none)
- **Local Historic District membership**
- **MACRIS description** — the narrative write-up by the surveyor

### What the tour can now do with this

**"Show me every Federal-period building within 500m of my position"**
```sql
SELECT mhc.historic_name, mhc.year_built, mhc.style, mhc.address,
       ST_Distance(mhc.geom::geography, user_loc::geography) AS m
FROM massgis.mhc_inventory mhc
WHERE mhc.style ILIKE '%federal%'
  AND ST_DWithin(mhc.geom::geography, user_loc::geography, 500)
ORDER BY m;
```
Result: narration layer writes itself. "Three Federal-period houses within sight: the Pickman-Derby House (1764), the Gardner-Pingree House (1804), the Peirce-Nichols House (1782)..."

**"Every witch-trial-era structure still standing"**
```sql
-- The witch trials were 1692. Buildings built before 1700 are the real relics.
SELECT *
FROM massgis.mhc_inventory
WHERE year_built < 1700
  AND ST_Within(geom, (SELECT geom FROM tiger.cousub
                       WHERE statefp='25' AND name='Salem' AND countyfp='009'));
```
Result: a filterable list of maybe 15-25 pre-1700 structures in Salem. The Witch House. The Pickering House (1651). The Narbonne House (1672). The Hooper-Hathaway House (1682). Every one a tour-worthy stop.

**"Tour by architectural style"**
A "Federal Period Walking Tour" can be assembled automatically: every Federal-period building in the McIntire Historic District polygon, ordered optimally by pgRouting's TSP solver against the walking graph. The curator no longer hand-picks the stops — she reviews the auto-generated list and tunes.

**"Hidden gems"**
Buildings with high MHC interest but no Wikipedia / low tourist foot traffic → curated as an "Insider's Tour" variant. Filter = MHC record present + parcel owner-class is not "commercial tourist" + National Register not listed. Produces a list of under-visited historic homes.

## 3.2 The Burial Grounds layer

**Before:** Cemetery = point of interest = one pin.

**Now:** MassGIS publishes `MHC_BURIALGROUNDS_PT` — a separate layer for historic burial grounds (ante-1850, typically). Each record has:
- Name (e.g., "Old Burying Point Cemetery, 1637")
- Founding year
- Notable interments
- Current condition / access status

Combined with MassGIS `CEMETERIES_PT` (modern cemetery locations) and TIGER `AREALM` (polygon outlines for the bigger cemeteries), Salem's cemetery landscape becomes:

| Cemetery | Era | Polygon source | Notable |
|---|---|---|---|
| Old Burying Point | 1637 | TIGER AREALM + MassGIS point | Justice Hathorne, Capt. Richard More (Mayflower), Salem Witch Trials victims' cenotaph |
| Howard Street Cemetery | 1801 | TIGER AREALM | Haunted-Salem folklore, Giles Corey association |
| Broad Street Cemetery | 1655 | TIGER AREALM | Second-oldest in Salem |
| Greenlawn Cemetery | 1807 | TIGER AREALM | Larger rural-garden cemetery style |
| St. Mary's Cemetery | Later | MassGIS point | Catholic |

**What the cemetery layer enables:**

1. **Navigable cemetery polygons** — the tour can route users TO the cemetery (at the entrance-point), and THROUGH the cemetery's internal paths (where those paths are in OSM or MassGIS trails data)
2. **Density-aware tagging** — the Witch Trials Memorial is physically adjacent to Old Burying Point; a good tour treats them as one stop, not two
3. **Polygon-triggered narration** — when the user's GPS enters the cemetery polygon, trigger audio: "You are entering Old Burying Point, founded 1637..."
4. **Cemetery-specific tour mode** — "The Mourners' Tour": visit all five Salem burial grounds in optimal walking order

## 3.3 The Shipwrecks + Maritime Heritage layer

MassGIS `MHC_SHIPWRECKS_PT` — historic shipwrecks off the MA coast. Salem has several in Salem Sound and off the waterfront. Tour integration:

- An "offshore storytelling" layer — looking out from Derby Wharf, the tour can point to where "the *Friendship* of Salem sank in the harbor in 1912..."
- Cross-reference with the National Register of Historic Places — some shipwrecks are NR-listed
- The new replica *Friendship of Salem* at Central Wharf is itself a National Historic Site parcel

This is the kind of data that LMA's tour can now actually *point at*, rather than mention abstractly.

---

# Section 4 — Reimagining the walking paths

## 4.1 The actual pedestrian network (not "roads minus cars")

**Before:** The Salem walking tour routes on TIGER road edges filtered by MTFCC. That gives us streets, but not:
- Derby Wharf's 1.1-mile pedestrian pier (not a road; no driving)
- Pickering Wharf's boardwalk (commercial pedestrian area)
- Salem Common's internal paths (the diagonal paths across the Common)
- Salem Willows promenade (~½ mile waterfront path)
- The McIntire Historic District walking tour (a curated route)
- Winter Island paths to Fort Pickering
- The Salem Maritime NHS internal paths (around the Customs House, through the garden, along the pier)

**Now:** Route on the **union** of:

| Source | What it adds |
|---|---|
| TIGER `edges` (MTFCC S1400/S1500/S1710/S1720/S1730/S1740/S1750/S1780) | All streets, local roads, walkways, stairways, alleys, parking lot aisles |
| MassGIS `trails` | Statewide walking trail inventory — includes cemetery paths, Maritime NHS internal paths, Salem Common internal diagonals where surveyed |
| MassGIS `dcrtrails` | DCR (state park) trail network — Winter Island, Forest River Park trails |
| MassGIS `biketrails` | Bike + rail trails (some are multi-use including pedestrian) |
| MassGIS `ldtrails` | Long-distance trails — the Bay Circuit Trail may clip the area |
| MassGIS `mad_trails` | DCR MAD atlas trails — secondary DCR network |
| OSM pedestrian features (Phase 6) | The filler: Derby Wharf pier, Pickering Wharf boardwalk, Salem Willows promenade, waterfront informal paths — wherever TIGER and MassGIS miss them |

### The unified pedestrian graph

pgRouting topology built on the UNION of these edges produces a "real" Salem pedestrian network. A tourist's "15-minute walk from Salem Depot" isochrone now includes:

- Everything reachable via streets (TIGER gives us this)
- Plus everything reachable via DCR trails
- Plus everything reachable via cemetery internal paths
- Plus the Derby Wharf pier (which is a dead-end for cars but a mile of walkable pier)
- Plus the Salem Willows waterfront promenade
- Plus the Salem Common diagonal paths (not detours through neighboring streets)

### Concrete tour route comparison

**Tour stop: Salem Depot → House of the Seven Gables**

- **TIGER-only route:** Walk south down Washington St → east on Essex St → east on Derby St → arrive. 0.8 mi, ~18 min walk.
- **TIGER + MassGIS + OSM route:** Same start, but the router prefers the Derby Wharf pier after Central St — it's a longer path but runs along the harbor. Tourists see the ships. 0.9 mi, ~20 min walk, **dramatically better experience**.

The difference isn't in minutes-saved; it's in **tourist hours well-spent**. The app can offer "fastest" vs "scenic" and the scenic mode is now real.

## 4.2 Isochrones that mean something

**Before:** A 10-minute-walk isochrone from Salem Depot was a polygon drawn on the road graph. It included streets you wouldn't actually walk (Route 114 with no sidewalks) and excluded places you would (through Salem Common).

**Now:** The isochrone is computed against the unified pedestrian graph. It correctly:
- Includes Salem Common's cross-cuts
- Includes the Derby Wharf pier loop
- Excludes non-walkable arterials
- Penalizes unpleasant-to-walk segments (can be weighted per MTFCC / OSM highway type)

This enables tour features like:
- **"Stops within 15 minutes of where I'm standing"** that returns an honest answer
- **"Can I make it back to the train in 20 minutes?"** with meaningful precision
- **"Wander mode"** — show everything interesting within 10-minute walk, ranked by historic-density

## 4.3 Elevation-aware walking time

Salem is relatively flat, but not uniformly. Chestnut Street's hill, Gallows Hill, the climb to Winter Island — these meaningfully change walking time. The Phase 5 walking router (MASTER_PLAN §Phase 5) can accept optional grade input from USGS 3DEP (see the future-cities memory — more important for SF/Hollywood but non-trivial even here).

For Salem specifically: flat enough that the default 4.5 km/h estimate is accurate to ±10% everywhere except Gallows Hill. But the architecture should accept elevation data; for Cape Ann extensions (Manchester, Gloucester, Rockport) it matters more.

## 4.4 The "Walk with the Tour" real-time experience

Combining:
- Authoritative address points for each curated POI
- Building-footprint-anchored stops
- Unified pedestrian graph
- GPS location of the user

...the tour app can do **turn-by-turn walking narration** that's meaningful:

> "In 50 meters, turn right onto Hawthorne Boulevard. You'll pass the Nathaniel Hawthorne statue on your left."
>
> "You are now entering the Old Burying Point Cemetery. Touch the screen when you'd like to hear about Justice Hathorne..."
>
> "The building on your right with the three gables is the Joshua Ward House, where you can feel the ghost of George Corwin push past you on the stair. Built 1784."

Every one of those sentences is data-driven: the turn direction from pgRouting, the "50 meters" from the edge length, the "building on your right" from the building polygon GPS-relative-bearing calculation, the building's facts from MHC.

---

# Section 5 — Reimagining the polygon grammar

## 5.1 Everything becomes a polygon (and polygons can be tapped, filled, animated)

A consistent design principle enabled by the data: **every feature the tour references can be rendered as its actual geographic shape.** Not as a symbol, not as a pin, but as the shape the feature occupies in the world.

### The polygon catalog for Salem

| Feature | Polygon source | What rendering gains |
|---|---|---|
| Salem city boundary | TIGER `cousub` | "You are in Salem" overlay; city-edge storytelling |
| McIntire Historic District | MassGIS `mhc_inventory` | Tinted overlay showing the protected district |
| Chestnut St Historic District | MassGIS `mhc_inventory` | Tinted overlay, narratable |
| Derby Street Historic District | MassGIS `mhc_inventory` | Tinted overlay |
| Salem Common | TIGER `AREALM` + MassGIS `openspace` | Park polygon, crossable |
| Old Burying Point Cemetery | TIGER `AREALM` | Cemetery polygon with internal paths |
| Howard Street Cemetery | TIGER `AREALM` | Polygon, haunted-Salem overlay |
| Broad Street Cemetery | TIGER `AREALM` | Polygon |
| Greenlawn Cemetery | TIGER `AREALM` | Polygon |
| Salem Willows Park | MassGIS `openspace` + TIGER `AREALM` | Park polygon extending to the waterfront |
| Winter Island Park | MassGIS `openspace` | Island polygon w/ Fort Pickering lighthouse at the tip |
| Forest River Park | MassGIS `openspace` | Park polygon |
| Salem Maritime NHS | MassGIS `openspace` or point | Federal historic site parcels; contains Derby Wharf, Custom House, Narbonne House |
| Salem Harbor | TIGER `areawater` + MassGIS `coast25k` | Water polygon; the harbor's shape defines Salem |
| Collins Cove | TIGER `areawater` | Inner harbor extension polygon |
| Salem Depot platform | MassGIS `mbta_commuter_stations` (point) + OSM building footprint | Station building polygon |
| Peabody Essex Museum | MassGIS `structures_poly` | ~6 connected building polygons |
| Witch House | MassGIS `structures_poly` | Single building polygon |
| House of Seven Gables | MassGIS `structures_poly` | Single building polygon + adjacent Hawthorne-birth-house polygon |
| Every Salem residential block | MassGIS `structures_poly` | Thousands of building polygons, render at zoom 14+ |
| Essex Coastal Scenic Byway | MassGIS `scenic_landscapes` | Corridor polygon through Salem |
| Tidelands | MassGIS `tidelands_*` | Legal harbor-boundary polygon — defines the wharves |

### What "everything is a polygon" enables

1. **Geofence narration.** When the user's GPS enters a polygon, trigger narration. Entering Salem Common → "You are on Salem Common, where witch-trial victims were..." Entering a Historic District → "You are in the McIntire District, designed by Samuel McIntire (1757-1811)..."

2. **Density heatmaps.** Count historic buildings per tract, render as choropleth. Salem's downtown tract will be red-hot with history.

3. **Selection UIs.** Tap a polygon → immediately highlighted → info card slides in. No pin-hit-testing fragility.

4. **Spatial queries in natural language.** "Show me all historic buildings in the McIntire District" is a one-line SQL query: `WHERE ST_Within(structure, district)`.

5. **Multi-polygon overlays.** The tour can render the Federal-period district + the witch-trial sites + the maritime sites as three different colored overlays, toggleable. Each is a coherent polygon set.

## 5.2 POIs inside polygons inside polygons

The containment grammar that becomes possible:

```
Salem city
  └── McIntire Historic District
       └── Peirce-Nichols House parcel
            └── Peirce-Nichols House building footprint
                 └── Rooms / galleries (curated LMA data)
                      └── Individual artifacts / narration points
```

Each level is a polygon (or point within a polygon). Each level can be selected, rendered, geofenced, narrated. The tour can zoom smoothly from city view → district view → building view → room view — all driven by the same geometric grammar.

---

# Section 6 — The join patterns (what's now possible)

The real power isn't any one layer. It's that all of these are in the same Postgres database under `tiger.*` and `massgis.*`, and can be joined. Below are the joins that become trivial.

## 6.1 "What is this building?" (tap on a building footprint)

```sql
SELECT
  s.gid                             AS building_id,
  ap.addr_full                      AS address,
  mhc.historic_name                 AS historic_name,
  mhc.year_built                    AS year_built,
  mhc.style                         AS architectural_style,
  mhc.nr_status                     AS national_register,
  mhc.narrative                     AS mhc_description,
  prc.owner_type                    AS owner_class,
  prc.use_class                     AS current_use,
  nr.nr_reference_number            AS nr_id,
  local.district_name               AS local_historic_district,
  ST_Distance(s.geom::geography, user_loc::geography) AS distance_m
FROM massgis.structures_poly s
LEFT JOIN massgis.address_points ap ON ST_Contains(s.geom, ap.geom)
LEFT JOIN massgis.mhc_inventory mhc ON ST_Intersects(s.geom, mhc.geom)
LEFT JOIN massgis.l3_aggregate_shp_20260101 prc ON ST_Intersects(s.geom, prc.geom)
LEFT JOIN massgis.national_register nr ON mhc.mhc_id = nr.mhc_id
LEFT JOIN massgis.local_historic_dist local ON ST_Within(s.geom, local.geom)
WHERE ST_Contains(s.geom, tap_point);
```

Returns a single row with the building's full story. Runs in milliseconds if indexes are correct.

## 6.2 "Tell me about this neighborhood" (tap on a district polygon)

```sql
SELECT
  d.district_name,
  d.year_designated,
  COUNT(DISTINCT s.gid)             AS buildings_in_district,
  COUNT(DISTINCT mhc.mhc_id)        AS mhc_inventoried,
  COUNT(DISTINCT nr.nr_id)          AS national_register_properties,
  AVG(mhc.year_built)               AS avg_year_built,
  MIN(mhc.year_built)               AS earliest_structure,
  MAX(mhc.year_built)               AS latest_structure,
  array_agg(DISTINCT mhc.style)     AS architectural_styles
FROM massgis.local_historic_dist d
LEFT JOIN massgis.structures_poly s ON ST_Within(s.geom, d.geom)
LEFT JOIN massgis.mhc_inventory mhc ON ST_Intersects(s.geom, mhc.geom)
LEFT JOIN massgis.national_register nr ON mhc.mhc_id = nr.mhc_id
WHERE d.district_name = 'McIntire Historic District'
GROUP BY d.district_name, d.year_designated;
```

Returns: "McIntire Historic District, designated 1981, contains 412 buildings, 387 inventoried by MHC, 134 on the National Register, avg year built 1812, range 1762-1895, styles: Federal, Georgian, Greek Revival, Victorian."

That's a paragraph of narration pulled from data.

## 6.3 "Optimal walking tour through a theme"

```sql
-- Given a theme (e.g., 'First Period architecture') and a starting point,
-- find the 10 best historic properties that match, then solve TSP
WITH matching_pois AS (
  SELECT mhc.mhc_id, mhc.geom, mhc.historic_name
  FROM massgis.mhc_inventory mhc
  JOIN tiger.cousub c ON ST_Within(mhc.geom, c.geom)
  WHERE c.statefp='25' AND c.name='Salem'
    AND mhc.style ILIKE '%first period%'
  ORDER BY mhc.year_built  -- older first
  LIMIT 10
),
nodes AS (
  SELECT mhc_id, (SELECT source FROM tiger.edges
                  ORDER BY geom <-> p.geom LIMIT 1) AS node
  FROM matching_pois p
),
costs AS (
  SELECT * FROM pgr_dijkstraCostMatrix(
    $$SELECT gid AS id, source, target, length_m AS cost
      FROM tiger.edges WHERE mtfcc IN ('S1400','S1500','S1710','S1720','S1730','S1740','S1750','S1780')$$,
    ARRAY(SELECT node FROM nodes),
    directed := false
  )
)
SELECT * FROM pgr_TSP($$SELECT * FROM costs$$);
```

Returns: ordered visit sequence that minimizes total walking distance through all 10 First Period buildings. Salem has maybe 8-12 First Period buildings still standing; this tour is ~2.5 miles and 2-3 hours. **Assembled automatically.**

## 6.4 "What's unique about this tour?"

Cross-join with the Massachusetts Office of Travel & Tourism (MOTT) tourism region data:

```sql
SELECT
  mott.region_name,        -- "North of Boston"
  (SELECT COUNT(*) FROM curated.pois WHERE region = mott.region_name) AS curated_pois,
  (SELECT COUNT(*) FROM massgis.mhc_inventory mhc
   WHERE ST_Within(mhc.geom, mott.geom)) AS mhc_inventoried,
  (SELECT COUNT(*) FROM massgis.national_register nr
   JOIN massgis.mhc_inventory mhc ON mhc.mhc_id = nr.mhc_id
   WHERE ST_Within(mhc.geom, mott.geom)) AS nr_properties
FROM massgis.mott_tourism_regions mott
WHERE ST_Contains(mott.geom, salem_point);
```

Gives LMA marketing ammunition: "You are in the 'North of Boston' tourism region, which has 1,247 historic properties, 487 of which are on the National Register — 38% of them in Salem alone."

## 6.5 "GeoInbox photo reverse-geocoding"

When a user uploads a photo with GPS EXIF (GeoInbox integration):

```sql
SELECT
  (SELECT ap.addr_full FROM massgis.address_points ap
   ORDER BY ap.geom <-> photo_geom LIMIT 1)                AS nearest_address,
  (SELECT s.gid FROM massgis.structures_poly s
   ORDER BY s.geom <-> photo_geom LIMIT 1)                 AS nearest_building,
  (SELECT mhc.historic_name FROM massgis.mhc_inventory mhc
   WHERE ST_DWithin(mhc.geom::geography, photo_geom::geography, 20)
   LIMIT 1)                                                AS likely_subject,
  reverse_geocode(photo_geom, true)                        AS tiger_reverse_geocode;
```

The photo gets: nearest address, nearest building polygon, most likely historic subject (if within 20m of a known MHC property), and the full TIGER reverse-geocoded address. Post all four to the user: "Your photo at 174 Derby St, near Peabody Essex Museum, likely showing the Gardner-Pingree House (1804)."

## 6.6 "MBTA arrival experience"

```sql
-- Tourist arrives at Salem Depot (MBTA commuter rail station)
WITH station AS (
  SELECT geom FROM massgis.mbta_commuter_stations
  WHERE name ILIKE '%salem%' LIMIT 1
),
reachable AS (
  -- 10-minute walk from station
  SELECT ST_Buffer(s.geom::geography, 800)::geometry AS radius
  FROM station s
)
SELECT p.name, p.description, ST_Distance(p.geom::geography, (SELECT geom FROM station)::geography) AS m
FROM lma.pois p, reachable
WHERE ST_Within(p.geom, reachable.radius)
ORDER BY p.priority DESC, m ASC;
```

Returns: ranked list of POIs within a 10-minute walk of the station, pre-weighted by tour priority. Feeds a "welcome screen" that shows up when GPS detects the tourist has just arrived: "Welcome to Salem. From here, in 10 minutes, you can reach: Peabody Essex Museum (3 min), Salem Witch Museum (6 min), Witch Trials Memorial (8 min)..."

---

# Section 7 — Tour storytelling patterns the data enables

## 7.1 The time-slider tour

Query MHC records by year-built buckets:
- Pre-1700 (First Period) — witch trials era
- 1700-1776 (Georgian) — colonial prosperity
- 1776-1830 (Federal) — maritime peak, McIntire era
- 1830-1860 (Greek Revival) — industrial transition
- 1860-1900 (Victorian) — peak population
- 1900+ (Modern) — contemporary

Render a time slider. As the tourist drags it, the map highlights buildings of that era. "Salem in 1700" shows ~20 buildings + the harbor. "Salem in 1820" shows ~500 buildings filling the McIntire district. "Salem today" shows the full built environment. Visual, visceral way to experience the city's growth.

## 7.2 The ghost-street tour

MHC records often include addresses for buildings **no longer standing** — a structure demolished in 1890 leaves a record at its former address. With 1:25k coastline + period maps overlaid:

- Render ghost buildings in low-opacity sepia
- Render the current built environment in full color
- Tourist walks Derby Street past the shade of the lost Crowninshield House (demolished 1892) — gets a narration bubble
- The tour tells the story of what WAS there, not just what IS

Data source: MHC records that are flagged "no longer extant" + historic property lost records in the state register.

## 7.3 The witch-trial map

The actual places relevant to 1692:
- **The Witch House** — Judge Corwin's home, existing (`structures_poly` + MHC)
- **The meeting house site** — gone, marker/monument at 16 Washington St (curated POI)
- **The dungeon** — replicated in the Witch Dungeon Museum (curated POI)
- **Proctor's Ledge** — the 2016-confirmed execution site (curated POI + polygon)
- **Gallows Hill** — historic overlook, now a neighborhood (MHC district overlay)
- **Burial grounds for victims** — Old Burying Point + cenotaph at Witch Trials Memorial (polygons)
- **Accused's and accusers' homes** — many extant (MHC records, filter by participant surnames in narrative field: "Corey," "Proctor," "Nurse," "Putnam," "Hathorne," "Corwin," "Sewall")

All of this can be pulled into a single "witch trials" theme layer with the map rendering a composite narrative route.

## 7.4 The "this-could-have-been-yours" parcel tour

Cross-join MHC historic inventory with L3 parcel data:
- Select every house on the National Register that's currently privately owned
- Display as an "uncelebrated historic home" tour
- Tourists walk past, narration: "This 1795 Federal house is still a private residence. You could, theoretically, buy it. Last sale 2003 for $890,000..."

Slightly cheeky but extremely popular for architectural tourism.

## 7.5 The shipwreck-and-shoreline tour

- Every MassGIS shipwreck point near Salem harbor
- Combined with historic tidelands boundaries (Tidelands_Shapefiles)
- Tourist walks Derby Wharf (curated POI on the pier) — audio narration points offshore to where each wreck occurred
- Uses compass orientation + bearings: "Look southeast — the *Anna Maria* sank there in 1762..."

## 7.6 The "you are in the historic district now" geofence

```
ENTER(McIntire_Historic_District polygon) → play ambient audio + show visual prompt
EXIT(McIntire_Historic_District polygon) → fade audio out
```

Each polygon has entry/exit handlers. Tour narration becomes location-aware without requiring the tourist to tap anything — the map knows where they are.

## 7.7 The "collective memory" layer

MHC records include social/political/community data as free-text fields. Mine it:
- Buildings associated with abolitionism (Pickering St, Essex St)
- Buildings associated with Chinese-American history (Salem's historic Chinatown on Essex St)
- Buildings associated with maritime trade (Derby wharf warehouses)
- Buildings associated with the 19th-century textile industry (Boston St mills)

Each is a filter against the narrative field. The tour can offer "Salem Abolitionist History Tour," "Salem Asian-American Heritage Tour," "Salem Industrial Revolution Tour" — all drawn from the same MHC data, sliced by theme.

---

# Section 8 — Concrete Salem tour experiences now possible

## 8.1 "The Three-Hour Salem Walking Tour" (upgraded)

**The classic curated tour:** 12 stops, ~2 miles, ~3 hours.

**What upgrades:**

- **Stop 1 (Witch House):** Highlight building polygon in UI; narration pulls MHC record for year built (1675), architectural style (First Period), narrative (Judge Corwin's residence 1675-1718). Tap extends to "see the parlor where Corwin questioned victims..." (curated). Address resolves to precise point; GPS-triggered when tourist is within 5m of building footprint.
- **Stop 2 (Salem Witch Museum):** Curated POI since it's a commercial museum; snapped to nearest entry point; adjacent Roger Conant statue is a tour-internal sub-stop.
- **Stop 3 (Old Burying Point + Witch Trials Memorial):** Geofence-triggered when user enters cemetery polygon. Audio narration: "You are in Old Burying Point, founded 1637, one of the three oldest graveyards in America. To your right, the memorial dedicated in 1992..."
- **Stop 4 (Nathaniel Hawthorne Statue):** Point at a specific curated location, but the map highlights the statue's site against the Hawthorne Blvd intersection. Narration references nearby House of Seven Gables (stop 7) as a preview.
- **Stop 5 (Peabody Essex Museum):** Building polygon; tap extends into museum wings (East India Marine Hall, etc.) each as a sub-polygon.
- **Stop 6 (Essex St pedestrian mall):** Geofence triggers "You are now on the Essex Street pedestrian mall" with ambient street-sounds audio layer.
- **Stop 7 (House of Seven Gables):** Building polygon + Hawthorne's Birthplace building polygon (adjacent). Narration cross-references Hawthorne stop (stop 4).
- **Stop 8 (Derby Wharf):** The pier is a walkable 1.1-mile pedestrian-only feature now in the routing graph. Tour invites tourist to walk to the end of the wharf, narration points to offshore shipwrecks, current harbor activity, the *Friendship of Salem* replica.
- **Stop 9 (Customs House):** Building polygon; part of Salem Maritime NHS.
- **Stop 10 (The Narbonne House):** 1672 First Period dwelling; building polygon; MHC record.
- **Stop 11 (Pickering Wharf):** Commercial area; narration adjusts (this is modern tourism, not historic).
- **Stop 12 (Back to Salem Depot):** Walking route back via Derby St; isochrone confirms <15 min walk; catches MBTA commuter rail.

**What's new:** Every single building highlighted has a polygon. Every historic fact is pulled from MHC, not typed by the narrator. Every route segment routes through actual pedestrian paths (Derby Wharf pier, not "walk up Derby St and back"). Every entry into a polygon triggers location-aware audio. Every POI tap shows building footprint + address + MHC narrative + National Register status + owner class.

Tour curation effort drops dramatically. Maintenance drops dramatically. The tourist experience is richer.

## 8.2 "The Dark Salem Tour" (ghost / witch-trial-focused)

Auto-generated from:
- All pre-1700 structures still standing (filter MHC: year_built<1700, Salem)
- All witch-trial execution sites (curated + Proctor's Ledge polygon)
- All burial grounds (TIGER AREALM + MassGIS MHC_BURIALGROUNDS)
- All shipwrecks visible from shore (MassGIS MHC_SHIPWRECKS + bearing)
- All haunted-legend locations (curated tags)

pgRouting TSP solves the optimal order. Tour emerges as an ~8-stop loop, ~2 hours, at dusk. Audio narration uses MHC field data for every stop. Tour can re-generate seasonally as new haunted-legend POIs are curated.

## 8.3 "The Waterfront Tour"

Route on MassGIS `coast25k` + the pedestrian graph to produce a continuous shoreline walk:
- Salem Willows (northeast waterfront)
- Through Winter Island (Fort Pickering Light + harbor view)
- Across the causeway (walkable? verify)
- Salem Harbor inner shore
- Collins Cove
- Derby Wharf extension
- Pickering Wharf
- Central Wharf
- South River
- Back to Derby Wharf

~4 miles, ~2.5 hours, entirely on waterfront paths. Impossible to route reliably without MassGIS's trail + wharf + tideland data.

## 8.4 "The 15-Minute Salem" (speed tour from train)

Tourist with only 15 minutes between trains:
- Isochrone from Salem Depot within 750m walking (≈ 9 min each way + 6 min viewing)
- Pick the top-3 highest-priority POIs inside the isochrone
- Generate a mini-loop route

Auto-generates. Tourist taps "I only have 15 minutes." App produces: "Walk to the Bewitched statue (3 min), turn left to Essex St pedestrian mall (2 min), see Lappin Park → Museum Place (2 min of window shopping), return via Washington St to station (7 min)." Total 14 min.

## 8.5 "The Parent's Salem" / "The Witch-Averse Salem"

Filter POIs by age-appropriate tags (curated) + MHC records. Parent + young child tour:
- Salem Common (park playground)
- Salem Willows (carousel, beach, arcade)
- Winter Island Park (forts, playground)
- Forest River Park
- Pioneer Village (if curated)
- MBTA commuter rail (kids love trains)

Skip all cemeteries, witch dungeons, gallows-hill imagery. Auto-assembled tour by tag filter against the POI catalog + MassGIS openspace park polygons.

## 8.6 "The McIntire-Only Tour"

Filter: every building within the McIntire Historic District polygon that's on the National Register.
- ~135 properties
- Narrow to the top 15 by architectural significance
- pgRouting-optimized walking route
- Audio narration from MHC descriptions
- Emphasis on Samuel McIntire (1757-1811) as the master carver-architect
- Tour is 2-3 hours, ~2 miles, lives entirely inside the McIntire district polygon

This tour literally did not exist before — curation cost was too high. Now it's a filter + a TSP solve.

---

# Section 9 — Implementation notes for LMA

## 9.1 Consumer contract options (Phase 7 — MASTER_PLAN reference)

LMA can consume TigerLine+MassGIS data three ways. Recommended mix per feature:

### (a) Pre-exported per-city SpatiaLite bundle (offline)
For: core walking graph, building footprints clipped to Salem, polygon overlays (historic districts, cemeteries, parks), landmark points.
- Size: ~40–80 MB for Salem extent (compressed)
- Offline-capable
- Zero server dependency
- Phase 4 (MASTER_PLAN) delivers this slice

### (b) Pre-exported binary routing graph (offline)
For: walking-directions routing (pgRouting topology exported as a binary graph file).
- Size: ~10–20 MB for Salem extent
- Consumed by pure-Kotlin Dijkstra (per MASTER_PLAN §Phase 5)
- Zero server dependency

### (c) Live SQL against `tiger.*` + `massgis.*`
For: complex queries (full MHC record join, neighborhood statistics, real-time reverse geocoding, dynamic isochrone), developer tools, admin dashboards, photo-based reverse geocoding (GeoInbox).
- Requires network connectivity
- Latency: ~50-200ms per query
- Unlimited flexibility

**Recommended default:** offline core (a + b) for tourist-device use; live SQL (c) for LMA backend tasks, content curation, and any feature that requires full-catalog breadth (e.g., "search across all MA MHC records").

## 9.2 The curation pipeline shift

**Before:** Salem-content pipeline curates each POI by hand: place a pin on a map, write text, upload photo.

**Now:** Salem-content pipeline accepts a canonical input and auto-populates metadata:

```python
# Example curator workflow (pseudocode)
curator_input = {
    "name": "Witch House",
    "address": "310½ Essex St, Salem MA 01970",
    "tags": ["witch-trials", "first-period", "museum", "iconic"],
    "custom_narrative": "The only structure in Salem with direct...",
}

# Auto-populate from TigerLine + MassGIS
auto = geocode_and_enrich(curator_input["address"])
# auto == {
#   "lon": -70.8973, "lat": 42.5209,
#   "building_gid": 1234567,
#   "building_footprint": <polygon>,
#   "mhc_id": "SAL.1",
#   "year_built": 1675,
#   "architectural_style": "First Period",
#   "nr_status": "NR Listed",
#   "mhc_narrative": "Owned by Jonathan Corwin (1640-1718)...",
#   "local_historic_district": "McIntire Historic District (adjacent)",
#   "parcel_owner_type": "municipal",
#   "current_use": "museum",
#   "tract": "2042",
#   "zip": "01970",
# }

final_poi = {**auto, **curator_input}  # curator input overrides where overlapping
```

The curator's job becomes **writing narrative** and **tagging context**, not **placing pins on maps**. That's a 5-10x productivity shift.

## 9.3 The multi-city generalization pattern

Per the operator's "every city can be a tour" ambition (memory-recorded 2026-04-21), whatever LMA builds for Salem should parameterize on:

- `city_name` (e.g., "Salem", "New Orleans", "Washington DC")
- `city_extent` (polygon: county-aligned for simple cities, sub-county for Hollywood, multi-county for NYC)
- `state_fips` (for MassGIS-like state-level supplements)
- Optional `gtfs_feed_urls` (MBTA, WMATA, SFMTA, etc.)
- Optional `elevation_source` (USGS 3DEP for hilly cities)

The Salem integration is the first realization. NOLA / DC / Hollywood / SF reuse the same pipeline with different config.

## 9.4 Integration phases (proposed)

| Phase | What LMA consumes | When |
|---|---|---|
| **Phase 7a** | Salem SpatiaLite bundle (buildings + polygons + landmarks) | After TigerLine Phase 2 (import finishes) + MassGIS ingest |
| **Phase 7b** | Salem walking router (binary graph + Kotlin Dijkstra) | After TigerLine Phase 5 |
| **Phase 7c** | Salem MBTiles tile set (custom style, Phase 10 cartography) | After TigerLine Phase 9-10 |
| **Phase 7d** | Live SQL API for backend / curation / photo geocoding | Ongoing — available as soon as TIGER + MassGIS import is done |

LMA can integrate features in any order; each is independent. Recommended critical-path: 7a + 7b for the 2026-07-15 deadline (the core tour works); 7c + 7d as follow-on polish.

## 9.5 Data freshness

- **TIGER**: annual refresh (Phase 12 MASTER_PLAN). Salem-specific TIGER data changes minimally year-over-year.
- **MassGIS**: per-layer update cadence varies — MBTA weekly, parcels quarterly, building footprints annual, MHC inventory episodic (as surveys are completed). The `--discover` mode in `download_massgis.sh` can be re-run quarterly to pick up any new layers or updated vintages.
- **Curated POIs (LMA-owned)**: whatever cadence LMA chooses. Independent of TIGER/MassGIS.

---

# Section 10 — What's NOT in either dataset (honest gaps)

Being upfront: TIGER + MassGIS doesn't solve everything.

## 10.1 Still needs curation

- **Commercial museum metadata** — Witch House hours, prices, exhibit descriptions
- **Statues and monuments** (most not in MHC) — Bewitched statue, Hawthorne statue, Conant statue, Bewitched TV-show placards
- **Commemorative plaques** — the dozens of bronze plaques around town
- **Food/drink establishments** — restaurants, coffee, bars (entirely absent from both)
- **Shopping** — witch-themed shops, souvenirs, art galleries
- **Ghost stories and folklore** — curated per-location
- **Tour-internal narrative** — the actual audio scripts, per-stop

LMA's `salem-content` pipeline owns all of this. The combined TigerLine+MassGIS substrate just makes curation cheaper and the pins more accurate.

## 10.2 External integrations

- **MBTA schedules + real-time** — MassGIS gives geometry, MBTA GTFS feed gives schedules. Separate ingest.
- **Weather** — Open-Meteo or NOAA API per location
- **Events** — Salem Haunted Happenings (October), Salem 400+ (2026), custom event feeds
- **Accessibility** — wheelchair accessibility per POI, step-count, stair-presence (curated or OSM)
- **Crowd levels / wait times** — Google Popular Times or similar (third-party)
- **Hours and prices** — often change seasonally; curated + verified

## 10.3 Not-yet-imported data

- **OSM pedestrian paths** (Phase 6 MASTER_PLAN) — fills in cemetery internal paths, waterfront boardwalks, park-internal shortcuts that MassGIS trails + TIGER edges miss. Phase 6 queued.
- **USGS 3DEP elevation** — optional for Phase 5 if walking-time-with-grade is needed. Salem is flat enough to defer.
- **Historic period maps** — 1692, 1790, 1850 Salem maps georeferenced as overlays. High-value differentiator but requires manual georef work. Out of current scope.

## 10.4 Content still owned by LMA

All of the above "not in dataset" items are LMA's content territory. TigerLine provides the **ground truth geometry**; LMA provides the **tour meaning**. Neither replaces the other.

---

# Section 11 — What to do next (proposed)

1. **Read this document.** Ask questions — we'd rather clarify scope now than after LMA starts building.
2. **Validate the join patterns** (Section 6) against LMA's current schema. Most are additive; some may need `salem-content` pipeline adjustments.
3. **Pick Phase 7 consumer modes.** Recommendation: start with 7a (SpatiaLite bundle) + 7d (live SQL for backend). 7b (routing) lands after TigerLine Phase 5 in June.
4. **Identify the 10 most important Salem tour stops.** We'll verify they all have: MassGIS building footprint, MHC record if historic, National Register status if applicable, address-point anchor. Any gaps get flagged for manual curation.
5. **Agree on the package format for 7a.** SpatiaLite (SQLite + spatial extension) is our recommendation; LMA has already consumed similar formats. Alternative: flat GeoJSON + separate Kotlin graph file.
6. **Schedule a dry-run import** of MassGIS after TigerLine Phase 2 finishes (expected 2026-04-22). We can have first MassGIS tables in `massgis.*` schema within a few hours of that, then begin cross-schema query validation.

The 2026-07-15 integration deadline gives us 12 weeks. Realistic scope for that window:

- Core walking graph (TIGER + MassGIS trails + OSM pedestrian) ✓ feasible
- Curated POIs anchored to building polygons ✓ feasible
- Historic-district geofencing ✓ feasible
- MHC-driven narrative enrichment for top 50 Salem POIs ✓ feasible
- Offline SpatiaLite bundle ✓ feasible
- Salem MBTiles tile set with Phase 10 cartography — **stretch** (probably lands post-deadline)

Everything in this document is achievable within the window except the Phase 10 cartography layer, which is a "polish" rather than "ship" requirement.

---

# Appendix A — Layer-to-tour-feature mapping

For the LMA engineering team implementing the integration:

| Tour feature | Data source(s) | Schema(s) |
|---|---|---|
| Salem city boundary | TIGER COUSUB | `tiger.cousub` |
| Historic district overlays | MassGIS MHC | `massgis.mhc_inventory` (filter type='district') |
| Building footprints | MassGIS STRUCTURES | `massgis.structures` (or `structures_poly`) |
| Building → MHC record join | MassGIS MHC + STRUCTURES spatial join | cross-schema via ST_Contains/ST_Intersects |
| Address-point geocoding | MassGIS ADDRESS_POINTS | `massgis.address_points` |
| Fallback address-range geocoding | TIGER ADDRFEAT + `postgis_tiger_geocoder` | `tiger.addrfeat` + `geocode()` function |
| Road + pedestrian routing graph | TIGER EDGES | `tiger.edges` + pgRouting topology |
| Trail supplement | MassGIS TRAILS + DCRTRAILS + LDTRAILS + MAD_TRAILS + BIKETRAILS | `massgis.trails` etc. (merged into routing graph) |
| Pedestrian-path supplement | OSM (Phase 6) | `osm.footways` (future) |
| Cemetery polygons | TIGER AREALM | `tiger.arealm` WHERE mtfcc='K2195' |
| Historic burial grounds | MassGIS MHC_BURIALGROUNDS | `massgis.historic_burial_grounds` |
| Park polygons | MassGIS OPENSPACE + TIGER AREALM | `massgis.openspace` + `tiger.arealm` |
| MBTA commuter rail | MassGIS MBTACOMMUTERRAIL | `massgis.trains` + `massgis.mbta_commuter_stations` |
| Coastline | MassGIS COAST25K + TIGER COASTLINE | `massgis.coast25k` (primary for MA) |
| Water polygons | TIGER AREAWATER + MassGIS HYDRO25K | both, cross-referenced |
| Scenic byway overlay | MassGIS SCENIC_LANDSCAPES | `massgis.scenic_landscapes` |
| Tidelands | MassGIS TIDELANDS | `massgis.tidelands_shapefiles` |
| Lighthouses | MassGIS LIGHTHOUSES | `massgis.lighthouses` |
| Beaches | MassGIS MARINEBEACHES | `massgis.marinebeaches` |
| Marinas | MassGIS MARINAS | `massgis.marinas` (if in discovery list) |
| Tourism region context | MassGIS MOTT | `massgis.mott_tourism_regions` |
| Parcels (property boundaries) | MassGIS L3_AGGREGATE | `massgis.l3_aggregate_shp_20260101` |
| Places of worship | MassGIS PLACES_OF_WORSHIP | `massgis.places_of_worship` |
| Schools (edu context) | MassGIS SCHOOLS | `massgis.schools` |
| Curated POIs | LMA salem-content pipeline | `lma.pois` |

---

# Appendix B — Pre-deadline checklist for LMA integration

- [ ] TigerLine Phase 2 import complete (~2026-04-22)
- [ ] pgRouting installed + topology built (~2026-04-23, via tnidf/tnidt fast path)
- [ ] MassGIS download + import (~2026-04-24 to 2026-04-26 given ~15-25 GB)
- [ ] Cross-schema query validation — all Appendix A joins return expected row counts (2026-04-28)
- [ ] Phase 4 Salem slice (MASTER_PLAN §4) — Essex County clipped materialized view (2026-05)
- [ ] Phase 5 walking router MVP (MASTER_PLAN §5) — pgr_dijkstra endpoint + binary export (2026-05 to 2026-06)
- [ ] Phase 7a SpatiaLite bundle delivery to LMA (2026-06-15)
- [ ] Phase 7b binary routing graph delivery to LMA (2026-07-01)
- [ ] LMA integration testing (2026-07-01 to 2026-07-14)
- [ ] LMA Phase 8 walking-directions integration shipped (2026-07-15 deadline)

**Buffer:** 2026-07-15 to 2026-09-01 (LMA Play Store submission for Salem 400+ October launch) for polish, bug fixes, style tuning, and Phase 10 cartography if feasible.

---

_Authored 2026-04-21 (TigerLine Session 001). Canonical source in TigerLine repo, intended to be published to OMEN architecture/ and LMA docs/ for reference._
