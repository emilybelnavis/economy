# Loan diagnostics — A6-09-02

## Owner views

The owner-bound `LedgerService.loanPage`, `loanView` and `loanSchedulePage` return immutable diagnostic snapshots through the sole storage worker. Contract views retain fixed origination APR, prime/version, score/profile revision, Tier, policy, denomination/FX witnesses, original principal, term, schedule hash and current servicing revision. Current principal, posted interest, earned accrual and signed residual remain distinct. Original installment requirements retain due dates and unmet amounts; a requirement is not another liability.

Exact as-of debt is calculated only within the saved current month. Overdue monthly work reports MONTHLY_CATCHUP_REQUIRED and saved amounts; it never pretends an incomplete accrual is a current payoff. Clock-before-snapshot is explicit. Current prime unavailability does not change an existing fixed contract. All reads recheck selected world/owner; missing and foreign selections give the same unavailable result.

Original schedule pages validate each row and expected page count against the captured fixed terms, with at most 104 pure schedule calculations. They retain v1 hash and currency, even after early repayment. Closed monthly installment pages use immutable v1 snapshots; later payments do not rewrite original required amounts. Owner application lookup returns the original immutable accepted decision, including denials; no profile means no fabricated credit score. Pre-underwriting transient failures have no stored business decision.

Owner commands (authenticated player-backed sources only):

- `/economy loan list [after-loan-uuid]` — four contracts per page, exclusive UUID cursor.
- `/economy loan contract <loan-uuid>` — captured contract and servicing status.
- Append `schedule [after-ordinal]` or `installments [after-ordinal]` — four original rows per page; ordinal is exclusive.
- Append `payoff <owned-funding-account-uuid>` — read-only full-payoff preview with calculation/expiry, loan/funding/ledger revisions and explicit FX notice. No debit or waiver occurs on preview.
- `/economy loan application <application-uuid>` — original owner-bound underwriting decision.

The existing native/FX repayment APIs still enforce their original revision, boundary, ownership and quote-consumption checks. A payoff preview expires within 30 seconds or sooner at relevant credit/loan boundaries. It cannot authorize money by itself. Foreign funding needs explicit captured FX and fees before settlement. No financial mutation command, GUI action or new packet is installed.

## Audited operator views

`LedgerService.inspectLoan` and `inspectLoanApplication` require separate inspection grants. Each page first commits the existing InspectionAudit operation, whose canonical intent binds operator, target, domain, selection and cursor/limit. The receipt uses existing `economy:account_inspection` v1; no new financial fact family is introduced. Live audience/session/privilege checks guard submission and publication. Owner grants cannot enter the inspection path, and inspection grants cannot enter owner reads.

- `/economy loan inspect <owner-uuid> <loan-uuid> [after <sequence> <position>]` — two examined event rows per page; start cursor is `0 -1`.
- `/economy loan inspect_application <owner-uuid> <application-uuid>` — captured approval/denial, independently audited.

Console/RCON and `/execute as` cannot impersonate the player-backed inspection identity. Errors are generic, private and suggest catch-up inspection without leaking another owner's selection. No edit/reset/skip/reprice path exists. The narrow existing audit route permits diagnosis while scheduled finance is pending, without admitting monetary writes or bypassing startup readiness.

Operator snapshots include journal frontier/as-of, current prime availability, original underwriting and finite funding witness when available, fixed unsecured claim/collateral capability, monthly provider head, completed/applying state and bounded event/payment evidence. Applying cursor values are redacted to THIS_LOAN/OTHER_TARGET/empty. Missing provider or original source is explicit, never reconstructed from today's balances/rates.

## Immutable receipts and bounded reconciliation

Existing v1 loan events are decoded, not migrated. PAYMENT totals require received=principal+interest and matching allocation subtotals (at most 34 lines). Recognition, signed residual disposition and additional successful-full-payoff interest waiver remain separate. Payment journal checking compares same-transaction loan-borrower LIABILITY and lender RECEIVABLE deltas with recognized interest minus repayment. FX fees do not satisfy debt. Operator output shows reconciliation flags and totals rather than raw private journal payloads.

Loan event identity is additionally tied to an immutable loan snapshot with matching owner/world/currency/lender/schedule hash. Origination is found through the earliest indexed contract fact. APPLICATION_DENIED events are excluded from contract history: applicants may choose a colliding loan UUID, so a denial belongs only to the separately owner-bound application lookup. Pagination advances by examined rows, including skipped denial rows, with bounded lookahead and no unbounded search for an origination event.

Owner portfolio reconciliation counts only loan debt against its separate loan-borrower liability. Lender reconciliation counts both card and loan debt against the shared lender receivable. Portfolio folds process at most 128 rows (shared lender budget); journal folds process at most 128 posting rows at the sampled frontier. One-row lookahead exposes incompleteness, never a false match. BigInteger protects journal totals; checked minor arithmetic protects portfolio totals. Mismatched projections or immutable journal evidence are reported rather than repaired.

V29 appends only `loan_diagnostic_records`, an expression index on encoded loan UUID, record type, sequence and position for existing loan/loan-event facts. Query predicates match the partial index and use exclusive sequence/position keysets. Main/offline validation and complete-world backup accept V29; V1–V28 migrations, journal formats and original financial behavior remain unchanged. No new table, source, scheduler, monetary posting or second database is introduced.

New production borrowing remains gated by CreditSafety pending A6-10 lifecycle acceptance. A6-09-03 owns loan foundation acceptance; reporting/default/collections/autopay, recurring scoring and B6 collateral remain later work. See [fixed loans](loans.md).
