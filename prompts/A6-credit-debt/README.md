# A6 — Loans, credit and collateral

Status: draft DESIGN prompt. Refine specifications and future prompts only. Do not implement code, scaffold, run Gradle/tests/game processes, generate assets, change dependencies, or migrate worlds. Edit planning files only when explicitly requested. Reading a roadmap or this file is not implementation authorization.

## Goal and dependencies

Prepare a complete, reviewable specification for A6. Required upstream work: A4. Do not treat existing code as completed redesign work. Supply approved upstream contracts explicitly; mark missing contracts unresolved.

Fixed baseline: Minecraft 1.21.1, NeoForge 21.1.252, Java 21; server-authoritative behavior on both integrated and dedicated servers. Stage A owns money/FX/credit; custody owns shares/return obligations; no market-owned parallel banking database.

## Scope to flesh out

Principal/rate/term contracts; secured/unsecured and fixed/variable lending; accrual and payment schedules; repayment allocation; arrears/default/recovery; collateral registration/valuation/priority; early repayment/closure; margin-facing API.

## Mandatory current-feature reimplementation

Inventory coverage: No current loan engine; F10 card semantics remain explicit.

Credit-card artwork currently provides access only. Preserve that fact unless a separately approved borrowing product links it to a loan. Loans use shared accounting/clock contracts and explicit lender/borrower accounts; no second balance database. Specify offline debt and collateral claim conflicts before margin.

All assigned existing behavior must appear in the coverage table even when replacement architecture differs. Record any proposed behavior change and its approval status. Existing implementation and existing tests do not satisfy the new phase gate.

## Proposed bounded subprompts

Loan state machine; interest/accrual precision; payment allocation and schedules; collateral and default; administration and future margin contract.

For each subprompt define outcome, owner, prerequisites, persistence/entities, service/network contracts, permissions, state transitions, transaction boundary, server/worker handoff, time/version semantics, idempotency, configuration and diagnostics. Specify failures, overflow/rounding, stale requests, disconnects, retries and recovery where relevant. Avoid production code and premature class/file prescriptions.

## Acceptance scenarios to specify, not execute

Accrual retry cannot charge twice; partial/early payments allocate predictably; variable-rate changes have effective versions; loan/default/closure reconcile; pledged assets cannot secure incompatible claims twice.

Include concrete numerical or state-transition examples where useful. Describe future unit, integration, restart/crash and performance checks appropriate to each subprompt. Distinguish proposed guarantees from verified behavior. Future runnable deliveries require configured runClient/runServer, mod registration, required development runtime dependencies, and runClient help/dry-run verification; run no checks during this planning task.

## Required planning response

- Phase specification and dependency/ownership map.
- F-ID coverage table: old behavior, new contract, owning subprompt, acceptance cases and explicit later handoff.
- Ordered self-contained future prompts with scope exclusions and completion gates.
- Decisions needed now, with recommended alternatives and consequences; do not silently approve defaults.
- Open questions and approved-versus-proposed rules.

Stop at planning. Do not execute the prompts you draft or expand into later-phase implementation.
