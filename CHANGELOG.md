# Changelog

Project changes are recorded in reverse chronological order, with the most recent changes first, by date and work package. Entries describe completed work and its verification; planned work is identified explicitly.




## 2026-11-03 — A4 ledger and transactions

- Added balanced settlement, durable holds, quoted FX, funded closure, and atomic denomination rebase.
- Added payment, retry, balance, history, and operator reconciliation commands. History retains original amounts and pairs entries by transaction ID.
- Added a shared ledger API for trusted server integrations. Schema 10 adds indexed account history. Existing worlds require a complete-world backup before upgrading.

## 2026-11-03 — A1 foundation acceptance

- Added restart-only worker, queue, callback, SQLite lock and drain settings. Ongoing UTC catch-up uses bounded chronological work and gates affected writes while behind.
- Added public `/economy status` and operator `/economy diagnostics` for storage health, recovery progress, queue stalls and sanitized failure details.
- Verified 67 tests, including sequential financial-world isolation and upgrades from supported schemas 1–3. The development client loaded the mod and SQLite. A disposable dedicated world backed up, restored separately, and booted with its original identity.
- Verified the release jars exclude fixtures. The foundation release ships without gameplay economic systems. [Acceptance and phase handoff](.codex/A1-05-03.md).

## 2026-11-03 — A1-04: Recovery, projections, backup/restore, and clock primitives

### Changed

- Added schema version 4 through the existing atomic upgrade chain and complete-world pre-upgrade backup gate; preserved earlier journal facts and identities.
- Added incremental startup validation before readiness: journal compatibility before migration, projection/checkpoint validation and rebuild, overdue updates, and final consistent-state checks. Unknown committed types/versions fail closed; new journal records require registered decoders.
- Added bounded projection rebuilds into separate generations, with atomic state/checkpoint commits, checksums, restart resume, immutable snapshots, and activation only after validation. Replay never reruns historical commands, random decisions, or external deliveries.
- Added persisted economic sequence, seed, stable interval/update identity, chronological dependency ordering, and explicit historical simulator versions. Update effects, journal outputs, and completion commit atomically; backward clocks retain debt, forward jumps become bounded catch-up, and sleep or `/time` do not drive economic time.
- Added a post-Minecraft-save clean-world witness distinct from database close, with shutdown-error and session checks.
- Added offline complete-world backup/restore commands with checksummed identity/version/journal/economic manifests, consistent DB/WAL handling, compatibility validation, staging, and a required new restore destination. Current worlds and backups are preserved; no online backup or retention automation was added.
- Documented A5 intent/reservation/commit/delivery/save/reconciliation evidence and the limits of database idempotency across Minecraft saves.

### Verified

- All 53 tests and the release build passed, including predecessor regressions, rebuild equality/resume/activation, rollback, version transitions, clock manipulation, batch/restart determinism, startup gating, backup rejection, separate restore, and restored-clock catch-up.
- Actual disposable child-process interruption tests covered an uncommitted rebuild batch and economic updates before/after commit. These are distinct from exception-injection tests and do not constitute hardware power-loss proof.
- Verified quiescent WAL-image inspection preserves sealed source files and includes committed WAL state.
- Gradle task inspection and required `runClient` help/dry-run checks passed. Actual client/JDBC loading succeeded; its smoke run was intentionally interrupted after loading.
- Actual disposable dedicated-server startup and restart reached ready state, then stopped normally with a complete drain and post-save clean-world witness.
- Ran the offline backup and separate-destination restore commands successfully against the stopped disposable server world. Release packaging excludes test projections, simulators, probes, and failure harnesses. `git diff --check` passed.

### Scope and handoff

- Covers F02/F03 recovery/checkpoint/version contracts, F09 journal rebuild support, and F23 restart/interruption/restore verification.
- Production registers no gameplay economic systems or fixture projections. No inflation, interest, FX, stock simulation, physical-item recovery, online backups, or automatic replacement was implemented.
- General restore currently supports clean schema-4 manifests. Runtime checks were on macOS; manual integrated-world play, hardware power-loss behavior, and the eventual market catch-up performance target were not exercised.
- A1-05 scheduling/diagnostics integration and acceptance remain unstarted. Protocol and A5 crash-boundary handoff: [.codex/A1-04.md](.codex/A1-04.md). Actual evidence: [.codex/VERIFICATION.md](.codex/VERIFICATION.md).

## 2026-11-03 — Dedicated A1-01 documentation

- Moved the original scaffold/lifecycle handoff from `.codex/PROJECT.md` into the missing `.codex/A1-01.md`, preserving its historical context and linking its verification evidence.
- Updated the project index to link all three completed package notes.
- Verified documentation links and `git diff --check`; no implementation changes or runtime checks were needed.

## 2026-11-03 — A1-03: Durable operations and immutable journal

### Changed

- Added schema version 3 through the existing atomic migration chain and offline complete-world backup requirement; preserved earlier foundation identity and migration history.
- Added versioned transaction envelopes, explicitly positioned immutable records, typed actor/service attribution, UTC audit instants, database-assigned sequences, durable results, and optional economic-update/correction references.
- Atomically commit journal records, same-store projections, canonical operation identity, and results. Persist terminal business rejections without domain effects.
- Added world-scoped operation deduplication across retries and restarts. Persist the full versioned canonical request; equivalent retries return the original outcome, while changed payloads conflict and unauthorized actors cannot retrieve outcomes.
- Added database protections against editing, deleting, replacing, or extending sealed journal facts. Startup and recovery validate protection triggers and foreign-key integrity.
- Added accepted/pending, committed, durably rejected, not-accepted, and unknown-outcome contracts. Request IDs trace attempts; operation IDs remain stable across retries.
- Added fail-closed connection handling and original-ID lookup after uncertain commits. Recovery never blindly repeats projections or substitutes a new operation ID; unconfirmed disposal retains world ownership.
- Added bounded server-thread admission/publication and immutable results. Cache/callback publication failure is logged separately and cannot reverse or relabel a durable commit; canceling a wait does not cancel accepted storage work.
- Added bounded journal paging and generic projection/process/failure fixtures confined to test sources.

### Verified

- All 39 tests passed, along with the release build, task inspection, and required `runClient` help/dry-run checks.
- Fixtures covered all-or-nothing multi-record/projection commits, 50 overlapping same-key submissions with one effect, canonical conflicts, authorization, stable terminal rejections, restart lookup, corrections, and append-only enforcement.
- Injected commit-response, rollback, close, and postcommit cleanup failures; verified original-outcome resolution or unknown/fail-closed behavior. Cache publication failures and canceled waits caused no duplicate durable effects.
- Terminated only disposable child processes at before/after-commit boundaries; reopening showed rollback before commit and original-outcome recovery after lost acknowledgment.
- Verified the supported schema-2 upgrade requires a stopped-state backup and preserves existing identity/history; removed protection triggers cause safe startup rejection.
- Actual disposable dedicated server reached ready state with schema 3 and stopped normally with a complete drain. Actual client/JDBC loading succeeded; the client smoke run was then intentionally interrupted.
- Inspected the release jar for the pinned driver and fixture exclusion; the runtime database had no test projection table. `git diff --check` passed.

### Scope and handoff

- Covers F02 (transaction/postcommit guarantees), F09 infrastructure (journal and durable operation identity), and F23 (atomicity/duplicate/interruption verification).
- No accounts, balances, financial postings, transfers, holds, or external/item-delivery protocols. A1-04 recovery integration and A4 financial semantics remain unstarted.
- Process-interruption and injected JDBC tests do not constitute hardware power-loss proof. Runtime checks were on macOS; manual integrated-world play and a separate installed-release runtime were not exercised.
- Protocol and handoff: [.codex/A1-03.md](.codex/A1-03.md). Actual checks and limits: [.codex/VERIFICATION.md](.codex/VERIFICATION.md).

## 2026-11-03 — Changelog ordering

- Reordered this changelog with the most recent changes first and made that ordering a project requirement.
- Verified entry order and `git diff --check`; documentation-only change.

## 2026-11-03 — A1-02: Identifiers, SQLite storage, and migrations

### Changed

- Added distinct identifier types for financial worlds, player/organization/system actors, accounts, transactions, operations, requests, and future payment, loan, and order entities; defined stable currency/catalog conventions and separate journal, economic, and audit values.
- Replaced the production no-persistence adapter with world-local SQLite storage, owned exclusively by the bounded storage worker. Added an OS ownership lock that excludes a second process until the connection is disposed.
- Bundled SQLite JDBC 3.53.4.0 and explicitly supplied it to client/server development runtimes. Kept the Minecraft, NeoForge, and Java baseline unchanged.
- Added recoverable world UUID initialization using a durable intent marker, an atomic SQLite foundation-schema transaction, and separately published world-side identity metadata.
- Added schema versions 1–2, ordered migration history, and atomic pending migration chains. Legacy, unsupported, corrupt, and wrong-world stores are rejected without replacement.
- Configured and verified WAL, FULL synchronization, foreign keys, and a five-second SQLite lock timeout.
- Added typed persistence and admission errors, readiness failure propagation, and a worker-only atomic transaction interface with bounded immutable query results for A1-03.
- Added an offline complete-world backup prerequisite for upgrades, with stopped-world locks, validated file hashes, and a receipt published only after successful backup validation. Startup validates backups; it never copies a running world.
- Added disposable storage fixtures and updated build/run and upgrade instructions.

### Verified

- All 22 lifecycle/storage tests and the release build passed; Gradle task inspection and required `runClient` help/dry-run checks passed.
- Fixtures covered fresh/reopened worlds, identity interruption boundaries, wrong/missing identity data, legacy/corrupt/future/empty stores, ordered upgrades, whole-chain rollback, stale/tampered backups, active-world rejection, cross-process ownership, lock timeout, read-only storage, missing driver, transaction bounds, and readiness propagation.
- Actual disposable dedicated-server runtime loaded the JDBC driver, created its SQLite foundation, reached ready state, and shut down successfully with a complete drain.
- Actual disposable client runtime loaded the JDBC driver and completed resource loading; the smoke run was then intentionally interrupted. Manual integrated-world play was not exercised.
- Ran the offline backup command successfully against the stopped disposable server world; inspected the release jar for the exact driver version/range, native libraries, licenses, and fixture exclusion. `git diff --check` passed.

### Scope and handoff

- Covers F02 (world storage), F03 (schema/migration capability), and F23 (storage verification). Legacy import remains explicitly excluded.
- Only foundation metadata and migration history are created. No accounts, balances, journal records, online backups, or restore operations were added.
- Cross-file initialization is recoverable, not a cross-file atomic transaction. Failed/uncertain writes are not blindly retried; durable operation resolution remains A1-03.
- Detailed protocol and verification results are recorded in `.codex/A1-02.md`.

## 2026-11-03 — Project change tracking

- Started this changelog with the completed A1-01 work and verification.
- Added a project instruction to record subsequent work here, including verification and remaining limitations.

## 2026-11-03 — A1-01: Scaffold and server lifecycle

### Changed

- Performed the authorized one-time reset on `redesign`, preserving `.git`, `prompts/`, and root `AGENTS.md`. The previous implementation remains recoverable from `main`.
- Created a fresh Economy mod scaffold pinned to Minecraft 1.21.1, NeoForge 21.1.252, and Java 21, with a newly generated Gradle 9.2.1 wrapper.
- Configured `runClient` and `runServer`, registered the main mod source set in development runs, separated their runtime directories, and enabled server console input.
- Added separate common and client entry points, mod metadata, release packaging, and test sources excluded from the release jar.
- Added one service container per server with initializing, ready, unavailable, stopping, and stopped states, readiness gating, isolated session identity, and repeatable shutdown handling.
- Added bounded storage and calculation workers, bounded completion delivery on server ticks, a callback-start budget, and shutdown drain and interruption handling.
- Added foundation API contracts for trusted server request context, correlation, payload bounds, and response status.
- Replaced the README with foundation build/run instructions and added fresh architecture and verification notes in `.codex/`.

### Verified

- Build and seven automated tests passed, covering lifecycle isolation, readiness and failure handling, worker bounds, thread ownership, callback delivery, shutdown, and payload limits.
- Gradle task inspection, `help --task runClient`, and `runClient --dry-run` passed.
- Actual development client loading succeeded; the smoke run was then intentionally interrupted. The dedicated server reached ready state and shut down through `stop` with a successful drain.
- Confirmed the release jar excludes tests and fixtures, and `git diff --check` passed.

### Scope and handoff

- Covers F01 (development scaffold), F02 (server lifecycle), F19 (request boundary contracts), and F23 (verification infrastructure).
- Storage is a no-persistence adapter. SQLite, migrations, durable commits, recovery, and gameplay are not implemented in this package.
- Sequential-world isolation was tested with fixtures; sequential integrated-world play was not manually exercised.
- Detailed results and limitations: [.codex/VERIFICATION.md](.codex/VERIFICATION.md). Architecture and A1-02 handoff: [.codex/PROJECT.md](.codex/PROJECT.md).
