#!/usr/bin/env python3
"""Render a Markdown security assessment to a styled, print-ready PDF.

Usage: python3 build_pdf.py <input.md> <output.pdf>
Deps:  python-markdown + weasyprint (both already on this box).
"""
import sys
import markdown
from weasyprint import HTML

CSS = """
@page {
  size: Letter;
  margin: 22mm 18mm 20mm 18mm;
  @bottom-center {
    content: "Confidential — Destructive AI Gurus, LLC · OMEN-025 Phase 1 · page " counter(page) " of " counter(pages);
    font-size: 8pt; color: #888;
  }
}
body { font-family: "DejaVu Sans", "Helvetica", sans-serif; font-size: 10.5pt;
       line-height: 1.45; color: #1a1a1a; }
h1 { font-size: 21pt; color: #4a1d6e; border-bottom: 3px solid #4a1d6e;
     padding-bottom: 6px; margin-top: 0; }
h2 { font-size: 15pt; color: #4a1d6e; margin-top: 22px;
     border-bottom: 1px solid #c9b3dd; padding-bottom: 3px; page-break-after: avoid; }
h3 { font-size: 12pt; color: #6a3a92; margin-top: 16px; page-break-after: avoid; }
p, li { orphans: 2; widows: 2; }
strong { color: #2a0a44; }
hr { border: none; border-top: 1px solid #ddd; margin: 18px 0; }
code { font-family: "DejaVu Sans Mono", monospace; font-size: 8.8pt;
       background: #f3eef8; padding: 1px 3px; border-radius: 3px; color: #3a1a5a; }
pre { background: #f6f3fa; border: 1px solid #e0d4ee; border-radius: 5px;
      padding: 9px 11px; font-size: 8.6pt; line-height: 1.3; overflow-wrap: anywhere;
      page-break-inside: avoid; }
pre code { background: none; padding: 0; }
table { border-collapse: collapse; width: 100%; margin: 12px 0; font-size: 9pt;
        page-break-inside: avoid; }
th { background: #4a1d6e; color: #fff; text-align: left; padding: 5px 7px; }
td { border: 1px solid #d8cce6; padding: 4px 7px; vertical-align: top; }
tr:nth-child(even) td { background: #f7f3fb; }
blockquote { border-left: 4px solid #b794d4; background: #f7f3fb; margin: 12px 0;
             padding: 6px 14px; color: #3a2a4a; }
"""


def main():
    src, out = sys.argv[1], sys.argv[2]
    with open(src, encoding="utf-8") as f:
        text = f.read()
    html_body = markdown.markdown(
        text, extensions=["tables", "fenced_code", "attr_list"]
    )
    html_doc = f"<!doctype html><html><head><meta charset='utf-8'>" \
               f"<style>{CSS}</style></head><body>{html_body}</body></html>"
    HTML(string=html_doc).write_pdf(out)
    print(f"wrote {out}")


if __name__ == "__main__":
    main()
