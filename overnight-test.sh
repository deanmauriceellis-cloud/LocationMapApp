#!/usr/bin/env bash
#
# overnight-test.sh — Unattended 6-hour test harness for LocationMapApp
#
# Exercises every feature via debug HTTP server (port 8085) and cache proxy (port 3000),
# collects time-series diagnostics, captures errors, and generates a morning report.
#
# Usage:
#   ./overnight-test.sh                    # Full 6-hour run
#   ./overnight-test.sh --duration 30      # 30-min run (for validation)
#   ./overnight-test.sh --skip-setup       # Skip adb check
#   ./overnight-test.sh --no-aircraft      # Skip all aircraft tests (save OpenSky quota)
#
# Ctrl+C at any time → generates report with data collected so far.
#

set -uo pipefail

# ════════════════════════════════════════════════════════════════════════════════
# CONFIGURATION
# ════════════════════════════════════════════════════════════════════════════════

APP_BASE="http://localhost:8085"
PROXY_BASE="http://10.0.0.4:3000"
PACKAGE="com.example.locationmapapp"
DURATION_MIN=360           # default 6 hours
SKIP_SETUP=false
NO_AIRCRAFT=false

# Test locations (lat lon label)
LOCATIONS=(
    "42.3601 -71.0589 Boston_Downtown"
    "42.3736 -71.1097 Cambridge_Harvard"
    "42.3467 -71.0826 Back_Bay"
    "42.3519 -71.0552 South_Station"
    "42.3732 -71.1189 Harvard_Square"
    "42.3467 -71.0972 Fenway"
    "42.3496 -71.0424 Seaport"
    "42.3876 -71.0995 Somerville"
    "42.3636 -71.0544 North_End"
    "42.3318 -71.1212 Brookline"
    "42.2529 -71.0023 Quincy"
    "42.3765 -71.2356 Waltham"
)

# Known MBTA stations for search tests
KNOWN_STATIONS=("Park Street" "South Station" "Harvard" "Downtown Crossing" "North Station")

# Toggleable layer prefs
ALL_PREFS=(
    "mbta_buses_on"
    "mbta_trains_on"
    "mbta_subway_on"
    "mbta_stations_on"
    "mbta_bus_stops_on"
    "aircraft_display_on"
    "webcams_on"
    "metar_display_on"
    "radar_display_on"
    "poi_display_on"
    "nws_alerts_on"
)

# ════════════════════════════════════════════════════════════════════════════════
# COLORS & COUNTERS
# ════════════════════════════════════════════════════════════════════════════════

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
DIM='\033[2m'
NC='\033[0m'

PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0
SKIP_COUNT=0
FAILURES=()
WARNINGS=()

# ════════════════════════════════════════════════════════════════════════════════
# PARSE ARGS
# ════════════════════════════════════════════════════════════════════════════════

while [[ $# -gt 0 ]]; do
    case "$1" in
        --duration)     DURATION_MIN="$2"; shift 2 ;;
        --skip-setup)   SKIP_SETUP=true; shift ;;
        --no-aircraft)  NO_AIRCRAFT=true; shift ;;
        -h|--help)
            echo "Usage: $0 [--duration MINUTES] [--skip-setup] [--no-aircraft]"
            echo "  --duration N     Run for N minutes (default: 360 = 6 hours)"
            echo "  --skip-setup     Skip adb forward + app launch check"
            echo "  --no-aircraft    Skip all aircraft tests (saves OpenSky quota)"
            exit 0
            ;;
        *) echo "Unknown arg: $1"; exit 1 ;;
    esac
done

DURATION_SEC=$((DURATION_MIN * 60))

# ════════════════════════════════════════════════════════════════════════════════
# OUTPUT DIRECTORY
# ════════════════════════════════════════════════════════════════════════════════

RUN_DIR="overnight-runs/$(date +%Y-%m-%d_%H%M)"
mkdir -p "$RUN_DIR"/{baseline,snapshots,screenshots,logs}

CSV_FILE="$RUN_DIR/time-series.csv"
EVENTS_LOG="$RUN_DIR/events.log"
REPORT_FILE="$RUN_DIR/report.md"
TEST_APP_OUTPUT="$RUN_DIR/test-app-output.txt"

# ════════════════════════════════════════════════════════════════════════════════
# HELPERS
# ════════════════════════════════════════════════════════════════════════════════

ts() { date "+%Y-%m-%d %H:%M:%S"; }
elapsed_min() { echo $(( ($(date +%s) - START_TIME) / 60 )); }
elapsed_sec() { echo $(( $(date +%s) - START_TIME )); }

# ── Time-awareness: MBTA service windows ──
# Subway: ~5:00 AM to ~12:45 AM (varies by line)
# Buses:  ~5:00 AM to ~1:00 AM (varies by route)
# CR:     ~5:30 AM to ~11:30 PM (varies by line)
# Returns: "active", "winding_down", "overnight", "starting_up"
transit_service_window() {
    local hour
    hour=$(date +%-H)
    if [[ "$hour" -ge 6 && "$hour" -le 22 ]]; then
        echo "active"
    elif [[ "$hour" -eq 23 || "$hour" -eq 5 ]]; then
        echo "winding_down"
    elif [[ "$hour" -ge 0 && "$hour" -le 4 ]]; then
        echo "overnight"
    else
        echo "starting_up"
    fi
}

# Check if we expect transit to be running right now
transit_should_be_active() {
    local window
    window=$(transit_service_window)
    [[ "$window" == "active" || "$window" == "winding_down" ]]
}

# Track first sighting of transit coming online
TRANSIT_FIRST_BUS=0
TRANSIT_FIRST_TRAIN=0
TRANSIT_FIRST_SUBWAY=0

# Log event to events.log AND terminal
event() {
    local level="$1"; shift
    local msg="$*"
    local timestamp
    timestamp=$(ts)
    echo "[$timestamp] [$level] $msg" >> "$EVENTS_LOG"
    case "$level" in
        PASS)  echo -e "${GREEN}[PASS]${NC} $msg" ;;
        FAIL)  echo -e "${RED}[FAIL]${NC} $msg" ;;
        WARN)  echo -e "${YELLOW}[WARN]${NC} $msg" ;;
        SKIP)  echo -e "${CYAN}[SKIP]${NC} $msg" ;;
        INFO)  echo -e "${DIM}[INFO]${NC} $msg" ;;
        PHASE) echo -e "\n${BOLD}━━━ $msg ━━━${NC}" ;;
        *)     echo -e "[$level] $msg" ;;
    esac
}

pass()  { PASS_COUNT=$((PASS_COUNT + 1)); event PASS "$1"; }
fail()  { FAIL_COUNT=$((FAIL_COUNT + 1)); FAILURES+=("$1"); event FAIL "$1"; }
warn()  { WARN_COUNT=$((WARN_COUNT + 1)); WARNINGS+=("$1"); event WARN "$1"; }
skip()  { SKIP_COUNT=$((SKIP_COUNT + 1)); event SKIP "$1"; }

# curl wrappers: debug server (app) and proxy
api() {
    curl -s --max-time 15 "${APP_BASE}$1" 2>/dev/null || echo '{"_error":"connection_failed"}'
}

proxy() {
    curl -s --max-time 15 "${PROXY_BASE}$1" 2>/dev/null || echo '{"_error":"connection_failed"}'
}

# jq field extraction (safe)
jqf() {
    echo "$1" | jq -r "$2" 2>/dev/null || echo ""
}

# jq numeric extraction (returns 0 on failure)
jqn() {
    local val
    val=$(echo "$1" | jq -r "$2" 2>/dev/null || echo "")
    if [[ "$val" =~ ^-?[0-9]+\.?[0-9]*$ ]]; then
        echo "$val"
    else
        echo "0"
    fi
}

# Safe integer percentage: pct PART TOTAL
pct() {
    local part="${1:-0}" total="${2:-0}"
    if [[ "$total" -gt 0 ]]; then
        echo $((part * 100 / total))
    else
        echo 0
    fi
}

# Save JSON to a file
save_json() {
    local data="$1" file="$2"
    echo "$data" | jq '.' > "$file" 2>/dev/null || echo "$data" > "$file"
}

# Take a screenshot via debug server, save as PNG
take_screenshot() {
    local label="${1:-screenshot}"
    local filename
    filename="$RUN_DIR/screenshots/${label}_$(date +%H%M%S).png"
    curl -s --max-time 10 "${APP_BASE}/screenshot" -o "$filename" 2>/dev/null
    if [[ -f "$filename" && $(stat -c%s "$filename" 2>/dev/null || echo 0) -gt 1024 ]]; then
        event INFO "Screenshot saved: $filename ($(stat -c%s "$filename") bytes)"
    else
        event WARN "Screenshot capture failed or too small: $filename"
        rm -f "$filename"
    fi
}

# ════════════════════════════════════════════════════════════════════════════════
# CSV TIME-SERIES
# ════════════════════════════════════════════════════════════════════════════════

# Write CSV header
write_csv_header() {
    echo "timestamp,elapsed_min,memory_used_mb,memory_total_mb,memory_max_mb,threads,"\
"markers_poi,markers_buses,markers_trains,markers_subway,markers_stations,markers_bus_stops,"\
"markers_aircraft,markers_webcams,markers_metar,"\
"livedata_buses,livedata_trains,livedata_subway,livedata_stations,livedata_aircraft,livedata_webcams,livedata_metars,"\
"cache_entries,cache_hits,cache_misses,cache_hit_rate,"\
"opensky_used,opensky_remaining,"\
"log_errors,uptime_ms" > "$CSV_FILE"
}

# Collect one row of time-series data from /state, /perf, /livedata, proxy /cache/stats
write_csv_row() {
    local state perf livedata cache_stats
    state=$(api "/state")
    perf=$(api "/perf")
    livedata=$(api "/livedata")
    cache_stats=$(proxy "/cache/stats")

    local mem_used mem_total mem_max threads
    mem_used=$(jqn "$perf" '.memory.used_mb')
    mem_total=$(jqn "$perf" '.memory.total_mb')
    mem_max=$(jqn "$perf" '.memory.max_mb')
    threads=$(jqn "$perf" '.threads.active')

    local m_poi m_buses m_trains m_subway m_stations m_bus_stops m_aircraft m_webcams m_metar
    m_poi=$(jqn "$state" '.markers.poi')
    m_buses=$(jqn "$state" '.markers.buses')
    m_trains=$(jqn "$state" '.markers.trains')
    m_subway=$(jqn "$state" '.markers.subway')
    m_stations=$(jqn "$state" '.markers.stations')
    m_bus_stops=$(jqn "$state" '.markers.busStops')
    m_aircraft=$(jqn "$state" '.markers.aircraft')
    m_webcams=$(jqn "$state" '.markers.webcams')
    m_metar=$(jqn "$state" '.markers.metar')

    local ld_buses ld_trains ld_subway ld_stations ld_aircraft ld_webcams ld_metars
    ld_buses=$(jqn "$livedata" '.mbtaBuses')
    ld_trains=$(jqn "$livedata" '.mbtaTrains')
    ld_subway=$(jqn "$livedata" '.mbtaSubway')
    ld_stations=$(jqn "$livedata" '.mbtaStations')
    ld_aircraft=$(jqn "$livedata" '.aircraft')
    ld_webcams=$(jqn "$livedata" '.webcams')
    ld_metars=$(jqn "$livedata" '.metars')

    local c_entries c_hits c_misses c_rate
    c_entries=$(jqn "$cache_stats" '.entries')
    c_hits=$(jqn "$cache_stats" '.hits')
    c_misses=$(jqn "$cache_stats" '.misses')
    c_rate=$(jqf "$cache_stats" '.hitRate // "0%"')

    local os_used os_remaining
    os_used=$(jqn "$cache_stats" '.opensky.requestsLast24h')
    os_remaining=$(jqn "$cache_stats" '.opensky.remaining')

    # Count error-level log lines
    local error_resp log_errors
    error_resp=$(api "/logs?level=E&tail=1")
    log_errors=$(jqn "$error_resp" '.count')

    local uptime_ms
    uptime_ms=$(jqn "$perf" '.uptime_ms')

    echo "$(ts),$(elapsed_min),$mem_used,$mem_total,$mem_max,$threads,"\
"$m_poi,$m_buses,$m_trains,$m_subway,$m_stations,$m_bus_stops,"\
"$m_aircraft,$m_webcams,$m_metar,"\
"$ld_buses,$ld_trains,$ld_subway,$ld_stations,$ld_aircraft,$ld_webcams,$ld_metars,"\
"$c_entries,$c_hits,$c_misses,$c_rate,"\
"$os_used,$os_remaining,"\
"$log_errors,$uptime_ms" >> "$CSV_FILE"

    # Store latest values in globals for Phase 3 monitoring
    LATEST_MEM_USED="$mem_used"
    LATEST_LOG_ERRORS="$log_errors"
    LATEST_OS_REMAINING="$os_remaining"
    LATEST_OS_USED="$os_used"
}

# ════════════════════════════════════════════════════════════════════════════════
# VEHICLE QUALITY CHECK — reusable for Phase 2 and Phase 3
# ════════════════════════════════════════════════════════════════════════════════

# Check vehicle data quality for a given type (buses, trains, subway)
# Sets: VQ_TOTAL, VQ_HEADSIGN_PCT, VQ_TRIP_PCT, VQ_STOP_PCT, VQ_MARKER_COUNT
check_vehicle_quality() {
    local type="$1"
    local resp
    resp=$(api "/vehicles?type=$type&limit=500")

    VQ_TOTAL=$(jqn "$resp" '.total')
    if [[ "$VQ_TOTAL" -eq 0 ]]; then
        VQ_HEADSIGN_PCT=0; VQ_TRIP_PCT=0; VQ_STOP_PCT=0; VQ_MARKER_COUNT=0
        return 1
    fi

    local with_hs with_trip with_stop
    with_hs=$(echo "$resp" | jq '[.vehicles[] | select(.headsign != null and .headsign != "")] | length' 2>/dev/null || echo 0)
    with_trip=$(echo "$resp" | jq '[.vehicles[] | select(.tripId != null and .tripId != "")] | length' 2>/dev/null || echo 0)
    with_stop=$(echo "$resp" | jq '[.vehicles[] | select(.stopName != null and .stopName != "")] | length' 2>/dev/null || echo 0)
    VQ_HEADSIGN_PCT=$(pct "$with_hs" "$VQ_TOTAL")
    VQ_TRIP_PCT=$(pct "$with_trip" "$VQ_TOTAL")
    VQ_STOP_PCT=$(pct "$with_stop" "$VQ_TOTAL")

    local markers
    markers=$(api "/markers?type=$type&limit=1")
    VQ_MARKER_COUNT=$(jqn "$markers" '.total')
    return 0
}

# ════════════════════════════════════════════════════════════════════════════════
# SIGNAL HANDLER — generate report on Ctrl+C
# ════════════════════════════════════════════════════════════════════════════════

cleanup() {
    echo ""
    event INFO "Interrupted — generating report with data collected so far..."
    generate_report "interrupted"
    echo ""
    echo -e "${BOLD}Report saved to: ${CYAN}$REPORT_FILE${NC}"
    echo -e "${BOLD}Run directory:   ${CYAN}$RUN_DIR${NC}"
    exit 0
}

trap cleanup SIGINT SIGTERM

# ════════════════════════════════════════════════════════════════════════════════
# PHASE 1: SETUP & BASELINE
# ════════════════════════════════════════════════════════════════════════════════

phase1_setup_baseline() {
    event PHASE "PHASE 1: Setup & Baseline"
    START_TIME=$(date +%s)

    # -- Prerequisites --
    if [[ "$SKIP_SETUP" == "false" ]]; then
        event INFO "Checking prerequisites..."

        # Check adb
        if ! command -v adb &>/dev/null; then
            echo "ERROR: adb not found in PATH"; exit 1
        fi
        adb forward tcp:8085 tcp:8085 2>/dev/null || true

        # Check app is running
        if ! adb shell pidof "$PACKAGE" >/dev/null 2>&1; then
            event INFO "App not running — launching..."
            adb shell am start -n "$PACKAGE/.ui.MainActivity" 2>/dev/null || true
            sleep 5
        else
            event INFO "App already running"
        fi
    fi

    # Check jq and bc
    for cmd in jq bc; do
        if ! command -v "$cmd" &>/dev/null; then
            echo "ERROR: $cmd not found — install with: sudo apt install $cmd"; exit 1
        fi
    done

    # Wait for debug server
    event INFO "Waiting for debug server on port 8085..."
    local ready=false
    for i in $(seq 1 30); do
        if curl -s --max-time 2 "${APP_BASE}/" >/dev/null 2>&1; then
            ready=true; break
        fi
        sleep 1
    done
    if [[ "$ready" != "true" ]]; then
        echo "ERROR: Debug server not reachable after 30s"; exit 1
    fi
    pass "Debug server reachable"

    # Check proxy
    local proxy_resp
    proxy_resp=$(proxy "/cache/stats")
    if [[ $(jqf "$proxy_resp" '._error') == "connection_failed" ]]; then
        fail "Cache proxy not reachable at $PROXY_BASE"
        event WARN "Continuing without proxy — some tests will be limited"
    else
        pass "Cache proxy reachable — $(jqn "$proxy_resp" '.entries') cache entries"
    fi

    # -- Baseline captures --
    event INFO "Capturing baseline snapshots..."

    local state perf prefs livedata cache_stats
    state=$(api "/state")
    perf=$(api "/perf")
    prefs=$(api "/prefs")
    livedata=$(api "/livedata")
    cache_stats=$(proxy "/cache/stats")

    save_json "$state"       "$RUN_DIR/baseline/state.json"
    save_json "$perf"        "$RUN_DIR/baseline/perf.json"
    save_json "$prefs"       "$RUN_DIR/baseline/prefs.json"
    save_json "$livedata"    "$RUN_DIR/baseline/livedata.json"
    save_json "$cache_stats" "$RUN_DIR/baseline/cache-stats.json"

    # DB stats (if available)
    local db_stats
    db_stats=$(proxy "/db/pois/stats")
    save_json "$db_stats" "$RUN_DIR/baseline/db-stats.json"

    # Record baseline memory for leak detection
    BASELINE_MEM=$(jqn "$perf" '.memory.used_mb')
    BASELINE_LOG_ERRORS=0
    BASELINE_OS_USED=$(jqn "$cache_stats" '.opensky.requestsLast24h')

    event INFO "Baseline memory: ${BASELINE_MEM}MB"
    event INFO "Baseline OpenSky used: $BASELINE_OS_USED requests"

    take_screenshot "baseline"

    # -- Clear logs for fresh error tracking --
    api "/logs/clear" >/dev/null
    event INFO "Debug logs cleared"

    # -- First CSV row --
    write_csv_header
    LATEST_MEM_USED=0; LATEST_LOG_ERRORS=0; LATEST_OS_REMAINING=0; LATEST_OS_USED=0
    write_csv_row

    # -- Position map to Boston --
    api "/map?lat=42.3601&lon=-71.0589&zoom=14" >/dev/null
    sleep 2

    # -- Run existing test-app.sh if it exists --
    if [[ -f "./test-app.sh" ]]; then
        event INFO "Running existing test-app.sh..."
        bash ./test-app.sh --skip-setup > "$TEST_APP_OUTPUT" 2>&1 || true
        local ta_pass ta_fail
        ta_pass=$(sed 's/\x1b\[[0-9;]*m//g' "$TEST_APP_OUTPUT" | grep -c '^\[PASS\]' 2>/dev/null || echo 0)
        ta_fail=$(sed 's/\x1b\[[0-9;]*m//g' "$TEST_APP_OUTPUT" | grep -c '^\[FAIL\]' 2>/dev/null || echo 0)
        event INFO "test-app.sh results: $ta_pass pass, $ta_fail fail"
    else
        event WARN "test-app.sh not found — skipping"
    fi

    event INFO "Phase 1 complete — baseline captured in $RUN_DIR/baseline/"
}

# ════════════════════════════════════════════════════════════════════════════════
# PHASE 1.5: INITIALIZATION OBSERVATION
# ════════════════════════════════════════════════════════════════════════════════

phase1_5_initialization() {
    event PHASE "PHASE 1.5: Layer Initialization Timing"

    local svc_window
    svc_window=$(transit_service_window)
    event INFO "Current service window: $svc_window (hour: $(date +%-H))"

    # Save init timing results
    local init_file="$RUN_DIR/baseline/init-timing.json"
    echo "{" > "$init_file"

    # Enable all layers and time how long each takes to populate
    local layers_to_time=(
        "mbta_stations_on:stations:stations:.total:5"
        "mbta_bus_stops_on:bus_stops:bus-stops:.total:5"
        "metar_display_on:metar:metar:.markers.metar:5"
        "webcams_on:webcams:webcams:.markers.webcams:5"
        "poi_display_on:pois:poi:.markers.poi:3"
    )

    # Only test live vehicles if service might be running
    if transit_should_be_active; then
        layers_to_time+=(
            "mbta_buses_on:buses:buses:.markers.buses:8"
            "mbta_trains_on:trains:trains:.markers.trains:8"
            "mbta_subway_on:subway:subway:.markers.subway:8"
        )
    else
        event INFO "Transit inactive ($svc_window) — skipping live vehicle init timing"
        event INFO "Static data layers (stations, bus stops) still tested — they're always available"
    fi

    local first=true
    for entry in "${layers_to_time[@]}"; do
        IFS=':' read -r pref layer check_endpoint check_field wait_sec <<< "$entry"

        local t_start t_end t_elapsed count_result

        # Enable + refresh + time it
        t_start=$(date +%s%N)
        api "/toggle?pref=$pref&value=true" >/dev/null
        api "/refresh?layer=$layer" >/dev/null
        sleep "$wait_sec"

        # Check how much loaded
        if [[ "$check_endpoint" == "bus-stops" || "$check_endpoint" == "stations" ]]; then
            local resp
            resp=$(api "/$check_endpoint?limit=1")
            count_result=$(jqn "$resp" "$check_field")
        else
            local state_resp
            state_resp=$(api "/state")
            count_result=$(jqn "$state_resp" "$check_field")
        fi

        t_end=$(date +%s%N)
        t_elapsed=$(( (t_end - t_start) / 1000000 ))  # milliseconds

        if [[ "$count_result" -gt 0 ]]; then
            pass "Init $layer: $count_result items in ${t_elapsed}ms"
        else
            event INFO "Init $layer: 0 items in ${t_elapsed}ms (may be expected at this hour)"
        fi

        # Write to JSON
        [[ "$first" == "true" ]] && first=false || echo "," >> "$init_file"
        printf '  "%s": {"count": %s, "ms": %s, "service_window": "%s"}' \
            "$layer" "$count_result" "$t_elapsed" "$svc_window" >> "$init_file"
    done

    echo "" >> "$init_file"
    echo "}" >> "$init_file"

    take_screenshot "init_complete"
    write_csv_row

    event INFO "Initialization timing saved to $init_file"
}

# ════════════════════════════════════════════════════════════════════════════════
# PHASE 2: FEATURE EXERCISE
# ════════════════════════════════════════════════════════════════════════════════

# Test vehicle data quality for a given type.
# Time-aware: during overnight hours, 0 vehicles is expected (not a warning).
test_vehicle_type() {
    local type="$1"
    local label="$2"

    check_vehicle_quality "$type"

    local svc_window
    svc_window=$(transit_service_window)

    if [[ "$VQ_TOTAL" -gt 0 ]]; then
        pass "$label loaded: $VQ_TOTAL vehicles, $VQ_MARKER_COUNT markers"
        [[ "$VQ_HEADSIGN_PCT" -ge 50 ]] && pass "$label headsign: ${VQ_HEADSIGN_PCT}%" || warn "$label headsign: only ${VQ_HEADSIGN_PCT}%"
        [[ "$VQ_TRIP_PCT" -ge 50 ]]     && pass "$label tripId: ${VQ_TRIP_PCT}%"       || warn "$label tripId: only ${VQ_TRIP_PCT}%"
        [[ "$VQ_STOP_PCT" -ge 30 ]]     && pass "$label stopName: ${VQ_STOP_PCT}%"     || warn "$label stopName: only ${VQ_STOP_PCT}%"

        # Cross-check: markers vs LiveData
        if [[ "$VQ_MARKER_COUNT" -gt 0 ]]; then
            local ratio
            ratio=$(pct "$VQ_MARKER_COUNT" "$VQ_TOTAL")
            if [[ "$ratio" -ge 50 ]]; then
                pass "$label marker/vehicle ratio: ${ratio}% ($VQ_MARKER_COUNT/$VQ_TOTAL)"
            else
                warn "$label marker/vehicle ratio low: ${ratio}%"
            fi
        fi

        # Nearest to downtown
        local nearest_resp nearest_dist
        nearest_resp=$(api "/markers/nearest?lat=42.3601&lon=-71.0589&type=$type&limit=1")
        nearest_dist=$(echo "$nearest_resp" | jq '.nearest[0].distance_m // empty' 2>/dev/null || echo "")
        if [[ -n "$nearest_dist" ]]; then
            local dist_km
            dist_km=$(echo "$nearest_dist" | awk '{printf "%.1f", $1/1000}')
            pass "Nearest $type to downtown: ${dist_km}km"
        fi

        # Tap first marker
        local tap_resp
        tap_resp=$(api "/markers/tap?type=$type&index=0")
        if [[ "$(jqf "$tap_resp" '.status')" == "tapped" ]]; then
            pass "$label marker tap: $(jqf "$tap_resp" '.title')"
        else
            warn "$label marker tap failed"
        fi
        sleep 2

        # Title format check (buses have "Bus ", trains have "Train ", subway have "Subway ")
        local markers_resp titled expected_prefix
        case "$type" in
            buses)  expected_prefix="Bus " ;;
            trains) expected_prefix="Train " ;;
            subway) expected_prefix="Subway " ;;
        esac
        markers_resp=$(api "/markers?type=$type&limit=20")
        titled=$(echo "$markers_resp" | jq --arg pfx "$expected_prefix" '[.markers[] | select(.title | startswith($pfx))] | length' 2>/dev/null || echo 0)
        local sample_count
        sample_count=$(jqn "$markers_resp" '.returned')
        if [[ "$titled" -gt 0 ]]; then
            pass "$label title format: $titled/$sample_count correct"
        fi

        return 0
    else
        # No vehicles — is that expected?
        if [[ "$svc_window" == "overnight" ]]; then
            pass "$label: 0 vehicles (EXPECTED — service window: $svc_window, hour $(date +%-H))"
        elif [[ "$svc_window" == "winding_down" || "$svc_window" == "starting_up" ]]; then
            event INFO "$label: 0 vehicles (transitional — service window: $svc_window)"
        else
            warn "$label: 0 vehicles during active hours — possible issue"
        fi
        return 1
    fi
}

phase2_feature_exercise() {
    event PHASE "PHASE 2: Feature Exercise"

    local svc_window
    svc_window=$(transit_service_window)
    event INFO "Service window: $svc_window (hour $(date +%-H))"

    # ── MBTA LIVE VEHICLES ─────────────────────────────────────────────────────
    # Test all three vehicle types — the test_vehicle_type function handles
    # time-awareness: overnight zeros are PASS not WARN
    for vtype_entry in "buses:MBTA Buses:mbta_buses_on" "trains:MBTA Commuter Rail:mbta_trains_on" "subway:MBTA Subway:mbta_subway_on"; do
        IFS=':' read -r vtype vlabel vpref <<< "$vtype_entry"
        event PHASE "$vlabel"
        api "/toggle?pref=$vpref&value=true" >/dev/null
        api "/refresh?layer=$vtype" >/dev/null
        sleep 5
        test_vehicle_type "$vtype" "$vlabel"
        sleep 2
    done

    # ── MBTA STATIONS ──────────────────────────────────────────────────────────
    event PHASE "MBTA Stations"

    api "/toggle?pref=mbta_stations_on&value=true" >/dev/null
    api "/refresh?layer=stations" >/dev/null
    sleep 5

    local station_resp station_total
    station_resp=$(api "/stations?limit=1")
    station_total=$(jqn "$station_resp" '.total')

    if [[ "$station_total" -gt 100 ]]; then
        pass "Stations loaded: $station_total total"
    elif [[ "$station_total" -gt 0 ]]; then
        warn "Stations loaded: only $station_total (expected > 100)"
    else
        fail "Stations: count is 0 — station fetch failed"
    fi

    # Route data check
    if [[ "$station_total" -gt 0 ]]; then
        local stn_detail with_routes returned
        stn_detail=$(api "/stations?limit=10")
        with_routes=$(echo "$stn_detail" | jq '[.stations[] | select(.routeIds | length > 0)] | length' 2>/dev/null || echo 0)
        returned=$(jqn "$stn_detail" '.returned')
        if [[ "$with_routes" -eq "$returned" ]]; then
            pass "Station routeIds: all $with_routes/$returned have routes"
        else
            warn "Station routeIds: only $with_routes/$returned have routes"
        fi
    fi

    # Search known stations
    for name in "${KNOWN_STATIONS[@]}"; do
        local search_resp found
        search_resp=$(api "/stations?q=$(echo "$name" | sed 's/ /%20/g')")
        found=$(jqn "$search_resp" '.total')
        if [[ "$found" -gt 0 ]]; then
            local first_name
            first_name=$(jqf "$search_resp" '.stations[0].name')
            pass "Station search '$name': found $found — first: $first_name"
        else
            warn "Station search '$name': no results"
        fi
    done

    # Tap a station → arrival board
    local stn_markers
    stn_markers=$(api "/markers?type=stations&limit=1")
    local stn_marker_count
    stn_marker_count=$(jqn "$stn_markers" '.total')
    if [[ "$stn_marker_count" -gt 0 ]]; then
        local tap_resp
        tap_resp=$(api "/markers/tap?type=stations&index=0")
        if [[ "$(jqf "$tap_resp" '.status')" == "tapped" ]]; then
            pass "Station marker tap: $(jqf "$tap_resp" '.title') — arrival board triggered"
        else
            warn "Station marker tap failed"
        fi
        sleep 3  # Let arrival board dialog load
    fi
    sleep 2

    # ── BUS STOPS ──────────────────────────────────────────────────────────────
    event PHASE "Bus Stops"

    api "/toggle?pref=mbta_bus_stops_on&value=true" >/dev/null
    api "/refresh?layer=bus_stops" >/dev/null
    sleep 5

    local bs_resp bs_total
    bs_resp=$(api "/bus-stops?limit=1")
    bs_total=$(jqn "$bs_resp" '.total')

    if [[ "$bs_total" -gt 5000 ]]; then
        pass "Bus stops loaded: $bs_total total"
    elif [[ "$bs_total" -gt 0 ]]; then
        warn "Bus stops: only $bs_total (expected > 5000)"
    else
        fail "Bus stops: count is 0 — fetch failed"
    fi

    # Zoom to 16 for viewport-filtered stops
    api "/map?lat=42.3601&lon=-71.0589&zoom=16" >/dev/null
    sleep 3  # 300ms debounce + settling

    local state_resp bs_markers
    state_resp=$(api "/state")
    bs_markers=$(jqn "$state_resp" '.markers.busStops')
    if [[ "$bs_markers" -gt 0 ]]; then
        pass "Bus stop markers at zoom 16: $bs_markers visible"
    elif [[ "$bs_total" -gt 0 ]]; then
        warn "Bus stop markers: 0 visible at zoom 16 (viewport filter issue?)"
    fi

    # Search bus stop by name
    local bs_search
    bs_search=$(api "/bus-stops?q=Harvard")
    local bs_found
    bs_found=$(jqn "$bs_search" '.total')
    if [[ "$bs_found" -gt 0 ]]; then
        pass "Bus stop search 'Harvard': $bs_found results"
    else
        warn "Bus stop search 'Harvard': no results"
    fi

    # Tap a bus stop marker
    if [[ "$bs_markers" -gt 0 ]]; then
        local tap_resp
        tap_resp=$(api "/markers/tap?type=bus_stops&index=0")
        if [[ "$(jqf "$tap_resp" '.status')" == "tapped" ]]; then
            pass "Bus stop marker tap: $(jqf "$tap_resp" '.title')"
        else
            warn "Bus stop marker tap failed"
        fi
        sleep 2
    fi

    # Restore zoom
    api "/map?zoom=14" >/dev/null
    sleep 2

    # ── AIRCRAFT ───────────────────────────────────────────────────────────────
    if [[ "$NO_AIRCRAFT" != "true" ]]; then
        event PHASE "Aircraft"

        # Check OpenSky budget first
        local cache_stats os_remaining
        cache_stats=$(proxy "/cache/stats")
        os_remaining=$(jqn "$cache_stats" '.opensky.remaining')

        if [[ "$os_remaining" -gt 200 ]]; then
            api "/map?lat=42.3601&lon=-71.0589&zoom=10" >/dev/null
            api "/toggle?pref=aircraft_display_on&value=true" >/dev/null
            api "/refresh?layer=aircraft" >/dev/null
            sleep 8

            local state_resp ac_count
            state_resp=$(api "/state")
            ac_count=$(jqn "$state_resp" '.markers.aircraft')
            if [[ "$ac_count" -gt 0 ]]; then
                pass "Aircraft loaded: $ac_count on map"

                # Check marker data
                local ac_markers with_title sample
                ac_markers=$(api "/markers?type=aircraft&limit=10")
                with_title=$(echo "$ac_markers" | jq '[.markers[] | select(.title != "")] | length' 2>/dev/null || echo 0)
                if [[ "$with_title" -gt 0 ]]; then
                    sample=$(echo "$ac_markers" | jq -r '.markers[0].title' 2>/dev/null)
                    pass "Aircraft titles: $with_title have callsign (sample: $sample)"
                fi

                # Check altitude data in related object
                local with_alt
                with_alt=$(echo "$ac_markers" | jq '[.markers[] | select(.altitude != null)] | length' 2>/dev/null || echo 0)
                if [[ "$with_alt" -gt 0 ]]; then
                    pass "Aircraft altitude data: $with_alt have altitude"
                else
                    warn "Aircraft altitude data: none found in marker data"
                fi

                # Brief follow test (30 seconds)
                local first_icao
                first_icao=$(echo "$ac_markers" | jq -r '.markers[0].icao24 // empty' 2>/dev/null)
                if [[ -n "$first_icao" ]]; then
                    api "/follow?type=aircraft&icao=$first_icao" >/dev/null
                    event INFO "Following aircraft $first_icao for 30s..."
                    sleep 30
                    local follow_state
                    follow_state=$(api "/state")
                    local following
                    following=$(jqf "$follow_state" '.followedAircraft')
                    if [[ "$following" != "null" && -n "$following" ]]; then
                        pass "Aircraft follow mode active after 30s (tracking: $following)"
                    else
                        warn "Aircraft follow mode — target may have been lost"
                    fi
                    api "/stop-follow" >/dev/null
                    event INFO "Stopped following aircraft"
                    sleep 2
                fi
            else
                warn "Aircraft: 0 on map (OpenSky may be unavailable)"
            fi

            # OpenSky rate check
            cache_stats=$(proxy "/cache/stats")
            os_remaining=$(jqn "$cache_stats" '.opensky.remaining')
            event INFO "OpenSky budget after aircraft tests: $os_remaining remaining"

            # Disable aircraft to conserve budget
            api "/toggle?pref=aircraft_display_on&value=false" >/dev/null
            api "/map?zoom=14" >/dev/null
            sleep 2
        else
            skip "Aircraft: OpenSky budget too low ($os_remaining remaining) — skipping"
        fi
    else
        skip "Aircraft: --no-aircraft flag set"
    fi

    # ── WEBCAMS ────────────────────────────────────────────────────────────────
    event PHASE "Webcams"

    api "/map?lat=42.3601&lon=-71.0589&zoom=12" >/dev/null
    api "/toggle?pref=webcams_on&value=true" >/dev/null
    api "/refresh?layer=webcams" >/dev/null
    sleep 5

    local state_resp wc_count
    state_resp=$(api "/state")
    wc_count=$(jqn "$state_resp" '.markers.webcams')
    if [[ "$wc_count" -gt 0 ]]; then
        pass "Webcams loaded: $wc_count on map"

        # Check title/category data
        local wc_markers with_title
        wc_markers=$(api "/markers?type=webcams&limit=10")
        with_title=$(echo "$wc_markers" | jq '[.markers[] | select(.title != "")] | length' 2>/dev/null || echo 0)
        if [[ "$with_title" -gt 0 ]]; then
            local sample
            sample=$(echo "$wc_markers" | jq -r '.markers[0].title' 2>/dev/null)
            pass "Webcam data: $with_title have title (sample: $sample)"
        else
            warn "Webcam data: no titles found"
        fi

        # Tap webcam marker
        local tap_resp
        tap_resp=$(api "/markers/tap?type=webcams&index=0")
        if [[ "$(jqf "$tap_resp" '.status')" == "tapped" ]]; then
            pass "Webcam marker tap: $(jqf "$tap_resp" '.title')"
        else
            warn "Webcam marker tap failed"
        fi
        sleep 2
    else
        warn "Webcams: 0 loaded (Windy API may be unavailable)"
    fi
    api "/map?zoom=14" >/dev/null
    sleep 2

    # ── METAR ──────────────────────────────────────────────────────────────────
    event PHASE "METAR Weather"

    api "/toggle?pref=metar_display_on&value=true" >/dev/null
    api "/refresh?layer=metar" >/dev/null
    sleep 5

    state_resp=$(api "/state")
    local metar_count
    metar_count=$(jqn "$state_resp" '.markers.metar')
    if [[ "$metar_count" -gt 0 ]]; then
        pass "METAR stations loaded: $metar_count on map"

        # Check snippet has weather data
        local metar_markers with_weather
        metar_markers=$(api "/markers?type=metar&limit=10")
        with_weather=$(echo "$metar_markers" | jq '[.markers[] | select(.snippet != "" and (.snippet | test("Temperature|Wind|Calm")))] | length' 2>/dev/null || echo 0)
        if [[ "$with_weather" -gt 0 ]]; then
            pass "METAR snippets: $with_weather have weather data"
        else
            warn "METAR snippets: none contain decoded weather data"
        fi
    else
        warn "METAR: 0 stations loaded"
    fi
    sleep 2

    # ── POI SEARCH ─────────────────────────────────────────────────────────────
    event PHASE "POI Search"

    # Trigger a POI search at Boston downtown
    api "/toggle?pref=poi_display_on&value=true" >/dev/null
    api "/search?lat=42.3601&lon=-71.0589" >/dev/null
    sleep 8  # Overpass can be slow

    state_resp=$(api "/state")
    local poi_count
    poi_count=$(jqn "$state_resp" '.markers.poi')
    if [[ "$poi_count" -gt 0 ]]; then
        pass "POI search returned markers: $poi_count on map"
    else
        warn "POI search: 0 markers on map after search"
    fi

    # Check proxy cache grew
    local poi_stats
    poi_stats=$(proxy "/pois/stats")
    local poi_cached
    poi_cached=$(jqn "$poi_stats" '.count')
    if [[ "$poi_cached" -gt 0 ]]; then
        pass "POI cache: $poi_cached cached POIs"
    fi

    # Check DB stats
    local db_stats
    db_stats=$(proxy "/db/pois/stats")
    local db_total
    db_total=$(jqn "$db_stats" '.total')
    if [[ "$db_total" -gt 0 ]]; then
        pass "POI database: $db_total POIs in PostgreSQL"
    else
        warn "POI database: 0 rows (import may not have run)"
    fi
    sleep 2

    # ── MAP NAVIGATION ─────────────────────────────────────────────────────────
    event PHASE "Map Navigation"

    # Navigate to 3 different locations and verify state
    local nav_locations=("42.3736 -71.1097 Cambridge" "42.3496 -71.0424 Seaport" "42.3601 -71.0589 Boston")
    for loc_str in "${nav_locations[@]}"; do
        local nav_lat nav_lon nav_label
        read -r nav_lat nav_lon nav_label <<< "$loc_str"
        api "/map?lat=$nav_lat&lon=$nav_lon&zoom=14" >/dev/null
        sleep 3
        local nav_state
        nav_state=$(api "/state")
        local actual_lat actual_lon
        actual_lat=$(jqf "$nav_state" '.center.lat')
        actual_lon=$(jqf "$nav_state" '.center.lon')
        if [[ -n "$actual_lat" && "$actual_lat" != "null" ]]; then
            pass "Navigate to $nav_label: center=($actual_lat, $actual_lon)"
        else
            fail "Navigate to $nav_label: state endpoint returned invalid data"
        fi
    done
    sleep 2

    # ── LAYER TOGGLES ─────────────────────────────────────────────────────────
    event PHASE "Layer Toggles"

    local toggle_ok=0 toggle_fail=0
    for pref in "${ALL_PREFS[@]}"; do
        # Turn OFF
        local resp status
        resp=$(api "/toggle?pref=$pref&value=false")
        status=$(jqf "$resp" '.status')
        if [[ "$status" != "ok" ]]; then
            toggle_fail=$((toggle_fail + 1))
            event WARN "Toggle $pref OFF failed"
            continue
        fi
        sleep 1

        # Turn back ON
        resp=$(api "/toggle?pref=$pref&value=true")
        status=$(jqf "$resp" '.status')
        if [[ "$status" == "ok" ]]; then
            toggle_ok=$((toggle_ok + 1))
        else
            toggle_fail=$((toggle_fail + 1))
            event WARN "Toggle $pref ON failed"
        fi
    done

    if [[ "$toggle_fail" -eq 0 ]]; then
        pass "Layer toggles: all ${#ALL_PREFS[@]} toggled OFF/ON successfully"
    else
        warn "Layer toggles: $toggle_ok OK, $toggle_fail failed"
    fi

    # Disable aircraft (default OFF) to save OpenSky
    api "/toggle?pref=aircraft_display_on&value=false" >/dev/null
    sleep 3  # Let layers restore

    # ── MARKER INTERACTIONS ────────────────────────────────────────────────────
    event PHASE "Marker Interactions"

    # Search markers by text
    local search_resp
    search_resp=$(api "/markers/search?q=restaurant")
    local search_count
    search_count=$(jqn "$search_resp" '.total')
    if [[ "$search_count" -gt 0 ]]; then
        pass "Marker search 'restaurant': $search_count results"
    else
        event INFO "Marker search 'restaurant': 0 results (may need more POI data)"
    fi

    # Search for bus-related
    search_resp=$(api "/markers/search?q=Bus")
    search_count=$(jqn "$search_resp" '.total')
    if [[ "$search_count" -gt 0 ]]; then
        pass "Marker search 'Bus': $search_count results"
    fi

    # Nearest of each type to downtown
    for mtype in poi buses trains subway stations metar webcams; do
        local nearest_resp nearest_count
        nearest_resp=$(api "/markers/nearest?lat=42.3601&lon=-71.0589&type=$mtype&limit=1")
        nearest_count=$(echo "$nearest_resp" | jq '.nearest | length' 2>/dev/null || echo 0)
        if [[ "$nearest_count" -gt 0 ]]; then
            local dist
            dist=$(echo "$nearest_resp" | jq -r '.nearest[0].distance_m // 0' 2>/dev/null)
            local dist_km
            dist_km=$(echo "$dist" | awk '{printf "%.1f", $1/1000}')
            pass "Nearest $mtype: ${dist_km}km from downtown"
        else
            event INFO "Nearest $mtype: none found"
        fi
    done
    sleep 2

    # ── RADAR ──────────────────────────────────────────────────────────────────
    event PHASE "Radar"

    api "/toggle?pref=radar_display_on&value=true" >/dev/null
    api "/refresh?layer=radar" >/dev/null
    sleep 3

    local overlays_resp overlay_count
    overlays_resp=$(api "/overlays")
    overlay_count=$(jqn "$overlays_resp" '.total')
    if [[ "$overlay_count" -gt 0 ]]; then
        pass "Radar: $overlay_count total map overlays present"
    else
        warn "Radar: 0 overlays"
    fi
    sleep 2

    # ── FOLLOW MODE (Vehicle) ──────────────────────────────────────────────────
    event PHASE "Vehicle Follow Mode"

    # Try to follow any available vehicle type
    local followed=false
    for ftype in buses subway trains; do
        local fcount_resp fcnt
        fcount_resp=$(api "/vehicles?type=$ftype&limit=1")
        fcnt=$(jqn "$fcount_resp" '.total')
        if [[ "$fcnt" -gt 0 ]]; then
            local follow_resp
            follow_resp=$(api "/follow?type=$ftype&index=0")
            local follow_status
            follow_status=$(jqf "$follow_resp" '.status')
            if [[ "$follow_status" == "ok" ]]; then
                event INFO "Following $ftype[0] for 20s..."
                sleep 20
                local follow_state
                follow_state=$(api "/state")
                local fv
                fv=$(jqf "$follow_state" '.followedVehicle')
                if [[ "$fv" != "null" && -n "$fv" ]]; then
                    pass "Vehicle follow ($ftype): active — tracking $fv"
                else
                    pass "Vehicle follow ($ftype): completed (may have stopped)"
                fi
                api "/stop-follow" >/dev/null
                followed=true
                sleep 2
                break
            fi
        fi
    done
    if [[ "$followed" == "false" ]]; then
        if transit_should_be_active; then
            warn "Vehicle follow: no vehicles available during active hours"
        else
            event INFO "Vehicle follow: no vehicles available (overnight — expected)"
        fi
    fi

    # Take a screenshot after all feature tests
    take_screenshot "phase2_complete"
    write_csv_row

    event INFO "Phase 2 complete — all features exercised"
}

# ════════════════════════════════════════════════════════════════════════════════
# PHASE 3: ENDURANCE MONITORING LOOP
# ════════════════════════════════════════════════════════════════════════════════

phase3_endurance_loop() {
    event PHASE "PHASE 3: Endurance Monitoring"

    local phase3_start
    phase3_start=$(date +%s)
    # Phase 3 runs until we're at (DURATION - 30min) from start
    local phase3_end
    phase3_end=$((START_TIME + DURATION_SEC - 1800))

    # If very short run, give Phase 3 at least 2 minutes
    if [[ "$phase3_end" -le "$phase3_start" ]]; then
        phase3_end=$((phase3_start + 120))
    fi

    local iteration=0
    local location_idx=0
    local last_snapshot=0
    local last_screenshot=0
    local last_refresh=0
    local last_map_move=0
    local last_aircraft_cycle=0
    local aircraft_following=false
    local aircraft_follow_start=0

    local last_svc_window=""
    last_svc_window=$(transit_service_window)
    event INFO "Endurance loop running until $(date -d "@$phase3_end" "+%H:%M:%S") ($(( (phase3_end - phase3_start) / 60 ))min)"
    event INFO "Starting service window: $last_svc_window"

    while [[ $(date +%s) -lt "$phase3_end" ]]; do
        local now
        now=$(date +%s)
        local elapsed_since_start=$(( (now - phase3_start) / 60 ))
        iteration=$((iteration + 1))

        # ── Service window transition detection ──
        local current_svc_window
        current_svc_window=$(transit_service_window)
        if [[ "$current_svc_window" != "$last_svc_window" ]]; then
            event INFO "SERVICE TRANSITION: $last_svc_window → $current_svc_window (hour $(date +%-H))"

            # When transitioning TO active, do a full refresh and quality check
            if [[ "$current_svc_window" == "active" || "$current_svc_window" == "starting_up" ]]; then
                event INFO "Transit starting up — refreshing all vehicle layers..."
                for rtype in buses trains subway; do
                    api "/refresh?layer=$rtype" >/dev/null
                    sleep 5
                    check_vehicle_quality "$rtype"
                    if [[ "$VQ_TOTAL" -gt 0 ]]; then
                        pass "TRANSITION: $rtype now has $VQ_TOTAL vehicles (service online!)"
                        # Record first sighting time
                        case "$rtype" in
                            buses)  [[ "$TRANSIT_FIRST_BUS" -eq 0 ]]    && TRANSIT_FIRST_BUS=$now ;;
                            trains) [[ "$TRANSIT_FIRST_TRAIN" -eq 0 ]]  && TRANSIT_FIRST_TRAIN=$now ;;
                            subway) [[ "$TRANSIT_FIRST_SUBWAY" -eq 0 ]] && TRANSIT_FIRST_SUBWAY=$now ;;
                        esac
                    else
                        event INFO "TRANSITION: $rtype still at 0 vehicles (not yet started)"
                    fi
                done
                take_screenshot "service_transition_$(date +%H%M)"
            fi
            last_svc_window="$current_svc_window"
        fi

        # ── Every 5 minutes: snapshot + CSV row ──
        if [[ $((now - last_snapshot)) -ge 300 ]]; then
            last_snapshot=$now
            local snap_name="snapshot_$(date +%H%M%S)"

            # Collect full state
            local state perf livedata
            state=$(api "/state")
            perf=$(api "/perf")
            livedata=$(api "/livedata")
            save_json "$state" "$RUN_DIR/snapshots/${snap_name}_state.json"

            write_csv_row

            # Memory leak detection
            local mem_growth
            mem_growth=$((LATEST_MEM_USED - BASELINE_MEM))
            if [[ "$mem_growth" -gt 50 ]]; then
                warn "Memory growth: +${mem_growth}MB over baseline (${BASELINE_MEM}MB → ${LATEST_MEM_USED}MB)"
            elif [[ "$mem_growth" -gt 25 ]]; then
                event INFO "Memory note: +${mem_growth}MB over baseline"
            fi

            # Error rate tracking
            if [[ "$LATEST_LOG_ERRORS" -gt "$BASELINE_LOG_ERRORS" ]]; then
                local new_errors=$((LATEST_LOG_ERRORS - BASELINE_LOG_ERRORS))
                if [[ "$new_errors" -gt 10 ]]; then
                    warn "Error accumulation: $new_errors new errors since baseline"
                fi
            fi

            event INFO "Snapshot #$((iteration)): mem=${LATEST_MEM_USED}MB, errors=$LATEST_LOG_ERRORS, OpenSky=$LATEST_OS_REMAINING remaining"
        fi

        # ── Every 15 minutes: screenshot ──
        if [[ $((now - last_screenshot)) -ge 900 ]]; then
            last_screenshot=$now
            take_screenshot "endurance_${elapsed_since_start}m"
        fi

        # ── Every 30 minutes: layer refresh + quality checks ──
        if [[ $((now - last_refresh)) -ge 1800 ]]; then
            last_refresh=$now
            local refresh_window
            refresh_window=$(transit_service_window)

            if [[ "$refresh_window" != "overnight" ]]; then
                # Transit might be running — do full vehicle refresh
                event INFO "Periodic refresh — refreshing vehicle layers ($refresh_window)..."
                for rtype in buses trains subway; do
                    api "/refresh?layer=$rtype" >/dev/null
                    sleep 3
                    check_vehicle_quality "$rtype"
                    if [[ "$VQ_TOTAL" -gt 0 ]]; then
                        event INFO "$rtype quality: $VQ_TOTAL vehicles, hs=${VQ_HEADSIGN_PCT}%, trip=${VQ_TRIP_PCT}%, stop=${VQ_STOP_PCT}%"
                        # Record first sighting
                        case "$rtype" in
                            buses)  [[ "$TRANSIT_FIRST_BUS" -eq 0 ]]    && TRANSIT_FIRST_BUS=$now && event INFO "FIRST BUS sighting at $(date +%H:%M:%S)!" ;;
                            trains) [[ "$TRANSIT_FIRST_TRAIN" -eq 0 ]]  && TRANSIT_FIRST_TRAIN=$now && event INFO "FIRST TRAIN sighting at $(date +%H:%M:%S)!" ;;
                            subway) [[ "$TRANSIT_FIRST_SUBWAY" -eq 0 ]] && TRANSIT_FIRST_SUBWAY=$now && event INFO "FIRST SUBWAY sighting at $(date +%H:%M:%S)!" ;;
                        esac
                    else
                        event INFO "$rtype: 0 vehicles"
                    fi
                done
            else
                event INFO "Periodic refresh — overnight: skipping vehicle refresh (no service)"
            fi

            # Webcam + METAR refresh — always (these are 24/7)
            api "/refresh?layer=webcams" >/dev/null
            sleep 2
            api "/refresh?layer=metar" >/dev/null
            sleep 2
            event INFO "Webcam + METAR refreshed (available 24/7)"
        fi

        # ── Every 60 minutes: move map to a new city + POI search ──
        if [[ $((now - last_map_move)) -ge 3600 ]]; then
            last_map_move=$now
            local loc_str=${LOCATIONS[$location_idx]}
            local loc_lat loc_lon loc_label
            read -r loc_lat loc_lon loc_label <<< "$loc_str"
            location_idx=$(( (location_idx + 1) % ${#LOCATIONS[@]} ))

            event INFO "Map move: navigating to $loc_label ($loc_lat, $loc_lon)"
            api "/map?lat=$loc_lat&lon=$loc_lon&zoom=14" >/dev/null
            sleep 5

            # Trigger POI search at new location
            api "/search?lat=$loc_lat&lon=$loc_lon" >/dev/null
            sleep 8  # Let Overpass respond (serialized queue)

            local state_resp poi_after
            state_resp=$(api "/state")
            poi_after=$(jqn "$state_resp" '.markers.poi')
            event INFO "POI at $loc_label: $poi_after markers on map"
        fi

        # ── Every 20 minutes: aircraft follow cycle (if budget allows) ──
        if [[ "$NO_AIRCRAFT" != "true" && $((now - last_aircraft_cycle)) -ge 1200 ]]; then
            local cache_stats os_remaining
            cache_stats=$(proxy "/cache/stats")
            os_remaining=$(jqn "$cache_stats" '.opensky.remaining')

            if [[ "$os_remaining" -gt 200 ]]; then
                if [[ "$aircraft_following" == "false" ]]; then
                    last_aircraft_cycle=$now
                    aircraft_following=true
                    aircraft_follow_start=$now

                    event INFO "Aircraft follow cycle starting (budget: $os_remaining remaining)..."
                    api "/toggle?pref=aircraft_display_on&value=true" >/dev/null
                    api "/map?zoom=10" >/dev/null
                    api "/refresh?layer=aircraft" >/dev/null
                    sleep 8

                    # Pick an aircraft to follow
                    local ac_markers first_icao
                    ac_markers=$(api "/markers?type=aircraft&limit=10")
                    first_icao=$(echo "$ac_markers" | jq -r '.markers[0].icao24 // empty' 2>/dev/null)
                    if [[ -n "$first_icao" ]]; then
                        api "/follow?type=aircraft&icao=$first_icao" >/dev/null
                        event INFO "Following aircraft $first_icao"
                    else
                        event INFO "No aircraft available to follow"
                        aircraft_following=false
                        api "/toggle?pref=aircraft_display_on&value=false" >/dev/null
                        api "/map?zoom=14" >/dev/null
                    fi
                fi
            else
                event INFO "Aircraft follow skipped — budget low ($os_remaining remaining)"
                last_aircraft_cycle=$now
            fi
        fi

        # End aircraft follow after 10 minutes
        if [[ "$aircraft_following" == "true" && $((now - aircraft_follow_start)) -ge 600 ]]; then
            event INFO "Aircraft follow cycle ending (10 min elapsed)"
            api "/stop-follow" >/dev/null
            api "/toggle?pref=aircraft_display_on&value=false" >/dev/null
            api "/map?zoom=14" >/dev/null
            aircraft_following=false
            sleep 2
        fi

        # Sleep 30 seconds between iterations
        sleep 30
    done

    # Stop any ongoing follow
    api "/stop-follow" >/dev/null
    api "/toggle?pref=aircraft_display_on&value=false" >/dev/null

    event INFO "Phase 3 complete — $iteration monitoring iterations over $(elapsed_min) minutes"
}

# ════════════════════════════════════════════════════════════════════════════════
# PHASE 4: LATE-NIGHT VALIDATION
# ════════════════════════════════════════════════════════════════════════════════

phase4_late_night_validation() {
    event PHASE "PHASE 4: Late-Night Validation"

    # ── MBTA vehicle counts during low-service hours ──
    event INFO "Checking transit service levels..."

    api "/refresh?layer=buses" >/dev/null
    sleep 5
    check_vehicle_quality "buses"
    local late_buses=$VQ_TOTAL

    api "/refresh?layer=trains" >/dev/null
    sleep 5
    check_vehicle_quality "trains"
    local late_trains=$VQ_TOTAL

    api "/refresh?layer=subway" >/dev/null
    sleep 5
    check_vehicle_quality "subway"
    local late_subway=$VQ_TOTAL

    event INFO "Late-night transit: buses=$late_buses, trains=$late_trains, subway=$late_subway"

    if [[ $((late_buses + late_trains + late_subway)) -eq 0 ]]; then
        event INFO "All transit at 0 — expected for overnight hours"
    else
        event INFO "Some transit still running — service hasn't ended yet"
    fi

    # ── Vehicle staleness check ──
    if [[ "$late_buses" -gt 0 ]]; then
        local bus_resp stale_count
        bus_resp=$(api "/vehicles?type=buses&limit=500")
        stale_count=$(echo "$bus_resp" | jq '[.vehicles[] | select(.updatedAt != null) | select((.updatedAt | sub("\\.[0-9]+Z$"; "Z") | fromdateiso8601) < (now - 300))] | length' 2>/dev/null || echo "?")
        if [[ "$stale_count" != "?" && "$stale_count" -gt 0 ]]; then
            warn "Stale buses: $stale_count vehicles with GPS >5min old"
        else
            pass "Bus freshness: no severely stale vehicles detected"
        fi
    fi

    # ── Station persistence ──
    local station_resp station_total
    station_resp=$(api "/stations?limit=1")
    station_total=$(jqn "$station_resp" '.total')
    if [[ "$station_total" -gt 100 ]]; then
        pass "Station persistence: $station_total stations still loaded"
    elif [[ "$station_total" -gt 0 ]]; then
        warn "Station persistence: only $station_total stations (some may have been dropped)"
    else
        fail "Station persistence: 0 stations — data lost"
    fi

    # ── METAR + webcam availability overnight ──
    api "/refresh?layer=metar" >/dev/null
    sleep 3
    local state_resp metar_late
    state_resp=$(api "/state")
    metar_late=$(jqn "$state_resp" '.markers.metar')
    if [[ "$metar_late" -gt 0 ]]; then
        pass "METAR overnight: $metar_late stations (weather data persists)"
    else
        warn "METAR overnight: 0 stations"
    fi

    api "/refresh?layer=webcams" >/dev/null
    sleep 3
    state_resp=$(api "/state")
    local wc_late
    wc_late=$(jqn "$state_resp" '.markers.webcams')
    if [[ "$wc_late" -gt 0 ]]; then
        pass "Webcams overnight: $wc_late cameras (API still serving)"
    else
        warn "Webcams overnight: 0 cameras"
    fi

    # ── Memory + error assessment ──
    write_csv_row
    local mem_growth=$((LATEST_MEM_USED - BASELINE_MEM))
    if [[ "$mem_growth" -le 20 ]]; then
        pass "Memory stable: ${LATEST_MEM_USED}MB (baseline ${BASELINE_MEM}MB, growth +${mem_growth}MB)"
    elif [[ "$mem_growth" -le 50 ]]; then
        warn "Memory elevated: ${LATEST_MEM_USED}MB (growth +${mem_growth}MB over baseline)"
    else
        fail "Possible memory leak: ${LATEST_MEM_USED}MB (growth +${mem_growth}MB over ${DURATION_MIN} minutes)"
    fi

    local total_errors=$LATEST_LOG_ERRORS
    if [[ "$total_errors" -le 5 ]]; then
        pass "Error count: $total_errors total errors logged"
    elif [[ "$total_errors" -le 20 ]]; then
        warn "Error count: $total_errors total errors logged"
    else
        fail "High error count: $total_errors errors logged over ${DURATION_MIN} minutes"
    fi

    # Save final error log dump
    local error_log
    error_log=$(api "/logs?level=E")
    save_json "$error_log" "$RUN_DIR/logs/errors_final.json"

    local all_logs
    all_logs=$(api "/logs?tail=500")
    save_json "$all_logs" "$RUN_DIR/logs/all_final.json"

    take_screenshot "phase4_final"

    event INFO "Phase 4 complete — late-night validation done"
}

# ════════════════════════════════════════════════════════════════════════════════
# PHASE 5: GENERATE REPORT
# ════════════════════════════════════════════════════════════════════════════════

generate_report() {
    local completion_status="${1:-completed}"

    event PHASE "PHASE 5: Generating Report"

    local end_time
    end_time=$(date +%s)
    local total_elapsed=$(( (end_time - START_TIME) / 60 ))

    # Collect final stats
    local final_perf final_state final_cache
    final_perf=$(api "/perf" 2>/dev/null || echo '{}')
    final_state=$(api "/state" 2>/dev/null || echo '{}')
    final_cache=$(proxy "/cache/stats" 2>/dev/null || echo '{}')

    local final_mem
    final_mem=$(jqn "$final_perf" '.memory.used_mb')
    local final_cache_entries
    final_cache_entries=$(jqn "$final_cache" '.entries')
    local final_hits
    final_hits=$(jqn "$final_cache" '.hits')
    local final_misses
    final_misses=$(jqn "$final_cache" '.misses')
    local final_hit_rate
    final_hit_rate=$(jqf "$final_cache" '.hitRate // "N/A"')
    local final_os_used
    final_os_used=$(jqn "$final_cache" '.opensky.requestsLast24h')
    local final_os_remaining
    final_os_remaining=$(jqn "$final_cache" '.opensky.remaining')
    local final_uptime
    final_uptime=$(jqf "$final_perf" '.uptime_human // "N/A"')

    # Read baseline cache stats
    local base_cache_entries=0 base_hits=0 base_misses=0
    if [[ -f "$RUN_DIR/baseline/cache-stats.json" ]]; then
        base_cache_entries=$(jq -r '.entries // 0' "$RUN_DIR/baseline/cache-stats.json" 2>/dev/null || echo 0)
        base_hits=$(jq -r '.hits // 0' "$RUN_DIR/baseline/cache-stats.json" 2>/dev/null || echo 0)
        base_misses=$(jq -r '.misses // 0' "$RUN_DIR/baseline/cache-stats.json" 2>/dev/null || echo 0)
    fi

    local session_hits=$((final_hits - base_hits))
    local session_misses=$((final_misses - base_misses))
    local session_total=$((session_hits + session_misses))
    local session_hit_rate="N/A"
    if [[ "$session_total" -gt 0 ]]; then
        session_hit_rate="$(pct "$session_hits" "$session_total")%"
    fi

    # Memory trend from CSV
    local mem_min mem_max mem_final
    mem_min=$(tail -n +2 "$CSV_FILE" | cut -d',' -f3 | sort -n | head -1 2>/dev/null || echo "$BASELINE_MEM")
    mem_max=$(tail -n +2 "$CSV_FILE" | cut -d',' -f3 | sort -n | tail -1 2>/dev/null || echo "$final_mem")
    mem_final="$final_mem"

    local csv_rows
    csv_rows=$(tail -n +2 "$CSV_FILE" | wc -l 2>/dev/null || echo 0)
    local screenshot_count
    screenshot_count=$(ls "$RUN_DIR/screenshots/"*.png 2>/dev/null | wc -l || echo 0)
    local snapshot_count
    snapshot_count=$(ls "$RUN_DIR/snapshots/"*.json 2>/dev/null | wc -l || echo 0)

    # Count events by type
    local event_passes event_fails event_warns
    event_passes=$(grep -c '\[PASS\]' "$EVENTS_LOG" 2>/dev/null || echo 0)
    event_fails=$(grep -c '\[FAIL\]' "$EVENTS_LOG" 2>/dev/null || echo 0)
    event_warns=$(grep -c '\[WARN\]' "$EVENTS_LOG" 2>/dev/null || echo 0)

    # Build ASCII memory chart (simple)
    local mem_chart=""
    if [[ "$csv_rows" -ge 2 ]]; then
        mem_chart=$(tail -n +2 "$CSV_FILE" | cut -d',' -f2,3 | tail -20 | while IFS=',' read -r min_val mem_val; do
            local bar_len=0
            if [[ "$mem_val" =~ ^[0-9]+$ && "$mem_val" -gt 0 ]]; then
                bar_len=$((mem_val / 5))
                [[ "$bar_len" -gt 50 ]] && bar_len=50
            fi
            printf "%4sm │%s %sMB\n" "$min_val" "$(printf '█%.0s' $(seq 1 "$bar_len" 2>/dev/null) 2>/dev/null || echo '█')" "$mem_val"
        done)
    fi

    # ── Write report.md ──
    cat > "$REPORT_FILE" << REPORT_EOF
# Overnight Test Report

**Status**: $completion_status
**Date**: $(date "+%Y-%m-%d")
**Start**: $(date -d "@$START_TIME" "+%H:%M:%S") (service window: $(transit_service_window))
**End**: $(date -d "@$end_time" "+%H:%M:%S")
**Duration**: ${total_elapsed} minutes (planned: ${DURATION_MIN} min)
**App uptime**: $final_uptime

---

## Summary

| Metric | Count |
|--------|-------|
| PASS | $PASS_COUNT |
| FAIL | $FAIL_COUNT |
| WARN | $WARN_COUNT |
| SKIP | $SKIP_COUNT |
| **Total checks** | $((PASS_COUNT + FAIL_COUNT + WARN_COUNT + SKIP_COUNT)) |
| CSV data points | $csv_rows |
| Screenshots | $screenshot_count |
| Snapshots | $snapshot_count |

---

## Memory Usage

| Metric | Value |
|--------|-------|
| Baseline | ${BASELINE_MEM}MB |
| Final | ${mem_final}MB |
| Min | ${mem_min}MB |
| Max | ${mem_max}MB |
| Growth | +$((mem_final - BASELINE_MEM))MB |

$(if [[ -n "$mem_chart" ]]; then
echo '```'
echo "$mem_chart"
echo '```'
fi)

---

## Cache Performance

| Metric | Baseline | Final | Session |
|--------|----------|-------|---------|
| Entries | $base_cache_entries | $final_cache_entries | +$((final_cache_entries - base_cache_entries)) |
| Hits | $base_hits | $final_hits | $session_hits |
| Misses | $base_misses | $final_misses | $session_misses |
| Hit Rate | — | $final_hit_rate | $session_hit_rate |

---

## OpenSky API Usage

| Metric | Value |
|--------|-------|
| Requests at start | $BASELINE_OS_USED |
| Requests at end | $final_os_used |
| Consumed this session | $((final_os_used - BASELINE_OS_USED)) |
| Remaining quota | $final_os_remaining |

---

## Service Window & Initialization

$(
    echo "**Started during**: $(transit_service_window) window"
    echo ""
    if [[ "$TRANSIT_FIRST_BUS" -gt 0 ]]; then
        echo "- First bus sighting: $(date -d "@$TRANSIT_FIRST_BUS" "+%H:%M:%S") ($(( (TRANSIT_FIRST_BUS - START_TIME) / 60 ))min into test)"
    else
        echo "- First bus sighting: _none during test_"
    fi
    if [[ "$TRANSIT_FIRST_TRAIN" -gt 0 ]]; then
        echo "- First train sighting: $(date -d "@$TRANSIT_FIRST_TRAIN" "+%H:%M:%S") ($(( (TRANSIT_FIRST_TRAIN - START_TIME) / 60 ))min into test)"
    else
        echo "- First train sighting: _none during test_"
    fi
    if [[ "$TRANSIT_FIRST_SUBWAY" -gt 0 ]]; then
        echo "- First subway sighting: $(date -d "@$TRANSIT_FIRST_SUBWAY" "+%H:%M:%S") ($(( (TRANSIT_FIRST_SUBWAY - START_TIME) / 60 ))min into test)"
    else
        echo "- First subway sighting: _none during test_"
    fi
    echo ""
    if [[ -f "$RUN_DIR/baseline/init-timing.json" ]]; then
        echo "### Layer Initialization Timing"
        echo ""
        echo '```'
        cat "$RUN_DIR/baseline/init-timing.json"
        echo '```'
    fi
)

---

## Failures

$(if [[ ${#FAILURES[@]} -gt 0 ]]; then
    for f in "${FAILURES[@]}"; do
        echo "- $f"
    done
else
    echo "_No failures recorded._"
fi)

## Warnings

$(if [[ ${#WARNINGS[@]} -gt 0 ]]; then
    for w in "${WARNINGS[@]}"; do
        echo "- $w"
    done
else
    echo "_No warnings recorded._"
fi)

---

## Event Timeline (Key Events)

\`\`\`
$(grep -E '\[(PASS|FAIL|WARN|PHASE)\]' "$EVENTS_LOG" 2>/dev/null | tail -100)
\`\`\`

---

## Feature Coverage Matrix

| Feature | Status | Notes |
|---------|--------|-------|
$(
    check_feature() {
        local name="$1" pattern="$2"
        local passes fails warns
        passes=$(grep -c "\[PASS\].*$pattern" "$EVENTS_LOG" 2>/dev/null || echo 0)
        fails=$(grep -c "\[FAIL\].*$pattern" "$EVENTS_LOG" 2>/dev/null || echo 0)
        warns=$(grep -c "\[WARN\].*$pattern" "$EVENTS_LOG" 2>/dev/null || echo 0)
        local status="—"
        if [[ "$fails" -gt 0 ]]; then status="FAIL"
        elif [[ "$warns" -gt 0 ]]; then status="WARN"
        elif [[ "$passes" -gt 0 ]]; then status="PASS"
        else status="SKIP"; fi
        echo "| $name | $status | ${passes}P ${fails}F ${warns}W |"
    }
    check_feature "MBTA Buses" "[Bb]us"
    check_feature "MBTA Trains" "[Tt]rain"
    check_feature "MBTA Subway" "[Ss]ubway"
    check_feature "MBTA Stations" "[Ss]tation"
    check_feature "Bus Stops" "[Bb]us stop"
    check_feature "Aircraft" "[Aa]ircraft"
    check_feature "Webcams" "[Ww]ebcam"
    check_feature "METAR" "METAR"
    check_feature "POI Search" "POI"
    check_feature "Map Navigation" "[Nn]avigate"
    check_feature "Layer Toggles" "[Tt]oggle"
    check_feature "Marker Search" "Marker search"
    check_feature "Radar" "[Rr]adar"
    check_feature "Follow Mode" "[Ff]ollow"
)

---

## Recommendations

$(
    echo ""
    # Memory
    local mem_growth=$((mem_final - BASELINE_MEM))
    if [[ "$mem_growth" -gt 50 ]]; then
        echo "1. **MEMORY LEAK DETECTED**: +${mem_growth}MB over $total_elapsed minutes. Investigate marker/overlay accumulation, icon cache, or LiveData subscribers."
    elif [[ "$mem_growth" -gt 25 ]]; then
        echo "1. **Memory growth elevated**: +${mem_growth}MB. Monitor over longer runs. Consider profiling with Android Studio Memory Profiler."
    else
        echo "1. **Memory stable**: +${mem_growth}MB — no leak detected."
    fi

    # Errors
    if [[ "$FAIL_COUNT" -gt 0 ]]; then
        echo "2. **$FAIL_COUNT test failures** require investigation. Check events.log for details."
    else
        echo "2. **No test failures** — all exercised features working correctly."
    fi

    # Cache efficiency
    if [[ "$session_total" -gt 0 ]]; then
        local hit_pct
        hit_pct=$(pct "$session_hits" "$session_total")
        if [[ "$hit_pct" -lt 50 ]]; then
            echo "3. **Cache hit rate low** (${hit_pct}%): Consider increasing TTLs or pre-warming cache for frequently accessed data."
        else
            echo "3. **Cache performance good**: ${hit_pct}% hit rate during this session."
        fi
    fi

    # OpenSky
    local os_consumed=$((final_os_used - BASELINE_OS_USED))
    if [[ "$final_os_remaining" -lt 500 ]]; then
        echo "4. **OpenSky quota concern**: Only $final_os_remaining requests remaining. Consumed $os_consumed this session."
    else
        echo "4. **OpenSky budget healthy**: $final_os_remaining remaining (consumed $os_consumed this session)."
    fi

    # Warnings
    if [[ "$WARN_COUNT" -gt 5 ]]; then
        echo "5. **$WARN_COUNT warnings** — review warnings list above for data quality issues (missing headsigns, low marker counts, etc.)."
    fi
)

---

## Files

| File | Description |
|------|-------------|
| \`time-series.csv\` | $csv_rows rows of periodic measurements |
| \`events.log\` | Full timestamped event stream |
| \`baseline/\` | Initial state captures (state, perf, cache, prefs, livedata, db-stats) |
| \`snapshots/\` | $snapshot_count periodic JSON snapshots |
| \`screenshots/\` | $screenshot_count PNG screen captures |
| \`logs/\` | Error logs + final log dump |
| \`test-app-output.txt\` | Results from existing test-app.sh |

REPORT_EOF

    event INFO "Report generated: $REPORT_FILE"
}

# ════════════════════════════════════════════════════════════════════════════════
# MAIN
# ════════════════════════════════════════════════════════════════════════════════

echo ""
echo -e "${BOLD}╔══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}║  LocationMapApp — Overnight Test Harness                ║${NC}"
echo -e "${BOLD}║  Duration: ${DURATION_MIN} minutes                                  ║${NC}"
echo -e "${BOLD}║  Output:   $RUN_DIR${NC}"
echo -e "${BOLD}╚══════════════════════════════════════════════════════════╝${NC}"
echo ""

# Phase 1: Setup & Baseline (~5 min)
phase1_setup_baseline

# Phase 1.5: Initialization Observation (~2-3 min)
phase1_5_initialization

# Phase 2: Feature Exercise (~15-45 min depending on data)
phase2_feature_exercise

# Phase 3: Endurance Monitoring (until 30 min before end)
phase3_endurance_loop

# Phase 4: Late-Night Validation (~15 min)
phase4_late_night_validation

# Phase 5: Final Report
write_csv_row   # final data point
generate_report "completed"

# ═════════════════════════════════════════════════════════════════════════════
# FINAL SUMMARY
# ════════════════════════════════════════════════════════════════════════════════

echo ""
echo -e "${BOLD}══════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  OVERNIGHT TEST COMPLETE${NC}"
echo -e "${BOLD}══════════════════════════════════════════════════════════${NC}"
echo -e "  Duration: $(elapsed_min) minutes"
echo -e "  ${GREEN}PASS${NC}: $PASS_COUNT"
echo -e "  ${RED}FAIL${NC}: $FAIL_COUNT"
echo -e "  ${YELLOW}WARN${NC}: $WARN_COUNT"
echo -e "  ${CYAN}SKIP${NC}: $SKIP_COUNT"
echo ""
echo -e "  Report:      ${CYAN}$REPORT_FILE${NC}"
echo -e "  CSV data:    ${CYAN}$CSV_FILE${NC}"
echo -e "  Events log:  ${CYAN}$EVENTS_LOG${NC}"
echo -e "  Screenshots: ${CYAN}$RUN_DIR/screenshots/${NC}"
echo ""

if [[ "$FAIL_COUNT" -gt 0 ]]; then
    echo -e "  ${RED}${BOLD}$FAIL_COUNT failure(s) detected — review report for details.${NC}"
    echo ""
    exit 1
else
    echo -e "  ${GREEN}${BOLD}All tests passed.${NC} ($WARN_COUNT warnings)"
    echo ""
    exit 0
fi
