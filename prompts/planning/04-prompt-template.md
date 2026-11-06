# Template — refine one bounded design prompt

You are preparing a future implementation prompt for [phase/subtask]. This is planning only. Do not write application code, run commands that build/test/start the mod, generate assets, migrate data, or edit files outside explicitly requested planning documents.

## Context supplied explicitly

- Relevant approved decisions and upstream contracts: [include actual text/versions].
- Relevant inventory and mandatory reimplementation rows: [F-IDs and behavior].
- Platform: Minecraft 1.21.1 / NeoForge 21.1.252 / Java 21.

## Outcome and ownership

Describe the concrete behavior and the one service/module that owns it. List dependencies, inputs/results/events, permissions, persisted entities, transaction boundary, worker/server thread handoff and compatibility requirements. Reuse accepted contracts and unchanged evidence; implement only the selected changed behavior.

## Rules and failure semantics

Define monetary/security invariants, state transitions, overflow/precision/rounding, stale requests, idempotency, time semantics, cancellation/disconnect/restart, overload and observability. Distinguish financial holds from transient item reservations. Include raw-versus-display history and configuration/version effects where relevant.

## Scope and acceptance

List included behavior, excluded later-phase behavior and exact handoff owner. Provide concrete success, rejection, retry and recovery examples with expected outcomes. Select proportionate future checks for meaningful changed behavior without running them; test categories are options, not cumulative gates. For runnable delivery require working runClient/runServer, registered mod and dependencies on development runtime; verify runClient via help and dry-run only when implementation is separately authorized.

## Required response

Return a self-contained draft implementation prompt, dependency list, feature coverage table, unresolved consequential decisions, and future acceptance checklist. Label recommendations versus approved requirements. Split oversized work into ordered subprompts. The draft itself must say implementation requires separate explicit authorization; do not start implementation.

## Verification scope

Follow [the project verification policy](../../.codex/VERIFICATION.md) for every package and sub-task. Planning uses diff/link review only; run no Gradle, tests or game processes now. Routine implementation uses at most one focused test invocation when useful, selecting named classes for meaningful changed behavior and reusing existing real-service/SQLite fixtures and unchanged evidence. Package/phase completion does not automatically require tests or a full suite. A full-suite run requires an explicit request or a stated concrete concern narrower checks cannot address.

For an ordinary runnable delivery, run `./gradlew build -x test`, `./gradlew help --task runClient` and `./gradlew runClient --dry-run` once. Keep working `runClient`/`runServer`, registered main source set and required development runtime dependencies. One owner runs Gradle/game checks sequentially. Use disposable worlds for necessary runtime/generation checks; preserve player worlds and backups.
