# GCP Picking Guide — QGIS Georeferencer

How to pick Ground Control Points (GCPs) on a cleaned historical map and export
them for the gdal_translate / gdalwarp bake pipeline.

Written for **1692 Upham** (S288) but the workflow is identical for 1700
Phillips, 1911 Walker, etc. — only the anchor feature list changes per map.

---

## Prereqs

- QGIS 3.34 (verified installed: `qgis --version`).
- Georeferencer plugin enabled: **Plugins → Manage and Install Plugins →
  Installed → ✓ Georeferencer GDAL** (ships built-in; just toggle on if not
  already enabled).
- Internet on the workstation (for the OSM basemap reference layer).
- Cleaned source PNG: `tools/historical-maps/1692/upham_1692_clean.png`
  (output of operator GIMP pass, Task #2).

---

## Step 1 — Open a fresh QGIS project + add OSM basemap

1. Launch QGIS. **Project → New**.
2. In the **Browser** panel (left side), expand **XYZ Tiles → OpenStreetMap**.
   Drag *OpenStreetMap* into the map view. (If you don't see it, right-click
   *XYZ Tiles → New Connection*, name "OSM", URL
   `https://tile.openstreetmap.org/{z}/{x}/{y}.png`, save.)
3. Zoom/pan to Salem + Danvers area. You should see the modern street grid.
4. **Set project CRS → EPSG:3857** (Web Mercator). Bottom-right corner of QGIS
   shows current CRS; click it → choose EPSG:3857. The bake pipeline outputs in
   EPSG:3857 too, keeps everything consistent.

---

## Step 2 — Open the Georeferencer

1. **Raster menu → Georeferencer…** (opens a separate window).
2. **Open Raster** button (top-left of Georeferencer toolbar) → select
   `tools/historical-maps/1692/upham_1692_clean.png`.
3. If prompted for CRS: just accept whatever — we're embedding GCPs later,
   the image starts in pixel space.

The cleaned 1692 image now fills the Georeferencer canvas.

---

## Step 3 — Configure transformation settings

1. Georeferencer window → **Settings → Transformation Settings…**
2. Set:
   - **Transformation type:** `Thin Plate Spline` (TPS — handles non-uniform
     distortion of historical reconstructions like Upham 1866).
   - **Resampling method:** `Bilinear`.
   - **Target SRS:** `EPSG:4326 - WGS 84` (NOT 3857 — the bake script's
     `gdal_translate -a_srs EPSG:4326` expects lon/lat coords).
   - **Output raster:** leave blank or auto (we won't use QGIS's warp output;
     just need the .points file).
   - **Save GCP points:** ✓ (this auto-saves a `.points` sidecar file).
   - **Load in QGIS when done:** unchecked.
3. OK.

---

## Step 4 — Pick GCPs

For each anchor feature in the list below:

1. Click the **Add Point** tool (Georeferencer toolbar, plus-with-pin icon).
2. **Click the feature on the Upham 1692 image** — this is the "from" point in
   pixel space. A small dialog pops up.
3. Click **From map canvas** in the dialog. Focus shifts to the main QGIS
   window (OSM basemap).
4. **Click the same feature on the modern OSM basemap** — this sets the "to"
   point in WGS84 lon/lat.
5. Back in the Georeferencer, the GCP row appears. Repeat for the next feature.

Target: **10–14 GCPs**, distributed across the bbox. As you add GCPs, the
Georeferencer shows a **residual error** per point (px) — anything > 30px is a
suspect and worth re-clicking.

---

## Step 5 — Anchor feature list (Salem Village survivors)

Pick from this list. Quality over quantity — better 10 confident anchors than
14 sloppy ones. Distribution matters more than count: a couple in the center +
a couple per edge + an outer corner or two.

### Core anchors (high confidence — buildings/sites still standing)

| # | On Upham 1692 (labeled) | Modern reference (search in QGIS OSM) | District |
|---|---|---|---|
| 1 | "Parris" or "Parsonage" — center of Salem Village | **Witch Memorial Park / Salem Village Parsonage Archaeological Site**, 67 Centre St, Danvers MA | Danvers Center |
| 2 | "Meeting House" — close to Parsonage | Salem Village Meeting House site marker at the **Hobart St / Centre St / Forest St** triangle, Danvers Center | Danvers Center |
| 3 | "R. Nurse" or labeled Rebecca Nurse property | **Rebecca Nurse Homestead, 149 Pine St, Danvers MA** (still standing) | South Danvers |
| 4 | "Endicott" / Orchard Farm | **Endicott Pear Tree site, 99 Endicott St, Danvers MA** (oldest cultivated fruit tree in N. America, still living) | NE Danvers |
| 5 | "Wadsworth" or burying ground | **Wadsworth Cemetery, Summer St, Danvers MA** (pre-1700 stones) | Danvers Center |
| 6 | "J. Proctor" / Proctor property | **John Proctor House Site, 348 Lowell St, Peabody MA** (marker / Goodale Farm) | West Peabody |

### Secondary anchors (lower confidence — useful for outer-bbox stretch)

| # | On Upham 1692 | Modern reference | District |
|---|---|---|---|
| 7 | "Putnam" cluster (multiple Putnam houses labeled) | Pick the most clearly-drawn one. Several Putnam family houses are documented around Hathorne Hill / North Danvers | North Danvers |
| 8 | Crane River — "Town Bridge" crossing | Where modern **Endicott St crosses the Crane River**, Danvers | E. Danvers |
| 9 | Frost Fish Brook / "Hadlock's Bridge" | Where modern **Hadlock Rd / Locust St** crosses Frost Fish Brook | E. Danvers |
| 10 | Bass River bend on "Beverly Side" | A clear named bend on the Bass River, Beverly side of map | Beverly |
| 11 | Northfields outer edge | Pick any clearly-labeled feature in the "Northfields" district | N. Danvers/Topsfield border |
| 12 | "The Cape" (Beverly Side bottom) | A named landmark on the Beverly peninsula edge of the map | Beverly |

### Operator's call

You live in the William Woodbury house in Beverly and have personal knowledge
of Old Planters / Salem Village geography. **Override or supplement this list
with anchors you know better than I do.** River-bend anchors (8–10) are
particularly suspect because channels have shifted in 300+ years — soft
anchors only.

---

## Step 6 — Save the .points file

When the Georeferencer detected GCP count looks right (10+) and residuals are
acceptable:

1. **File → Save GCP Points As…** in Georeferencer.
2. Save next to the cleaned image as:
   `tools/historical-maps/1692/upham_1692_clean.points`

QGIS writes a CSV-formatted .points file:
```
mapX,mapY,pixelX,pixelY,enable,dX,dY,residual
-70.9405,42.5689,1456,623,1,0.0,0.0,0.0
-70.9603,42.5499,1289,847,1,0.0,0.0,0.0
...
```

That's the only deliverable for Task #3. Hand off to me and I run Task #4
(bake) from there.

---

## Step 7 — (Optional) sanity-check residuals

Before saving, look at the **Residual (pixels)** column in the GCP Table panel.

- 0–10 px: great
- 10–30 px: acceptable
- 30–100 px: meaningful interior error — Upham's reconstruction may genuinely
  put this feature wrong, OR you mis-clicked. Re-click the GCP to verify.
- 100+ px: drop the GCP (uncheck the "Enable" box).

TPS warp fits the surface exactly through enabled GCPs, so high-residual
points dragging the surface won't actually appear as residuals in TPS mode
— they appear in Polynomial mode. If you want to **see** how bad an outlier
is, briefly switch transformation type to "Polynomial 1" in Settings, eyeball
residuals, then switch back to "Thin Plate Spline" for the actual export.

---

## Step 8 — Hand off

Ping me when `upham_1692_clean.points` exists. I'll:
1. Parse the .points file into a gdal_translate -gcp command (Task #4).
2. Run gdalwarp -tps + gdal2tiles + merge into salem_tiles.sqlite.
3. WebP-compress (Task #5).
4. Build debug AAB + bundletool install on Lenovo (Task #6).
5. You drive the on-device validation (Task #7).

---

## Gotchas

- **Don't move the OSM basemap CRS to EPSG:4326** while you're picking GCPs.
  Display CRS = 3857 (Web Mercator) for OSM tiles to render correctly; storage
  CRS for GCPs = 4326 (WGS84) as configured in Step 3. QGIS handles the
  transform.
- **Upham 1866 is a hand-drawn reconstruction.** Don't expect survey
  accuracy. ~20–50m residual error on interior features is the floor, even
  with 14 perfect GCPs. Goal is "street-aware", not "GPS-grade".
- **River channels move.** Crane, Bass, Ipswich, Frost Fish — all have been
  realigned for mills, dams, and road bridges over 300+ years. Don't anchor
  more than 2 GCPs on river features.
- **The "Royal Side" / "Beverly Side" labels on Upham** correspond to
  political subdivisions of 1692 Salem Village, not modern town lines.
  Beverly's modern center is south of where Upham draws "Beverly Side".

---

## Next maps (after 1692)

Same workflow, different anchor lists:
- **1700 Phillips** — downtown Salem reconstruction. Anchors: Salem Common
  corners, Charter St Cemetery, First Church (Essex St), Old Burying Point,
  Witch House, Pickering House, Custom House.
- **1911 Walker** — modern atlas plate. Anchors: dense downtown intersections
  (Essex/Washington, Derby/Lafayette, etc.); already mostly-surveyed so
  should hit < 10 px residuals on most GCPs.

These don't need a separate guide — same QGIS process. We'll do them after
1692 ships and validates.
