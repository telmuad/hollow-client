# Hollow Client branding

The emblem is a cut crystal (a hexagon with a flat table and six bevels) with an **H cut out of its middle**, so the
centre is literally hollow. Its edge glows from violet to cyan and a small spark sits at its upper right. It is fully
original: drawn from geometry in `tools/emblem.py`, no traced or borrowed art.

| File | What it is |
|---|---|
| `emblem.svg` | Master, full color with glow |
| `emblem-small.svg` | Simplified for 16–48 px (no facets or spark, bolder H) |
| `emblem-flat-white.svg`, `emblem-flat-black.svg` | Single color |
| `png/emblem-<size>.png` | 16, 32, 48, 64, 128, 256, 512, 1024 px (16–48 use the simplified drawing) |
| `emblem.ico` | Multi-size Windows icon (the launcher's `icon.ico` is a copy) |
| `wordmark.svg`, `wordmark.png` | "HOLLOW" bold with "CLIENT" small, in Inter (SIL Open Font License) |
| `skyblock-badge.svg`, `.png` | The emblem over a small floating island, for SkyBlock Mode |
| `banner.png` | 1280×320, shown at the top of the README |
| `social-preview.png` | 1280×640, for GitHub's link previews |
| `concepts/` | The three concepts (A Facet Hex — chosen, B Orbit Shard, C Crystal Ring) |

## Regenerate everything

From the repository root:

```
launcher\.venv\Scripts\python branding\tools\emblem.py
```

This also refreshes the copies the mod and launcher use: the mod's `icon.png`, the in-game emblem layers
(`textures/gui/emblem/`, white so the game can color them with your theme), the sigil and wordmark textures, and the
launcher's `logo.png`, `icon.ico`, `emblem_flat.png`, `wordmark.png` and intro layers. Then rebuild (`scripts\build_all.bat`).

To use another concept instead, change `CHOSEN` near the top of `tools/emblem.py` to `"b-orbit-shard"` or
`"c-crystal-ring"` and run it again. `... emblem.py concepts` redraws only the concept sheets.

## Set the social preview on GitHub

GitHub doesn't read the social preview from the repository; it has to be uploaded once:

1. Open the repository on github.com and click **Settings** (you need to be the owner).
2. In **General**, scroll to **Social preview** and click **Edit** → **Upload an image…**
3. Choose `branding/social-preview.png` (1280×640, under 1 MB) and save.

Links to the repository (Discord, chats, search results) then show the Hollow card.
