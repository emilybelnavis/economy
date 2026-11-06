# A7 — Village Banking Package

Status: planning-only DESIGN specification. Refine specifications and draft future prompts only when selected. Reading this file does not authorize implementation, assets, dependency changes, migrations or game processes. A6-13 is accepted; reuse its delivered contracts and evidence.

Baseline: Minecraft 1.21.1, NeoForge 21.1.252, Java 21; server authority on integrated and dedicated servers. Preserve existing code, worlds, accounts, money and backups. [Shared A7–A10 planning boundaries](../../.codex/architecture/planned-a7-a10.md) and [current project state](../../.codex/PROJECT.md) supply scope. No parallel financial database or specific integration mod is authorized.

## Goal and dependencies

Make financial services available in every newly generated vanilla village through automatically configured branches of the existing 23 institutions. Depend on accepted A6 catalog, finite funding, hardware binding, branch admission, menus and server integration; retain A1 persistence/recovery and A5 physical custody. This is new functionality, not a requirement to rebuild accepted banking.

## Required scope

| Village size | Branches | Institution policy |
| --- | --- | --- |
| Small | One compact branch | Guaranteed server-currency Tier1/hybrid supporting normal primary opening |
| Medium | One larger branch; chance of a second | Guaranteed local branch; possible eligible foreign institution |
| Large | Two or three distinct institution branches | Guaranteed local branch; at least one foreign institution when eligible |

Select suitable institutions with fixed native currencies and current eligibility. Never change a bank's currency to fit generation. Weighted village pool entries alone do not meet the guarantee. Measure the chosen layout and reserve/find street-accessible sites with terrain support, collision checks and a deliberate fallback before generation finalizes. Target new vanilla villages only; explored, player-built and modded villages need separately approved compatibility rules. Do not overwrite player construction.

Persist branch identity, institution/catalog, dimension/location, template/size, service anchors and lifecycle revision independently of employees. Use deterministic generation IDs, duplicate suppression and recoverable activation across partial world/database effects. Generated hardware needs a trusted bootstrap through existing binding/policy/journal services because ordinary placements start unbound/disabled. Generating a branch must not reseed capital or create customer accounts, cash or authority.

Add dedicated villager-like banker behavior and assigned branch/workstation identity. Bankers explain account opening/eligibility, institution/native currency and supported mechanics, and route customers to supported menus. Financial access must pass kiosk-equivalent live server authorization and revision checks. Dialogue alone performs no financial mutation. ATMs, tellers and kiosks remain usable without staff.

The workstation is a two-wide, two-high, one-deep desk with a computer monitor and ledger. Staffing follows branch size and valid desk capacity. Persist bounded replacement state and deduplicate replacements after death, unload or restart. Only active branches with valid vacant workstations replace staff; losing a desk suspends that job slot. Death or workstation loss never deletes the branch, accounts or money.

Broaden the kiosk to a two-wide/two-high console with broad screen, lower counter, institution header, recessed base and substantial side panels. Preserve registered IDs and bank bindings. Existing narrow placements need collision-safe legacy handling or an explicit upgrade; never silently occupy neighboring construction. Define rotation, anchor/satellites, support, integrity, rollback, single-drop and revision invalidation for both desk and kiosk. Assets must be original or appropriately licensed.

## Proposed bounded packages

| Draft ID | Outcome | Prerequisite |
| --- | --- | --- |
| A7-01 | Generation feasibility, shared branch/bootstrap contracts and policy decisions | Accepted A6; affected vanilla generation/source review |
| A7-02 | Durable branch lifecycle and recoverable hardware activation | A7-01 |
| A7-03 | Wider kiosk geometry/assets and safe legacy placement handling | A7-01; existing A6 hardware contract |
| A7-04 | Vanilla village generation, institution selection and building templates | A7-02, A7-03 |
| A7-05 | Banker, desk, staffing/replacement and dialogue/menu adapters | A7-02, A7-03, A7-04 branch layout contract |
| A7-06 | Integrated scoped acceptance, documentation and handoff | A7-04, A7-05 |

These are draft boundaries, not executable prompts. Prepare only the selected package and its stated references. Split further if necessary before implementation.

## Decisions to settle before implementation

Define size metric/thresholds, extra-branch probabilities, village start coverage, generation hook/layout timing, safe site bounds and fallback guarantee. Specify missing-local/foreign-provider behavior and later denomination changes. Define branch activation/recovery, decommissioning, hardware repair/replacement, staffing caps/delay, workstation reassignment and operator controls. Define safe legacy kiosk migration. Record recommended choices separately from approved requirements.

## Acceptance scenarios to specify

Required branch counts/local eligibility across vanilla village types and size boundaries; foreign selection when eligible; terrain/collision fallback; no duplicate generation/configuration after reload or partial failure; normal primary opening through authenticated services; unauthorized/stale/out-of-reach NPC interactions reject; staff death/desk loss preserves all finance; replacement remains bounded; unattended terminals still work; old kiosks and adjacent construction survive upgrades.

Select meaningful witnesses for changed behavior when executing the chosen package. No blanket seed matrix, full suite or repeated historical gates is required by this list.

## Required planning output and handoff

Return a reviewable specification, decisions, shared interfaces, dependency/ownership map and ordered self-contained package prompts with exclusions, failure/recovery rules and proportionate checks. Extend F13/F18/F19/F21/F22 only where affected; record village generation and banker lifecycle as new scope. Publish stable branch/service contracts for A8–A10; do not start them.

## Verification scope

Follow [the project verification policy](../../.codex/VERIFICATION.md) for every package and sub-task. Planning uses diff/link review only; run no Gradle, tests or game processes now. Routine implementation uses at most one focused test invocation when useful, selecting named classes for meaningful changed behavior and reusing existing real-service/SQLite fixtures and unchanged evidence. Package/phase completion does not automatically require tests or a full suite. A full-suite run requires an explicit request or a stated concrete concern narrower checks cannot address.

For an ordinary runnable delivery, run `./gradlew build -x test`, `./gradlew help --task runClient` and `./gradlew runClient --dry-run` once. Keep working `runClient`/`runServer`, registered main source set and required development runtime dependencies. One owner runs Gradle/game checks sequentially. Use disposable worlds for necessary runtime/generation checks; preserve player worlds and backups.
