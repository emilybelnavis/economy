# Template — refine one bounded design prompt

You are preparing a future implementation prompt for [phase/subtask]. This is planning only. Do not write application code, run commands that build/test/start the mod, generate assets, migrate data, or edit files outside explicitly requested planning documents.

## Context supplied explicitly

- Relevant approved decisions and upstream contracts: [include actual text/versions].
- Relevant inventory and mandatory reimplementation rows: [F-IDs and behavior].
- Platform: Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21.

## Outcome and ownership

Describe the concrete behavior and the one service/module that owns it. List dependencies, inputs/results/events, permissions, persisted entities, transaction boundary, worker/server thread handoff and compatibility requirements. Existing implementation is a reference; this phase must rebuild its assigned functionality under the new contracts.

## Rules and failure semantics

Define monetary/security invariants, state transitions, overflow/precision/rounding, stale requests, idempotency, time semantics, cancellation/disconnect/restart, overload and observability. Distinguish financial holds from transient item reservations. Include raw-versus-display history and configuration/version effects where relevant.

## Scope and acceptance

List included behavior, excluded later-phase behavior and exact handoff owner. Provide concrete success, rejection, retry and recovery examples with expected outcomes. Describe future unit/integration/restart/crash/performance checks appropriate to this task without running them. For runnable delivery require working runClient/runServer, registered mod and dependencies on development runtime; verify runClient via help and dry-run only when implementation is separately authorized.

## Required response

Return a self-contained draft implementation prompt, dependency list, feature coverage table, unresolved consequential decisions, and future acceptance checklist. Label recommendations versus approved requirements. Split oversized work into ordered subprompts. The draft itself must say implementation requires separate explicit authorization; do not start implementation.
