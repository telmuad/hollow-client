import sys
from pathlib import Path

import pytest

# Make `import hollow_launcher` work when pytest runs from the launcher folder.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))


@pytest.fixture(autouse=True)
def _isolated_from_the_real_setup(monkeypatch, tmp_path_factory):
    """Tests never read your real launcher/config.json (with your app ID) and never open a real browser tab."""
    from hollow_launcher import appconfig, auth

    empty = tmp_path_factory.mktemp("no-config")
    monkeypatch.setattr(appconfig, "config_candidates", lambda: [empty / "config.json"])
    monkeypatch.setattr(auth.webbrowser, "open", lambda url, *a, **k: True)
