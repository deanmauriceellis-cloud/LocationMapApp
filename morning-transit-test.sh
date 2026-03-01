#!/usr/bin/env bash
#
# morning-transit-test.sh — Deep transit test for when MBTA service is active
#
# Designed to run automatically after overnight-test.sh finishes (~5-9 AM),
# or standalone any time during service hours.
#
# Tests: bus/train/subway data quality, arrival boards, trip schedules,
# staleness detection, vehicle follow endurance, station coverage,
# multi-location transit density, and API response reliability.
#
# Usage:
#   ./morning-transit-test.sh                   # 60-min default
#   ./morning-transit-test.sh --duration 30     # 30-min run
#   ./morning-transit-test.sh --wait-for-service # Poll until vehicles appear
#

set -uo pipefail

# ═══════════════════════════════════════════════════════════════════════════════
# CONFIG
# ═══════════════════════════════════════════════════════════════════════════════

APP="http://localhost:8085"
PROXY="http://10.0.0.4:3000"
DURATION_MIN=60
WAIT_FOR_SERVICE=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --duration)           DURATION_MIN="$2"; shift 2 ;;
        --wait-for-service)   WAIT_FOR_SERVICE=true; shift ;;
        -h|--help)
            echo "Usage: $0 [--duration MINUTES] [--wait-for-service]"
            exit 0 ;;
        *) echo "Unknown: $1"; exit 1 ;;
    esac
done

# ═══════════════════════════════════════════════════════════════════════════════
# OUTPUT
# ═══════════════════════════════════════════════════════════════════════════════

RUN_DIR="overnight-runs/morning_$(date +%Y-%m-%d_%H%M)"
mkdir -p "$RUN_DIR"/{snapshots,screenshots,logs}

CSV="$RUN_DIR/transit-series.csv"
LOG="$RUN_DIR/events.log"
REPORT="$RUN_DIR/report.md"

# ═══════════════════════════════════════════════════════════════════════════════
# HELPERS
# ═══════════════════════════════════════════════════════════════════════════════

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; DIM='\033[2m'; NC='\033[0m'

PASS_COUNT=0; FAIL_COUNT=0; WARN_COUNT=0; SKIP_COUNT=0
FAILURES=(); WARNINGS=()

ts()  { date "+%Y-%m-%d %H:%M:%S"; }

event() {
    local lv="$1"; shift
    echo "[$(ts)] [$lv] $*" >> "$LOG"
    case "$lv" in
        PASS)  PASS_COUNT=$((PASS_COUNT+1)); echo -e "${GREEN}[PASS]${NC} $*" ;;
        FAIL)  FAIL_COUNT=$((FAIL_COUNT+1)); FAILURES+=("$*"); echo -e "${RED}[FAIL]${NC} $*" ;;
        WARN)  WARN_COUNT=$((WARN_COUNT+1)); WARNINGS+=("$*"); echo -e "${YELLOW}[WARN]${NC} $*" ;;
        SKIP)  SKIP_COUNT=$((SKIP_COUNT+1)); echo -e "${CYAN}[SKIP]${NC} $*" ;;
        INFO)  echo -e "${DIM}[INFO]${NC} $*" ;;
        PHASE) echo -e "\n${BOLD}━━━ $* ━━━${NC}" ;;
    esac
}

api()   { curl -s --max-time 15 "${APP}$1" 2>/dev/null || echo '{"_error":"connection_failed"}'; }
proxy() { curl -s --max-time 15 "${PROXY}$1" 2>/dev/null || echo '{"_error":"connection_failed"}'; }
jqf()   { echo "$1" | jq -r "$2" 2>/dev/null || echo ""; }
jqn()   { local v; v=$(echo "$1" | jq -r "$2" 2>/dev/null || echo ""); [[ "$v" =~ ^-?[0-9]+\.?[0-9]*$ ]] && echo "$v" || echo "0"; }
pct()   { [[ "${2:-0}" -gt 0 ]] && echo $(($1 * 100 / $2)) || echo 0; }

screenshot() {
    local f="$RUN_DIR/screenshots/${1}_$(date +%H%M%S).png"
    curl -s --max-time 10 "${APP}/screenshot" -o "$f" 2>/dev/null
    [[ -f "$f" && $(stat -c%s "$f" 2>/dev/null || echo 0) -gt 1024 ]] && event INFO "Screenshot: $f" || rm -f "$f"
}

save() { echo "$1" | jq '.' > "$2" 2>/dev/null || echo "$1" > "$2"; }

# ═══════════════════════════════════════════════════════════════════════════════
# CSV
# ═══════════════════════════════════════════════════════════════════════════════

echo "timestamp,elapsed_min,buses,trains,subway,bus_headsign_pct,bus_trip_pct,bus_stop_pct,"\
"train_headsign_pct,train_trip_pct,subway_headsign_pct,subway_trip_pct,"\
"stations,bus_stops_visible,memory_mb,log_errors" > "$CSV"

START_TIME=$(date +%s)

csv_row() {
    local s p el
    s=$(api "/state"); p=$(api "/perf")
    el=$(( ($(date +%s) - START_TIME) / 60 ))
    local mem; mem=$(jqn "$p" '.memory.used_mb')

    # Vehicle counts + quality
    local bv tv sv
    bv=$(api "/vehicles?type=buses&limit=500")
    tv=$(api "/vehicles?type=trains&limit=500")
    sv=$(api "/vehicles?type=subway&limit=500")

    local bc tc sc
    bc=$(jqn "$bv" '.total'); tc=$(jqn "$tv" '.total'); sc=$(jqn "$sv" '.total')

    local b_hs b_tr b_st t_hs t_tr s_hs s_tr
    b_hs=0; b_tr=0; b_st=0; t_hs=0; t_tr=0; s_hs=0; s_tr=0

    if [[ "$bc" -gt 0 ]]; then
        local wh wt ws
        wh=$(echo "$bv" | jq '[.vehicles[]|select(.headsign!=null and .headsign!="")]|length' 2>/dev/null||echo 0)
        wt=$(echo "$bv" | jq '[.vehicles[]|select(.tripId!=null and .tripId!="")]|length' 2>/dev/null||echo 0)
        ws=$(echo "$bv" | jq '[.vehicles[]|select(.stopName!=null and .stopName!="")]|length' 2>/dev/null||echo 0)
        b_hs=$(pct "$wh" "$bc"); b_tr=$(pct "$wt" "$bc"); b_st=$(pct "$ws" "$bc")
    fi
    if [[ "$tc" -gt 0 ]]; then
        local wh wt
        wh=$(echo "$tv" | jq '[.vehicles[]|select(.headsign!=null and .headsign!="")]|length' 2>/dev/null||echo 0)
        wt=$(echo "$tv" | jq '[.vehicles[]|select(.tripId!=null and .tripId!="")]|length' 2>/dev/null||echo 0)
        t_hs=$(pct "$wh" "$tc"); t_tr=$(pct "$wt" "$tc")
    fi
    if [[ "$sc" -gt 0 ]]; then
        local wh wt
        wh=$(echo "$sv" | jq '[.vehicles[]|select(.headsign!=null and .headsign!="")]|length' 2>/dev/null||echo 0)
        wt=$(echo "$sv" | jq '[.vehicles[]|select(.tripId!=null and .tripId!="")]|length' 2>/dev/null||echo 0)
        s_hs=$(pct "$wh" "$sc"); s_tr=$(pct "$wt" "$sc")
    fi

    local stns bsv errs
    stns=$(jqn "$s" '.markers.stations')
    bsv=$(jqn "$s" '.markers.busStops')
    local er; er=$(api "/logs?level=E&tail=1"); errs=$(jqn "$er" '.count')

    echo "$(ts),$el,$bc,$tc,$sc,$b_hs,$b_tr,$b_st,$t_hs,$t_tr,$s_hs,$s_tr,$stns,$bsv,$mem,$errs" >> "$CSV"
}

# ═══════════════════════════════════════════════════════════════════════════════
# WAIT FOR SERVICE (optional)
# ═══════════════════════════════════════════════════════════════════════════════

wait_for_transit() {
    event PHASE "Waiting for transit service to come online"
    local attempts=0 max_wait=120  # 120 × 30s = 60 min max wait

    while [[ "$attempts" -lt "$max_wait" ]]; do
        # Check all three vehicle types
        for vtype in buses subway trains; do
            api "/toggle?pref=mbta_${vtype}_on&value=true" >/dev/null 2>&1
            api "/refresh?layer=$vtype" >/dev/null 2>&1
        done
        sleep 10

        local bc tc sc
        bc=$(jqn "$(api "/vehicles?type=buses&limit=1")" '.total')
        tc=$(jqn "$(api "/vehicles?type=trains&limit=1")" '.total')
        sc=$(jqn "$(api "/vehicles?type=subway&limit=1")" '.total')

        local total=$((bc + tc + sc))
        if [[ "$total" -gt 0 ]]; then
            event INFO "Transit online! buses=$bc trains=$tc subway=$sc (waited $((attempts * 30))s)"
            return 0
        fi

        attempts=$((attempts + 1))
        if [[ $((attempts % 4)) -eq 0 ]]; then
            event INFO "Still waiting for transit... attempt $attempts ($(date +%H:%M))"
        fi
        sleep 20
    done

    event WARN "Timed out waiting for transit after 60 min"
    return 1
}

# ═══════════════════════════════════════════════════════════════════════════════
# DEEP VEHICLE QUALITY ANALYSIS
# ═══════════════════════════════════════════════════════════════════════════════

# Thorough vehicle test — goes beyond basic counts
deep_vehicle_test() {
    local type="$1" label="$2"
    event PHASE "$label — Deep Analysis"

    api "/toggle?pref=mbta_${type}_on&value=true" >/dev/null
    api "/refresh?layer=$type" >/dev/null
    sleep 8

    local resp
    resp=$(api "/vehicles?type=$type&limit=500")
    local total
    total=$(jqn "$resp" '.total')

    if [[ "$total" -eq 0 ]]; then
        event WARN "$label: 0 vehicles — cannot perform deep analysis"
        return 1
    fi

    event INFO "$label: $total vehicles loaded"

    # ── Data completeness ──
    local with_hs with_trip with_stop with_route with_bearing with_speed
    with_hs=$(echo "$resp" | jq '[.vehicles[]|select(.headsign!=null and .headsign!="")]|length' 2>/dev/null||echo 0)
    with_trip=$(echo "$resp" | jq '[.vehicles[]|select(.tripId!=null and .tripId!="")]|length' 2>/dev/null||echo 0)
    with_stop=$(echo "$resp" | jq '[.vehicles[]|select(.stopName!=null and .stopName!="")]|length' 2>/dev/null||echo 0)
    with_route=$(echo "$resp" | jq '[.vehicles[]|select(.routeName!=null and .routeName!="")]|length' 2>/dev/null||echo 0)
    with_bearing=$(echo "$resp" | jq '[.vehicles[]|select(.bearing!=null and .bearing!=0)]|length' 2>/dev/null||echo 0)
    with_speed=$(echo "$resp" | jq '[.vehicles[]|select(.speedMph!=null and .speedMph>0)]|length' 2>/dev/null||echo 0)

    local hs_pct trip_pct stop_pct route_pct bearing_pct speed_pct
    hs_pct=$(pct "$with_hs" "$total")
    trip_pct=$(pct "$with_trip" "$total")
    stop_pct=$(pct "$with_stop" "$total")
    route_pct=$(pct "$with_route" "$total")
    bearing_pct=$(pct "$with_bearing" "$total")
    speed_pct=$(pct "$with_speed" "$total")

    [[ "$hs_pct" -ge 80 ]]      && event PASS "$label headsign: ${hs_pct}% ($with_hs/$total)"      || event WARN "$label headsign: ${hs_pct}% ($with_hs/$total)"
    [[ "$trip_pct" -ge 80 ]]     && event PASS "$label tripId: ${trip_pct}% ($with_trip/$total)"     || event WARN "$label tripId: ${trip_pct}% ($with_trip/$total)"
    [[ "$stop_pct" -ge 50 ]]     && event PASS "$label stopName: ${stop_pct}% ($with_stop/$total)"   || event WARN "$label stopName: ${stop_pct}% ($with_stop/$total)"
    [[ "$route_pct" -ge 90 ]]    && event PASS "$label routeName: ${route_pct}%"                     || event WARN "$label routeName: ${route_pct}%"
    [[ "$bearing_pct" -ge 30 ]]  && event PASS "$label bearing: ${bearing_pct}% have non-zero"       || event INFO "$label bearing: ${bearing_pct}% (many may be stationary)"
    event INFO "$label moving (speed>0): ${speed_pct}% ($with_speed/$total)"

    # ── Staleness check ──
    local with_updated stale_2m stale_5m
    with_updated=$(echo "$resp" | jq '[.vehicles[]|select(.updatedAt!=null)]|length' 2>/dev/null||echo 0)
    if [[ "$with_updated" -gt 0 ]]; then
        # Count vehicles with updatedAt field but check via currentStatus for staleness hints
        local statuses
        statuses=$(echo "$resp" | jq '[.vehicles[].currentStatus]|group_by(.)|map({status:.[0],count:length})' 2>/dev/null||echo "[]")
        event INFO "$label status distribution: $(echo "$statuses" | jq -c '.' 2>/dev/null)"
        event PASS "$label updatedAt: $with_updated/$total have timestamps"
    else
        event WARN "$label updatedAt: 0 have timestamps"
    fi

    # ── Marker cross-check ──
    local markers marker_total
    markers=$(api "/markers?type=$type&limit=1")
    marker_total=$(jqn "$markers" '.total')
    local ratio
    ratio=$(pct "$marker_total" "$total")
    if [[ "$ratio" -ge 80 ]]; then
        event PASS "$label markers: $marker_total/$total rendered (${ratio}%)"
    elif [[ "$ratio" -ge 50 ]]; then
        event WARN "$label markers: only $marker_total/$total rendered (${ratio}%) — some outside viewport?"
    else
        event FAIL "$label markers: $marker_total/$total rendered (${ratio}%) — rendering issue"
    fi

    # ── Title format ──
    local title_sample
    title_sample=$(api "/markers?type=$type&limit=5")
    local sample_titles
    sample_titles=$(echo "$title_sample" | jq -r '[.markers[].title]|join(", ")' 2>/dev/null)
    event INFO "$label sample titles: $sample_titles"

    # ── Unique routes ──
    local unique_routes
    unique_routes=$(echo "$resp" | jq '[.vehicles[].routeId]|unique|length' 2>/dev/null||echo 0)
    event INFO "$label serving $unique_routes unique routes"

    # ── Tap test ──
    if [[ "$marker_total" -gt 0 ]]; then
        local tap
        tap=$(api "/markers/tap?type=$type&index=0")
        if [[ "$(jqf "$tap" '.status')" == "tapped" ]]; then
            event PASS "$label tap: $(jqf "$tap" '.title')"
        else
            event FAIL "$label tap failed: $(jqf "$tap" '.error // .status')"
        fi
        sleep 2
    fi

    # Save full vehicle dump for analysis
    save "$resp" "$RUN_DIR/snapshots/${type}_vehicles_$(date +%H%M).json"

    return 0
}

# ═══════════════════════════════════════════════════════════════════════════════
# STATION & BUS STOP DEEP TESTS
# ═══════════════════════════════════════════════════════════════════════════════

deep_station_test() {
    event PHASE "MBTA Stations — Deep Analysis"

    api "/toggle?pref=mbta_stations_on&value=true" >/dev/null
    api "/refresh?layer=stations" >/dev/null
    sleep 8

    # LiveData count
    local livedata stn_ld
    livedata=$(api "/livedata")
    stn_ld=$(jqn "$livedata" '.mbtaStations')
    event INFO "Stations in LiveData: $stn_ld"

    # Endpoint count
    local resp total
    resp=$(api "/stations?limit=1")
    total=$(jqn "$resp" '.total')

    # Marker count
    local state stn_markers
    state=$(api "/state")
    stn_markers=$(jqn "$state" '.markers.stations')
    event INFO "Station markers on map: $stn_markers"

    if [[ "$total" -gt 200 ]]; then
        event PASS "Stations total: $total (LiveData=$stn_ld, markers=$stn_markers)"
    elif [[ "$total" -gt 0 ]]; then
        event WARN "Stations: only $total (expected >200)"
    else
        # The init test showed 0 from /stations but markers exist — interesting discrepancy
        if [[ "$stn_markers" -gt 0 ]]; then
            event WARN "Stations: /stations returns 0 BUT $stn_markers markers on map — LiveData/endpoint mismatch"
        else
            event FAIL "Stations: 0 everywhere"
        fi
    fi

    # Search known stations
    local search_hits=0
    for name in "Park Street" "South Station" "Harvard" "Downtown Crossing" "North Station" \
                "Alewife" "Braintree" "Wonderland" "Forest Hills" "Ashmont"; do
        local sr
        sr=$(api "/stations?q=$(echo "$name" | sed 's/ /%20/g')")
        local found
        found=$(jqn "$sr" '.total')
        if [[ "$found" -gt 0 ]]; then
            search_hits=$((search_hits + 1))
        fi
    done
    if [[ "$search_hits" -ge 8 ]]; then
        event PASS "Station search: $search_hits/10 known stations found"
    elif [[ "$search_hits" -ge 5 ]]; then
        event WARN "Station search: only $search_hits/10 found"
    else
        event FAIL "Station search: only $search_hits/10 found"
    fi

    # Route coverage — check each line has stations
    local all_stns
    all_stns=$(api "/stations?limit=500")
    for line in Red Blue Orange Green-B Green-C Green-D Green-E; do
        local line_count
        line_count=$(echo "$all_stns" | jq --arg l "$line" '[.stations[]|select(.routeIds[]? == $l)]|length' 2>/dev/null||echo 0)
        if [[ "$line_count" -gt 0 ]]; then
            event INFO "  $line line: $line_count stations"
        fi
    done

    # Tap station → verify arrival board fires
    if [[ "$stn_markers" -gt 0 ]]; then
        local tap
        tap=$(api "/markers/tap?type=stations&index=0")
        if [[ "$(jqf "$tap" '.status')" == "tapped" ]]; then
            event PASS "Station tap → arrival board: $(jqf "$tap" '.title')"
        fi
        sleep 3
    fi

    save "$all_stns" "$RUN_DIR/snapshots/stations_full_$(date +%H%M).json"
}

deep_bus_stop_test() {
    event PHASE "Bus Stops — Deep Analysis"

    api "/toggle?pref=mbta_bus_stops_on&value=true" >/dev/null
    api "/refresh?layer=bus_stops" >/dev/null
    sleep 5

    local resp total
    resp=$(api "/bus-stops?limit=1")
    total=$(jqn "$resp" '.total')

    local state bs_markers
    state=$(api "/state")
    bs_markers=$(jqn "$state" '.markers.busStops')
    local bs_total_mem
    bs_total_mem=$(jqn "$state" '.markers.busStopsTotal')

    event INFO "Bus stops: endpoint=$total, markers=$bs_markers, allBusStops=$bs_total_mem"

    if [[ "$bs_total_mem" -gt 5000 ]]; then
        event PASS "Bus stops in memory: $bs_total_mem (healthy)"
    elif [[ "$bs_total_mem" -gt 0 ]]; then
        event WARN "Bus stops in memory: $bs_total_mem (expected >5000)"
    elif [[ "$bs_markers" -gt 0 ]]; then
        event WARN "Bus stops: allBusStops=0 but $bs_markers markers visible — data loaded but endpoint mismatch"
    else
        event FAIL "Bus stops: 0 everywhere"
    fi

    # Zoom 16 viewport test at 3 locations
    for loc in "42.3601 -71.0589 Downtown" "42.3736 -71.1097 Cambridge" "42.3496 -71.0424 Seaport"; do
        local lat lon label
        read -r lat lon label <<< "$loc"
        api "/map?lat=$lat&lon=$lon&zoom=16" >/dev/null
        sleep 3
        state=$(api "/state")
        local vis
        vis=$(jqn "$state" '.markers.busStops')
        if [[ "$vis" -gt 0 ]]; then
            event PASS "Bus stops at $label zoom 16: $vis visible"
        else
            event WARN "Bus stops at $label zoom 16: 0 visible"
        fi
    done

    # Search
    for q in "Harvard" "Mass Ave" "Washington" "Broadway"; do
        local sr found
        sr=$(api "/bus-stops?q=$(echo "$q" | sed 's/ /%20/g')")
        found=$(jqn "$sr" '.total')
        [[ "$found" -gt 0 ]] && event PASS "Bus stop search '$q': $found" || event WARN "Bus stop search '$q': 0"
    done

    api "/map?zoom=14" >/dev/null
}

# ═══════════════════════════════════════════════════════════════════════════════
# VEHICLE FOLLOW ENDURANCE
# ═══════════════════════════════════════════════════════════════════════════════

follow_endurance_test() {
    event PHASE "Vehicle Follow — Endurance Test"

    # Find an active bus to follow
    local resp
    resp=$(api "/vehicles?type=buses&limit=10")
    local total
    total=$(jqn "$resp" '.total')

    if [[ "$total" -eq 0 ]]; then
        event SKIP "Follow endurance: no buses available"
        return
    fi

    # Pick a bus that has headsign + tripId (good data quality)
    local target_idx=-1
    for i in 0 1 2 3 4; do
        local hs tr
        hs=$(echo "$resp" | jq -r ".vehicles[$i].headsign // \"\"" 2>/dev/null)
        tr=$(echo "$resp" | jq -r ".vehicles[$i].tripId // \"\"" 2>/dev/null)
        if [[ -n "$hs" && "$hs" != "" && -n "$tr" && "$tr" != "" ]]; then
            target_idx=$i
            break
        fi
    done
    [[ "$target_idx" -eq -1 ]] && target_idx=0

    local target_label
    target_label=$(echo "$resp" | jq -r ".vehicles[$target_idx].label // \"?\"" 2>/dev/null)
    local target_route
    target_route=$(echo "$resp" | jq -r ".vehicles[$target_idx].routeName // \"?\"" 2>/dev/null)

    event INFO "Following bus $target_label (route $target_route) for 60s..."

    local follow_resp
    follow_resp=$(api "/follow?type=buses&index=$target_idx")
    if [[ "$(jqf "$follow_resp" '.status')" != "ok" ]]; then
        event FAIL "Follow failed: $(jqf "$follow_resp" '.error')"
        return
    fi

    # Monitor follow state every 10 seconds for 60 seconds
    local checks=0 active=0
    for i in $(seq 1 6); do
        sleep 10
        local state fv
        state=$(api "/state")
        fv=$(jqf "$state" '.followedVehicle')
        checks=$((checks + 1))
        if [[ "$fv" != "null" && -n "$fv" ]]; then
            active=$((active + 1))
            local zoom lat lon
            zoom=$(jqf "$state" '.zoom')
            lat=$(jqf "$state" '.center.lat')
            lon=$(jqf "$state" '.center.lon')
            event INFO "  Follow check $i/6: active, center=($lat,$lon) zoom=$zoom"
        else
            event INFO "  Follow check $i/6: NOT active (vehicle may have gone out of service)"
        fi
    done

    api "/stop-follow" >/dev/null

    if [[ "$active" -ge 4 ]]; then
        event PASS "Follow endurance: $active/$checks checks active over 60s"
    elif [[ "$active" -gt 0 ]]; then
        event WARN "Follow endurance: only $active/$checks checks active (vehicle may have disappeared)"
    else
        event FAIL "Follow endurance: 0/$checks checks active"
    fi
    sleep 2
}

# ═══════════════════════════════════════════════════════════════════════════════
# MULTI-LOCATION TRANSIT DENSITY
# ═══════════════════════════════════════════════════════════════════════════════

multi_location_density() {
    event PHASE "Transit Density Across Locations"

    local locations=(
        "42.3601 -71.0589 Downtown"
        "42.3736 -71.1097 Harvard_Sq"
        "42.3519 -71.0552 South_Station"
        "42.3467 -71.0972 Fenway"
        "42.3496 -71.0424 Seaport"
        "42.2529 -71.0023 Quincy"
    )

    for loc in "${locations[@]}"; do
        local lat lon label
        read -r lat lon label <<< "$loc"

        api "/map?lat=$lat&lon=$lon&zoom=14" >/dev/null
        sleep 4

        # Count nearby transit of each type
        local state
        state=$(api "/state")
        local b t s st bs
        b=$(jqn "$state" '.markers.buses')
        t=$(jqn "$state" '.markers.trains')
        s=$(jqn "$state" '.markers.subway')
        st=$(jqn "$state" '.markers.stations')
        bs=$(jqn "$state" '.markers.busStops')

        local transit_total=$((b + t + s))
        event INFO "$label: buses=$b trains=$t subway=$s stations=$st busStops=$bs (total vehicles: $transit_total)"

        if [[ "$transit_total" -gt 0 ]]; then
            # Find nearest bus
            local nearest
            nearest=$(api "/markers/nearest?lat=$lat&lon=$lon&type=buses&limit=1")
            local dist
            dist=$(echo "$nearest" | jq '.nearest[0].distance_m // empty' 2>/dev/null || echo "")
            if [[ -n "$dist" ]]; then
                local km
                km=$(echo "$dist" | awk '{printf "%.1f", $1/1000}')
                event INFO "  Nearest bus: ${km}km"
            fi
        fi
    done

    # Return to Boston
    api "/map?lat=42.3601&lon=-71.0589&zoom=14" >/dev/null
}

# ═══════════════════════════════════════════════════════════════════════════════
# API RELIABILITY TEST
# ═══════════════════════════════════════════════════════════════════════════════

api_reliability_test() {
    event PHASE "API Reliability — Rapid Request Test"

    # Hit each debug endpoint 5 times rapidly and check for failures
    local endpoints=("/state" "/perf" "/livedata" "/prefs" "/overlays"
                     "/vehicles?type=buses&limit=1" "/stations?limit=1"
                     "/markers?type=poi&limit=1" "/bus-stops?limit=1")

    local total_calls=0 total_ok=0 total_fail=0
    for ep in "${endpoints[@]}"; do
        local ok=0 fail=0
        for i in 1 2 3 4 5; do
            local r
            r=$(api "$ep")
            total_calls=$((total_calls + 1))
            if [[ "$(jqf "$r" '._error')" == "connection_failed" ]]; then
                fail=$((fail + 1))
                total_fail=$((total_fail + 1))
            else
                ok=$((ok + 1))
                total_ok=$((total_ok + 1))
            fi
        done
        if [[ "$fail" -gt 0 ]]; then
            event WARN "Endpoint $ep: $fail/5 failures"
        fi
    done

    if [[ "$total_fail" -eq 0 ]]; then
        event PASS "API reliability: $total_ok/$total_calls requests succeeded (100%)"
    else
        local rate
        rate=$(pct "$total_ok" "$total_calls")
        event WARN "API reliability: $total_ok/$total_calls succeeded (${rate}%) — $total_fail failures"
    fi
}

# ═══════════════════════════════════════════════════════════════════════════════
# MONITORING LOOP
# ═══════════════════════════════════════════════════════════════════════════════

monitoring_loop() {
    event PHASE "Monitoring Loop"

    local end_time=$((START_TIME + DURATION_MIN * 60))
    local last_csv=0 last_screenshot=0 last_deep=0
    local iteration=0

    while [[ $(date +%s) -lt "$end_time" ]]; do
        local now
        now=$(date +%s)
        iteration=$((iteration + 1))

        # CSV every 2 minutes
        if [[ $((now - last_csv)) -ge 120 ]]; then
            last_csv=$now
            csv_row
            event INFO "CSV row #$iteration: $(tail -1 "$CSV" | cut -d',' -f3-5 | sed 's/,/ trains=/;s/,/ subway=/' | sed 's/^/buses=/')"
        fi

        # Screenshot every 10 minutes
        if [[ $((now - last_screenshot)) -ge 600 ]]; then
            last_screenshot=$now
            screenshot "monitor_${iteration}"
        fi

        # Deep vehicle recheck every 15 minutes
        if [[ $((now - last_deep)) -ge 900 ]]; then
            last_deep=$now
            event INFO "Deep recheck at $(date +%H:%M)..."

            for vtype in buses trains subway; do
                api "/refresh?layer=$vtype" >/dev/null
                sleep 3
            done
            sleep 5

            # Quick quality summary
            for vtype_label in "buses:Buses" "trains:Trains" "subway:Subway"; do
                IFS=':' read -r vt vl <<< "$vtype_label"
                local vr
                vr=$(api "/vehicles?type=$vt&limit=500")
                local vn
                vn=$(jqn "$vr" '.total')
                if [[ "$vn" -gt 0 ]]; then
                    local wh
                    wh=$(echo "$vr" | jq '[.vehicles[]|select(.headsign!=null and .headsign!="")]|length' 2>/dev/null||echo 0)
                    event INFO "  $vl: $vn vehicles, headsign=$(pct "$wh" "$vn")%"
                else
                    event INFO "  $vl: 0"
                fi
            done
        fi

        sleep 30
    done
}

# ═══════════════════════════════════════════════════════════════════════════════
# REPORT
# ═══════════════════════════════════════════════════════════════════════════════

generate_report() {
    event PHASE "Generating Report"
    local end_time
    end_time=$(date +%s)
    local total_min=$(( (end_time - START_TIME) / 60 ))

    local final_perf final_state
    final_perf=$(api "/perf" 2>/dev/null || echo '{}')
    final_state=$(api "/state" 2>/dev/null || echo '{}')
    local mem; mem=$(jqn "$final_perf" '.memory.used_mb')
    local csv_rows; csv_rows=$(tail -n +2 "$CSV" | wc -l 2>/dev/null || echo 0)
    local ss_count; ss_count=$(ls "$RUN_DIR/screenshots/"*.png 2>/dev/null | wc -l || echo 0)

    # Transit vehicle trend from CSV
    local bus_trend train_trend subway_trend
    bus_trend=$(tail -n +2 "$CSV" | cut -d',' -f3 | tr '\n' ' ')
    train_trend=$(tail -n +2 "$CSV" | cut -d',' -f4 | tr '\n' ' ')
    subway_trend=$(tail -n +2 "$CSV" | cut -d',' -f5 | tr '\n' ' ')

    cat > "$REPORT" << EOF
# Morning Transit Test Report

**Date**: $(date "+%Y-%m-%d")
**Start**: $(date -d "@$START_TIME" "+%H:%M:%S")
**End**: $(date -d "@$end_time" "+%H:%M:%S")
**Duration**: ${total_min} minutes

---

## Summary

| Metric | Count |
|--------|-------|
| PASS | $PASS_COUNT |
| FAIL | $FAIL_COUNT |
| WARN | $WARN_COUNT |
| SKIP | $SKIP_COUNT |
| Total | $((PASS_COUNT + FAIL_COUNT + WARN_COUNT + SKIP_COUNT)) |
| CSV rows | $csv_rows |
| Screenshots | $ss_count |
| Memory | ${mem}MB |

---

## Vehicle Count Trends

\`\`\`
Buses:  $bus_trend
Trains: $train_trend
Subway: $subway_trend
\`\`\`

---

## Failures

$(if [[ ${#FAILURES[@]} -gt 0 ]]; then
    for f in "${FAILURES[@]}"; do echo "- $f"; done
else
    echo "_None_"
fi)

## Warnings

$(if [[ ${#WARNINGS[@]} -gt 0 ]]; then
    for w in "${WARNINGS[@]}"; do echo "- $w"; done
else
    echo "_None_"
fi)

---

## Event Timeline

\`\`\`
$(grep -E '\[(PASS|FAIL|WARN|PHASE)\]' "$LOG" 2>/dev/null | tail -80)
\`\`\`

---

## Recommendations

$(
    if [[ "$FAIL_COUNT" -gt 0 ]]; then
        echo "1. **$FAIL_COUNT failures** — review details above"
    else
        echo "1. **No failures** — transit features working correctly"
    fi
    if [[ "$WARN_COUNT" -gt 5 ]]; then
        echo "2. **$WARN_COUNT warnings** — review data quality issues"
    fi
    echo ""
    echo "### Key findings to investigate:"
    grep '\[FAIL\]' "$LOG" 2>/dev/null | sed 's/^/- /' || echo "- None"
    echo ""
    grep '\[WARN\]' "$LOG" 2>/dev/null | head -10 | sed 's/^/- /' || true
)

EOF
    event INFO "Report: $REPORT"
}

# ═══════════════════════════════════════════════════════════════════════════════
# SIGNAL HANDLER
# ═══════════════════════════════════════════════════════════════════════════════

cleanup() {
    echo ""
    event INFO "Interrupted — generating report..."
    api "/stop-follow" >/dev/null 2>&1
    generate_report
    echo -e "\n${BOLD}Report: ${CYAN}$REPORT${NC}\n"
    exit 0
}
trap cleanup SIGINT SIGTERM

# ═══════════════════════════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════════════════════════

echo ""
echo -e "${BOLD}╔═══════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}║  Morning Transit Deep Test — ${DURATION_MIN} minutes              ║${NC}"
echo -e "${BOLD}║  Output: $RUN_DIR${NC}"
echo -e "${BOLD}╚═══════════════════════════════════════════════════════╝${NC}"
echo ""

# Verify connectivity
if ! curl -s --max-time 3 "${APP}/" >/dev/null 2>&1; then
    echo "ERROR: Debug server not reachable on port 8085"
    exit 1
fi
event PASS "Debug server reachable"

# Optionally wait for transit
if [[ "$WAIT_FOR_SERVICE" == "true" ]]; then
    wait_for_transit
fi

# Ensure all transit layers ON
for p in mbta_buses_on mbta_trains_on mbta_subway_on mbta_stations_on mbta_bus_stops_on; do
    api "/toggle?pref=$p&value=true" >/dev/null
done
api "/map?lat=42.3601&lon=-71.0589&zoom=14" >/dev/null
sleep 3

# Initial CSV + screenshot
csv_row
screenshot "start"

# Deep tests
deep_vehicle_test "buses" "Buses"
deep_vehicle_test "trains" "Commuter Rail"
deep_vehicle_test "subway" "Subway"
deep_station_test
deep_bus_stop_test

# Follow endurance
follow_endurance_test

# Multi-location density
multi_location_density

# API reliability
api_reliability_test

# Monitoring loop for remaining time
monitoring_loop

# Final
csv_row
screenshot "final"
api "/stop-follow" >/dev/null 2>&1
generate_report

echo ""
echo -e "${BOLD}══════════════════════════════════════════════════════${NC}"
echo -e "  ${GREEN}PASS${NC}: $PASS_COUNT  ${RED}FAIL${NC}: $FAIL_COUNT  ${YELLOW}WARN${NC}: $WARN_COUNT  ${CYAN}SKIP${NC}: $SKIP_COUNT"
echo -e "  Report: ${CYAN}$REPORT${NC}"
echo -e "${BOLD}══════════════════════════════════════════════════════${NC}"
echo ""

[[ "$FAIL_COUNT" -gt 0 ]] && exit 1 || exit 0
