#!/usr/bin/env python3
"""Build the Tier 1 counsel packet PDF.

Uses markdown-pdf (installed in /tmp/pdfvenv) to produce a single PDF with:
- Hierarchical bookmarks (outline) from headings
- Hyperlinked internal anchors
- Clean print-ready CSS
"""
from pathlib import Path
from markdown_pdf import MarkdownPdf, Section

TIER1_DIR = Path(__file__).parent.parent / "tier1"
OUT = Path(__file__).parent.parent / "Katrina-Counsel-Packet-Tier1-Pre-NDA.pdf"

# Section order, in presentation order.
FILES = [
    "00-cover-and-nda-request.md",
    "A1-legal-walkthrough-redacted.md",
    "A2-pricing-and-age-gate.md",
    "A3-privacy-policy-v1.md",
    "A4-terms-of-service-stub.md",
    "A5-data-safety-answers.md",
    "A6-play-store-checklist.md",
    "A7-decision-checklist.md",
    "B1-tier2-holdback-manifest.md",
    "B2-mutual-nda-template.md",
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
    color: #3b1e48;
    font-size: 22pt;
    border-bottom: 2px solid #3b1e48;
    padding-bottom: 6pt;
    margin-top: 0;
    page-break-after: avoid;
}
h2 {
    color: #3b1e48;
    font-size: 15pt;
    margin-top: 18pt;
    margin-bottom: 6pt;
    page-break-after: avoid;
}
h3 {
    color: #5a2d6a;
    font-size: 12pt;
    margin-top: 14pt;
    margin-bottom: 4pt;
    page-break-after: avoid;
}
h4 {
    color: #5a2d6a;
    font-size: 11pt;
    margin-top: 10pt;
    margin-bottom: 3pt;
    font-style: italic;
    page-break-after: avoid;
}
p { margin: 6pt 0; text-align: justify; }
li { margin: 3pt 0; }
blockquote {
    border-left: 3px solid #8b5cf6;
    padding: 3pt 0 3pt 10pt;
    margin: 8pt 0;
    color: #4a3a5a;
    font-style: italic;
}
table {
    border-collapse: collapse;
    width: 100%;
    margin: 8pt 0;
    font-size: 9.5pt;
}
th, td {
    border: 1px solid #c0b0c8;
    padding: 4pt 6pt;
    text-align: left;
    vertical-align: top;
}
th {
    background: #f3e8ff;
    color: #3b1e48;
    font-weight: bold;
}
code {
    background: #f3f0f5;
    padding: 1pt 3pt;
    border-radius: 2pt;
    font-family: 'Courier New', monospace;
    font-size: 9pt;
}
a { color: #6b2e8c; text-decoration: none; }
a:hover { text-decoration: underline; }
hr {
    border: none;
    border-top: 1px solid #c0b0c8;
    margin: 12pt 0;
}
strong { color: #3b1e48; }
"""

def main():
    pdf = MarkdownPdf(toc_level=3, optimize=True)
    pdf.meta["title"] = "Katrina's Mystic Visitors Guide — Counsel Packet Tier 1 (Pre-NDA)"
    pdf.meta["author"] = "Dean Maurice Ellis"
    pdf.meta["subject"] = "Pre-NDA counsel packet for 2026-04-20 C-corp formation + engagement meeting"
    pdf.meta["keywords"] = "counsel packet, Katrina's Mystic Visitors Guide, pre-NDA, V1, Salem"

    for fname in FILES:
        src = TIER1_DIR / fname
        md = src.read_text()
        # Force page break between sections by prepending a page-break marker.
        # markdown-pdf handles page breaks between sections automatically.
        pdf.add_section(Section(md), user_css=CSS)
        print(f"  added: {fname} ({len(md.splitlines())} lines)")

    pdf.save(str(OUT))
    print(f"\nWrote: {OUT}")
    print(f"Size: {OUT.stat().st_size:,} bytes")

if __name__ == "__main__":
    main()
