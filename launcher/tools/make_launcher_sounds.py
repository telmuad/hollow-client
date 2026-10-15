"""Writes the launcher's UI sounds as WAV files (Windows plays WAV natively). Reuses the mod's synthesizer
(mod/tools/soundgen/make_sounds.py), so the launcher and the game sound the same; the intro swell is new.

Run from the launcher folder:  python tools/make_launcher_sounds.py assets/sounds
All sounds are synthesized from scratch (fixed random seed), so they are original.
"""

import importlib.util
import math
import os
import sys
from pathlib import Path

GEN = Path(__file__).resolve().parents[2] / "mod" / "tools" / "soundgen" / "make_sounds.py"


def load_generator():
    spec = importlib.util.spec_from_file_location("hollow_soundgen", GEN)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main() -> None:
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "assets/sounds")
    out.mkdir(parents=True, exist_ok=True)
    g = load_generator()
    made = dict(g.sounds())
    picks = {"click": "click", "hover": "hover", "whoosh": "whoosh_open", "charge": "charge", "pop": "pop", "toggle": "toggle_on"}
    for name, source in picks.items():
        g.write_wav(str(out / f"{name}.wav"), g.fade_out(list(made[source])))
    # intro: a soft rising chord with a shimmer on top
    intro = g.mix(
        g.tone(lambda t: 220 * (1 + 0.02 * t), 1.6, attack=0.5, decay=0.6, vol=0.25, harmonics=((1, 1.0), (2, 0.25))),
        g.tone(lambda t: 329.6, 1.6, attack=0.7, decay=0.6, vol=0.18),
        g.tone(lambda t: 440 + 6 * math.sin(t * 30), 1.6, attack=0.9, decay=0.5, vol=0.14),
        g.noise(1.4, attack=0.8, decay=0.35, vol=0.12, sweep=lambda t: 0.05 + 0.3 * min(1.0, t)),
    )
    g.write_wav(str(out / "intro.wav"), g.fade_out(intro, 60))
    for f in sorted(os.listdir(out)):
        print("wrote", out / f)


if __name__ == "__main__":
    main()
