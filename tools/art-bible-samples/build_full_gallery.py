#!/usr/bin/env python3
"""Build full_set.html — review gallery for the full 124-hero h3 run.
Reads out_full/manifest.json, emits a self-contained page (data inlined; works
over file://). Grouped HIST_BLDG then WORSHIP, filter buttons, retry badges."""
import json, html
from pathlib import Path

HERE = Path(__file__).parent
m = json.load(open(HERE / "out_full" / "manifest.json"))
m.sort(key=lambda x: (x["category"], x["name"]))
nb = sum(1 for x in m if x["category"] == "HISTORICAL_BUILDINGS")
nw = sum(1 for x in m if x["category"] == "WORSHIP")
nr = sum(1 for x in m if (x.get("retries") or 0) > 0)

def card(x):
    isB = x["category"] == "HISTORICAL_BUILDINGS"
    yr = f"est. {x['year']}" if x.get("year") else "year n/a"
    rb = (f'<span class="badge retry">re-rolled ×{x["retries"]}</span>'
          if (x.get("retries") or 0) > 0 else "")
    return f'''<div class="card" data-cat="{x['category']}">
      <img loading="lazy" src="out_full/hero_{html.escape(x['id'])}.webp" alt="{html.escape(x['name'])}">
      <div class="body"><h3>{html.escape(x['name'])}</h3>
        <div class="row"><span class="badge {'bldg' if isB else 'worship'}">{'HIST_BLDG' if isB else 'WORSHIP'}</span>
          <span class="badge">{yr}</span>
          <span class="badge">marker: {'ghost badge' if isB else 'church glyph'}</span>{rb}</div>
        <div class="prompt">{html.escape(x['prompt'])}</div></div></div>'''

cards = "\n".join(card(x) for x in m)
DOC = f'''<!DOCTYPE html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Full Hero Set — 124 bespoke (h3 haunted) · S299</title>
<style>
:root{{--parchment:#EFE6D3;--cream:#F6EFE0;--ink:#0E100F;--charcoal:#2B2720;
--grey:#7C766A;--tan:#B39A72;--umber:#4A4136;--teal:#3BBBB0;--deepteal:#1E6E68;}}
*{{box-sizing:border-box;margin:0;padding:0}}
body{{background:var(--ink);color:var(--parchment);font-family:'Iowan Old Style',Palatino,Georgia,serif;line-height:1.5}}
.wrap{{max-width:1320px;margin:0 auto;padding:0 24px}}
header{{border-bottom:3px solid var(--teal);background:var(--charcoal);padding:46px 0 30px}}
header h1{{font-size:30px}} header h1 .t{{color:var(--teal)}}
header .sub{{color:var(--tan);margin-top:8px;font-size:15px}}
.meta{{margin-top:16px;display:flex;flex-wrap:wrap;gap:8px}}
.pill{{font-family:ui-monospace,monospace;font-size:11.5px;background:rgba(59,187,176,.12);
border:1px solid var(--deepteal);color:var(--teal);padding:4px 10px;border-radius:999px}}
nav{{position:sticky;top:0;z-index:20;background:rgba(14,16,15,.94);backdrop-filter:blur(6px);
border-bottom:1px solid var(--umber)}}
nav .wrap{{display:flex;gap:8px;flex-wrap:wrap;align-items:center;padding:11px 24px}}
nav .lbl{{font-family:ui-monospace,monospace;font-size:12px;color:var(--grey);margin-right:4px}}
.fbtn{{font-family:ui-monospace,monospace;font-size:12px;color:var(--parchment);background:transparent;
border:1px solid var(--umber);padding:6px 13px;border-radius:6px;cursor:pointer}}
.fbtn:hover{{border-color:var(--deepteal);color:var(--teal)}}
.fbtn.on{{background:var(--teal);color:var(--ink);border-color:var(--teal);font-weight:700}}
section{{padding:34px 0}}
.grid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(330px,1fr));gap:20px}}
.card{{background:var(--charcoal);border:1px solid var(--umber);border-radius:10px;overflow:hidden;display:flex;flex-direction:column}}
.card img{{width:100%;display:block;background:var(--parchment);aspect-ratio:2.25/1;object-fit:cover}}
.card .body{{padding:12px 14px 14px}}
.card h3{{font-size:16.5px;color:var(--cream)}}
.row{{display:flex;gap:6px;flex-wrap:wrap;margin:7px 0 9px}}
.badge{{font-family:ui-monospace,monospace;font-size:10px;padding:3px 7px;border-radius:5px;border:1px solid var(--umber);color:var(--tan)}}
.badge.bldg{{border-color:var(--deepteal);color:var(--teal)}}
.badge.worship{{border-color:var(--tan)}}
.badge.retry{{border-color:#b3724a;color:#d98a5c}}
.prompt{{font-family:ui-monospace,monospace;font-size:11px;color:var(--grey);background:var(--ink);
border:1px solid var(--umber);border-radius:6px;padding:9px 11px;line-height:1.5;white-space:pre-wrap;max-height:74px;overflow:auto}}
.count{{color:var(--tan);font-size:13px;margin:0 0 18px;font-family:ui-monospace,monospace}}
footer{{padding:30px 0 60px;color:var(--grey);font-size:13px}} footer code{{color:var(--tan);font-family:ui-monospace,monospace}}
</style></head><body>
<header><div class="wrap">
<h1>Full Hero Set — <span class="t">124 bespoke heroes</span></h1>
<div class="sub">h3 haunted-Halloween woodcut · narration-mined prompts · 2.25:1 master · OCR text-rejected</div>
<div class="meta"><span class="pill">Session 299</span><span class="pill">{nb} HIST_BLDG</span>
<span class="pill">{nw} WORSHIP</span><span class="pill">{nr} re-rolled</span>
<span class="pill">staging: out_full/</span></div>
</div></header>
<nav><div class="wrap"><span class="lbl">filter:</span>
<button class="fbtn on" data-f="all">All ({len(m)})</button>
<button class="fbtn" data-f="HISTORICAL_BUILDINGS">Hist. Buildings ({nb})</button>
<button class="fbtn" data-f="WORSHIP">Worship ({nw})</button>
<button class="fbtn" data-f="retry">Re-rolled ({nr})</button>
<a class="fbtn" href="index.html" style="text-decoration:none;margin-left:auto">← POC index</a>
</div></nav>
<section><div class="wrap">
<p class="count" id="count"></p>
<div class="grid" id="grid">
{cards}
</div></div></section>
<footer><div class="wrap">Staging only — nothing synced into app assets yet. Source <code>out_full/</code> ·
generator <code>render_hero_full.py</code> · spec <code>docs/plans/graphics-art-bible.md</code> §5.</div></footer>
<script>
const grid=document.getElementById('grid'), cnt=document.getElementById('count');
function apply(f){{
  let n=0;
  grid.querySelectorAll('.card').forEach(c=>{{
    const show = f==='all' || (f==='retry' ? c.querySelector('.badge.retry') : c.dataset.cat===f);
    c.style.display = show?'':'none'; if(show)n++;
  }});
  cnt.textContent = `showing ${{n}} of {len(m)}`;
}}
document.querySelectorAll('.fbtn[data-f]').forEach(b=>b.onclick=()=>{{
  document.querySelectorAll('.fbtn[data-f]').forEach(x=>x.classList.remove('on'));
  b.classList.add('on'); apply(b.dataset.f);
}});
apply('all');
</script></body></html>'''
(HERE / "full_set.html").write_text(DOC)
print(f"wrote full_set.html — {len(m)} cards ({nb} bldg / {nw} worship / {nr} re-rolled)")
