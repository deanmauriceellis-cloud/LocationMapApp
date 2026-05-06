#!/usr/bin/env python3
"""
S228 — pull WickedSalem GPS-burst photos off the Lenovo and organize them
into per-session folders under /mnt/sdb-images/LMASalemPictures/.

Workflow (all manual for now — no CLI flags yet):

  1. adb -s HNY0CY0W pull /sdcard/Pictures/WickedSalemRecon/ /tmp/lmasalem-stage/
  2. python3 tools/pull-and-organize-burst-photos.py
  3. (optional) adb -s HNY0CY0W shell 'rm /sdcard/Pictures/WickedSalemRecon/*.jpg && \
        am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
        -d file:///sdcard/Pictures/WickedSalemRecon/'

A "session" = a contiguous run of photos with no gap > SESSION_GAP_S between
consecutive timestamps. The on-device throttle (currently 2 s, S228 round 2)
means anything > ~2 minutes is a clear session boundary (operator toggled OFF
then back ON later).

Per-session outputs:
  - the JPEGs themselves (moved from staging)
  - track.gpx     — GPX 1.1 track of the photo positions in time order
  - track.geojson — same data as a GeoJSON LineString for quick web preview
  - metadata.csv  — filename,iso_time,lat,lon,gap_s_from_prev
  - summary.txt   — one-paragraph human summary

Filename format produced by GpsBurstCameraManager:
  burst_YYYYMMDD-HHMMSS_<lat>_<lon>.jpg   (negatives use 'n' instead of '-')
"""
import csv
import os
import re
import shutil
import sys
from datetime import datetime, timezone

STAGE_DIR = "/tmp/lmasalem-stage/WickedSalemRecon"
ROOT_DIR  = "/mnt/sdb-images/LMASalemPictures"
SESSION_GAP_S = 120  # > 2 min between consecutive shots = new session

FNAME_RE = re.compile(
    r"^burst_(\d{8})-(\d{6})_([n\d.]+)_([n\d.]+)\.jpg$"
)


def parse_filename(name: str):
    m = FNAME_RE.match(name)
    if not m:
        return None
    date_s, time_s, lat_s, lon_s = m.groups()
    ts = datetime.strptime(date_s + time_s, "%Y%m%d%H%M%S").replace(tzinfo=timezone.utc)
    lat = float(lat_s.replace("n", "-"))
    lon = float(lon_s.replace("n", "-"))
    return ts, lat, lon


def collect_photos(stage_dir: str):
    photos = []
    for name in os.listdir(stage_dir):
        if not name.endswith(".jpg"):
            continue
        parsed = parse_filename(name)
        if parsed is None:
            print(f"  skip (bad name): {name}", file=sys.stderr)
            continue
        ts, lat, lon = parsed
        photos.append({
            "name": name,
            "path": os.path.join(stage_dir, name),
            "ts": ts,
            "lat": lat,
            "lon": lon,
        })
    photos.sort(key=lambda p: p["ts"])
    return photos


def split_into_sessions(photos):
    sessions = []
    current = []
    prev_ts = None
    for p in photos:
        if prev_ts is None or (p["ts"] - prev_ts).total_seconds() <= SESSION_GAP_S:
            current.append(p)
        else:
            sessions.append(current)
            current = [p]
        prev_ts = p["ts"]
    if current:
        sessions.append(current)
    return sessions


def write_gpx(session, out_path: str):
    start_ts = session[0]["ts"].strftime("%Y-%m-%dT%H:%M:%SZ")
    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<gpx version="1.1" creator="LMA-burst-organizer" '
        'xmlns="http://www.topografix.com/GPX/1/1">',
        f"  <metadata><time>{start_ts}</time>"
        f"<name>WickedSalemRecon burst {start_ts}</name></metadata>",
        f"  <trk><name>burst {start_ts}</name><trkseg>",
    ]
    for p in session:
        iso = p["ts"].strftime("%Y-%m-%dT%H:%M:%SZ")
        lines.append(
            f'    <trkpt lat="{p["lat"]:.6f}" lon="{p["lon"]:.6f}">'
            f"<time>{iso}</time></trkpt>"
        )
    lines.extend(["  </trkseg></trk>", "</gpx>"])
    with open(out_path, "w") as fh:
        fh.write("\n".join(lines) + "\n")


def write_geojson(session, out_path: str):
    coords = [[p["lon"], p["lat"]] for p in session]
    pts = []
    for p in session:
        pts.append({
            "type": "Feature",
            "geometry": {"type": "Point", "coordinates": [p["lon"], p["lat"]]},
            "properties": {
                "filename": p["name"],
                "time": p["ts"].strftime("%Y-%m-%dT%H:%M:%SZ"),
            },
        })
    obj = {
        "type": "FeatureCollection",
        "features": [
            {
                "type": "Feature",
                "geometry": {"type": "LineString", "coordinates": coords},
                "properties": {"name": "track", "photos": len(session)},
            },
            *pts,
        ],
    }
    import json
    with open(out_path, "w") as fh:
        json.dump(obj, fh, indent=1)


def write_metadata_csv(session, out_path: str):
    with open(out_path, "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["filename", "iso_time", "lat", "lon", "gap_s_from_prev"])
        prev_ts = None
        for p in session:
            gap = "" if prev_ts is None else f"{(p['ts'] - prev_ts).total_seconds():.0f}"
            w.writerow([
                p["name"],
                p["ts"].strftime("%Y-%m-%dT%H:%M:%SZ"),
                f"{p['lat']:.6f}",
                f"{p['lon']:.6f}",
                gap,
            ])
            prev_ts = p["ts"]


def write_summary(session, out_path: str):
    import math
    start = session[0]["ts"]
    end = session[-1]["ts"]
    duration_s = (end - start).total_seconds()
    lats = [p["lat"] for p in session]
    lons = [p["lon"] for p in session]
    R = 6371000
    lat1 = math.radians(session[0]["lat"]); lat2 = math.radians(session[-1]["lat"])
    dlat = lat2 - lat1
    dlon = math.radians(session[-1]["lon"] - session[0]["lon"])
    a = math.sin(dlat/2)**2 + math.cos(lat1)*math.cos(lat2)*math.sin(dlon/2)**2
    end_to_end_m = 2 * R * math.asin(math.sqrt(a))
    with open(out_path, "w") as fh:
        fh.write(
            f"WickedSalemRecon burst session\n"
            f"================================\n"
            f"Start:        {start.strftime('%Y-%m-%d %H:%M:%S UTC')}\n"
            f"End:          {end.strftime('%Y-%m-%d %H:%M:%S UTC')}\n"
            f"Duration:     {duration_s:.0f} s ({duration_s/60:.1f} min)\n"
            f"Photos:       {len(session)}\n"
            f"Bounding box: lat [{min(lats):.5f}, {max(lats):.5f}]  "
            f"lon [{min(lons):.5f}, {max(lons):.5f}]\n"
            f"End-to-end:   {end_to_end_m:.0f} m (great-circle, "
            f"first→last photo)\n"
        )


def main():
    if not os.path.isdir(STAGE_DIR):
        print(f"Stage dir missing: {STAGE_DIR}", file=sys.stderr)
        print(f"Did you run `adb pull /sdcard/Pictures/WickedSalemRecon/ "
              f"{os.path.dirname(STAGE_DIR)}/` first?", file=sys.stderr)
        sys.exit(1)
    os.makedirs(ROOT_DIR, exist_ok=True)

    print(f"Scanning {STAGE_DIR} ...")
    photos = collect_photos(STAGE_DIR)
    print(f"  parsed {len(photos)} burst photos")

    sessions = split_into_sessions(photos)
    print(f"  → {len(sessions)} session(s) (split on gap > {SESSION_GAP_S}s)")

    for i, session in enumerate(sessions, 1):
        start = session[0]["ts"]
        folder = os.path.join(
            ROOT_DIR,
            f"session-{start.strftime('%Y%m%d-%H%M%S')}-{len(session)}photos",
        )
        os.makedirs(folder, exist_ok=True)
        print(f"\n[{i}/{len(sessions)}] {os.path.basename(folder)}")
        for p in session:
            dst = os.path.join(folder, p["name"])
            shutil.move(p["path"], dst)
        write_gpx(session, os.path.join(folder, "track.gpx"))
        write_geojson(session, os.path.join(folder, "track.geojson"))
        write_metadata_csv(session, os.path.join(folder, "metadata.csv"))
        write_summary(session, os.path.join(folder, "summary.txt"))
        print(f"  {len(session)} photos + track.gpx + track.geojson + "
              f"metadata.csv + summary.txt")

    try:
        if not os.listdir(STAGE_DIR):
            os.rmdir(STAGE_DIR)
            parent = os.path.dirname(STAGE_DIR)
            if not os.listdir(parent):
                os.rmdir(parent)
    except OSError:
        pass

    print(f"\nDone. Root: {ROOT_DIR}")


if __name__ == "__main__":
    main()
