# Changelog

Project changes are recorded in reverse chronological order, with the most recent changes first, by date and work package. Entries describe completed work and its verification; planned work is identified explicitly.




## 2026-11-06 — D15 automatic payments and A6-13 acceptance

- Added default-OFF automatic payments with reviewed per-facility consent and one named owned same-currency bank account.
- Added card minimum, statement-after-payments and fresh full-payoff targets, plus loan due-installment payments.
- Added atomic all-or-nothing payments, two daily retries, persistent original targets and replay-safe offline recovery before same-instant delinquency and reporting.
- Added schema 35 migration and bounded historical statement/payment reconstruction. Banking protocol 8 includes the consent screens.
- Completed A6-13 acceptance with automatic-payment scenarios and actual client enable/disable verification. Broader multiplayer and combined gameplay coverage remains in the recorded A8 handoff.

## 2026-11-06 — A6-13 integrated acceptance fixes

- Fixed debit-card issuance, lost-card reporting and branch closure failing before admission because the authenticated owner was bound twice.
- Added release acceptance coverage for native bank lifecycle and remote CHF-funded collection payments, plus actual client finance controls and rendered screens.
- Corrected finance amount labels and issued-credit-card names, and added discoverable scrolling for longer terms.
- Updated schema 34 upgrade/restore instructions and published downstream integration handoffs.
- Recorded automatic payments as an outstanding approved feature. A6 phase closure remains qualified by that gap.

## 2026-11-06 — A6-12 bank and credit menus

- Added issuer-kiosk credit applications, limit increases, credit-token replacement and fixed bank/subprime loan applications.
- Added remote credit, loan and collection repayment menus, loan payoff and schedules, credit reports and net-worth explanations.
- Connected held-credit-card register purchases to atomic charge and protected goods delivery. Added advance-to-account review before separate cash withdrawal.
- Published the version-one server banking integration contract and preserved saved original-operation recovery across finance menus.

## 2026-11-05 — A6-11 Collection Agent

- Added the original nonhostile Collection Agent with bounded loaded-chunk spawning and debtor-only follow behaviour.
- Added private case/funding selection and reviewed partial/full payments, including explicit FX fees and original receipt recovery.
- Added durable presentation leases and seven-financial-day death cooldowns in schema34.

## 2026-11-05 — A6-10-03 reporting and lifecycle acceptance

- Activated resumable 28-day credit scoring after due-date reports, with exact daily utilization and captured historical FX.
- Added safe historical obligation/report reconstruction and precise rejection for histories needing corrective accounting.
- Accepted debt boundaries, collection expiry, offline replay and process recovery. Production borrowing APIs now require the installed, current lifecycle dependency.
- Added schema33 source, daily-area and migration checkpoints. Existing history and balances are preserved.

## 2026-11-05 — A6-10-02 debt diagnostics

- Added private collection-clock, report-revision and collection accounting-history commands.
- Added audited operator explanations for original arrears, cure, sale, independent expiry clocks and creditor reconciliation.
- Added indexed bounded history reads in schema32.

## 2026-11-05 — A6-10-01 shared debt lifecycle

- Added original-obligation late reporting, cure revisions, exact default boundaries and independent collection/report clocks for cards and loans.
- Added funded claim sale, recovery discount accounting, expiry write-offs and protected native/FX collection repayment.
- Replaced the shared-lender repayment scan with transactional claim witnesses and added schema V31 lifecycle storage.
- Legacy obligations behind the completed financial timeline require reconciliation before startup readiness. Production borrowing awaits lifecycle acceptance.

## 2026-11-05 — A6-09-03 loan foundation acceptance

- Accepted fixed-loan term/rate, amortization, early payoff, wealth and restart/recovery behavior with 482 passing tests.
- Fixed full settlement leaving inconsistent borrower or lender claims by checking bounded accounting before repayment.
- Added the immutable original-obligation source for lifecycle consumers and its schema V30 index.
- Production borrowing awaits lifecycle acceptance and a scalable successor to the 128-claim repayment reconciliation bound.

## 2026-11-05 — A6-09-02 loan diagnostics

- Added owner commands for captured loan contracts, original schedules, closed installments, application decisions and payoff previews.
- Added separately audited operator views for underwriting, finite funding, payment allocations, shared receivable reconciliation and monthly catch-up.
- Added a bounded history index in schema V29; original financial records and the production borrowing gate remain unchanged.

## 2026-11-05 — A6-09-01 fixed loan foundation

- Added fixed unsecured bank/subprime underwriting, captured rates and monthly amortization schedules backed by finite lender capital.
- Added protected partial repayment and early payoff, explicit currency conversion and separate fees without requiring another bank account.
- Included loan debt and accrued interest in wealth and shared exposure checks, with schema V28 persistence.
- Verified 463 tests and isolated client/server loading. New production borrowing remains gated pending lifecycle safeguards; loan screens and diagnostics follow in separate packages.

## 2026-11-05 — A6-08-03 credit foundation acceptance

- Accepted numerical, offline and crash/recovery behavior across draw, interest, statements and repayment.
- Gated new production credit issuance, increases and draws pending downstream default/reporting safety, while preserving original outcomes and protected debt actions.
- Fixed credit API owner binding and published a bounded immutable lifecycle source with a V27 index for future consumers.

## 2026-11-05 — A6-08-02 credit diagnostics

- Added owner facility, APR/bucket/minimum/due and frozen-statement views with bounded paging.
- Added separately audited operator diagnostics for historical daily inputs, payment allocations, reconciliation and schedule backlog, including catch-up inspection.
- Added immutable prospective servicing receipts and schema V26 diagnostic indexes; older records explicitly identify unavailable detail. No financial editing controls were added.

## 2026-11-05 — A6-08-01 credit facility foundation

- Added funded credit facilities, separate network credit tokens, once-only starter issuance, underwritten increases, closure and replacement APIs.
- Added separate purchase/advance interest, immutable 28-day statements and minimums, balanced draws and same-currency/explicit-FX repayments.
- Added schema V25, bounded daily/statement scheduling and authoritative credit liabilities in net worth. Product screens, autopay, default/reporting and collections remain later-package work.

## 2026-11-05 — A6-07 credit profile foundation

- Added deterministic 300–900 scoring and approved underwriting ceilings/APRs, with exact time-weighted utilization and correction-aware inputs.
- Added schema V24 profiles, immutable score history and cross-issuer inquiry grouping, plus owner profile/history views and audited operator read APIs.
- Reserved atomic 650/2,500 first-credit issuance for the future credit backend. Debit cards and failed applications create no profile; production recurring scoring stays dormant until reporting is installed.

## 2026-11-05 — A6-06 issued debit cards

- Added 28 bank/Tier debit designs with server-owned token linkage, expiry, lost reporting and issuer-kiosk issuance/replacement.
- Added free first issuance and renewal window, combined 25-world-unit replacement fee/FX preview, atomic old-token revocation and protected item claims.
- Added schema V23 and bounded card recovery. Copies share status; provider changes/closure revoke old tokens. Legacy collectibles stay unchanged; no score, credit facility or debt is created.

## 2026-11-05 — A6-05-01 branded branch hardware

- Added bank selection for all 23 ATM/teller/kiosk designs and a craftable two-cell branch kiosk. Native-currency cash settings preserve ATM bill and teller coin distinctions.
- Added in-person account opening, protected closure, legacy provider assignment and two-branch provider switching. Generic cards, tellers and commands cannot perform restricted account lifecycle actions.
- Added schema V22 with explicit unbound legacy migration, preserved settings/custody, owner-confirmed bank binding and read-only operator inspection. Existing colored IDs remain; ATM dye recipes are retired.
- Card replacement and loan/credit applications remain unavailable pending their service packages.

## 2026-11-05 — A6-05 original banking artwork

- Added original marks for 23 banks and four networks, 74 approved debit/credit card designs, Tier 2 treatments and 12 menu icons.
- Added matching native-currency ATM/teller resources, distinct branch kiosk models and civilian Collection Agent art, with editable sources and CC0 provenance.
- Preserved legacy collectibles and existing physical machines. Physical binding, card issuance and agent behavior remain in their later packages.

## 2026-11-05 — A6-04 qualification, pricing and deposit yield

- Added authoritative net-worth qualification at 100M world-currency units, including unpaid liabilities and earned ledger receivables, with explicit unavailable-source handling.
- Added bank-owned Tier 1 FX fees and zero Tier 2 FX fees, captured quote inputs, protected closure budgets and explicit cash-exchange previews. Legacy unassigned accounts retain their transitional behavior.
- Added time-weighted deposit yield, funded monthly payout promises, deferred distress and retained claims across provider/currency changes; closure settles earned amounts without minting money.
- Added future-day prime configuration and securities/trading-fee provider APIs; actual lending/trading and live branch adapters remain outside this package.
- Schema 21 requires the existing stopped-world upgrade backup procedure.

## 2026-11-05 — A6-03 institution accounts

- Added the 23-bank catalog, persistent provider attribution, bounded legacy migration and a one-time 28B world-currency capital allocation across banks and the two service institutions.
- Raised the total open bank-account limit to four while preserving primary protection, currency uniqueness, balances and history.
- Added server-side branch admission and provider switching contracts. Live opening, closing and provider changes remain unavailable until branch kiosks are delivered; existing account services remain available.
- Schema 20 requires the stopped-world upgrade backup procedure for existing worlds.

## 2026-11-05 — A6-03-01 CHF currency and cash

- Added CHF accounts/FX support and thirteen original notes and coins, with a CHF creative tab; existing cash and card identities remain intact.
- Added future-only CHF FX activation for existing worlds using the approved 1 CHF = 1.60 CAD game calibration, preserving earlier publications and world currency.
- Added exact CHF cent settlement, cash representability checks and versioned ledger replay/history, including fractional balances created by CHF conversion.
- Schema 19 includes both posting versions in account history; existing worlds require the stopped-world upgrade backup procedure.

## 2026-11-05 — A6-02-02 financial calendar diagnostics

- Added console/operator calendar diagnostics with UTC origin, elapsed periods, retained targets, policy versions, next boundaries and active work cursors.
- Added bounded registry pages and timestamped cached reports during catch-up, clock waits and storage pressure; distinguished fixed backlog from the domain pending-event indicator.

## 2026-11-04 — Banking interface fixes

- Replaced manual account and collection numbers with selectable accounts and item batches; My accounts opens transaction history directly.
- Restored Deposit and Withdrawal wording, tied withdrawal currency to its account, and added From/To selectors for exchange.
- Restored the compact original banking frame and restricted the player inventory to Deposit.
- Explained the three-account limit and added reviewed teller account closure with balance transfer to the primary account.
- Added specific deposit rejection messages, including disabled terminal services and unaccepted cash, with Creative cash regression coverage.
- Made teller cash services always available, defaulted new ATMs to all cash services, and added per-ATM owner configuration through an empty-hand right-click.
- Shortened deposit completion to a thank-you, mentioning returned or collectible bills only when applicable.
- Replaced the New account tab with a banking home page, first-account creation in My accounts, and a dedicated account management screen with balances, available currencies and the three-account limit.
- Added a full-width Open a new account button and separated transaction-history headings and balances from the scrolling transaction list.

## 2026-11-04 — A5-09 banking menus and phase acceptance

- Added world currency setup, bank accounts/history, FX preview and confirmation, terminal settings, cash deposit/withdrawal, register purchase and claim screens.
- Bank cards provide remote banking access without credit. Server-issued sessions, revisions and current terminal checks reject stale or remote cash actions.
- Protected originals survive page changes, closure and saved input recovery. Cash and goods delivery requires observed player/source saves, including the singleplayer host’s world metadata.
- Verified normal dedicated/integrated workflows, original-operation lookup, disposable process interruption and paired/skewed recovery. Trading UI remains excluded.

## 2026-11-04 — A5-08 physical terminals

- Added 17 ATM finishes, 17 register finishes and a teller, with original art, recipes, recoloring and a creative tab.
- Wide machines place across four cells, rotate toward the player and clean up with one survival drop when any cell breaks. Registers fit above stock containers.
- Fresh placement identities bind terminals to owner policy. Saved removal records survive admission queue pressure. Banking and purchase screens remain in the next package.

## 2026-11-04 — A5-07 register sales

- Added owner-priced exact-batch register checkout with stock reservation, matching primary-account currencies, atomic payment receipts and protected delivery claims.
- Added server-side stock and buyer-capacity checks, saved replay protection and the owner-authorized shop price command.
- Schema 17 preserves register prices, sales and stock recovery. Physical blocks and purchase menus remain scheduled separately.

## 2026-11-03 — A5-06 withdrawals

- Added exact ATM bill planning and teller bill/coin planning with validated bill edits and ceiling account debit.
- Full-batch capacity gates precede debit and issuance. Committed withdrawals retain protected custody delivery and saved replay markers.
- Schema 16 preserves frozen withdrawal intents and recovery identity.

## 2026-11-03 — A5-05 deposits

- Added 24-slot deposit APIs with frozen account, terminal and FX terms, source-currency rounding and authentic cash quantity reservations.
- Added exact-original cancellation and skipped-item returns, partial-capacity claims and saved return replay protection.
- Schema 15 preserves deposit recovery and quantity locks through interrupted or uncertain delivery. Final menus remain in their integration package.

## 2026-11-03 — Easier currency setup

- Initial currency selection now uses /economy choose CAD with tab completion, lowercase support and no UUID to copy.

## 2026-11-03 — A5-04 terminal policy

- Added owner/operator terminal policy APIs with deposit/withdraw modes and currency/denomination allowlists.
- New placements start disabled. Durable placement generations and revisions reject stale configuration after replacement.
- Schema 14 preserves world-scoped settings, tombstones and used generation identities.

## 2026-11-03 — A5-03 cash and cards

- Restored 118 cash collectibles, eight single-stack banking cards, twelve tabs and both Canadian bill artworks.
- Added persistent world-bound issuance markers and journaled quantity validation for trusted integrations. Copied markers share the original lot's remaining quantity.
- Schema 13 quarantines outstanding restored issuance quantities pending verified custody reconciliation. Development collectibles and worlds are preserved.

## 2026-11-03 — A5 custody foundation

- Added trusted custody APIs, durable owner claims and evidence-based operator resolution.
- Capacity checks precede settlement and collection. Restored cases enter review before new custody work is admitted.
- Schema 12 preserves custody audit records and save witnesses. Live inventory adapters remain pending integration verification.

## 2026-11-03 — A4 ledger and transactions

- Added balanced settlement, durable holds, quoted FX, funded closure, and atomic denomination rebase.
- Added payment, retry, balance, history, and operator reconciliation commands. History retains original amounts and pairs entries by transaction ID.
- Added a shared ledger API for trusted server integrations. Schema 10 adds indexed account history. Existing worlds require a complete-world backup before upgrading.

## 2026-11-03 — A1 foundation acceptance

- Added restart-only worker, queue, callback, SQLite lock and drain settings. Ongoing UTC catch-up uses bounded chronological work and gates affected writes while behind.
- Added public `/economy status` and operator `/economy diagnostics` for storage health, recovery progress, queue stalls and sanitized failure details.
- Verified 67 tests, including sequential financial-world isolation and upgrades from supported schemas 1–3. The development client loaded the mod and SQLite. A disposable dedicated world backed up, restored separately, and booted with its original identity.
- Verified the release jars exclude fixtures. The foundation release ships without gameplay economic systems. [Acceptance and phase handoff](.codex/A1-05-03.md).

## 2026-11-03 — A1-04: Recovery, projections, backup/restore, and clock primitives

### Changed

- Added schema version 4 through the existing atomic upgrade chain and complete-world pre-upgrade backup gate; preserved earlier journal facts and identities.
- Added incremental startup validation before readiness: journal compatibility before migration, projection/checkpoint validation and rebuild, overdue updates, and final consistent-state checks. Unknown committed types/versions fail closed; new journal records require registered decoders.
- Added bounded projection rebuilds into separate generations, with atomic state/checkpoint commits, checksums, restart resume, immutable snapshots, and activation only after validation. Replay never reruns historical commands, random decisions, or external deliveries.
- Added persisted economic sequence, seed, stable interval/update identity, chronological dependency ordering, and explicit historical simulator versions. Update effects, journal outputs, and completion commit atomically; backward clocks retain debt, forward jumps become bounded catch-up, and sleep or `/time` do not drive economic time.
- Added a post-Minecraft-save clean-world witness distinct from database close, with shutdown-error and session checks.
- Added offline complete-world backup/restore commands with checksummed identity/version/journal/economic manifests, consistent DB/WAL handling, compatibility validation, staging, and a required new restore destination. Current worlds and backups are preserved; no online backup or retention automation was added.
- Documented A5 intent/reservation/commit/delivery/save/reconciliation evidence and the limits of database idempotency across Minecraft saves.

### Verified

- All 53 tests and the release build passed, including predecessor regressions, rebuild equality/resume/activation, rollback, version transitions, clock manipulation, batch/restart determinism, startup gating, backup rejection, separate restore, and restored-clock catch-up.
- Actual disposable child-process interruption tests covered an uncommitted rebuild batch and economic updates before/after commit. These are distinct from exception-injection tests and do not constitute hardware power-loss proof.
- Verified quiescent WAL-image inspection preserves sealed source files and includes committed WAL state.
- Gradle task inspection and required `runClient` help/dry-run checks passed. Actual client/JDBC loading succeeded; its smoke run was intentionally interrupted after loading.
- Actual disposable dedicated-server startup and restart reached ready state, then stopped normally with a complete drain and post-save clean-world witness.
- Ran the offline backup and separate-destination restore commands successfully against the stopped disposable server world. Release packaging excludes test projections, simulators, probes, and failure harnesses. `git diff --check` passed.

### Scope and handoff

- Covers F02/F03 recovery/checkpoint/version contracts, F09 journal rebuild support, and F23 restart/interruption/restore verification.
- Production registers no gameplay economic systems or fixture projections. No inflation, interest, FX, stock simulation, physical-item recovery, online backups, or automatic replacement was implemented.
- General restore currently supports clean schema-4 manifests. Runtime checks were on macOS; manual integrated-world play, hardware power-loss behavior, and the eventual market catch-up performance target were not exercised.
- A1-05 scheduling/diagnostics integration and acceptance remain unstarted. Protocol and A5 crash-boundary handoff: [.codex/A1-04.md](.codex/A1-04.md). Actual evidence: [.codex/VERIFICATION.md](.codex/VERIFICATION.md).

## 2026-11-03 — Dedicated A1-01 documentation

- Moved the original scaffold/lifecycle handoff from `.codex/PROJECT.md` into the missing `.codex/A1-01.md`, preserving its historical context and linking its verification evidence.
- Updated the project index to link all three completed package notes.
- Verified documentation links and `git diff --check`; no implementation changes or runtime checks were needed.

## 2026-11-03 — A1-03: Durable operations and immutable journal

### Changed

- Added schema version 3 through the existing atomic migration chain and offline complete-world backup requirement; preserved earlier foundation identity and migration history.
- Added versioned transaction envelopes, explicitly positioned immutable records, typed actor/service attribution, UTC audit instants, database-assigned sequences, durable results, and optional economic-update/correction references.
- Atomically commit journal records, same-store projections, canonical operation identity, and results. Persist terminal business rejections without domain effects.
- Added world-scoped operation deduplication across retries and restarts. Persist the full versioned canonical request; equivalent retries return the original outcome, while changed payloads conflict and unauthorized actors cannot retrieve outcomes.
- Added database protections against editing, deleting, replacing, or extending sealed journal facts. Startup and recovery validate protection triggers and foreign-key integrity.
- Added accepted/pending, committed, durably rejected, not-accepted, and unknown-outcome contracts. Request IDs trace attempts; operation IDs remain stable across retries.
- Added fail-closed connection handling and original-ID lookup after uncertain commits. Recovery never blindly repeats projections or substitutes a new operation ID; unconfirmed disposal retains world ownership.
- Added bounded server-thread admission/publication and immutable results. Cache/callback publication failure is logged separately and cannot reverse or relabel a durable commit; canceling a wait does not cancel accepted storage work.
- Added bounded journal paging and generic projection/process/failure fixtures confined to test sources.

### Verified

- All 39 tests passed, along with the release build, task inspection, and required `runClient` help/dry-run checks.
- Fixtures covered all-or-nothing multi-record/projection commits, 50 overlapping same-key submissions with one effect, canonical conflicts, authorization, stable terminal rejections, restart lookup, corrections, and append-only enforcement.
- Injected commit-response, rollback, close, and postcommit cleanup failures; verified original-outcome resolution or unknown/fail-closed behavior. Cache publication failures and canceled waits caused no duplicate durable effects.
- Terminated only disposable child processes at before/after-commit boundaries; reopening showed rollback before commit and original-outcome recovery after lost acknowledgment.
- Verified the supported schema-2 upgrade requires a stopped-state backup and preserves existing identity/history; removed protection triggers cause safe startup rejection.
- Actual disposable dedicated server reached ready state with schema 3 and stopped normally with a complete drain. Actual client/JDBC loading succeeded; the client smoke run was then intentionally interrupted.
- Inspected the release jar for the pinned driver and fixture exclusion; the runtime database had no test projection table. `git diff --check` passed.

### Scope and handoff

- Covers F02 (transaction/postcommit guarantees), F09 infrastructure (journal and durable operation identity), and F23 (atomicity/duplicate/interruption verification).
- No accounts, balances, financial postings, transfers, holds, or external/item-delivery protocols. A1-04 recovery integration and A4 financial semantics remain unstarted.
- Process-interruption and injected JDBC tests do not constitute hardware power-loss proof. Runtime checks were on macOS; manual integrated-world play and a separate installed-release runtime were not exercised.
- Protocol and handoff: [.codex/A1-03.md](.codex/A1-03.md). Actual checks and limits: [.codex/VERIFICATION.md](.codex/VERIFICATION.md).

## 2026-11-03 — Changelog ordering

- Reordered this changelog with the most recent changes first and made that ordering a project requirement.
- Verified entry order and `git diff --check`; documentation-only change.

## 2026-11-03 — A1-02: Identifiers, SQLite storage, and migrations

### Changed

- Added distinct identifier types for financial worlds, player/organization/system actors, accounts, transactions, operations, requests, and future payment, loan, and order entities; defined stable currency/catalog conventions and separate journal, economic, and audit values.
- Replaced the production no-persistence adapter with world-local SQLite storage, owned exclusively by the bounded storage worker. Added an OS ownership lock that excludes a second process until the connection is disposed.
- Bundled SQLite JDBC 3.53.4.0 and explicitly supplied it to client/server development runtimes. Kept the Minecraft, NeoForge, and Java baseline unchanged.
- Added recoverable world UUID initialization using a durable intent marker, an atomic SQLite foundation-schema transaction, and separately published world-side identity metadata.
- Added schema versions 1–2, ordered migration history, and atomic pending migration chains. Legacy, unsupported, corrupt, and wrong-world stores are rejected without replacement.
- Configured and verified WAL, FULL synchronization, foreign keys, and a five-second SQLite lock timeout.
- Added typed persistence and admission errors, readiness failure propagation, and a worker-only atomic transaction interface with bounded immutable query results for A1-03.
- Added an offline complete-world backup prerequisite for upgrades, with stopped-world locks, validated file hashes, and a receipt published only after successful backup validation. Startup validates backups; it never copies a running world.
- Added disposable storage fixtures and updated build/run and upgrade instructions.

### Verified

- All 22 lifecycle/storage tests and the release build passed; Gradle task inspection and required `runClient` help/dry-run checks passed.
- Fixtures covered fresh/reopened worlds, identity interruption boundaries, wrong/missing identity data, legacy/corrupt/future/empty stores, ordered upgrades, whole-chain rollback, stale/tampered backups, active-world rejection, cross-process ownership, lock timeout, read-only storage, missing driver, transaction bounds, and readiness propagation.
- Actual disposable dedicated-server runtime loaded the JDBC driver, created its SQLite foundation, reached ready state, and shut down successfully with a complete drain.
- Actual disposable client runtime loaded the JDBC driver and completed resource loading; the smoke run was then intentionally interrupted. Manual integrated-world play was not exercised.
- Ran the offline backup command successfully against the stopped disposable server world; inspected the release jar for the exact driver version/range, native libraries, licenses, and fixture exclusion. `git diff --check` passed.

### Scope and handoff

- Covers F02 (world storage), F03 (schema/migration capability), and F23 (storage verification). Legacy import remains explicitly excluded.
- Only foundation metadata and migration history are created. No accounts, balances, journal records, online backups, or restore operations were added.
- Cross-file initialization is recoverable, not a cross-file atomic transaction. Failed/uncertain writes are not blindly retried; durable operation resolution remains A1-03.
- Detailed protocol and verification results are recorded in `.codex/A1-02.md`.

## 2026-11-03 — Project change tracking

- Started this changelog with the completed A1-01 work and verification.
- Added a project instruction to record subsequent work here, including verification and remaining limitations.

## 2026-11-03 — A1-01: Scaffold and server lifecycle

### Changed

- Performed the authorized one-time reset on `redesign`, preserving `.git`, `prompts/`, and root `AGENTS.md`. The previous implementation remains recoverable from `main`.
- Created a fresh Economy mod scaffold pinned to Minecraft 1.21.1, NeoForge 21.1.252, and Java 21, with a newly generated Gradle 9.2.1 wrapper.
- Configured `runClient` and `runServer`, registered the main mod source set in development runs, separated their runtime directories, and enabled server console input.
- Added separate common and client entry points, mod metadata, release packaging, and test sources excluded from the release jar.
- Added one service container per server with initializing, ready, unavailable, stopping, and stopped states, readiness gating, isolated session identity, and repeatable shutdown handling.
- Added bounded storage and calculation workers, bounded completion delivery on server ticks, a callback-start budget, and shutdown drain and interruption handling.
- Added foundation API contracts for trusted server request context, correlation, payload bounds, and response status.
- Replaced the README with foundation build/run instructions and added fresh architecture and verification notes in `.codex/`.

### Verified

- Build and seven automated tests passed, covering lifecycle isolation, readiness and failure handling, worker bounds, thread ownership, callback delivery, shutdown, and payload limits.
- Gradle task inspection, `help --task runClient`, and `runClient --dry-run` passed.
- Actual development client loading succeeded; the smoke run was then intentionally interrupted. The dedicated server reached ready state and shut down through `stop` with a successful drain.
- Confirmed the release jar excludes tests and fixtures, and `git diff --check` passed.

### Scope and handoff

- Covers F01 (development scaffold), F02 (server lifecycle), F19 (request boundary contracts), and F23 (verification infrastructure).
- Storage is a no-persistence adapter. SQLite, migrations, durable commits, recovery, and gameplay are not implemented in this package.
- Sequential-world isolation was tested with fixtures; sequential integrated-world play was not manually exercised.
- Detailed results and limitations: [.codex/VERIFICATION.md](.codex/VERIFICATION.md). Architecture and A1-02 handoff: [.codex/PROJECT.md](.codex/PROJECT.md).
