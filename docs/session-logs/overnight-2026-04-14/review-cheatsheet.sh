#!/usr/bin/env bash
# Post-overnight review helpers — run from docs/session-logs/overnight-2026-04-14/
LOG="${1:-logcat.log}"
echo "=== All POI ENTRY events ==="
grep -E "NARR-GEO: ENTRY:" "$LOG" | wc -l
grep -E "NARR-GEO: ENTRY:" "$LOG" | awk '{print $NF}' | sort | uniq -c | sort -rn | head -20
echo ""
echo "=== Unique POIs narrated (intro segments) ==="
grep -oE "text=You are at [^.]+" "$LOG" | sort -u
echo ""
echo "=== All 1692 newspaper dispatches that fired ==="
grep -oE 'Salem 1692 — [0-9-]+' "$LOG" | sort -u
echo ""
echo "=== Tour-start / historical-mode events ==="
grep -E "Tour started|Historical Mode ENABLED|Walk sim started" "$LOG" | tail -20
echo ""
echo "=== Any FATAL / crash / ANR ==="
grep -E "FATAL EXCEPTION|AndroidRuntime: |ANR in" "$LOG" | head -10
echo ""
echo "=== Walk sim completions ==="
grep -E "Walk complete" "$LOG" | wc -l
echo ""
echo "=== Newspaper pointer progression ==="
grep -E "HistHeadline: advance:" "$LOG" | tail -20
