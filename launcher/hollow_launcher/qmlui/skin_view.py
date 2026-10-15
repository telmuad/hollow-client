"""A small 3D viewer for your Minecraft skin with your Hollow cosmetics (cape, wings, halo), drawn with QPainter.

The player is a set of textured boxes using the standard skin layout. Each face is a flat parallelogram on screen
(the view is orthographic), so it can be drawn exactly with one affine image transform; faces are sorted back to
front. No 3D engine needed. Drag to turn it; it breathes, the cape sways, wings flap and the halo glows.
"""

from __future__ import annotations

import math
import time
from dataclasses import dataclass, field
from pathlib import Path

from PySide6.QtCore import Property, QPointF, QRectF, Qt, QTimer, Signal
from PySide6.QtGui import QColor, QImage, QPainter, QPen, QRadialGradient, QTransform
from PySide6.QtQuick import QQuickPaintedItem

SHADES = 8


@dataclass
class Box:
    """A textured box in model pixels (y up, the player faces +z). (u, v) is the texture offset as in Minecraft."""
    x: float
    y: float
    z: float
    w: float
    h: float
    d: float
    u: float
    v: float
    texture: str = "skin"
    inflate: float = 0.0
    mirror: bool = False
    pivot: tuple[float, float, float] = (0.0, 0.0, 0.0)
    rot: tuple[float, float, float] = (0.0, 0.0, 0.0)  # degrees around x, y, z (applied z, y, x)
    uv_scale: float = 1.0
    swap_front_back: bool = False
    faces: tuple[str, ...] = ("front", "back", "right", "left", "top", "bottom")
    two_sided: bool = False
    extra: dict = field(default_factory=dict)


def _rotate(p, rot):
    x, y, z = p
    rx, ry, rz = (math.radians(a) for a in rot)
    if rz:
        c, s = math.cos(rz), math.sin(rz)
        x, y = x * c - y * s, x * s + y * c
    if ry:
        c, s = math.cos(ry), math.sin(ry)
        x, z = x * c + z * s, -x * s + z * c
    if rx:
        c, s = math.cos(rx), math.sin(rx)
        y, z = y * c - z * s, y * s + z * c
    return x, y, z


def face_quads(b: Box):
    """(name, top-left, top-right, bottom-left, texture rect, mirrored) for each face, in model space. A mirrored
    face keeps its corners (for the outward direction) but is drawn flipped left to right."""
    i = b.inflate
    x0, x1 = b.x - i, b.x + b.w + i
    y0, y1 = b.y - i, b.y + b.h + i
    z0, z1 = b.z - i, b.z + b.d + i
    u, v, w, h, d = b.u, b.v, b.w, b.h, b.d
    front_rect = (u + d, v + d, w, h)
    back_rect = (u + 2 * d + w, v + d, w, h)
    if b.swap_front_back:
        front_rect, back_rect = back_rect, front_rect
    right_rect = (u, v + d, d, h)
    left_rect = (u + d + w, v + d, d, h)
    if b.mirror:
        right_rect, left_rect = left_rect, right_rect
    quads = {
        "front": ((x0, y1, z1), (x1, y1, z1), (x0, y0, z1), front_rect),
        "back": ((x1, y1, z0), (x0, y1, z0), (x1, y0, z0), back_rect),
        "right": ((x0, y1, z0), (x0, y1, z1), (x0, y0, z0), right_rect),
        "left": ((x1, y1, z1), (x1, y1, z0), (x1, y0, z1), left_rect),
        "top": ((x0, y1, z0), (x1, y1, z0), (x0, y1, z1), (u + d, v, w, d)),
        "bottom": ((x0, y0, z1), (x1, y0, z1), (x0, y0, z0), (u + d + w, v, w, d)),
    }
    out = []
    for name in b.faces:
        tl, tr, bl, rect = quads[name]
        rect = tuple(c * b.uv_scale for c in rect)
        out.append((name, tl, tr, bl, rect, b.mirror))
    return out


def player_boxes(slim: bool, legacy: bool, arm_swing: float = 0.0) -> list[Box]:
    """The player model (and its second layer) in the standard skin layout."""
    aw = 3 if slim else 4
    boxes = [
        Box(-4, 24, -4, 8, 8, 8, 0, 0),
        Box(-4, 12, -2, 8, 12, 4, 16, 16),
        Box(-4, 0, -2, 4, 12, 4, 0, 16),
        Box(-4 - aw, 12, -2, aw, 12, 4, 40, 16, pivot=(-4 - aw / 2, 22, 0), rot=(arm_swing, 0, 2)),
    ]
    if legacy:
        boxes.append(Box(0, 0, -2, 4, 12, 4, 0, 16, mirror=True))
        boxes.append(Box(4, 12, -2, aw, 12, 4, 40, 16, mirror=True, pivot=(4 + aw / 2, 22, 0), rot=(-arm_swing, 0, -2)))
        boxes.append(Box(-4, 24, -4, 8, 8, 8, 32, 0, inflate=0.5))
        return boxes
    boxes += [
        Box(0, 0, -2, 4, 12, 4, 16, 48),
        Box(4, 12, -2, aw, 12, 4, 32, 48, pivot=(4 + aw / 2, 22, 0), rot=(-arm_swing, 0, -2)),
        # second layer
        Box(-4, 24, -4, 8, 8, 8, 32, 0, inflate=0.5),
        Box(-4, 12, -2, 8, 12, 4, 16, 32, inflate=0.25),
        Box(-4, 0, -2, 4, 12, 4, 0, 32, inflate=0.25),
        Box(0, 0, -2, 4, 12, 4, 0, 48, inflate=0.25),
        Box(-4 - aw, 12, -2, aw, 12, 4, 40, 32, inflate=0.25, pivot=(-4 - aw / 2, 22, 0), rot=(arm_swing, 0, 2)),
        Box(4, 12, -2, aw, 12, 4, 48, 48, inflate=0.25, pivot=(4 + aw / 2, 22, 0), rot=(-arm_swing, 0, -2)),
    ]
    return boxes


def is_slim(img: QImage) -> bool:
    """Slim ("Alex") skins leave a strip of the arm texture empty."""
    if img.width() < 64 or img.height() < 64:
        return False
    return img.pixelColor(50, 16).alpha() == 0 and img.pixelColor(51, 16).alpha() == 0 and img.pixelColor(50, 31).alpha() == 0


def placeholder_skin(accent: QColor) -> QImage:
    """An original stand-in skin (a dark hooded figure with glowing eyes) for when you aren't signed in."""
    img = QImage(64, 64, QImage.Format.Format_ARGB32)
    img.fill(Qt.GlobalColor.transparent)
    p = QPainter(img)
    hood, face, body, sleeve, legs = QColor("#2a2540"), QColor("#14121c"), QColor("#1f1b2e"), QColor("#262238"), QColor("#16141f")

    def fill(x, y, w, h, c):
        p.fillRect(x, y, w, h, c)

    fill(0, 8, 32, 8, hood)
    fill(8, 0, 16, 8, hood)
    fill(8, 8, 8, 8, face)
    fill(9, 12, 2, 1, accent)
    fill(13, 12, 2, 1, accent)
    fill(16, 20, 24, 12, body)
    fill(20, 16, 16, 4, body)
    fill(21, 22, 1, 7, accent)
    fill(26, 22, 1, 7, accent)
    fill(21, 25, 6, 1, accent)
    for (ux, uy) in ((40, 16), (32, 48)):
        fill(ux, uy + 4, 16, 12, sleeve)
        fill(ux + 4, uy, 8, 4, sleeve)
        fill(ux, uy + 13, 16, 3, QColor("#3a3550"))
    for (ux, uy) in ((0, 16), (16, 48)):
        fill(ux, uy + 4, 16, 12, legs)
        fill(ux + 4, uy, 8, 4, legs)
    p.end()
    return img


def _shaded(img: QImage) -> list[QImage]:
    out = []
    for i in range(SHADES):
        k = 0.5 + 0.5 * i / (SHADES - 1)
        copy = img.convertToFormat(QImage.Format.Format_ARGB32_Premultiplied)
        if k < 0.999:
            p = QPainter(copy)
            p.setCompositionMode(QPainter.CompositionMode.CompositionMode_SourceAtop)
            p.fillRect(copy.rect(), QColor(0, 0, 0, int(255 * (1 - k))))
            p.end()
        out.append(copy)
    return out


class SkinView(QQuickPaintedItem):
    yawChanged = Signal()
    pitchChanged = Signal()
    sourceChanged = Signal()

    def __init__(self, parent=None):
        super().__init__(parent)
        self._yaw = 28.0
        self._pitch = 10.0
        self._skin_path = ""
        self._cape_path = ""
        self._wings_path = ""
        self._halo = QColor(0, 0, 0, 0)
        self._accent = QColor("#9B6CFF")
        self._animate = True
        self._images: dict[str, list[QImage]] = {}
        self._sizes: dict[str, tuple[int, int]] = {}
        self._slim = False
        self._legacy = False
        self._start = time.monotonic()
        self._reload()
        self._timer = QTimer(self)
        self._timer.setInterval(33)
        self._timer.timeout.connect(self.update)
        self._timer.start()

    # ------------------------------------------------------------------------------------------------ properties

    def _get_yaw(self):
        return self._yaw

    def _set_yaw(self, v):
        self._yaw = float(v) % 360
        self.yawChanged.emit()
        self.update()

    def _get_pitch(self):
        return self._pitch

    def _set_pitch(self, v):
        self._pitch = max(-35.0, min(45.0, float(v)))
        self.pitchChanged.emit()
        self.update()

    def _source_prop(name):  # noqa: N805
        def getter(self):
            return getattr(self, name)

        def setter(self, value):
            if getattr(self, name) != value:
                setattr(self, name, value)
                self._reload()
                self.sourceChanged.emit()
                self.update()
        return getter, setter

    yaw = Property(float, _get_yaw, _set_yaw, notify=yawChanged)
    pitch = Property(float, _get_pitch, _set_pitch, notify=pitchChanged)
    skinPath = Property(str, *_source_prop("_skin_path"), notify=sourceChanged)
    capePath = Property(str, *_source_prop("_cape_path"), notify=sourceChanged)
    wingsPath = Property(str, *_source_prop("_wings_path"), notify=sourceChanged)
    haloColor = Property(QColor, *_source_prop("_halo"), notify=sourceChanged)
    accent = Property(QColor, *_source_prop("_accent"), notify=sourceChanged)
    animate = Property(bool, *_source_prop("_animate"), notify=sourceChanged)

    # ------------------------------------------------------------------------------------------------ loading

    @staticmethod
    def _load(path: str) -> QImage | None:
        if not path:
            return None
        p = path[8:] if path.startswith("file:///") else path
        img = QImage(p) if Path(p).is_file() or p.startswith(":") else QImage()
        return None if img.isNull() else img

    def _reload(self) -> None:
        self._images.clear()
        skin = self._load(self._skin_path)
        if skin is None or skin.width() != 64 or skin.height() not in (32, 64):
            skin = placeholder_skin(self._accent)
        self._legacy = skin.height() == 32
        self._slim = is_slim(skin)
        self._images["skin"] = _shaded(skin)
        self._sizes["skin"] = (skin.width(), skin.height())
        cape = self._load(self._cape_path)
        if cape is not None and cape.width() >= 22:
            if cape.height() > cape.width() // 2:  # animated strip or GIF frames: show the first frame
                cape = cape.copy(0, 0, cape.width(), cape.width() // 2)
            self._images["cape"] = _shaded(cape)
            self._sizes["cape"] = (cape.width(), cape.height())
        wings = self._load(self._wings_path)
        if wings is not None:
            self._images["wings"] = _shaded(wings)
            self._sizes["wings"] = (wings.width(), wings.height())

    # ------------------------------------------------------------------------------------------------ drawing

    def _scene(self, t: float) -> list[Box]:
        swing = 3 * math.sin(t * 1.3) if self._animate else 0
        boxes = player_boxes(self._slim, self._legacy, swing)
        if "cape" in self._images:
            sway = 9 + (2.5 * math.sin(t * 1.1) if self._animate else 0)
            k = self._sizes["cape"][0] / 64
            # the outside of the cape (seen from behind) uses the art at (1, 1), like Minecraft's cape
            boxes.append(Box(-5, 8, -3, 10, 16, 1, 0, 0, texture="cape", pivot=(0, 24, -2), rot=(sway, 0, 0),
                             uv_scale=k, swap_front_back=True))
        if "wings" in self._images:
            flap = 18 + (10 * math.sin(t * 2.2) if self._animate else 0)
            k = self._sizes["wings"][0] / 64
            boxes.append(Box(1, 7, -3, 14, 16, 1, 0, 0, texture="wings", pivot=(1, 22, -3), rot=(0, flap, 10), uv_scale=k,
                             faces=("front",), two_sided=True))
            boxes.append(Box(-15, 7, -3, 14, 16, 1, 0, 0, texture="wings", mirror=True, pivot=(-1, 22, -3), rot=(0, -flap, -10),
                             uv_scale=k, faces=("front",), two_sided=True))
        return boxes

    def paint(self, painter: QPainter) -> None:
        w, h = self.width(), self.height()
        if w < 10 or h < 10:
            return
        base = painter.transform()
        t = time.monotonic() - self._start
        s = min(h / 46.0, w / 34.0)
        cx, cy = w / 2, h / 2 + 2 * s
        yaw, pitch = math.radians(self._yaw), math.radians(self._pitch)
        cyaw, syaw, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
        bob = 0.25 * math.sin(t * 1.3) if self._animate else 0

        def view(p):
            x, y, z = p
            y -= 16 - bob
            x1 = x * cyaw + z * syaw
            z1 = -x * syaw + z * cyaw
            y2 = y * cp - z1 * sp
            z2 = y * sp + z1 * cp
            return cx + x1 * s, cy - y2 * s, z2

        # soft glow behind the player in the theme color
        glow = QRadialGradient(QPointF(cx, cy), h * 0.42)
        c = QColor(self._accent)
        c.setAlpha(70)
        glow.setColorAt(0, c)
        c2 = QColor(self._accent)
        c2.setAlpha(0)
        glow.setColorAt(1, c2)
        painter.fillRect(QRectF(0, 0, w, h), glow)
        # shadow on the ground
        fx, fy, _ = view((0, 0, 0))
        painter.setPen(Qt.PenStyle.NoPen)
        painter.setBrush(QColor(0, 0, 0, 70))
        painter.drawEllipse(QPointF(fx, fy), 7 * s, 2.2 * s)

        items = []
        light = (0.35, 0.55, 0.76)
        for b in self._scene(t):
            shades = self._images.get(b.texture)
            if not shades:
                continue
            for name, tl, tr, bl, rect, mirrored in face_quads(b):
                pts = []
                for q in (tl, tr, bl):
                    lx, ly, lz = (q[0] - b.pivot[0], q[1] - b.pivot[1], q[2] - b.pivot[2])
                    rx, ry, rz = _rotate((lx, ly, lz), b.rot)
                    pts.append((rx + b.pivot[0], ry + b.pivot[1], rz + b.pivot[2]))
                vt = [view(q) for q in pts]
                # outward normal in view space
                ax, ay, az = (pts[2][k] - pts[0][k] for k in range(3))
                bx, by, bz = (pts[1][k] - pts[0][k] for k in range(3))
                n = (ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
                nx = n[0] * cyaw + n[2] * syaw
                nz1 = -n[0] * syaw + n[2] * cyaw
                ny = n[1] * cp - nz1 * sp
                nz = n[1] * sp + nz1 * cp
                length = math.sqrt(nx * nx + ny * ny + nz * nz) or 1
                facing = nz / length
                if facing <= 0.001 and not b.two_sided:
                    continue
                lum = max(0.0, (nx * light[0] + ny * light[1] + abs(nz) * light[2]) / length)
                shade = shades[min(SHADES - 1, int(lum * (SHADES - 1) + 0.5))]
                depth = (vt[1][2] + vt[2][2]) / 2 + (0.01 if b.inflate else 0)
                if mirrored:
                    # flip left to right: swap the top corners, the bottom-left moves to the old bottom-right
                    (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = vt
                    vt = [(x1, y1, z1), (x0, y0, z0), (x2 + x1 - x0, y2 + y1 - y0, z2)]
                items.append((depth, vt, rect, shade))
        halo = self._halo.alpha() > 0
        if halo:
            ring = []
            spin = t * 0.8 if self._animate else 0
            pulse = 0.75 + 0.25 * math.sin(t * 2.4) if self._animate else 1
            for i in range(36):
                a0 = spin + i * math.tau / 36
                a1 = spin + (i + 1) * math.tau / 36
                p0 = view((4.6 * math.cos(a0), 35 + bob, 4.6 * math.sin(a0)))
                p1 = view((4.6 * math.cos(a1), 35 + bob, 4.6 * math.sin(a1)))
                ring.append(((p0[2] + p1[2]) / 2, p0, p1, pulse))
        items.sort(key=lambda it: it[0])
        painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform, False)
        hi = 0
        ring_sorted = sorted(ring, key=lambda r: r[0]) if halo else []

        def draw_ring_until(depth_limit):
            nonlocal hi
            while hi < len(ring_sorted) and ring_sorted[hi][0] <= depth_limit:
                _, p0, p1, pulse = ring_sorted[hi]
                for width, alpha in ((3.2, 50), (1.9, 110), (0.9, 255)):
                    col = QColor(self._halo)
                    col.setAlpha(int(alpha * pulse))
                    painter.setPen(QPen(col, width * s * 0.5, Qt.PenStyle.SolidLine, Qt.PenCapStyle.RoundCap))
                    painter.drawLine(QPointF(p0[0], p0[1]), QPointF(p1[0], p1[1]))
                hi += 1

        for depth, vt, rect, shade in items:
            if halo:
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)
                draw_ring_until(depth)
                painter.setRenderHint(QPainter.RenderHint.Antialiasing, False)
            (x0, y0, _), (x1, y1, _), (x2, y2, _) = vt
            tu, tv, tw, th = rect
            if tw <= 0 or th <= 0:
                continue
            painter.setTransform(QTransform((x1 - x0) / tw, (y1 - y0) / tw, (x2 - x0) / th, (y2 - y0) / th, x0, y0) * base)
            painter.drawImage(QRectF(-0.04, -0.04, tw + 0.08, th + 0.08), shade, QRectF(tu, tv, tw, th))
            painter.setTransform(base)
        if halo:
            painter.setRenderHint(QPainter.RenderHint.Antialiasing, True)
            draw_ring_until(float("inf"))
