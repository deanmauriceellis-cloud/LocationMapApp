#!/usr/bin/env python3
"""
Live status page writer for the triptych campaign.

Runs as a standalone background daemon alongside generate_full.py. Every
SNAPSHOT_INTERVAL_S seconds, reads the output directory + the campaign log
tail, then rewrites output-full/status.html with:
  - meta-refresh every PAGE_REFRESH_S seconds
  - progress bar + stats
  - category-grouped gallery of EVERY triptych generated so far
  - each thumbnail is a link that opens the full-size WebP in a new tab

The gallery grows with every refresh until the campaign hits 1,837.
"""

import argparse
import html
import re
import sqlite3
import time
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent
OUT_DIR = SCRIPT_DIR / "output-full"
TRIP_DIR = OUT_DIR / "triptychs"
FAILED_DIR = OUT_DIR / "failed"

DB_PATH = (
    Path.home()
    / "Development/LocationMapApp_v1.5/app-salem/src/main/assets/salem_content.db"
)

SNAPSHOT_INTERVAL_S = 20
PAGE_REFRESH_S = 15
DEFAULT_LOG = Path(
    "/tmp/claude-1000/-home-witchdoctor-Development-LocationMapApp-v1-5/3d5cab92-89d8-49b5-927e-cff7dfe3f80d/tasks/bkfnkdqwi.output"
)
TOTAL_FALLBACK = 1837

LINE_RE = re.compile(
    r"\[(\d+)/(\d+)\]\s+(.+?)\s+\(([A-Z_ ]+)\)\s+[\d.]+s\s+(\d+)KB\s+rate=([\d.]+)/min\s+eta=(\d+)min"
)


def load_poi_meta() -> dict[str, dict]:
    """Cache POI id → {name, category} from the bundled Room DB."""
    if not DB_PATH.exists():
        print(f"warn: DB not found at {DB_PATH}", flush=True)
        return {}
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    rows = conn.execute(
        "SELECT id, name, category FROM salem_pois "
        "WHERE (data_source NOT LIKE '%dedup%' OR data_source IS NULL)"
    ).fetchall()
    conn.close()
    return {r["id"]: {"name": r["name"], "category": r["category"] or "UNKNOWN"} for r in rows}


def parse_last_line(log_text: str) -> dict | None:
    for line in reversed(log_text.splitlines()):
        m = LINE_RE.search(line)
        if m:
            return {
                "i": int(m.group(1)),
                "total": int(m.group(2)),
                "name": m.group(3).strip(),
                "category": m.group(4).strip(),
                "kb": int(m.group(5)),
                "rate": float(m.group(6)),
                "eta_min": int(m.group(7)),
            }
    return None


def read_log_tail(log_path: Path, max_bytes: int = 80_000) -> str:
    try:
        size = log_path.stat().st_size
        with open(log_path, "rb") as f:
            if size > max_bytes:
                f.seek(size - max_bytes)
            return f.read().decode(errors="replace")
    except FileNotFoundError:
        return ""


def write_status_html(poi_meta: dict[str, dict], log_path: Path):
    files = list(TRIP_DIR.glob("*.webp")) if TRIP_DIR.exists() else []
    done = len(files)
    failed = len(list(FAILED_DIR.glob("*.txt"))) if FAILED_DIR.exists() else 0
    log_tail = read_log_tail(log_path)
    parsed = parse_last_line(log_tail)

    total = parsed["total"] if parsed else TOTAL_FALLBACK
    pct = (done / total * 100.0) if total else 0.0
    rate = parsed["rate"] if parsed else 0.0
    eta_min = parsed["eta_min"] if parsed else 0
    last_name = parsed["name"] if parsed else "—"
    last_cat = parsed["category"] if parsed else "—"

    # Group completed triptychs by category (per DB metadata, fallback UNKNOWN)
    by_cat: dict[str, list[tuple[str, str, float]]] = {}
    for f in files:
        pid = f.stem
        meta = poi_meta.get(pid, {"name": pid, "category": "UNKNOWN"})
        cat = meta["category"]
        by_cat.setdefault(cat, []).append((pid, meta["name"], f.stat().st_mtime))

    cats_sorted = sorted(by_cat.keys(), key=lambda c: (-len(by_cat[c]), c))

    # TOC: category → count
    toc_html = "".join(
        f'<a href="#cat-{html.escape(c)}">{html.escape(c)} · {len(by_cat[c])}</a>'
        for c in cats_sorted
    )

    # Per-category grids
    cat_sections = []
    for cat in cats_sorted:
        items = sorted(by_cat[cat], key=lambda x: x[1].lower())
        cards = []
        for pid, name, _mtime in items:
            rel = f"triptychs/{html.escape(pid)}.webp"
            n_esc = html.escape(name)
            p_esc = html.escape(pid)
            cards.append(
                f'<a class=card href="{rel}" target="_blank" title="{n_esc}">'
                f'<img src="{rel}" loading=lazy alt="{n_esc}">'
                f'<div class=name>{n_esc}</div><div class=pid>{p_esc}</div>'
                f'</a>'
            )
        cat_sections.append(
            f'<section class=cat id="cat-{html.escape(cat)}">'
            f'<h2>{html.escape(cat)} — {len(items)}</h2>'
            f'<div class=grid>{"".join(cards)}</div></section>'
        )

    tail_block = html.escape("\n".join(log_tail.splitlines()[-12:]))
    now_str = time.strftime("%Y-%m-%d %H:%M:%S")

    page = f"""<!doctype html><html><head><meta charset=utf-8>
<meta http-equiv="refresh" content="{PAGE_REFRESH_S}">
<title>Triptych Campaign — Live Status ({done}/{total})</title>
<style>
  body{{margin:0;padding:20px;background:#111;color:#eee;font-family:system-ui,-apple-system,sans-serif;max-width:1600px;margin-left:auto;margin-right:auto}}
  h1{{margin:0;font-size:22px}}
  .sub{{color:#888;font-size:13px;margin-top:2px;margin-bottom:16px}}
  .dot{{display:inline-block;width:9px;height:9px;border-radius:50%;background:#4CAF50;box-shadow:0 0 8px #4CAF50;animation:pulse 1.6s infinite;margin-right:6px;vertical-align:middle}}
  @keyframes pulse{{0%,100%{{opacity:1}}50%{{opacity:0.35}}}}
  .stats{{display:grid;grid-template-columns:repeat(auto-fit,minmax(160px,1fr));gap:12px;margin-bottom:16px}}
  .stat{{background:#1c1c20;border:1px solid #2a2a30;border-radius:6px;padding:10px 14px}}
  .stat .k{{color:#888;font-size:11px;text-transform:uppercase;letter-spacing:.08em}}
  .stat .v{{color:#e6b84c;font-size:22px;font-weight:600;margin-top:2px}}
  .stat .s{{color:#bbb;font-size:12px;margin-top:2px}}
  .bar{{background:#1c1c20;border:1px solid #2a2a30;border-radius:6px;padding:4px;margin-bottom:16px}}
  .fill{{background:linear-gradient(90deg,#4CAF50,#e6b84c);height:22px;border-radius:4px;transition:width 0.5s ease;display:flex;align-items:center;justify-content:flex-end;padding-right:10px;color:#111;font-weight:600;font-size:12px;min-width:0}}
  .last{{background:#1c1c20;border:1px solid #2a2a30;border-radius:6px;padding:10px 14px;margin-bottom:16px;font-size:13px}}
  .last .lbl{{color:#888;font-size:11px;text-transform:uppercase;letter-spacing:.08em}}
  .last .name{{color:#e6b84c;font-weight:600;margin-top:2px}}
  .last .cat{{color:#888;font-size:11px;margin-left:8px}}
  .toc{{display:flex;flex-wrap:wrap;gap:6px;margin-bottom:16px;padding:10px;background:#1c1c20;border:1px solid #2a2a30;border-radius:6px}}
  .toc a{{color:#e6b84c;text-decoration:none;font-size:11px;padding:3px 8px;background:#2a2a30;border-radius:4px;font-family:monospace}}
  .toc a:hover{{background:#3a3a40;color:#fff}}
  section.cat{{margin-top:18px}}
  section.cat h2{{margin:0 0 8px 0;padding:6px 10px;background:#2a2a30;color:#e6b84c;border-radius:4px;font-size:12px;letter-spacing:.08em;font-family:monospace}}
  .grid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(520px,1fr));gap:10px}}
  a.card{{background:#1c1c20;border:1px solid #2a2a30;border-radius:4px;overflow:hidden;font-size:10px;text-decoration:none;color:inherit;display:block}}
  a.card:hover{{border-color:#e6b84c;box-shadow:0 0 0 1px #e6b84c}}
  a.card img{{display:block;width:100%;height:auto}}
  a.card .name{{padding:3px 6px 1px;color:#eee;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}}
  a.card .pid{{padding:0 6px 3px;color:#666;font-family:monospace;font-size:9px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}}
  pre.tail{{background:#0c0c10;border:1px solid #2a2a30;border-radius:4px;padding:10px;color:#9ae48a;font-size:11px;overflow-x:auto;max-height:180px;overflow-y:auto;white-space:pre;margin:0}}
  .failed{{color:#ff6b6b;font-weight:600}}
  .ok{{color:#4CAF50}}
  .footer{{color:#555;font-size:11px;margin-top:20px;text-align:center}}
  .top{{position:fixed;bottom:16px;right:16px;background:#e6b84c;color:#111;padding:8px 14px;border-radius:20px;text-decoration:none;font-size:12px;font-weight:600;box-shadow:0 2px 10px rgba(0,0,0,.5)}}
</style>
</head><body>
<h1><span class=dot></span>Triptych Campaign — Live Status</h1>
<div class=sub>Auto-refreshes every {PAGE_REFRESH_S} s · snapshot {now_str} · click any thumbnail to open its full-size WebP in a new tab</div>

<div class=stats>
  <div class=stat><div class=k>Done</div><div class=v>{done:,}</div><div class=s>of {total:,} POIs</div></div>
  <div class=stat><div class=k>Progress</div><div class=v>{pct:.1f}%</div><div class=s>{total - done:,} remaining</div></div>
  <div class=stat><div class=k>Rate</div><div class=v>{rate:.1f}/min</div><div class=s>≈ {int(60/rate) if rate > 0 else 0} s per POI</div></div>
  <div class=stat><div class=k>ETA</div><div class=v>{eta_min}m</div><div class=s>≈ {eta_min // 60}h {eta_min % 60}m</div></div>
  <div class=stat><div class=k>Failures</div><div class=v class="{'failed' if failed else 'ok'}">{failed}</div><div class=s>{'check output-full/failed/' if failed else 'none so far'}</div></div>
</div>

<div class=bar><div class=fill style="width: {max(pct, 2.0):.1f}%">{pct:.1f}%</div></div>

<div class=last>
  <div class=lbl>Currently rendering</div>
  <div class=name>{html.escape(last_name)}<span class=cat>({html.escape(last_cat)})</span></div>
</div>

<div class=toc>{toc_html}</div>

{"".join(cat_sections)}

<h2 style="color:#e6b84c;font-size:13px;letter-spacing:.08em;margin:24px 0 8px 0;text-transform:uppercase">Log tail</h2>
<pre class=tail>{tail_block}</pre>

<div class=footer>status_writer.py · scans output-full/triptychs/ every {SNAPSHOT_INTERVAL_S} s · gallery grows as new triptychs complete</div>
<a href="#" class=top>↑ top</a>
</body></html>
"""
    (OUT_DIR / "status.html").write_text(page)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--log", type=Path, default=DEFAULT_LOG)
    ap.add_argument("--interval", type=int, default=SNAPSHOT_INTERVAL_S)
    args = ap.parse_args()

    poi_meta = load_poi_meta()
    print(f"status_writer: log={args.log}  interval={args.interval}s  poi_meta={len(poi_meta)}", flush=True)
    print(f"output: {OUT_DIR / 'status.html'}", flush=True)

    while True:
        try:
            write_status_html(poi_meta, args.log)
        except Exception as e:
            print(f"warn: {e!r}", flush=True)
        time.sleep(args.interval)


if __name__ == "__main__":
    main()
