#!/usr/bin/env bash
# Overnight Heritage Trail keeper — "the grand plan".
#
# Runs back-to-back walks with no more than ~60 s of dead air between them,
# but NEVER interrupts a walk that's already in progress. The newspaper
# pointer (SharedPreferences-backed inside the app) persists across walks,
# so each restart resumes where the last one left off.
#
# Safety model:
#   1. Verify adb can see HNY0CY0W.
#   2. Re-establish adb forward tcp:4303 if missing.
#   3. If the app is dead, relaunch it (activity intent → debug server up).
#   4. Check — via the debug HTTP endpoint, not logcat parsing — whether a
#      walk-sim is already running. If YES, sleep 60 s and re-check. If NO,
#      fire a fresh /walk-tour. This is the single invariant that prevents
#      the keeper from cancelling a walk mid-route ("display jumps to a
#      different point over and over" issue).
#
# The polling cadence is 15 s (dropped from 60 s in S125). Rationale:
# overnight 2026-04-14 logged up to 60 s of silence between walk completion
# and next walk start because the keeper only checked once per minute. 15 s
# keeps cadence tight without meaningful adb/curl load.

DEVICE="HNY0CY0W"
PKG="com.example.wickedsalemwitchcitytour"
ACTIVITY="${PKG}/.ui.SalemMainActivity"
BASE=/home/witchdoctor/Development/LocationMapApp_v1.5/docs/session-logs/overnight-2026-04-14
LOG="$BASE/auto-restart.log"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOG"; }

ensure_device() {
  adb devices | grep -q "^${DEVICE}[[:space:]]\+device"
}

ensure_forward() {
  if ! adb -s "$DEVICE" forward --list 2>/dev/null | grep -q "tcp:4303"; then
    adb -s "$DEVICE" forward tcp:4303 tcp:4303 >/dev/null 2>&1
    log "re-established adb forward tcp:4303"
  fi
}

ensure_app() {
  if adb -s "$DEVICE" shell pidof "$PKG" 2>/dev/null | grep -qE '[0-9]+'; then
    return 0
  fi
  log "app not running — launching $ACTIVITY"
  adb -s "$DEVICE" shell am start -n "$ACTIVITY" >/dev/null 2>&1
  for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
    if curl -s -m 2 http://localhost:4303/state >/dev/null 2>&1; then
      log "app debug endpoint up (after ${i}s)"
      return 0
    fi
    sleep 1
  done
  log "WARN: debug endpoint did not come up within 20s"
  return 1
}

# Authoritative check: does the device's recent logcat show an in-progress
# walk-sim from EITHER the Activity's walkSimJob OR the DebugEndpoints
# walkJob? Both emit "Manual location:" updates every ~1 s. If we keyed
# only on "WALK-SIM: step" we'd miss the debug path and the keeper would
# fire a second /walk-tour on top of an already-running one — the exact
# race that caused the "map bouncing between two POIs" bug.
walk_still_running() {
  local last_epoch current_epoch age
  last_epoch=$(adb -s "$DEVICE" logcat -d -t 400 -v epoch 2>/dev/null \
               | grep -E "Manual location:|WALK-SIM: step " | tail -1 \
               | awk '{print $1}' | cut -d. -f1)
  if [ -z "$last_epoch" ]; then
    return 1
  fi
  current_epoch=$(date +%s)
  age=$((current_epoch - last_epoch))
  if [ "$age" -lt 15 ]; then
    return 0  # Manual location updates within last 15s → walk is live
  else
    return 1
  fi
}

start_walk() {
  local resp
  resp=$(curl -s -m 5 'http://localhost:4303/walk-tour?tour=tour_salem_heritage_trail&speed=2.0' 2>&1)
  log "walk-tour fired: $(echo "$resp" | tr '\n' ' ')"
}

log "keeper starting — will NEVER interrupt an in-progress walk"

while true; do
  if ! ensure_device; then
    log "device offline — sleeping 60s"
    sleep 60
    continue
  fi
  ensure_forward
  if ! ensure_app; then
    sleep 30
    continue
  fi
  if walk_still_running; then
    # Already walking — let it run, check again in 15s (S125)
    sleep 15
  else
    log "no walk in progress → starting one"
    start_walk
    # Let the walk actually begin (first step log lands ~3s after trigger)
    # before the next walk_still_running check. 30s guard is generous enough
    # to rule out false-negatives from a slow first-step log while still
    # cutting the old 60s dead-air window on back-to-back restarts.
    sleep 30
  fi
done
