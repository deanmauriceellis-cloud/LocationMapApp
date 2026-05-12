#!/usr/bin/env python3
"""bake-tigerbase.py — render TIGER/Line shapefiles into a slippy-map WebP tile pyramid.

Layered output: each layer renders to its own pyramid with transparent background, so they
can be composited at serve time or toggled independently on-device. Pass --layer all to
bake every layer in one pass (reuses the shapefile load).

Layers:
  water        — opaque water-color tile minus land mask; the visual base
  boundaries   — state + county lines (transparent bg)
  roads        — primary + secondary roads (transparent bg)
  places       — city/town dots (transparent bg)
  labels       — city/town text with halo and per-tile collision dedup (transparent bg)

In-house: no Mapnik, no OSM toolchain. Pillow + Shapely only.
Pipeline contract: touches only tools/tigerbase/. Never reads tools/tile-bake/ (Salem Witchy).
"""

from __future__ import annotations

import argparse
import json
import math
import multiprocessing as mp
import os
import sys
import time
from pathlib import Path
from typing import Iterable

import geopandas as gpd
import mercantile
import pandas as pd
from PIL import Image, ImageDraw, ImageFont
from shapely.geometry import box
from shapely.ops import unary_union

# Module-level globals populated in the main process before forking workers.
# Linux fork() means workers inherit these pages via copy-on-write, so we pay
# the ~3 GB shapefile-load cost once instead of per-worker.
_LAYERS: dict | None = None
_STYLE: dict | None = None
_OUT_ROOT: Path | None = None
_LAYERS_TO_RENDER: tuple | None = None

TILE = 256
ALL_LAYERS = ("water", "boundaries", "roads", "places", "labels")

# Per-layer WebP encoder tuning. method=6 is the slowest+smallest encoder pass;
# was method=4 in the very first Z3-Z10 bake. Confirmed empirically (S252) that
# lossless WebP is larger than lossy q=70 for ALL of our layer types — the tiles
# carry antialiased lines or smooth fills, not flat-color palette content.
LAYER_ENCODE = {
    "water":      {"quality": 70, "method": 6},
    "boundaries": {"quality": 75, "method": 6},
    "roads":      {"quality": 60, "method": 6},
    "places":     {"quality": 75, "method": 6},
    "labels":     {"quality": 75, "method": 6},
}


# ---------------------------------------------------------------------------
# Web Mercator
# ---------------------------------------------------------------------------

def tile_pixel_xy(lon: float, lat: float, z: int, tx: int, ty: int) -> tuple[float, float]:
    n = 2 ** z
    world_px = TILE * n
    x = (lon + 180.0) / 360.0 * world_px
    sin_lat = math.sin(math.radians(lat))
    y = (0.5 - math.log((1 + sin_lat) / (1 - sin_lat)) / (4 * math.pi)) * world_px
    return x - tx * TILE, y - ty * TILE


def pixel_to_lonlat(px: float, py: float, z: int, tx: int, ty: int) -> tuple[float, float]:
    n = 2 ** z
    world_px = TILE * n
    abs_x = tx * TILE + px
    abs_y = ty * TILE + py
    lon = abs_x / world_px * 360.0 - 180.0
    yt = math.pi - 2.0 * math.pi * abs_y / world_px
    lat = math.degrees(math.atan(math.sinh(yt)))
    return lon, lat


# ---------------------------------------------------------------------------
# Sources
# ---------------------------------------------------------------------------

def load_sources(sources_dir: Path, bbox: tuple[float, float, float, float]) -> dict:
    minx, miny, maxx, maxy = bbox
    clip = box(minx, miny, maxx, maxy)
    out: dict = {}

    # Roads
    road_dirs = sorted((sources_dir / "PRISECROADS").glob("*"))
    rframes = []
    for d in road_dirs:
        shps = list(d.glob("*.shp"))
        if not shps:
            continue
        g = gpd.read_file(shps[0]).to_crs(4326)
        g = g[g.intersects(clip)]
        if len(g):
            rframes.append(g)
    if rframes:
        roads = gpd.GeoDataFrame(pd.concat(rframes, ignore_index=True), crs=4326)
        out["roads_primary"] = roads[roads["MTFCC"] == "S1100"]
        out["roads_secondary"] = roads[roads["MTFCC"] == "S1200"]
    else:
        out["roads_primary"] = gpd.GeoDataFrame(geometry=[], crs=4326)
        out["roads_secondary"] = gpd.GeoDataFrame(geometry=[], crs=4326)

    # Places: representative points + ALAND for priority
    pdirs = sorted((sources_dir / "PLACE").glob("*"))
    pframes = []
    for d in pdirs:
        shps = list(d.glob("*.shp"))
        if not shps:
            continue
        g = gpd.read_file(shps[0]).to_crs(4326)
        g = g.copy()
        g["geometry"] = g.geometry.representative_point()
        g = g[g.intersects(clip)]
        if len(g):
            pframes.append(g)
    if pframes:
        out["places"] = gpd.GeoDataFrame(pd.concat(pframes, ignore_index=True), crs=4326)
        # ALAND used as label-rank proxy (TIGER doesn't ship population in PLACE shapefile)
        out["places"]["_rank"] = out["places"]["ALAND"].astype(float)
    else:
        out["places"] = gpd.GeoDataFrame(geometry=[], crs=4326)

    # Boundaries
    nat = sources_dir / "national"
    for key, sub in (("county", "county"), ("state", "state")):
        ss = list((nat / sub).glob("*.shp"))
        if ss:
            g = gpd.read_file(ss[0]).to_crs(4326)
            out[key] = g[g.intersects(clip)]
        else:
            out[key] = gpd.GeoDataFrame(geometry=[], crs=4326)

    # Land mask for water layer = union of state polygons clipped to bbox
    if len(out["state"]) > 0:
        try:
            out["_land_union"] = unary_union(list(out["state"].geometry)).intersection(clip)
        except Exception:
            out["_land_union"] = None
    else:
        out["_land_union"] = None

    return out


# ---------------------------------------------------------------------------
# Fonts
# ---------------------------------------------------------------------------

_FONT_CACHE: dict[int, ImageFont.FreeTypeFont] = {}


def get_font(size: int) -> ImageFont.FreeTypeFont:
    if size in _FONT_CACHE:
        return _FONT_CACHE[size]
    for p in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ):
        if os.path.exists(p):
            f = ImageFont.truetype(p, size)
            _FONT_CACHE[size] = f
            return f
    f = ImageFont.load_default()
    _FONT_CACHE[size] = f
    return f


# ---------------------------------------------------------------------------
# Drawing helpers
# ---------------------------------------------------------------------------

def draw_lines(draw: ImageDraw.ImageDraw, geom, z: int, tx: int, ty: int, color, width: float):
    if geom.is_empty:
        return
    gt = geom.geom_type
    if gt == "MultiLineString":
        for sub in geom.geoms:
            draw_lines(draw, sub, z, tx, ty, color, width)
        return
    if gt == "LineString":
        pts = [tile_pixel_xy(x, y, z, tx, ty) for x, y in geom.coords]
        if len(pts) >= 2:
            draw.line(pts, fill=color, width=max(1, int(round(width))))
        return
    if gt == "MultiPolygon":
        for sub in geom.geoms:
            draw_lines(draw, sub, z, tx, ty, color, width)
        return
    if gt == "Polygon":
        pts = [tile_pixel_xy(x, y, z, tx, ty) for x, y in geom.exterior.coords]
        if len(pts) >= 2:
            draw.line(pts, fill=color, width=max(1, int(round(width))))
        for interior in geom.interiors:
            pts2 = [tile_pixel_xy(x, y, z, tx, ty) for x, y in interior.coords]
            if len(pts2) >= 2:
                draw.line(pts2, fill=color, width=max(1, int(round(width))))


def draw_polygons_filled(draw: ImageDraw.ImageDraw, geom, z: int, tx: int, ty: int, fill):
    if geom.is_empty:
        return
    gt = geom.geom_type
    if gt == "MultiPolygon":
        for sub in geom.geoms:
            draw_polygons_filled(draw, sub, z, tx, ty, fill)
        return
    if gt == "Polygon":
        pts = [tile_pixel_xy(x, y, z, tx, ty) for x, y in geom.exterior.coords]
        if len(pts) >= 3:
            draw.polygon(pts, fill=fill)


# ---------------------------------------------------------------------------
# Per-layer renderers
# ---------------------------------------------------------------------------

def render_water(z, tx, ty, layers, style) -> tuple[Image.Image, int]:
    cfg = style["water"]
    img = Image.new("RGBA", (TILE, TILE), cfg["water_color"])
    draw = ImageDraw.Draw(img, "RGBA")
    land = layers.get("_land_union")
    if land is None or land.is_empty:
        return img, 1
    tile_box = _tile_box(z, tx, ty)
    clipped = land.intersection(tile_box)
    if clipped.is_empty:
        return img, 1
    draw_polygons_filled(draw, clipped, z, tx, ty, cfg["land_color"])
    return img, 1


def render_boundaries(z, tx, ty, layers, style) -> tuple[Image.Image, int]:
    img = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img, "RGBA")
    tile_box = _tile_box(z, tx, ty)
    features = 0
    for key in ("county", "state"):
        cfg = style[key]
        if not (cfg["min_zoom"] <= z <= cfg["max_zoom"]):
            continue
        gdf = layers[key]
        if gdf.empty:
            continue
        clipped = gdf[gdf.intersects(tile_box)]
        for geom in clipped.geometry:
            inter = geom.intersection(tile_box)
            draw_lines(draw, inter, z, tx, ty, cfg["stroke"], cfg["width_px"])
            features += 1
    return img, features


def render_roads(z, tx, ty, layers, style) -> tuple[Image.Image, int]:
    img = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img, "RGBA")
    tile_box = _tile_box(z, tx, ty)
    features = 0
    for key in ("roads_secondary", "roads_primary"):  # secondary first so primary draws on top
        cfg = style[key]
        if not (cfg["min_zoom"] <= z <= cfg["max_zoom"]):
            continue
        gdf = layers[key]
        if gdf.empty:
            continue
        width = cfg["width_px_by_zoom"][str(z)]
        clipped = gdf[gdf.intersects(tile_box)]
        for geom in clipped.geometry:
            inter = geom.intersection(tile_box)
            draw_lines(draw, inter, z, tx, ty, cfg["stroke"], width)
            features += 1
    return img, features


def render_places(z, tx, ty, layers, style) -> tuple[Image.Image, int]:
    img = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img, "RGBA")
    cfg = style["place_dot"]
    if not (cfg["min_zoom"] <= z <= cfg["max_zoom"]):
        return img, 0
    gdf = layers["places"]
    if gdf.empty:
        return img, 0
    tile_box = _tile_box(z, tx, ty)
    clipped = gdf[gdf.intersects(tile_box)]
    if clipped.empty:
        return img, 0
    radius = cfg["radius_by_zoom"][str(z)]
    features = 0
    for _, row in clipped.iterrows():
        pt = row.geometry
        px, py = tile_pixel_xy(pt.x, pt.y, z, tx, ty)
        draw.ellipse((px - radius, py - radius, px + radius, py + radius),
                     fill=cfg["fill"], outline=cfg.get("outline"))
        features += 1
    return img, features


def render_labels(z, tx, ty, layers, style) -> tuple[Image.Image, int]:
    img = Image.new("RGBA", (TILE, TILE), (0, 0, 0, 0))
    cfg = style["place_label"]
    if not (cfg["min_zoom"] <= z <= cfg["max_zoom"]):
        return img, 0
    gdf = layers["places"]
    if gdf.empty:
        return img, 0
    tile_box = _tile_box(z, tx, ty)
    clipped = gdf[gdf.intersects(tile_box)]
    if clipped.empty:
        return img, 0

    # Priority order: ALAND descending so big cities reserve their spot first.
    clipped = clipped.sort_values("_rank", ascending=False)
    font = get_font(cfg["size_by_zoom"][str(z)])
    draw = ImageDraw.Draw(img, "RGBA")
    placed_boxes: list[tuple[int, int, int, int]] = []
    features = 0
    pad = cfg.get("collision_pad", 2)
    for _, row in clipped.iterrows():
        name = row.get("NAME")
        if not name:
            continue
        pt = row.geometry
        px, py = tile_pixel_xy(pt.x, pt.y, z, tx, ty)
        # Compute text bbox at offset
        text_x, text_y = px + 4, py - 6
        bbox_xy = draw.textbbox((text_x, text_y), name, font=font, stroke_width=cfg["halo_width"])
        # Inflate by pad
        b = (bbox_xy[0] - pad, bbox_xy[1] - pad, bbox_xy[2] + pad, bbox_xy[3] + pad)
        # Collision check
        collide = False
        for ob in placed_boxes:
            if not (b[2] < ob[0] or b[0] > ob[2] or b[3] < ob[1] or b[1] > ob[3]):
                collide = True
                break
        if collide:
            continue
        # Cull labels whose center is outside the tile (avoid duplicate placement when point is just outside)
        if px < -64 or px > TILE + 64 or py < -64 or py > TILE + 64:
            continue
        draw.text((text_x, text_y), name, font=font, fill=cfg["fill"],
                  stroke_width=cfg["halo_width"], stroke_fill=cfg["halo"])
        placed_boxes.append(b)
        features += 1
    return img, features


LAYER_RENDERERS = {
    "water": render_water,
    "boundaries": render_boundaries,
    "roads": render_roads,
    "places": render_places,
    "labels": render_labels,
}


# ---------------------------------------------------------------------------
# Tile bookkeeping
# ---------------------------------------------------------------------------

def _tile_box(z: int, tx: int, ty: int):
    """shapely box in lon/lat for tile (z, tx, ty)."""
    lon_w, lat_n = mercantile.ul(tx, ty, z)
    lon_e, lat_s = mercantile.ul(tx + 1, ty + 1, z)
    return box(lon_w, lat_s, lon_e, lat_n)


def save_tile(img: Image.Image, out_dir: Path, layer: str, z: int, tx: int, ty: int, features: int) -> int:
    if features == 0:
        return 0
    d = out_dir / layer / str(z) / str(tx)
    d.mkdir(parents=True, exist_ok=True)
    p = d / f"{ty}.webp"
    encode = LAYER_ENCODE.get(layer, {"quality": 70, "method": 6})
    img.save(p, "WEBP", **encode)
    return p.stat().st_size


# ---------------------------------------------------------------------------
# Driver
# ---------------------------------------------------------------------------

def _render_one(args_tuple) -> tuple[int, dict[str, tuple[int, int]]]:
    """Worker entry: render all configured layers for one tile.
    Returns (z, {layer: (1_if_written_else_0, bytes_written)}).
    Uses module-level globals inherited from parent via fork()."""
    z, tx, ty = args_tuple
    results: dict[str, tuple[int, int]] = {}
    for layer in _LAYERS_TO_RENDER:
        img, feat = LAYER_RENDERERS[layer](z, tx, ty, _LAYERS, _STYLE)
        nb = save_tile(img, _OUT_ROOT, layer, z, tx, ty, feat)
        results[layer] = (1 if nb else 0, nb)
    return z, results


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--bbox", required=True, help="minx,miny,maxx,maxy (lon/lat)")
    p.add_argument("--zmin", type=int, default=6)
    p.add_argument("--zmax", type=int, default=14)
    p.add_argument("--sources", required=True)
    p.add_argument("--style", default=None)
    p.add_argument("--out", required=True, help="Output dir; layers placed as <out>/<layer>/{z}/{x}/{y}.webp")
    p.add_argument("--layer", choices=("all",) + ALL_LAYERS, default="all")
    p.add_argument("--workers", type=int, default=max(1, min(8, (os.cpu_count() or 4) - 2)),
                   help="Worker processes for parallel tile render. 1 = serial fallback.")
    args = p.parse_args()

    bbox = tuple(float(x) for x in args.bbox.split(","))
    if len(bbox) != 4:
        print("bad --bbox", file=sys.stderr)
        return 2

    style_path = Path(args.style) if args.style else Path(__file__).parent.parent / "style" / "tigerbase.json"
    style = json.loads(style_path.read_text())
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    layers_to_render: Iterable[str] = ALL_LAYERS if args.layer == "all" else (args.layer,)

    print(f"[tigerbase] loading sources from {args.sources} clipped to {bbox} ...")
    t0 = time.time()
    layers = load_sources(Path(args.sources), bbox)
    print(f"[tigerbase] sources loaded in {time.time() - t0:.1f}s — "
          f"primary={len(layers['roads_primary'])} secondary={len(layers['roads_secondary'])} "
          f"places={len(layers['places'])} county={len(layers['county'])} state={len(layers['state'])} "
          f"land_union={'yes' if layers.get('_land_union') is not None else 'no'}")

    # Force-touch spatial index on each GeoDataFrame so it's built once in the parent
    # and inherited by workers via fork() COW (not rebuilt independently in each worker).
    for k in ("roads_primary", "roads_secondary", "places", "county", "state"):
        if not layers[k].empty:
            _ = layers[k].sindex

    # Populate module globals so fork()ed workers can read them via inherited pages.
    global _LAYERS, _STYLE, _OUT_ROOT, _LAYERS_TO_RENDER
    _LAYERS = layers
    _STYLE = style
    _OUT_ROOT = out_dir
    _LAYERS_TO_RENDER = tuple(layers_to_render)

    totals: dict[str, dict[str, int]] = {l: {"written": 0, "skipped": 0, "bytes": 0} for l in layers_to_render}
    workers = max(1, args.workers)
    print(f"[tigerbase] using {workers} worker process(es) (fork-COW shared sources)")

    use_pool = workers > 1
    ctx = mp.get_context("fork") if use_pool else None

    for z in range(args.zmin, args.zmax + 1):
        z_tiles = list(mercantile.tiles(*bbox, [z]))
        print(f"[tigerbase] zoom {z}: {len(z_tiles)} tiles  layers={list(layers_to_render)}")
        zt0 = time.time()
        work_items = [(z, t.x, t.y) for t in z_tiles]
        if use_pool and len(work_items) >= workers * 4:
            # Chunk so each worker pulls a sensible batch (reduces IPC overhead vs 1-tile chunks).
            chunksize = max(1, len(work_items) // (workers * 8))
            with ctx.Pool(workers) as pool:
                for _z, per_layer in pool.imap_unordered(_render_one, work_items, chunksize=chunksize):
                    for layer, (wrote, nb) in per_layer.items():
                        if wrote:
                            totals[layer]["written"] += 1
                            totals[layer]["bytes"] += nb
                        else:
                            totals[layer]["skipped"] += 1
        else:
            # Tiny zoom (cheaper to run inline) or workers==1
            for wi in work_items:
                _z, per_layer = _render_one(wi)
                for layer, (wrote, nb) in per_layer.items():
                    if wrote:
                        totals[layer]["written"] += 1
                        totals[layer]["bytes"] += nb
                    else:
                        totals[layer]["skipped"] += 1
        print(f"[tigerbase] zoom {z} done in {time.time() - zt0:.1f}s "
              f"({len(z_tiles) / max(0.001, time.time() - zt0):.1f} tiles/s)")

    print("[tigerbase] DONE")
    for layer, t in totals.items():
        print(f"  {layer:11s}  written={t['written']:6d}  skipped={t['skipped']:6d}  "
              f"size={t['bytes'] / 1024 / 1024:7.2f} MB")
    print(f"  wall={time.time() - t0:.1f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
