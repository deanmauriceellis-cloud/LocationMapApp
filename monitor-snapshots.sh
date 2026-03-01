#!/usr/bin/env bash
#
# monitor-snapshots.sh — 30-minute snapshot monitor
# Runs alongside overnight test, captures screenshots + log analysis for morning review.
#

set -uo pipefail

APP="http://localhost:8085"
PROXY="http://10.0.0.4:3000"
MONITOR_DIR="overnight-runs/monitor-$(date +%Y-%m-%d)"
DURATION_HOURS=${1:-7}
INTERVAL_SEC=1800  # 30 minutes

mkdir -p "$MONITOR_DIR"

SUMMARY="$MONITOR_DIR/summary.md"

cat > "$SUMMARY" <<'HEADER'
# Overnight Monitor — Snapshot Summary

| # | Time | Memory | Buses | Subway | Trains | Stations | Aircraft | Webcams | POI | Errors | PASS | FAIL | WARN | Notes |
|---|------|--------|-------|--------|--------|----------|----------|---------|-----|--------|------|------|------|-------|
HEADER

snap_count=0
end_time=$(( $(date +%s) + DURATION_HOURS * 3600 ))

while [[ $(date +%s) -lt $end_time ]]; do
    snap_count=$((snap_count + 1))
    NOW=$(date '+%H:%M')
    STAMP=$(date '+%Y%m%d_%H%M%S')
    SNAP_DIR="$MONITOR_DIR/snap_${STAMP}"
    mkdir -p "$SNAP_DIR"

    echo "[$(date '+%H:%M:%S')] Snapshot #$snap_count..."

    # 1. Screenshot
    curl -s --max-time 10 "$APP/screenshot" -o "$SNAP_DIR/screen.png" 2>/dev/null
    SCREEN_SIZE=$(stat -c%s "$SNAP_DIR/screen.png" 2>/dev/null || echo 0)

    # 2. App state
    STATE=$(curl -s --max-time 5 "$APP/state" 2>/dev/null || echo '{}')
    echo "$STATE" | jq . > "$SNAP_DIR/state.json" 2>/dev/null

    # 3. Performance
    PERF=$(curl -s --max-time 5 "$APP/perf" 2>/dev/null || echo '{}')
    echo "$PERF" | jq . > "$SNAP_DIR/perf.json" 2>/dev/null

    # 4. Proxy stats
    CACHE=$(curl -s --max-time 5 "$PROXY/cache/stats" 2>/dev/null || echo '{}')
    echo "$CACHE" | jq . > "$SNAP_DIR/cache-stats.json" 2>/dev/null

    # 5. Recent logs (last 50 lines with errors/warnings)
    curl -s --max-time 5 "$APP/logs?tail=100&level=E" 2>/dev/null | jq -r '.logs[]?' > "$SNAP_DIR/error-logs.txt" 2>/dev/null
    curl -s --max-time 5 "$APP/logs?tail=50" 2>/dev/null | jq -r '.logs[]?' > "$SNAP_DIR/recent-logs.txt" 2>/dev/null

    # 6. LiveData snapshot
    curl -s --max-time 5 "$APP/livedata" 2>/dev/null | jq . > "$SNAP_DIR/livedata.json" 2>/dev/null

    # 7. Find the active test run and grab its events
    TEST_DIR=$(ls -td overnight-runs/20* 2>/dev/null | head -1)
    PASS_CT=0; FAIL_CT=0; WARN_CT=0; ERROR_CT=0; NOTES=""
    if [[ -n "$TEST_DIR" && -f "$TEST_DIR/events.log" ]]; then
        cp "$TEST_DIR/events.log" "$SNAP_DIR/test-events.log" 2>/dev/null
        PASS_CT=$(grep -c '\[PASS\]' "$TEST_DIR/events.log" 2>/dev/null || echo 0)
        FAIL_CT=$(grep -c '\[FAIL\]' "$TEST_DIR/events.log" 2>/dev/null || echo 0)
        WARN_CT=$(grep -c '\[WARN\]' "$TEST_DIR/events.log" 2>/dev/null || echo 0)
        # Last 5 notable events
        grep -E '\[PASS\]|\[FAIL\]|\[WARN\]|\[PHASE\]' "$TEST_DIR/events.log" | tail -5 > "$SNAP_DIR/latest-events.txt" 2>/dev/null
        # Copy time-series
        cp "$TEST_DIR/time-series.csv" "$SNAP_DIR/time-series.csv" 2>/dev/null
    fi

    # Extract metrics
    MEM=$(echo "$PERF" | jq -r '.memory.used_mb // "?"' 2>/dev/null)
    BUSES=$(echo "$STATE" | jq -r '.markers.buses // 0' 2>/dev/null)
    SUBWAY=$(echo "$STATE" | jq -r '.markers.subway // 0' 2>/dev/null)
    TRAINS=$(echo "$STATE" | jq -r '.markers.trains // 0' 2>/dev/null)
    STATIONS=$(echo "$STATE" | jq -r '.markers.stations // 0' 2>/dev/null)
    AIRCRAFT=$(echo "$STATE" | jq -r '.markers.aircraft // 0' 2>/dev/null)
    WEBCAMS=$(echo "$STATE" | jq -r '.markers.webcams // 0' 2>/dev/null)
    POI=$(echo "$STATE" | jq -r '.markers.poi // 0' 2>/dev/null)
    ERROR_CT=$(echo "$CACHE" | jq -r '.errors // 0' 2>/dev/null)
    OPENSKY=$(echo "$CACHE" | jq -r '.opensky.remaining // "?"' 2>/dev/null)

    # Check for notable conditions
    HOUR=$(date +%-H)
    if [[ "$HOUR" -ge 5 && "$HOUR" -le 6 && "$BUSES" -gt 0 ]]; then
        NOTES="Transit waking up"
    elif [[ "$HOUR" -ge 7 && "$BUSES" -gt 50 ]]; then
        NOTES="Full service"
    elif [[ "$HOUR" -le 4 ]]; then
        NOTES="Overnight"
    fi
    if [[ "$FAIL_CT" -gt 0 ]]; then
        NOTES="$NOTES **${FAIL_CT} FAILS**"
    fi
    if [[ "$SCREEN_SIZE" -lt 1000 ]]; then
        NOTES="$NOTES SCREENSHOT FAIL"
    fi

    # Write analysis file
    cat > "$SNAP_DIR/analysis.md" <<ANALYSIS
# Snapshot #$snap_count — $NOW

## App State
- Memory: ${MEM}MB
- Markers: POI=$POI, Buses=$BUSES, Subway=$SUBWAY, Trains=$TRAINS, Stations=$STATIONS, Aircraft=$AIRCRAFT, Webcams=$WEBCAMS
- Follow: $(echo "$STATE" | jq -r 'if .followedVehicle then "Vehicle: " + .followedVehicle elif .followedAircraft then "Aircraft: " + .followedAircraft else "None" end' 2>/dev/null)

## Test Progress
- PASS: $PASS_CT | FAIL: $FAIL_CT | WARN: $WARN_CT
- OpenSky remaining: $OPENSKY

## Latest Events
$(cat "$SNAP_DIR/latest-events.txt" 2>/dev/null || echo "No events yet")

## Error Logs (count: $(wc -l < "$SNAP_DIR/error-logs.txt" 2>/dev/null || echo 0))
$(head -10 "$SNAP_DIR/error-logs.txt" 2>/dev/null || echo "None")

## Screenshot
Size: $SCREEN_SIZE bytes — $(if [[ "$SCREEN_SIZE" -gt 1000 ]]; then echo "OK"; else echo "MISSING"; fi)
ANALYSIS

    # Append to summary table
    echo "| $snap_count | $NOW | ${MEM}MB | $BUSES | $SUBWAY | $TRAINS | $STATIONS | $AIRCRAFT | $WEBCAMS | $POI | $ERROR_CT | $PASS_CT | $FAIL_CT | $WARN_CT | $NOTES |" >> "$SUMMARY"

    echo "[$(date '+%H:%M:%S')] Snap #$snap_count saved: ${MEM}MB mem, ${BUSES}b/${SUBWAY}s/${TRAINS}t, P${PASS_CT}/F${FAIL_CT}/W${WARN_CT}"

    # Sleep until next interval
    sleep $INTERVAL_SEC
done

echo "" >> "$SUMMARY"
echo "Monitor completed at $(date '+%Y-%m-%d %H:%M:%S') — $snap_count snapshots taken." >> "$SUMMARY"
echo "[$(date '+%H:%M:%S')] Monitor complete — $snap_count snapshots in $MONITOR_DIR/"
