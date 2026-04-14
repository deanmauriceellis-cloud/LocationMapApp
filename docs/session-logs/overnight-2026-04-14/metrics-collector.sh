#!/usr/bin/env bash
# Overnight metrics collector. Runs in a loop, samples the full logcat
# every 15 min, and appends a timestamped summary block to metrics.log.
# Also polls the debug /state endpoint and the newspaper pointer for
# drift detection.

BASE=/home/witchdoctor/Development/LocationMapApp_v1.5/docs/session-logs/overnight-2026-04-14
LOGCAT="$BASE/logcat.log"
OUT="$BASE/metrics.log"
DEVICE="HNY0CY0W"
PREV_LINES=0

while true; do
  ts=$(date '+%Y-%m-%d %H:%M:%S')
  total_lines=$(wc -l < "$LOGCAT" 2>/dev/null || echo 0)
  delta=$(( total_lines - PREV_LINES ))
  PREV_LINES=$total_lines

  {
    echo ""
    echo "================ $ts ================"
    echo "logcat: $total_lines lines (+${delta} since last sample)"
    echo ""
    echo "-- Walk sim sessions --"
    echo "  Walk sim started     : $(grep -c 'Walk sim started' "$LOGCAT" 2>/dev/null)"
    echo "  Walk complete        : $(grep -c 'Walk complete' "$LOGCAT" 2>/dev/null)"
    echo "  Walk-tour fired      : $(grep -c 'walk-tour fired' "$BASE/auto-restart.log" 2>/dev/null)"
    echo ""
    echo "-- Tour engine --"
    echo "  Tour started         : $(grep -c 'TourEngine: Tour started' "$LOGCAT" 2>/dev/null)"
    echo "  Historical Mode ON   : $(grep -c 'Historical Mode ENABLED' "$LOGCAT" 2>/dev/null)"
    echo ""
    echo "-- POI narrations --"
    echo "  ENTRY events (total) : $(grep -c 'NARR-GEO: ENTRY:' "$LOGCAT" 2>/dev/null)"
    echo "  Unique POIs entered  : $(grep 'NARR-GEO: ENTRY:' "$LOGCAT" 2>/dev/null | sed -E 's/.*ENTRY: (.*) dist=.*/\1/' | sort -u | wc -l)"
    echo "  Top 10 POIs:"
    grep 'NARR-GEO: ENTRY:' "$LOGCAT" 2>/dev/null \
      | sed -E 's/.*ENTRY: (.*) dist=.*/\1/' \
      | sort | uniq -c | sort -rn | head -10 \
      | sed 's/^/    /'
    echo ""
    echo "-- 1692 Newspaper --"
    echo "  Dispatches fired     : $(grep -c 'newspaper_1692' "$LOGCAT" 2>/dev/null)"
    echo "  Unique dates spoken  : $(grep -oE 'Salem 1692 — [0-9-]+' "$LOGCAT" 2>/dev/null | sort -u | wc -l)"
    echo "  Pointer advances     : $(grep -c 'HistHeadline: advance:' "$LOGCAT" 2>/dev/null)"
    last_advance=$(grep 'HistHeadline: advance:' "$LOGCAT" 2>/dev/null | tail -1)
    [ -n "$last_advance" ] && echo "  Last advance         : $(echo "$last_advance" | awk -F'advance:' '{print $2}' | xargs)"
    echo ""
    echo "-- Interrupts --"
    echo "  Newspaper cancelled  : $(grep -c 'cancelSegmentsWithTag(newspaper_1692).*killed' "$LOGCAT" 2>/dev/null)"
    echo "  POI-interrupted-POI  : $(grep -c 'Interrupting prior POI' "$LOGCAT" 2>/dev/null)"
    echo "  Dropped (min-hold)   : $(grep -c 'DROP ENTRY.*min-hold' "$LOGCAT" 2>/dev/null)"
    echo ""
    echo "-- Errors / crashes --"
    echo "  FATAL EXCEPTION      : $(grep -c 'FATAL EXCEPTION' "$LOGCAT" 2>/dev/null)"
    echo "  ANR                  : $(grep -c 'ANR in' "$LOGCAT" 2>/dev/null)"
    echo "  E/SalemMainActivity  : $(grep -c 'E/SalemMainActivity' "$LOGCAT" 2>/dev/null)"
    echo "  NARR-GEO errors      : $(grep -c 'E/NARR-GEO\|E/NarrationMgr' "$LOGCAT" 2>/dev/null)"
    last_fatal=$(grep 'FATAL EXCEPTION' "$LOGCAT" 2>/dev/null | tail -1)
    [ -n "$last_fatal" ] && echo "  Last FATAL           : $last_fatal"
    echo ""
    echo "-- Device state --"
    app_pid=$(adb -s "$DEVICE" shell pidof com.example.wickedsalemwitchcitytour 2>/dev/null | tr -d '\r')
    echo "  App PID              : ${app_pid:-NOT RUNNING}"
    state=$(curl -s -m 3 http://localhost:4303/state 2>/dev/null | grep -oE '"center":\{[^}]+\}' | head -1)
    echo "  Map center           : ${state:-endpoint unreachable}"
    echo ""
  } >> "$OUT"

  sleep 900  # 15 min
done
