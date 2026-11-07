# Village bank templates

Original mid-century modern bank buildings created with the connected GenMC studio,
following the approved reference in thread `01a1b341-0a81-7fa0-9812-17c9ca0c1da5`.
The original design and generated structure assets are dedicated under CC0-1.0;
Minecraft block names are references to game assets, which are not redistributed here.

`genmc/projects.json` records the reference and five private studio collections.
Each collection contains compact and larger branches for all 23 institutions.
The adjacent JavaScript programs and compressed JSON exports retain their source.
No studio pairing credentials are included.

Run `python3 tools/village-banks/export_templates.py` from the repository to convert
the exact studio block exports into 230 standard structure NBT files and their
SHA-256 manifest. The converter crops/translates exported geometry, inserts the
registered wide Economy kiosk blocks in reserved service bays and writes bank sign
text. It does not invent replacement building geometry.

Plains uses oak, desert jungle, savanna acacia, snowy spruce and taiga spruce.
Every branch has glazing, a flat roof, institution colors, a geometric roof mark,
lighting, furniture and a bed. Compact branches reserve one kiosk and one desk;
larger branches reserve two of each. The converter installs real banker desks and
rotates both desks and consoles to local south. Their anchors switch columns within
the two-block bays, with coordinates recorded in the manifest.

Templates are 11×8×9 or 17×9×11, including explicit interior air. Runtime generation
rotates their states and anchors together, validates supports and connects paths.
The GenMC scene uses clear service bays because its renderer lacks mod block IDs;
the shipped NBT contains real kiosks rather than colored machine placeholders.
