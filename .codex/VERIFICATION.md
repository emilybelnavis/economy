# Current verification

Use Java 21 and the wrapper. Run targeted checks first:

- `./gradlew ledgerTest`: integer arithmetic, overflow atomicity, insufficient funds, immutable capped history.
- `./gradlew persistenceTest`: actual SavedData save/load round-trip for balances and histories, unknown version rejection.
- `./gradlew build`: compilation, resources, both checks, distributable and sources JARs.
- `./gradlew help --task runClient` and `./gradlew runClient --dry-run`: task availability and wiring.
- `./gradlew runClient`: client loading/resource smoke check; stop after observing startup.
- `./gradlew runServer`: dedicated-server behavior; owner must accept Minecraft EULA.

ModDevGradle development runs bind `economy` to the main source set. No extra mod dependencies
are required. A dry run alone does not prove game behavior.

Before release, manually verify all twelve creative tabs, ATM information, two-player payments,
shop ownership, component-specific stock matching, insufficient stock/funds, purchases,
block rotation/loot, and save/reload across worlds and dimensions. Validate dedicated-server loading.
Hard-crash consistency is not guaranteed by ordinary SavedData/world saving.

Respect current user testing limits; historical restrictions are not durable defaults.
Append dated command results and limitations in `history/verification.md`.

For artwork changes, compare existing texture hashes and confirm only requested files change.
Check removed item IDs and recipes are absent from a clean JAR; preserve bank SavedData.

For terminal variants, check both block/item tags and recolouring recipes, lower-half-only
ATM loot, pairing in all orientations, and inclusion of all 17 register blocks in the
block entity type. Manually verify coloured register ownership, save/reload and purchases.
New decorative workstation shapes must rotate with the model and load on a dedicated server.

## Four-section machines

- `./gradlew machineLayoutTest`: all orientations, inverse anchor mapping, adjacent isolation.
- `./gradlew machineShapeTest`: all four clipped shapes assemble to the full model silhouette.
- `./gradlew runGameTestServer`: isolated real server world tests for all 19 machine types;
  placement/obstruction, survival drop count from each tile, creative removal, adjacent isolation,
  and support loss. No client world or EULA acceptance is needed for GameTestServer.
- Test classes/templates are a separate development source set, excluded from the release JAR.
- Manually inspect chunk unloading/reloading, explosions, mirror/structure loading, border/height
  limits, and client seams. Old placed machines must be broken/replaced; no footprint migration.

## SQLite/account/market overhaul

Run `./gradlew financeDatabaseTest` for real SQLite integration checks; no server needed.
`./gradlew build` includes it. `./gradlew runGameTestServer` includes async menu integration
checks; fast GameTest ticks need a larger tick budget for real executor work. Never block a
GameTest tick on futures. Schedule async assertions back into a helper sequence to expose failures.
Use `additionalRuntimeClasspath` for SQLite in NeoForge1.21.1 development runs, in addition to
implementation/jarJar. Verify actual client/server class loading as well as Gradle dependencies.
Release JAR must contain nested SQLite driver and both finance JSON catalogs, and exclude tests.
Check driver PRAGMA values on its writer connection; bank/market ledger reconciliation by shared
transaction ID, no overdraft/oversell, close/open caps, FX including JPY, and postcommit caches.
UI: 6x4 deposit grid, count/subtotal/deposit-total preview, exact denomination quantities and
source account amount, owner/op setup, account closure FX, stock funding/search/whole-share trades.
