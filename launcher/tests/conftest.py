import sys
from pathlib import Path

# Make `import hollow_launcher` work when pytest runs from the launcher folder.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
