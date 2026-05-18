#!/usr/bin/env python3
"""HB coverage using actual captured GPS positions (steps 1540-1887)."""
import math, re, sqlite3

ASSET_DB = "/home/witchdoctor/Development/LocationMapApp_v1.5/app-salem/src/main/assets/salem_content.db"
STREAM_LOG = "/tmp/s281_narr_stream.log"

def hav_m(a,b,c,d):
    R=6371000.0; p1,p2=math.radians(a),math.radians(c)
    dp=math.radians(c-a); dl=math.radians(d-b)
    x=math.sin(dp/2)**2+math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return 2*R*math.asin(math.sqrt(x))

# Parse GPS points from stream
gps = []
step_re = re.compile(r"step (\d+)/\d+ at (-?\d+\.\d+),(-?\d+\.\d+)")
with open(STREAM_LOG, errors="ignore") as f:
    for line in f:
        m = step_re.search(line)
        if m:
            gps.append((int(m.group(1)), float(m.group(2)), float(m.group(3))))
gps.sort()
print(f"GPS points captured: {len(gps)} (steps {gps[0][0]}-{gps[-1][0]})")

# Parse announces
announced = set()
pats = [
    re.compile(r"enqueueNarration: (.+?) tier="),
    re.compile(r"Enqueued: \w+ — (.+?) \(queue:"),
    re.compile(r"Speaking: \w+ — (.+?) voice="),
    re.compile(r"poiName=([^,]+)"),
]
ann_with_step = []  # (lineno, name)
with open(STREAM_LOG, errors="ignore") as f:
    for line in f:
        for pat in pats:
            m = pat.search(line)
            if m:
                announced.add(m.group(1).strip())
                ann_with_step.append(m.group(1).strip())
                break
print(f"Distinct names announced in capture window: {len(announced)}")

# Load HBs
con = sqlite3.connect(ASSET_DB)
hbs = con.execute("""SELECT id, name, lat, lng, geofence_radius_m
                     FROM salem_pois WHERE category='HISTORICAL_BUILDINGS'""").fetchall()

# For each HB: min dist to any captured GPS point + which step was closest
in_range = []
for pid, name, lat, lng, rad in hbs:
    if lat is None: continue
    best = min(((hav_m(lat, lng, glat, glng), step) for step, glat, glng in gps), key=lambda x: x[0])
    if best[0] <= rad:
        in_range.append((pid, name, best[0], rad, best[1]))

in_range.sort(key=lambda x: x[4])  # by step

hit_names = {n for _, n, _, _, _ in in_range} & announced
miss_names = {n for _, n, _, _, _ in in_range} - announced

print(f"\n=== HBs in range of CAPTURED GPS trace (steps {gps[0][0]}-{gps[-1][0]}) ===")
print(f"In range:   {len(in_range)}")
print(f"Announced:  {len(hit_names)}")
print(f"Missed:     {len(miss_names)}")

print("\nIn-range HBs (chronological by closest step):")
for pid, name, d, r, step in in_range:
    flag = "  HIT  " if name in hit_names else "MISSED!"
    print(f"  step≈{step:4d}  [{d:5.1f}m / r={r:>2}m]  {flag}  {name}")

# Non-HB announces
non_hb = announced - {n for _, n, _, _, _ in in_range}
if non_hb:
    print(f"\nAnnounced but not an in-range HB ({len(non_hb)}):")
    for n in sorted(non_hb):
        print(f"  • {n}  (other category, or > 40m from GPS trace)")
