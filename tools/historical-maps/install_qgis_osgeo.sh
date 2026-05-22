#!/bin/bash
# install_qgis_osgeo.sh — replace the Ubuntu-shipped qgis (missing
# Georeferencer in 24.04's build) with the OSGeo official QGIS LTR package
# which includes Georeferencer as a built-in core tool.
#
# Run with sudo:
#   sudo ./install_qgis_osgeo.sh
#
# Ubuntu 24.04 (noble) only — verified target.
set -euo pipefail

if [ "$EUID" -ne 0 ]; then
  echo "ERROR: run with sudo" >&2
  exit 1
fi

CODENAME=$(lsb_release -cs)
if [ "$CODENAME" != "noble" ]; then
  echo "WARNING: This script was written for Ubuntu 24.04 (noble); detected '$CODENAME'."
  echo "Press Ctrl-C to abort, or Enter to continue at your own risk."
  read -r
fi

echo "==== 1/6 — installing prerequisites"
apt-get update -qq
apt-get install -y gnupg software-properties-common ca-certificates wget

echo "==== 2/6 — adding OSGeo QGIS signing key"
mkdir -p /etc/apt/keyrings
wget -q -O /etc/apt/keyrings/qgis-archive-keyring.gpg \
  https://download.qgis.org/downloads/qgis-archive-keyring.gpg
chmod 0644 /etc/apt/keyrings/qgis-archive-keyring.gpg

echo "==== 3/6 — registering OSGeo QGIS apt source (LTR channel)"
cat >/etc/apt/sources.list.d/qgis.sources <<EOF
Types: deb deb-src
URIs: https://qgis.org/ubuntu-ltr
Suites: ${CODENAME}
Architectures: amd64
Components: main
Signed-By: /etc/apt/keyrings/qgis-archive-keyring.gpg
EOF

echo "==== 4/6 — apt update (will fetch OSGeo's package list)"
apt-get update

echo "==== 5/6 — installing qgis + qgis-providers from OSGeo"
# Force OSGeo's qgis to replace Ubuntu's. The OSGeo package includes
# Georeferencer built into the core (no separate plugin needed).
DEBIAN_FRONTEND=noninteractive apt-get install -y --allow-downgrades \
  qgis qgis-plugin-grass qgis-providers python3-qgis

echo "==== 6/6 — verifying Georeferencer is in this build"
if strings /usr/lib/x86_64-linux-gnu/libqgis_app.so* 2>/dev/null | grep -qi "georef"; then
  echo "PASS: 'georef' symbols found in libqgis_app — Georeferencer compiled in"
else
  echo "WARN: 'georef' symbols not found by strings — may still work via Raster menu; launch QGIS and check"
fi

echo
echo "Install complete. Launch with:"
echo "  qgis tools/historical-maps/1692/georef_workspace.qgs"
echo
echo "Then: Raster -> Georeferencer..."
