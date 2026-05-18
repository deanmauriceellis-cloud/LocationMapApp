#!/usr/bin/env python3
"""
S278 — review gallery for the Katrina's Collection ghost batch.

Reads ~/AI-Studio/ghost-batch-v2-si-clued/manifest.json and writes an HTML page
to the same directory ('review.html'). Each row shows:
  - A image (256px)
  - B image (256px, hover label = expression_b)
  - poi_id, poi_name, profession, hair, oddity, prop
  - a 'keep' / 'regen' / 'flag' radio set so the operator can scan and mark fast
  - a 'notes' input

Marks are stored in localStorage so refresh doesn't wipe them. A small 'Export
picks' button at the top dumps the current state as JSON to the clipboard for
hand-off into a regen script.

Run after a batch finishes:
  python3 cache-proxy/scripts/build-ghost-review-gallery.py
  open ~/AI-Studio/ghost-batch-v2-si-clued/review.html
"""
import json
from pathlib import Path

BATCH = Path.home() / "AI-Studio" / "ghost-batch-v2-si-clued"
MANIFEST = BATCH / "manifest.json"
OUT = BATCH / "review.html"


def main() -> None:
    if not MANIFEST.exists():
        raise SystemExit(f"No manifest at {MANIFEST}")

    manifest = json.loads(MANIFEST.read_text())
    manifest.sort(key=lambda r: r.get("poi_id") or r["slug"])

    rows_html = []
    for i, row in enumerate(manifest):
        slug = row["slug"]
        a_path = f"{slug}_a.png"
        b_path = f"{slug}_b.png"
        poi_id = row.get("poi_id", "—")
        poi_name = row.get("poi_name", "—")
        prof = row.get("profession") or row.get("persona", {}).get("profession", "—")
        hair = row.get("hair") or row.get("persona", {}).get("hair", "—")
        oddity = row.get("oddity") or row.get("persona", {}).get("oddity", "—")
        prop = row.get("prop") or row.get("persona", {}).get("prop", "—")
        rows_html.append(
            f"""
            <tr data-poi="{poi_id}">
              <td class="num">{i + 1}</td>
              <td><img src="{a_path}" loading="lazy"></td>
              <td><img src="{b_path}" loading="lazy"></td>
              <td>
                <div class="poi">{poi_name}</div>
                <div class="pid">{poi_id}</div>
                <div class="prof">{prof}</div>
                <div class="meta">{hair} · {oddity}</div>
                <div class="prop">{prop}</div>
              </td>
              <td class="decision">
                <label><input type="radio" name="d-{poi_id}" value="keep"> keep</label>
                <label><input type="radio" name="d-{poi_id}" value="regen"> regen</label>
                <label><input type="radio" name="d-{poi_id}" value="flag"> flag</label>
                <input type="text" class="note" placeholder="note (optional)">
              </td>
            </tr>
            """
        )

    html = f"""<!doctype html>
<html><head><meta charset="utf-8"><title>Ghost Batch Review — S278</title>
<style>
  body {{ font-family: -apple-system, system-ui, sans-serif; background: #1a1a1a; color: #e0e0e0; margin: 16px; }}
  h1 {{ font-size: 18px; margin: 0 0 12px; }}
  .toolbar {{ position: sticky; top: 0; background: #1a1a1a; padding: 8px 0; border-bottom: 1px solid #333; z-index: 10; }}
  .toolbar button {{ background: #2a4; color: #fff; border: 0; padding: 8px 16px; margin-right: 8px; border-radius: 4px; cursor: pointer; }}
  .toolbar button.alt {{ background: #444; }}
  .toolbar .count {{ display: inline-block; margin-left: 16px; opacity: 0.7; }}
  table {{ border-collapse: collapse; width: 100%; }}
  td {{ padding: 6px 10px; border-bottom: 1px solid #2a2a2a; vertical-align: top; }}
  img {{ width: 192px; height: 192px; object-fit: cover; border-radius: 6px; }}
  td.num {{ width: 28px; opacity: 0.5; font-variant-numeric: tabular-nums; }}
  .poi {{ font-weight: 600; }}
  .pid {{ opacity: 0.5; font-family: monospace; font-size: 11px; }}
  .prof {{ color: #a4d; margin-top: 4px; }}
  .meta {{ opacity: 0.6; font-size: 12px; margin-top: 2px; }}
  .prop {{ opacity: 0.7; font-size: 12px; margin-top: 4px; font-style: italic; }}
  td.decision {{ width: 220px; }}
  .decision label {{ display: block; padding: 2px 0; cursor: pointer; }}
  .decision input[type=text] {{ width: 100%; margin-top: 4px; background: #2a2a2a; color: #eee; border: 1px solid #444; padding: 4px; border-radius: 3px; }}
  tr.is-keep {{ background: rgba(46, 160, 64, 0.08); }}
  tr.is-regen {{ background: rgba(200, 50, 50, 0.12); }}
  tr.is-flag {{ background: rgba(220, 180, 0, 0.10); }}
</style></head>
<body>
<h1>Katrina's Collection — Ghost Batch v2 Review (S278)</h1>
<div class="toolbar">
  <button onclick="exportPicks()">📋 Copy picks to clipboard</button>
  <button class="alt" onclick="clearAll()">Reset</button>
  <span class="count" id="count"></span>
</div>
<table>
<tr><th></th><th>A</th><th>B (smirk)</th><th>persona</th><th>decision</th></tr>
{''.join(rows_html)}
</table>
<script>
const KEY = 'ghost-review-v2';
function load() {{
  const s = JSON.parse(localStorage.getItem(KEY) || '{{}}');
  for (const [poi, v] of Object.entries(s)) {{
    const tr = document.querySelector(`tr[data-poi="${{poi}}"]`);
    if (!tr) continue;
    if (v.d) {{
      const radio = tr.querySelector(`input[value="${{v.d}}"]`);
      if (radio) radio.checked = true;
      tr.className = 'is-' + v.d;
    }}
    if (v.n) tr.querySelector('.note').value = v.n;
  }}
  updateCount();
}}
function save() {{
  const s = {{}};
  for (const tr of document.querySelectorAll('tr[data-poi]')) {{
    const poi = tr.dataset.poi;
    const checked = tr.querySelector('input[type=radio]:checked');
    const note = tr.querySelector('.note').value;
    if (checked || note) s[poi] = {{ d: checked ? checked.value : null, n: note || null }};
  }}
  localStorage.setItem(KEY, JSON.stringify(s));
  updateCount();
}}
function updateCount() {{
  const s = JSON.parse(localStorage.getItem(KEY) || '{{}}');
  const keep = Object.values(s).filter(v => v.d === 'keep').length;
  const regen = Object.values(s).filter(v => v.d === 'regen').length;
  const flag = Object.values(s).filter(v => v.d === 'flag').length;
  document.getElementById('count').textContent = `keep ${{keep}} · regen ${{regen}} · flag ${{flag}}`;
}}
document.addEventListener('change', e => {{
  if (e.target.matches('input[type=radio]')) {{
    e.target.closest('tr').className = 'is-' + e.target.value;
  }}
  save();
}});
document.addEventListener('input', e => {{ if (e.target.matches('.note')) save(); }});
function exportPicks() {{
  const s = JSON.parse(localStorage.getItem(KEY) || '{{}}');
  const out = {{ keep: [], regen: [], flag: [] }};
  for (const [poi, v] of Object.entries(s)) {{
    if (v.d) out[v.d].push({{ poi_id: poi, note: v.n || '' }});
  }}
  navigator.clipboard.writeText(JSON.stringify(out, null, 2));
  alert(`Copied! keep:${{out.keep.length}} regen:${{out.regen.length}} flag:${{out.flag.length}}`);
}}
function clearAll() {{ if (confirm('Reset all marks?')) {{ localStorage.removeItem(KEY); location.reload(); }} }}
load();
</script>
</body></html>
"""
    OUT.write_text(html)
    print(f"[gallery] wrote {OUT}  ({len(manifest)} rows)")


if __name__ == "__main__":
    main()
