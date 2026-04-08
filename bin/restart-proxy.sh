#!/bin/bash
# Recycle the cache proxy server (kill existing + restart)
#
# Reads credentials from cache-proxy/.env (gitignored). See cache-proxy/.env.example
# for the template. Per OMEN-002, no credential values may appear in this script.

PROXY_DIR="$(dirname "$0")/../cache-proxy"
ENV_FILE="$PROXY_DIR/.env"

# Kill any existing proxy on port 4300
PIDS=$(lsof -ti tcp:4300 2>/dev/null)
if [ -n "$PIDS" ]; then
  echo "Killing existing proxy (PIDs: $PIDS)..."
  kill -9 $PIDS 2>/dev/null
  sleep 1
else
  echo "No existing proxy found on port 4300."
fi

# Load environment from cache-proxy/.env
if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: $ENV_FILE not found. Copy cache-proxy/.env.example and fill in real values." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

# JWT_SECRET defaults to a fresh per-boot random secret if .env leaves it blank
if [ -z "$JWT_SECRET" ]; then
  export JWT_SECRET=$(openssl rand -hex 32)
fi

echo "Starting proxy..."
cd "$PROXY_DIR" || { echo "ERROR: cache-proxy directory not found"; exit 1; }
node server.js
