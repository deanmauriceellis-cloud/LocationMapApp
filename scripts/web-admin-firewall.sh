#!/usr/bin/env bash
#
# web-admin-firewall.sh — open/close the host firewall for the web admin portal.
#
# The dev servers (Vite :4302, cache-proxy :4300) already bind 0.0.0.0; ufw is the
# only thing blocking other LAN/WiFi machines. This toggles the ufw rules, scoped
# to the LAN subnet so the credentialed admin portal is never exposed beyond it.
#
# Browser → Vite :4302 → (server-side localhost) cache-proxy :4300 for /api/* calls,
# so :4302 alone covers the whole historian/admin workflow. :4300 is opened too only
# because the Witch Trials tab makes 2 cross-origin calls straight to :4300.
#
# Usage (needs sudo — it edits ufw):
#   sudo ./scripts/web-admin-firewall.sh on       # allow LAN → 4300,4302
#   sudo ./scripts/web-admin-firewall.sh off      # remove those rules
#   sudo ./scripts/web-admin-firewall.sh status   # show ufw + whether servers are up
#
# Portal (once on):  http://10.0.0.229:4302/admin   (login: admin / historian)

set -euo pipefail

LAN_SUBNET="10.0.0.0/24"
PORTS=(4302 4300)            # 4302 = Vite web admin, 4300 = cache-proxy
ADMIN_URL="http://10.0.0.229:4302/admin"

if [[ "${EUID}" -ne 0 ]]; then
  echo "This script edits ufw and must run as root. Re-run with: sudo $0 ${1:-status}" >&2
  exit 1
fi

action="${1:-status}"

case "${action}" in
  on)
    for p in "${PORTS[@]}"; do
      # `ufw allow` is idempotent (skips duplicate rules); comment tags them for off/status.
      ufw allow from "${LAN_SUBNET}" to any port "${p}" proto tcp comment "web-admin-portal" >/dev/null
      echo "allowed ${LAN_SUBNET} -> tcp/${p}"
    done
    echo
    echo "Web admin portal is reachable from the ${LAN_SUBNET} LAN at: ${ADMIN_URL}"
    ;;
  off)
    # Delete by reconstructing the exact rule spec (comment isn't part of the match).
    for p in "${PORTS[@]}"; do
      if ufw delete allow from "${LAN_SUBNET}" to any port "${p}" proto tcp >/dev/null 2>&1; then
        echo "removed ${LAN_SUBNET} -> tcp/${p}"
      else
        echo "no rule for tcp/${p} (already off)"
      fi
    done
    ;;
  status)
    echo "=== ufw rules tagged 'web-admin-portal' ==="
    ufw status verbose | grep -E "web-admin-portal|Status:" || echo "(no portal rules active)"
    echo
    echo "=== servers listening? ==="
    for p in "${PORTS[@]}"; do
      if ss -ltn 2>/dev/null | grep -q ":${p} "; then
        echo "  tcp/${p} UP"
      else
        echo "  tcp/${p} DOWN  (start it before the portal will work)"
      fi
    done
    ;;
  *)
    echo "usage: sudo $0 {on|off|status}" >&2
    exit 2
    ;;
esac
