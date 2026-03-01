#!/usr/bin/env bash
#
# run-full-test-suite.sh — Master test runner
#
# Chains: overnight test → waits for transit → morning deep transit test
# Fully automated, no prompts. Run it and walk away.
#
# Usage:
#   ./run-full-test-suite.sh                     # Full suite (8hr overnight + 1hr morning)
#   ./run-full-test-suite.sh --overnight 360     # 6hr overnight + 1hr morning
#   ./run-full-test-suite.sh --morning-only      # Skip overnight, just morning test
#   ./run-full-test-suite.sh --quick             # 30min overnight + 30min morning
#

set -uo pipefail

OVERNIGHT_MIN=480
MORNING_MIN=60
MORNING_ONLY=false
SKIP_SETUP=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --overnight)      OVERNIGHT_MIN="$2"; shift 2 ;;
        --morning)        MORNING_MIN="$2"; shift 2 ;;
        --morning-only)   MORNING_ONLY=true; shift ;;
        --skip-setup)     SKIP_SETUP=true; shift ;;
        --quick)          OVERNIGHT_MIN=30; MORNING_MIN=30; shift ;;
        -h|--help)
            echo "Usage: $0 [--overnight MIN] [--morning MIN] [--morning-only] [--quick] [--skip-setup]"
            exit 0 ;;
        *) echo "Unknown: $1"; exit 1 ;;
    esac
done

BOLD='\033[1m'
CYAN='\033[0;36m'
GREEN='\033[0;32m'
DIM='\033[2m'
NC='\033[0m'

SUITE_LOG="overnight-runs/suite_$(date +%Y-%m-%d_%H%M).log"
mkdir -p overnight-runs

log() { echo "[$(date '+%H:%M:%S')] $*" | tee -a "$SUITE_LOG"; }

echo ""
echo -e "${BOLD}╔═══════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}║  LocationMapApp — Full Test Suite                        ║${NC}"
if [[ "$MORNING_ONLY" == "true" ]]; then
echo -e "${BOLD}║  Mode: Morning transit test only (${MORNING_MIN} min)              ║${NC}"
else
echo -e "${BOLD}║  Mode: Overnight (${OVERNIGHT_MIN}min) + Morning transit (${MORNING_MIN}min)     ║${NC}"
fi
echo -e "${BOLD}║  Log:  $SUITE_LOG${NC}"
echo -e "${BOLD}╚═══════════════════════════════════════════════════════════╝${NC}"
echo ""

# ── Ensure connectivity ──
log "Checking prerequisites..."

if [[ "$SKIP_SETUP" == "false" ]]; then
    adb forward tcp:8085 tcp:8085 2>/dev/null || true
fi

if ! curl -s --max-time 3 "http://localhost:8085/" >/dev/null 2>&1; then
    log "ERROR: Debug server not reachable on port 8085"
    log "Make sure: app running + adb forward tcp:8085 tcp:8085"
    exit 1
fi
log "Debug server: OK"

if ! curl -s --max-time 3 "http://10.0.0.4:3000/cache/stats" >/dev/null 2>&1; then
    log "WARNING: Cache proxy not reachable — some tests limited"
else
    log "Cache proxy: OK"
fi

for cmd in jq bc; do
    command -v "$cmd" >/dev/null || { log "ERROR: $cmd not found"; exit 1; }
done

# ── Phase A: Overnight Test ──
if [[ "$MORNING_ONLY" == "false" ]]; then
    log "═══ PHASE A: Overnight Test (${OVERNIGHT_MIN} min) ═══"
    log "Starting at $(date '+%Y-%m-%d %H:%M:%S'), ends ~$(date -d "+${OVERNIGHT_MIN} minutes" '+%H:%M')"

    ./overnight-test.sh --duration "$OVERNIGHT_MIN" --skip-setup 2>&1 | tee -a "$SUITE_LOG"
    OVERNIGHT_EXIT=$?

    log "Overnight test finished (exit code: $OVERNIGHT_EXIT)"

    # Find the overnight report
    OVERNIGHT_DIR=$(ls -td overnight-runs/20* 2>/dev/null | head -1)
    if [[ -n "$OVERNIGHT_DIR" && -f "$OVERNIGHT_DIR/report.md" ]]; then
        log "Overnight report: $OVERNIGHT_DIR/report.md"
    fi
fi

# ── Phase B: Morning Transit Test ──
log ""
log "═══ PHASE B: Morning Transit Deep Test (${MORNING_MIN} min) ═══"

# Check if transit is likely active
HOUR=$(date +%-H)
if [[ "$HOUR" -ge 1 && "$HOUR" -le 4 ]]; then
    log "Transit unlikely active (hour $HOUR) — using --wait-for-service"
    WAIT_FLAG="--wait-for-service"
else
    log "Transit should be active (hour $HOUR)"
    WAIT_FLAG=""
fi

./morning-transit-test.sh --duration "$MORNING_MIN" $WAIT_FLAG 2>&1 | tee -a "$SUITE_LOG"
MORNING_EXIT=$?

log "Morning test finished (exit code: $MORNING_EXIT)"

# Find the morning report
MORNING_DIR=$(ls -td overnight-runs/morning_* 2>/dev/null | head -1)
if [[ -n "$MORNING_DIR" && -f "$MORNING_DIR/report.md" ]]; then
    log "Morning report: $MORNING_DIR/report.md"
fi

# ── Summary ──
echo ""
echo -e "${BOLD}══════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  FULL TEST SUITE COMPLETE${NC}"
echo -e "${BOLD}══════════════════════════════════════════════════════════${NC}"
echo -e "  Started:  $(head -1 "$SUITE_LOG" | grep -o '[0-9:]*')"
echo -e "  Finished: $(date '+%H:%M:%S')"
if [[ "$MORNING_ONLY" == "false" && -n "${OVERNIGHT_DIR:-}" ]]; then
echo -e "  Overnight report: ${CYAN}${OVERNIGHT_DIR}/report.md${NC}"
fi
if [[ -n "${MORNING_DIR:-}" ]]; then
echo -e "  Morning report:   ${CYAN}${MORNING_DIR}/report.md${NC}"
fi
echo -e "  Suite log:        ${CYAN}$SUITE_LOG${NC}"
echo ""

exit $((OVERNIGHT_EXIT + MORNING_EXIT))
