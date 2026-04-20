#!/usr/bin/env python3
"""Build the Tier 2 counsel packet PDF (post-NDA).

Bundles: Tier 2 cover + IP register + GOVERNANCE + COMMERCIALIZATION +
DESIGN-REVIEW + future-state Privacy Policy.
"""
from pathlib import Path
import re
from markdown_pdf import MarkdownPdf, Section

def strip_dangling_anchors(md: str) -> str:
    """Strip [text](#anchor) links whose target anchor doesn't exist in the
    file as an explicit <a name="anchor"> or <a id="anchor">.
    markdown-pdf fails on dangling anchors, and Tier 2 source files rely on
    heading-slug auto-anchors that markdown-pdf does not generate.
    """
    defined = set(re.findall(r'<a\s+(?:name|id)="([^"]+)"', md))
    def fix(m):
        target = m.group(2)
        if target in defined:
            return m.group(0)  # keep
        return m.group(1)  # strip link, keep text
    return re.sub(r'\[([^\]]+)\]\(#([^)]+)\)', fix, md)

TIER2_DIR = Path(__file__).parent.parent / "tier2"
OUT = Path(__file__).parent.parent / "Katrina-Counsel-Packet-Tier2-Post-NDA.pdf"

FILES = [
    "00-tier2-cover.md",
    "T2-01-ip-register.md",
    "T2-02-governance.md",
    "T2-03-commercialization.md",
    "T2-04-design-review.md",
    "T2-05-future-privacy-policy.md",
]

CSS = """
@page { size: Letter; margin: 0.85in 0.85in 0.85in 0.85in; }
body {
    font-family: Georgia, 'Liberation Serif', serif;
    font-size: 10.5pt;
    line-height: 1.45;
    color: #1a1a1a;
}
h1 {
    color: #5a1e48;
    font-size: 22pt;
    border-bottom: 2px solid #5a1e48;
    padding-bottom: 6pt;
    margin-top: 0;
    page-break-after: avoid;
}
h2 {
    color: #5a1e48;
    font-size: 15pt;
    margin-top: 18pt;
    margin-bottom: 6pt;
    page-break-after: avoid;
}
h3 {
    color: #7a2d5a;
    font-size: 12pt;
    margin-top: 14pt;
    margin-bottom: 4pt;
    page-break-after: avoid;
}
h4 {
    color: #7a2d5a;
    font-size: 11pt;
    margin-top: 10pt;
    margin-bottom: 3pt;
    font-style: italic;
}
p { margin: 6pt 0; text-align: justify; }
li { margin: 3pt 0; }
blockquote {
    border-left: 3px solid #a14ca8;
    padding: 3pt 0 3pt 10pt;
    margin: 8pt 0;
    color: #6a4a7a;
    font-style: italic;
}
table {
    border-collapse: collapse;
    width: 100%;
    margin: 8pt 0;
    font-size: 9.5pt;
}
th, td {
    border: 1px solid #c0a0c8;
    padding: 4pt 6pt;
    text-align: left;
    vertical-align: top;
}
th {
    background: #fae0ff;
    color: #5a1e48;
    font-weight: bold;
}
code {
    background: #f3f0f5;
    padding: 1pt 3pt;
    border-radius: 2pt;
    font-family: 'Courier New', monospace;
    font-size: 9pt;
}
pre {
    background: #f6f3f8;
    padding: 6pt;
    border-left: 3px solid #a14ca8;
    font-family: 'Courier New', monospace;
    font-size: 8.5pt;
    overflow-x: auto;
    white-space: pre-wrap;
}
a { color: #7b2e8c; text-decoration: none; }
hr {
    border: none;
    border-top: 1px solid #c0a0c8;
    margin: 12pt 0;
}
strong { color: #5a1e48; }
"""

def main():
    pdf = MarkdownPdf(toc_level=3, optimize=True)
    pdf.meta["title"] = "Katrina's Mystic Visitors Guide — Counsel Packet Tier 2 (Post-NDA)"
    pdf.meta["author"] = "Dean Maurice Ellis"
    pdf.meta["subject"] = "POST-NDA ONLY — confidential IP, governance, commercial, and design material"
    pdf.meta["keywords"] = "confidential, NDA-gated, IP register, patents"

    for fname in FILES:
        src = TIER2_DIR / fname
        md = strip_dangling_anchors(src.read_text())
        pdf.add_section(Section(md), user_css=CSS)
        print(f"  added: {fname} ({len(md.splitlines())} lines)")

    pdf.save(str(OUT))
    print(f"\nWrote: {OUT}")
    print(f"Size: {OUT.stat().st_size:,} bytes")

if __name__ == "__main__":
    main()
