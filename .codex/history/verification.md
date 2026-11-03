# Verification history

## 2026-11-02 — 0.1.0 initial implementation

Staged build with Java 21: `./gradlew build` including ledger and SavedData round-trip checks.
Client task inspection and dry-run succeeded. Actual development client discovered Economy
0.1.0 with Minecraft 1.21.1 and NeoForge 21.1.252 and loaded mod resources/texture atlases;
then intentionally interrupted (exit 130). No in-world interaction was performed.
First launch printed a NightConfig temporary-file watcher exception on macOS; client continued
loading. No economy resource errors appeared in latest.log. Gameplay and dedicated-server
behavior remain unverified. Final destination build results are recorded after copying.

Final destination: `./gradlew clean build help --task runClient` passed, including both
ledger and SavedData checks. `./gradlew runClient --dry-run` passed. Repository AGENTS.md
files are excluded from game resources and source JAR packaging. JSON resources in the
final distributable parse successfully. Gameplay and dedicated-server checks remain pending.

## 2026-11-02 — 0.1.1 creative tab and terminal models

`./gradlew build help --task runClient`, `./gradlew runClient --dry-run`, and final
`./gradlew build` passed with Java 21. Existing ledger and SavedData checks passed.
Development client discovered Economy 0.1.1 and loaded the redesigned resources and
texture atlases without model/resource errors in latest.log, then was intentionally
interrupted. Final JSON geometry refinements were packaged and inspected in a software
model preview. This preview is not an in-game screenshot. All final JAR JSON parses.
Manual two-block placement, support removal, survival/creative breaking, loot count,
creative-tab contents, and interaction on both halves still need in-world confirmation.
Existing one-block ATMs should be broken/replaced to create the new upper half.

## 2026-11-02 — 0.1.2 decorative cards and currency sets

Added 79 inert items (4 credit cards, 4 bank cards, 39 coins, 32 bills). Full build,
existing ledger tests, SavedData tests, runClient task help and dry-run passed.
Validated exact catalog/model/Java-ID/localization coverage, unique PNG content, 64px dimensions,
and presence of all 158 sprite/model resources in the final JAR. All JAR JSON parses.
Client discovered Economy 0.1.2 and loaded mod resources and the expanded texture atlas;
no collectible model or texture errors appeared. Session-profile lookup timed out at Mojang's
service; the client continued loading. Smoke-test client intentionally interrupted afterward.
The client subsequently entered the existing development world (Dev joined), but no item
behavior or creative inventory contents were manually validated by the agent. New items have no economic value,
recipes, account state, or ATM integration; original valued items are unchanged.

Design references (inspiration only; no artwork copied):
- https://www.ecb.europa.eu/euro/banknotes/current/design/html/index.en.html
- https://www.bankofengland.co.uk/banknotes/current-banknotes
- https://www.rba.gov.au/education/resources/in-a-nutshell/pdf/banknotes-in-australia.pdf
- https://www.rbnz.govt.nz/money-and-cash/banknotes-and-coins/banknotes-in-circulation
- https://www.bankofcanada.ca/banknotes/
- https://www.uscurrency.gov/denominations

## 2026-11-02 — 0.1.3 currency cleanup and creative tabs

Clean build, ledger checks, SavedData round-trip, runClient task inspection and dry-run passed
with Java 21. Final JAR contains 84 collectibles, all required PNG/model pairs and valid JSON;
legacy six item assets, copper recipe, and EconomyConfig class are absent.
All 72 original non-USD collectible textures match the 0.1.2 SHA-256 baseline exactly.
Only six existing USD textures changed (the $1 already used the chosen green); five horizontal
CAD textures/models were added. Software contact sheet inspected; other bill layouts unchanged.
Eight creative tabs configured in sequence: blocks, EUR, GBP, AUD, NZD, CAD, USD, cards.
No client launched or in-world creative-tab inspection performed for this update.
Legacy cash exchange commands/config removed; digital banking core unchanged and tested.
Old legacy item stacks are not migrated. Original CAD item IDs still reference vertical bills.

## 2026-11-02 — 0.1.4 terminal colours, workstations, cards, four currency sets

`./gradlew build help --task runClient` and `./gradlew runClient --dry-run` passed on Java21.
Ledger and SavedData checks passed. Client loaded Economy0.1.4, resource atlases, recipes, and
entered the existing development world without ERROR/Exception/model/recipe failures in
latest.log. Client left running for user review. No automated creative-tab clicks, block
placement, coloured register purchases or dedicated-server launch were performed.

Validated 126 collectible IDs against catalog/model/sprite/localization, requested tab icons,
all ten `Economy - XXX Cash` titles, 34 new block states/models/recipes/loot, 17-entry ATM and
register item tags, no duplicate main/generated paths, and all final JAR JSON. Resolved all
local model parents/texture references. Coloured RegisterEntity valid blocks include all17.
Software model/sprite previews visually inspected (not in-game screenshots).

Baseline hash comparison: every old bill, coin, bank card, original teal model, and original
body texture unchanged; exactly four old credit-card PNGs restyled. No real logos copied.
Teller/trading blocks are decorative. All new currency remains inert. Remaining manual checks:
colour crafting, two-half ATM placement/loot, coloured register ownership and save/reload,
all12 creative tabs in-game, workstation collision rotation, and dedicated-server loading.

## 2026-11-02 — 0.1.5 four-section 2×2×1 machines

Clean build and runClient task inspection passed. Banking/SavedData checks passed; machine
coordinate checks passed166 assertions and assembled collision shape checks passed108.
`./gradlew runGameTestServer` passed all4 required server-world tests, covering all19 types
(17ATMs+2workstations) in four facings: actual BlockItem placement, obstruction/no partial
placement or item consumption, survival loot from each tile, Creative no-loot removal,
adjacent-machine isolation, and delayed support-loss removal with exactly one drop.
GameTestServer ran an isolated world and exited0; no client world used or EULA modified.
First test startup generated missing server.properties defaults after logging that file absent.
Remaining manual checks include explosions, border/height limits, mirror/structure loading,
chunk unload/reload behavior and actual client rendering seams. Old machines need replacement.

Final 0.1.5 client task dry-run passed with the dev test source set. Actual client discovered
0.1.5 and loaded resized model resources/texture atlases without model/recipe errors; Mojang's
session-profile lookup timed out, as in a prior launch, unrelated to machine resources.
Client left open. Final JAR validated: 19 resized machines, 304 facing/part variants,
canonical lower-left loot, generated collision class, all JSON valid, and dev GameTest
classes/template excluded. All126 currency/card PNGs match the preceding version exactly.

## 2026-11-02 — 0.1.6 inventory icon centering

Fixed36 block item GUI transforms using projected geometry bounds instead of guessed offsets.
Icons have14px maximum extent inside16px slots. An independent Java/JOML quaternion check
using Minecraft's actual ItemTransform rotation order verified all36 projected centres and
bounds within0.0001px tolerance. Resource baseline diff: exactly36 item-model JSON files and
mod version metadata changed; all other resources unchanged. World/item geometry, UVs,
textures, collision shapes and non-GUI display contexts unchanged. Regeneration hook added.
`./gradlew build help --task runClient` passed, including banking/layout/shape checks.
This visual-only change did not warrant rerunning the server-world lifecycle suite.

## 2026-11-02 — project identity and version reset

Gradle group set to ca.emilybelnavis.mc-mods.economy; version reset to0.1.0 in Gradle
and NeoForge metadata. Minecraft namespace remains economy; Java package remains
 dev.emily.economy. Current implementation retained. Clean build and banking/layout/shape
checks passed; runClient task help and dry-run passed. Final JAR metadata verified0.1.0.

## 2026-11-02 — Group and source package migration

Set Gradle group to `ca.emilybelnavis.mcmods.economy`; migrated main, generated, ledger-check and GameTest Java packages and generator output paths. Existing version preserved. Java 21 clean build, ledger/persistence/layout/shape checks, GameTest compilation, runClient help and dry-run passed. Release JAR contains new package paths and no old `dev/emily/economy` classes.
