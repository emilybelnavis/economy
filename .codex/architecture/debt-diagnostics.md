# Debt diagnostics

A6-10-02 adds command-only private owner views and separately authorized operator inspection over the A6-10-01 lifecycle. Platform pins and development runs retain their existing configuration.

## Entry points and authority

- `/economy debt cases [after-case-uuid]` pages collection clocks and current creditor.
- `/economy debt reports [after <effective-ms> <report-uuid> <revision>]` pages immutable report revisions, including original missed and subsequent cured status.
- `/economy debt payments <card|loan> <facility-uuid> [after <sequence> <position>]` pages owner-bound pre-default payment receipts using the existing credit/loan decoders. Loan pages advance across a bounded source-event page and can be empty while still supplying a next cursor.
- `/economy debt history <case-uuid> [after <sequence> <position>]` pages typed collection sale, payment and write-off receipts.
- `/economy debt inspect <card|loan> <owner-uuid> <facility-uuid> [after-obligation-uuid]` provides audited bounded operator explanations, including pre-default original obligations.

Operator history uses `/economy debt inspect_reports <owner-uuid>` with the report tuple cursor and `/economy debt inspect_history <owner-uuid> <case-uuid>` with the accounting cursor. Every page obtains its own audited inspection.

Commands require a directly authenticated player. Inspection also requires current operator authority. Replies are private and session/player identity is rechecked before publication. Each operator page seals a fresh account-inspection audit before the private read. Inspection remains available during financial catch-up after startup readiness. Owner and inspection grants stay separate. LedgerService adds `debtCases`, `debtReportPage`, `debtEvents`, `cardPaymentHistory`, `loanPaymentHistory`, `inspectDebt`, `inspectDebtReports` and `inspectDebtEvents`. The existing raw `debtReports`, collection and repayment APIs retain their contracts. There is no new network packet or GUI protocol.

## Read and storage contracts

Pages accept 1–16 entries through the service API and commands request four. Owner filtering binds world and player before selection. Journal receipts validate their selected case and same-transaction owner-bound case snapshot. Cursor order uses case or obligation UUID, report effective instant/report UUID/revision, or journal sequence/position. Historical reports remain readable after their score eligibility expires. Later-cure revisions are history-only while the original missed snapshot remains a score input. Revision selection examines at most 17 saved revisions per report and labels incomplete selection explicitly. Owner payment receipts require same-transaction owner-bound facility snapshots. Captured journal frontiers identify each read snapshot.

V32 adds three indexes only. `debt_diagnostic_obligations` indexes world/kind/facility/obligation. `debt_accounting_history` indexes the canonical accounting case UUID substring and journal sequence/position. `debt_sale_diagnostic_history` indexes the sale-attempt UUID and journal sequence/position. Online open, reopen, offline inspection and backup compatibility validate the new schema. No new monetary entities, facts, fact versions or deadline providers are introduced. Existing v1 debt accounting/report/case decoders and append-only cure revisions remain authoritative.

## Clocks and accounting

Original due dates determine arrears age, due+2 reporting and the 84-day default boundary. Collection acknowledgement and 728-day expiry remain independent of the original 2548-day default-report expiry. Settled, uncollectable and expired reporting receive separate labels. Payment-score eligibility follows the score policy window and default suppression. Upcoming world-aligned 28-day policy boundaries are shown with the production scoring adapter’s dormant status.

Operator reconciliation compares the selected frozen facility to its case and current creditor/borrower aggregate claim witnesses to posted receivable/liability accounts. Shared creditor totals include all applicable claims in that currency. Provider cursors expose only THIS_SELECTION/OTHER_TARGET. A pending sale explains its latest case-bound failed attempt and next attempt clock.

A6-10-01’s legacy timeline reconciliation boundary remains applicable. Startup can reject untracked obligations whose original transitions precede the completed financial frontier. A6-10-03 must resolve that migration boundary and accept the scoring/reporting DAG before activation. This package preserves automatic append-only cure corrections under the approved servicing policy.

A6-10-03 adds [active score dependency status](debt-acceptance.md). Diagnostics show the owner’s actual scheduled score head when installed, include the redacted score provider and distinguish installed catch-up from the detached dormant fallback.
