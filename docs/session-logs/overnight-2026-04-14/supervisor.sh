#!/usr/bin/env bash
# Overnight supervisor — guarantees the three keeper processes stay alive.
# Checks every 5 min; if any of keeper / metrics / logcat-tail has died,
# relaunches it. Writes status to supervisor.log.

BASE=/home/witchdoctor/Development/LocationMapApp_v1.5/docs/session-logs/overnight-2026-04-14
LOG="$BASE/supervisor.log"

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOG"; }

is_running() {
  # Pattern match against `ps -ef` for this exact script path.
  local pattern="$1"
  pgrep -f "$pattern" >/dev/null 2>&1
}

start_keeper() {
  nohup bash "$BASE/auto-restart-walk.sh" >/dev/null 2>&1 &
  disown 2>/dev/null
  log "started keeper (pid $!)"
}

start_metrics() {
  nohup bash "$BASE/metrics-collector.sh" >/dev/null 2>&1 &
  disown 2>/dev/null
  log "started metrics collector (pid $!)"
}

start_logcat() {
  # Append (>>) so we preserve logs from a prior instance that died.
  nohup adb -s HNY0CY0W logcat -T 1 -v time >> "$BASE/logcat.log" 2>&1 &
  disown 2>/dev/null
  log "started logcat tail (pid $!)"
}

rotate_logcat_if_huge() {
  local max_bytes=$((200 * 1024 * 1024))  # 200 MB ceiling per file
  local current
  current=$(stat -c%s "$BASE/logcat.log" 2>/dev/null || echo 0)
  if [ "$current" -lt "$max_bytes" ]; then
    return
  fi
  # Rotate: stop the tail, mv current to a timestamped archive, gzip in
  # background, restart the tail so a new logcat.log starts fresh.
  local stamp
  stamp=$(date '+%Y%m%d-%H%M%S')
  local archive="$BASE/logcat-${stamp}.log"
  log "logcat.log hit ${current} bytes (>= ${max_bytes}) — rotating to ${archive}.gz"
  pkill -f "logcat -T 1 -v time" 2>/dev/null
  sleep 1
  mv "$BASE/logcat.log" "$archive" 2>/dev/null
  # gzip in background; keeps compressed archives around forever
  (gzip "$archive" 2>/dev/null) &
  start_logcat
  log "logcat tail restarted on fresh logcat.log"
}

log "supervisor starting"

while true; do
  if ! is_running "auto-restart-walk.sh"; then
    log "WARN: keeper is down — restarting"
    start_keeper
  fi
  if ! is_running "metrics-collector.sh"; then
    log "WARN: metrics collector is down — restarting"
    start_metrics
  fi
  # Logcat tail: unique signature "logcat -T 1 -v time" + output redirected to logcat.log
  if ! pgrep -f "logcat -T 1 -v time" >/dev/null 2>&1; then
    log "WARN: logcat tail is down — restarting"
    start_logcat
  fi
  rotate_logcat_if_huge
  sleep 300  # 5 min
done
