# Economy project

## Identity and pins

Mod ID `economy`; package `ca.emilybelnavis.mcmods.economy`; version 0.1.0; MIT license.
Gradle group/project ID: `ca.emilybelnavis.mcmods.economy`.
Maven coordinates: `ca.emilybelnavis.mcmods.economy:economy:0.1.0`.
Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21 / ModDevGradle 2.0.148 / Gradle 9.2.1.
No extra mod dependencies. Client/server runs register `sourceSets.main`.

## Architecture index

- `Economy.java`: item/block/entity registries and twelve ordered creative tabs.
- `CollectibleItems.java`: 126 inert items (8 cards, 61 coins, 57 bills); catalog order per series.
- `DecorativeItem.java`: localized collectible tooltip; cards stack to one, currency to 64.
- `api/`: server-thread public EconomyApi and immutable Transaction snapshots.
- `banking/Ledger.java`: checked integer arithmetic, validated atomic in-process transfers.
- `banking/BankData.java`: versioned overworld SavedData, UUID accounts, 100 history entries/account.
- `EconomyCommands.java`: balance, online payments, history, shop pricing, ATM information.
- `TerminalVariants.java`: 16 dye variants each for ATM/register, two decorative workstations,
  and the complete set of 17 valid register blocks for RegisterEntity.
- `WorkstationBlock.java`: 2×2 decorative teller/trading workstations with generated collision shapes.
- `WideMachineBlock.java`, `WideMachineLayout.java`: four-tile 2×2 footprint, placement/support,
  anchor ownership, whole-machine cleanup/loot, explosion routing, and unloaded-chunk guards.
- `AtmBlock.java`: existing ATM interaction from every section.
- `MachineShapes.java` in `src/generated/java`: shared clipped/rotated collision geometry.
- `tools/generate_wide_machines.py`: 2×2 models/states/loot; immutable 0.1.4 sources in
  `tools/model_sources/`; invokes `generate_machine_shapes.py` for server geometry.
- `TerminalBlock.java`, `RegisterEntity.java`: register placement, owner/product/price persistence,
  inventory planning and digital purchases from the single inventory directly below.
- `Messages.java`, `src/main/resources/assets/economy/lang/en_us.json`: messages/localization.
- `tools/collectibles.json`: visual face values, names, palettes; no economic values are assigned.
- `tools/generate_collectibles.py`: original pixel art/models to `src/generated/resources`.
  Run this to regenerate assets; preserve authored translations. Preview goes to `art/`.
- `src/ledgerChecks/`: arithmetic, SavedData, machine coordinate and assembled-shape tests.
- `src/machineGameTests/`: dev-only server-world placement/obstruction/loot/support checks.
  `runGameTestServer` uses `run-gametest`, never the development client world.
- `src/main/java/AGENTS.md`, `src/main/resources/AGENTS.md`: scoped guidance.
- `.codex/VERIFICATION.md`: current checks; `history/verification.md`: historical evidence.

## Current behavior and limits

Creative tabs: Economy (36 blocks), ten `Economy - XXX Cash` tabs, and Bank & Credit Cards.
Codes: EUR, GBP, AUD, NZD, CAD, USD, HKD, JPY, CNY, DKK.
CAD includes original vertical bills and five additional horizontal bills with separate IDs.
USD bills share the original $1 green palette; other original bill artwork is unchanged.
All 126 cards/currencies are decorative, without recipes or account state. Six legacy items,
EconomyConfig, and the copper minting recipe were removed. Old stacks are not converted.
Cash deposits/withdrawals are unavailable until collectible currency functionality is designed.
Existing bank balances, digital transfers, API, and register purchases remain.

ATMs use chat controls; no custom screen. Shop owner alone configures product/price;
product changes disable sales until the owner sets a new price. Stock containers keep vanilla
access rules. No claim protection is provided. API is experimental in 0.1.x.
All changes run on the server thread with validation before mutations, but normal Minecraft
world saving is used; no crash-safe journal spans bank/player/container files.
Machines from older versions should be broken/replaced to create their four-part footprint.

## Asset preservation

Generator/catalog edits must preserve all unrequested existing artwork. For 0.1.3,
all 72 non-USD original collectible PNGs are byte-identical to 0.1.2. Original CAD IDs
remain vertical; horizontal variants use `cad_bill_<value>_horizontal`.

## 0.1.4 terminals, currencies, and cards

Original teal ATM/register IDs and textures are preserved. ATM geometry is now wider; registers retain their size. Coloured variants
inherit the original models and override only `trim`. `tools/generate_terminal_variants.py`
generates variants, original workstation models, recipes, loot and block/item tags; its
`tools/terminal_variants.json` manifest includes localization and shape recommendations.
The original and all coloured cash registers share the existing block entity type.
Recolouring is a crafting recipe on unplaced block items, not an in-world dye interaction.

Teller station and stock trading computer are decorative; no new banking/trading behavior.
42 additional currency items are inert. Every pre-existing bill, coin, and bank-card texture
is byte-identical to 0.1.3. Only the four credit-card textures were restyled with original
issuer names and artwork loosely informed by the requested payment brands.
Tab icons follow the request; JPY uses its 1,000 bill and DKK its 20 coin because these
collections have no 20 bill. All localizations use `Economy - XXX Cash` for currency tabs.

## 0.1.5 machine footprint

All 17 ATM variants, teller station, and trading computer occupy 2w×2h×1d. One item places
four real tiles using `facing`, existing `half` and new `right` properties. All blocks keep IDs.
Each section uses local 0..16 clipped geometry with corresponding cropped UVs; inventory models
show the complete 32×32×16 footprint. Workstations scale x/y; ATMs scale x only.
Loot tables require lower-left state; player loot canonicalizes any mined tile after vanilla
harvest handling. Matched cleanup avoids adjacent machines and skips unloaded chunks.
Periodic validation cleans orphan tiles once neighbor chunks are available; pistons are blocked.
Model cuboids generate collision shapes for all orientations; test assembly must equal the
whole reference silhouette. Regenerating terminal colours invokes the wide generator last.
No currency/card textures or register models were changed in this update.

## Inventory transforms

`tools/center_item_models.py` resolves inherited machine geometry and computes each item's
GUI scale/translation using Minecraft's ItemTransform order. All36 block icons have centered
projected bounds and fit a14px square inside16px inventory/hotbar slots. Only display.gui is
modified. World models, item elements/UVs, textures, other display contexts and collisions
are preserved. The wide generator runs centering last, including coloured cash registers.
