# A8 — Core integration, UI and hardening

Status: draft DESIGN prompt. Refine specifications and future prompts only. Do not implement code, scaffold, run Gradle/tests/game processes, generate assets, change dependencies, or migrate worlds. Edit planning files only when explicitly requested. Reading a roadmap or this file is not implementation authorization.

## Goal and dependencies

Prepare a complete, reviewable specification for A8. Required upstream work: A1–A7. Do not treat existing code as completed redesign work. Supply approved upstream contracts explicitly; mark missing contracts unresolved.

Fixed baseline: Minecraft 1.21.1, NeoForge 21.1.252, Java 21; server-authoritative behavior on both integrated and dedicated servers. Stage A owns money/FX/credit; custody owns shares/return obligations; no market-owned parallel banking database.

## Scope to flesh out

Public API/events; banking/FX/statement UI; permissions/admin/audit; explicitly chosen compatibility adapters; migrations/restores/recovery; throughput and persistence budgets; stock-facing documentation.

## Mandatory current-feature reimplementation

Inventory coverage: F06 chooser/admin UI, F18 banking/FX/history, F19 core synchronization, F20 stabilized API/admin, F23 core coverage.

Finish all existing banking, history, terminal setup, account lifecycle and world-currency UX, including frozen display history, cursor pagination, historical FX pair views, clipped native controls/localization. Stabilize future-based API without leaking database/client implementation types. No specific integration mod is authorized by generic adapter scope.

All assigned existing behavior must appear in the coverage table even when replacement architecture differs. Record any proposed behavior change and its approval status. Existing implementation and existing tests do not satisfy the new phase gate.

## Proposed bounded subprompts

Core UI and history; API/events; administration/auditing; compatibility and migration procedures; operational/performance acceptance.

For each subprompt define outcome, owner, prerequisites, persistence/entities, service/network contracts, permissions, state transitions, transaction boundary, server/worker handoff, time/version semantics, idempotency, configuration and diagnostics. Specify failures, overflow/rounding, stale requests, disconnects, retries and recovery where relevant. Avoid production code and premature class/file prescriptions.

## Acceptance scenarios to specify, not execute

Integrated and dedicated servers satisfy the same authority/recovery contract; two-player/reconnect/shutdown flows covered; account/ledger/issuance reconcile; development runClient wiring and actual runtime checks scheduled for implementation. Core is playable without stocks.

Include concrete numerical or state-transition examples where useful. Describe future unit, integration, restart/crash and performance checks appropriate to each subprompt. Distinguish proposed guarantees from verified behavior. Future runnable deliveries require configured runClient/runServer, mod registration, required development runtime dependencies, and runClient help/dry-run verification; run no checks during this planning task.

## Required planning response

- Phase specification and dependency/ownership map.
- F-ID coverage table: old behavior, new contract, owning subprompt, acceptance cases and explicit later handoff.
- Ordered self-contained future prompts with scope exclusions and completion gates.
- Decisions needed now, with recommended alternatives and consequences; do not silently approve defaults.
- Open questions and approved-versus-proposed rules.

Stop at planning. Do not execute the prompts you draft or expand into later-phase implementation.
