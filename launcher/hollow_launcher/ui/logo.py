"""The Hollow logo, drawn in code: a violet ring with a gap and a light core, on a dark rounded square."""

from __future__ import annotations

from PySide6.QtCore import QPointF, QRectF, Qt
from PySide6.QtGui import QBrush, QColor, QConicalGradient, QImage, QLinearGradient, QPainter, QPainterPath, QPen


def draw_mark(p: QPainter, rect: QRectF, background: bool = True) -> None:
    p.save()
    p.setRenderHint(QPainter.RenderHint.Antialiasing, True)
    s = min(rect.width(), rect.height())
    x = rect.x() + (rect.width() - s) / 2
    y = rect.y() + (rect.height() - s) / 2
    if background:
        bg = QLinearGradient(QPointF(x, y), QPointF(x + s, y + s))
        bg.setColorAt(0.0, QColor("#1d1a36"))
        bg.setColorAt(1.0, QColor("#0e0f15"))
        path = QPainterPath()
        path.addRoundedRect(QRectF(x, y, s, s), s * 0.22, s * 0.22)
        p.fillPath(path, QBrush(bg))
    c = QPointF(x + s / 2, y + s / 2)
    radius = s * 0.27
    width = s * 0.11
    ring = QConicalGradient(c, 90)
    ring.setColorAt(0.0, QColor("#b59cff"))
    ring.setColorAt(0.5, QColor("#6b4fe0"))
    ring.setColorAt(1.0, QColor("#b59cff"))
    pen = QPen(QBrush(ring), width)
    pen.setCapStyle(Qt.PenCapStyle.RoundCap)
    p.setPen(pen)
    arc = QRectF(c.x() - radius, c.y() - radius, radius * 2, radius * 2)
    # Qt angles are in 1/16 degree, counter-clockwise from 3 o'clock. Leave a gap at the lower right (like the mod icon).
    p.drawArc(arc, int(-20 * 16), int(310 * 16))
    p.setPen(Qt.PenStyle.NoPen)
    p.setBrush(QColor("#d9ccff"))
    dot = s * 0.075
    p.drawEllipse(c, dot, dot)
    p.restore()


def render(size: int, background: bool = True) -> QImage:
    img = QImage(size, size, QImage.Format.Format_ARGB32_Premultiplied)
    img.fill(Qt.GlobalColor.transparent)
    p = QPainter(img)
    draw_mark(p, QRectF(0, 0, size, size), background)
    p.end()
    return img
