#!/usr/bin/env python3
"""
S275 Katrina's Collection — one-shot HTML inspector for the ghost batch.

Reads manifest.json + portrait PNGs + frame overlay PNGs, emits a single self-contained
HTML file that renders the actual badge tile composition (portrait + frame overlay on top
+ caption). Toggle A↔B and color↔greyscale at the top to QA the smirk pairs and the
collected/uncollected look.

Opens with `xdg-open ~/AI-Studio/ghost-batch-v1/inspector.html` — no web server needed,
file:// URLs work because all images sit in or under the same dir.

Usage:
  python3 cache-proxy/scripts/build-ghost-gallery.py
  python3 cache-proxy/scripts/build-ghost-gallery.py --portraits <dir> --frames <dir>
"""
import argparse
import hashlib
import json
from html import escape
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SEED_FILE = REPO_ROOT / "cache-proxy" / "data" / "ghost-personas-seed.json"


def derive_frame_slug(slug: str, frame_slugs: list[str]) -> str:
    """Deterministic per-ghost frame pick when manifest pre-dates the simplified frame schema."""
    h = int(hashlib.sha1(slug.encode()).hexdigest()[:8], 16)
    return frame_slugs[h % len(frame_slugs)]


HTML_HEAD = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Katrina's Collection — Ghost Inspector (S275)</title>
<style>
  :root {
    --bg: #1a1a1f;
    --panel: #25252e;
    --text: #e8e8ea;
    --muted: #8a8a92;
    --accent: #d4a843;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0; padding: 0;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    background: var(--bg); color: var(--text);
  }
  header {
    position: sticky; top: 0; z-index: 10;
    background: var(--panel);
    padding: 14px 24px;
    border-bottom: 1px solid #000;
    display: flex; gap: 24px; align-items: center; flex-wrap: wrap;
  }
  h1 { margin: 0; font-size: 18px; font-weight: 600; }
  .stats { color: var(--muted); font-size: 13px; }
  .toolbar { display: flex; gap: 12px; margin-left: auto; }
  button {
    background: #3a3a45; color: var(--text); border: 1px solid #555;
    padding: 8px 14px; border-radius: 6px; cursor: pointer; font-size: 13px;
  }
  button.active { background: var(--accent); color: #1a1a1f; border-color: var(--accent); }
  button:hover:not(.active) { background: #46464f; }

  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 16px;
    padding: 24px;
  }
  .tile {
    background: var(--panel);
    border-radius: 8px;
    overflow: hidden;
    display: flex; flex-direction: column;
  }
  .tile .box {
    position: relative;
    width: 100%;
    aspect-ratio: 1 / 1;
    background: #0a0a0a;
  }
  .tile img.portrait, .tile img.frame {
    position: absolute; inset: 0;
    width: 100%; height: 100%;
    display: block;
  }
  .tile img.frame {
    pointer-events: none;
  }
  .tile.uncollected img.portrait {
    filter: grayscale(100%);
  }
  .tile.show-b img.portrait { opacity: 0; }
  .tile.show-b img.portrait-b { opacity: 1; }
  .tile img.portrait-b {
    position: absolute; inset: 0;
    width: 100%; height: 100%;
    display: block;
    opacity: 0;
    transition: opacity 0.15s ease;
  }
  .tile.show-b img.portrait { transition: opacity 0.15s ease; }
  .caption {
    padding: 8px 10px;
    font-size: 12px;
    border-top: 1px solid #000;
  }
  .caption .name { font-weight: 600; }
  .caption .meta { color: var(--muted); font-size: 11px; margin-top: 2px; }
  .caption .frame-slug { color: var(--accent); font-size: 10px; margin-top: 2px; }
  /* Make individual tiles clickable to flip just that one */
  .tile { cursor: pointer; user-select: none; }
</style>
</head>
<body>
<header>
  <h1>Katrina's Collection — Ghost Inspector</h1>
  <span class="stats" id="stats"></span>
  <div class="toolbar">
    <button id="toggleAB"      class="active">A (normal)</button>
    <button id="toggleColor"   class="active">Color (collected)</button>
    <button id="toggleFrame"   class="active">Frame ON</button>
  </div>
</header>
<div class="grid" id="grid"></div>
<script>
const tiles = document.querySelectorAll('.tile');
let mode = { ab: 'a', collected: true, frame: true };
function apply() {
  tiles.forEach(t => {
    t.classList.toggle('show-b', mode.ab === 'b');
    t.classList.toggle('uncollected', !mode.collected);
    const f = t.querySelector('.frame');
    if (f) f.style.display = mode.frame ? 'block' : 'none';
  });
}
document.getElementById('toggleAB').onclick = (e) => {
  mode.ab = mode.ab === 'a' ? 'b' : 'a';
  e.target.textContent = mode.ab === 'a' ? 'A (normal)' : 'B (smirk)';
  e.target.classList.toggle('active', mode.ab === 'a');
  apply();
};
document.getElementById('toggleColor').onclick = (e) => {
  mode.collected = !mode.collected;
  e.target.textContent = mode.collected ? 'Color (collected)' : 'Greyscale (uncollected)';
  e.target.classList.toggle('active', mode.collected);
  apply();
};
document.getElementById('toggleFrame').onclick = (e) => {
  mode.frame = !mode.frame;
  e.target.textContent = mode.frame ? 'Frame ON' : 'Frame OFF';
  e.target.classList.toggle('active', mode.frame);
  apply();
};
// Click a tile to flip just that one to B / back
tiles.forEach(t => {
  t.addEventListener('click', () => {
    t.classList.toggle('show-b');
  });
});
apply();
document.getElementById('stats').textContent =
  tiles.length + ' ghosts • click a tile to flip just that one • toolbar flips all';
</script>
</body>
</html>
"""


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "--portraits",
        type=Path,
        default=Path.home() / "AI-Studio" / "ghost-batch-v1",
        help="dir with ghost_<poi_id>_a/b.png + manifest.json",
    )
    ap.add_argument(
        "--frames",
        type=Path,
        default=Path.home() / "AI-Studio" / "ghost-frames-v1",
        help="dir with frame_<slug>.png overlays",
    )
    ap.add_argument(
        "--out",
        type=Path,
        default=None,
        help="output HTML path (default: <portraits>/inspector.html)",
    )
    args = ap.parse_args()

    manifest_path = args.portraits / "manifest.json"
    if not manifest_path.exists():
        raise SystemExit(f"No manifest at {manifest_path}")
    manifest = json.loads(manifest_path.read_text())
    if args.out is None:
        args.out = args.portraits / "inspector.html"

    seed = json.loads(SEED_FILE.read_text())
    frame_slugs = [f["slug"] for f in seed["frames"]]

    # Frames may be in a sibling dir; produce relative path from the HTML location.
    rel_frames = Path("..") / args.frames.name

    cards = []
    for row in manifest:
        slug = row["slug"]
        poi_name = row.get("poi_name") or slug
        prof = row.get("profession", "")
        expr_a = row.get("expression_a", "")
        expr_b = row.get("expression_b", "")
        frame_slug = row.get("frame_slug") or (row.get("frame") or {}).get("slug")
        # Manifest from the original --from-pg run pre-dates the simplified `frame`
        # schema — derive a deterministic frame slug from the ghost slug so the inspector
        # still shows what each tile WILL look like in the final composition.
        if not frame_slug:
            frame_slug = derive_frame_slug(slug, frame_slugs)
        frame_src = f"{rel_frames}/frame_{frame_slug}.png"
        frame_img = f'<img class="frame" src="{escape(frame_src)}" alt="">'
        a_src = f"{slug}_a.png"
        b_src = f"{slug}_b.png"
        cards.append(
            f'<div class="tile" data-slug="{escape(slug)}">'
            f'  <div class="box">'
            f'    <img class="portrait"   src="{escape(a_src)}" alt="A">'
            f'    <img class="portrait-b" src="{escape(b_src)}" alt="B">'
            f'    {frame_img}'
            f'  </div>'
            f'  <div class="caption">'
            f'    <div class="name">{escape(poi_name)}</div>'
            f'    <div class="meta">{escape(prof)} • A: {escape(expr_a)} → B: {escape(expr_b)}</div>'
            f'    <div class="frame-slug">{escape(frame_slug or "(no frame in manifest)")}</div>'
            f'  </div>'
            f'</div>'
        )

    html = HTML_HEAD.replace(
        '<div class="grid" id="grid"></div>',
        '<div class="grid" id="grid">\n' + "\n".join(cards) + "\n</div>",
    )
    args.out.write_text(html)
    print(f"Wrote {args.out}")
    print(f"Open with: xdg-open {args.out}")


if __name__ == "__main__":
    main()
