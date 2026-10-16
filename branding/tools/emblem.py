"""Hollow Client's emblem, defined once and exported everywhere.

The emblem is a cut crystal with an "H" cut out of its middle, so the center is literally hollow, with a glowing
violet -> cyan edge. This script holds the geometry of the three concepts and writes every file the project needs:

    branding/concepts/*.svg          the three concepts (A is the chosen one, see DECISIONS.md)
    branding/emblem.svg              master (full color with glow)
    branding/emblem-flat-white.svg / -flat-black.svg / -small.svg
    branding/png/emblem-<size>.png   16 ... 1024 (16-48 use the simplified small-size drawing)
    branding/emblem.ico              multi-size icon for the launcher exe
    branding/wordmark.svg/.png       "HOLLOW" bold + "CLIENT" small, in Inter (SIL OFL)
    branding/skyblock-badge.svg/.png the emblem with a small floating island
    branding/banner.png, social-preview.png (1280x640)
    + the copies the mod and launcher use (icon.png, gui textures, animation layers, launcher assets)

Run from the repository root with the launcher's Python (it needs PySide6):

    launcher\\.venv\\Scripts\\python branding\\tools\\emblem.py            (everything)
    launcher\\.venv\\Scripts\\python branding\\tools\\emblem.py concepts   (only the three concepts)

Everything is drawn with Qt (QPainter for the PNGs, hand-written SVG). Qt's SVG renderer can't do blur filters, so
the glow in the PNGs is drawn here by blurring a separate layer; the SVGs carry a standard SVG glow filter instead.
"""

from __future__ import annotations

import math
import shutil
import struct
import sys
from dataclasses import dataclass, field
from pathlib import Path

from PySide6.QtCore import QBuffer, QByteArray, QIODevice, QPointF, QRectF, Qt
from PySide6.QtGui import (QBrush, QColor, QFont, QFontDatabase, QGuiApplication, QImage, QLinearGradient, QPainter,
                           QPainterPath, QPen, QPolygonF, QRadialGradient, QTransform)

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "branding"
FONT_DIR = ROOT / "mod" / "src" / "main" / "resources" / "assets" / "hollowclient" / "font"

VIOLET = QColor("#9b6cff")
CYAN = QColor("#22d3ee")
DEEP = QColor("#160f2e")
WHITE = QColor("#ffffff")
BLACK = QColor("#000000")
SKY = QColor("#38bdf8")
GOLD = QColor("#f5c542")

S = 1000.0  # design space: 1000 x 1000, center (500, 500)


# ------------------------------------------------------------------------------------------------ geometry helpers

def poly(points) -> QPainterPath:
    path = QPainterPath()
    pts = [QPointF(x, y) for x, y in points]
    path.moveTo(pts[0])
    for p in pts[1:]:
        path.lineTo(p)
    path.closeSubpath()
    return path


def regular(cx: float, cy: float, r: float, n: int, start_deg: float) -> list[tuple[float, float]]:
    return [(cx + r * math.cos(math.radians(start_deg + 360 * i / n)), cy + r * math.sin(math.radians(start_deg + 360 * i / n)))
            for i in range(n)]


def h_shape(cx: float, cy: float, h: float, stem: float, gap: float, bar: float, chamfer: float = 0.0) -> QPainterPath:
    """An upright H: two stems of width `stem`, `gap` apart (inner edges), joined by a crossbar `bar` high."""
    top, bottom = cy - h / 2, cy + h / 2
    lx0, lx1 = cx - gap / 2 - stem, cx - gap / 2
    rx0, rx1 = cx + gap / 2, cx + gap / 2 + stem
    by0, by1 = cy - bar / 2, cy + bar / 2
    c = chamfer
    pts = [(lx0 + c, top), (lx1 - c, top), (lx1, top + c), (lx1, by0), (rx0, by0), (rx0, top + c), (rx0 + c, top),
           (rx1 - c, top), (rx1, top + c), (rx1, bottom - c), (rx1 - c, bottom), (rx0 + c, bottom), (rx0, bottom - c),
           (rx0, by1), (lx1, by1), (lx1, bottom - c), (lx1 - c, bottom), (lx0 + c, bottom), (lx0, bottom - c), (lx0, top + c)]
    return poly(pts)


def spark_shape(cx: float, cy: float, r: float, thin: float = 0.22) -> QPainterPath:
    """A four-pointed star."""
    pts = []
    for i in range(8):
        a = math.radians(-90 + 45 * i)
        rr = r if i % 2 == 0 else r * thin
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return poly(pts)


def mix(a: QColor, b: QColor, t: float) -> QColor:
    t = max(0.0, min(1.0, t))
    return QColor(round(a.red() + (b.red() - a.red()) * t), round(a.green() + (b.green() - a.green()) * t),
                  round(a.blue() + (b.blue() - a.blue()) * t), round(a.alpha() + (b.alpha() - a.alpha()) * t))


def shade(c: QColor, k: float) -> QColor:
    """k > 0 lightens toward white, k < 0 darkens toward the deep base."""
    return mix(c, WHITE, k) if k >= 0 else mix(c, DEEP, -k)


# ------------------------------------------------------------------------------------------------ emblem model

@dataclass
class Facet:
    path: QPainterPath
    color: QColor
    light: float = 0.0   # -1 (in shadow) .. 1 (facing the light); used for the theme-tinted in-game layers


@dataclass
class Emblem:
    """Everything needed to draw one emblem in the 1000-unit design space."""
    name: str
    silhouette: QPainterPath            # outer shape (before the H is cut out)
    hole: QPainterPath                  # the H (cut out of the silhouette); may be empty for positive-H concepts
    facets: list[Facet]                 # colored pieces, drawn clipped to silhouette minus hole
    edge: QPainterPath                  # the outline that glows
    extras: list[Facet] = field(default_factory=list)   # drawn on top, not clipped (orbits, sparks, floating parts)
    small_hole: QPainterPath | None = None               # a bolder H for 16-48 px

    def body(self) -> QPainterPath:
        return self.silhouette.subtracted(self.hole) if not self.hole.isEmpty() else self.silhouette


def gradient_color(x: float, y: float) -> QColor:
    """The brand gradient across the design space: violet at the top left, cyan at the bottom right."""
    t = ((x / S) * 0.6 + (y / S) * 0.4 - 0.15) * 1.4
    return mix(VIOLET, CYAN, t)


def facet_color(points, light: float) -> QColor:
    cx = sum(p[0] for p in points) / len(points)
    cy = sum(p[1] for p in points) / len(points)
    return shade(gradient_color(cx, cy), light)


def concept_a() -> Emblem:
    """A: Facet Hex. A pointy-top hexagon cut like a gem (a flat table and six bevels), H cut out of the middle,
    a small spark at the upper right."""
    outer = regular(500, 500, 440, 6, -90)
    inner = regular(500, 500, 285, 6, -90)
    facets = []
    # bevel lights: light comes from the upper left; the table sits a little darker so the cut H reads
    lights = [0.16, -0.04, -0.34, -0.56, -0.30, 0.04]
    for i in range(6):
        j = (i + 1) % 6
        pts = [outer[i], outer[j], inner[j], inner[i]]
        facets.append(Facet(poly(pts), facet_color(pts, lights[i]), lights[i]))
    facets.append(Facet(poly(inner), facet_color(inner, -0.40), -0.40))
    hole = h_shape(500, 500, 400, 88, 120, 74, chamfer=10)
    small_hole = h_shape(500, 500, 430, 120, 120, 100, chamfer=0)
    spark = Facet(spark_shape(838, 178, 62), QColor("#e9fbff"))
    return Emblem("A — Facet Hex", poly(outer), hole, facets, poly(outer), [spark], small_hole)


def concept_b() -> Emblem:
    """B: Orbit Shard. A tall diamond crystal with the H cut out and a tilted orbit with a spark around it."""
    pts = [(500, 70), (770, 500), (500, 930), (230, 500)]
    c = (500, 500)
    facets = []
    lights = [0.3, -0.05, -0.4, 0.05]
    for i in range(4):
        tri = [pts[i], pts[(i + 1) % 4], c]
        facets.append(Facet(poly(tri), facet_color(tri, lights[i])))
    hole = h_shape(500, 500, 330, 60, 86, 56, chamfer=6)
    small_hole = h_shape(500, 500, 360, 90, 80, 80)
    # orbit: an ellipse ring, tilted
    ring = QPainterPath()
    ring.addEllipse(QPointF(500, 500), 455, 120)
    inner_ring = QPainterPath()
    inner_ring.addEllipse(QPointF(500, 500), 430, 100)
    orbit = ring.subtracted(inner_ring)
    t = QTransform().translate(500, 500).rotate(-22).translate(-500, -500)
    orbit = t.map(orbit)
    spark_pos = t.map(QPointF(500 + 455 * math.cos(math.radians(-35)), 500 + 120 * math.sin(math.radians(-35))))
    extras = [Facet(orbit, QColor(CYAN.red(), CYAN.green(), CYAN.blue(), 200)),
              Facet(spark_shape(spark_pos.x(), spark_pos.y(), 58), QColor("#e9fbff"))]
    return Emblem("B — Orbit Shard", poly(pts), hole, facets, poly(pts), extras, small_hole)


def concept_c() -> Emblem:
    """C: Crystal Ring. A twelve-facet ring whose middle is empty, with an H of three glass bars floating in it."""
    outer = regular(500, 500, 440, 12, -90)
    inner = regular(500, 500, 330, 12, -90)
    ring = poly(outer).subtracted(poly(inner))
    facets = []
    for i in range(12):
        j = (i + 1) % 12
        quad = [outer[i], outer[j], inner[j], inner[i]]
        light = 0.35 * math.cos(math.radians(30 * i + 15 + 135))
        facets.append(Facet(poly(quad), facet_color(quad, light)))
    bars = [h_shape(500, 500, 360, 80, 110, 66, chamfer=8)]
    extras = [Facet(b, mix(VIOLET, CYAN, 0.5)) for b in bars]
    return Emblem("C — Crystal Ring", ring, QPainterPath(), facets, ring, extras, None)


CONCEPTS = {"a-facet-hex": concept_a, "b-orbit-shard": concept_b, "c-crystal-ring": concept_c}
CHOSEN = "a-facet-hex"


# ------------------------------------------------------------------------------------------------ SVG writer

def svg_d(path: QPainterPath) -> str:
    out = []
    i = 0
    n = path.elementCount()
    while i < n:
        e = path.elementAt(i)
        if e.type == QPainterPath.ElementType.MoveToElement:
            out.append(f"M{e.x:.1f} {e.y:.1f}")
            i += 1
        elif e.type == QPainterPath.ElementType.LineToElement:
            out.append(f"L{e.x:.1f} {e.y:.1f}")
            i += 1
        elif e.type == QPainterPath.ElementType.CurveToElement:
            c1 = e
            c2 = path.elementAt(i + 1)
            end = path.elementAt(i + 2)
            out.append(f"C{c1.x:.1f} {c1.y:.1f} {c2.x:.1f} {c2.y:.1f} {end.x:.1f} {end.y:.1f}")
            i += 3
        else:
            i += 1
    out.append("Z")
    return " ".join(out)


def hexc(c: QColor) -> str:
    return c.name(QColor.NameFormat.HexRgb)


def svg_emblem(e: Emblem, mode: str = "color", size: int = 1000) -> str:
    """mode: color (glow), small (simplified, no glow), white, black."""
    hole = e.small_hole if (mode == "small" and e.small_hole is not None) else e.hole
    body = e.silhouette.subtracted(hole) if not hole.isEmpty() else e.silhouette
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 1000" width="{size}" height="{size}">',
             f"<title>Hollow Client — {e.name}</title>"]
    if mode in ("white", "black"):
        col = "#ffffff" if mode == "white" else "#000000"
        parts.append(f'<path fill="{col}" fill-rule="evenodd" d="{svg_d(body)}"/>')
        for x in e.extras:
            parts.append(f'<path fill="{col}" d="{svg_d(x.path)}"/>')
        parts.append("</svg>")
        return "\n".join(parts)
    parts.append('<defs>'
                 '<linearGradient id="brand" x1="0" y1="0" x2="1" y2="1">'
                 f'<stop offset="0" stop-color="{hexc(VIOLET)}"/><stop offset="1" stop-color="{hexc(CYAN)}"/></linearGradient>'
                 '<filter id="glow" x="-30%" y="-30%" width="160%" height="160%">'
                 '<feGaussianBlur stdDeviation="22" result="b"/>'
                 '<feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>'
                 f'<clipPath id="body"><path clip-rule="evenodd" fill-rule="evenodd" d="{svg_d(body)}"/></clipPath>'
                 '</defs>')
    if mode == "color":
        parts.append(f'<path d="{svg_d(e.edge)}" fill="none" stroke="url(#brand)" stroke-width="26" filter="url(#glow)" opacity="0.85"/>')
    if mode == "small":
        parts.append(f'<path fill="url(#brand)" fill-rule="evenodd" d="{svg_d(body)}"/>')
    else:
        parts.append('<g clip-path="url(#body)">')
        for f in e.facets:
            parts.append(f'<path fill="{hexc(f.color)}" d="{svg_d(f.path)}"/>')
        for f in e.facets:
            parts.append(f'<path fill="none" stroke="#ffffff" stroke-opacity="0.18" stroke-width="3" d="{svg_d(f.path)}"/>')
        parts.append("</g>")
        if not hole.isEmpty():
            parts.append(f'<clipPath id="hole"><path d="{svg_d(hole)}"/></clipPath>')
            for width, alpha in ((40, 0.16), (24, 0.27), (12, 0.47)):
                parts.append(f'<path clip-path="url(#hole)" fill="none" stroke="{hexc(CYAN)}" stroke-opacity="{alpha}" '
                             f'stroke-width="{width}" d="{svg_d(hole)}"/>')
        parts.append(f'<path d="{svg_d(e.edge)}" fill="none" stroke="url(#brand)" stroke-width="10"/>')
        if not hole.isEmpty():
            parts.append(f'<path d="{svg_d(hole)}" fill="none" stroke="{hexc(CYAN)}" stroke-width="6" opacity="0.9"/>')
    for x in e.extras:
        a = x.color.alphaF()
        flt = ' filter="url(#glow)"' if mode == "color" else ""
        parts.append(f'<path fill="{hexc(x.color)}" fill-opacity="{a:.2f}" d="{svg_d(x.path)}"{flt}/>')
    parts.append("</svg>")
    return "\n".join(parts)


# ------------------------------------------------------------------------------------------------ raster drawing

def new_image(w: int, h: int) -> QImage:
    img = QImage(w, h, QImage.Format.Format_ARGB32_Premultiplied)
    img.fill(Qt.GlobalColor.transparent)
    return img


def blur(img: QImage, radius_px: float) -> QImage:
    """A cheap, smooth blur: shrink and grow back a few times."""
    w, h = img.width(), img.height()
    out = img
    factor = max(2, int(radius_px / 2))
    for _ in range(3):
        small = out.scaled(max(1, w // factor), max(1, h // factor), Qt.AspectRatioMode.IgnoreAspectRatio,
                           Qt.TransformationMode.SmoothTransformation)
        out = small.scaled(w, h, Qt.AspectRatioMode.IgnoreAspectRatio, Qt.TransformationMode.SmoothTransformation)
    return out


def brand_brush(scale: float) -> QBrush:
    g = QLinearGradient(QPointF(0, 0), QPointF(S * scale, S * scale))
    g.setColorAt(0, VIOLET)
    g.setColorAt(1, CYAN)
    return QBrush(g)


def paint_emblem(p: QPainter, e: Emblem, scale: float, mode: str = "color", glow: bool = True) -> None:
    """Draw the emblem onto `p` with the design space scaled by `scale` (origin at the painter's origin)."""
    hole = e.small_hole if (mode == "small" and e.small_hole is not None) else e.hole
    body = e.silhouette.subtracted(hole) if not hole.isEmpty() else e.silhouette
    t = QTransform().scale(scale, scale)
    p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
    if mode in ("white", "black"):
        col = WHITE if mode == "white" else BLACK
        p.fillPath(t.map(body), col)
        for x in e.extras:
            p.fillPath(t.map(x.path), col)
        return
    if mode == "small":
        p.fillPath(t.map(body), brand_brush(scale))
        p.setPen(QPen(QColor(255, 255, 255, 70), max(1.0, 14 * scale)))
        p.drawPath(t.map(e.edge))
        for x in e.extras:
            if x.path.boundingRect().width() > 200:   # keep big extras (rings, floating H), drop tiny sparks
                p.fillPath(t.map(x.path), x.color)
        return
    p.save()
    p.setClipPath(t.map(body))
    for f in e.facets:
        p.fillPath(t.map(f.path), f.color)
    # thin light seams between the facets, like the edges of a cut crystal
    p.setPen(QPen(QColor(255, 255, 255, 46), max(1.0, 3 * scale)))
    p.setBrush(Qt.BrushStyle.NoBrush)
    for f in e.facets:
        p.drawPath(t.map(f.path))
    p.restore()
    if not hole.isEmpty():
        # the hollow center glows from its edges inward
        p.save()
        p.setClipPath(t.map(hole))
        for width, alpha in ((40, 40), (24, 70), (12, 120)):
            p.setPen(QPen(QColor(CYAN.red(), CYAN.green(), CYAN.blue(), alpha), width * scale))
            p.drawPath(t.map(hole))
        p.restore()
    p.setPen(QPen(brand_brush(scale), 10 * scale))
    p.setBrush(Qt.BrushStyle.NoBrush)
    p.drawPath(t.map(e.edge))
    if not hole.isEmpty():
        p.setPen(QPen(QColor(CYAN.red(), CYAN.green(), CYAN.blue(), 230), 6 * scale))
        p.drawPath(t.map(hole))
    for x in e.extras:
        p.fillPath(t.map(x.path), x.color)


def render(e: Emblem, size: int, mode: str = "color", glow: bool = True, pad: float = 0.0) -> QImage:
    """The emblem as a square image. `pad` is extra empty border as a fraction of the size (room for the glow)."""
    inner = size * (1 - 2 * pad)
    scale = inner / S
    off = size * pad
    img = new_image(size, size)
    if mode == "color" and glow:
        g = new_image(size, size)
        gp = QPainter(g)
        gp.setRenderHint(QPainter.RenderHint.Antialiasing, True)
        gp.translate(off, off)
        gp.setPen(QPen(brand_brush(scale), 44 * scale))
        gp.drawPath(QTransform().scale(scale, scale).map(e.edge))
        for x in e.extras:
            gp.fillPath(QTransform().scale(scale, scale).map(x.path), x.color)
        gp.end()
        g = blur(g, max(2.0, 26 * scale * 1.6))
        p = QPainter(img)
        p.setOpacity(0.95)
        p.drawImage(0, 0, g)
        p.setOpacity(0.6)
        p.drawImage(0, 0, g)
        p.end()
    p = QPainter(img)
    p.translate(off, off)
    paint_emblem(p, e, scale, mode)
    p.end()
    return img


def render_small(e: Emblem, size: int) -> QImage:
    """The simplified drawing for 16-48 px: drawn large, then scaled down smoothly."""
    big = render(e, size * 8, "small", glow=False, pad=0.02)
    return big.scaled(size, size, Qt.AspectRatioMode.IgnoreAspectRatio, Qt.TransformationMode.SmoothTransformation)


def render_any(e: Emblem, size: int) -> QImage:
    if size <= 48:
        return render_small(e, size)
    big = render(e, min(2048, size * 2), "color", pad=0.06)
    return big.scaled(size, size, Qt.AspectRatioMode.IgnoreAspectRatio, Qt.TransformationMode.SmoothTransformation)


# ------------------------------------------------------------------------------------------------ text, badge, banner

def load_fonts() -> dict[str, str]:
    """Inter (SIL OFL): the mod's medium and semibold cuts, and the launcher's bold cut for "HOLLOW"."""
    families = {}
    files = {"inter_semibold.ttf": FONT_DIR / "inter_semibold.ttf", "inter_medium.ttf": FONT_DIR / "inter_medium.ttf",
             "inter_bold.ttf": ROOT / "launcher" / "assets" / "fonts" / "Inter-Bold.ttf"}
    for name, path in files.items():
        fid = QFontDatabase.addApplicationFont(str(path))
        if fid >= 0:
            families[name] = QFontDatabase.applicationFontFamilies(fid)[0]
    return families


def text_path(text: str, family: str, px: float, weight: QFont.Weight, spacing: float) -> QPainterPath:
    f = QFont(family)
    f.setPixelSize(round(px))
    f.setWeight(weight)
    f.setLetterSpacing(QFont.SpacingType.AbsoluteSpacing, spacing)
    path = QPainterPath()
    path.addText(QPointF(0, 0), f, text)
    return path


def wordmark_paths(families: dict[str, str]) -> tuple[QPainterPath, QPainterPath]:
    fam_bold = families.get("inter_bold.ttf", families.get("inter_semibold.ttf", "Segoe UI"))
    fam_med = families.get("inter_medium.ttf", "Segoe UI")
    hollow = text_path("HOLLOW", fam_bold, 200, QFont.Weight.Bold, 14)
    client = text_path("CLIENT", fam_med, 64, QFont.Weight.Medium, 30)
    hb = hollow.boundingRect()
    cb = client.boundingRect()
    hollow.translate(-hb.left(), -hb.top())
    # CLIENT sits under HOLLOW, right-aligned
    client.translate(-cb.left() + (hb.width() - cb.width()), -cb.top() + hb.height() + 34)
    return hollow, client


def render_wordmark(families: dict[str, str], height: int, on_dark: bool = True) -> QImage:
    hollow, client = wordmark_paths(families)
    box = hollow.boundingRect().united(client.boundingRect())
    scale = height / box.height()
    w = math.ceil(box.width() * scale) + 4
    img = new_image(w, height + 4)
    p = QPainter(img)
    p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
    p.translate(2, 2)
    p.scale(scale, scale)
    g = QLinearGradient(QPointF(0, 0), QPointF(box.width(), 0))
    g.setColorAt(0, VIOLET)
    g.setColorAt(1, CYAN)
    p.fillPath(hollow, QBrush(g))
    p.fillPath(client, QColor("#d9dcf2") if on_dark else QColor("#3b3f55"))
    p.end()
    return img


def svg_wordmark(families: dict[str, str]) -> str:
    hollow, client = wordmark_paths(families)
    box = hollow.boundingRect().united(client.boundingRect())
    return "\n".join([
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {box.width():.0f} {box.height():.0f}">',
        "<title>Hollow Client wordmark</title>",
        f'<defs><linearGradient id="brand" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="{hexc(VIOLET)}"/>'
        f'<stop offset="1" stop-color="{hexc(CYAN)}"/></linearGradient></defs>',
        f'<path fill="url(#brand)" d="{svg_d(hollow)}"/>',
        f'<path fill="#d9dcf2" d="{svg_d(client)}"/>',
        "</svg>"])


def island_paths() -> list[Facet]:
    """A small floating island (design space 1000), drawn under the emblem for the SkyBlock badge."""
    grass = poly([(250, 760), (750, 760), (720, 800), (280, 800)])
    rock = poly([(280, 800), (720, 800), (600, 900), (520, 975), (470, 940), (400, 900)])
    tree_trunk = poly([(690, 700), (705, 700), (705, 765), (690, 765)])
    tree_top = QPainterPath()
    tree_top.addEllipse(QPointF(698, 680), 44, 40)
    return [Facet(rock, QColor("#7a5a3a")), Facet(grass, QColor("#4ade80")), Facet(tree_trunk, QColor("#6b4423")),
            Facet(tree_top, QColor("#22c55e"))]


def render_badge(e: Emblem, size: int) -> QImage:
    img = new_image(size, size)
    p = QPainter(img)
    p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
    # a sky-blue and gold halo behind
    rg = QRadialGradient(QPointF(size / 2, size * 0.45), size * 0.48)
    rg.setColorAt(0, QColor(SKY.red(), SKY.green(), SKY.blue(), 120))
    rg.setColorAt(0.7, QColor(GOLD.red(), GOLD.green(), GOLD.blue(), 40))
    rg.setColorAt(1, QColor(0, 0, 0, 0))
    p.fillRect(0, 0, size, size, QBrush(rg))
    em = render(e, round(size * 0.78), "color", pad=0.05)
    p.drawImage(round(size * 0.11), 0, em)
    t = QTransform().scale(size / S, size / S)
    for f in island_paths():
        p.fillPath(t.map(f.path), f.color)
    p.end()
    return img


def svg_badge(e: Emblem) -> str:
    inner = svg_emblem(e, "color").split("\n", 2)[2].rsplit("</svg>", 1)[0]
    island = "\n".join(f'<path fill="{hexc(f.color)}" d="{svg_d(f.path)}"/>' for f in island_paths())
    return "\n".join([
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1000 1000">',
        "<title>Hollow Client — SkyBlock Mode badge</title>",
        f'<g transform="translate(110 0) scale(0.78)">{inner}</g>', island, "</svg>"])


def render_card(e: Emblem, families: dict[str, str], w: int, h: int, tagline: str) -> QImage:
    """Banner / social preview: deep space background, emblem on the left, wordmark and tagline on the right."""
    img = QImage(w, h, QImage.Format.Format_ARGB32_Premultiplied)
    p = QPainter(img)
    p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
    bg = QLinearGradient(QPointF(0, 0), QPointF(w, h))
    bg.setColorAt(0, QColor("#0b0918"))
    bg.setColorAt(1, QColor("#08131c"))
    p.fillRect(0, 0, w, h, QBrush(bg))
    for cx, cy, r, c in ((0.25, 0.4, 0.55, VIOLET), (0.8, 0.75, 0.5, CYAN)):
        rg = QRadialGradient(QPointF(w * cx, h * cy), h * r)
        rg.setColorAt(0, QColor(c.red(), c.green(), c.blue(), 70))
        rg.setColorAt(1, QColor(0, 0, 0, 0))
        p.fillRect(0, 0, w, h, QBrush(rg))
    # a few stars
    import random
    rnd = random.Random(7)
    for _ in range(int(w * h / 4000)):
        a = rnd.randint(40, 160)
        s = rnd.choice((1, 1, 1, 2))
        p.fillRect(rnd.randrange(w), rnd.randrange(h), s, s, QColor(255, 255, 255, a))
    em_size = round(h * 0.78)
    p.drawImage(round(h * 0.11), round((h - em_size) / 2), render(e, em_size, "color", pad=0.05))
    x = round(h * 0.11 + em_size + h * 0.08)
    avail = w - x - round(h * 0.12)
    # the wordmark fills the space right of the emblem, never wider than it
    probe = render_wordmark(families, 100)
    wm_h = min(round(h * 0.34), int(100 * avail / probe.width()))
    wm = render_wordmark(families, wm_h)
    from PySide6.QtGui import QFontMetricsF
    f = QFont(families.get("inter_medium.ttf", "Segoe UI"))
    px = h * 0.07
    while True:
        f.setPixelSize(round(px))
        if QFontMetricsF(f).horizontalAdvance(tagline) <= avail or px < 10:
            break
        px -= 1
    block = wm.height() + h * 0.05 + px * 1.3
    top = (h - block) / 2
    p.drawImage(x, round(top), wm)
    p.setFont(f)
    p.setPen(QColor("#aab0d0"))
    p.drawText(QRectF(x, top + wm.height() + h * 0.05, avail, px * 1.6), Qt.AlignmentFlag.AlignLeft, tagline)
    p.end()
    return img


# ------------------------------------------------------------------------------------------------ animation layers

def mono(light: float) -> QColor:
    """Gray level for a theme-tinted layer: the game multiplies it by a theme color, so it is never black."""
    v = round(255 * max(0.46, min(1.0, 0.86 + light * 0.5)))
    return QColor(v, v, v)


def layers_for_mod(e: Emblem, size: int, out_dir: Path, tinted: bool = False) -> None:
    """Separate pieces for the intro animation (facets assemble, the H hollows out, the glow pulses), all on the
    same square canvas so they line up when drawn at the same place: facet_<i>.png, hole_fill.png, edge.png,
    glow.png, spark.png and facets.json (each facet's center, for the animation and the gradient). With
    `tinted`, everything is white/gray so the game can color it with the current theme."""
    out_dir.mkdir(parents=True, exist_ok=True)
    pad = 0.08
    scale = size * (1 - 2 * pad) / S
    off = size * pad
    t = QTransform().translate(off, off).scale(scale, scale)
    body = e.body()
    for i, f in enumerate(e.facets):
        img = new_image(size, size)
        p = QPainter(img)
        p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
        p.setClipPath(t.map(body))
        p.fillPath(t.map(f.path), mono(f.light) if tinted else f.color)
        p.setPen(QPen(QColor(255, 255, 255, 46), max(1.0, 3 * scale)))
        p.drawPath(t.map(f.path))
        p.end()
        img.save(str(out_dir / f"facet_{i}.png"))
    import json
    centers = []
    for f in e.facets:
        r = f.path.boundingRect()
        centers.append({"x": round(r.center().x() / S, 4), "y": round(r.center().y() / S, 4), "light": f.light})
    (out_dir / "facets.json").write_text(json.dumps({"facets": centers, "pad": pad}, indent=1), encoding="utf-8")
    # the H filled in (fades away as the emblem "hollows out")
    img = new_image(size, size)
    p = QPainter(img)
    p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
    p.fillPath(t.map(e.hole), WHITE if tinted else mix(VIOLET, CYAN, 0.5))
    p.end()
    img.save(str(out_dir / "hole_fill.png"))
    # bright edges
    img = new_image(size, size)
    p = QPainter(img)
    p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
    p.setPen(QPen(WHITE if tinted else brand_brush(scale), 10 * scale))
    p.translate(off, off)
    p.drawPath(QTransform().scale(scale, scale).map(e.edge))
    p.end()
    img.save(str(out_dir / "edge.png"))
    # the glowing rim of the cut-out H (its own layer, so it can brighten while the H hollows out)
    img = new_image(size, size)
    p = QPainter(img)
    p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
    p.setClipPath(t.map(e.hole))
    rim = WHITE if tinted else CYAN
    for width, alpha in ((40, 40), (24, 70), (12, 120), (6, 230)):
        p.setPen(QPen(QColor(rim.red(), rim.green(), rim.blue(), alpha), width * scale))
        p.drawPath(t.map(e.hole))
    p.end()
    img.save(str(out_dir / "hole_rim.png"))
    # glow on its own
    g = new_image(size, size)
    p = QPainter(g)
    p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
    p.translate(off, off)
    p.setPen(QPen(WHITE if tinted else brand_brush(scale), 44 * scale))
    p.drawPath(QTransform().scale(scale, scale).map(e.edge))
    p.end()
    blur(g, 26 * scale * 1.6).save(str(out_dir / "glow.png"))
    # the spark
    img = new_image(size, size)
    p = QPainter(img)
    p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
    for x in e.extras:
        p.fillPath(t.map(x.path), WHITE if tinted else x.color)
    p.end()
    img.save(str(out_dir / "spark.png"))


def composite_layers(layer_dir: Path) -> QImage:
    """The settled emblem from its layers (facets, edge, glowing rim of the H, spark), as one image."""
    import json
    info = json.loads((layer_dir / "facets.json").read_text(encoding="utf-8"))
    first = QImage(str(layer_dir / "facet_0.png"))
    img = new_image(first.width(), first.height())
    p = QPainter(img)
    for i in range(len(info["facets"])):
        p.drawImage(0, 0, QImage(str(layer_dir / f"facet_{i}.png")))
    for name in ("edge", "hole_rim", "spark"):
        p.drawImage(0, 0, QImage(str(layer_dir / f"{name}.png")))
    p.end()
    return img


def game_wordmarks(families: dict[str, str]) -> dict[str, QImage]:
    """White "HOLLOW" (bold) and "CLIENT" (medium, wide) textures, 96 px tall, for the game to tint with the theme."""
    out = {}
    specs = {"wordmark_hollow": ("HOLLOW", "inter_bold.ttf", QFont.Weight.Bold, 10),
             "wordmark_client": ("CLIENT", "inter_medium.ttf", QFont.Weight.Medium, 26)}
    for name, (text, font, weight, spacing) in specs.items():
        path = text_path(text, families.get(font, families.get("inter_semibold.ttf", "Segoe UI")), 120, weight, spacing)
        box = path.boundingRect()
        scale = 88 / box.height()
        w = math.ceil(box.width() * scale) + 8
        img = new_image(w, 96)
        p = QPainter(img)
        p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
        p.translate(4, 4)
        p.scale(scale, scale)
        p.translate(-box.left(), -box.top())
        p.fillPath(path, WHITE)
        p.end()
        out[name] = img
    return out


# ------------------------------------------------------------------------------------------------ ICO

def png_bytes(img: QImage) -> bytes:
    data = QByteArray()
    buf = QBuffer(data)
    buf.open(QIODevice.OpenModeFlag.WriteOnly)
    img.save(buf, "PNG")
    buf.close()
    return bytes(data)


def write_ico(e: Emblem, path: Path, sizes=(16, 24, 32, 48, 64, 128, 256)) -> None:
    images = [png_bytes(render_any(e, s)) for s in sizes]
    header = struct.pack("<HHH", 0, 1, len(sizes))
    offset = 6 + 16 * len(sizes)
    entries = b""
    for s, data in zip(sizes, images):
        entries += struct.pack("<BBBBHHII", s % 256, s % 256, 0, 0, 1, 32, len(data), offset)
        offset += len(data)
    path.write_bytes(header + entries + b"".join(images))


# ------------------------------------------------------------------------------------------------ main

def main() -> None:
    app = QGuiApplication.instance() or QGuiApplication(sys.argv[:1])  # noqa: F841 (fonts and painting need it)
    families = load_fonts()
    (OUT / "concepts").mkdir(parents=True, exist_ok=True)
    (OUT / "png").mkdir(parents=True, exist_ok=True)

    for key, make in CONCEPTS.items():
        e = make()
        (OUT / "concepts" / f"{key}.svg").write_text(svg_emblem(e, "color", 512), encoding="utf-8")
        render_any(e, 512).save(str(OUT / "concepts" / f"{key}-512.png"))
        render_any(e, 32).save(str(OUT / "concepts" / f"{key}-32.png"))
        render_any(e, 16).save(str(OUT / "concepts" / f"{key}-16.png"))
    if "concepts" in sys.argv[1:]:
        print("concepts written to", OUT / "concepts")
        return

    e = CONCEPTS[CHOSEN]()
    (OUT / "emblem.svg").write_text(svg_emblem(e, "color", 1024), encoding="utf-8")
    (OUT / "emblem-small.svg").write_text(svg_emblem(e, "small", 64), encoding="utf-8")
    (OUT / "emblem-flat-white.svg").write_text(svg_emblem(e, "white", 1024), encoding="utf-8")
    (OUT / "emblem-flat-black.svg").write_text(svg_emblem(e, "black", 1024), encoding="utf-8")
    for s in (16, 32, 48, 64, 128, 256, 512, 1024):
        render_any(e, s).save(str(OUT / "png" / f"emblem-{s}.png"))
    for mode in ("white", "black"):
        render(e, 512, mode, pad=0.04).save(str(OUT / "png" / f"emblem-flat-{mode}-512.png"))
    write_ico(e, OUT / "emblem.ico")
    (OUT / "wordmark.svg").write_text(svg_wordmark(families), encoding="utf-8")
    render_wordmark(families, 256).save(str(OUT / "wordmark.png"))
    (OUT / "skyblock-badge.svg").write_text(svg_badge(e), encoding="utf-8")
    render_badge(e, 512).save(str(OUT / "skyblock-badge.png"))
    render_card(e, families, 1280, 320, "A personal client: crazy-smooth visuals, quality of life and a SkyBlock Mode.").save(
        str(OUT / "banner.png"))
    render_card(e, families, 1280, 640, "Visuals, quality of life and SkyBlock Mode for Minecraft 26.2.").save(
        str(OUT / "social-preview.png"))

    # ---- copies the mod and launcher use
    mod_assets = ROOT / "mod" / "src" / "main" / "resources" / "assets" / "hollowclient"
    render_any(e, 128).save(str(mod_assets / "icon.png"))
    gui = mod_assets / "textures" / "gui"
    # in-game layers are white/gray: the game colors them with the current theme
    layers_for_mod(e, 256, gui / "emblem", tinted=True)
    composite_layers(gui / "emblem").save(str(gui / "sigil.png"))
    shutil.copyfile(gui / "emblem" / "glow.png", gui / "sigil_glow.png")
    render_badge(e, 256).save(str(gui / "skyblock_badge.png"))
    for name, img in game_wordmarks(families).items():
        img.save(str(gui / f"{name}.png"))
    launcher_assets = ROOT / "launcher" / "assets"
    render_any(e, 512).save(str(launcher_assets / "logo.png"))
    shutil.copyfile(OUT / "emblem.ico", launcher_assets / "icon.ico")
    render(e, 512, "color", glow=False, pad=0.08).save(str(launcher_assets / "emblem_flat.png"))
    layers_for_mod(e, 512, launcher_assets / "emblem")
    render_badge(e, 256).save(str(launcher_assets / "skyblock_badge.png"))
    render_wordmark(families, 160).save(str(launcher_assets / "wordmark.png"))
    print("branding written to", OUT)


if __name__ == "__main__":
    main()
