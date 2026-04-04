#!/bin/bash
# Recycle the cache proxy server (kill existing + restart)

PROXY_DIR="$(dirname "$0")/../cache-proxy"

# Kill any existing proxy on port 4300
PIDS=$(lsof -ti tcp:4300 2>/dev/null)
if [ -n "$PIDS" ]; then
  echo "Killing existing proxy (PIDs: $PIDS)..."
  kill -9 $PIDS 2>/dev/null
  sleep 1
else
  echo "No existing proxy found on port 4300."
fi

echo "Starting proxy..."
cd "$PROXY_DIR" || { echo "ERROR: cache-proxy directory not found"; exit 1; }

DATABASE_URL=postgres://witchdoctor:fuckers123@localhost/locationmapapp \
JWT_SECRET=$(openssl rand -hex 32) \
OPENSKY_CLIENT_ID=deanmauriceellis-api-client \
OPENSKY_CLIENT_SECRET=6m3uBQ5HXwSzJeLemRJK12G8Ux4L5veR \
node server.js
