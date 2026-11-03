# B6 — Margin lending, leverage and liquidation

Status: draft DESIGN prompt. Refine specifications and future prompts only. Do not implement code, scaffold, run Gradle/tests/game processes, generate assets, change dependencies, or migrate worlds. Edit planning files only when explicitly requested. Reading a roadmap or this file is not implementation authorization.

## Goal and dependencies

Prepare a complete, reviewable specification for B6. Required upstream work: A6, B4. Do not treat existing code as completed redesign work. Supply approved upstream contracts explicitly; mark missing contracts unresolved.

Fixed baseline: Minecraft 1.21.1, NeoForge 21.1.252, Java 21; server-authoritative behavior on both integrated and dedicated servers. Stage A owns money/FX/credit; custody owns shares/return obligations; no market-owned parallel banking database.

## Scope to flesh out

Margin-enabled accounts; initial/maintenance requirements; buying power/leverage caps; collateral eligibility/haircuts; borrowing/interest; FX-aware mark-to-market; margin calls/deadlines; deterministic partial/forced liquidation and residual debt.

## Mandatory current-feature reimplementation

Inventory coverage: No current margin; reuse redesigned shared credit contracts.

No independent margin money engine. Credit owns borrowing and cash liabilities; custody owns pledged assets. Resolve collateral priority and simultaneous orders/withdrawals so collateral cannot be counted twice.

All assigned existing behavior must appear in the coverage table even when replacement architecture differs. Record any proposed behavior change and its approval status. Existing implementation and existing tests do not satisfy the new phase gate.

## Proposed bounded subprompts

Eligibility/buying power; borrowing/interest; collateral valuation; calls/deadlines; liquidation order execution and debt recovery.

For each subprompt define outcome, owner, prerequisites, persistence/entities, service/network contracts, permissions, state transitions, transaction boundary, server/worker handoff, time/version semantics, idempotency, configuration and diagnostics. Specify failures, overflow/rounding, stale requests, disconnects, retries and recovery where relevant. Avoid production code and premature class/file prescriptions.

## Acceptance scenarios to specify, not execute

Concurrent withdrawal/order cannot bypass limits; shocks and illiquidity leave explicitly tracked debt; interest posts once; liquidation respects finite liquidity and bounded server work; restart preserves deadlines.

Include concrete numerical or state-transition examples where useful. Describe future unit, integration, restart/crash and performance checks appropriate to each subprompt. Distinguish proposed guarantees from verified behavior. Future runnable deliveries require configured runClient/runServer, mod registration, required development runtime dependencies, and runClient help/dry-run verification; run no checks during this planning task.

## Required planning response

- Phase specification and dependency/ownership map.
- F-ID coverage table: old behavior, new contract, owning subprompt, acceptance cases and explicit later handoff.
- Ordered self-contained future prompts with scope exclusions and completion gates.
- Decisions needed now, with recommended alternatives and consequences; do not silently approve defaults.
- Open questions and approved-versus-proposed rules.

Stop at planning. Do not execute the prompts you draft or expand into later-phase implementation.
