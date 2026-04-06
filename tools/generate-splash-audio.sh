#!/usr/bin/env bash
# Splash audio: DEMON warlock (5 flavors) + HIGH-PITCHED cackles
set -e

rm -rf tools/splash-audio/raw tools/splash-audio/fx tools/splash-audio/final
mkdir -p tools/splash-audio/raw
mkdir -p tools/splash-audio/fx
mkdir -p tools/splash-audio/final

# ── STEP 1: Generate raw clips ──
~/AI-Studio/bark/venv/bin/python << 'PYEOF'
import numpy as np
import soundfile as sf
from bark import SAMPLE_RATE, generate_audio, preload_models

print("Loading Bark models...")
preload_models()

OUT = "tools/splash-audio/raw"

# Deep warlock — 3 raw takes, we'll make 5 demon flavors from them in sox
warlock_takes = [
    ("warlock_a", "v2/en_speaker_6",
     "THIS IS THE WICKED SALEM WITCH TOUR! EVERYONE IS INVITED AT THEIR OWN RISK!"),
    ("warlock_b", "v2/en_speaker_6",
     "THIS IS THE WICKED SALEM WITCH TOUR! YOU ARE ALL INVITED AT YOUR OWN RISK!"),
    ("warlock_c", "v2/en_speaker_6",
     "THE WICKED SALEM WITCH TOUR! EVERYONE IS INVITED AT THEIR OWN RISK!"),
]

# Cackles — screaming witch
cackle_takes = [
    ("cackle_a", "v2/en_speaker_9",
     "[laughs] AHAHAHAHAHA! [laughs] [laughs] [laughs]"),
    ("cackle_b", "v2/en_speaker_9",
     "AHAHAHAHAHA! HEHEHEHE! [laughs] [laughs] [laughs]"),
    ("cackle_c", "v2/en_speaker_9",
     "EEEEHEHEHEHEHE! AHAHAHA! [laughs] [laughs] [laughs]"),
]

print("\n=== WARLOCK TAKES ===")
for name, voice, text in warlock_takes:
    print(f"  {name}: {text}")
    audio = generate_audio(text, history_prompt=voice)
    sf.write(f"{OUT}/{name}.wav", audio, SAMPLE_RATE)
    print(f"  → {len(audio)/SAMPLE_RATE:.1f}s")

print("\n=== CACKLE TAKES ===")
for name, voice, text in cackle_takes:
    print(f"  {name}: {text}")
    audio = generate_audio(text, history_prompt=voice)
    sf.write(f"{OUT}/{name}.wav", audio, SAMPLE_RATE)
    print(f"  → {len(audio)/SAMPLE_RATE:.1f}s")

print("\nRaw clips done.")
PYEOF

# ── STEP 2: 5 DEMON FLAVORS from the best warlock take ──
echo ""
echo "=== 5 DEMON WARLOCK FLAVORS ==="

RAW="tools/splash-audio/raw"
FX="tools/splash-audio/fx"

# Apply all 5 flavors to each warlock take
for w in "$RAW"/warlock_*.wav; do
    base=$(basename "$w" .wav)

    # DEMON 1: octave down + overdrive + phaser + light reverb
    sox "$w" "$FX/${base}_demon1_phaser.wav" \
        pitch -800 overdrive 12 phaser 0.6 0.85 2 0.3 1.5 -t reverb 35 gain -4
    echo "  ${base}_demon1_phaser  (octave down, overdrive, heavy phaser, light reverb)"

    # DEMON 2: octave down + double phaser + slight echo
    sox "$w" "$FX/${base}_demon2_dblphaser.wav" \
        pitch -800 overdrive 10 phaser 0.7 0.9 1.5 0.35 2 -t phaser 0.4 0.8 2.5 0.2 1.8 -s reverb 25 echo 0.8 0.5 30 0.3 gain -5
    echo "  ${base}_demon2_dblphaser  (octave down, double phaser, whisper echo)"

    # DEMON 3: octave down + phaser + flanger + dry
    sox "$w" "$FX/${base}_demon3_flanger.wav" \
        pitch -800 overdrive 15 phaser 0.5 0.85 1 0.24 2 -t flanger 1 3 0 70 0.5 sine reverb 20 gain -4
    echo "  ${base}_demon3_flanger  (octave down, phaser, flanger, minimal reverb)"

    # DEMON 4: 10 semitones down + chorus + phaser (legion of voices)
    sox "$w" "$FX/${base}_demon4_legion.wav" \
        pitch -1000 overdrive 10 chorus 0.6 0.9 50 0.4 0.25 2 -t phaser 0.5 0.85 1.5 0.3 2 -t reverb 30 gain -5
    echo "  ${base}_demon4_legion  (10st down, chorus+phaser, legion of demons)"

    # DEMON 5: octave down + heavy phaser + tremolo (pulsing demon)
    sox "$w" "$FX/${base}_demon5_pulse.wav" \
        pitch -800 overdrive 12 phaser 0.7 0.9 2 0.35 2.5 -t tremolo 3 50 reverb 25 gain -4
    echo "  ${base}_demon5_pulse  (octave down, phaser, tremolo pulse, dry)"
done

# ── STEP 3: HIGH-PITCHED CACKLES with echo/reverb ──
echo ""
echo "=== HIGH-PITCHED CACKLES ==="

for c in "$RAW"/cackle_*.wav; do
    base=$(basename "$c" .wav)

    # CACKLE 1: up 6 semitones + heavy reverb + echo
    sox "$c" "$FX/${base}_high1.wav" \
        pitch 600 reverb 75 60 90 echo 0.8 0.7 50 0.5 gain -4
    echo "  ${base}_high1  (+6st, heavy reverb, echo)"

    # CACKLE 2: up full octave + cathedral reverb + decay echo
    sox "$c" "$FX/${base}_high2.wav" \
        pitch 1200 reverb 85 70 100 echo 0.8 0.8 40 0.5 echo 0.8 0.6 80 0.4 gain -5
    echo "  ${base}_high2  (+12st full octave up, cathedral, double echo)"

    # CACKLE 3: up 9 semitones + massive reverb + triple echo
    sox "$c" "$FX/${base}_high3.wav" \
        pitch 900 reverb 90 75 100 100 40 echo 0.8 0.7 30 0.5 echo 0.8 0.6 60 0.4 echo 0.8 0.5 90 0.3 gain -6
    echo "  ${base}_high3  (+9st, massive reverb, triple echo decay)"

    # CACKLE 4: up 6st + phaser + reverb (otherworldly shriek)
    sox "$c" "$FX/${base}_high4.wav" \
        pitch 600 phaser 0.6 0.85 2 0.3 1.5 -t reverb 70 50 90 echo 0.8 0.7 60 0.5 gain -4
    echo "  ${base}_high4  (+6st, phaser, reverb, echo — otherworldly)"

    # CACKLE 5: up 1.5 octaves + echo canyon
    sox "$c" "$FX/${base}_high5.wav" \
        pitch 1800 reverb 80 60 100 echo 0.9 0.8 50 0.6 echo 0.9 0.7 100 0.5 gain -6
    echo "  ${base}_high5  (+18st 1.5 octaves up, echo canyon)"
done

# ── STEP 4: Combined finals ──
echo ""
echo "=== COMBINING FINALS ==="

FINAL="tools/splash-audio/final"
sox -n -r 24000 -c 1 "$FX/silence.wav" trim 0 0.7

# Best demon + best cackle combos
sox "$FX/warlock_a_demon1_phaser.wav" "$FX/silence.wav" "$FX/cackle_a_high2.wav" \
    "$FINAL/splash_01.wav"
echo "  → splash_01  (demon phaser + octave-up cackle)"

sox "$FX/warlock_a_demon2_dblphaser.wav" "$FX/silence.wav" "$FX/cackle_b_high3.wav" \
    "$FINAL/splash_02.wav"
echo "  → splash_02  (demon dblphaser + 9st cackle triple echo)"

sox "$FX/warlock_b_demon4_legion.wav" "$FX/silence.wav" "$FX/cackle_c_high5.wav" \
    "$FINAL/splash_03.wav"
echo "  → splash_03  (demon legion + 1.5 octave cackle canyon)"

sox "$FX/warlock_c_demon5_pulse.wav" "$FX/silence.wav" "$FX/cackle_a_high4.wav" \
    "$FINAL/splash_04.wav"
echo "  → splash_04  (demon pulse + otherworldly cackle)"

sox "$FX/warlock_b_demon3_flanger.wav" "$FX/silence.wav" "$FX/cackle_b_high1.wav" \
    "$FINAL/splash_05.wav"
echo "  → splash_05  (demon flanger + 6st reverb cackle)"

echo ""
TOTAL=$(find "$FX" -name "*.wav" ! -name "silence.wav" | wc -l)
echo "============================================================"
echo "DONE!"
echo "  Raw:     $RAW/    (6 clips)"
echo "  Effects: $FX/     ($TOTAL files)"
echo "  Finals:  $FINAL/  (5 combined splashes)"
echo ""
echo "  Warlock: 5 demon flavors × 3 takes = 15 files"
echo "  Cackle:  5 high-pitch styles × 3 takes = 15 files"
echo "============================================================"
