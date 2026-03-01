#!/usr/bin/env bash
#
# test-app.sh — Automated test suite for LocationMapApp debug API
#
# Usage:
#   ./test-app.sh              # Run all tests
#   ./test-app.sh --skip-setup # Skip adb forward + app launch (already running)
#   ./test-app.sh --suite X    # Run only suite X (core, buses, trains, subway, stations, bus-stops, toggles, aircraft, webcams, metar)
#
set -euo pipefail

# ── Config ────────────────────────────────────────────────────────────────────
BASE="http://localhost:8085"
PACKAGE="com.example.locationmapapp"
BOSTON_LAT=42.3601
BOSTON_LON=-71.0589
PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0
SKIP_COUNT=0
FAILURES=()
WARNINGS=()

# ── Colors ────────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'  # No color
BOLD='\033[1m'

# ── Helpers ───────────────────────────────────────────────────────────────────
pass() {
    PASS_COUNT=$((PASS_COUNT + 1))
    echo -e "${GREEN}[PASS]${NC} $1"
}

fail() {
    FAIL_COUNT=$((FAIL_COUNT + 1))
    FAILURES+=("$1")
    echo -e "${RED}[FAIL]${NC} $1"
}

warn() {
    WARN_COUNT=$((WARN_COUNT + 1))
    WARNINGS+=("$1")
    echo -e "${YELLOW}[WARN]${NC} $1"
}

skip() {
    SKIP_COUNT=$((SKIP_COUNT + 1))
    echo -e "${CYAN}[SKIP]${NC} $1"
}

suite_header() {
    echo ""
    echo -e "${BOLD}━━━ $1 ━━━${NC}"
}

# curl wrapper: returns body, fails silently on connection errors
api() {
    curl -s --max-time 10 "$BASE$1" 2>/dev/null || echo '{"_error":"connection_failed"}'
}

# jq helper: extract field, return empty string on failure
jqf() {
    echo "$1" | jq -r "$2" 2>/dev/null || echo ""
}

# Wait for debug server to be ready (up to 30s)
wait_for_server() {
    echo "Waiting for debug server on port 8085..."
    for i in $(seq 1 30); do
        if curl -s --max-time 2 "$BASE/" >/dev/null 2>&1; then
            echo "Debug server is ready."
            return 0
        fi
        sleep 1
    done
    echo "ERROR: Debug server not reachable after 30s"
    exit 1
}

# ── Parse args ────────────────────────────────────────────────────────────────
SKIP_SETUP=false
SUITE_FILTER=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-setup) SKIP_SETUP=true; shift ;;
        --suite)      SUITE_FILTER="$2"; shift 2 ;;
        *)            echo "Unknown arg: $1"; exit 1 ;;
    esac
done

should_run() {
    [[ -z "$SUITE_FILTER" ]] || [[ "$SUITE_FILTER" == "$1" ]]
}

# ── Setup ─────────────────────────────────────────────────────────────────────
suite_header "Setup"

if [[ "$SKIP_SETUP" == "false" ]]; then
    echo "Setting up adb forward..."
    adb forward tcp:8085 tcp:8085 2>/dev/null || true

    # Check if app is running
    if ! adb shell pidof "$PACKAGE" >/dev/null 2>&1; then
        echo "Launching app..."
        adb shell am start -n "$PACKAGE/.ui.MainActivity" 2>/dev/null || true
        sleep 3
    else
        echo "App already running."
    fi
fi

wait_for_server

# ══════════════════════════════════════════════════════════════════════════════
# TEST SUITE: Core State
# ══════════════════════════════════════════════════════════════════════════════
if should_run "core"; then
    suite_header "Core State"

    # T01: Debug server responds
    RESP=$(api "/")
    ENDPOINT_COUNT=$(jqf "$RESP" '.endpoints | length')
    if [[ "$ENDPOINT_COUNT" -ge 19 ]]; then
        pass "T01: Debug server responds — $ENDPOINT_COUNT endpoints listed"
    else
        fail "T01: Debug server responds — got $ENDPOINT_COUNT endpoints (expected >= 19)"
    fi

    # T02: Map can be positioned
    RESP=$(api "/map?lat=$BOSTON_LAT&lon=$BOSTON_LON&zoom=14")
    STATUS=$(jqf "$RESP" '.status')
    if [[ "$STATUS" == "ok" ]]; then
        pass "T02: Map positioned to Boston (42.36, -71.06, zoom 14)"
    else
        fail "T02: Map positioning failed — $RESP"
    fi
    sleep 1  # Let map settle

    # T03: State endpoint returns valid data
    RESP=$(api "/state")
    ZOOM=$(jqf "$RESP" '.zoom')
    if [[ -n "$ZOOM" && "$ZOOM" != "null" ]]; then
        CENTER_LAT=$(jqf "$RESP" '.center.lat')
        CENTER_LON=$(jqf "$RESP" '.center.lon')
        pass "T03: State endpoint — center=($CENTER_LAT, $CENTER_LON) zoom=$ZOOM"
    else
        fail "T03: State endpoint returned invalid data"
    fi
fi

# ══════════════════════════════════════════════════════════════════════════════
# TEST SUITE: MBTA Buses
# ══════════════════════════════════════════════════════════════════════════════
if should_run "buses"; then
    suite_header "MBTA Buses"

    # Enable buses and refresh
    api "/toggle?pref=mbta_buses_on&value=true" >/dev/null
    api "/refresh?layer=buses" >/dev/null
    sleep 3  # Wait for API response

    # T10: Bus count > 0
    RESP=$(api "/vehicles?type=buses")
    BUS_TOTAL=$(jqf "$RESP" '.total')
    if [[ "$BUS_TOTAL" -gt 0 ]]; then
        pass "T10: Bus vehicles loaded — $BUS_TOTAL buses"
    else
        fail "T10: Bus vehicles — count is $BUS_TOTAL (expected > 0)"
    fi

    # T11: Bus markers have title format "Bus {label} — {route}"
    RESP=$(api "/markers?type=buses&limit=20")
    MARKER_COUNT=$(jqf "$RESP" '.total')
    TITLED=$(echo "$RESP" | jq '[.markers[] | select(.title | test("^Bus "))] | length' 2>/dev/null || echo 0)
    if [[ "$MARKER_COUNT" -gt 0 && "$TITLED" -gt 0 ]]; then
        pass "T11: Bus marker titles — $TITLED/$MARKER_COUNT have 'Bus {label}' format"
    elif [[ "$MARKER_COUNT" -eq 0 ]]; then
        warn "T11: No bus markers on map (buses may not have loaded yet)"
    else
        fail "T11: Bus marker titles — $TITLED/$MARKER_COUNT have correct format"
    fi

    # T12: Bus vehicle data has headsign populated
    if [[ "$BUS_TOTAL" -gt 0 ]]; then
        RESP=$(api "/vehicles?type=buses&limit=500")
        WITH_HEADSIGN=$(echo "$RESP" | jq '[.vehicles[] | select(.headsign != null and .headsign != "")] | length' 2>/dev/null || echo 0)
        PCT=$((WITH_HEADSIGN * 100 / BUS_TOTAL))
        if [[ "$PCT" -ge 50 ]]; then
            pass "T12: Bus headsign populated — $WITH_HEADSIGN/$BUS_TOTAL ($PCT%)"
        else
            warn "T12: Bus headsign — only $WITH_HEADSIGN/$BUS_TOTAL ($PCT%) have headsign"
        fi
    else
        skip "T12: Bus headsign — no buses loaded"
    fi

    # T13: Bus vehicle data has stopName populated
    if [[ "$BUS_TOTAL" -gt 0 ]]; then
        WITH_STOP=$(echo "$RESP" | jq '[.vehicles[] | select(.stopName != null and .stopName != "")] | length' 2>/dev/null || echo 0)
        PCT=$((WITH_STOP * 100 / BUS_TOTAL))
        if [[ "$PCT" -ge 50 ]]; then
            pass "T13: Bus stopName populated — $WITH_STOP/$BUS_TOTAL ($PCT%)"
        else
            warn "T13: Bus stopName — only $WITH_STOP/$BUS_TOTAL ($PCT%) have stopName"
        fi
    else
        skip "T13: Bus stopName — no buses loaded"
    fi

    # T14: Bus vehicle data has tripId
    if [[ "$BUS_TOTAL" -gt 0 ]]; then
        WITH_TRIP=$(echo "$RESP" | jq '[.vehicles[] | select(.tripId != null and .tripId != "")] | length' 2>/dev/null || echo 0)
        PCT=$((WITH_TRIP * 100 / BUS_TOTAL))
        if [[ "$PCT" -ge 80 ]]; then
            pass "T14: Bus tripId populated — $WITH_TRIP/$BUS_TOTAL ($PCT%)"
        elif [[ "$PCT" -ge 50 ]]; then
            warn "T14: Bus tripId — $WITH_TRIP/$BUS_TOTAL ($PCT%) (expected > 80%)"
        else
            fail "T14: Bus tripId — only $WITH_TRIP/$BUS_TOTAL ($PCT%) (expected > 80%)"
        fi
    else
        skip "T14: Bus tripId — no buses loaded"
    fi

    # T15: Nearest bus to Boston coords
    RESP=$(api "/markers/nearest?lat=$BOSTON_LAT&lon=$BOSTON_LON&type=buses&limit=1")
    NEAREST_DIST=$(echo "$RESP" | jq '.nearest[0].distance_m // empty' 2>/dev/null || echo "")
    if [[ -n "$NEAREST_DIST" ]]; then
        DIST_KM=$(echo "$NEAREST_DIST" | awk '{printf "%.1f", $1/1000}')
        pass "T15: Nearest bus to Boston — ${DIST_KM}km away"
    elif [[ "$MARKER_COUNT" -eq 0 ]]; then
        warn "T15: No bus markers to search"
    else
        fail "T15: Could not find nearest bus marker"
    fi
fi

# ══════════════════════════════════════════════════════════════════════════════
# TEST SUITE: MBTA Trains
# ══════════════════════════════════════════════════════════════════════════════
if should_run "trains"; then
    suite_header "MBTA Trains"

    api "/toggle?pref=mbta_trains_on&value=true" >/dev/null
    api "/refresh?layer=trains" >/dev/null
    sleep 3

    # T20: Train count > 0
    RESP=$(api "/vehicles?type=trains")
    TRAIN_TOTAL=$(jqf "$RESP" '.total')
    if [[ "$TRAIN_TOTAL" -gt 0 ]]; then
        pass "T20: Train vehicles loaded — $TRAIN_TOTAL trains"
    else
        warn "T20: Train vehicles — count is $TRAIN_TOTAL (trains may not be running)"
    fi

    # T21: Train vehicle data has headsign
    if [[ "$TRAIN_TOTAL" -gt 0 ]]; then
        WITH_HEADSIGN=$(echo "$RESP" | jq '[.vehicles[] | select(.headsign != null and .headsign != "")] | length' 2>/dev/null || echo 0)
        PCT=$((WITH_HEADSIGN * 100 / TRAIN_TOTAL))
        if [[ "$PCT" -ge 50 ]]; then
            pass "T21: Train headsign populated — $WITH_HEADSIGN/$TRAIN_TOTAL ($PCT%)"
        else
            warn "T21: Train headsign — only $WITH_HEADSIGN/$TRAIN_TOTAL ($PCT%)"
        fi
    else
        skip "T21: Train headsign — no trains loaded"
    fi

    # T22: Train markers have correct title format
    RESP=$(api "/markers?type=trains&limit=20")
    TRAIN_MARKERS=$(jqf "$RESP" '.total')
    if [[ "$TRAIN_MARKERS" -gt 0 ]]; then
        TITLED=$(echo "$RESP" | jq '[.markers[] | select(.title | test("^Train "))] | length' 2>/dev/null || echo 0)
        pass "T22: Train marker titles — $TITLED/$TRAIN_MARKERS have 'Train {label}' format"
    else
        skip "T22: Train markers — none on map"
    fi
fi

# ══════════════════════════════════════════════════════════════════════════════
# TEST SUITE: MBTA Subway
# ══════════════════════════════════════════════════════════════════════════════
if should_run "subway"; then
    suite_header "MBTA Subway"

    api "/toggle?pref=mbta_subway_on&value=true" >/dev/null
    api "/refresh?layer=subway" >/dev/null
    sleep 3

    # T30: Subway count > 0
    RESP=$(api "/vehicles?type=subway")
    SUBWAY_TOTAL=$(jqf "$RESP" '.total')
    if [[ "$SUBWAY_TOTAL" -gt 0 ]]; then
        pass "T30: Subway vehicles loaded — $SUBWAY_TOTAL"
    else
        warn "T30: Subway vehicles — count is $SUBWAY_TOTAL (subway may not be running)"
    fi

    # T31: Subway headsign
    if [[ "$SUBWAY_TOTAL" -gt 0 ]]; then
        WITH_HEADSIGN=$(echo "$RESP" | jq '[.vehicles[] | select(.headsign != null and .headsign != "")] | length' 2>/dev/null || echo 0)
        PCT=$((WITH_HEADSIGN * 100 / SUBWAY_TOTAL))
        if [[ "$PCT" -ge 50 ]]; then
            pass "T31: Subway headsign populated — $WITH_HEADSIGN/$SUBWAY_TOTAL ($PCT%)"
        else
            warn "T31: Subway headsign — only $WITH_HEADSIGN/$SUBWAY_TOTAL ($PCT%)"
        fi
    else
        skip "T31: Subway headsign — no subway loaded"
    fi
fi

# ══════════════════════════════════════════════════════════════════════════════
# TEST SUITE: Stations
# ══════════════════════════════════════════════════════════════════════════════
if should_run "stations"; then
    suite_header "Stations"

    api "/toggle?pref=mbta_stations_on&value=true" >/dev/null
    api "/refresh?layer=stations" >/dev/null
    sleep 3

    # T40: Station count > 100
    RESP=$(api "/stations?limit=1")
    STATION_TOTAL=$(jqf "$RESP" '.total')
    if [[ "$STATION_TOTAL" -gt 100 ]]; then
        pass "T40: Stations loaded — $STATION_TOTAL stations"
    elif [[ "$STATION_TOTAL" -gt 0 ]]; then
        warn "T40: Stations — only $STATION_TOTAL (expected > 100)"
    else
        fail "T40: Stations — count is 0"
    fi

    # T41: Station data has routeIds
    if [[ "$STATION_TOTAL" -gt 0 ]]; then
        RESP=$(api "/stations?limit=10")
        WITH_ROUTES=$(echo "$RESP" | jq '[.stations[] | select(.routeIds | length > 0)] | length' 2>/dev/null || echo 0)
        RETURNED=$(jqf "$RESP" '.returned')
        if [[ "$WITH_ROUTES" -eq "$RETURNED" ]]; then
            pass "T41: Station routeIds — all $WITH_ROUTES/$RETURNED have routes"
        else
            fail "T41: Station routeIds — only $WITH_ROUTES/$RETURNED have routes"
        fi
    else
        skip "T41: Station routeIds — no stations loaded"
    fi

    # T42: Search station by name
    RESP=$(api "/stations?q=Park%20Street")
    FOUND=$(jqf "$RESP" '.total')
    if [[ "$FOUND" -gt 0 ]]; then
        FIRST_NAME=$(jqf "$RESP" '.stations[0].name')
        pass "T42: Station search 'Park Street' — found $FOUND, first: $FIRST_NAME"
    else
        warn "T42: Station search 'Park Street' — no results"
    fi
fi

# ══════════════════════════════════════════════════════════════════════════════
# TEST SUITE: Bus Stops
# ══════════════════════════════════════════════════════════════════════════════
if should_run "bus-stops"; then
    suite_header "Bus Stops"

    api "/toggle?pref=mbta_bus_stops_on&value=true" >/dev/null
    api "/refresh?layer=bus_stops" >/dev/null
    sleep 3

    # T50: Bus stop total > 5000
    RESP=$(api "/bus-stops?limit=1")
    BS_TOTAL=$(jqf "$RESP" '.total')
    if [[ "$BS_TOTAL" -gt 5000 ]]; then
        pass "T50: Bus stops loaded — $BS_TOTAL total"
    elif [[ "$BS_TOTAL" -gt 0 ]]; then
        warn "T50: Bus stops — only $BS_TOTAL (expected > 5000)"
    else
        fail "T50: Bus stops — count is 0"
    fi

    # T51: Zoom to 16, verify viewport-filtered stops appear as markers
    api "/map?lat=$BOSTON_LAT&lon=$BOSTON_LON&zoom=16" >/dev/null
    sleep 2  # Let viewport refresh + 300ms debounce
    RESP=$(api "/state")
    BS_MARKERS=$(jqf "$RESP" '.markers.busStops')
    if [[ "$BS_MARKERS" -gt 0 ]]; then
        pass "T51: Bus stop markers at zoom 16 — $BS_MARKERS visible"
    elif [[ "$BS_TOTAL" -gt 0 ]]; then
        warn "T51: Bus stop markers — 0 visible at zoom 16 (may need more time)"
    else
        skip "T51: Bus stop markers — no bus stops loaded"
    fi

    # Restore zoom
    api "/map?zoom=14" >/dev/null
fi

# ══════════════════════════════════════════════════════════════════════════════
# TEST SUITE: Layer Toggles
# ══════════════════════════════════════════════════════════════════════════════
if should_run "toggles"; then
    suite_header "Layer Toggles"

    # T60: Toggle buses off -> count drops to 0
    api "/toggle?pref=mbta_buses_on&value=false" >/dev/null
    sleep 1
    RESP=$(api "/state")
    BUS_MARKERS=$(jqf "$RESP" '.markers.buses')
    if [[ "$BUS_MARKERS" -eq 0 ]]; then
        pass "T60: Toggle buses OFF — markers dropped to 0"
    else
        fail "T60: Toggle buses OFF — still $BUS_MARKERS markers"
    fi

    # T61: Toggle buses on -> count restores
    api "/toggle?pref=mbta_buses_on&value=true" >/dev/null
    sleep 3
    RESP=$(api "/state")
    BUS_MARKERS=$(jqf "$RESP" '.markers.buses')
    if [[ "$BUS_MARKERS" -gt 0 ]]; then
        pass "T61: Toggle buses ON — $BUS_MARKERS markers restored"
    else
        warn "T61: Toggle buses ON — 0 markers (refresh may be slow)"
    fi

    # T62: Toggle each layer and verify response
    LAYERS=("mbta_trains_on" "mbta_subway_on" "mbta_stations_on" "metar_display_on" "webcams_on")
    ALL_OK=true
    for PREF in "${LAYERS[@]}"; do
        RESP=$(api "/toggle?pref=$PREF&value=false")
        STATUS=$(jqf "$RESP" '.status')
        if [[ "$STATUS" != "ok" ]]; then
            ALL_OK=false
            break
        fi
        # Re-enable
        api "/toggle?pref=$PREF&value=true" >/dev/null
    done
    if [[ "$ALL_OK" == "true" ]]; then
        pass "T62: Toggle each layer — all ${#LAYERS[@]} layers respond 'ok'"
    else
        fail "T62: Toggle layers — some failed"
    fi
    sleep 2  # Let layers restore
fi

# ══════════════════════════════════════════════════════════════════════════════
# TEST SUITE: Aircraft
# ══════════════════════════════════════════════════════════════════════════════
if should_run "aircraft"; then
    suite_header "Aircraft"

    # Set zoom to 10 for aircraft visibility
    api "/map?lat=$BOSTON_LAT&lon=$BOSTON_LON&zoom=10" >/dev/null
    api "/toggle?pref=aircraft_display_on&value=true" >/dev/null
    api "/refresh?layer=aircraft" >/dev/null
    sleep 5

    # T70: Aircraft count
    RESP=$(api "/state")
    AC_COUNT=$(jqf "$RESP" '.markers.aircraft')
    if [[ "$AC_COUNT" -gt 0 ]]; then
        pass "T70: Aircraft loaded — $AC_COUNT on map"
    else
        warn "T70: Aircraft count — 0 (OpenSky may be rate-limited)"
    fi

    # T71: Aircraft markers have callsign in title
    if [[ "$AC_COUNT" -gt 0 ]]; then
        RESP=$(api "/markers?type=aircraft&limit=10")
        WITH_TITLE=$(echo "$RESP" | jq '[.markers[] | select(.title != "")] | length' 2>/dev/null || echo 0)
        if [[ "$WITH_TITLE" -gt 0 ]]; then
            SAMPLE=$(echo "$RESP" | jq -r '.markers[0].title' 2>/dev/null)
            pass "T71: Aircraft titles — $WITH_TITLE have title (sample: $SAMPLE)"
        else
            fail "T71: Aircraft titles — none have titles"
        fi
    else
        skip "T71: Aircraft titles — no aircraft loaded"
    fi

    # Restore zoom
    api "/map?zoom=14" >/dev/null
    api "/toggle?pref=aircraft_display_on&value=false" >/dev/null
fi

# ══════════════════════════════════════════════════════════════════════════════
# TEST SUITE: Webcams
# ══════════════════════════════════════════════════════════════════════════════
if should_run "webcams"; then
    suite_header "Webcams"

    api "/map?lat=$BOSTON_LAT&lon=$BOSTON_LON&zoom=12" >/dev/null
    api "/toggle?pref=webcams_on&value=true" >/dev/null
    api "/refresh?layer=webcams" >/dev/null
    sleep 3

    # T80: Webcam count > 0
    RESP=$(api "/state")
    WC_COUNT=$(jqf "$RESP" '.markers.webcams')
    if [[ "$WC_COUNT" -gt 0 ]]; then
        pass "T80: Webcams loaded — $WC_COUNT on map"
    else
        warn "T80: Webcams — 0 (Windy API may be unavailable)"
    fi

    # T81: Webcam markers have title
    if [[ "$WC_COUNT" -gt 0 ]]; then
        RESP=$(api "/markers?type=webcams&limit=10")
        WITH_TITLE=$(echo "$RESP" | jq '[.markers[] | select(.title != "")] | length' 2>/dev/null || echo 0)
        if [[ "$WITH_TITLE" -gt 0 ]]; then
            SAMPLE=$(echo "$RESP" | jq -r '.markers[0].title' 2>/dev/null)
            pass "T81: Webcam titles — $WITH_TITLE have title (sample: $SAMPLE)"
        else
            fail "T81: Webcam titles — none have titles"
        fi
    else
        skip "T81: Webcam titles — no webcams loaded"
    fi

    api "/map?zoom=14" >/dev/null
fi

# ══════════════════════════════════════════════════════════════════════════════
# TEST SUITE: METAR
# ══════════════════════════════════════════════════════════════════════════════
if should_run "metar"; then
    suite_header "METAR"

    api "/toggle?pref=metar_display_on&value=true" >/dev/null
    api "/refresh?layer=metar" >/dev/null
    sleep 3

    # T90: METAR count > 0
    RESP=$(api "/state")
    METAR_COUNT=$(jqf "$RESP" '.markers.metar')
    if [[ "$METAR_COUNT" -gt 0 ]]; then
        pass "T90: METAR stations loaded — $METAR_COUNT on map"
    else
        warn "T90: METAR — 0 stations loaded"
    fi

    # T91: METAR markers have station data in snippet
    if [[ "$METAR_COUNT" -gt 0 ]]; then
        RESP=$(api "/markers?type=metar&limit=10")
        WITH_SNIPPET=$(echo "$RESP" | jq '[.markers[] | select(.snippet != "" and (.snippet | test("Temperature|Wind|Calm")))] | length' 2>/dev/null || echo 0)
        if [[ "$WITH_SNIPPET" -gt 0 ]]; then
            pass "T91: METAR snippets — $WITH_SNIPPET have weather data"
        else
            fail "T91: METAR snippets — none contain weather data"
        fi
    else
        skip "T91: METAR snippets — no METAR loaded"
    fi
fi

# ══════════════════════════════════════════════════════════════════════════════
# TEST SUITE: Enhanced Marker Data (relatedObject)
# ══════════════════════════════════════════════════════════════════════════════
if should_run "markers"; then
    suite_header "Enhanced Marker Data"

    # T100: Bus markers include relatedObject vehicle data
    RESP=$(api "/markers?type=buses&limit=5")
    BUS_MARKER_COUNT=$(jqf "$RESP" '.total')
    if [[ "$BUS_MARKER_COUNT" -gt 0 ]]; then
        WITH_VEHICLE_ID=$(echo "$RESP" | jq '[.markers[] | select(.vehicleId != null)] | length' 2>/dev/null || echo 0)
        if [[ "$WITH_VEHICLE_ID" -gt 0 ]]; then
            SAMPLE_HS=$(echo "$RESP" | jq -r '.markers[0].headsign // "null"' 2>/dev/null)
            pass "T100: Bus markers have vehicleId — $WITH_VEHICLE_ID (headsign sample: $SAMPLE_HS)"
        else
            fail "T100: Bus markers missing vehicleId (relatedObject not set?)"
        fi
    else
        skip "T100: Bus markers — none on map"
    fi

    # T101: Station markers include relatedObject stop data
    RESP=$(api "/markers?type=stations&limit=5")
    STN_MARKER_COUNT=$(jqf "$RESP" '.total')
    if [[ "$STN_MARKER_COUNT" -gt 0 ]]; then
        WITH_STOP_ID=$(echo "$RESP" | jq '[.markers[] | select(.stopId != null)] | length' 2>/dev/null || echo 0)
        if [[ "$WITH_STOP_ID" -gt 0 ]]; then
            pass "T101: Station markers have stopId — $WITH_STOP_ID"
        else
            fail "T101: Station markers missing stopId"
        fi
    else
        skip "T101: Station markers — none on map"
    fi

    # T102: /markers/tap on a bus invokes custom listener (returns tapped status)
    if [[ "$BUS_MARKER_COUNT" -gt 0 ]]; then
        RESP=$(api "/markers/tap?type=buses&index=0")
        TAP_STATUS=$(jqf "$RESP" '.status')
        if [[ "$TAP_STATUS" == "tapped" ]]; then
            TAP_TITLE=$(jqf "$RESP" '.title')
            pass "T102: Marker tap — bus[0] tapped successfully ($TAP_TITLE)"
        else
            fail "T102: Marker tap — unexpected response: $TAP_STATUS"
        fi
        sleep 1  # Let dialog appear/dismiss
    else
        skip "T102: Marker tap — no bus markers"
    fi
fi

# ══════════════════════════════════════════════════════════════════════════════
# SUMMARY
# ══════════════════════════════════════════════════════════════════════════════
echo ""
echo -e "${BOLD}══════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  TEST RESULTS${NC}"
echo -e "${BOLD}══════════════════════════════════════════════════════════${NC}"
TOTAL=$((PASS_COUNT + FAIL_COUNT + WARN_COUNT + SKIP_COUNT))
echo -e "  ${GREEN}PASS${NC}: $PASS_COUNT"
echo -e "  ${RED}FAIL${NC}: $FAIL_COUNT"
echo -e "  ${YELLOW}WARN${NC}: $WARN_COUNT"
echo -e "  ${CYAN}SKIP${NC}: $SKIP_COUNT"
echo -e "  Total: $TOTAL"

if [[ ${#FAILURES[@]} -gt 0 ]]; then
    echo ""
    echo -e "${RED}Failures:${NC}"
    for f in "${FAILURES[@]}"; do
        echo -e "  ${RED}-${NC} $f"
    done
fi

if [[ ${#WARNINGS[@]} -gt 0 ]]; then
    echo ""
    echo -e "${YELLOW}Warnings:${NC}"
    for w in "${WARNINGS[@]}"; do
        echo -e "  ${YELLOW}-${NC} $w"
    done
fi

echo ""
if [[ "$FAIL_COUNT" -eq 0 ]]; then
    echo -e "${GREEN}${BOLD}All tests passed!${NC} ($WARN_COUNT warnings, $SKIP_COUNT skipped)"
    exit 0
else
    echo -e "${RED}${BOLD}$FAIL_COUNT test(s) failed.${NC}"
    exit 1
fi
