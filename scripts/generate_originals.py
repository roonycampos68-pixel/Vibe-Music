#!/usr/bin/env python3
"""Reproducible original ambient compositions. Audio and composition dedicated to CC0-1.0."""
import math, random, struct, wave
from pathlib import Path
RATE = 22050
SECONDS = 32
ROOT = Path(__file__).resolve().parents[1] / 'app/src/main/assets/originals'
ROOT.mkdir(parents=True, exist_ok=True)
PI = math.pi
for filename, notes, seed in [
    ('midnight', [130.8128, 164.8138, 195.9977, 246.9417], 4),
    ('orbits', [110.0, 130.8128, 164.8138, 195.9977], 8),
    ('tide', [146.8324, 174.6141, 220.0, 261.6256], 12),
]:
    rng = random.Random(seed)
    phases = [rng.random() * 2 * PI for _ in notes]
    data = bytearray()
    for i in range(RATE * SECONDS):
        t = i / RATE
        fade = min(1, t / 2.5, (SECONDS - t) / 3)
        chord = sum((math.sin(2 * PI * f * t + p) + .18 * math.sin(2 * PI * f * 2.001 * t + p)) * (.72 + .28 * math.sin(.25 * t + p)) for f, p in zip(notes, phases)) / 6
        beat = t % .75
        note = notes[int(t / .75) % len(notes)] * 4
        bell = math.sin(2 * PI * note * t) * math.exp(-beat * 7) * .13
        bass = math.sin(2 * PI * notes[0] / 2 * t) * .1
        sample = max(-.95, min(.95, (chord + bell + bass) * fade * .68))
        data.extend(struct.pack('<h', int(sample * 32767)))
    with wave.open(str(ROOT / (filename + '.wav')), 'wb') as out:
        out.setnchannels(1); out.setsampwidth(2); out.setframerate(RATE); out.writeframes(data)
    print(filename, len(data), 'bytes')
