#!/usr/bin/env bash
# ============================================================
# Bark TTS User Setup — no sudo needed
# Run AFTER: sudo bash tools/setup-bark.sh
# ============================================================
set -e

BARK_DIR="$HOME/AI-Studio/bark"

echo "=== Creating Bark venv at $BARK_DIR ==="
mkdir -p "$BARK_DIR"
python3 -m venv "$BARK_DIR/venv"
source "$BARK_DIR/venv/bin/activate"

echo "=== Installing Bark + dependencies ==="
pip install --upgrade pip

# Bark from suno-ai (text-to-speech with voice cloning)
pip install git+https://github.com/suno-ai/bark.git

# Audio processing
pip install \
    scipy \
    soundfile \
    pydub \
    librosa \
    numpy

# Bark will auto-download models on first run (~5GB to ~/.cache/suno/bark_v0/)
# Models: text_2.pt, coarse_2.pt, fine_2.pt, encodec (~1.3GB each)

echo ""
echo "=== Verifying installation ==="
python -c "
from bark import SAMPLE_RATE, generate_audio, preload_models
import torch
print(f'Bark imported OK')
print(f'CUDA available: {torch.cuda.is_available()}')
print(f'GPU: {torch.cuda.get_device_name(0)}')
print(f'Sample rate: {SAMPLE_RATE}')
print()
print('Preloading models (first run downloads ~5GB)...')
preload_models()
print('Models loaded!')
print()
print('Quick test generation...')
audio = generate_audio('Hello from Salem!', history_prompt='v2/en_speaker_6')
print(f'Generated {len(audio)} samples ({len(audio)/SAMPLE_RATE:.1f} sec)')
print()
print('=== Bark is ready! ===')
"

echo ""
echo "=== Setup complete ==="
echo "Bark venv: $BARK_DIR/venv/bin/python"
echo "Run voice generator: $BARK_DIR/venv/bin/python tools/generate-voice-clips.py"
echo ""

deactivate
