#!/usr/bin/env bash
# ============================================================
# Bark TTS Setup — WickedSalemWitchCityTour Voice Generation
# Run with: sudo bash tools/setup-bark.sh
# ============================================================
set -e

echo "=== System packages (sudo required) ==="

# Audio processing tools
apt-get install -y \
    sox \
    libsox-fmt-mp3 \
    libportaudio2 \
    portaudio19-dev \
    libsndfile1-dev \
    lame \
    vorbis-tools \
    normalize-audio

# Python build deps (for scipy, soundfile, etc.)
apt-get install -y \
    python3-dev \
    python3-venv

echo ""
echo "=== System packages installed ==="
echo ""
echo "=== Now run the non-sudo setup as your user: ==="
echo "    bash tools/setup-bark-user.sh"
echo ""
