# A1.3 — Durable commits, operation identity and journal

Status: implemented and verified. Original package requirements below; do not repeat completed work automatically.
Prerequisite: A1-02. For explicitly requested work, apply the [A1 execution contract](CONTRACT.md).

## Approved A1-03 decisions

Request ID traces a submission attempt; operation ID identifies intent across retries; transaction ID identifies an atomic committed change; journal sequence orders committed transactions. Retry uses the same operation ID even if the request ID changes.

Journal envelopes include transaction/operation IDs, actor and originating service, sequence, UTC timestamp, operation type, payload format version, immutable ordered records and durable result. Each record has type/version/position. Economic update IDs are included when applicable. Trusted server services construct records; clients never submit arbitrary journal entries.

Atomically commit operation identity/canonical request, journal records, affected authoritative projections and durable result. Acknowledgment follows commit. Corrections append new referenced transactions; migrations preserve original meaning rather than silently editing journal facts.

Scope operation IDs to the financial world and bind actor/type. A versioned canonical request representation is persisted, not just its hash. Equivalent duplicate returns its original outcome; changed payload conflicts; unauthorized callers cannot expose outcomes. Retain committed identities for world lifetime initially; any archival must preserve deduplication. Retain terminal rejection identity/results for stable retries as well.

Outcomes: accepted/pending (no success promise), committed, durably rejected (no domain effect), not accepted (admission/configuration/availability/early validation failure), unknown (caller cannot establish outcome). Terminal business rejections during processing are durably recorded; a fresh intended attempt uses a new operation ID. Transient admission failures do not consume IDs. Pending queue entries need not survive restart; durable lookup resolves retry safety.

Uncertain commit: stop writes on affected connection, recover, look up original ID; return durable outcome if present, or process with the same ID only when definitively absent. Never automatically retry with a new ID. Rollback/cleanup failure disables unsafe connection use. Postcommit cache/callback/network failure cannot relabel a commit as rejected; invalidate affected caches and log delivery failure separately. Disconnect/cancelling a wait does not reverse commit. Replay never repeats item delivery/external effects.

## Outcome and prerequisites

Implement the durable recording mechanism later used by A4. Require A1.1 lifecycle, A1.2 identifiers/storage/migrations, and an approved journal-authority model. A1 provides atomic records; A4 adds balanced financial meaning.

## Deliverables to implement

- Versioned append-only transaction/record envelopes with transaction ID, operation/request identity, actor attribution, ordered sequence, audit timestamp, record type/version and immutable payload. Define transaction boundaries for multiple records and any same-store projections.
- Atomic durable commit and acknowledgment. Define accepted, pending, committed, rejected and unknown-outcome semantics, without confusing request acceptance with successful commit.
- Persistent operation lookup and duplicate protection. Same key and equivalent canonical payload returns the original committed result; same key/different payload rejects. Specify key scope, retention, canonicalization/versioning and retry behavior after restart.
- Append-only enforcement; corrections are new records. Migration procedures must not become an informal route to rewrite financial history.
- Connection/commit/rollback failure handling. A postcommit cache/callback failure must not report the durable transaction as uncommitted. Unknown outcomes are resolved through durable operation lookup, never blind resubmission with a new key.
- Publish immutable results and invalidate derived caches only after commit. Define boundaries for cache refresh failures and unavailable storage.
- Minimal generic fixture operations/projections sufficient to demonstrate transaction behavior without introducing money movement.

## Reimplementation coverage

F02: rebuild transaction/postcommit guarantees. F09 infrastructure: rebuild append-only audit/sequence/correlation support; complete double-entry, posting authorization and financial history remain A4. F23: implement and run atomicity, duplicate and interruption fixtures. Existing generated transaction UUIDs alone are not durable caller idempotency.

## Acceptance checks — implement and run

A multi-record fixture commits entirely or not at all; concurrent same-key submissions produce one effect; changed-payload retry rejects; acknowledged commit survives restart; lost acknowledgment resolves to the original result; rollback/cleanup failures fail closed; cache publication failure never causes duplicate durable effects.

## Exclusions and handoff

No chart of accounts, debit/credit posting validation, balances, transfers, FX or financial holds. Record their A4 ownership. Deliver durable envelope/operation contracts, versioning, sequence rules and uncertain-outcome behavior to A1.4 and A4. Cash and shop item operations require A5 protocols beyond database idempotency.
