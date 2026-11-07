# Banker desk assets

Run `python3 tools/banker-desk/generate.py` from the repository. The script uses Python's standard library and writes the desk's blockstates, four wood palettes, four cell models per wood palette, item model and original material textures. North-facing geometry extends east from the lower-left anchor; rotations follow Minecraft's horizontal model convention.

The desk uses original veneer grain in four wood palettes, metal shoes, brass edge strips and drawer handles. Its monitor has a recessed account display, keyboard and mouse. The ruled ledger has a leather cover, with a pen cup beside it.

The monitor, ledger, supports, hardware pixels and model geometry are original and dedicated to the public domain under CC0 1.0. Veneer textures are authored in this generator for oak, jungle, acacia and spruce appearances.

The generator checks the complete 64-state variant count. Desk item placement ignores item block-state and block-entity components and creates a fresh unbound identity.
