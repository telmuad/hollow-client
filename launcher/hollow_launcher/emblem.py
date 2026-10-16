"""The Hollow emblem for the launcher: the files made by branding/tools/emblem.py (a cut crystal with the H cut out
of its middle), plus the facet layout the intro animation uses.

The PNGs in assets/ are generated; edit branding/tools/emblem.py and run it to change the look everywhere.
"""

from __future__ import annotations

import json
from pathlib import Path

from PySide6.QtGui import QImage

from . import paths


def emblem_dir() -> Path:
    """The separate layers (facet_<i>.png, edge, hole_fill, hole_rim, glow, spark) on one square canvas."""
    return paths.assets_dir() / "emblem"


def flat_file() -> Path:
    """The whole emblem without its glow, for the title bar, the home page and the end of the intro."""
    return paths.assets_dir() / "emblem_flat.png"


def facets() -> list[list[float]]:
    """Each facet's center as [x, y] in 0..1 of the layer canvas (padding included), in drawing order."""
    data = json.loads((emblem_dir() / "facets.json").read_text(encoding="utf-8"))
    pad = float(data.get("pad", 0.08))
    return [[round(pad + f["x"] * (1 - 2 * pad), 4), round(pad + f["y"] * (1 - 2 * pad), 4)] for f in data["facets"]]


def intro_points(count: int = 300) -> list[list[float]]:
    """Points spread over the emblem's crystal (x, y in -0.5..0.5 of the canvas) for the intro's particles."""
    img = QImage(str(flat_file()))
    if img.isNull():
        return []
    w, h = img.width(), img.height()
    pts = []
    step = max(1, round(w * 0.012))
    for y in range(0, h, step):
        for x in range(0, w, step):
            if img.pixelColor(x, y).alpha() > 160:
                pts.append([round(x / w - 0.5, 4), round(y / h - 0.5, 4)])
    if len(pts) > count:
        stride = len(pts) / count
        pts = [pts[int(i * stride)] for i in range(count)]
    return pts
