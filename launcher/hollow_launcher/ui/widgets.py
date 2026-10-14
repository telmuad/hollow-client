"""Custom widgets: cards, the painted hero area, the big PLAY button, the account badge and the banner."""

from __future__ import annotations

from PySide6.QtCore import QPointF, QRectF, QSize, Qt, Signal
from PySide6.QtGui import (QColor, QFont, QImage, QLinearGradient, QPainter, QPainterPath, QPixmap,
                           QRadialGradient)
from PySide6.QtWidgets import (QFrame, QHBoxLayout, QLabel, QPushButton, QSizePolicy, QVBoxLayout, QWidget)

from . import theme
from .logo import draw_mark


class Card(QFrame):
    def __init__(self, title: str | None = None, parent: QWidget | None = None):
        super().__init__(parent)
        self.setObjectName("card")
        self.body = QVBoxLayout(self)
        self.body.setContentsMargins(18, 16, 18, 16)
        self.body.setSpacing(8)
        if title:
            t = QLabel(title)
            t.setObjectName("cardTitle")
            self.body.addWidget(t)


def label(text: str, name: str = "cardText", wrap: bool = True) -> QLabel:
    lab = QLabel(text)
    lab.setObjectName(name)
    lab.setWordWrap(wrap)
    lab.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
    return lab


def button(text: str, name: str | None = None, slot=None) -> QPushButton:
    b = QPushButton(text)
    if name:
        b.setObjectName(name)
    b.setCursor(Qt.CursorShape.PointingHandCursor)
    if slot:
        b.clicked.connect(slot)
    return b


def repolish(w: QWidget) -> None:
    """Re-apply the style sheet after changing a dynamic property (e.g. kind=error)."""
    w.style().unpolish(w)
    w.style().polish(w)
    w.update()


class HeroPanel(QWidget):
    """Original backdrop art: a deep violet gradient, a soft glow and a large faint Hollow ring."""

    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self.setMinimumHeight(250)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)

    def paintEvent(self, event) -> None:  # noqa: N802 (Qt naming)
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        r = QRectF(self.rect()).adjusted(0.5, 0.5, -0.5, -0.5)
        path = QPainterPath()
        path.addRoundedRect(r, 18, 18)
        p.setClipPath(path)
        grad = QLinearGradient(r.topLeft(), r.bottomRight())
        grad.setColorAt(0.0, QColor("#221b42"))
        grad.setColorAt(0.55, QColor("#15142a"))
        grad.setColorAt(1.0, QColor("#0f1018"))
        p.fillPath(path, grad)
        glow = QRadialGradient(QPointF(r.right() - r.width() * 0.22, r.top() + r.height() * 0.25), r.width() * 0.45)
        glow.setColorAt(0.0, QColor(139, 108, 255, 90))
        glow.setColorAt(1.0, QColor(139, 108, 255, 0))
        p.fillRect(r, glow)
        # A faint grid of dots, like distant stars.
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(QColor(255, 255, 255, 18))
        step = 26
        for yy in range(int(r.top()) + 14, int(r.bottom()), step):
            for xx in range(int(r.left()) + 14 + (yy // step % 2) * 13, int(r.right()), step):
                p.drawEllipse(QPointF(xx, yy), 1.0, 1.0)
        size = r.height() * 1.15
        p.setOpacity(0.16)
        draw_mark(p, QRectF(r.right() - size * 0.78, r.center().y() - size / 2, size, size), background=False)
        p.setOpacity(1.0)
        p.setClipping(False)
        p.setPen(QColor(theme.BORDER))
        p.setBrush(Qt.BrushStyle.NoBrush)
        p.drawRoundedRect(r, 18, 18)


class PlayButton(QPushButton):
    def __init__(self, parent: QWidget | None = None):
        super().__init__("PLAY", parent)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.setFixedSize(260, 64)
        self.sub = ""
        self._hover = False

    def set_sub(self, text: str) -> None:
        self.sub = text
        self.update()

    def enterEvent(self, e) -> None:  # noqa: N802
        self._hover = True
        self.update()

    def leaveEvent(self, e) -> None:  # noqa: N802
        self._hover = False
        self.update()

    def paintEvent(self, event) -> None:  # noqa: N802
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        r = QRectF(self.rect()).adjusted(1, 1, -1, -1)
        path = QPainterPath()
        path.addRoundedRect(r, 16, 16)
        grad = QLinearGradient(r.topLeft(), r.bottomRight())
        if self.isEnabled():
            grad.setColorAt(0.0, QColor("#a58cff" if self._hover else "#8b6cff"))
            grad.setColorAt(1.0, QColor("#7b5cf0" if self._hover else "#5f43d6"))
        else:
            grad.setColorAt(0.0, QColor("#34354a"))
            grad.setColorAt(1.0, QColor("#2a2b3b"))
        p.fillPath(path, grad)
        p.setPen(QColor(255, 255, 255, 40))
        p.drawPath(path)
        f = QFont(theme.FONT, 17)
        f.setBold(True)
        f.setLetterSpacing(QFont.SpacingType.AbsoluteSpacing, 4)
        p.setFont(f)
        p.setPen(QColor("#ffffff") if self.isEnabled() else QColor(theme.TEXT_DIM))
        main = QRectF(r.x(), r.y() + (4 if not self.sub else -6), r.width(), r.height())
        p.drawText(main, Qt.AlignmentFlag.AlignCenter, self.text())
        if self.sub:
            f2 = QFont(theme.FONT, 8)
            p.setFont(f2)
            p.setPen(QColor(255, 255, 255, 190))
            p.drawText(QRectF(r.x(), r.y() + 34, r.width(), 20), Qt.AlignmentFlag.AlignCenter, self.sub)


def face_pixmap(skin_png: bytes | None, size: int) -> QPixmap:
    """The front of the head from a skin (with the hat layer), scaled with hard pixels; a placeholder without a skin."""
    img = QImage()
    pm = QPixmap(size, size)
    pm.fill(Qt.GlobalColor.transparent)
    p = QPainter(pm)
    p.setRenderHint(QPainter.RenderHint.Antialiasing)
    clip = QPainterPath()
    clip.addRoundedRect(QRectF(0, 0, size, size), size * 0.25, size * 0.25)
    p.setClipPath(clip)
    if skin_png and img.loadFromData(skin_png) and img.width() >= 64:
        scale = img.width() // 64
        face = img.copy(8 * scale, 8 * scale, 8 * scale, 8 * scale)
        hat = img.copy(40 * scale, 8 * scale, 8 * scale, 8 * scale)
        p.drawImage(QRectF(0, 0, size, size), face.scaled(size, size, Qt.AspectRatioMode.IgnoreAspectRatio, Qt.TransformationMode.FastTransformation))
        p.drawImage(QRectF(0, 0, size, size), hat.scaled(size, size, Qt.AspectRatioMode.IgnoreAspectRatio, Qt.TransformationMode.FastTransformation))
    else:
        p.fillRect(QRectF(0, 0, size, size), QColor(theme.ACCENT_SOFT))
        draw_mark(p, QRectF(size * 0.15, size * 0.15, size * 0.7, size * 0.7), background=False)
    p.end()
    return pm


class AccountBadge(QPushButton):
    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.setObjectName("account")
        self.setStyleSheet(f"""
            QPushButton#account {{ background: {theme.CARD}; border: 1px solid {theme.BORDER}; border-radius: 20px;
                                   padding: 4px 14px 4px 4px; text-align: left; font-size: 12px; font-weight: 600; }}
            QPushButton#account:hover {{ border-color: {theme.ACCENT}; }}
            QPushButton#account::menu-indicator {{ image: none; width: 0; }}""")
        self.setIconSize(QSize(30, 30))
        self.set_account(None, None)

    def set_account(self, name: str | None, skin_png: bytes | None, note: str | None = None) -> None:
        self.setIcon(face_pixmap(skin_png, 60))
        if name:
            self.setText(f"  {name}" + (f"  ·  {note}" if note else "") + "  ▾")
        else:
            self.setText("  Log in with Microsoft  ▾" if not note else f"  {note}  ▾")


class Banner(QFrame):
    action_clicked = Signal()

    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self.setObjectName("banner")
        lay = QHBoxLayout(self)
        lay.setContentsMargins(14, 10, 10, 10)
        lay.setSpacing(10)
        self.text = QLabel()
        self.text.setObjectName("bannerText")
        self.text.setWordWrap(True)
        lay.addWidget(self.text, 1)
        self.action = button("", "primary", self.action_clicked.emit)
        lay.addWidget(self.action)
        close = button("✕", "link", self.hide)
        close.setToolTip("Hide")
        lay.addWidget(close)
        self.hide()

    def show_message(self, text: str, kind: str = "info", action: str | None = None) -> None:
        self.text.setText(text)
        self.setProperty("kind", kind)
        repolish(self)
        self.action.setVisible(bool(action))
        if action:
            self.action.setText(action)
        self.show()
