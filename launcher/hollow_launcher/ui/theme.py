"""Colors, fonts and the Qt style sheet for the launcher."""

from __future__ import annotations

import sys

BG = "#0e0f14"
SIDEBAR = "#12131a"
CARD = "#181a22"
CARD_HOVER = "#1f222c"
BORDER = "#262935"
TEXT = "#ecedf2"
TEXT_DIM = "#9ea3b0"
TEXT_FAINT = "#636875"
ACCENT = "#8b6cff"
ACCENT_DARK = "#6b4fe0"
ACCENT_SOFT = "#2a2342"
DANGER = "#f2706b"
SUCCESS = "#5bd18b"
WARNING = "#ffc24a"
FONT = "Segoe UI"

STYLE = f"""
* {{ font-family: "{FONT}"; color: {TEXT}; }}
QMainWindow, #root {{ background: {BG}; }}
QToolTip {{ background: {CARD}; color: {TEXT}; border: 1px solid {BORDER}; padding: 4px 6px; }}

#sidebar {{ background: {SIDEBAR}; border-right: 1px solid {BORDER}; }}
#brand {{ font-size: 15px; font-weight: 700; letter-spacing: 2px; }}
#brandSub {{ color: {TEXT_FAINT}; font-size: 11px; letter-spacing: 1px; }}
QPushButton#nav {{
    text-align: left; padding: 10px 14px; border: none; border-radius: 9px;
    color: {TEXT_DIM}; font-size: 13px; font-weight: 600; background: transparent;
}}
QPushButton#nav:hover {{ background: {CARD}; color: {TEXT}; }}
QPushButton#nav:checked {{ background: {ACCENT_SOFT}; color: {TEXT}; }}
#sideVersion {{ color: {TEXT_FAINT}; font-size: 11px; }}

#topbar {{ background: transparent; }}
#pageTitle {{ font-size: 22px; font-weight: 700; }}
#pageSub {{ color: {TEXT_DIM}; font-size: 12px; }}

QFrame#card {{ background: {CARD}; border: 1px solid {BORDER}; border-radius: 14px; }}
#cardTitle {{ font-size: 13px; font-weight: 700; }}
#cardText, #hint {{ color: {TEXT_DIM}; font-size: 12px; }}
#faint {{ color: {TEXT_FAINT}; font-size: 11px; }}
#value {{ font-size: 13px; font-weight: 600; }}

QPushButton {{
    background: {CARD_HOVER}; border: 1px solid {BORDER}; border-radius: 9px; padding: 8px 14px;
    font-size: 12px; font-weight: 600; color: {TEXT};
}}
QPushButton:hover {{ border-color: {ACCENT}; }}
QPushButton:pressed {{ background: {ACCENT_SOFT}; }}
QPushButton:disabled {{ color: {TEXT_FAINT}; border-color: {BORDER}; background: {CARD}; }}
QPushButton#primary {{ background: {ACCENT_DARK}; border: 1px solid {ACCENT}; }}
QPushButton#primary:hover {{ background: {ACCENT}; }}
QPushButton#primary:disabled {{ background: {CARD}; border-color: {BORDER}; color: {TEXT_FAINT}; }}
QPushButton#danger {{ color: {DANGER}; }}
QPushButton#danger:hover {{ border-color: {DANGER}; }}
QPushButton#link {{ background: transparent; border: none; color: {ACCENT}; padding: 2px 4px; }}
QPushButton#link:hover {{ color: #b59cff; }}

#banner {{ background: {ACCENT_SOFT}; border: 1px solid {ACCENT_DARK}; border-radius: 12px; }}
#banner[kind="error"] {{ background: #2c1719; border-color: #6e2c2e; }}
#banner[kind="warn"] {{ background: #2b2414; border-color: #6b5422; }}
#bannerText {{ font-size: 12px; }}

#status {{ color: {TEXT_DIM}; font-size: 12px; }}
#status[kind="error"] {{ color: {DANGER}; }}
#status[kind="ok"] {{ color: {SUCCESS}; }}

QProgressBar {{ background: {CARD}; border: none; border-radius: 3px; height: 6px; max-height: 6px; }}
QProgressBar::chunk {{ background: {ACCENT}; border-radius: 3px; }}

QLineEdit, QSpinBox {{
    background: {BG}; border: 1px solid {BORDER}; border-radius: 8px; padding: 7px 10px; font-size: 12px;
    selection-background-color: {ACCENT_DARK};
}}
QLineEdit:focus, QSpinBox:focus {{ border-color: {ACCENT}; }}
QSpinBox::up-button, QSpinBox::down-button {{ width: 0; border: none; }}
QCheckBox {{ font-size: 12px; spacing: 8px; }}
QCheckBox::indicator {{ width: 16px; height: 16px; border-radius: 5px; border: 1px solid {BORDER}; background: {BG}; }}
QCheckBox::indicator:checked {{ background: {ACCENT}; border-color: {ACCENT}; }}

QSlider::groove:horizontal {{ height: 6px; background: {BORDER}; border-radius: 3px; }}
QSlider::sub-page:horizontal {{ background: {ACCENT}; border-radius: 3px; }}
QSlider::handle:horizontal {{ background: #f4f4f8; width: 16px; height: 16px; margin: -5px 0; border-radius: 8px; }}

QPlainTextEdit#log {{
    background: #0a0b0f; border: 1px solid {BORDER}; border-radius: 12px; padding: 8px;
    font-family: "Cascadia Mono", Consolas, monospace; font-size: 11px; color: #c9ccd6;
    selection-background-color: {ACCENT_DARK};
}}
QScrollBar:vertical {{ background: transparent; width: 10px; margin: 4px; }}
QScrollBar::handle:vertical {{ background: {BORDER}; border-radius: 3px; min-height: 30px; }}
QScrollBar::add-line:vertical, QScrollBar::sub-line:vertical {{ height: 0; }}
QScrollBar::add-page:vertical, QScrollBar::sub-page:vertical {{ background: transparent; }}

QMenu {{ background: {CARD}; border: 1px solid {BORDER}; border-radius: 8px; padding: 4px; }}
QMenu::item {{ padding: 6px 18px; border-radius: 6px; }}
QMenu::item:selected {{ background: {ACCENT_SOFT}; }}
QMessageBox {{ background: {CARD}; }}
QMessageBox QLabel {{ font-size: 12px; }}
"""


def dark_title_bar(widget) -> None:
    """Ask Windows 10/11 for a dark title bar on this window (ignored where unsupported)."""
    if sys.platform != "win32":
        return
    try:
        import ctypes

        hwnd = int(widget.winId())
        value = ctypes.c_int(1)
        for attr in (20, 19):  # DWMWA_USE_IMMERSIVE_DARK_MODE (newer, older builds)
            if ctypes.windll.dwmapi.DwmSetWindowAttribute(hwnd, attr, ctypes.byref(value), ctypes.sizeof(value)) == 0:
                break
    except Exception:
        pass
