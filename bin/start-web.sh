#!/bin/bash
# Start the web app dev server

WEB_DIR="$(dirname "$0")/../web"

cd "$WEB_DIR" || { echo "ERROR: web directory not found"; exit 1; }

echo "Starting web dev server on http://localhost:4302 ..."
npm run dev
