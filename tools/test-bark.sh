#!/usr/bin/env bash
~/AI-Studio/bark/venv/bin/python -c "
import torch
print(f'PyTorch {torch.__version__}, CUDA: {torch.cuda.is_available()}')
from bark import SAMPLE_RATE, generate_audio, preload_models
print('Loading models...')
preload_models()
print('Generating test...')
audio = generate_audio('Welcome to Salem, mortal!', history_prompt='v2/en_speaker_6')
import soundfile as sf
sf.write('/home/witchdoctor/AI-Studio/bark/test_output.wav', audio, SAMPLE_RATE)
print(f'Done! {len(audio)/SAMPLE_RATE:.1f} sec')
"
