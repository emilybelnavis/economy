# Original banking art (A6-05)

Run `python3 tools/banking-assets/generate.py` from the repository root, then `python3 tools/banking-assets/verify.py`. Generation uses only Python's standard library. `brands.json` records the approved catalog, palettes and short display labels; the verifier compares these to `InstitutionCatalog` and its approved network matrix.

All new art and geometry in this directory and the generated `banking/` resource directories are original project-authored work, dedicated under **CC0-1.0** (https://creativecommons.org/publicdomain/zero/1.0/). No downloaded images, fonts, real financial marks or copied trade dress were used. This dedication does not relicense existing project assets or Minecraft/NeoForge code. Existing terminal geometry is reused under the project's existing license.

`art.py`, `generate.py`, `hardware.py` and `agent.py` are editable construction sources. `sources/` contains pixel-aligned SVG artwork and geometry source snapshots. `resource_inventory.json` mirrors the shipped manifest at `assets/economy/banking/art_manifest.json`; it lists every issuer, network, card, icon, hardware family and agent resource. `previews/` contains all banks at inventory/UI scale, monochrome identities, hardware fronts and civilian agent views. Card lettering uses the original 3×5 pixel alphabet in `art.py`; DB/rectangle and CR/arrow identify product, network motifs identify networks, and I/II plus double trim identify tiers without color alone.

## Physical binding handoff

These are resource-only models, loaded through `BankingArtModels` for model baking. No new items, blocks, recipes, loot, entity registrations or world migrations are introduced. A6-05-01 owns physical registration, bank binding, orientation, safe migration and support/drop semantics. Generic legacy machines retain their current presentation until then. Card art grants no authority: A6-06 must validate server-issued credentials independently of appearance. The eight generic legacy collectible cards have been retired. The Bank & Credit Cards creative tab contains the current issuer designs.

For each bank, `<key>_atm` and `<key>_teller` item models preserve existing inventory geometry. Four block cells are ordered **right, base, upper_right, upper** (0–3), using the shared detailed elements/UVs; rotate all cells together from north-front. ATM screens, bezels and attached controls are recessed four model pixels behind their original position to expose the keypad; plain, dyed, branded and inventory appearances share that recess. Original kiosk geometry has two vertically stacked cells, a slender pedestal, header and distinct base; see manifest placement notes. Every front labels the bank and its native currency, independent of the world's cash currency. Hardware sources include model snapshots and textures.

## Collection Agent handoff

`CollectionAgentGeometry.create()` supplies a 64×64 humanoid layer with nine parts (standard humanoid bones plus body-attached folio and satchel). `collection_agent_model.json` records cube positions and UVs; `sources/agent/collection_agent_uv.svg` is the editable skin. The original Waystone civilian wears a teal coat, brass ledger badge and carries paperwork; no weapons or hostile imagery. A6-11 owns model-layer registration, renderer, entity, animation and all behavior. No agent is spawned by this package.

No persisted entities, schema migrations, journal decoders, money effects, service packets or authorization changes occur. Resource loading is client-only. Resource existence is never evidence of an account, facility or permission.
