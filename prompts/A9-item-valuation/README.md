# A9 — Basic item valuation

Status: planning-only DESIGN specification. Refine specifications and draft future prompts only when selected. Reading this file does not authorize implementation, assets, dependency changes, migrations or game processes. A6-13 is accepted; reuse its delivered contracts and evidence.

Baseline: Minecraft 1.21.1, NeoForge 21.1.252, Java 21; server authority on integrated and dedicated servers. Preserve existing code, worlds, accounts, money and backups. [Shared A7–A10 planning boundaries](../../.codex/architecture/planned-a7-a10.md) and [current project state](../../.codex/PROJECT.md) supply scope. No parallel financial database or specific integration mod is authorized.

## Goal and dependencies

Provide configurable resource values and recipe-derived reference costs with currency-aware APIs, overrides and explanations. Use A2 monetary/FX/denomination contracts and existing server lifecycle/configuration services. A9 follows A8 in the selected phase sequence, but any simulation input must have an explicit contract; do not invent a runtime dependency on branch staffing or securities.

## Required scope

- Configurable base resource values, including resources such as iron, gold and diamonds, with explicit currency, exact representation, validation and immutable configuration revisions.
- Per-output recipe reference costs from supported inputs and output counts. Set deterministic ingredient-alternative and multiple-recipe selection, precision/rounding, bounded depth/work and cycle handling. Unsupported/dynamic inputs yield explained unavailability, not zero.
- Immutable server-side currency/valuation APIs reporting item/quantity and supported component-sensitive identity, denomination, effective time, source/configuration/recipe revisions and availability. Reuse money and captured FX/version/rounding contracts. Provide a separate-mod integration SPI; specific integrations require a request.
- Override precedence: explicit item override, configured base resource, supported recipe derivation, otherwise unavailable. Preserve chosen source and bounded derivation provenance. Identify intentional override inconsistencies, invalidate affected caches on reload and retain versions captured by historical consumers.

Currencies remain fiat-like. Existing authorized one-time 28B institution capital and operator-created cash/deposits are separate from item reference values. Banks retain finite funded spending. Reference costs do not add commodity backing, redemption guarantees, market prices, shop repricing, automatic trades, Tier2 wealth qualification or collateral eligibility.

## Proposed bounded packages

| Draft ID | Outcome | Prerequisite |
| --- | --- | --- |
| A9-01 | Shared monetary value, configuration, override/provenance and availability contract | Existing currency/FX services; accepted preceding phase handoffs |
| A9-02 | Bounded deterministic recipe derivation and reload/cache behavior | A9-01 |
| A9-03 | Public API/SPI, diagnostics and scoped integrated acceptance | A9-01, A9-02 |

These are draft boundaries. Prepare only a selected package; do not execute from this README.

## Decisions to settle before implementation

Define supported recipe types, ingredient alternatives/tags, multiple valid recipes, output batching, fractional costs and final rounding. Specify container returns, byproducts, reversible recipes, processing/fuel assumptions, cycle policy and derivation limits. Define item component sensitivity, override validation/precedence, cache invalidation, historical version retention and foreign-denomination conversion. Recommendations do not silently approve defaults.

## Acceptance scenarios to specify

Numerical base/override and multi-output examples; alternative and competing recipes; missing inputs and cycles; unsupported recipes; container/byproduct policy; overflow and rounding; versioned FX conversion; reload invalidation and retained provenance. Confirm valuation reads cannot issue money, mutate balances or qualify financial products. Use a few meaningful changed-behavior cases rather than duplicating every algorithm branch.

## Required planning output and handoff

Return a reviewable specification, source/precedence rules, API/result contracts, numerical examples, unresolved decisions and ordered bounded implementation prompts. This is new scope; do not invent historical F-ID coverage. Hand read-only reference values and explanations to A10; securities marks, collateral/trading consumers and specific integrations remain separately authorized work.

## Verification scope

Follow [the project verification policy](../../.codex/VERIFICATION.md) for every package and sub-task. Planning uses diff/link review only; run no Gradle, tests or game processes now. Routine implementation uses at most one focused test invocation when useful, selecting named classes for meaningful changed behavior and reusing existing real-service/SQLite fixtures and unchanged evidence. Package/phase completion does not automatically require tests or a full suite. A full-suite run requires an explicit request or a stated concrete concern narrower checks cannot address.

For an ordinary runnable delivery, run `./gradlew build -x test`, `./gradlew help --task runClient` and `./gradlew runClient --dry-run` once. Keep working `runClient`/`runServer`, registered main source set and required development runtime dependencies. One owner runs Gradle/game checks sequentially. Use disposable worlds for necessary runtime/generation checks; preserve player worlds and backups.
