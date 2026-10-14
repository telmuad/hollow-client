"""Entry script for PyInstaller (a plain script, so the package's relative imports keep working)."""

from hollow_launcher.__main__ import main

raise SystemExit(main())
