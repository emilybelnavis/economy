# Accepted credit foundation and lifecycle handoff — A6-08-03

## Acceptance boundary and production admission

The A6-08 foundation is accepted for its captured contracts, accounting, scheduler, diagnostics and detached recovery fixtures. It does not yet enable unrestricted production borrowing. `LedgerService.issueCreditFacility`, `increaseCreditFacility` and `drawCredit` wrap their registrations with `CreditSafety.production`. The wrapper preserves canonical version and returns transient CREDIT_LIFECYCLE_UNAVAILABLE inside handler preparation. It installs no deadline provider and cannot block empty-world readiness. There is no mutable enable switch, operator override or client capability. A6-10 must replace this dormant admission only after its required lifecycle is installed and accepted.

A1 resolves an identical original committed operation before invoking the handler, so the new gate cannot turn a stored successful issuance/draw into a new financial operation. Current owner/session/canonical identity protections still apply. Production account/facility reads and diagnostics, same-currency/FX repayment, closure, replacement/revocation and existing outcome lookup do not enter this new-exposure gate. Unknown commits retain original-ID recovery; reconnect never authorizes a second facility or custody delivery.

Core `CreditFacilities` registrations are trusted worker/domain primitives used by detached fixtures and future validated runtime composition. Live integrations must use LedgerService with current owner and branch/physical admission; core test callbacks are not a player API or production activation flag. Existing generic collectibles still have no issued token/facility authority.

Credit canonical request records include their captured world and owner. The shared PartyOperation admission also binds the owner. `LedgerService.unboundCreditRequest` verifies the supplied world/owner matches the grant, removes only that already matching owner field, then lets PartyOperation restore it. This fixes the previously rejected double binding without changing final canonical bytes, financial handler comparison, operation identity, amounts or historical decoders. No foreign owner field is silently replaced.

## Immutable downstream event source

`CreditEvents` is a trusted worker-only read API over existing immutable journal facts. It is not a player endpoint, packet, new financial database, event delivery side effect or installed reporting/default provider. The supported v1 source families are:

| Payload | Existing source | Downstream meaning |
| --- | --- | --- |
| FacilityChanged | economy:a6/credit-facility | Saved contract/status/access/buckets and remaining original minimum requirements after an operation |
| StatementClosed | economy:a6/credit-statement | Original statement/obligation identity, close/due instants, frozen debt/principal/period interest and original new minimum |
| InterestPosted | economy:a6/credit-daily-witness | Captured v1 before/after bucket inputs, effective instant, earned/posted amounts and residual |
| PaymentReceived | economy:a6/credit-payment-allocation | Effective payment, principal/interest allocation, signed payoff residual and statement-attributed financial allocations |

Each envelope retains journal sequence/position, world, original transaction/operation UUID, audit UTC, economic-update identity and correction link. Snapshot/witness world must match the immutable envelope. Unknown versions or invalid historical inputs fail closed. Validated detached facility/statement decoders are exported; consumers never recalculate a historical statement from current rates or mutable balances. Token facts and raw legacy draw references are excluded.

`open(reads)` captures the current world and immutable journal frontier. `read(reads,cursor,limit)` permits 1–16 entries plus one lookahead with an exclusive (sequence,position) cursor. Returned checkpoint/nextCursor retain that fixed frontier; later commits cannot enter an in-progress fold. `open(reads,completedCheckpoint)` starts a subsequent frontier without rewinding sequence. Wrong world or a frontier beyond the restored database fails rather than silently skipping history. Consumers must persist their own progress atomically with derived effects and use deterministic original obligation/event identities; a cursor is not permission to create money.

A statement and its facility after-snapshot describe one obligation, not two debts. Financial payment allocations are not contractual minimum-satisfaction allocations. Future due/default/reporting consumers derive remaining original requirements from ordered facility after-snapshots and retain original statement due/amount; absence after satisfaction does not erase the original frozen obligation. Legacy snapshots/statements remain readable, while detailed daily/payment witnesses only exist prospectively from V26. Unsupported old detail must remain explicit rather than invented.

V27 adds only `credit_event_feed`, a partial (sequence,position) index with the exact supported type predicate. The source query uses that predicate and an indexed journal-envelope join, bounded to at most 17 rows. Real SQLite EXPLAIN verifies index selection. Main/offline schema validators and complete-world backup/restore accept V27; V1–V26 migrations remain unchanged. No new persisted entity, journal fact family/version, balance column, scheduling provider or physical effect is added by the source reader.

## Accepted behavior and remaining lifecycle dependency

Acceptance uses the approved card cutoff rule: only complete anchor days charge interest; intra-day balance changes affect the next cutoff, not elapsed loan-style segments. Separate APR/residual buckets compound exactly and settle minor units once. Frozen statement balances/period interest and minimum metadata stay distinct from current debt and satisfaction. Tests cover due/statement overlap, several offline periods, partial/full payoff, bounded requirements, JPY zero-posted earned interest and checked overflow.

Actual fixture JVM termination before/after commit covers draw, daily interest, statement and payment. In addition, injected uncertainty after a real commit exercises recovery lookup. These are separate evidence categories; no player's game is killed. Original outcomes, receipt counts, liability/receivable/reservation/customer balances and journal reconciliation remain stable after restart/replay. New event pages and the production gate add no replayed physical delivery.

A6-10's due+2 reports, due+84 default, access freeze, reservation release and collection clocks are not installed here. A6-08-01's outstanding 16-requirement/34-slice limits therefore remain a foundation bound, not permission for indefinitely unpaid live portfolios. New production exposure stays unavailable until those required safety dependencies are accepted. Existing debt/history is preserved and protected operations stay available under ordinary chronological guards. Reporting/recurring score activation remains A6-10-03; A6-09-01 may only start on explicit invocation.

## A6-10-01 current integration

Shared obligation/report/default providers are now installed. Default closes spending and releases undrawn reservation, while protected collection repayment follows the current creditor. Original due/default/report anchors and historical debt survive expiry. Production borrowing and score activation proceed through A6-10-03 acceptance. See [lifecycle contracts](debt-lifecycle.md).
