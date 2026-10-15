"""The Hollow sigil, drawn in code: a hexagon ring with a sharp, slanted "H" and two orbit dots.

The shape matches the mod's texture generator (mod/tools/texturegen/HollowTextureGen.java, ``sigilShape``), so the
launcher icon, the in-game logo and the mod icon are the same mark. Colors follow the Void theme (violet to cyan).
"""

from __future__ import annotations

import math

from PySide6.QtCore import QPointF, QRectF, Qt
from PySide6.QtGui import QBrush, QColor, QImage, QLinearGradient, QPainter, QPainterPath, QPen

VIOLET = "#9b6cff"
CYAN = "#22d3ee"


def _hexagon(cx: float, cy: float, r: float) -> QPainterPath:
    path = QPainterPath()
    for i in range(6):
        a = math.radians(-90 + 60 * i)
        pt = QPointF(cx + r * math.cos(a), cy + r * math.sin(a))
        if i == 0:
            path.moveTo(pt)
        else:
            path.lineTo(pt)
    path.closeSubpath()
    return path


def _parallelogram(x: float, y: float, w: float, h: float, slant: float) -> QPainterPath:
    path = QPainterPath()
    path.moveTo(x + slant, y)
    path.lineTo(x + w + slant, y)
    path.lineTo(x + w, y + h)
    path.lineTo(x, y + h)
    path.closeSubpath()
    return path


def sigil_path(size: float) -> QPainterPath:
    """The sigil in a size x size box at the origin (same proportions as the Java generator)."""
    c = size / 2
    ro = size * 0.44
    ri = size * 0.385
    shape = _hexagon(c, c, ro).subtracted(_hexagon(c, c, ri))
    top, bottom = size * 0.29, size * 0.71
    bar_w, slant = size * 0.085, size * 0.035
    shape = shape.united(_parallelogram(size * 0.335, top, bar_w, bottom - top, slant))
    shape = shape.united(_parallelogram(size * 0.58, top, bar_w, bottom - top, slant))
    cy, ch, tilt = size * 0.5, size * 0.06, size * 0.012
    cross = QPainterPath()
    cross.moveTo(size * 0.40, cy - ch / 2 + tilt)
    cross.lineTo(size * 0.60, cy - ch / 2 - tilt)
    cross.lineTo(size * 0.60, cy + ch / 2 - tilt)
    cross.lineTo(size * 0.40, cy + ch / 2 + tilt)
    cross.closeSubpath()
    shape = shape.united(cross)
    dot = size * 0.045
    for dy in (-(ro + dot * 0.2), ro + dot * 0.2):
        d = QPainterPath()
        d.addEllipse(QPointF(c, c + dy), dot, dot)
        shape = shape.united(d)
    return shape


def draw_mark(p: QPainter, rect: QRectF, background: bool = True, glow: float = 1.0,
              start: str = VIOLET, end: str = CYAN) -> None:
    """Draw the sigil centered in ``rect``. ``background`` adds the dark rounded square used for the app icon."""
    p.save()
    p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
    s = min(rect.width(), rect.height())
    x = rect.x() + (rect.width() - s) / 2
    y = rect.y() + (rect.height() - s) / 2
    if background:
        bg = QLinearGradient(QPointF(x, y), QPointF(x + s, y + s))
        bg.setColorAt(0.0, QColor("#1b1636"))
        bg.setColorAt(1.0, QColor("#08090e"))
        square = QPainterPath()
        square.addRoundedRect(QRectF(x, y, s, s), s * 0.21, s * 0.21)
        p.fillPath(square, QBrush(bg))
        inner = s * 0.86
        ox, oy = x + s * 0.07, y + s * 0.07
    else:
        inner, ox, oy = s, x, y
    path = sigil_path(inner).translated(ox, oy)
    grad = QLinearGradient(QPointF(ox, oy), QPointF(ox + inner, oy + inner))
    grad.setColorAt(0.0, QColor(start))
    grad.setColorAt(1.0, QColor(end))
    # Soft glow: the outline stroked a few times with wide, faint pens
    if glow > 0 and inner >= 24:
        p.setBrush(Qt.BrushStyle.NoBrush)
        steps = 10
        for i in range(steps):
            width = inner * 0.11 * (1 - i / steps)
            alpha = 9
            g = QLinearGradient(grad)
            c0, c1 = QColor(start), QColor(end)
            c0.setAlpha(int(alpha * glow))
            c1.setAlpha(int(alpha * glow))
            g.setColorAt(0.0, c0)
            g.setColorAt(1.0, c1)
            pen = QPen(QBrush(g), width)
            pen.setJoinStyle(Qt.PenJoinStyle.RoundJoin)
            p.setPen(pen)
            p.drawPath(path)
    p.setPen(Qt.PenStyle.NoPen)
    p.fillPath(path, QBrush(grad))
    p.restore()


def render(size: int, background: bool = True) -> QImage:
    img = QImage(size, size, QImage.Format.Format_ARGB32_Premultiplied)
    img.fill(Qt.GlobalColor.transparent)
    p = QPainter(img)
    draw_mark(p, QRectF(0, 0, size, size), background)
    p.end()
    return img
