#!/usr/bin/env python3
"""S298 — KMVG Woodcut palette card. Hex derived from the approved S298 renders
(quantized dominant tones), curated into named roles with usage discipline."""
from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

OUT = Path(__file__).parent / "out_palette"
OUT.mkdir(parents=True, exist_ok=True)

# group -> [(name, hex, role, usage)]
PALETTE = [
    ("GROUND", [
        ("Parchment",     "#EFE6D3", "paper ground / dominant field", "~45%"),
        ("Aged Cream",    "#F6EFE0", "lightest highlight on paper",   "~5%"),
    ]),
    ("STRUCTURE (the woodcut line)", [
        ("Ink Black",     "#0E100F", "outlines, the carved line",     "~20%"),
        ("Charcoal",      "#2B2720", "soft line / deep shadow fill",  "~8%"),
    ]),
    ("EARTH (muted mids)", [
        ("Weathered Grey","#7C766A", "stone, aged wood, neutral mid", "~8%"),
        ("Aged Wood Tan", "#B39A72", "warm timber / soil mid",        "~6%"),
        ("Umber",         "#4A4136", "deep brown shadow",             "~4%"),
    ]),
    ("ACCENT — the ONLY saturated hue (supernatural & focal only)", [
        ("Spectral Teal", "#3BBBB0", "ghost glow, eyes, key highlight","<5%"),
        ("Deep Teal",     "#1E6E68", "teal shadow / muted teal field", "<5%"),
    ]),
]

def font(sz):
    for p in ["/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
              "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"]:
        try: return ImageFont.truetype(p, sz)
        except Exception: pass
    return ImageFont.load_default()

W = 1100
ROW, SW, PAD, HDR = 96, 150, 28, 56
groups = PALETTE
H = PAD*2 + sum(HDR + len(items)*ROW + 16 for _,items in groups)
img = Image.new("RGB", (W, H), (0x16,0x16,0x14))
d = ImageDraw.Draw(img)
f_title=font(34); f_hdr=font(22); f_name=font(26); f_hex=font(24); f_role=font(19)

d.text((PAD, 16), "KMVG  ·  Woodcut Palette v1", font=f_title, fill=(0xEF,0xE6,0xD3))
y = PAD + 36
for gname, items in groups:
    d.text((PAD, y), gname, font=f_hdr, fill=(0x9B,0x95,0x89)); y += HDR
    for name, hx, role, usage in items:
        rgb = tuple(int(hx[i:i+2],16) for i in (1,3,5))
        d.rectangle([PAD, y, PAD+SW, y+ROW-16], fill=rgb, outline=(0,0,0))
        tx = PAD+SW+24
        d.text((tx, y+4),  name, font=f_name, fill=(0xEF,0xE6,0xD3))
        d.text((tx, y+40), f"{hx}   {usage}", font=f_hex, fill=(0xB3,0x9A,0x72))
        d.text((tx+360, y+42), role, font=f_role, fill=(0x9B,0x95,0x89))
        y += ROW
    y += 16

img.save(OUT/"palette_card.png")
print("wrote", OUT/"palette_card.png", img.size)
