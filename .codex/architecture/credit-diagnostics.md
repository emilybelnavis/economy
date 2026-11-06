# Credit diagnostics — A6-08-02

## Owner reads and display

`CreditDiagnostics.ownerPage` returns immutable facility views plus an exclusive UUID cursor, sampled UTC and journal frontier, at most 16 entries. `facility` checks world/owner before returning data. Views contain no token/custody identities. They distinguish contractual limit/headroom (not guaranteed approval), posted principal, posted interest, exact balance including signed residual, current posted balance, newly earned period interest, current outstanding minimum and strictly past-due minimum. The oldest unmet due date is separate from the latest frozen statement balance/date and upcoming accrual/statement deadlines. Closed access reports zero headroom. Backlog labels identify saved snapshots rather than simulating current debt in a read.

`LedgerService.creditFacilityPage/creditFacilityView` require fresh owner grants; existing `creditStatements` supplies frozen statement pages. Corrupted statement bodies must match selected world/facility/owner and indexed statement identity/times before display. Contract currency is fixed and shown explicitly. A statement retains original balance/principal/earned interest/new minimum/total required at close; later repayments and current APRs cannot rewrite it. Statement version is the historical v1 supported by A6-08-01.

Banking protocol 6 adds only credit_facilities, credit_facilities_next, credit_facility, credit_statements and credit_statements_next. ATM/teller/kiosk/card owner surfaces permit these reads; register/admin/world-setup surfaces do not. Each facility ID comes from server-offered choices; per-menu cursors are bounded. Lists contain at most eight facilities; statement screens at most two snapshots. Screens preserve current audience/menu revision and safe pending navigation. `CreditReport` emits bounded text with no private token, raw journal payload or financial mutation controls.

## Audited operator inspection

`LedgerService.inspectCreditFacility` requires a separate operator inspection grant, scoped owner/facility/cursor/limit and live audience callback. Every page is a durable existing InspectionAudit operation before any private query, with live checks before and after asynchronous work. Owner grants cannot invoke operator mode; inspection grants cannot invoke owner reads.

`ServerServices.inspectAccountsDuringCatchup` is a narrow audit-only path. It constructs the sealed `PartyOperation.inspection`, whose only handler emits InspectionAudit; it does not accept arbitrary handlers or money postings. Audit persistence uses the ordinary owned storage worker and original-operation recovery without the financial-current gate. Normal settlement stays on `economicWrite` and remains rejected during catch-up. Startup/unavailable state is not bypassed. This allows a ready server with pending scheduled finance to diagnose backlog without unaudited private reads or admitting a backdated payment.

Player-backed command syntax:

- `/economy credit facility <facility-uuid>` — owner saved view.
- `/economy credit inspect <owner-uuid> <facility-uuid>` — first daily witness page.
- Append `daily`, `payments` or `events`; append `after <sequence> <position>` for the section's exclusive cursor. The start is `after 0 -1`.
- Payments use one receipt per page. A final allocation_offset argument pages at most 16 allocations within that same receipt. Reuse the same receipt cursor for another allocation slice; only use the reported next receipt cursor when finished.

Console, RCON, unauthenticated automation and `/execute as` sources cannot impersonate the player-backed operator identity. There is no edit/set/reset/skip command. Errors are generic and output is private, not broadcast.

Operator views contain a sampled journal frontier, facility revision, bounded immutable daily/payment/legacy event pages, next due times, arithmetic counts of overdue anchor days/statements and the two provider heads. Global applying cursor values are reduced to THIS_FACILITY / OTHER_TARGET / empty so another owner's facility UUID is not leaked. The counts describe this facility's saved schedule, not a promised processing ETA or unbounded global obligation count.

## Immutable servicing evidence

`CreditAuditFacts.DAILY = economy:a6/credit-daily-witness` v1 is 13 pipe-separated fields: facility, world, effective UTC, currency, policy version, before purchase/advance buckets, after purchase/advance buckets, earned purchase/advance major amounts and posted purchase/advance minor amounts. Each bucket captures kind/principal/posted interest/signed 18-place residual/period-earned amount/APR. Validation reproduces the v1 daily formula from those captured inputs and checks exact outputs. It does not read the current catalog's rate. A zero-posted day still has its exact receipt.

`CreditAuditFacts.PAYMENT = economy:a6/credit-payment-allocation` v1 is ten fields: facility/world/effective/currency/policy, received amount, principal/interest portions, signed payoff residual disposition and base64url allocation lines. Each line retains statement UUID or unbilled marker, bucket, interest/principal flag and minor amount. Registered validation bounds fields, requires positive allocations and checks every allocation total against the received and principal/interest totals. New receipts are appended in the same original financial transaction/outcome; they cannot independently post money or cause a second repayment. FX fees are separate and never satisfy a minimum or debt.

Existing economy:a6/credit-event v1 remains supported and unchanged. DRAW event diagnostics strip token and destination references. Old daily/payment events explicitly say LEGACY_INPUTS_UNAVAILABLE / LEGACY_ALLOCATION_UNAVAILABLE; no reconstruction from today's balances or invented allocation is offered. New detailed witnesses begin prospectively after this package. Existing statements retain v1 snapshots; no historical financial facts are migrated or recalculated.

## Bounded reconciliation and schema

Operator reconciliation independently compares positive outstanding facility debt with typed borrower LIABILITY / issuer RECEIVABLE balances, then folds immutable posting facts at the captured journal frontier. Each owner/issuer portfolio fold and journal fold reads at most 129 rows, processes 128 and exposes explicit completeness. BigInteger accumulates journal amounts; checked long arithmetic protects facility totals. A truncated fold never reports a match. These are bounded diagnostic windows, not an unbounded SQL SUM disguised as one returned row. Large histories report incomplete instead of asserting false reconciliation.

V26 appends only four indexes: credit_diagnostic_records on legacy base64url facility identity / sequence / position; credit_audit_records on new plain facility prefix / sequence / position; and positive-debt partial owner/issuer indexes. Queries include the exact partial predicates and exclusive (sequence,position) cursors; real SQLite query plans are verified. No table, balance column, limit editor, second database, scheduler/provider or physical delivery protocol is introduced. Main/open/offline schema validation and complete-world backup accept V26. Historical migrations and v1 debit/credit decoders remain preserved.

Default/reporting/collections/autopay and recurring score activation remain later packages. Diagnostics expose A6-08-01's bounded servicing state and backlog; they do not claim that the A6-10 lifecycle is installed or remove its long-lived unpaid-portfolio limitation.

## A6-08-03 acceptance

A6-08-03 accepts the foundation and adds a separate trusted CreditEvents source at an immutable frontier. Existing diagnostic pages retain their bounded reconciliation rules; new production exposure stays unavailable pending lifecycle safety. See [acceptance](credit-acceptance.md).

## A6-09-01 shared receivable reconciliation

Issuer reconciliation includes positive loan debt alongside credit facilities under the existing bounded processed-row budget, with explicit incomplete results on overflow. Borrower credit reconciliation remains credit-only because loan debt uses a separate liability party. This adds no loan diagnostic commands; A6-09-02 owns those. See [loan persistence and reconciliation](loans.md).

## A6-10-01 ownership integration

Original-issuer reconciliation excludes sold card and loan claims from its receivable portfolio. Defaulted owner views show stopped accrual. Lifecycle cases carry the current creditor and independent report/collectability fields. A6-10-02 owns those diagnostic surfaces. See [shared debt contracts](debt-lifecycle.md).
