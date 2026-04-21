<!--
PUBLISHED SNAPSHOT — NOT THE CANONICAL SOURCE.

Canonical file:  ~/Research/TigerLine/SALEM_TOUR_FEATURES.md
Source repo:     github.com/deanmauriceellis-cloud/TigerLine
Source commit:   98cdaad6125c100bf812e65b6046c410a11b473c
Published to LMA: 2026-04-20 (TigerLine Session 001)

This is TigerLine's per-layer inventory of TIGER/Line 2025 features available
for the Salem tour, written from TigerLine's perspective as the geospatial
foundation layer. LMA consumes these features via the Phase 7 consumer contract
(pre-exported SpatiaLite + binary routing graph + MBTiles, and/or live SQL
against the tiger.* schema). See TigerLine MASTER_PLAN.md §Phase 7 for details.

Do not edit this copy directly — any changes should be made in the TigerLine
repo and re-published here. This copy is for LMA's reference while planning
API surfaces and map-integration strategy.
-->

# Salem Tour — Mappable Features Reference

> **Purpose.** An exhaustive inventory of every feature TigerLine's PostGIS ingest makes available for the Salem tour (LMA's Katrina's Mystic Visitors Guide). For each layer: what it contains, what it gives us *specifically for Salem*, and a blunt verdict on whether it benefits the tour.
>
> **Generated from:** TIGER/Line 2025 vintage schema (all 49 layers), the `postgis_tiger_geocoder` extension's table conventions, and the MASTER_PLAN.md Phase 5 walking-router deliverable. Salem status fields noted as "will have data" / "expected empty" / "verify after Phase 2 import completes."

---

## Geographic scope of "Salem" in TIGER terms

Salem MA is identified at multiple nesting levels in TIGER's hierarchy. Any Salem-only slice of the data uses these keys:

| TIGER hierarchy level | FIPS code (standard) | Notes |
|---|---|---|
| State (MA) | `STATEFP = 25` | Massachusetts |
| County (Essex) | `COUNTYFP = 009` (full: `25009`) | Salem's county; also contains Peabody, Danvers, Beverly, Marblehead, Gloucester, etc. |
| County subdivision (Salem city) | `COUSUBFP = 60340` (within 25009) | Salem is a "city" in MA parlance, encoded as a COUSUB. **Verify exact FIPS after import.** |
| Incorporated place (Salem) | `PLACEFP` — distinct from COUSUBFP | MA has dual-encoding: Salem appears in both PLACE and COUSUB with potentially different FIPS. Reconcile after import. |
| Census tracts covering Salem | `TRACTCE` values within `25009` | Salem city proper spans approximately 10–14 census tracts. |

**Practical Salem slice query** (use after Phase 2 import):

```sql
-- All roads inside Salem city boundary
SELECT r.* FROM tiger.roads r
JOIN tiger.cousub c ON ST_Intersects(r.geom, c.geom)
WHERE c.statefp = '25' AND c.cousubfp = '60340';
```

**Tour working extent** likely needs to be broader than Salem city proper — visitors may stay in Peabody, Marblehead, or along the MBTA commuter-rail corridor. A practical bounding polygon should include:
- Salem city (core walking tour zone)
- Peabody (neighbor, some tour stops may be there)
- Marblehead (scenic waterfront, included in some tour extensions)
- Danvers (historic, witch-trial-era Salem Village is now in Danvers)
- Beverly (across the harbor)

All five of these are in Essex County FIPS 25009, so a county-level slice captures everything relevant.

---

## Benefit rating shorthand (used below)

- **CRITICAL** — load-bearing for the tour app; won't work without it
- **HIGH** — directly visible on the tour map OR substantially improves UX
- **MEDIUM** — useful for analytics, filtering, secondary features; not user-facing
- **LOW** — technically available but no obvious tour use case
- **NONE** — not applicable to Salem MA (empty data expected)

---

# Part I — Administrative & boundary layers

These layers answer the question "Where is Salem, and what's around it?" They provide the canvas on which every other feature is drawn and allow geographic filtering of all other data.

## STATE

**Contents.** One polygon per U.S. state, plus territories. National file (1 zip).
**Columns.** `statefp` (2-digit FIPS), `stusps` (USPS 2-letter), `name`, `geom` (MultiPolygon).
**Salem status.** Will contain a single Massachusetts polygon. Useful primarily as a reference frame.
**Tour benefit: MEDIUM** — used by the map renderer as the outermost boundary and for "you are in Massachusetts" labeling. Also needed to filter all other layers to MA.

## COUNTY

**Contents.** One polygon per U.S. county / parish / independent city. National file (1 zip). ~3,234 features.
**Columns.** `statefp`, `countyfp`, `name` (e.g., "Essex"), `namelsad` ("Essex County"), `geom`.
**Salem status.** Essex County MA polygon (FIPS 25009) will be present. Also its neighbors: Middlesex (25017), Suffolk (25025 — Boston), Rockingham NH (33015).
**Tour benefit: HIGH** — defines the tour's effective universe (Essex County is the sensible outer bound). Used for clipping other layers, for "show only Essex County data" filters, and for rendering county-line labels at lower zooms.

## COUSUB

**Contents.** County subdivisions — in New England states (including MA) this is the **town/city** layer. 56 files (one per state). A Salem tour cares specifically about Essex County's COUSUBs.
**Columns.** `statefp`, `countyfp`, `cousubfp`, `name` (e.g., "Salem"), `namelsad` ("Salem city"), `funcstat`, `geom`.
**Salem status.** Will contain Salem (city), Peabody, Beverly, Marblehead, Danvers, Gloucester, Lynn, Swampscott, Ipswich, Newburyport, and every other Essex County town as distinct polygons.
**Tour benefit: CRITICAL** — this is the **official Salem city boundary**. Every "is this point in Salem?" query uses COUSUB. Essential for:
- Drawing the city outline on the map
- Filtering tour stops to Salem city only
- Computing "X minutes walk to leave Salem"
- Display "You are in Salem" / "You are entering Peabody" context

## PLACE

**Contents.** Incorporated places (cities, towns, villages) and Census-Designated Places. 56 files.
**Columns.** `statefp`, `placefp`, `name`, `namelsad`, `classfp` (incorporation type), `geom`.
**Salem status.** Salem will appear here too. In MA, COUSUB and PLACE often represent the same geographic reality with subtly different definitions (COUSUB is the general-purpose division; PLACE tracks incorporation).
**Tour benefit: MEDIUM** — partly redundant with COUSUB for MA but preserves place-class distinctions (incorporated vs CDP). Useful for cross-referencing with Census data and some state-level datasets that key off PLACE.
**Practical note.** For MA specifically, prefer COUSUB for boundary queries unless you specifically need incorporation metadata.

## CBSA (Core Based Statistical Area)

**Contents.** Census Bureau's metropolitan and micropolitan statistical areas. National file (1 zip). ~950 features.
**Columns.** `cbsafp`, `name` (e.g., "Boston-Cambridge-Newton, MA-NH"), `lsad`, `geom`.
**Salem status.** Salem is inside the **Boston-Cambridge-Newton, MA-NH Metropolitan Statistical Area** (CBSA 14460).
**Tour benefit: LOW** — useful if the tour ever shows "regional context" cards ("Salem is part of Greater Boston, which has 4.9M residents"), otherwise not user-facing. Metadata-only.

## CSA (Combined Statistical Area)

**Contents.** Super-metropolitan groupings of adjacent CBSAs. National file (1 zip). ~175 features.
**Columns.** `csafp`, `name` (e.g., "Boston-Worcester-Providence, MA-RI-NH-CT"), `geom`.
**Salem status.** Part of **Boston-Worcester-Providence CSA** (CSA 148).
**Tour benefit: LOW** — same rationale as CBSA, even broader. Reference only.

## METDIV (Metropolitan Division)

**Contents.** Subdivisions inside the largest CBSAs. Only relevant for very large metros (NYC, Chicago, LA, DC, Boston). National file (1 zip).
**Columns.** `metdivfp`, `name` (e.g., "Boston, MA"), `geom`.
**Salem status.** Salem is inside the **Boston, MA Metropolitan Division** (a subdivision of the Boston-Cambridge-Newton CBSA).
**Tour benefit: LOW** — informational only.

## CD (Congressional District)

**Contents.** 119th Congress districts (2025–2027). 56 files.
**Columns.** `statefp`, `cdfp` (district number), `geom`.
**Salem status.** Salem is in **MA-6** (6th Congressional District — Seth Moulton, most of Essex County including Cape Ann).
**Tour benefit: LOW** — political boundary, not tour-relevant unless the tour ever surfaces civic info ("Contact your congressperson about Salem 400+ celebrations"). Skip.

## SLDU (State Legislative District — Upper, i.e., State Senate)

**Contents.** State senate districts. 52 files (states + DC + PR; some states have no upper chamber).
**Columns.** `statefp`, `sldust` (district identifier), `geom`.
**Salem status.** Salem is in MA's **Second Essex state senate district**.
**Tour benefit: LOW** — civic/political, not tourism.

## SLDL (State Legislative District — Lower, i.e., State House)

**Contents.** State house/assembly districts. 50 files.
**Columns.** `statefp`, `sldlst`, `geom`.
**Salem status.** Salem is in MA's **7th Essex state representative district**.
**Tour benefit: LOW** — civic/political.

## ZCTA520 (ZIP Code Tabulation Area, 2020)

**Contents.** Polygon approximations of USPS ZIP code delivery areas (ZCTAs are NOT actual ZIP boundaries — they're the Census Bureau's best polygon fit, but USPS doesn't publish true ZIP polygons). National file (1 zip). ~33,000 features.
**Columns.** `zcta5ce20` (5-digit ZCTA), `geom`.
**Salem status.** Salem's ZIP codes: **01970** (primary — downtown, most of the city), **01971** (PO Box delivery only — usually no polygon), **01945** (Marblehead, adjacent). Will show as coverage polygons.
**Tour benefit: MEDIUM** — useful for:
- Postal-address geocoding ("01970" resolves to Salem)
- Showing "ZIP boundary" overlays if ever useful
- Cross-referencing with ZIP-level tourism/demographics data (e.g., hotel-availability queries)
- Fallback for address-to-area lookup when street-level geocoding fails

## UAC20 (Urban Area / Urban Cluster, 2020)

**Contents.** Census-defined urban areas — contiguous built-up territory. National file (1 zip).
**Columns.** `uace20` (code), `name` (e.g., "Boston, MA--NH--RI Urban Area"), `uatype20` (U=Urban Area, C=Urban Cluster), `geom`.
**Salem status.** Salem is inside the **Boston MA-NH-RI Urban Area** (one giant contiguous blob covering Greater Boston including Salem).
**Tour benefit: LOW** — used by Census for urban/rural classification. Tourist-facing relevance zero. Useful only if the tour app ever does "we're in an urban zone, not suburban" type logic.

## PUMA20 (Public Use Microdata Area, 2020)

**Contents.** Census statistical areas of ~100,000 people each, used for the American Community Survey microdata. 53 files (states + DC + PR).
**Columns.** `statefp`, `pumace20`, `name`, `geom`.
**Salem status.** Salem is in a PUMA that roughly covers the "North Shore" — Salem + Beverly + Danvers + neighbors.
**Tour benefit: LOW** — demographic-analysis geometry only. No tourism use.

## UNSD / ELSD / SCSD / SDADM (School Districts — Unified / Elementary / Secondary / Administrative)

**Contents.**
- **UNSD** (Unified School District) — one district handles both elementary and secondary. 56 files.
- **ELSD** (Elementary School District) — elementary-only districts. 25 files (not all states have these).
- **SCSD** (Secondary School District) — secondary-only districts. 19 files.
- **SDADM** — administrative school district boundaries (unified + elementary + secondary combined). 1 national file.

**Columns.** `statefp`, `unsdlea`/`elsdlea`/`scsdlea`, `name` (e.g., "Salem Public Schools"), `geom`.
**Salem status.** Salem Public Schools (unified district covering Salem city). Neighboring districts: Peabody Public, Beverly Public, Marblehead Public.
**Tour benefit: NONE** — school districts have no tour relevance (tourists aren't enrolling their kids). Possibly could matter if the app ever showed "family-friendly" safety ratings derived from school performance, but that's a stretch.

## BG (Block Group)

**Contents.** Subdivisions of census tracts. Typically 600–3,000 people each. 56 files. ~240,000 features nationwide.
**Columns.** `statefp`, `countyfp`, `tractce`, `blkgrpce`, `geohash` (geoid), `geom`.
**Salem status.** Salem city contains ~30–50 block groups.
**Tour benefit: MEDIUM** — primary unit for ACS (American Community Survey) demographic data. Useful if the tour integrates census info ("this neighborhood has X median income / Y demographics") or for clustering tour stops by neighborhood. Not directly displayed but useful analytically.

## TRACT (Census Tract)

**Contents.** ~1,200–8,000-person statistical subdivisions of counties. 56 files. ~74,000 features nationwide.
**Columns.** `statefp`, `countyfp`, `tractce` (6-digit), `name` (e.g., "2045.01"), `geom`.
**Salem status.** Salem city spans approximately 10–14 tracts. Each tract aligns with reasonably coherent neighborhoods.
**Tour benefit: MEDIUM** — same use case as BG but coarser. Often the right granularity for "neighborhood" labels on the map (tract 2045.01 ≈ "downtown Salem / The Point / waterfront"). A Salem-specific tract-to-neighborhood-name table would be valuable curated metadata.

## TABBLOCK20 (Census Block, 2020)

**Contents.** The **smallest** Census geography. Typically one city block or a subset thereof. 56 files. ~8.1 million features nationwide.
**Columns.** `statefp`, `countyfp`, `tractce`, `blockce20`, `geohash`, `geom`.
**Salem status.** Salem city contains thousands of blocks. Each block is typically a single city block bounded by streets.
**Tour benefit: MEDIUM** — useful for:
- Very high-zoom rendering of "block-level" geometry
- Population-density coloring at the finest granularity
- Enforcing "no tour stops on non-road blocks" filters
Likely not user-facing but a technical building block.

## TABBLOCKSUFX (Block Suffix)

**Contents.** Additional block subdivision qualifier (rarely populated — most blocks have no suffix). 56 files.
**Columns.** Block-suffix identifiers.
**Salem status.** Essex County likely has zero or few suffix-qualified blocks.
**Tour benefit: NONE** — niche Census-internal concept. Ignore.

---

# Part II — The street network (routing backbone)

These layers are the core of any walking-directions system. Phase 5 of the MASTER_PLAN is built on them.

## EDGES

**Contents.** **Every** road, street, path, driveway, alley, and ramp segment in the U.S., represented as directed line geometry in the topologically complete TIGER graph. One file per county. 3,234 files total (one for every U.S. county).
**Columns.** `tlid` (topology linear ID — the key), `statefp`, `countyfp`, `tfidl`/`tfidr` (face IDs on left/right), `tnidf`/`tnidt` (node IDs at from/to), `mtfcc` (feature class code: S1100=primary road, S1200=secondary, S1400=local neighborhood road, S1500=vehicular trail, S1630=ramp, S1710=walkway/pedestrian, S1720=stairway, etc.), `fullname` (street name), `smid` (street match ID), `roadflg` ('Y' if drivable), `geom` (LineString).
**Salem status.** Essex County's EDGES file (`tl_2025_25009_edges.zip`) will have every street in Salem + all of Essex County. Expect ~50,000–150,000 edges for Essex County alone.
**Tour benefit: CRITICAL** —
- **This is the routing graph.** pgRouting builds its topology on EDGES. Walking directions = pgRouting queries against EDGES.
- Filtered by `mtfcc IN (S1400, S1500, S1710, S1720, S1730, S1740, S1750, S1780)` to get pedestrian-navigable edges only.
- **Without EDGES, no walking directions. Full stop.**

## ROADS

**Contents.** Road geometry, per county. Similar to EDGES but simpler — no topological IDs, just the road geometry with name and class. 3,225 files (9 counties' worth are Census-absent per our Phase 1 gap list, none in MA).
**Columns.** `linearid` (unique road segment ID), `fullname`, `rttyp` (route type: I=Interstate, U=US route, S=state, C=county, M=common name, O=other), `mtfcc`, `geom`.
**Salem status.** Essex County's ROADS file present. All Salem streets — Essex St, Derby St, Hawthorne Blvd, Lafayette St, Route 114, Route 107, Route 1A, etc.
**Tour benefit: HIGH** —
- Use when you need a **pre-aggregated** road representation (continuous Lafayette St as one feature, rather than many EDGES segments).
- Better than EDGES for map rendering (fewer features, cleaner labels).
- EDGES for routing, ROADS for drawing. Both are used.

## PRIMARYROADS

**Contents.** National file (1 zip). Only **interstate highways + US routes + state primary routes**.
**Columns.** `linearid`, `fullname`, `rttyp`, `mtfcc` (S1100 for Primary Roads), `geom`.
**Salem status.** I-95 passes ~10 miles west of Salem (through Peabody, Danvers, Beverly). US-1 passes through Peabody. Route 128 (I-95 spur) nearby. **No primary road enters Salem city proper.** Closest: Route 114 (Highland Ave), which is a *primary secondary* (appears in PRISECROADS, not PRIMARYROADS).
**Tour benefit: MEDIUM** — useful for:
- Pre-tour context maps ("Salem is 20 miles NE of Boston via Route 128")
- Driver-direction "you'll exit I-95 at..." flows (if the app ever gives pre-arrival driving directions)
- Low-zoom basemap rendering (show interstates at zoom 6–10)

## PRISECROADS

**Contents.** Primary + Secondary roads combined, per state. 56 files.
**Columns.** Same as PRIMARYROADS, plus state/US numbered secondary routes.
**Salem status.** Will include Route 114, Route 107, Route 1A through Salem. These are the **arterials** of Salem — Highland Ave, Loring Ave, North St, Boston St.
**Tour benefit: HIGH** — the arterials are the backbone of any Salem basemap. Every tourist orients themselves by Highland Ave and the harbor road. These roads need to render at zooms 10–14.

## RAILS

**Contents.** Rail lines (freight + passenger + transit). 1 national file.
**Columns.** `linearid`, `fullname` (e.g., "MBTA Commuter Rail Newburyport/Rockport Line"), `mtfcc` (R1011 = main rail, R1051 = carline/streetcar, R1052 = light rail), `geom`.
**Salem status.** **MBTA Commuter Rail Newburyport/Rockport Line** runs through Salem, including **Salem Station (Salem Depot)** downtown. This line serves every ~30–60 min from North Station (Boston). Major arrival mode for tourists.
**Tour benefit: HIGH** —
- Render rail lines on the map (visual cue, orientation).
- Salem Station is a primary arrival point for ~50% of day-trip tourists from Boston.
- Computing "walking time from Salem Station to Witch Museum" is a top-10 use case.

## FEATNAMES

**Contents.** Name records **linked** to EDGES. Not geometry — non-spatial attribute data (DBF-only). Per-county. 3,235 files.
**Columns.** `tlid` (links to EDGES.tlid), `fullname`, `name`, `predir`/`pretyp`/`pretypabrv`/`prequal`, `sufdir`/`suftyp`/`suftypabrv`/`sufqual`, `paflag` (primary/alternate flag — Y=primary name, N=alternate).
**Salem status.** Every Salem street's alternate names. E.g., Lafayette St also known as Route 114. Essex St has historical names. Etc.
**Tour benefit: HIGH** — enables:
- Geocoder tolerance ("I typed 'Route 114'" resolves to Lafayette St via FEATNAMES)
- Displaying alternate/historical names on hover
- Street name normalization in address queries

---

# Part III — Addresses & geocoding

## ADDR

**Contents.** **Address ranges** keyed to street segments. For each side of each road, the from-house-number and to-house-number, plus ZIP. Per-county. 3,227 files.
**Columns.** `tlid` (links to EDGES), `fromhn`, `tohn`, `side` (L=left, R=right), `zip`, `plus4`, `fromtyp`/`totyp` (numeric/alpha/other), `fromarmid`/`toarmid` (address range master IDs), `arid` (address range ID), `mtfcc`, `statefp`, `geom` (inherited from linked edge).
**Salem status.** Every Salem street's address ranges. E.g., "Essex St odd 100–198 left side, even 101–199 right side, ZIP 01970."
**Tour benefit: CRITICAL** for geocoding —
- Resolving a street address to a map location: "174 Derby St" → point on the Derby St segment corresponding to that address range.
- Reverse geocoding: tap a point near Derby St → "near 174 Derby St"
- **3,141 of 3,227 files already completed** from a prior import run (pre-scaffold 2026-04-16). Essex County's file is among the completed set, meaning **Salem addresses are already queryable** in `tiger.addr`.

## ADDRFEAT

**Contents.** Address features — edges with address-range data pre-joined. Per-county. 3,226 files.
**Columns.** Combines EDGES geometry with ADDR attributes: `tlid`, `fullname`, `lfromhn`/`ltohn`/`rfromhn`/`rtohn` (left/right from/to), `zipl`/`zipr` (left/right ZIP), `parityl`/`parityr` (O=odd, E=even, B=both), `edge_mtfcc`, `geom`.
**Salem status.** Essex County ADDRFEAT file is present (3,226 files means almost all U.S. counties). **Ingham MI (26065) is the only county genuinely missing from this layer in our gap list — Essex MA is fine.**
**Tour benefit: HIGH** — simpler than joining EDGES ⋈ ADDR; use directly for:
- Street-level geocoder queries
- Labeling streets with address-range info
- "What's at 174 Derby St?" reverse lookup returns an ADDRFEAT segment directly

## ADDRFN

**Contents.** Address-to-feature-name linkage. Non-spatial (DBF-only). Per-county. 3,226 files.
**Columns.** `arid` (links to ADDR.arid), `linkid` (links to FEATNAMES indirectly), sequence info.
**Salem status.** Present for Essex County.
**Tour benefit: MEDIUM** — the technical glue that lets a geocoder answer "Lafayette St, which is also Route 114, which is also..." Not directly queried by the UI but enables robust address matching.

---

# Part IV — Water & coastline (huge for Salem)

**Salem is a port city.** Its entire identity and half its tour-worthy sites are waterfront: Derby Wharf, Pickering Wharf, Winter Island, Salem Willows, the Friendship of Salem replica ship, the Maritime National Historic Site, Salem Harbor itself. **Water layers are unusually high-value for this tour** compared to a typical inland tour.

## AREAWATER (Area Water)

**Contents.** Water features represented as **polygons**: lakes, ponds, harbors, wide rivers, reservoirs, swamps (if mapped). Per-county. 3,235 files.
**Columns.** `hydroid`, `fullname` (e.g., "Salem Harbor"), `mtfcc` (H2030=lake/pond, H2040=reservoir, H2051=bay/estuary, H2053=ocean, H3010=stream/river, H3013=braided stream, H2081=glacier, etc.), `geom` (MultiPolygon).
**Salem status.** Essex County AREAWATER will contain:
- **Salem Harbor** (H2051 — bay/estuary) — the iconic Salem feature
- **Collins Cove** (inner harbor extension)
- **North River** (wider sections)
- **Forest River** (wider sections)
- **Salem Sound** extension
- **Palmer Cove** (at Salem Willows)
- **Juniper Cove**
- Beaver Pond, Furlong Pond, and smaller lakes
**Tour benefit: CRITICAL** — the water is what makes Salem LOOK like Salem. The harbor outline is the single most recognizable geographic feature. Must render on every map zoom.

## LINEARWATER (Linear Water)

**Contents.** Water features as **line geometry**: streams, creeks, narrow rivers, drainage channels, ditches, aqueducts. Per-county. 3,233 files (2 counties missing, neither in MA).
**Columns.** `linearid`, `fullname`, `mtfcc` (H3010=stream/river, H3013=braided stream, H3020=canal/ditch, H3015=pipeline/aqueduct/flume), `geom` (LineString).
**Salem status.** Will contain:
- **North River** (upstream portions, narrow) — feeds into Salem Harbor
- **Forest River** — south side of Salem
- Various unnamed streams
- Possibly some historic canals / raceways (if still mapped)
**Tour benefit: MEDIUM** — secondary water features for visual richness. Not iconic but fills out the map. Essential for realism at high zoom.

## COASTLINE

**Contents.** Mean high tide line for the U.S. coast. **National file (1 zip)** — 1 giant file, not per-county.
**Columns.** `linearid`, `region`, `mtfcc` (L4020 = coastline), `geom` (LineString, probably MultiLineString given the fractal nature of coast).
**Salem status.** Will contain the entire Essex County coastline: Salem Harbor entrance, Marblehead Neck, Cape Ann (Rockport, Gloucester), Plum Island. **Every waterfront edge of Salem.**
**Tour benefit: CRITICAL** — the boundary between "where you can walk" and "water." Defines Derby Wharf, the piers, Winter Island's shape, the Salem Willows promontory. Must render precisely on the basemap. Probably the second-most-visually-important layer after ROADS for a Salem-specific map.

---

# Part V — Landmarks (what's notable that's IN TIGER)

## POINTLM (Point Landmarks)

**Contents.** Named features represented as **single points**: schools, churches, hospitals, airports, government buildings, cemeteries (if small), shopping malls (if small), parks (if small). Per-state. 56 files.
**Columns.** `statefp`, `pointid`, `fullname` (e.g., "Salem State University"), `mtfcc` (K1231=hospital, K1234=police, K1235=fire, K1236=library, K1237=post office, K1238=city/town hall, K2110=military, K2165=government center, K2167=convention center, K2180=park (point representation), K2181=recreation area, K2195=cemetery (point), K2451=airport, K2540=school (college/university), K2541=school (private), K2542=school (K-12), K2543=sports complex, K2564=amusement park, K2565=theme park, K2582=monument (statue), K2586=religious inst (church/temple), etc.), `geom` (Point).
**Salem status.** Expected Salem POINTLM entries (will verify post-import):
- **Salem State University** (K2540)
- **Peabody Essex Museum** (maybe — museums are sometimes K2165, often omitted)
- **Salem Witch Museum** (unlikely — commercial museums usually NOT in TIGER)
- **Salem Hospital** (K1231)
- **Salem Post Office** (K1237)
- **Salem City Hall** (K1238)
- **Salem Public Library** (K1236)
- **Salem Police** (K1234) / Salem Fire (K1235)
- **Salem High School, Bowditch School, Nathaniel Bowditch School, etc.** (K2542)
- **Multiple churches** — First Church in Salem, Old North Church of Salem, etc. (K2586)
- **Old Burying Point Cemetery** — may or may not have a POINTLM entry (usually cemeteries are in AREALM with a point version here only for very small ones)
**Tour benefit: HIGH but INCOMPLETE** —
- What's here: official/administrative landmarks (schools, government, hospitals, churches) — these ARE useful
- What's NOT here: **most tour-relevant Salem landmarks.** Witch House, House of Seven Gables, Witch Dungeon Museum, Peabody Essex Museum, the Salem Witch Trials Memorial, the Bewitched statue, the Nathaniel Hawthorne statue — these are tourism POIs that were never in TIGER. **They must come from curated POI data** (the LMA `salem-content` pipeline).
- **Use POINTLM as a partial free baseline.** Layer curated POIs on top.

## AREALM (Area Landmarks)

**Contents.** Named features represented as **polygons**: parks, cemeteries, shopping malls, airports (runways), schools (campuses), hospitals (grounds), recreation areas. Per-state. 56 files (4 small layers got missed in the first download pass then completed; all files present now).
**Columns.** `statefp`, `arealmid`, `fullname`, `mtfcc` (same K-codes as POINTLM but for features large enough to have a polygon), `geom` (MultiPolygon).
**Salem status.** Expected Salem AREALM entries:
- **Salem Common** (K2180 — the central park, witch-trial-era gathering place)
- **Old Burying Point Cemetery** (K2195 — 1637, oldest burial ground in Salem, adjacent to Witch Trials Memorial)
- **Howard Street Cemetery** (K2195)
- **Broad Street Cemetery**
- **Greenlawn Cemetery** (larger, north side)
- **Salem Willows Park** (K2180 — waterfront amusement park, carousel, arcades — tour-relevant)
- **Winter Island Park** (K2180 — Fort Pickering historic site, now recreation)
- **Forest River Park** (K2180)
- **Salem Maritime National Historic Site** (MAYBE — federal properties sometimes appear as K2110 or as AREALM K2181)
- **Salem State University campus** (K2540)
- **Salem Hospital grounds** (K1231)
- **Various school campuses**
**Tour benefit: HIGH** — the polygon landmarks give us:
- Actual park outlines (Salem Common boundary) — visually essential
- Cemetery outlines (THE photographic-draw for witch-theme tourism) — essential
- Waterfront park outlines (Willows, Winter Island) — essential for shore-walking routes
- **More tour-relevant than POINTLM** because parks/cemeteries/wharves are where tourists actually GO.

---

# Part VI — Topological substrate (behind-the-scenes)

## FACES

**Contents.** The atomic **polygon faces** of the TIGER topology — every piece of ground that's bounded on all sides by EDGES. Per-county. 3,234 files.
**Columns.** `tfid` (topology face ID — the key), `statefp`, `countyfp`, `tractce`, `blkgrpce`, `blockce` (2020 block), plus all the geographic hierarchy codes (`placefp`, `cousubfp`, `csafp`, `cbsafp`, `countyfpfips`, `zcta5ce20`, etc.), `geom` (Polygon).
**Salem status.** Essex County FACES will contain ~500,000+ face polygons. Every block, park, parking lot, building footprint boundary, etc., as a face.
**Tour benefit: MEDIUM (structural)** —
- Not user-facing.
- FACES is the **building block**: every polygon layer (PLACE, COUSUB, BG, TRACT, TABBLOCK20, etc.) is technically an aggregation of FACES.
- Useful for pgRouting topology build (nodes and faces are the two topological primitives).
- Queryable to get "which face does this point lie in?" → resolves to block, BG, tract, place, cousub all in one.
- **FACES Cowlitz County WA (53015) is in our Phase 1 gap.** That's a WA issue — Essex MA FACES file is present and complete.

## FACESAH, FACESAL, FACESMIL (Faces — American Hawaiian / Alaska / Military)

**Contents.** Auxiliary FACES-style files for non-standard jurisdictional overlays: American Indian / Alaska Native / Native Hawaiian lands (AH), Alaska-specific (AL), and military installation overlays (MIL).
**Salem status.** **EMPTY** or close to it — no AIANNH lands, no Alaska, no military installations in Essex County MA.
- Files exist (were downloaded — FACESAH has 3,235 files nationally, FACESAL has 56 per-state, FACESMIL has 1 national) but the Essex County entries will have zero or near-zero features.
**Tour benefit: NONE** for Salem.

---

# Part VII — Layers not applicable to Salem (brief)

These layers are in the pipeline for national/portfolio completeness, but will have **zero or effectively zero** features for Essex County MA. Skip at tour-build time.

| Layer | Why N/A for Salem |
|---|---|
| AIANNH | No American Indian / Alaska Native / Native Hawaiian lands in Essex County. |
| AITSN | No tribal subdivisions here. |
| ANRC | Alaska Native Regional Corporations — Alaska only. |
| CONCITY | Consolidated Cities (like Jacksonville FL) — Salem is not one. |
| ESTATE | Puerto Rico "estates" — PR only. |
| FACESAH | AH/Hawaiian face overlay — no relevant features in Essex County. |
| FACESAL | Alaska face overlay — irrelevant to MA. |
| FACESMIL | Military face overlay — no military installations in Essex County. |
| INTERNATIONALBOUNDARY | U.S. land borders — Canada/Mexico, no relevance to MA. |
| MIL | Military installations — no active base in Essex County (Hanscom AFB is Middlesex, not Essex). |
| SUBBARRIO | Puerto Rico "sub-barrios" — PR only. |
| TBG | Tribal Block Groups — N/A. |
| TTRACT | Tribal Tracts — N/A. |

All 13 layers above import successfully (for national coverage) but a Salem-only query against them returns empty. That's fine — it's cheap to include them and means the `tiger` schema is a complete national mirror.

---

# Part VIII — Derived products we can build for Salem

These are NOT raw TIGER layers — they're what we build from combinations during Phases 3–10 of MASTER_PLAN.md.

## 1. Salem pedestrian routing graph (Phase 3–5)

**Built from:** EDGES + FACES + pgRouting topology build, filtered to `mtfcc IN ('S1400','S1500','S1710','S1720','S1730','S1740','S1750','S1780')` (local roads, vehicular trails, walkways, stairways, alleys).
**Augmented by (Phase 6):** OpenStreetMap pedestrian-specific features — footways, park paths, steps, pedestrian malls, historic walking routes — overlaid onto the TIGER skeleton. This is load-bearing for Salem because Salem's waterfront has many footpaths (Derby Wharf walkway, Pickering Wharf boardwalk, Salem Willows promenade) that TIGER's road-centric graph does not cover.
**Output:** `tiger.routing_topology` (pgRouting topology) + a Salem-filtered export as a **binary routing graph file** consumed by LMA's pure-Kotlin router (see MASTER_PLAN §Phase 5).
**Tour benefit: CRITICAL** — the core deliverable. Drives every "walk from Stop A to Stop B" query.

## 2. Salem geocoder

**Built from:** ADDRFEAT + ADDR + ADDRFN + FEATNAMES + the installed `postgis_tiger_geocoder` extension.
**Capabilities:**
- Address → point: "174 Derby St, Salem, MA 01970" → lon/lat
- Point → address: lon/lat → nearest street segment + estimated address
- Fuzzy / alternate name matching: "Route 114" → Lafayette St
**Output:** Callable via `SELECT * FROM geocode('174 Derby St, Salem MA')` or via an exported per-city SpatiaLite package (Phase 9).
**Tour benefit: HIGH** — visitors typing addresses into the app, photo EXIF reverse-geocoding (for GeoInbox integration), "nearest tour stop" calculations.

## 3. Salem basemap tile set (Phase 9–10)

**Built from:** COUNTY (outline) + COUSUB (Salem boundary) + ROADS + AREAWATER + LINEARWATER + COASTLINE + AREALM (parks/cemeteries) + POINTLM (schools, government, churches) + RAILS.
**Output:** MBTiles vector tile set, rendered with a custom MapLibre style (Phase 10 — "Cartoon Cartography"). Likely a witch/mystic aesthetic for Katrina's Visitors Guide.
**Tour benefit: CRITICAL** — the map tourists see. Every other feature is rendered on top of this.

## 4. Salem neighborhood overlay

**Built from:** TRACT + BG + a curated tract-to-neighborhood-name lookup table (manual, one-time).
**Output:** Named neighborhood polygons (e.g., "The Point," "McIntire Historic District," "South Salem," "North Salem," "Willows," "Downtown," "Waterfront").
**Tour benefit: MEDIUM** — useful for neighborhood-based tour filtering ("show me all stops in the McIntire Historic District"). Needs curation effort.

## 5. Reverse-geocoding for photos

**Built from:** ADDRFEAT + FACES + the geocoder functions.
**Output:** Given a lat/lon (from photo EXIF), return the nearest street, nearest landmark, and containing census geography.
**Tour benefit: HIGH for GeoInbox integration** (not directly for LMA), but infrastructure we share. Also supports "take a photo, see it placed on the tour map" features.

---

# Part IX — What TIGER does NOT give us (gaps)

These are features the Salem tour needs but TIGER cannot provide. Each must come from another source.

## 1. Historical / witch-trial-era POIs (must be curated)

TIGER has zero coverage of:
- **The Witch House** (Jonathan Corwin House, 310½ Essex St) — the only structure still standing with direct ties to the 1692 trials
- **House of the Seven Gables** (115 Derby St) — Hawthorne-era mansion
- **Witch Dungeon Museum** (16 Lynde St)
- **Salem Witch Museum** (19½ Washington Sq N)
- **The Witch House at Salem** (same as Jonathan Corwin House — but sometimes under different names)
- **Peabody Essex Museum** (161 Essex St) — the premier regional art/culture museum
- **Salem Witch Trials Memorial** (24 Liberty St) — adjacent to Old Burying Point
- **Proctor's Ledge** (off Pope St) — 2016-confirmed 1692 execution site
- **Hawthorne's Birthplace** (27 Hardy St)
- **The Friendship of Salem** (replica tall ship at Central Wharf)
- **Custom House** (Salem Maritime NHS — Hawthorne worked here)
- **Gallows Hill** (historical site, now a neighborhood)
- **Bewitched statue** (Lappin Park, Essex/Washington St intersection)
- **Nathaniel Hawthorne statue** (in front of Salem Town Hall)
- **Roger Conant statue** (opposite Salem Witch Museum)
- **Statue of Elizabeth Montgomery** (Bewitched TV star)
- Tens of smaller historic markers, plaques, storefront attractions, shops

**Source:** Curated POI database owned by LMA's `salem-content` pipeline. Each POI carries: name, canonical location (lon/lat), brief description, photo reference, opening hours, admission fee, tour-order priority, themed tags (witch-trials / maritime / Hawthorne / literary / Halloween / etc.).
**Who owns this:** LMA. TigerLine provides the *ground truth* (the basemap); LMA provides the *POIs*.

## 2. Building footprints

TIGER does NOT include building polygons. Salem's downtown with its dense Federal-era architecture cannot be rendered from TIGER alone — every building would just show as empty space between roads.
**Sources, in preference order:**
- **OpenStreetMap** (ODbL license — attribution required) — has extensive Salem building coverage
- **MassGIS** (state-level open data) — has authoritative MA building footprints
- **Microsoft Building Footprints** (open-license, U.S. only)
**Recommendation:** OSM or MassGIS. Add as a Phase-10-adjacent optional layer for higher-zoom realism.

## 3. Sidewalks and pedestrian-only paths

TIGER's EDGES include **some** walkways (mtfcc S1710/S1720) but coverage is spotty — the pedestrian-path graph is thin. Salem's actual walking infrastructure:
- Derby Wharf walking pier (1.1-mile roundtrip) — NOT in TIGER
- Pickering Wharf boardwalk — NOT in TIGER
- Salem Willows promenade — NOT in TIGER
- McIntire Historic District walking tour paths — NOT in TIGER
- Salem Common internal paths — NOT in TIGER
- Salem Maritime NHS walking trails — partial

**Source:** OpenStreetMap pedestrian features (Phase 6 explicitly covers this: footways, park paths, steps, pedestrian mall). **Critical for a WALKING tour.**

## 4. Transit stops and schedules

TIGER's RAILS gives us the *tracks* but not the *stations* as POINTLM entries reliably. No bus routes at all.
**Sources:**
- **MBTA GTFS feed** (open data, public) — commuter rail stations (Salem Depot), bus routes (MBTA 450, 455, 459, 465, 468 all serve Salem), schedules
- **Salem Skipper** (on-demand shuttle service) — Salem-operated
- **The Trolley Depot** (commercial tourist trolley) — private, may need licensing
**Integration point:** can be loaded into PostGIS as extra tables (`gtfs.stops`, `gtfs.routes`) alongside the `tiger` schema. Salem tour can show "nearest bus stop: 100m west" using cross-schema queries.

## 5. Parking lots

AREALM *may* contain some public parking garages (as K2194 or similar) but coverage is spotty. Tourists need to know:
- Downtown municipal parking garages (MBTA parking at Salem Depot, Museum Place garage, Washington St South garage, etc.)
- Street-parking regulations
- Parking availability (live — outside TIGER's scope entirely)
**Source:** City of Salem open data (if published) + OpenStreetMap parking tags. Needs curation.

## 6. Live information

- Hours of operation
- Admission prices
- Current events / festivals (Salem Haunted Happenings, Salem 400+ events October 2026)
- Weather
- Real-time transit
- Wait times
**Source:** All externally curated; LMA's app integrates these at runtime via their own backends.

## 7. Elevation / terrain

TIGER is strictly 2D. Salem's terrain is mostly flat but some areas have noticeable grade (Chestnut St hill, Gallows Hill, Winter Island approach). Walking-time calculations that account for hills need elevation data.
**Sources:**
- **USGS 3DEP** (free, open) — national DEM at 1–10m resolution
- **MassGIS elevation** — state-level
**Integration:** Optional; only needed for precision walking-time estimates. TIGER 4.5 km/h flat-walk default is usually fine.

## 8. Historical map overlays

1692 / 1700s / 1800s Salem maps exist (Peabody Essex Museum archives, Salem Historical Society, Library of Congress). Overlaying these on a modern basemap is a UNIQUE differentiator for Salem tourism (the Witch Trials happened on a specific street grid that partially survives).
**Sources:** Public-domain historical maps, georeferenced. Out-of-scope for TigerLine; potential LMA Phase 10+ differentiator.

---

# Part X — Practical Salem tour map checklist

When building the Salem-specific MBTiles tile set (Phase 9 deliverable), the following layers and filters should be loaded:

## Layers TO include

**Base canvas:**
- [x] COASTLINE — clipped to Essex County bbox (critical — Salem's shape)
- [x] AREAWATER — filtered to Essex County (harbor, coves, ponds)
- [x] LINEARWATER — filtered to Essex County (streams, narrow rivers)

**Boundaries:**
- [x] STATE — MA polygon (low zoom only)
- [x] COUNTY — Essex County (low–mid zoom)
- [x] COUSUB — Salem, Peabody, Marblehead, Beverly, Danvers (mid–high zoom)
- [x] PLACE — Salem, Peabody (labeling only)

**Streets:**
- [x] PRIMARYROADS — I-95, US-1 (zoom 6–10 only, regional context)
- [x] PRISECROADS — Route 114, 107, 1A, 128 (zoom 10–14)
- [x] ROADS — all Salem streets (zoom 12–18 for rendering)
- [x] EDGES — for routing only, not rendering (backed by pgRouting topology)

**Transit:**
- [x] RAILS — MBTA commuter rail line (zoom 10+)

**Landmarks (TIGER-provided, free baseline):**
- [x] AREALM — parks (Salem Common, Salem Willows, Winter Island), cemeteries (Old Burying Point, Howard St, Broad St, Greenlawn), hospitals, school campuses
- [x] POINTLM — schools, government buildings, churches, hospitals, post offices, libraries (as point labels at zoom 14+)

**Census (optional, mostly for analytics):**
- [ ] TRACT, BG — include only if neighborhood-label overlay is desired
- [ ] TABBLOCK20 — probably skip for tile set (too granular)
- [ ] ZCTA520 — include if ZIP-overlay toggle is planned

**School districts (SKIP):** UNSD, ELSD, SCSD, SDADM — no tourism relevance.
**Political (SKIP):** CD, SLDU, SLDL — no tourism relevance.

## Layers to SUPPLEMENT (not TIGER-sourced)

- [ ] **Curated Salem POIs** (Witch House, Peabody Essex Museum, H7G, Witch Trials Memorial, statues, all commercial attractions) — **REQUIRED**, owned by LMA's salem-content pipeline
- [ ] **Building footprints** — OSM or MassGIS, added as a zoom 16–18 detail layer
- [ ] **OSM pedestrian paths** (Phase 6) — footways, park paths, steps, Derby Wharf pier, etc. — **REQUIRED for walking tour**
- [ ] **MBTA bus stops and rail stations** — from GTFS feed, added as POIs
- [ ] **Historic map overlay** — public-domain 1692/1790/1850 Salem maps, georeferenced (optional differentiator)

---

## Sanity check once Phase 2 import finishes

When `tiger.*` tables are populated, verify Salem's share of the data with these queries:

```sql
-- Salem city boundary — should return 1 polygon
SELECT name, ST_Area(geom::geography)/1e6 AS sq_km
FROM tiger.cousub
WHERE statefp='25' AND name='Salem' AND countyfp='009';

-- Streets inside Salem
SELECT COUNT(*) AS salem_edges
FROM tiger.edges e, tiger.cousub c
WHERE c.statefp='25' AND c.name='Salem' AND c.countyfp='009'
  AND ST_Intersects(e.geom, c.geom);

-- Named parks/cemeteries in Salem
SELECT fullname, mtfcc
FROM tiger.arealm
WHERE statefp='25'
  AND ST_Intersects(geom, (SELECT geom FROM tiger.cousub WHERE statefp='25' AND name='Salem' AND countyfp='009'))
ORDER BY fullname;

-- Point landmarks (schools, churches, etc.) in Salem
SELECT fullname, mtfcc
FROM tiger.pointlm
WHERE statefp='25'
  AND ST_Intersects(geom, (SELECT geom FROM tiger.cousub WHERE statefp='25' AND name='Salem' AND countyfp='009'))
ORDER BY mtfcc, fullname;

-- Salem Harbor polygon
SELECT fullname, ST_Area(geom::geography)/1e6 AS sq_km
FROM tiger.areawater
WHERE statefp='25' AND fullname ILIKE '%salem%harbor%';

-- Rail line through Salem
SELECT fullname, mtfcc, ST_Length(geom::geography)/1000 AS km
FROM tiger.rails
WHERE ST_Intersects(geom, (SELECT geom FROM tiger.cousub WHERE statefp='25' AND name='Salem' AND countyfp='009'));
```

Run these after the import completes to ground-truth the Salem data and confirm the expected features are present.

---

_Reference document generated 2026-04-20 (Session 001) while Phase 2 PostGIS import runs. Update after import completion to replace "expected" with verified feature counts from the live `tiger.*` tables._
