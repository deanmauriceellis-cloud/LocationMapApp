#!/usr/bin/env python3
"""
generate-poi-inventory-pdf.py

Reads ALL POI data from the bundled SQLite content database
(`salem-content/salem_content.db`) and produces a single PDF document
with every field of every POI fully written out, organized by
category and subcategory.

Why this script exists: the operator needs a paper-style inspection
document of every POI in the dataset. Useful for review, dedup
hunting, content audits, and pre-migration sanity checks.

Source of truth: the bundled content DB is the single source for
this document. tour_pois (45 rows) and salem_businesses (861 rows)
live ONLY in the bundled DB at the moment. narration_points (817 rows)
is also in PostgreSQL (814 rows after S98 import), but the bundled
DB is the more inclusive copy.

Output: docs/poi-inventory-YYYY-MM-DD.pdf (gitignored).

Run: tools/.poi-pdf-venv/bin/python tools/generate-poi-inventory-pdf.py
"""

import sqlite3
import json
import os
import sys
from datetime import datetime

from reportlab.lib import colors
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle,
    KeepTogether,
)
from reportlab.lib.enums import TA_LEFT


# ─── Paths ───────────────────────────────────────────────────────────────────

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), os.pardir))
DB_PATH = os.path.join(REPO_ROOT, 'salem-content', 'salem_content.db')
TODAY = datetime.now().strftime('%Y-%m-%d')
OUT_PATH = os.path.join(REPO_ROOT, 'docs', f'poi-inventory-{TODAY}.pdf')


# ─── Field display helpers ───────────────────────────────────────────────────

def fmt_value(v):
    """Render a SQLite cell value as a printable string. NULL → '—'."""
    if v is None:
        return '—'
    if isinstance(v, (int, float)):
        # Long Unix-millis timestamps → ISO date for readability
        if isinstance(v, int) and v > 1_000_000_000_000:
            try:
                return f"{v} ({datetime.fromtimestamp(v / 1000).strftime('%Y-%m-%d %H:%M:%S')})"
            except (ValueError, OSError):
                return str(v)
        return str(v)
    if isinstance(v, str):
        # Try to pretty-print embedded JSON
        s = v.strip()
        if (s.startswith('[') and s.endswith(']')) or (s.startswith('{') and s.endswith('}')):
            try:
                parsed = json.loads(s)
                return json.dumps(parsed, indent=2, ensure_ascii=False)
            except (ValueError, TypeError):
                pass
        return v
    return str(v)


def escape_for_paragraph(s):
    """Escape text for ReportLab Paragraph (XML-like markup)."""
    return (str(s)
            .replace('&', '&amp;')
            .replace('<', '&lt;')
            .replace('>', '&gt;'))


# ─── Stylesheet ──────────────────────────────────────────────────────────────

styles = getSampleStyleSheet()

styles.add(ParagraphStyle(
    name='SectionH1',
    parent=styles['Heading1'],
    fontSize=22,
    leading=26,
    spaceBefore=24,
    spaceAfter=12,
    textColor=colors.HexColor('#1A237E'),
))

styles.add(ParagraphStyle(
    name='CategoryH2',
    parent=styles['Heading2'],
    fontSize=16,
    leading=20,
    spaceBefore=18,
    spaceAfter=8,
    textColor=colors.HexColor('#283593'),
))

styles.add(ParagraphStyle(
    name='SubcategoryH3',
    parent=styles['Heading3'],
    fontSize=13,
    leading=16,
    spaceBefore=10,
    spaceAfter=6,
    textColor=colors.HexColor('#3949AB'),
))

styles.add(ParagraphStyle(
    name='POITitle',
    parent=styles['Heading4'],
    fontSize=11,
    leading=14,
    spaceBefore=8,
    spaceAfter=4,
    textColor=colors.HexColor('#000000'),
    backColor=colors.HexColor('#E8EAF6'),
    borderPadding=4,
))

styles.add(ParagraphStyle(
    name='FieldKey',
    parent=styles['Normal'],
    fontSize=8,
    leading=10,
    textColor=colors.HexColor('#555555'),
    fontName='Helvetica-Bold',
))

styles.add(ParagraphStyle(
    name='FieldValue',
    parent=styles['Normal'],
    fontSize=8,
    leading=10,
    textColor=colors.HexColor('#000000'),
    fontName='Helvetica',
))

styles.add(ParagraphStyle(
    name='Counter',
    parent=styles['Normal'],
    fontSize=9,
    textColor=colors.HexColor('#666666'),
    spaceAfter=10,
))


# ─── POI rendering ───────────────────────────────────────────────────────────

# Layout constants — letter is 8.5" wide, 0.6" margins on each side leaves 7.3"
KEY_COL_WIDTH = 1.7 * inch
VALUE_COL_WIDTH = 5.4 * inch
TABLE_LINE_COLOR = colors.HexColor('#CCCCCC')


def render_poi(row, columns, kind_label):
    """
    Build a flowable group for a single POI: title bar + 2-column field table.
    """
    elems = []

    title_text = (
        f"<b>{escape_for_paragraph(row['name'] or '(unnamed)')}</b> "
        f"<font size=8 color='#666666'>[{kind_label}] id: {escape_for_paragraph(row['id'] or '?')}</font>"
    )
    elems.append(Paragraph(title_text, styles['POITitle']))

    table_data = []
    for col in columns:
        key_para = Paragraph(escape_for_paragraph(col), styles['FieldKey'])
        value_text = fmt_value(row[col])
        # Long text needs to be a Paragraph so it wraps inside the cell
        value_para = Paragraph(escape_for_paragraph(value_text), styles['FieldValue'])
        table_data.append([key_para, value_para])

    tbl = Table(
        table_data,
        colWidths=[KEY_COL_WIDTH, VALUE_COL_WIDTH],
        hAlign='LEFT',
    )
    tbl.setStyle(TableStyle([
        ('VALIGN', (0, 0), (-1, -1), 'TOP'),
        ('LEFTPADDING', (0, 0), (-1, -1), 4),
        ('RIGHTPADDING', (0, 0), (-1, -1), 4),
        ('TOPPADDING', (0, 0), (-1, -1), 2),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 2),
        ('GRID', (0, 0), (-1, -1), 0.25, TABLE_LINE_COLOR),
        ('BACKGROUND', (0, 0), (0, -1), colors.HexColor('#F5F5F5')),
    ]))
    elems.append(tbl)
    elems.append(Spacer(1, 8))

    return elems


# ─── Section builders ────────────────────────────────────────────────────────

def build_section(db, table, columns, primary_group_col, secondary_group_col,
                  kind_label, section_title, total_filter='1=1'):
    """
    Build a complete section: title + grouped POIs (primary → secondary → entity).
    `secondary_group_col` may be None if there's no natural sub-grouping;
    in that case all POIs in a primary group appear directly under it.
    """
    elems = [Paragraph(section_title, styles['SectionH1'])]

    total_rows = db.execute(f"SELECT COUNT(*) FROM {table} WHERE {total_filter}").fetchone()[0]
    primary_count = db.execute(
        f"SELECT COUNT(DISTINCT COALESCE({primary_group_col}, '(none)')) FROM {table} WHERE {total_filter}"
    ).fetchone()[0]
    elems.append(Paragraph(
        f"{total_rows} POIs across {primary_count} {primary_group_col} buckets. "
        f"All fields are shown for every POI; '—' indicates NULL.",
        styles['Counter'],
    ))

    primary_values = [r[0] for r in db.execute(
        f"SELECT DISTINCT COALESCE({primary_group_col}, '(none)') AS g "
        f"FROM {table} WHERE {total_filter} ORDER BY g"
    ).fetchall()]

    for primary in primary_values:
        primary_label = primary if primary != '(none)' else '(no category)'
        elems.append(Paragraph(escape_for_paragraph(primary_label), styles['CategoryH2']))

        if secondary_group_col is None:
            # Flat: all POIs in this bucket
            rows = db.execute(
                f"SELECT * FROM {table} WHERE COALESCE({primary_group_col}, '(none)') = ? "
                f"AND {total_filter} ORDER BY name",
                (primary,),
            ).fetchall()
            for row in rows:
                elems.extend(render_poi(row, columns, kind_label))
        else:
            secondary_values = [r[0] for r in db.execute(
                f"SELECT DISTINCT COALESCE({secondary_group_col}, '(none)') AS g "
                f"FROM {table} WHERE COALESCE({primary_group_col}, '(none)') = ? "
                f"AND {total_filter} ORDER BY g",
                (primary,),
            ).fetchall()]

            for secondary in secondary_values:
                secondary_label = secondary if secondary != '(none)' else '(no subcategory)'
                elems.append(Paragraph(escape_for_paragraph(secondary_label), styles['SubcategoryH3']))

                rows = db.execute(
                    f"SELECT * FROM {table} "
                    f"WHERE COALESCE({primary_group_col}, '(none)') = ? "
                    f"AND COALESCE({secondary_group_col}, '(none)') = ? "
                    f"AND {total_filter} ORDER BY name",
                    (primary, secondary),
                ).fetchall()
                for row in rows:
                    elems.extend(render_poi(row, columns, kind_label))

    elems.append(PageBreak())
    return elems


def get_columns(db, table):
    return [r['name'] for r in db.execute(f"PRAGMA table_info({table})").fetchall()]


# ─── Main ────────────────────────────────────────────────────────────────────

def main():
    if not os.path.exists(DB_PATH):
        sys.exit(f"ERROR: bundled DB not found at {DB_PATH}")

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)

    db = sqlite3.connect(DB_PATH)
    db.row_factory = sqlite3.Row

    print(f"Reading from: {DB_PATH}")
    print(f"Writing to:   {OUT_PATH}")

    counts = {
        t: db.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
        for t in ('tour_pois', 'salem_businesses', 'narration_points')
    }
    print(f"Source counts: tour_pois={counts['tour_pois']}, "
          f"salem_businesses={counts['salem_businesses']}, "
          f"narration_points={counts['narration_points']}, "
          f"TOTAL={sum(counts.values())}")

    doc = SimpleDocTemplate(
        OUT_PATH,
        pagesize=letter,
        leftMargin=0.6 * inch,
        rightMargin=0.6 * inch,
        topMargin=0.6 * inch,
        bottomMargin=0.6 * inch,
        title=f'WickedSalemWitchCityTour POI Inventory ({TODAY})',
        author='LocationMapApp v1.5',
    )

    story = []

    # ─── Cover page ─────────────────────────────────────────────────────────
    story.append(Spacer(1, 1.5 * inch))
    story.append(Paragraph(
        '<b>WickedSalemWitchCityTour</b><br/>POI Inventory',
        styles['Title'],
    ))
    story.append(Spacer(1, 0.4 * inch))
    story.append(Paragraph(
        f"Generated {TODAY} from <font face='Courier'>salem-content/salem_content.db</font>",
        styles['Normal'],
    ))
    story.append(Spacer(1, 0.2 * inch))
    story.append(Paragraph(
        f"<b>{counts['tour_pois']}</b> Tour POIs • "
        f"<b>{counts['salem_businesses']}</b> Salem Businesses • "
        f"<b>{counts['narration_points']}</b> Narration Points • "
        f"<b>{sum(counts.values())}</b> total",
        styles['Normal'],
    ))
    story.append(Spacer(1, 0.4 * inch))
    story.append(Paragraph(
        'Every field of every POI is rendered below. NULL values appear as <b>—</b>. '
        'JSON-encoded fields are pretty-printed inline.',
        styles['Normal'],
    ))
    story.append(PageBreak())

    # ─── Section 1: Tour POIs ───────────────────────────────────────────────
    # tour_pois.subcategories is a JSON array — group by the first element.
    print("Building Section 1: Tour POIs...")
    cols_tour = get_columns(db, 'tour_pois')
    story.extend(build_section(
        db,
        table='tour_pois',
        columns=cols_tour,
        primary_group_col='category',
        secondary_group_col=None,  # subcategories is JSON, hard to GROUP BY natively
        kind_label='tour',
        section_title='Section 1 — Tour POIs (45 rows)',
    ))

    # ─── Section 2: Salem Businesses ────────────────────────────────────────
    print("Building Section 2: Salem Businesses...")
    cols_biz = get_columns(db, 'salem_businesses')
    story.extend(build_section(
        db,
        table='salem_businesses',
        columns=cols_biz,
        primary_group_col='business_type',
        secondary_group_col='cuisine_type',
        kind_label='business',
        section_title='Section 2 — Salem Businesses (861 rows)',
    ))

    # ─── Section 3: Narration Points ────────────────────────────────────────
    print("Building Section 3: Narration Points...")
    cols_nar = get_columns(db, 'narration_points')
    story.extend(build_section(
        db,
        table='narration_points',
        columns=cols_nar,
        primary_group_col='type',
        secondary_group_col='wave',  # Wave 1-4 is the natural sub-grouping
        kind_label='narration',
        section_title='Section 3 — Narration Points (817 rows)',
    ))

    print("Rendering PDF... (this can take a minute or two)")
    doc.build(story)

    size_mb = os.path.getsize(OUT_PATH) / (1024 * 1024)
    print(f"\n✓ Wrote {OUT_PATH} ({size_mb:.1f} MB)")


if __name__ == '__main__':
    main()
