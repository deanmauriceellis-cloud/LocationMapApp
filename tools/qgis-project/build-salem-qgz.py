#!/usr/bin/env python3
"""Build a ready-to-open QGIS project (.qgz) for Salem.

Exports Salem-clipped layers from the local `tiger` Postgres DB into a
self-contained GeoPackage, then assembles a styled QGIS project referencing
those layers. The resulting .qgz file needs no Postgres connection to open.

Output:
  tools/qgis-project/salem-gis.gpkg   (data, ~20-30 MB)
  tools/qgis-project/salem-gis.qgz    (QGIS project — double-click to open)
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
GPKG = HERE / "salem-gis.gpkg"
QGZ = HERE / "salem-gis.qgz"
ICONS_DIR = HERE / "icons"

PG = "PG:dbname=tiger"
PG_LMA = "PG:dbname=locationmapapp"
LMA_ICON_ROOT = Path(
    "/home/witchdoctor/Development/LocationMapApp_v1.5/"
    "app-salem/src/main/assets/poi-icons"
)

# Representative icon per salem_pois.category (PG-13-safe "cute" variants).
# Keys here must match distinct values of salem_pois.category.
CATEGORY_ICONS: dict[str, str] = {
    "SHOPPING":             "shopping/department_stores_cute.png",
    "FOOD_DRINK":           "food_drink/restaurants_cute.png",
    "OFFICES":              "offices/companies_cute.png",
    "ENTERTAINMENT":        "entertainment/event_venues_cute.png",
    "HEALTHCARE":           "healthcare/hospitals_cute.png",
    "CIVIC":                "civic/community_centres_cute.png",
    "HISTORICAL_BUILDINGS": "historic_house/colonial_houses_cute.png",
    "PARKS_REC":            "parks_rec/parks_cute.png",
    "AUTO_SERVICES":        "auto_services/car_washes_cute.png",
    "WITCH_SHOP":           "witch_shop/crystal_shops_cute.png",
    "EDUCATION":            "education/schools_cute.png",
    "WORSHIP":              "worship/places_of_worship_cute.png",
    "LODGING":              "lodging/hotels_cute.png",
    "TOUR_COMPANIES":       "tourism_history/aquariums_cute.png",
    "PSYCHIC":              "psychic/palm_readings_cute.png",
    "FINANCE":              "finance/banks_cute.png",
}

# -----------------------------------------------------------------------------
# Stage 1 — export layers to a single GeoPackage
# -----------------------------------------------------------------------------

SALEM_CLIP = (
    "SELECT {cols} FROM {tbl} {alias} "
    "WHERE ST_Intersects({alias}.{geomcol}, "
    "(SELECT geom FROM massgis.boundaries WHERE town='SALEM'))"
)

# Each entry:  (dest_layer_name, SQL, geom_col_in_result)
EXPORTS: list[tuple[str, str, str]] = [
    (
        "salem_boundary",
        "SELECT gid, town, shape_area, shape_len, geom "
        "FROM massgis.boundaries WHERE town='SALEM'",
        "geom",
    ),
    (
        "openspace_parks",
        "SELECT o.* FROM massgis.openspace o "
        "WHERE ST_Intersects(o.geom, "
        "(SELECT geom FROM massgis.boundaries WHERE town='SALEM'))",
        "geom",
    ),
    (
        "cemeteries",
        "SELECT gid, lu05_desc, lucode, area, len, geom "
        "FROM massgis.landuse2005 "
        "WHERE lu05_desc='Cemetery' AND ST_Intersects(geom, "
        "(SELECT geom FROM massgis.boundaries WHERE town='SALEM'))",
        "geom",
    ),
    (
        "civic_institutional",
        "SELECT gid, lu05_desc, lucode, area, len, geom "
        "FROM massgis.landuse2005 "
        "WHERE lu05_desc='Urban Public/Institutional' AND ST_Intersects(geom, "
        "(SELECT geom FROM massgis.boundaries WHERE town='SALEM'))",
        "geom",
    ),
    (
        "landuse_all",
        "SELECT gid, lu05_desc, lucode, area, len, geom "
        "FROM massgis.landuse2005 "
        "WHERE ST_Intersects(geom, "
        "(SELECT geom FROM massgis.boundaries WHERE town='SALEM'))",
        "geom",
    ),
    (
        "buildings",
        "SELECT s.gid, s.struct_id, s.source, s.area_sq_ft, s.town_id, "
        "s.shape_area, s.shape_len, s.geom "
        "FROM massgis.structures s "
        "WHERE ST_Intersects(s.geom, "
        "(SELECT geom FROM massgis.boundaries WHERE town='SALEM'))",
        "geom",
    ),
    (
        "parcels",
        "SELECT objectid, map_par_id, loc_id, poly_type, map_no, site, "
        "lu_codes, shape_area, shape_len, geom "
        "FROM massgis.l3_parcels_essex "
        "WHERE ST_Intersects(geom, "
        "(SELECT geom FROM massgis.boundaries WHERE town='SALEM'))",
        "geom",
    ),
    (
        "mhc_inventory",
        "SELECT gid, mhcn, demolished, type AS mhc_type, designatio, d_date, "
        "legend, historic_n, common_nam, address, town_name, constructi, "
        "architectu, maker, use_type, significan, geom "
        "FROM massgis.mhc_inventory "
        "WHERE town_name='Salem'",
        "geom",
    ),
    (
        "roads_massdot",
        "SELECT m.gid, m.streetname, m.rt_number, m.f_class, m.f_f_class, "
        "m.jurisdictn, m.num_lanes, m.speed_lim, m.surface_tp, "
        "m.lt_sidewlk, m.rt_sidewlk, m.shldr_lt_w, m.shldr_rt_w, "
        "m.toll_road, m.truck_rte, m.nhs, m.geom "
        "FROM massgis.massdot_roads m "
        "WHERE ST_Intersects(m.geom, "
        "(SELECT geom FROM massgis.boundaries WHERE town='SALEM'))",
        "geom",
    ),
    (
        "tiger_edges",
        "SELECT e.gid, e.tlid, e.mtfcc, e.fullname, e.roadflg, e.railflg, "
        "e.hydroflg, e.divroad, e.tnidf, e.tnidt, e.the_geom AS geom "
        "FROM tiger.edges e "
        "WHERE e.statefp='25' AND e.countyfp='009' "
        "AND ST_Intersects(e.the_geom, "
        "(SELECT geom FROM massgis.boundaries WHERE town='SALEM'))",
        "geom",
    ),
    (
        "tiger_cemeteries_named",
        "SELECT a.gid, a.fullname, a.mtfcc, a.geom "
        "FROM tiger.arealm a "
        "WHERE a.statefp='25' AND a.mtfcc='K2582' "
        "AND ST_Intersects(a.geom, "
        "(SELECT geom FROM massgis.boundaries WHERE town='SALEM'))",
        "geom",
    ),
]


def export_layers() -> None:
    if GPKG.exists():
        print(f"  removing existing {GPKG.name}")
        GPKG.unlink()

    for idx, (name, sql, _geom) in enumerate(EXPORTS):
        action = "fresh" if idx == 0 else "append"
        print(f"  [{idx+1}/{len(EXPORTS)}] exporting {name} ({action})")
        cmd = ["ogr2ogr"]
        if idx > 0:
            cmd += ["-append", "-update"]
        cmd += [
            "-f",
            "GPKG",
            str(GPKG),
            PG,
            "-nln",
            name,
            "-sql",
            sql,
            "-nlt",
            "PROMOTE_TO_MULTI",
            "-lco",
            "SPATIAL_INDEX=YES",
            "-overwrite",
        ]
        r = subprocess.run(cmd, capture_output=True, text=True)
        if r.returncode != 0:
            print(f"    ogr2ogr FAILED:\n{r.stderr}", file=sys.stderr)
            sys.exit(2)

    # LMA POIs — different DB, no PostGIS. Use CSV intermediary.
    print(f"  [{len(EXPORTS)+1}/{len(EXPORTS)+1}] exporting lma_pois (LMA DB via CSV)")
    with tempfile.NamedTemporaryFile(
        "w", suffix=".csv", delete=False, dir=str(HERE)
    ) as tmp:
        csv_path = Path(tmp.name)
    try:
        # \copy to a CSV the user can write
        copy_sql = (
            r"\copy (SELECT id, name, lat, lng, category, subcategory, "
            r"short_description, short_narration, merchant_tier, priority, "
            r"is_narrated, status "
            r"FROM salem_pois WHERE deleted_at IS NULL "
            r"AND lat IS NOT NULL AND lng IS NOT NULL) "
            r"TO '" + str(csv_path) + r"' CSV HEADER"
        )
        r = subprocess.run(
            ["psql", "-d", "locationmapapp", "-c", copy_sql],
            capture_output=True, text=True,
        )
        if r.returncode != 0:
            print(f"    psql copy FAILED:\n{r.stderr}", file=sys.stderr)
            sys.exit(2)

        # Ingest CSV as point layer into the GPKG
        cmd = [
            "ogr2ogr", "-append", "-update",
            "-f", "GPKG", str(GPKG), str(csv_path),
            "-nln", "lma_pois",
            "-oo", "X_POSSIBLE_NAMES=lng",
            "-oo", "Y_POSSIBLE_NAMES=lat",
            "-oo", "AUTODETECT_TYPE=YES",
            "-oo", "KEEP_GEOM_COLUMNS=NO",
            "-a_srs", "EPSG:4326",
            "-lco", "SPATIAL_INDEX=YES",
            "-lco", "GEOMETRY_NAME=geom",
            "-overwrite",
        ]
        r = subprocess.run(cmd, capture_output=True, text=True)
        if r.returncode != 0:
            print(f"    ogr2ogr (LMA POIs) FAILED:\n{r.stderr}", file=sys.stderr)
            sys.exit(2)
    finally:
        csv_path.unlink(missing_ok=True)

    print(f"  GeoPackage complete: {GPKG.stat().st_size/1024/1024:.1f} MB")


def copy_icons() -> None:
    """Copy representative category icons into the project bundle."""
    if ICONS_DIR.exists():
        shutil.rmtree(ICONS_DIR)
    ICONS_DIR.mkdir(parents=True)
    for category, rel in CATEGORY_ICONS.items():
        src = LMA_ICON_ROOT / rel
        dst = ICONS_DIR / f"{category.lower()}.png"
        if src.exists():
            shutil.copy2(src, dst)
        else:
            print(f"  WARN: missing icon {src} — POIs in {category} will use fallback")
    total_kb = sum(p.stat().st_size for p in ICONS_DIR.iterdir()) / 1024
    print(f"  icons copied: {len(list(ICONS_DIR.iterdir()))} files / {total_kb:.0f} KB")


# -----------------------------------------------------------------------------
# Stage 2 — build styled QGIS project with PyQGIS
# -----------------------------------------------------------------------------


def build_project() -> None:
    from qgis.core import (  # noqa: E402
        QgsApplication,
        QgsCategorizedSymbolRenderer,
        QgsCoordinateReferenceSystem,
        QgsFillSymbol,
        QgsLineSymbol,
        QgsMarkerSymbol,
        QgsProject,
        QgsRasterLayer,
        QgsRectangle,
        QgsRendererCategory,
        QgsSingleSymbolRenderer,
        QgsSymbol,
        QgsVectorLayer,
    )

    QgsApplication.setPrefixPath("/usr", True)
    qgs = QgsApplication([], False)
    qgs.initQgis()

    project = QgsProject.instance()
    project.setCrs(QgsCoordinateReferenceSystem("EPSG:4326"))
    project.setTitle("Salem GIS — MassGIS + TigerLine overlays")

    def vl(layer_name: str, display: str) -> "QgsVectorLayer":
        uri = f"{GPKG}|layername={layer_name}"
        lyr = QgsVectorLayer(uri, display, "ogr")
        if not lyr.isValid():
            raise RuntimeError(f"layer {layer_name} failed to load from {GPKG}")
        return lyr

    def fill(color: str, outline: str, outline_w: float, opacity: float = 1.0):
        s = QgsFillSymbol.createSimple(
            {
                "color": color,
                "outline_color": outline,
                "outline_width": str(outline_w),
            }
        )
        s.setOpacity(opacity)
        return s

    def line(color: str, width: float):
        return QgsLineSymbol.createSimple({"color": color, "width": str(width)})

    def marker(color: str, size: float, shape: str = "circle"):
        return QgsMarkerSymbol.createSimple(
            {
                "name": shape,
                "color": color,
                "outline_color": "#333333",
                "outline_width": "0.3",
                "size": str(size),
            }
        )

    # Ordered bottom-to-top so the layer tree reads naturally in QGIS
    layers_to_add: list = []

    # --- 1. OSM basemap (XYZ tiles) ---
    osm_uri = (
        "type=xyz&zmin=0&zmax=19&"
        "url=https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    )
    osm = QgsRasterLayer(osm_uri, "OpenStreetMap", "wms")
    if osm.isValid():
        layers_to_add.append(osm)

    # --- 2. Salem boundary ---
    boundary = vl("salem_boundary", "Salem boundary")
    boundary.setRenderer(
        QgsSingleSymbolRenderer(fill("#00000000", "#333333", 0.8))
    )
    layers_to_add.append(boundary)

    # --- 3. Open Space (parks) ---
    openspace = vl("openspace_parks", "Open Space / Parks")
    openspace.setRenderer(
        QgsSingleSymbolRenderer(fill("#2e7d32", "#1b5e20", 0.3, opacity=0.35))
    )
    layers_to_add.append(openspace)

    # --- 4. Cemeteries ---
    cemeteries = vl("cemeteries", "Cemeteries (MassGIS landuse2005)")
    cemeteries.setRenderer(
        QgsSingleSymbolRenderer(fill("#808080", "#333333", 0.5, opacity=0.55))
    )
    layers_to_add.append(cemeteries)

    # --- 5. Civic / Institutional ---
    civic = vl("civic_institutional", "Civic / Institutional")
    civic.setRenderer(
        QgsSingleSymbolRenderer(fill("#3f51b5", "#283593", 0.4, opacity=0.30))
    )
    layers_to_add.append(civic)

    # --- 6. Landuse (all categories, muted, for context) ---
    landuse = vl("landuse_all", "Land Use (all categories)")
    landuse.setOpacity(0.35)
    lu_categories = [
        ("Forest", "#2e7d32"),
        ("Forested Wetland", "#1b5e20"),
        ("Non-Forested Wetland", "#80cbc4"),
        ("Water", "#4fc3f7"),
        ("Open Land", "#c5e1a5"),
        ("Low Density Residential", "#fff59d"),
        ("Very Low Density Residential", "#fff9c4"),
        ("Medium Density Residential", "#ffe082"),
        ("High Density Residential", "#ffcc80"),
        ("Multi-Family Residential", "#ff9800"),
        ("Commercial", "#e57373"),
        ("Industrial", "#b71c1c"),
        ("Urban Public/Institutional", "#3f51b5"),
        ("Cemetery", "#808080"),
        ("Participation Recreation", "#66bb6a"),
        ("Cropland", "#d4e157"),
        ("Pasture", "#dce775"),
        ("Transportation", "#bdbdbd"),
        ("Powerline/Utility", "#e0e0e0"),
        ("Mining", "#6d4c41"),
        ("Waste Disposal", "#424242"),
        ("Golf Course", "#aed581"),
        ("Saltwater Wetland", "#26a69a"),
        ("Saltwater Sandy Beach", "#fff176"),
        ("Brushland/Successional", "#9ccc65"),
    ]
    lu_cats = []
    for desc, color in lu_categories:
        sym = fill(color, color, 0.1)
        lu_cats.append(QgsRendererCategory(desc, sym, desc))
    # default/other
    lu_cats.append(QgsRendererCategory("", fill("#f5f5f5", "#e0e0e0", 0.1), "other"))
    landuse.setRenderer(QgsCategorizedSymbolRenderer("lu05_desc", lu_cats))
    layers_to_add.append(landuse)

    # --- 7. Buildings (structures) ---
    buildings = vl("buildings", "Building Footprints (MassGIS structures)")
    buildings.setRenderer(
        QgsSingleSymbolRenderer(fill("#6d4c41", "#3e2723", 0.2, opacity=0.8))
    )
    layers_to_add.append(buildings)

    # --- 8. Parcels ---
    parcels = vl("parcels", "L3 Parcels (Essex slice, Salem-clipped)")
    parcels.setRenderer(
        QgsSingleSymbolRenderer(fill("#00000000", "#9e9e9e", 0.2))
    )
    layers_to_add.append(parcels)

    # --- 9. Roads (MassDOT, colored by functional class) ---
    roads = vl("roads_massdot", "MassDOT Roads (by functional class)")
    fclass_colors = [
        ("1", "Interstate", "#d32f2f", 3.0),
        ("2", "Other Principal Arterial", "#f57c00", 2.2),
        ("3", "Minor Arterial", "#fbc02d", 1.6),
        ("4", "Major Collector", "#afb42b", 1.2),
        ("5", "Minor Collector", "#689f38", 1.0),
        ("6", "Local", "#616161", 0.7),
        ("7", "Not Classified", "#9e9e9e", 0.5),
    ]
    road_cats = [
        QgsRendererCategory(int(val), line(color, w), f"{val} — {label}")
        for (val, label, color, w) in fclass_colors
    ]
    road_cats.append(QgsRendererCategory("", line("#bdbdbd", 0.4), "other"))
    roads.setRenderer(QgsCategorizedSymbolRenderer("f_class", road_cats))
    layers_to_add.append(roads)

    # --- 10. TIGER edges (walkability reference) ---
    tiger_edges = vl("tiger_edges", "TIGER Edges (walkable filter)")
    mtfcc_walk = [
        ("S1100", "Interstate (not walkable)", "#b71c1c", 0.6),
        ("S1200", "Secondary", "#f57c00", 0.5),
        ("S1400", "Local", "#616161", 0.3),
        ("S1630", "Ramp", "#b71c1c", 0.3),
        ("S1640", "Service", "#9e9e9e", 0.3),
        ("S1710", "Walkway", "#00897b", 0.8),
        ("S1720", "Stairway", "#00796b", 0.8),
        ("S1730", "Alley", "#9e9e9e", 0.3),
        ("S1740", "Private road", "#9e9e9e", 0.3),
        ("S1780", "Parking lot road", "#bdbdbd", 0.3),
        ("S1820", "Bike path", "#1976d2", 0.7),
    ]
    tiger_cats = [
        QgsRendererCategory(code, line(color, w), f"{code} {label}")
        for (code, label, color, w) in mtfcc_walk
    ]
    tiger_cats.append(QgsRendererCategory("", line("#bdbdbd", 0.3), "other"))
    tiger_edges.setRenderer(QgsCategorizedSymbolRenderer("mtfcc", tiger_cats))
    layers_to_add.append(tiger_edges)

    # --- 11. TIGER named cemeteries (labels) ---
    tiger_ceme = vl("tiger_cemeteries_named", "Cemeteries (TIGER, named)")
    tiger_ceme.setRenderer(
        QgsSingleSymbolRenderer(fill("#00000000", "#424242", 0.8))
    )
    layers_to_add.append(tiger_ceme)

    # --- 12. LMA POIs (categorized by category, raster-marker icons, zoom-in-only) ---
    from qgis.core import (  # noqa: E402
        QgsRasterMarkerSymbolLayer,
    )

    pois = vl("lma_pois", "LMA POIs (category icons — visible ≤ 1:1000)")
    poi_cats = []
    for category in CATEGORY_ICONS.keys():
        icon_path = ICONS_DIR / f"{category.lower()}.png"
        sym = QgsMarkerSymbol()
        if icon_path.exists():
            raster = QgsRasterMarkerSymbolLayer(str(icon_path))
            # Marker size is in mm by default; 6 mm ~= 22 px at 96 DPI.
            raster.setSize(6.0)
            sym.changeSymbolLayer(0, raster)
        else:
            sym = marker("#9e9e9e", 3.0)
        poi_cats.append(QgsRendererCategory(category, sym, category))
    # Fallback
    poi_cats.append(QgsRendererCategory("", marker("#9e9e9e", 2.5), "other"))
    pois.setRenderer(QgsCategorizedSymbolRenderer("category", poi_cats))

    # Scale-based visibility: show ONLY when zoomed in to 1:1000 or closer
    # (denominator ≤ 1000). In QGIS, minimumScale is the LARGEST denominator
    # at which the layer remains visible — set it to 1000 and the layer
    # disappears as soon as you zoom out past 1:1000.
    pois.setScaleBasedVisibility(True)
    pois.setMinimumScale(1000.0)   # hide when denominator > 1000 (zoomed out)
    pois.setMaximumScale(0.0)      # no lower bound (stays visible at max zoom)
    layers_to_add.append(pois)

    # --- 13. MHC Inventory (categorized by DESIGNATIO) ---
    mhc = vl("mhc_inventory", "MHC Historic Inventory (Salem)")
    # Categorize by legend which is a cleaner single-value field
    mhc_cats = [
        QgsRendererCategory(
            "NHL", marker("#d32f2f", 4.0, "star"), "NHL (National Historic Landmark)"
        ),
        QgsRendererCategory(
            "NRHP",
            marker("#e65100", 3.5, "square"),
            "NRHP (National Register)",
        ),
        QgsRendererCategory(
            "PR", marker("#fbc02d", 3.0, "triangle"), "PR (Preservation Restriction)"
        ),
        QgsRendererCategory(
            "LHD",
            marker("#7b1fa2", 3.0, "diamond"),
            "LHD (Local Historic District)",
        ),
        QgsRendererCategory(
            "Inventoried Property",
            marker("#1976d2", 2.5, "circle"),
            "Inventoried",
        ),
        QgsRendererCategory("", marker("#9e9e9e", 2.0, "circle"), "other"),
    ]
    mhc.setRenderer(QgsCategorizedSymbolRenderer("legend", mhc_cats))
    layers_to_add.append(mhc)

    # Add every layer to the project
    for lyr in layers_to_add:
        project.addMapLayer(lyr)

    # Set initial view to Salem bbox (plus a small buffer)
    salem_extent = QgsRectangle(-70.95, 42.47, -70.75, 42.56)
    # The canvas extent is normally captured per mapcanvas; for file-level,
    # set via the project's view settings proxy
    project.viewSettings().setDefaultViewExtent(
        project.viewSettings().defaultViewExtent()
        if False
        else _as_reference_extent(salem_extent)
    )

    project.write(str(QGZ))
    print(f"  QGIS project saved: {QGZ} ({QGZ.stat().st_size/1024:.1f} KB)")

    qgs.exitQgis()


def _as_reference_extent(rect):
    # Helper: wrap a QgsRectangle into a QgsReferencedRectangle with WGS84
    from qgis.core import QgsCoordinateReferenceSystem, QgsReferencedRectangle

    return QgsReferencedRectangle(rect, QgsCoordinateReferenceSystem("EPSG:4326"))


# -----------------------------------------------------------------------------
# Entrypoint
# -----------------------------------------------------------------------------


def main() -> None:
    if not shutil.which("ogr2ogr"):
        print("ogr2ogr not found in PATH", file=sys.stderr)
        sys.exit(1)
    print("=== Stage 1: exporting Salem data to GeoPackage ===")
    export_layers()
    print("=== Stage 1b: copying category icons ===")
    copy_icons()
    print("=== Stage 2: building QGIS project ===")
    build_project()
    print()
    print(f"Open with:  qgis {QGZ}")


if __name__ == "__main__":
    main()
