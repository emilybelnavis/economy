# A1.2 — Identifiers, storage schema and migrations

Status: implemented and verified. Original package requirements below; do not repeat completed work automatically.
Prerequisite: A1-01. For explicitly requested work, apply the [A1 execution contract](CONTRACT.md).

## Approved A1-02 decisions

- Financial world: generated UUID persisted in SQLite and world-side foundation metadata. Mismatch makes the economy unavailable. Complete world copies remain valid independent copies; the check does not prohibit cloning.
- Players use Minecraft UUIDs. Organizations/system actors have typed identities distinct from players. Accounts, transactions, operations, payments, loans and orders use UUIDs with distinct types. Currency uses stable uppercase codes. Company/security/exchange/listing catalog IDs are stable and independent of display names. Journal sequences are database-assigned, increasing and separate from UTC audit instants and economic sequences.
- Fresh initialization creates only foundation metadata and schema/migration structures. Recognized old economy databases reject with an explanation; no import, overwrite or deletion. Corrupt, foreign-identity, unsupported or unreadable databases never become empty replacement stores.
- One worker owns all connection use and filesystem/database initialization; immutable requests/results, bounded queue, server callbacks, no tick-thread JDBC/waits. Separate read connections require later evidence and a deliberate change.
- SQLite WAL, FULL synchronization, foreign keys and a finite 5-second lock timeout. Verify effective settings. Bundle/pin a suitable JDBC driver during implementation and verify client/server development runtime loading. Reject a second process attempting to own the same financial world; SQLite's brief write locks alone are not the ownership contract.
- Explicit current/minimum supported schema versions, ordered migrations and migration history. Commit the pending migration chain and version changes in one transaction; failure returns to the prior supported schema. Any future exception to this policy requires an explicit recovery design. Reject newer unsupported schemas.
- Before upgrading an existing redesigned database, create/validate the approved complete-world stopped-state backup. World-side initialization/identity writes and pre-migration backup ordering need a recoverable protocol because they are outside SQLite's transaction; do not claim cross-file atomicity. Future implementation must specify how backup prerequisites are satisfied before startup migrations, without silently copying a running world.
- Typed errors distinguish configuration/driver, identity, schema, corruption/I/O, lock timeout, overload and migration failure. Admission failure is explicit. A1-03 owns uncertain commit outcome resolution; never blind-retry writes here.

## Outcome and prerequisites

Implement world-isolated, explicitly versioned storage behind the lifecycle from A1.1. Use approved SQLite and fresh-data/no-legacy-import policy. Preserve old files untouched and support future redesigned schema upgrades.

## Deliverables to implement

- Stable identifiers for financial world, actor/owner, account, currency, transaction, operation and request. Define future payment/loan/security/listing/order ID conventions without implementing their entities. Distinguish identifiers, journal sequence and audit timestamps.
- Persistent world identity and compatibility checks. Locate financial storage under the selected world, never in a global shared store.
- A storage abstraction with one explicitly owned executor/connection strategy, immutable inputs/results and no JDBC or waits on the tick thread. Bound the queue immediately; expose its rejection contract for A1.5.
- Explicit schema version, supported-version range and ordered migration registry. Define transaction boundaries, failure rollback, partial-upgrade recovery and newer-schema rejection.
- For approved SQLite: driver bundling and development runtime wiring, WAL/FULL/foreign-key configuration and verification, busy/lock behavior, connection ownership and filesystem initialization on the worker. Explain any alternative settings.
- Typed persistence errors and readiness propagation. Corruption, wrong-world identity and unsupported schema must never be treated as an empty valid database.
- Only foundation metadata/schema tables now. Define the interface needed by A1.3's journal rather than prematurely creating banking/market schemas.

## Reimplementation coverage

F02: rebuild world storage, worker ownership and configuration. F03: rebuild explicit version/migration capability. The old SavedData importer remains in the inventory but is explicitly excluded by the approved fresh-data policy. The historical A1-01 reset is complete; normal storage initialization must never delete worlds or claim legacy migration support. F23: implement and run disposable storage/migration fixtures.

## Acceptance checks — implement and run

Fresh schema initialization; ordered upgrade from supported fixture versions; failed upgrade leaves a recoverable supported state; future schema rejected; wrong-world data rejected; no cross-world connection reuse; missing driver and locked/unwritable/corrupt storage surface safe failures; runtime driver loads in both development server and client.

## Exclusions and handoff

No accounts/balances, currency definitions, financial journal postings or legacy import implementation without approved scope. Deliver storage lifecycle, migration protocol, identifier conventions and atomic transaction interface to A1.3. Explain whether fresh initialization and migrations are themselves atomic and recoverable.
