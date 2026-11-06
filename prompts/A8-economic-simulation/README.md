# A8 — Economic simulation and monetary policy

Status: draft DESIGN prompt. Refine specifications and future prompts only. Do not implement code, scaffold, run Gradle/tests/game processes, generate assets, change dependencies, or migrate worlds. Edit planning files only when explicitly requested. Reading a roadmap or this file is not implementation authorization.

Formerly A7; A7 now owns village banking.

## Goal and dependencies

Prepare a complete, reviewable specification for A8. Required upstream work: A2, A6; A1 clock and A4 accounting. A7 precedes this phase in the plan; consume its branch interfaces only if needed. Reuse accepted upstream contracts and unchanged evidence; mark missing contracts unresolved.

Fixed baseline: Minecraft 1.21.1, NeoForge 21.1.252, Java 21; server-authoritative behavior on both integrated and dedicated servers. Stage A owns money/FX/credit; custody owns shares/return obligations; no market-owned parallel banking database.

Preserve [the accepted prime handoff](../../.codex/architecture/a6-downstream.md): versioned future rates, retained history and fixed-loan terms. Valuation is A9; commodity backing or supply/trade-driven FX needs an explicit approved design.

## Scope to flesh out

Persisted economic scheduler; inflation/deflation; currency supply/issuance/sinks; reference interest and credit conditions; regional factors; economic event history/overrides; intensity controls; indicator API.

## Mandatory current-feature reimplementation

Inventory coverage: F05 evolves from configured recording; F17 clock coordination only.

Unify explicit clock contracts instead of accidentally mixing current UTC FX and in-game market days. Preserve recorded-rate history while adding deterministic evolution. Ground supply metrics in ledger and cash issuance; avoid disconnected simulated money totals.

Record affected feature coverage and proposed behavior changes. Preserve accepted behavior and reuse unchanged evidence; do not rebuild earlier phases merely to satisfy this planning document.

## Proposed bounded subprompts

Economy clock/catch-up; supply metrics; policy/credit indicators; FX/event coupling; configuration/override/audit.

For each subprompt define outcome, owner, prerequisites, persistence/entities, service/network contracts, permissions, state transitions, transaction boundary, server/worker handoff, time/version semantics, idempotency, configuration and diagnostics. Specify failures, overflow/rounding, stale requests, disconnects, retries and recovery where relevant. Avoid production code and premature class/file prescriptions.

## Acceptance scenarios to specify, not execute

Sleep/time commands/restarts/offline periods cannot apply events twice; bounded catch-up; same approved seed/version inputs reproduce updates; changes reach FX/loan consumers at defined boundaries.

Include concrete numerical or state-transition examples where useful. Select proportionate future checks for changed behavior under the project verification policy; categories are options, not cumulative gates. Distinguish proposed guarantees from verified behavior. Future runnable deliveries require configured runClient/runServer, mod registration, required development runtime dependencies, and runClient help/dry-run verification; run no checks during this planning task.

## Required planning response

- Phase specification and dependency/ownership map.
- F-ID coverage table: old behavior, new contract, owning subprompt, acceptance cases and explicit later handoff.
- Ordered self-contained future prompts with scope exclusions and completion gates.
- Decisions needed now, with recommended alternatives and consequences; do not silently approve defaults.
- Open questions and approved-versus-proposed rules.

Stop at planning. Do not execute the prompts you draft or expand into later-phase implementation.

## Verification scope

Follow [the project verification policy](../../.codex/VERIFICATION.md) for every package and sub-task. Planning uses diff/link review only; run no Gradle, tests or game processes now. Routine implementation uses at most one focused test invocation when useful, selecting named classes for meaningful changed behavior and reusing existing real-service/SQLite fixtures and unchanged evidence. Package/phase completion does not automatically require tests or a full suite. A full-suite run requires an explicit request or a stated concrete concern narrower checks cannot address.

For an ordinary runnable delivery, run `./gradlew build -x test`, `./gradlew help --task runClient` and `./gradlew runClient --dry-run` once. Keep working `runClient`/`runServer`, registered main source set and required development runtime dependencies. One owner runs Gradle/game checks sequentially. Use disposable worlds for necessary runtime/generation checks; preserve player worlds and backups.
