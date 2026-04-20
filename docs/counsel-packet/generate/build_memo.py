#!/usr/bin/env python3
"""Build the operator prep memo PDF (not for counsel)."""
from pathlib import Path
import re
from markdown_pdf import MarkdownPdf, Section

SRC = Path(__file__).parent.parent / "OPERATOR-PREP-MEMO.md"
OUT = Path(__file__).parent.parent / "Operator-Prep-Memo.pdf"

CSS = """
@page { size: Letter; margin: 0.85in 0.85in 0.85in 0.85in; }
body {
    font-family: Georgia, 'Liberation Serif', serif;
    font-size: 10.5pt;
    line-height: 1.45;
    color: #1a1a1a;
}
h1 {
    color: #8b2e1e;
    font-size: 20pt;
    border-bottom: 2px solid #8b2e1e;
    padding-bottom: 6pt;
    margin-top: 0;
}
h2 {
    color: #8b2e1e;
    font-size: 14pt;
    margin-top: 16pt;
    margin-bottom: 6pt;
}
h3 {
    color: #b04a2e;
    font-size: 11.5pt;
    margin-top: 12pt;
    margin-bottom: 4pt;
}
p { margin: 6pt 0; text-align: justify; }
li { margin: 3pt 0; }
blockquote {
    border-left: 3px solid #f06040;
    padding: 3pt 0 3pt 10pt;
    margin: 8pt 0;
    font-style: italic;
}
table {
    border-collapse: collapse;
    width: 100%;
    margin: 8pt 0;
    font-size: 9.5pt;
}
th, td {
    border: 1px solid #d0b0a0;
    padding: 4pt 6pt;
    vertical-align: top;
}
th { background: #fde0d0; color: #8b2e1e; font-weight: bold; }
code {
    background: #f5efe8;
    padding: 1pt 3pt;
    font-family: 'Courier New', monospace;
    font-size: 9pt;
}
a { color: #8b2e1e; text-decoration: none; }
hr { border: none; border-top: 1px solid #d0b0a0; margin: 12pt 0; }
strong { color: #8b2e1e; }
"""

def strip_dangling_anchors(md: str) -> str:
    defined = set(re.findall(r'<a\s+(?:name|id)="([^"]+)"', md))
    def fix(m):
        return m.group(0) if m.group(2) in defined else m.group(1)
    return re.sub(r'\[([^\]]+)\]\(#([^)]+)\)', fix, md)

def main():
    pdf = MarkdownPdf(toc_level=3, optimize=True)
    pdf.meta["title"] = "Operator Prep Memo — Counsel Meeting 2026-04-20"
    pdf.meta["author"] = "Dean Maurice Ellis"
    pdf.meta["subject"] = "Operator-facing preparation notes. Not for counsel."
    md = strip_dangling_anchors(SRC.read_text())
    pdf.add_section(Section(md), user_css=CSS)
    pdf.save(str(OUT))
    print(f"Wrote: {OUT}")
    print(f"Size: {OUT.stat().st_size:,} bytes")

if __name__ == "__main__":
    main()
