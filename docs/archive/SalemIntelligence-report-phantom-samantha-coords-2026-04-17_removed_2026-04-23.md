# SalemIntelligence — BCS Geocoding Fallback Bug (Phantom Samantha Coords)

**Reporter:** LocationMapApp (Dean Maurice Ellis) — Session 144
**Date:** 2026-04-17
**Severity:** Data-quality / UX — blocks ambient-narration feature at cold start
**Affected dataset:** `bcs` ingest (1,724 POIs), visible in `/api/intel/poi-export`
**LMA commit context:** post-S143 `dad35b5`
**Database checked:** LMA bundled Room DB `app-salem/src/main/assets/salem_content.db` (1,837 rows, post-S140 Oracle tile import)

---

## TL;DR

Ten BCS POIs with disparate street addresses have been collapsed onto the coordinate `42.5213339, -70.8958456` — which is ~0.2 m from the Bewitched / Samantha statue (a famous Salem tourist landmark). The addresses are scattered across Canal, Derby, Essex, Buffum, Congress, Forrester, and Bridge streets, so they cannot legitimately share a coordinate. The most likely cause is a BCS-pipeline geocoding fallback that defaults unresolved POIs to the Samantha statue point instead of flagging them as `needs-review`.

LMA discovered this today while shipping a feature that snaps the user's location to Samantha when they are outside Salem's city bbox. The snap made all ten phantom POIs fire simultaneous geofence-entry events, which caused the opening ambient narration to play an AutoZone commercial spot first. That is a tangible paid-app UX hit for LMA, so we're raising this formally.

---

## Evidence

### 10 phantom-coord POIs (all `data_source = salem_intelligence_bcs`)

```sql
SELECT id, name, category, lat, lng, data_source
FROM salem_pois
WHERE ABS(lat - 42.5213339) < 0.0001
  AND ABS(lng - (-70.8958456)) < 0.0001
  AND id != 'bewitched_sculpture_samantha_statue'
ORDER BY name;
```

| id | name | category | real address |
|---|---|---|---|
| `autozone_auto_parts_salem_5075` | AutoZone Auto Parts Salem # 5075 | SHOPPING | 292 Canal St, Salem, MA 01970 |
| `habanero_cycles` | Habanero Cycles | SHOPPING | 18 Buffum Street, Salem, MA 01970 |
| `lifebridge_thrift_shop_2` | Lifebridge Thrift Shop | SHOPPING | 47 Canal St, Salem, MA 01970 |
| `notch_brewing_2` | Notch Brewing | FOOD_DRINK | 283R Derby St, Salem, MA 01970 |
| `salem_tipico_restaurant` | Salem Tipico Restaurant | FOOD_DRINK | 88 Congress St, Salem, MA 01970 |
| `salem_witch_village` | Salem Witch Village | WITCH_SHOP | 282 Derby Street, Salem, MA |
| `st_nicholas_orthodox_church` | St. Nicholas Orthodox Church | WORSHIP | 64 Forrester Street, Salem, MA 01970 |
| `starbird_dispensary_2` | Starbird Dispensary | SHOPPING | 2 Bridge Street, Salem, MA |
| `the_village` | The Village | FOOD_DRINK | 168 Essex Street, Salem, MA 01970 |
| `witch_side_tavern` | Witch Side Tavern | FOOD_DRINK | 283 Derby Street, Salem, MA 01970 |

All 10 rows share exactly `lat = 42.5213339`, `lng = -70.8958456`, `data_source = salem_intelligence_bcs`.

### The coordinate is the Samantha statue's

```sql
SELECT id, name, lat, lng, data_source
FROM salem_pois
WHERE id = 'bewitched_sculpture_samantha_statue';
-- bewitched_sculpture_samantha_statue | Bewitched Sculpture – Samantha Statue
-- | 42.5213319 | -70.8958518 | destination_salem+openstreetmap
```

Offset from the phantom coord: **0.000002° lat (~0.2 m north), 0.0000062° lng (~0.5 m west)**. Effectively the same point. The statue's coordinate came from a different source (`destination_salem+openstreetmap`) and has correct data. The 10 BCS rows appear to have been anchored near — but deliberately offset from — that landmark point.

### This is not a shared-building collision

Other shared-coordinate clusters in the dataset ARE legitimate (84 Highland Ave = medical office building with 12 tenants, 30 Church St = mixed-use with 11 tenants). The distinguishing factor: those clusters share ONE common street address. The Samantha cluster has addresses spanning at least **seven distinct streets** across downtown Salem — which cannot be a real co-location.

---

## Hypothesis on root cause

BCS enrichment pipeline appears to have a fallback branch something like:

```python
# pseudocode
coords = geocode(address) or geocode(place_name) or SALEM_DEFAULT_COORDS
```

…where `SALEM_DEFAULT_COORDS` equals (or is very close to) the Samantha statue coordinate, perhaps chosen historically as a "downtown Salem pivot" point. Whenever the upstream geocoder returned null for a POI, the pipeline silently assigned this fallback coord instead of (a) skipping the POI, (b) marking it `needs_review`, or (c) raising an error.

Supporting observations:
- All 10 affected rows have non-null `address` values containing real, different street addresses, so the upstream geocode failed at some other stage — it's not a missing-address problem, it's a geocoder-miss problem.
- The 0.2 m offset from Samantha's actual coord suggests the fallback point was hand-picked (a round-number pivot) rather than copied verbatim from Samantha's row.
- Several affected rows have `_2` suffixes (`lifebridge_thrift_shop_2`, `notch_brewing_2`, `starbird_dispensary_2`) suggesting these are duplicate / re-import rows where a reingest re-hit the same failure path. AutoZone even has two IDs: `autozone_auto_parts_salem_5075` (phantom coord, primary) and `autozone_auto_parts_salem_5075_2` (legitimate coord at 42.5014616, -70.8963651, the real south-Salem store).

---

## Impact on LocationMapApp

1. **Ambient-narration UX at cold start is broken** when the user is outside the Salem bbox. LMA's S144 shipped a clamp that defaults out-of-bbox users to the Samantha statue so the map/narration stays grounded in downtown Salem. Because all 10 phantom POIs sit at exactly the clamp point, they all fire simultaneous geofence-entry events, and the dequeue picked AutoZone (`SHOPPING`, tier=REST) as the opening narration. This is a user-hostile first impression.
2. **Narration queue's super-HD density mode trips unnecessarily.** The logs show `SUPER-HD MODE: 5 POIs in 40m, 5 in 30m` because the cluster density exceeds the 3-POI threshold. This forces the discard radius down to 20 m and prunes legit downtown POIs from the queue.
3. **Proximity-dock pollution.** The dock renders the survivors list; these 10 phantom POIs will always appear at the top for any user near the statue in real life.
4. **Map-marker pollution.** Because we restored `SHOPPING`, `FOOD_DRINK`, `WITCH_SHOP`, and `WORSHIP` marker icons in S143, all 10 phantom POIs render as overlapping markers on the Samantha statue spot. Visually the statue marker is buried under a pile of icons from businesses that are not there.

LMA mitigations shipped in S144 (stopgap, does not fix the data):
- MainViewModel now clamps GPS to the Samantha statue when outside the Salem bbox (intended).
- Narration queue explore-mode now uses tier-first ordering while the user is within 40 m of the initial narration anchor, so the opening sequence prefers HISTORIC / CIVIC over REST even when the cluster is dense.

Neither mitigation fixes the underlying data bug. LMA will either need to soft-delete these 10 rows pre-Play-Store bundling, or SI fixes the pipeline and LMA reimports from `/api/intel/poi-export`.

---

## Asks

1. **Confirm the root cause in the BCS enrichment pipeline.** Expected: identify the geocoding-fallback code path, audit how many POIs in SI's full corpus are affected (we see 10 in the LMA slice, there may be more not yet imported).
2. **Fix the fallback.** Options in order of preference:
   - Drop POIs with unresolvable coords entirely, logging a `needs_review` record.
   - Keep the POI but set `lat = NULL, lng = NULL` so downstream consumers can detect and skip it.
   - At minimum, set a sentinel coord obviously outside Salem (e.g. `0.0, 0.0`) so it cannot collide with real landmarks.
3. **Patch the existing 10 rows.** These POIs all have valid addresses — they should be re-geocoded. For `autozone_auto_parts_salem_5075` specifically, merge it into `autozone_auto_parts_salem_5075_2` (the version with correct south-Salem coords) and drop the phantom.
4. **Flag `st_nicholas_orthodox_church`, `salem_witch_village`, `salem_tipico_restaurant`, `notch_brewing_2`, etc.** — these are real, well-known Salem establishments. Verify their coords against OSM / Google and update.
5. **Add a pipeline invariant / test.** No BCS POI's coordinate should be within 5 m of another POI with a different street address. A post-ingest check would have caught this before publication.

---

## Reference

- LMA live session log: `~/Development/LocationMapApp_v1.5/docs/session-logs/session-144-2026-04-17.md`
- LMA bundled DB: `~/Development/LocationMapApp_v1.5/app-salem/src/main/assets/salem_content.db`
- SI export endpoint consulted: `http://:8089/api/intel/poi-export` (per `~/.claude/projects/-home-witchdoctor-Development-LocationMapApp-v1-5/memory/reference_salem_intelligence_api.md`)
- Device log excerpt (Lenovo HNY0CY0W, S144 2026-04-17 21:25):
  ```
  21:25:45.242 ENTRY: AutoZone Auto Parts Salem # 5075 (0m)
  21:25:48.174 NARR-QUEUE: Picked AutoZone Auto Parts Salem # 5075 tier=REST dist=0m
                 (explore-mode closest-first, 3 total survivors)
  21:25:48.201 NARR-PLAY:   text=AutoZone Auto Parts Salem #5075 is a one-stop shop for top-q...
  ```

Please reply in the OMEN notes file `~/Development/OMEN/notes/locationmapapp.md` when this is triaged, or open a direct ticket under SalemIntelligence's project if that's the preferred channel.
