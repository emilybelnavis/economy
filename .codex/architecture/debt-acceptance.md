# Accepted delinquency and reporting foundation

A6-10-03 accepts the shared card/loan lifecycle for current and provenance-complete histories. The registered production dependency now includes original obligations, due+2 reports, independent collection clocks and a stage6 credit-score provider. Minecraft1.21.1, NeoForge21.1.252 and Java21 retain their pins.

## Startup and historical compatibility

Startup performs bounded legacy debt reconciliation, ordinary debt bootstrap, score-source reconstruction, historical score folds, projection validation and chronological calendar catch-up before publishing readiness. Each reconciliation turn commits one source snapshot, original report boundary, facility check or score fold step with a stable operation identity. Global calendar history is preserved. Historical score installation processes boundaries through the existing calendar frontier under its own migration operations before the new recurring provider joins ordinary dispatch.

The legacy reconciler reconstructs original obligation and report snapshots, including removed paid requirements and later cure receipts. Tracked facilities are verified against their immutable source history. It never changes core loan/card snapshots, money or the global clock. An unpaid historical default that would require changing already committed interest/borrowing fails with `DEBT_LEGACY_DEFAULT_REQUIRES_ACCOUNTING_RECONCILIATION`. Missing historical obligations on a tracked facility fail with `DEBT_TRACKED_HISTORY_REQUIRES_RECONCILIATION`. Missing or conflicting source history has separate precise migration reasons. Metadata reconstruction can have committed earlier pages before a later unsupported history rejects. Financial readiness remains closed, and original money/history is preserved. Those histories require a separately defined corrective accounting or provenance repair process.

## Score source and daily utilization

The installed reporting observer captures every authoritative revolving facility snapshot in the original transaction after lifecycle observation. Live capture begins only after the reporting bootstrap completes. This preserves detached foundation fixtures while production admission requires complete reporting. Source bootstrap replays original immutable facility facts one at a time, verifies contiguous facility revisions, nondecreasing effective time and current core/source agreement, and then seeds each profile's next world-aligned28-day head.

Sources retain owner/world/facility/revision, currency, contractual limit, active status, last accrual instant, posted purchase/advance balances, signed residuals and captured APRs. Event time comes from the same transaction's credit, statement or collection receipt. A missing timestamp or source never becomes zero utilization.

The preceding28-day cycle is integrated exactly as rational owed/limit area. Intraday balance changes split the interval. Accrued interest follows captured daily balance/APR segments, and days split at world-day and UTC FX-publication boundaries. Different currencies use the immutable FX publication for that UTC date in one common valuation unit. An all-same-currency portfolio uses its native common unit. Closed/defaulted facilities leave the active denominator. Empty active periods have zero active duration, while active zero debt has the policy's exact0% utilization. Missing daily coverage or historical FX fails closed. At most three elapsed-time active revolving facilities,8192-bit rational precision and16,384-byte canonical task records bound each calculation.

## Durable score dependency

`economy:a6/credit-score` v1 runs at stage6 after stage5 reports and before stage7 Tier work. Each target is an owner plus a monotonically increasing fold-step cursor. One turn processes a source/interval or at most16 report, case or inquiry rows. Daily area receipts, partial summary and next source cursor persist in the same journal commit as calendar progress. A completed task retains the prior portfolio cursor for the next cycle. Zero-duration same-instant source ordering can temporarily retain up to seven old/new/transient facilities, while elapsed-time intervals enforce the three-active-facility cap.

Report folding selects the latest effective factual snapshot per original obligation and retains automatic cure receipts as history. Visible default anchors suppress that facility's payment points. Default age/expiry remain original through sale, acknowledgement and settlement. Inquiry groups use their original world-aligned seven-day buckets and strict84-day window, including valid applications at the world origin. Each cycle recomputes from650 and appends the existing profile/history facts atomically. No earlier score deduction is applied as a delta.

`CreditSafety.production` admits new-exposure handlers only when debt/legacy/reporting bootstrap witnesses and all six debt/score providers are installed, no calendar child is APPLYING and no score head is overdue at the completed frontier. Existing service readiness also blocks financial writes during catch-up. Original outcomes replay before current admission. Protected repayment, history and separate audited operator authority retain their contracts. Profile/diagnostic views now report installed/catch-up status and the owner's actual next score head. Provider cursors expose only THIS_SELECTION/OTHER_TARGET.

## V33 persistence and records

V33 adds six tables and five indexes. `debt_legacy_reconciliation` stores its per-facility replay/verification cursor. `credit_reporting_sources` stores immutable minimal utilization snapshots. `credit_reporting_bootstrap` stores source/facility/profile phases. `credit_score_heads` stores next owner boundaries. `credit_score_tasks` stores canonical resumable folds. `credit_utilization_days` stores exact daily area receipts. Indexes support pending legacy reports, owner/time utilization, score deadlines, original facility source replay, grouped inquiries. Existing owner-case indexes support the default fold. Existing V31/V32 tables and immutable fact encodings retain their versions.

New v1 journal families are `economy:a6/debt-legacy-checkpoint`, `economy:a6/score-utilization-source`, `economy:a6/score-source-bootstrap`, `economy:a6/score-fold` and `economy:a6/score-utilization-day`. Existing credit profile/history v1 facts store the result and component/source fingerprint. Online/reopen, offline inspection, backup compatibility and the core record catalog cover V33.

No new network message, physical item or entity effect occurs. All changed effects are worker-owned database/journal commits. Existing command replies remain private and session checked.

## A6-11 integration

Use owner-bound `LedgerService.debtCases` or `collectionCases`/`collectionCase` for bounded candidate views. `DebtLifecycle.demands(case,now)` requires collectible, unsettled debt within the acknowledgement/expiry interval. Use `reportVisible`/`bureauStatus` separately for bureau state. Presentation cannot acknowledge, settle or revive a case.

Native payment uses `quoteCollectionPayment` and `repayCollection`. Explicit FX uses `quoteCollectionPaymentFx` and `repayCollectionFx`. Fresh owned BANK funding, case/funding/ledger/provider/FX revisions, exact expiry and original-operation retry checks remain authoritative. Fees are separate from debt satisfaction. A successful positive partial payment before expiry alone moves acknowledgement. Full payment settles, while refused/failed/zero/stale actions leave the clocks unchanged. Agent gameplay and dialogue belong to A6-11.

A6-11 now consumes these APIs through [Collection Agent presentation and private payment dialogue](collection-agent.md). Durable presentation leases and cooldowns remain independent of the financial case/report clocks.
