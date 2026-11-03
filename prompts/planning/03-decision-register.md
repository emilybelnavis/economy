# Decision register — approved foundation and open later decisions

Foundation approvals below were given explicitly in chat on 2026-11-03. Approval is design-only, not implementation or test evidence. Later rows remain proposals unless explicitly marked approved. Do not infer gameplay approval from foundation architecture.

| ID | Decision | Proposed starting direction | Needed before |
| --- | --- | --- | --- |
| D01 | APPROVED: fresh financial data | No legacy importer. Historical approval covered the now-completed A1-01 one-time root reset except .git/prompts/AGENTS.md on the new branch; later runtime initialization preserves databases. Future redesigned schemas still migrate. Reason: ground-up redesign. | A1 |
| D02 | APPROVED: SQLite/journal authority | One server owns each world; <world>/economy/economy.sqlite; WAL/FULL/FK; one worker/connection; explicit transactional migration chain and pre-upgrade offline backup; journal/projection/operation result atomicity. Reason: world-local deployment and recoverable writes. | A1 |
| D03 | APPROVED boundary; domain details later | Accounting owns money, custody shares/return obligations; trading uses shared atomic coordination. Posting/settlement implementations remain A4/B2/B4. | A1/B2 |
| D04 | APPROVED: real-world elapsed time | Process all offline time gradually; gate affected actions; persisted chronological/versioned deterministic updates; sleep/time commands irrelevant; no backward repeat or forward truncation. Domain accrual/cadence rules later. | A1/A6/A7 |
| D05 | APPROVED initial limits/target; full benchmark later | Maximum expected 15 players; target seven offline days in 30 seconds at eventual 800 listings. One storage worker, two CPU workers, 256/64 pending tasks, 1 ms callback-start budget/tick, 5 s lock timeout, 30 s drain target. Size caps/retention/hardware benchmark details remain to specify. | A1/B3 |
| D06 | Account structure and limits | Separate identity/account/currency balance; preserve three-account/primary workflow unless explicit replacement approved | A3 |
| D07 | Organization authorization deferred | [Registration, grants, revocation and integration authority](deferred-organization-identity.md). A3 retains typed owner references, player and system authorization, and audit actors. | Integration phase TBD |
| D08 | World currency and rebase semantics | Default/reporting currency separate from explicit redenomination; preserve chooser and confirmed admin capability | A2/A3/A4 |
| D09 | FX rounding, spread, fees and quote expiry | Explicit per-operation rules, versioned quotes, clearing/fee/residual postings | A2/A4 |
| D10 | Cash issuance/supply and acquisition | Track issuance/redemption and creative/admin minting; choose survival acquisition and whether terminals hold finite cash | A5/A7 |
| D11 | Cards and banking access | Preserve eight card identities and no automatic borrowing; decide generic versus linked identity/access | A5 |
| D12 | APPROVED foundation boundary; A5 protocol OPEN | A1 durable identities/outcomes and recovery hooks; A5 must reconcile reservation, commit, delivery and save disagreement. No outbox-only atomicity claim. Complete-world offline backups do not substitute for live item recovery. | A1/A5 |
| D13 | Loan and collateral gameplay | Eligibility, lenders, fixed/variable rates, repayment timing, default consequences and claim priority | A6 |
| D14 | Exchange/listing allocation | Exactly review 800 listing rows, ten currencies; replace five NOK entries with EUR Euronext rows | B1 |
| D15 | Existing fictional company names and real metadata | Keep source provenance and identities; approve display/name and licensing policy | B1 |
| D16 | Sessions, market calendar and time zones | Choose real calendars versus game-scaled sessions, DST/closed-market behavior | B1/B3 |
| D17 | Liquidity and institutional balance sheets | Finite participants with explicit cash/share inventory and risk limits | B3 |
| D18 | Settlement and instrument precision | Immediate atomic settlement initially; whole-share player quantities; explicit price ticks separate from currency precision | B2/B4 |
| D19 | Portfolio/cost basis | Lot selection, fee/FX attribution and realized/unrealized reporting | B2 |
| D20 | Leverage, borrow and liquidation policy | Configurable limits; consistent collateral valuation; bounded deterministic liquidation | B6/B7 |
| D21 | Index coverage | Full constituent data versus explicitly labeled subset approximation; do not imply full S&P 500 from name alone | B8 |
| D22 | Resource IDs/footprints and art | Preserve full inventory; explicitly decide compatibility and geometry changes | A5/B4 |

Resolve only decisions needed by the selected phase. Use worked monetary examples and failure cases when choices affect value or recovery. No rates, leverage limits, fees or performance numbers are approved merely by inclusion in a prompt.

## Additional approved foundation decisions — 2026-11-03

- Identifiers: typed UUIDs for world/account/transaction/operation/payment/loan/order; player UUIDs preserved; typed organization/system actors; uppercase currency codes; stable catalog IDs; independent journal sequences/UTC timestamps.
- World UUID in database and world metadata; mismatch fails closed, complete copied worlds remain valid. Old economy database rejects without alteration. Recoverable cross-file initialization is an implementation obligation.
- Operation semantics: separate attempt/request, intent/operation and committed transaction IDs; canonical versioned request stored, world-scoped identity bound to actor/type; authorized duplicate lookup; same payload returns original result, conflict rejects. Committed/rejected terminal identities retained for world lifetime initially; transient admission failures do not consume IDs.
- Atomic journal/projection/outcome commits before acknowledgment; durable terminal business rejections; uncertain commit disables writes until recovery/lookup resolves it. No blind new-key retries. Append-only corrections; callback/cache failures cannot reverse a commit or justify duplicate effects.
- Recovery startup pipeline and staged projection generations/checkpoints; no partial publication or replayed external side effects. Unknown/corrupt authoritative records fail closed.
- Complete-world stopped-server backup/restore with manifest (world UUID, mod/schema/journal versions, sequence, economic checkpoint, timestamp, checksums, clean status). Restore into separate destination; explicit authorization before replacement. Future online interface allowed, online implementation excluded. Retention automation not decided.
- UTC due-time, monotonic runtime budgets, persisted economic completion IDs; stable seed/version and effective-version boundaries. Overdue version policy must be specified explicitly, not silently chosen.
- Queue fairness, safe read coalescing, bounded batches/results/payloads, explicit preacceptance rejection, no silent drop. Disconnect/timeouts cancel waiting, not committed work; revalidate live context for callbacks.
- Shutdown stops admission/scheduling, completes/rolls back current transaction, drains then closes on owner; drain expiry reports unresolved status, no unsafe cross-thread close.
- Validated restart-only storage/worker/queue configuration, no silent clamping; read-only status/diagnostics for console/op level 2; sanitized player messages, operation-correlated rate-limited logs and qualified ETAs.
- Final foundation gate includes actual development dependency loading, runClient help/dry-run plus client/server loading, disposable atomicity/migration/recovery/overload fixtures and release packaging isolation. Exception injection and actual process termination evidence remain distinct. No tests executed during design.

All five A1 packages are design-approved. Remaining low-level specifications must respect these decisions; later gameplay fees, leverage, calendar, loan rules, listing allocation, item recovery and full-load measurements remain open in their owning phases.

## Historical implementation-location decision — completed in A1-01

The approved one-time rebuild was performed in the economy Git root on the redesign branch, preserving .git, prompts/ and AGENTS.md. A1-01 records the original reset scope and the historical handoff records completion. This decision authorizes no further deletion. Runtime storage rejection and restore protections remain unchanged.
