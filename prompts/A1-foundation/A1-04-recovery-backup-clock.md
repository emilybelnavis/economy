# A1.4 — Recovery, projections, backup and clock primitives

Status: implemented and verified. Original package requirements below; do not repeat completed work automatically.
Prerequisite: A1-02 and A1-03. For explicitly requested work, apply the [A1 execution contract](CONTRACT.md).

## Approved A1-04 decisions

Startup order: storage recovery; world identity/schema/record compatibility validation; approved migration with backup prerequisites; projection/checkpoint validation; required rebuilds; overdue economic updates; ready-state publication. Expose progress/failures. Corrupt authoritative records or unknown committed types fail closed; replay does not rerun historical commands/random decisions or side effects.

Each projection has version, last fully applied transaction/checkpoint and build state. Rebuild into a separate generation in bounded batches; changes/checkpoint commit together. Validate and atomically activate the completed generation; never expose half-built state. Restart resumes committed progress. A1 uses a generic fixture. Define immutable snapshot/checkpoint interfaces only as needed; no premature second authoritative snapshot store.

Back up the complete world including economy directory after clean shutdown, not the financial database alone. Manifest: financial-world UUID, mod/schema/journal versions, last journal sequence, economic checkpoint, UTC backup timestamp, checksums and clean-shutdown status. Handle SQLite companion/WAL files consistently. A documented offline procedure plus validation support suffices; no GUI/online manager. Online backup remains a future extension.

Restore requires stopped server, validated manifest/checksums, separate destination, identity/version/journal/checkpoint validation and preservation of the current world until explicit replacement approval. Restore world and economy from the same backup. Normal catch-up resumes from restored checkpoint; it does not recreate post-backup player actions.

Use UTC wall time for due intervals; monotonic runtime timing for budgets; durable economic sequence for completed updates. Each update has stable system/interval/version identity and atomically committed effects/completion. Systems may have different cadences. Process chronologically with deterministic same-time dependency ordering, bounded batches, persisted progress and batch/restart-independent results. No skipping effects for the 30-second target. Document any later equivalent aggregation in its domain phase.

Backward wall-clock changes never reverse/repeat economic time; wait until the persisted frontier is passed. Forward jumps become reported catch-up, not silent truncation. Persist seed and simulation versions; derive random inputs from stable update IDs. Version transitions have explicit effective boundaries and a policy for overdue historical intervals; implementation must make that policy explicit rather than arbitrarily selecting the current code version. Actual domain simulation algorithms remain later-phase work.

A5 must reconcile precommit reservations, postcommit undelivered items, delivery before persisted acknowledgment, and disagreeing Minecraft/database saves. A1 provides IDs, outcomes and recovery hooks only; no outbox-only or cross-save atomicity claim.

## Outcome and prerequisites

Implement and verify recoverable foundation state without gameplay systems. Require A1.2 storage/versioning, A1.3 committed record semantics, and explicit decisions on clock policy, offline behavior and simulation reproducibility.

## Deliverables to implement

- Separate storage-engine crash recovery, application journal replay and backup restore. Define what each guarantees and which failures remain outside its guarantee.
- Startup checks for world identity, supported envelope/schema versions, transaction completeness and projection checkpoints. Do not skip unknown committed record types silently.
- A minimal rebuildable fixture projection with version and last-applied transaction/sequence. Specify atomic progress, restart/resume and prevention of partially published rebuilds. Replaying never reissues commands or repeats external side effects.
- Immutable consistent snapshot/checkpoint contract. Add snapshots only if required by the agreed recovery model; avoid a second authoritative store or a premature full snapshot framework.
- Consistent backup creation and restore validation, including SQLite WAL handling. A filesystem copy of a live database file alone is not an approved backup procedure. Use approved complete-world offline backup/restore and separate restore destination. Backup retention automation is not approved; document operator ownership and future online extension.
- Persisted monotonic economic update sequence, seed and simulation version; update completion tied atomically to its durable outputs. Keep wall-clock audit time separate from game time and economic time.
- Explicit restart, sleep, /time, offline pause/catch-up and bounded catch-up semantics. Missing decisions block dependent behavior rather than receiving accidental defaults.
- A crash-boundary design for future cash/shops: list required intent, reservation, financial commit, delivery, save and reconciliation evidence. No claim that an outbox alone atomically joins Minecraft inventories with SQLite.

## Reimplementation coverage

F02/F03: rebuild recovery, projection and version compatibility contracts. F09: journal rebuild support only, financial history A4. F23: implement and run restart/interruption/restore fixtures. Cash/deposit/withdraw/shop features F11–F14 remain A5; autonomous FX/economics/market movement remain A2/A7/B3.

## Acceptance checks — implement and run

Rebuild fixture equals committed state; interruption during rebuild resumes without duplicate effects; corrupted/incompatible backup rejects safely; restore identifies correct world and last commit; clock update retry cannot complete twice; time manipulation/restart cannot reroll an already committed simulation result. Describe actual process-interruption checks separately from exception-injection checks.

## Exclusions and handoff

No inflation, interest, FX generation, stock simulation, physical-item recovery implementation or old-world migration without approval. Pass explicit clock/checkpoint/recovery contracts to A1.5 and dependent phases. List unresolved achievable item-delivery guarantees for A5.
