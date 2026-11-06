# Planned A7–A10 scope

Planning only. A6-13 is accepted; these phases are not implemented. This document does not select an implementation prompt. Preserve existing worlds, accounts, balances and historical evidence.

## Sequence

| Phase | Scope |
| --- | --- |
| A7 | Village Banking Package |
| A8 | Economic simulation, formerly A7 |
| A9 | Basic item valuation |
| A10 | Core integration, carrying forward former A8 player experience and broader playtesting |

B2 securities, B4 trading and B6 expanded lending keep their own identities and prerequisites. Renumber current planning references; retain historical evidence as written.

## A7 — Village Banking Package

Target newly generated vanilla villages only. Retrofitting explored villages, player-built settlements and modded villages requires separate compatibility rules. Never overwrite player construction.

| Size | Presence | Currency/institution policy |
| --- | --- | --- |
| Small | One compact branch | Guaranteed server-currency Tier1/hybrid institution eligible for normal primary-account opening |
| Medium | One larger branch; chance of a second | Guaranteed local branch; possible eligible foreign institution |
| Large | Two or three distinct institution branches | Guaranteed local branch and at least one foreign institution when eligible |

Select from the existing 23 institutions with fixed native currencies and eligibility; never randomly change their currencies. Every targeted village must receive the required branches. Weighted pool entries alone cannot guarantee this.

Before implementation, settle deterministic village-size measurements/thresholds, extra-branch probabilities, supported starts, layout timing and safe placement/fallback rules. Establish a deliberate generation strategy that measures layout and reserves or finds sites with street access, terrain support and collision checks before finalizing placement. Prove feasibility across vanilla village types; silently skipping a required branch fails the guarantee. Specify failure/readiness behavior if no eligible local institution exists, foreign-provider fallback and behavior after world denomination changes.

Persist branch identity separately from employees, with institution/catalog reference, dimension/location, template/size, service anchors and lifecycle revision. Seed-stable selection and durable IDs must suppress duplicate branches and configuration across reloads and partial generation. Branch creation must not reseed institution capital or create customer money/accounts. Define recoverable placement/configuration activation because Minecraft world writes and ledger transactions are separate effects.

Current player-placed hardware starts unbound/disabled. Generated branches need an explicit trusted server bootstrap adapter through existing policy/journal services, with native-currency bindings and idempotent activation. Do not authorize banking from block appearance, NPC profession or client IDs. Reuse kiosk-grade live server checks for actor/session, reach, loaded placement/generation, institution, revisions, permissions and offered action. Preserve account caps, primary eligibility, specialist checks, quote consent and custody.

Bankers are villager-like NPCs with dedicated behavior and persistent branch/workstation assignment. They explain account opening, eligibility, branch institution/currency and banking mechanics, and can route customers into supported menus. Financial menu access requires the same server authorization as kiosks. Dialogue alone moves no money. ATMs, tellers and kiosks work without bankers.

The banker job block is a two-wide, two-high, one-deep desk with a computer monitor and ledger. Define anchor/satellites, facing, support/collision, placement rollback, teardown and single-drop behavior. Staffing follows branch size and valid desk capacity. Replacement is bounded and persistent: only active branches with valid vacant workstations can replace missing employees; deduplicate pending replacements across death, unload and restart. Losing a desk suspends its job slot until reassignment. Define replacement delay, caps and operator controls before implementation. Death/workstation loss never deletes the branch, accounts or money.

Broaden the kiosk to a two-wide/two-high service console with a broad screen, lower counter, institution header, recessed base and substantial side panels readable from several angles. Preserve registered IDs and authoritative bindings. Plan a collision-safe legacy representation or explicit upgrade for existing narrow kiosks; never automatically occupy an adjacent player block. Preserve loaded-only integrity, rotation, rollback, one survival drop and revision invalidation. Assets must be original or appropriately licensed.

Suggested independently selectable packages:

1. A7-01: generation feasibility, shared branch/bootstrap contracts and policy decisions.
2. A7-02: persistent branch lifecycle and recoverable hardware configuration.
3. A7-03: wider kiosk geometry/assets and safe existing-placement compatibility.
4. A7-04: vanilla village generation, institution selection and building templates.
5. A7-05: banker entity, desk, staffing/replacement and explanatory dialogue/menu adapters.
6. A7-06: integrated acceptance, documentation and handoff.

Prepare exact prerequisites when selecting a package. Generation and staffing consume the durable branch contract, not parallel financial services.

## A8 — Economic simulation

Retain the existing downstream prime contract: bounded registered scheduling, versioned future financial-day rates, retained history and captured fixed-loan terms. Initial configured prime and existing FX remain authoritative until approved adapters replace them. Phase renumbering does not authorize commodity-, trade- or money-supply-driven FX.

## A9 — Basic item valuation

Currencies remain fiat-like. Authorized one-time 28B institutional capital and operator-created cash/deposits remain separate from reference valuations. Banks spend finite funded cash; claims do not fund spending. Reference prices create no commodity reserve, guaranteed redemption, automatic trading, collateral eligibility or wealth qualification.

- Configurable base resource values: per-item values with explicit currency, exact monetary representation, validation and immutable configuration revisions. Iron/gold/diamond values do not make those items currency backing.
- Recipe-derived costs: per-output reference costs from supported inputs and output quantities. Set deterministic rules for ingredient alternatives, multiple recipes, reload/version, rounding and bounded work/depth. Detect cycles and unsupported/dynamic recipes; return explained unavailability rather than zero. Define container returns, byproducts, fuel/processing assumptions and reversible recipe handling before implementation.
- Currency and valuation APIs: immutable server results identifying item/quantity, supported component-sensitive identity, denomination, effective time and source/configuration/recipe revisions. Reuse existing money/FX contracts with captured conversion versions and rounding. Provide an integration SPI for separate mods; specific integrations require a request. Keep reference reads separate from settlement and financial qualification.
- Overrides and provenance: explicit item override, then configured base resource, then supported recipe derivation, otherwise unavailable. Preserve selected source and bounded derivation explanations. Overrides may intentionally break recipe consistency and must be identified. Reload invalidates affected cached values; historical consumers retain captured versions.

Suggested packages: A9-01 shared value/configuration/provenance contract; A9-02 bounded recipe derivation; A9-03 APIs/diagnostics and scoped acceptance. Finalize policies before implementation. Do not silently feed reference values into Tier2 wealth, collateral, FX, shop pricing or trading.

## A10 — Core integration

Carry forward banking explanations/navigation, window/GUI-scale review, external multiplayer/network conditions and combined borrowing-to-default-to-collection gameplay. Integrate branch discovery, banker availability, institution/currency explanations and valuation provenance. Reuse unchanged A6 evidence and retain explicit composite gameplay limits. Unsupported B-series capabilities remain unavailable.

## Verification

Apply the project verification policy to every package: [VERIFICATION.md](../VERIFICATION.md). Prose uses diff/link review only. Routine changes use at most one focused test invocation when useful, with named behavior tests and existing real-service/SQLite fixtures. No automatic test/full-suite phase gates. Use disposable worlds for necessary generation/placement/reload witnesses; never player worlds.

Ordinary runnable deliveries run `./gradlew build -x test`, `./gradlew help --task runClient` and `./gradlew runClient --dry-run` once. Keep Minecraft 1.21.1, NeoForge 21.1.252, Java 21, working client/server tasks, registered main source set and required development runtime dependencies. One owner runs Gradle/game checks sequentially. Full-suite checks require an explicit request or a concrete concern narrower checks cannot address.
