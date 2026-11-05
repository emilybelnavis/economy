# World storage, identifiers and migrations

## Storage ownership and API

Production startup supplies FoundationRuntime to ServerServices; it owns SqliteStorage on the storage worker. One storage worker owns its
connection from initialize through close; all filesystem, JDBC and migration work stays
there. No extra read connections. ServerServices supplies the existing bounded 256-task
storage queue, 64-task calculation queue, 320 outstanding result cap and 1ms callback-start
budget. Admission errors now carry StorageException.Kind.OVERLOAD. Fatal persistence errors
transition to UNAVAILABLE on the server thread; accepted failure completions still drain.
Initialization failure rejects readiness. Driver failure is logged on client loading and
propagated through server readiness rather than replacing a store or stopping vanilla play.

<world>/economy/owner.lock is an OS FileLock, held until the connection is actually closed,
including after a drain timeout. Never unlink lock files: file existence is not ownership.
Do not use network/shared multi-server storage. Symlinked storage paths are rejected.
The filesystem must support file forcing, atomic rename and directory synchronization;
unsupported providers fail safely. macOS is verified, other platforms are not runtime-tested.

FoundationStore.transaction takes a trusted worker-only callback with no exposed JDBC
handle. Call it through services.storage(() -> store.transaction(...)). Inputs/results must
be detached immutable values. Never join on ticks. Transaction methods support one data
statement at a time (SELECT/INSERT/UPDATE/DELETE/WITH), at most 64KiB SQL, 256 parameters,
1MiB total UTF-8 parameter bytes, and immutable results capped at 1024 rows/1MiB values.
SQL and callbacks must never be accepted from clients. Escaped transaction objects reject
use after the callback returns. DDL belongs to the migration registry, not this interface.
Commit success returns the callback result; failure rolls back without retries. Rollback
failure poisons the connection and never enables autocommit (which could commit partial work).
Financial writes use [durable operation identity and uncertain commit resolution](journal.md).

## Identity publication protocol

1. Acquire ownership, then inspect database, world-identity and identity.pending without
   substituting absent/corrupt data. A completed identity without its database is a hard error.
2. For truly fresh storage, generate a UUID and durably publish identity.pending first.
   Files are written to a unique temporary sibling, forced, atomically renamed, then their
   directory forced. No promise of atomicity across SQLite and external files is made.
3. Create the entire foundation schema and identity/history in one SQLite transaction.
4. Publish world-identity using the same UUID; only then delete the intent and force its
   directory. Interrupted final publication can resume from matching committed DB + intent.

An intent without a DB, or with an empty uninitialized DB, can finish initial creation.
A committed DB + matching intent can finish external metadata publication. Missing both
world markers for an existing DB, conflicting markers, mismatched UUIDs, unsupported
metadata formats and corrupt data are unavailable states, never permission to create anew.
Temporary partial files are not trusted as identity. A full world copy retains valid
matching identity and may run independently; UUIDs do not prohibit cloning.

## SQLite and schema

Driver org.xerial:sqlite-jdbc:3.53.4.0 is pinned, jar-in-jar bundled with native binaries
and upstream license files, and explicitly in additionalRuntimeClasspath for development.
Effective pragmas are verified: WAL, synchronous=2 (FULL), foreign_keys=1, busy_timeout=5000ms.
No automatic busy retry is performed beyond SQLite's finite busy timeout.

application_id=0x45434F4E identifies redesigned storage. Current schema=19; minimum=1. V3 adds the [journal](journal.md); V4 adds [recovery and clock metadata](recovery-clock.md). V5 adds the [FX journal indexes and activation-scoped observation](fx.md). V6 adds the [account catalog](accounts.md). V7 adds the [ledger balance projection](postings.md). V8 adds [holds](postings.md#holds). V9 adds [quote consumption and the denomination index](postings.md#fx-closure-and-rebase). V10 adds the [posting history index](postings.md#service-and-history).
V11–V17 add merchant payments, custody, cash issuance, terminal policy, deposits/returns, withdrawals and register sales. V18 adds the [financial calendar](financial-calendar.md) and paged domain checkpoints, retaining prior IDs, balances and history.
V1 creates only foundation_metadata (world UUID, product, schema version) and migration_history.
V2 adds explicit identity_protocol=1. user_version must match metadata and contiguous
recognized migration history. Newer/older unsupported versions and legacy/foreign databases
reject. Startup also performs SQLite quick_check. Only foundation tables are created.

SchemaMigrations validates ordered contiguous registry versions and applies all pending
steps, history entries, metadata version and user_version in ONE transaction. DDL failure
rolls back the whole chain to the prior supported schema. A rollback failure closes the
connection; startup reports failure and does not keep executing. On a later explicit
restart, inspect the actual committed metadata/history, not an assumed last attempted step.
No implementation can promise recovery from arbitrary filesystem/hardware failure; ambiguous
commit failures are not blindly retried. SQLite atomicity protects old-or-new schema state.

## Offline pre-upgrade backup gate

Before upgrading a supported older schema, stop Minecraft completely and run:

    ./gradlew prepareEconomyUpgrade -PeconomyWorld=/absolute/path/to/world

UpgradeBackup acquires Minecraft session.lock AND economy owner.lock. It copies the entire
world to a unique sibling <world-name>-economy-upgrade-backups/<uuid>/world, preserving the
original files. Runtime lock files, SQLite shared-memory cache and prior backup receipt are
excluded; SQLite DB and WAL, identity markers and all world/player/region files are included.
Symlinks and special files reject. Copied files and directories are forced, all file hashes
and inventory validated, and a receipt is atomically published LAST in the source economy
directory. A crash during backup leaves no usable new receipt; no incomplete backup can gate
an upgrade. Existing receipts can be replaced only by another completed offline backup.

Startup does NOT copy a world. It checks the saved receipt, every backup file hash and the
DB/WAL/identity fingerprint captured before opening JDBC. Missing, stale or tampered backups
prevent migration and keep the economy unavailable. The snapshot is a complete stopped-state
world backup, not a claim that mutable vanilla world files remain identical after startup.
Identity publication after an interrupted initial creation needs no upgrade backup because
the schema already matches current. No online backup, automatic restore, or backup deletion.
General offline backup and restore have separate [clean-world requirements](recovery-clock.md).

## Identifier conventions

Identifiers records prevent UUID entity/actor type confusion. Players retain Minecraft
UUIDs. Organization/system actor types are distinct. Currency codes are uppercase 3–12
ASCII letters/digits beginning with a letter. Catalog keys are stable lowercase namespaced
keys (128 characters max), with company/security/exchange/listing kinds, independent of names.
Payment/loan/order types are conventions only. JournalSequence is positive, database-assigned
by the journal; EconomicSequence is nonnegative and independent; AuditInstant is UTC Instant.

## CHF history compatibility

V19 replaces only the posting-history index predicate with record_version IN (1,2), enabling [CHF minor-unit posting history](chf.md). V10 remains frozen for old schema/backup validation. Existing schemas1–18 require the normal stopped-world backup gate; no balance/account/cash table is changed. Strict v1 and v2 record decoders coexist. Supported clean backup manifests include19.


## A6-03 current contract (2026-11-05)

[Institution/provider contracts](institutions.md) supersede earlier lifecycle/cap references: schema V20, four total open BANK accounts including primary, version3 institution postings, explicit legacy provider migration, and sealed branch-only lifecycle admission. Live opening/closing/provider changes are unavailable until A6-05-01. Existing account services and original outcome lookup remain.


## A6-04 current contract (2026-11-05)

[Wealth, pricing and funded yield](wealth-pricing.md) defines schema V21, persisted required-source contracts, fresh qualification/fee witnesses, assigned-bank FX pricing and time-weighted bank-funded deposit yield. Production installs bounded accrual/payout providers at stage 1 and daily Tier reevaluation at stage 7; empty domains remain dormant. Source snapshots and unpaid receivables remain distinct from spendable funds. Live branch adapters, issued cards, lending and trading are still separate packages. Earlier no-product/default-Tier2-denial descriptions apply to their historical delivery checkpoints.

## A6-05-01 schema V22

Adds `terminal_bank_bindings` and preserves legacy terminal settings as explicit unbound metadata. [Exact binding/fact/migration contract](branch-hardware.md). Open/recovery/offline validation and complete-world restore recognize V22; original history and backup-before-upgrade rules remain.

## A6-06 schema V23

Adds `issued_cards` and one-use `issued_card_fee_quotes`; [facts/encoding and custody](issued-cards.md). Old IDs, journals and settings remain intact. V23 participates in open/recovery/offline schema validation and complete-world manifest restore under the existing backup-before-upgrade gate.

## A6-07 schema V24

Adds four unseeded credit profile/history/inquiry tables and three indexes; [exact fact/first-issuance contract](credit-profiles.md). V23 upgrade preserves existing accounts, tokens, settings, capital and journal history. Open/recovery/offline schema and complete-world manifests recognize V24 under the existing backup gate.

## A6-08-01 extension

V25 adds funded credit facilities, separate tokens and immutable statements with five lookup/uniqueness indexes. Main/offline schema checks and complete-world backup/restore accept V25; historical migrations remain unchanged. See [credit persistence](credit-facilities.md).

## A6-08-02 diagnostics

V26 appends four partial/expression indexes for bounded credit diagnostics; no table or monetary schema changes. New daily witness and payment allocation facts are registered v1 records; old financial facts are preserved. See [diagnostic persistence](credit-diagnostics.md).

## A6-08-03 acceptance

V27 appends one partial credit_event_feed index for ordered supported credit facts; no new monetary entity/table or fact family is added. Main/offline validation and backup/restore accept V27. Existing v1 facts and V1–V26 migrations are unchanged. See [immutable lifecycle source](credit-acceptance.md).
