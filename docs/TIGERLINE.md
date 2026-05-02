# TigerLine validate-location workflow

> Operator reference for the per-POI **Validate via TigerLine** button (S218).
> Last updated 2026-05-01.

---

## What it does in one sentence

Click the fuchsia **Validate via TigerLine** button on a POI → server geocodes
the POI's stored address against U.S. Census TigerLine + filters to the Salem
area + snaps to the nearest street centerline + writes the result to
`lat_proposed` / `lng_proposed` → editor closes, map flies to the proposal at
zoom 20, draggable fuchsia "?" pin shows where Tiger thinks the POI should
be → drag to fine-tune → click **Accept** to commit (or **Cancel** to
discard).

---

## When to use it

- POIs whose stored coords look wildly off relative to their address (the
  whole point — surface stray POIs).
- The Lint tab's **Address ↔ geocode mismatch** check, sorted descending by
  drift. Click the fuchsia **Validate** button on each row to skip the
  editor and jump straight to map review.
- Spot-checking a recently edited POI you suspect is mis-placed.

## When NOT to use it

- POIs with `R` / `Rear` / `Suite` / `Unit` / `#` in the address. Tiger
  interpolates the front-of-block centerline; the actual entrance is
  somewhere else. Better to leave the curated coords alone OR Validate, look
  at the proposal for orientation only, then drag to the actual building.
- POIs near wharves, parks, the Salem Common, Pickering Wharf, the Maritime
  NHS, large complexes. Tiger has gaps in those areas — interpolation can
  land in parking lots or open water.
- POIs flagged `location_truth_of_record = true`. Those are pinned by you;
  the verifier respects that flag.

---

## What happens under the hood

The endpoint `POST /admin/salem/pois/:id/validate-location-tiger` runs four
ordered steps. Each step is logged to the cache-proxy stdout (default at
`/tmp/cache-proxy-*.log` while running under nohup).

### 1. `tiger.geocode()` — variant ladder

The address is sent to PostGIS's `tiger.geocode()` function with three
progressive variants:

1. As-is (after normalization that strips trailing "USA" / "United States").
2. Street-type abbreviations (`Street → St`, `Avenue → Ave`, etc.).
3. Without ZIP.

Each variant gets up to 3 candidates back, ordered by Tiger's rating:

| Rating | Meaning |
|--------|---------|
| 0 | Exact house-number match (rare and great) |
| 1–9 | Very close match — minor token differences |
| 10–30 | Fuzzy match, usually right town wrong block, or rough number |
| 30–90 | Loose match, possibly wrong town |
| ≥ 90 | Fell back to ZIP centroid or city centroid — useless |

The ladder stops as soon as a variant produces a rating < 90. The earlier
variant wins if it had any rating < 90.

### 2. Salem-area filter (S218 — backstop for wrong-town matches)

Tiger will fuzzy-match a same-named street in another town when the city tag
isn't authoritative. Example: "94 Wharf St, Salem MA" was matched at rating
18 to **94 N Wharf St, Weymouth Town, MA** (33.8 km away) because Salem's
Wharf St only has house numbers 1–72 in Tiger's `addr` table.

Every Tiger candidate's distance from Salem center (42.5223, -70.8950) is
checked. Anything > 15 km is dropped (covers Salem proper plus Beverly,
Peabody, Danvers, Marblehead, Lynn — anywhere a Salem-listed POI could
plausibly sit). Each drop is recorded in the `warnings` array so you see
exactly which town Tiger was guessing.

### 3. Snap to nearest street centerline

The best surviving candidate gets snapped to the nearest `tiger.edges` road
within **50 m**. This puts the proposed point literally on the road
(matches the operator rule: "order them right on street edges") instead of
wherever Tiger's house-number interpolation landed (usually 2–8 m off-center
toward the right curb).

If no road edge is within 50 m (rare — open lot, lakeside), the raw Tiger
coords are kept.

### 4. Street-name fallback (S218 — when full geocoding fails)

If steps 1–3 produce zero in-area candidates, the endpoint **doesn't give
up** — it pulls the street component out of the address (`94 Wharf St` →
`Wharf St`) and runs a KNN lookup on `tiger.edges` for the closest matching
edge **within 2 km of the POI's current coords**.

Result: Sea Level Oyster Bar (94 Wharf St — house number not in Tiger's
Salem range) gets a proposal **14 m from current**, snapped onto Salem's
actual Wharf St (TLID 86659365). The proposal is flagged
`location_source = 'tigerline_street_fallback'` and the warnings list names
all the dropped wrong-town candidates so you can see what Tiger tried.

### 5. Write `lat_proposed` / commit on Accept

On success, the proposal is written to:
- `lat_proposed` / `lng_proposed`
- `location_source` (`tigerline` | `tigerline_street_fallback`)
- `location_status` (`verified` if rating < 5 AND drift < 10 m, else
  `needs_review`)
- `location_drift_m` (from current to snapped point)
- `location_geocoder_rating` (the Tiger rating, NULL for the street fallback)
- `location_verified_at` = `NOW()`

The live `lat` / `lng` columns are **not touched** until you click **Accept**.

---

## Drift bands and what they mean

Both the review-panel readout and the AdminMap's dashed line render the
**effective drift** (current → wherever the pin is right now, drag-aware).

| Drift | Color | Likely cause |
|-------|-------|--------------|
| 0–30 m | slate | Normal — Tiger hit the right block; drag onto building if you want |
| 30–100 m | slate | Tiger interpolation was off by a block or two; drag onto building |
| 100 m–1 km | slate | Stored coords were genuinely misplaced (or the address has `R`/`Rear`/`Suite` and Tiger lands on front-of-block) |
| 1–5 km | **amber banner** | Unusual for a Salem POI — double-check the address before accepting |
| ≥ 5 km | **rose banner** | Tiger likely matched a same-named street in another town. Verify the address |
| > 15 km | (filtered out) | Backend dropped the candidate entirely; you'll see `no_match` with town names |

---

## Reading the verbose logs

Every validate emits a structured block to stdout. Tail the cache-proxy log:

```bash
tail -f /tmp/cache-proxy-s218*.log | grep validate-tiger
```

Each block looks like this (Sea Level Oyster Bar example):

```
[validate-tiger sea_level_oyster_bar] START
[validate-tiger sea_level_oyster_bar] POI name="Sea Level Oyster Bar" current=(42.52022, -70.88784) address="94 Wharf St, Salem, MA"
[validate-tiger sea_level_oyster_bar] [geocode] input raw="94 Wharf St, Salem, MA" base="..." variants=[...] limit=3
[validate-tiger sea_level_oyster_bar] [geocode] variant "94 Wharf St, Salem, MA" → 3 rows in 211ms
[validate-tiger sea_level_oyster_bar] [geocode]   r18 (42.21902, -70.92157) "94 N Wharf St, Weymouth Town, MA 02189"
[validate-tiger sea_level_oyster_bar] [geocode]   r20 (42.80767, -71.00101) "94 Wharf Ln, Haverhill, MA 01830"
[validate-tiger sea_level_oyster_bar] [geocode]   r20 (41.70857, -70.25694) "94 Wharf Ln, Yarmouth Port, MA 02675"
[validate-tiger sea_level_oyster_bar] Salem-area filter: 3 candidates, radius 15000m
[validate-tiger sea_level_oyster_bar]   r18 (42.21902, -70.92157) 33.8km from Salem ✗ DROPPED 94 N Wharf St, Weymouth Town, MA 02189
[validate-tiger sea_level_oyster_bar]   r20 (42.80767, -71.00101) 32.9km from Salem ✗ DROPPED 94 Wharf Ln, Haverhill, MA 01830
[validate-tiger sea_level_oyster_bar]   r20 (41.70857, -70.25694) 104.7km from Salem ✗ DROPPED 94 Wharf Ln, Yarmouth Port, MA 02675
[validate-tiger sea_level_oyster_bar] Salem-area filter result: 0 kept / 3 dropped
[validate-tiger sea_level_oyster_bar] street fallback: extracted street="Wharf St" from address="94 Wharf St, Salem, MA"
[validate-tiger sea_level_oyster_bar] [fallback] pattern="Wharf St" near (42.52022, -70.88784) radius=2000m → 5 KNN candidate(s)
[validate-tiger sea_level_oyster_bar] [fallback]   TLID 86659365 "Wharf St" closest pt (42.52015, -70.88795) → 12m ✓
[validate-tiger sea_level_oyster_bar] [fallback]   TLID 86647278 "Wharf St" closest pt (42.42364, -70.91235) → 10925m ✗ outside radius
[validate-tiger sea_level_oyster_bar] [fallback] BEST: TLID 86659365 "Wharf St" at 12m
[validate-tiger sea_level_oyster_bar] END status=proposed (street_fallback) drift=12m source=tigerline_street_fallback (313ms)
```

To find the worst-drift cases in your most recent test run:

```bash
grep -E "drift=[0-9]+m" /tmp/cache-proxy-s218*.log | sort -t= -k2 -n -r | head -20
```

To find every dropped wrong-town match across a session:

```bash
grep "✗ DROPPED" /tmp/cache-proxy-s218*.log
```

---

## Known limitations

### Tiger house-number interpolation is linear

Tiger stores street edges as line segments with a from/to house number range
(e.g. Derby St edge TLID 86643817 covers #238–#298). Tiger interpolates
linearly: house #282 = (282-238)/(298-238) = 73% along the edge. Real-world
houses aren't evenly spaced — so Tiger places #282 wherever 73% along the
edge geometry lands, which can be:

- A parking lot
- A gap between buildings
- The wrong block (when the edge crosses a real block break)
- An empty stretch of waterfront (Pickering Wharf, Maritime NHS)

Typical accuracy: ±30 m. Worst case in dense or atypical blocks: 100+ m.

### Suite / Rear / Unit / # addresses

Tiger only sees the front-of-block postal address. "282R Derby St" is
treated as "282 Derby St" — the rear-unit detail is lost. The interpolated
location is the front of the block, but the actual entrance is on a side
street or behind. Salem Witch Village is the canonical example.

### House-number range gaps

Tiger only knows the ranges that were loaded. If Salem's Wharf St addresses
in Tiger go #1–#72 but the POI is at #94, the fuzzy fallback will match a
Wharf St / Wharf Ln in another town. The Salem-area filter catches that
and routes to the street-name fallback.

### Statement timeout

Each validate call sets `statement_timeout = 5s`. Tiger queries usually run
in 50–600 ms; queries over 5 s are killed and logged as a variant failure.

---

## Validate via Google (Places API New)

S218 — adds a sky-blue **Validate via Google** button next to **Validate via
TigerLine** in:

- The POI editor card header
- Every POI row in the Lint tab

Backend: `POST /admin/salem/pois/:id/geocode-via-google`. Calls Google's
**Places API (New)** Text Search with `<poi.name> <poi.address>` as the
query. Field mask: `places.displayName,places.formattedAddress,places.location,places.id,places.types`.
Same Salem-area filter (15 km from Salem center) drops wrong-town matches.
Same proposal-review flow (`lat_proposed` write, fly-to-z20, draggable pin,
Accept/Cancel) — only difference is `location_source = 'google'`.

**Why Google over Tiger sometimes:**
- Storefront-level accuracy: Google resolves to the actual business
  location, not Tiger's house-number interpolation
- Knows about businesses by name: "Sea Level Oyster Bar 94 Wharf St" finds
  the exact restaurant even when Tiger has gaps in the address range
- Better with R/Rear/Suite/Unit addresses (the place's location is the
  storefront, not the front-of-block centerline)

**Smoke-test results:**
| POI | Tiger result | Google result |
|-----|-------------|---------------|
| Sea Level Oyster Bar | street fallback (12 m) | drift 14 m, place_id ChIJG0J0XWYU44kRYH0-R6Wu5Lc |
| Salem Witch Village | drift 113 m (interpolated, empty area) | drift 11 m ("Salem Witch Village and the Lost Library") |
| Colonial Hall | drift 12 m | drift 9 m ("Colonial Hall at Rockafellas" — note Google says address is 231 Essex, not 227) |

**Setup:**
- Requires `GOOGLE_GEOCODE_API_KEY` env var on cache-proxy
- Same key as SalemIntelligence's `GOOGLE_PLACES_API_KEY` (one shared GCP
  project)
- **Places API (New)** must be enabled in GCP. Legacy Places API and
  Geocoding API are NOT required and may be disabled.
- Free tier: ~10,000 calls/month for Text Search. Plenty for admin use.

**Logs** (`tail -f /tmp/cache-proxy-*.log | grep geocode-google`):

```
[geocode-google sea_level_oyster_bar] START
[geocode-google sea_level_oyster_bar] POI name="Sea Level Oyster Bar" current=(42.52022, -70.88784) address="94 Wharf St, Salem, MA"
[geocode-google sea_level_oyster_bar] Places API (New) Text Search query="Sea Level Oyster Bar 94 Wharf St, Salem, MA"
[geocode-google sea_level_oyster_bar] Google responded in 476ms places=1
[geocode-google sea_level_oyster_bar]   "Sea Level Oyster Bar" (42.52030, -70.88773) 0.6km from Salem ✓ "94 Wharf St, Salem, MA 01970, USA"
[geocode-google sea_level_oyster_bar] writing proposal: final=(42.52030, -70.88773) drift=14m status=verified source=google name="Sea Level Oyster Bar"
[geocode-google sea_level_oyster_bar] END status=proposed (491ms)
```

**Source label in the review panel:** the proposal panel shows a colored
pill — `Google` (sky) or `Tiger` (fuchsia) or `Tiger (street fallback)` —
so the operator knows which source's coordinates they're about to commit.

---

## MassGIS L3 — would it give better results?

**Yes**, significantly better for the cases Tiger struggles with.

MassGIS L3 (Massachusetts Level-3 Property Tax Parcels) is authoritative
cadastral data: actual parcel polygons keyed by `SITE_ADDR`. For "282 Derby
St" it would return the actual Salem Witch Village lot polygon, not Tiger's
house-number-interpolation guess. R-suffix, Suite, Rear, wharf-area
addresses all resolve correctly because parcels honor real property
boundaries.

**Current state in this repo:**

- Schema is ready in `salem_pois` (`mhc_id`, `parcel_owner_class`,
  `building_footprint_geojson`) — added in Phase 9Y.
- No MassGIS data is loaded into the `locationmapapp` Postgres for the
  validate workflow.
- SalemIntelligence has a MassGIS collector that pulls Salem's ~1,152
  parcels from the MA ArcGIS Hub
  (`services1.arcgis.com/hGdibHYSPO59RG1h`), but that project is paused
  under the S214 provenance hold.

**To wire MassGIS into validate (~3–5 hours):**

1. Load Salem's 1,152 parcels into `locationmapapp` directly from the
   ArcGIS Hub (no auth, paginated REST). Schema: `loc_id` / `site_addr` /
   `polygon (geometry 4326)`. GIST index on geometry, B-tree on
   `site_addr`.
2. New `findParcelBySiteAddr(client, normalizedAddress)` helper — matches
   the address, returns parcel centroid + polygon.
3. In `validate-location-tiger`, try MassGIS **before** Tiger. New status:
   `location_source = 'massgis_parcel'`. Fall through to Tiger only on
   parcel miss.
4. Update the review panel to surface `Source: MassGIS parcel` so you can
   trust those without dragging.

This would resolve the Salem Witch Village–style failures (postal address
correct but Tiger interpolation wrong building) without needing operator
ground-truth.

Not done yet — pending operator decision.

---

## Files

| Path | Purpose |
|------|---------|
| `cache-proxy/lib/tiger-geocode.js` | Shared helpers: `tigerPool`, `geocodeOneAddress`, `snapToNearestEdge`, `findEdgeByStreetName`, `extractStreetFromAddress`, `normalizeAddressForTiger` |
| `cache-proxy/lib/admin-pois.js` | `POST /admin/salem/pois/:id/validate-location-tiger`, `/accept-proposed-location` (with optional body override), `/discard-proposed-location` |
| `cache-proxy/lib/admin-lint.js` | `address_geocode_mismatch` deep-scan check (drives the sorted Lint list) |
| `web/src/admin/PoiEditDialog.tsx` | Top-of-card **Validate via TigerLine** button (header) |
| `web/src/admin/AdminLayout.tsx` | Proposal-review state machine, fly-to-z20, accept/cancel handlers |
| `web/src/admin/AdminMap.tsx` | `ProposalPreviewLayer` (draggable fuchsia "?" pin + dashed line), `FlyToProposal` |
| `web/src/admin/ProposalReviewPanel.tsx` | Floating bottom-right panel with current/proposed/drift/Accept/Cancel |
| `web/src/admin/LintTab.tsx` | Per-row **Validate** button on every POI lint item |

## Endpoints

| Endpoint | Method | Body | Purpose |
|----------|--------|------|---------|
| `/admin/salem/pois/:id/validate-location-tiger` | POST | (none) | Run Tiger geocode → write `lat_proposed` |
| `/admin/salem/pois/:id/accept-proposed-location` | POST | `{lat?, lng?}` | Commit `lat_proposed` → `lat`, clear proposal. Optional body overrides with the dragged pin position |
| `/admin/salem/pois/:id/discard-proposed-location` | POST | (none) | Clear proposal, reset `location_status='unverified'` |

## DB columns (S162 schema)

| Column | Type | Set by |
|--------|------|--------|
| `lat_proposed` | double | validate (Tiger or fallback), drag |
| `lng_proposed` | double | validate (Tiger or fallback), drag |
| `location_source` | text | `tigerline` / `tigerline_street_fallback` / `massgis_*` (when wired) / `manual` |
| `location_status` | text | `unverified` / `no_address` / `no_match` / `needs_review` / `verified` / `accepted` |
| `location_drift_m` | real | computed: haversine current → snapped point |
| `location_geocoder_rating` | int | Tiger rating (0=exact, 100=city centroid). NULL for street fallback |
| `location_verified_at` | timestamptz | `NOW()` on every validate or accept |
| `location_truth_of_record` | bool | Operator pin — verifier skips on future passes |
