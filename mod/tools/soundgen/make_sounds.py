"""Generates Hollow Client's UI sounds from scratch (simple synthesis, fixed random seed) and encodes them to OGG.

Run from the mod folder:  python tools/soundgen/make_sounds.py src/main/resources/assets/hollowclient/sounds
Needs ffmpeg on PATH (for Vorbis encoding). All sounds are original.
"""

import math
import os
import random
import struct
import subprocess
import sys
import tempfile
import wave

RATE = 44100
rng = random.Random(1717)


def env(t, attack, decay):
    """Fast attack, exponential decay."""
    if t < attack:
        return t / attack
    return math.exp(-(t - attack) / decay)


def tone(freq_fn, length, attack=0.003, decay=0.05, vol=0.6, harmonics=((1, 1.0),)):
    out = []
    phase = [0.0] * len(harmonics)
    n = int(RATE * length)
    for i in range(n):
        t = i / RATE
        f = freq_fn(t)
        s = 0.0
        for k, (mult, amp) in enumerate(harmonics):
            phase[k] += 2 * math.pi * f * mult / RATE
            s += amp * math.sin(phase[k])
        out.append(s * env(t, attack, decay) * vol)
    return out


def noise(length, lowpass=0.2, attack=0.002, decay=0.02, vol=0.3, sweep=None):
    out = []
    last = 0.0
    n = int(RATE * length)
    for i in range(n):
        t = i / RATE
        a = lowpass if sweep is None else sweep(t)
        last += a * (rng.uniform(-1, 1) - last)
        out.append(last * env(t, attack, decay) * vol)
    return out


def mix(*tracks):
    n = max(len(t) for t in tracks)
    return [sum(t[i] for t in tracks if i < len(t)) for i in range(n)]


def fade_out(samples, ms=8):
    k = int(RATE * ms / 1000)
    for i in range(min(k, len(samples))):
        samples[-1 - i] *= i / k
    return samples


def sounds():
    yield "click", mix(
        tone(lambda t: 1900 - 9000 * t, 0.06, decay=0.012, vol=0.45, harmonics=((1, 1.0), (2.01, 0.25))),
        noise(0.03, lowpass=0.5, decay=0.004, vol=0.25))
    yield "hover", tone(lambda t: 2600, 0.05, attack=0.004, decay=0.012, vol=0.18)
    yield "toggle_on", mix(
        tone(lambda t: 880 if t < 0.045 else 1320, 0.14, decay=0.04, vol=0.4, harmonics=((1, 1.0), (3, 0.12))),
        noise(0.02, lowpass=0.6, decay=0.003, vol=0.15))
    yield "toggle_off", mix(
        tone(lambda t: 1320 if t < 0.045 else 880, 0.14, decay=0.04, vol=0.4, harmonics=((1, 1.0), (3, 0.12))),
        noise(0.02, lowpass=0.6, decay=0.003, vol=0.15))
    yield "whoosh_open", noise(0.32, attack=0.12, decay=0.08, vol=0.55, sweep=lambda t: 0.02 + 0.5 * min(1.0, t / 0.25))
    yield "whoosh_close", noise(0.28, attack=0.03, decay=0.09, vol=0.5, sweep=lambda t: max(0.02, 0.45 - 1.6 * t))
    yield "toast", mix(
        tone(lambda t: 1046.5, 0.55, attack=0.004, decay=0.16, vol=0.32, harmonics=((1, 1.0), (2, 0.2))),
        tone(lambda t: 1568.0, 0.55, attack=0.03, decay=0.18, vol=0.22))
    yield "notify", mix(
        tone(lambda t: 1760, 0.35, decay=0.08, vol=0.35, harmonics=((1, 1.0), (2.5, 0.15))),
        tone(lambda t: 2349, 0.35, attack=0.05, decay=0.1, vol=0.25))
    yield "shutter", mix(
        noise(0.05, lowpass=0.9, decay=0.006, vol=0.5),
        [0.0] * int(RATE * 0.07) + noise(0.06, lowpass=0.7, decay=0.008, vol=0.4),
        tone(lambda t: 600, 0.08, decay=0.01, vol=0.2))
    yield "charge", tone(lambda t: 220 + 900 * t * t, 0.7, attack=0.4, decay=0.25, vol=0.35, harmonics=((1, 1.0), (2, 0.3), (3, 0.1)))
    yield "pop", mix(
        tone(lambda t: 400 + 2400 * math.exp(-t * 40), 0.12, decay=0.03, vol=0.45),
        noise(0.05, lowpass=0.4, decay=0.01, vol=0.2))


def write_wav(path, samples):
    peak = max(1e-6, max(abs(s) for s in samples))
    scale = 0.89 / peak if peak > 0.89 else 1.0
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s * scale)) * 32767)) for s in samples))


def main():
    out_dir = sys.argv[1]
    os.makedirs(out_dir, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for name, samples in sounds():
            wav = os.path.join(tmp, name + ".wav")
            write_wav(wav, fade_out(samples))
            ogg = os.path.join(out_dir, name + ".ogg")
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", ogg], check=True)
            print("wrote", ogg)


if __name__ == "__main__":
    main()
