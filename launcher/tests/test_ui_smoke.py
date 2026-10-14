"""The window builds and the main states render without errors (offscreen, temp game folder, in-memory keyring)."""

import os

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

import pytest  # noqa: E402

PySide6 = pytest.importorskip("PySide6")

from PySide6.QtWidgets import QApplication, QMessageBox  # noqa: E402

from hollow_launcher.auth import NOT_APPROVED_MESSAGE, LoginError  # noqa: E402
from hollow_launcher.token_store import MemoryBackend, TokenStore  # noqa: E402
from hollow_launcher.ui import main_window, theme  # noqa: E402


@pytest.fixture
def window(tmp_path, monkeypatch):
    monkeypatch.setenv("APPDATA", str(tmp_path))
    monkeypatch.setattr(main_window, "TokenStore", lambda: TokenStore(MemoryBackend()))
    shown = []
    monkeypatch.setattr(QMessageBox, "information", lambda *a, **k: shown.append(a))
    app = QApplication.instance() or QApplication([])
    app.setStyleSheet(theme.STYLE)
    w = main_window.MainWindow(network=False)
    w.shown_dialogs = shown
    yield w
    w.close()


def test_pages_switch(window):
    for i in range(4):
        window.show_page(i)
        assert window.pages.currentIndex() == i
    assert window.page_title.text() == "Logs"


def test_not_installed_state(window):
    assert "Not installed yet" in window.home.status.text()
    assert window.home.play.sub.startswith("Installs first")


def test_play_without_config_explains_setup(window, monkeypatch):
    monkeypatch.setattr(window.installer, "is_installed", lambda: True)
    window.on_play()
    assert window.shown_dialogs, "a help dialog about config.json is shown"
    assert "config.json" in window.home.status.text() or "Azure" in window.home.status.text()


def test_not_approved_message_is_shown_exactly(window):
    window._login_failed(LoginError("not_approved", NOT_APPROVED_MESSAGE), silent=False)
    assert window.home.banner.text.text() == NOT_APPROVED_MESSAGE
    assert window.shown_dialogs and NOT_APPROVED_MESSAGE in window.shown_dialogs[-1]


def test_logout_clears_the_stored_token(window):
    window.store.save_refresh_token("abc")
    window.logout()
    assert window.store.load_refresh_token() is None
    assert window.account is None


def test_settings_page_saves(window, tmp_path):
    page = window.settings_page
    page.ram.setValue(12)  # 12 * 512 MB
    page.close_on_launch.setChecked(True)
    saved = (tmp_path / ".hollowclient" / "launcher_settings.json").read_text(encoding="utf-8")
    assert '"ram_mb": 6144' in saved and '"close_on_launch": true' in saved
