#!/usr/bin/env bash
# Clear corrupted Bark model cache and re-download
set -e

echo "Clearing Bark model cache..."
rm -rf ~/.cache/suno/bark_v0/
mkdir -p ~/.cache/suno/bark_v0/

echo "Re-downloading models and running test..."
~/AI-Studio/bark/venv/bin/python -c "
from bark import SAMPLE_RATE, generate_audio, preload_models
import soundfile as sf
print('Downloading fresh models...')
preload_models()
print('Test generate...')
audio = generate_audio('Welcome to Salem, mortal!', history_prompt='v2/en_speaker_6')
sf.write('/home/witchdoctor/AI-Studio/bark/test_output.wav', audio, SAMPLE_RATE)
print(f'OK! {len(audio)/SAMPLE_RATE:.1f} sec')
"

echo ""
echo "Models clean. Now run: bash tools/generate-splash-audio.sh"
