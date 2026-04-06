#!/usr/bin/env bash
# Fix Bark install: reuse system PyTorch, patch weights_only issue
set -e

BARK_DIR="/home/witchdoctor/AI-Studio/bark"
HF_TOKEN="${HF_TOKEN:?Set HF_TOKEN env var — see ~/Development/OMEN/docs/credential-audit-2026-04-05.md}"

# Clean up any previous attempts
rm -rf /root/AI-Studio/bark 2>/dev/null || true
rm -rf "$BARK_DIR" 2>/dev/null || true
pip cache purge 2>/dev/null || true

# Create venv with --system-site-packages to inherit PyTorch + CUDA
sudo -u witchdoctor mkdir -p "$BARK_DIR"
sudo -u witchdoctor python3 -m venv --system-site-packages "$BARK_DIR/venv"

# Install Bark WITHOUT pulling torch again
sudo -u witchdoctor "$BARK_DIR/venv/bin/pip" install --upgrade pip
sudo -u witchdoctor "$BARK_DIR/venv/bin/pip" install --no-deps git+https://github.com/suno-ai/bark.git
sudo -u witchdoctor "$BARK_DIR/venv/bin/pip" install encodec transformers tokenizers huggingface-hub safetensors
sudo -u witchdoctor "$BARK_DIR/venv/bin/pip" install scipy soundfile pydub librosa numpy

# Patch torch.load weights_only issue
GENFILE=$(find "$BARK_DIR/venv" -name "generation.py" -path "*/bark/*" 2>/dev/null | head -1)
if [ -z "$GENFILE" ]; then
    # Check system site-packages too
    GENFILE=$(find /usr/lib/python3*/dist-packages -name "generation.py" -path "*/bark/*" 2>/dev/null | head -1)
fi
if [ -z "$GENFILE" ]; then
    GENFILE=$(find "$BARK_DIR/venv/lib" -name "generation.py" 2>/dev/null | head -1)
fi

if [ -n "$GENFILE" ]; then
    echo "Patching $GENFILE"
    sed -i 's/torch\.load(ckpt_path, map_location=device)/torch.load(ckpt_path, map_location=device, weights_only=False)/g' "$GENFILE"
    sed -i 's/torch\.load(ckpt_path)/torch.load(ckpt_path, weights_only=False)/g' "$GENFILE"
    echo "Patched OK"
else
    echo "WARNING: bark/generation.py not found"
    find "$BARK_DIR" -name "*.py" -path "*/bark/*" 2>/dev/null
fi

# Set up HuggingFace token
sudo -u witchdoctor mkdir -p /home/witchdoctor/.cache/huggingface
echo "$HF_TOKEN" > /home/witchdoctor/.cache/huggingface/token
chown -R witchdoctor:witchdoctor /home/witchdoctor/.cache/huggingface

# Ensure model cache dirs are owned by witchdoctor
sudo -u witchdoctor mkdir -p /home/witchdoctor/.cache/suno
chown -R witchdoctor:witchdoctor /home/witchdoctor/.cache/suno

# Test
echo ""
echo "=== Testing Bark ==="
export HF_TOKEN="$HF_TOKEN"
sudo -u witchdoctor -E "$BARK_DIR/venv/bin/python" -c "
import torch
print(f'PyTorch {torch.__version__}, CUDA: {torch.cuda.is_available()}, GPU: {torch.cuda.get_device_name(0)}')

from bark import SAMPLE_RATE, generate_audio, preload_models
print('Loading models (downloads ~5GB on first run)...')
preload_models()
print('Generating test clip...')
audio = generate_audio('Welcome to Salem, mortal!', history_prompt='v2/en_speaker_6')
import soundfile as sf
sf.write('$BARK_DIR/test_output.wav', audio, SAMPLE_RATE)
print(f'Generated {len(audio)/SAMPLE_RATE:.1f} sec → $BARK_DIR/test_output.wav')
print('=== Bark is ready! ===')
"
