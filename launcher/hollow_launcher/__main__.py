"""Entry point: `python -m hollow_launcher` (or HollowClient.exe).

  (no arguments)    open the launcher window
  --install-only    install or repair everything without a window, printing progress
  --check-updates   print installed vs. newest Fabric API / Sodium
  --selftest        build the window off-screen, check the bundled mod jar, exit 0 if all is well
  --screenshots DIR save a PNG of every launcher page into DIR (for checking the layout)
"""

from __future__ import annotations

import argparse
import sys

from . import APP_NAME, VERSION, paths


def _install_only() -> int:
    from .installer import InstallError, Installer, PrintProgress

    inst = Installer(paths.game_dir(), PrintProgress())
    try:
        version_id = inst.install_all()
    except InstallError as e:
        print(f"!! {e}", file=sys.stderr)
        return 1
    print(f"Done. Launch version: {version_id}")
    for k, v in inst.summary().items():
        print(f"   {k}: {v}")
    return 0


def _check_updates() -> int:
    from .installer import Installer, PrintProgress
    from .modrinth import ModrinthError

    inst = Installer(paths.game_dir(), PrintProgress())
    try:
        for u in inst.check_updates():
            print(f"{u.name}: installed {u.installed or '-'}, newest {u.latest}{'  (update available)' if u.needed else ''}")
    except ModrinthError as e:
        print(f"!! {e}", file=sys.stderr)
        return 1
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="HollowClient", description=f"{APP_NAME} launcher {VERSION}")
    parser.add_argument("--install-only", action="store_true")
    parser.add_argument("--check-updates", action="store_true")
    parser.add_argument("--selftest", action="store_true")
    parser.add_argument("--screenshots", metavar="DIR")
    args = parser.parse_args(argv)
    if args.install_only:
        return _install_only()
    if args.check_updates:
        return _check_updates()
    from .qmlui.app import run

    return run(selftest=args.selftest, screenshots=args.screenshots)


if __name__ == "__main__":
    sys.exit(main())
