#!/usr/bin/env python3
"""Ingest MassGIS L3 Parcels + L3_ASSESS for Essex County, MA into Postgres.

Streams AGOL/MassachusettsPropertyTaxParcels FeatureServer:
  layer 1 (Tax Parcels, polygons)  -> massgis.l3_parcels_essex
  table 4 (GISDATA.L3_ASSESS)      -> massgis.l3_assess_essex

Idempotent: maintains a pagination checkpoint in cache-proxy/out/l3-essex/.
"""

from __future__ import annotations

import json
import os
import sys
import time
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

import psycopg2
from psycopg2.extras import execute_values

BASE = "http://arcgisserver.digital.mass.gov/arcgisserver/rest/services/AGOL/MassachusettsPropertyTaxParcels/FeatureServer"
PARCEL_LAYER = 1
ASSESS_TABLE = 4
PAGE = 2000

ESSEX_BBOX = (-71.26, 42.41, -70.67, 42.89)  # WGS84

CHECK_DIR = Path(__file__).resolve().parent.parent / "out" / "l3-essex"
CHECK_DIR.mkdir(parents=True, exist_ok=True)

PG = {
    "dbname": "tiger",
    "user": os.environ.get("USER", "witchdoctor"),
}


def log(msg: str) -> None:
    ts = time.strftime("%H:%M:%S")
    print(f"[{ts}] {msg}", flush=True)


def fetch_json(url: str, retries: int = 4) -> dict:
    last_err = None
    for attempt in range(retries):
        try:
            req = Request(url, headers={"User-Agent": "lma-l3-ingest/1.0"})
            with urlopen(req, timeout=60) as r:
                return json.loads(r.read())
        except Exception as e:
            last_err = e
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"fetch failed after {retries} tries: {last_err}\n  url={url}")


def post_json(url: str, form: dict, retries: int = 4) -> dict:
    """POST form-encoded params — used when the query string would be too long."""
    body = urlencode(form).encode("utf-8")
    last_err = None
    for attempt in range(retries):
        try:
            req = Request(
                url,
                data=body,
                headers={
                    "User-Agent": "lma-l3-ingest/1.0",
                    "Content-Type": "application/x-www-form-urlencoded",
                },
            )
            with urlopen(req, timeout=60) as r:
                return json.loads(r.read())
        except Exception as e:
            last_err = e
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"post failed after {retries} tries: {last_err}\n  url={url}")


def count(layer: int, geometry: bool) -> int:
    params = {"where": "1=1", "returnCountOnly": "true", "f": "json"}
    if geometry:
        params.update(
            {
                "geometry": ",".join(map(str, ESSEX_BBOX)),
                "geometryType": "esriGeometryEnvelope",
                "inSR": "4326",
                "spatialRel": "esriSpatialRelIntersects",
            }
        )
    return fetch_json(f"{BASE}/{layer}/query?{urlencode(params)}")["count"]


def page(layer: int, offset: int, geometry: bool) -> dict:
    params = {
        "where": "1=1",
        "outFields": "*",
        "resultOffset": str(offset),
        "resultRecordCount": str(PAGE),
        "outSR": "4269",
        "f": "json",
    }
    if geometry:
        params.update(
            {
                "geometry": ",".join(map(str, ESSEX_BBOX)),
                "geometryType": "esriGeometryEnvelope",
                "inSR": "4326",
                "spatialRel": "esriSpatialRelIntersects",
                "returnGeometry": "true",
            }
        )
    else:
        params["returnGeometry"] = "false"
    return fetch_json(f"{BASE}/{layer}/query?{urlencode(params)}")


def dump_layer(layer: int, out_path: Path, geometry: bool, label: str) -> int:
    total = count(layer, geometry)
    log(f"{label}: {total:,} features to fetch")
    ck = out_path.with_suffix(".offset")
    start = int(ck.read_text()) if ck.exists() else 0
    if start:
        log(f"{label}: resuming at offset {start:,}")
    mode = "a" if start else "w"
    offset = start
    written = 0
    with out_path.open(mode) as f:
        while True:
            data = page(layer, offset, geometry)
            feats = data.get("features", [])
            if not feats:
                break
            for ft in feats:
                f.write(json.dumps(ft) + "\n")
            written += len(feats)
            offset += len(feats)
            ck.write_text(str(offset))
            if written and written % (PAGE * 5) == 0:
                log(f"{label}: {offset:,}/{total:,} ({offset/total*100:.1f}%)")
            # Correct termination: ArcGIS REST sets exceededTransferLimit
            # when more pages remain. A short page is NOT a stop signal.
            if not data.get("exceededTransferLimit", False):
                break
    log(f"{label}: wrote {written:,} new features  (file total ≈ {offset:,})")
    return offset


def esri_to_wkt_multipolygon(esri_geom: dict) -> str:
    """Convert Esri JSON polygon/multipolygon rings to WKT MULTIPOLYGON.

    Esri stores all rings (outer and holes) in a flat 'rings' list. A ring is
    outer when its signed area is negative (clockwise in screen coords) — we
    group holes with their most-recent preceding outer ring.
    """
    rings = esri_geom.get("rings", [])
    if not rings:
        return "MULTIPOLYGON EMPTY"

    def signed_area(r):
        return sum(
            (r[i][0] - r[i - 1][0]) * (r[i][1] + r[i - 1][1])
            for i in range(1, len(r))
        )

    polys: list[list[list]] = []
    for r in rings:
        if signed_area(r) < 0:  # outer ring (Esri convention)
            polys.append([r])
        elif polys:
            polys[-1].append(r)
        else:
            polys.append([r])  # orphan — treat as outer

    parts = []
    for poly in polys:
        ring_strs = []
        for r in poly:
            pts = ", ".join(f"{x} {y}" for x, y, *_ in r)
            ring_strs.append(f"({pts})")
        parts.append(f"({', '.join(ring_strs)})")
    return f"MULTIPOLYGON({', '.join(parts)})"


def create_tables(conn) -> None:
    with conn.cursor() as cur:
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS massgis.l3_parcels_essex (
              objectid     integer PRIMARY KEY,
              map_par_id   text,
              loc_id       text,
              poly_type    text,
              map_no       text,
              source       text,
              plan_id      text,
              last_edit    integer,
              bnd_chk      text,
              no_match     text,
              town_id      smallint,
              site         text,
              esn          smallint,
              lu_codes     text,
              dev          text,
              sym1         smallint,
              sym2         smallint,
              shape_area   double precision,
              shape_len    double precision,
              geom         geometry(MultiPolygon, 4269)
            );
            CREATE INDEX IF NOT EXISTS idx_l3_parcels_essex_geom
              ON massgis.l3_parcels_essex USING GIST (geom);
            CREATE INDEX IF NOT EXISTS idx_l3_parcels_essex_loc_id
              ON massgis.l3_parcels_essex (loc_id);

            CREATE TABLE IF NOT EXISTS massgis.l3_assess_essex (
              objectid      integer PRIMARY KEY,
              loc_id        text,
              raw           jsonb
            );
            CREATE INDEX IF NOT EXISTS idx_l3_assess_essex_loc_id
              ON massgis.l3_assess_essex (loc_id);
            """
        )
    conn.commit()


def load_parcels(conn, ndjson_path: Path) -> int:
    cols = [
        "objectid",
        "map_par_id",
        "loc_id",
        "poly_type",
        "map_no",
        "source",
        "plan_id",
        "last_edit",
        "bnd_chk",
        "no_match",
        "town_id",
        "site",
        "esn",
        "lu_codes",
        "dev",
        "sym1",
        "sym2",
        "shape_area",
        "shape_len",
        "geom",
    ]
    rows: list[tuple] = []
    inserted = 0
    with conn.cursor() as cur, ndjson_path.open() as f:
        for line in f:
            feat = json.loads(line)
            a = feat["attributes"]
            g = feat.get("geometry")
            if not g:
                continue
            wkt = esri_to_wkt_multipolygon(g)
            rows.append(
                (
                    a.get("OBJECTID"),
                    a.get("MAP_PAR_ID"),
                    a.get("LOC_ID"),
                    a.get("POLY_TYPE"),
                    a.get("MAP_NO"),
                    a.get("SOURCE"),
                    a.get("PLAN_ID"),
                    a.get("LAST_EDIT"),
                    a.get("BND_CHK"),
                    a.get("NO_MATCH"),
                    a.get("TOWN_ID"),
                    a.get("SITE"),
                    a.get("ESN"),
                    a.get("LU_CODES"),
                    a.get("DEV"),
                    a.get("SYM1"),
                    a.get("SYM2"),
                    a.get("Shape__Area"),
                    a.get("Shape__Length"),
                    wkt,
                )
            )
            if len(rows) >= 500:
                _flush_parcels(cur, cols, rows)
                inserted += len(rows)
                rows.clear()
                if inserted % 5000 == 0:
                    log(f"parcels: inserted {inserted:,}")
        if rows:
            _flush_parcels(cur, cols, rows)
            inserted += len(rows)
    conn.commit()
    return inserted


def _flush_parcels(cur, cols, rows) -> None:
    sql = (
        f"INSERT INTO massgis.l3_parcels_essex ({','.join(cols)}) "
        "VALUES %s ON CONFLICT (objectid) DO NOTHING"
    )
    template = (
        "(" + ",".join(["%s"] * (len(cols) - 1)) + ",ST_Multi(ST_GeomFromText(%s,4269)))"
    )
    execute_values(cur, sql, rows, template=template, page_size=250)


def load_assess(conn, ndjson_path: Path) -> int:
    rows = []
    inserted = 0
    with conn.cursor() as cur, ndjson_path.open() as f:
        for line in f:
            feat = json.loads(line)
            a = feat["attributes"]
            rows.append((a.get("OBJECTID"), a.get("LOC_ID"), json.dumps(a)))
            if len(rows) >= 1000:
                execute_values(
                    cur,
                    "INSERT INTO massgis.l3_assess_essex (objectid, loc_id, raw) "
                    "VALUES %s ON CONFLICT (objectid) DO NOTHING",
                    rows,
                )
                inserted += len(rows)
                rows.clear()
                if inserted % 10000 == 0:
                    log(f"assess: inserted {inserted:,}")
        if rows:
            execute_values(
                cur,
                "INSERT INTO massgis.l3_assess_essex (objectid, loc_id, raw) "
                "VALUES %s ON CONFLICT (objectid) DO NOTHING",
                rows,
            )
            inserted += len(rows)
    conn.commit()
    return inserted


def main() -> None:
    parcels_nd = CHECK_DIR / "parcels.ndjson"
    assess_nd = CHECK_DIR / "assess.ndjson"

    log("=== Stage 1: Parcels fetch (layer 1, spatial bbox) ===")
    dump_layer(PARCEL_LAYER, parcels_nd, geometry=True, label="parcels")

    log("=== Stage 2: Create tables + load parcels into Postgres ===")
    with psycopg2.connect(**PG) as conn:
        create_tables(conn)
        with conn.cursor() as cur:
            cur.execute("SELECT count(*) FROM massgis.l3_parcels_essex")
            existing = cur.fetchone()[0]
        if existing == 0:
            n_parcels = load_parcels(conn, parcels_nd)
            log(f"parcels: loaded {n_parcels:,} rows into massgis.l3_parcels_essex")
        else:
            log(f"parcels: {existing:,} rows already in table, skipping load")

    log("=== Stage 3: Fetch assess (filtered to parcels' LOC_IDs) ===")
    with psycopg2.connect(**PG) as probe:
        with probe.cursor() as cur:
            cur.execute("SELECT DISTINCT loc_id FROM massgis.l3_parcels_essex WHERE loc_id IS NOT NULL")
            loc_ids = [r[0] for r in cur.fetchall()]
    log(f"assess: {len(loc_ids):,} distinct LOC_IDs to pull")

    # Chunk LOC_IDs into IN(...) groups of 200 to keep URL sane
    if not assess_nd.exists() or assess_nd.stat().st_size == 0:
        with assess_nd.open("w") as out:
            for i in range(0, len(loc_ids), 200):
                chunk = loc_ids[i : i + 200]
                quoted = ",".join(f"'{lid}'" for lid in chunk)
                offset = 0
                while True:
                    params = {
                        "where": f"LOC_ID IN ({quoted})",
                        "outFields": "*",
                        "resultOffset": str(offset),
                        "resultRecordCount": str(PAGE),
                        "returnGeometry": "false",
                        "f": "json",
                    }
                    data = post_json(f"{BASE}/{ASSESS_TABLE}/query", params)
                    feats = data.get("features", [])
                    for ft in feats:
                        out.write(json.dumps(ft) + "\n")
                    offset += len(feats)
                    if not feats or not data.get("exceededTransferLimit", False):
                        break
                if (i // 200) % 20 == 0:
                    log(f"assess: fetched through chunk {i//200}/{len(loc_ids)//200}")
        log(f"assess: fetch complete")
    else:
        log(f"assess: {assess_nd.name} already exists, skipping fetch")

    log("=== Stage 4: Load assess into Postgres ===")
    with psycopg2.connect(**PG) as conn:
        n_assess = load_assess(conn, assess_nd)
        log(f"assess: loaded {n_assess:,} rows into massgis.l3_assess_essex")

        with conn.cursor() as cur:
            cur.execute("SELECT count(*) FROM massgis.l3_parcels_essex")
            pc = cur.fetchone()[0]
            cur.execute("SELECT count(*) FROM massgis.l3_assess_essex")
            ac = cur.fetchone()[0]
            log(f"final: parcels={pc:,}  assess={ac:,}")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        log("interrupted by user — offset checkpoint preserved, re-run to resume")
        sys.exit(130)
