# A6 shared contract — version 1

Status: **contract/design delivery for A6-01**. The user requested this document after approving all A6 prompts and all remaining policies, with funding increased to **28,000,000,000 server-currency units**. No A6 runtime behavior, schema migration, asset generation or game verification is delivered here. Implement one selected package at a time.

## 1. Authority and scope

This contract defines shared identities, ownership, state vocabulary, boundaries and handoffs. Exact gameplay parameters are normative in the approved [credit/funding](POLICIES-CREDIT.md), [banking](POLICIES-BANKING.md) and [servicing](POLICIES-SERVICING.md) contracts, with approval recorded in [POLICY-REVIEW.md](POLICY-REVIEW.md). The [specification](SPECIFICATION.md) owns feature coverage, [branding](BRANDING.md) owns original visual identity, and [execution queue](EXECUTION.md) owns package order. Later explicit user instructions override these documents. Do not reinterpret an old record using a new policy or silently expand scope.

Baseline: Minecraft 1.21.1, NeoForge 21.1.252, Java 21, per-world SQLite and one owned storage worker. Existing A1–A5 behavior is delivered upstream; A6 extends it. Preserve code, all player/world/account identities, immutable history, worlds and backups. No reset, second banking database, live real-bank integration, built-in external-mod integration or premature stock/margin implementation.

## 2. Upstream contract inventory

| Upstream | Current evidence and preserved guarantee | Required A6 extension / owner |
| --- | --- | --- |
| A1 storage | `SchemaMigrations.CURRENT=17`, minimum1; one connection/worker, additive contiguous migration registry, transactional pending chain and pre-upgrade backup gate | Append migrations when implementing each package; no DDL or version increase in A6-01 |
| A1 journal | Actor/world/kind/canonical intent bound to original operation; atomic facts/projections/outcome; unknown commit resolved through original-ID lookup | Register exact A6 fact/version decoders before use; replay facts, never commands or external effects |
| A1 clock | UTC/high-water catch-up, historical effective versions, fixed-cadence system definitions, max64 systems, ordered bounded transactions | A6-02-01 adds arbitrary domain-deadline dispatch and paged completion without one clock registration per player/facility; see §8 |
| A2 money/FX | Ten existing currencies; immutable versioned publications, explicit quote consumption and per-currency rounding; historical reference independent of current denomination | A6-03-01 adds CHF as eleventh currency and future-only opening publication; preserve old publication decoders |
| A3 identity/accounts | `AccountCatalog` is player-only; shared open-account cap3; globally unique open currency per player; permanent primary; account initialization has no bank/kiosk identity | A6-03 extends cap to4, adds provider attribution/admission and system-account support without treating institutions as players |
| A3 authorization | `PartyAuthorization` permits trusted matching system actor/owner; denies ordinary organization ownership; detached grants from live server actor/session | Narrow institution authority and branch admission; no client-provided actors/grants |
| A4 settlement | Ordinary `ACCOUNT` leg requires an OPEN BANK row; known system roles are global issuance/clearing/fee. Cash availability protects active holds; funds/postings/history share a snapshot | A6-03 adds explicit institution cash/financial-account targeting; A6-08/09/10 add receivable/liability accounting, not a pretend existing loan API |
| A4 closure/rebase | Atomic funded closure with claims/holds/primary/world-currency protection; exact quote/revisions; rebase preserves raw history and retires currency collisions | Extend provider metadata, cap-sized queries, yield settlement and debt/card payment links; preserve existing settlement/rebase limits unless explicitly revised and verified |
| A5 cash/custody | 118 existing cash items/eight access-only cards, world/lot quantity conservation, protected physical delivery and ambiguity quarantine | CHF cash and issued tokens are additive; no cloned delivery or credit authority from old collectibles |
| A5 terminals/menus | Generation/revision/geometry/session/owner/reach admission, ATM bills-only/teller bills+coins, old dye variants and hard bounds10 currencies/118 denominations | Branded binding, kiosk kind/geometry, explicit old-placement migration and bounded11-currency/CHF-resource payload changes |

Source references: [identity](../../.codex/architecture/identity.md), [accounts](../../.codex/architecture/accounts.md), [postings](../../.codex/architecture/postings.md), [journal](../../.codex/architecture/journal.md), [clock](../../.codex/architecture/recovery-clock.md), [FX](../../.codex/architecture/fx.md), [custody](../../.codex/architecture/custody.md), [terminals](../../.codex/architecture/terminals.md), [menus](../../.codex/architecture/banking.md). Some older architecture sections describe earlier schema or pre-delivery states; actual source and [the current project index](../../.codex/PROJECT.md) establish baseline17. Do not downgrade source to stale prose.

## 3. Stable identities and value vocabulary

Reuse typed `WorldId`, player UUID/`PlayerId`, `SystemActorId`, `AccountId`, `OperationId`, `RequestId`, `TransactionId`, `LoanId`, currency and independent journal/economic sequences. Money is checked signed64 minor units with explicit currency; balances have role-specific permitted signs. Rates are exact decimals, not binary floating point. Precision, residual and quote rules are in the servicing contract.

Add distinct domain types; do not repurpose `CatalogId`'s company/security/exchange/listing variants:

| Type | Meaning / identity rule |
| --- | --- |
| `InstitutionId` | Immutable namespaced key from the manifest below, independent of display name and item/block ID |
| `NetworkId` | `economy:keystone`, `economy:orbit`, `economy:wayline`, `economy:ember`; exact approved APR/product metadata |
| `ProductId` | Institution + kind + supported network + policy version; no player balance or per-card UUID in catalog metadata |
| `FacilityId` | World-scoped UUID, typed loan or revolving identity; one borrower and contractual currency; loan adapters retain `LoanId` |
| `CardTokenId` | World-scoped UUID referencing one issued debit account or credit facility, owner, issuer, expiry and current status |
| `StatementId` / `ObligationId` | Stable facility + period/due ordinal identity; retries never create a replacement requirement |
| `CreditProfileId` | World + player key, absent until first actual credit issuance; one profile across all banks |
| `ReportId` | Original obligation/default + report kind; corrections reference it and retain original dates |
| `CollectionCaseId` | Defaulted facility case UUID, independent of agent entity UUID or creditor sale |
| `QuoteId` | World/actor-scoped server-issued immutable priced intent, expected revisions and expiry; never permission on its own |
| `BranchAdmissionId` | Ephemeral server-owned lease for one live kiosk/session/action; not a persisted substitute for current authority |
| `PolicyVersion` | Exact immutable algorithm/config/catalog version and effective boundary; not an arbitrary mutable tag |
| `DeadlineId` | World + target identity + event kind + ordinal + effective version; stable across crash/restart |

Persist generated UUIDs with the first corresponding fact/outcome. Deterministic system-account and scheduled-operation UUIDs use a documented domain-separated world/key/currency/event mapping fixed by their first implementation version; retain golden fixtures. A6-01 reserves the semantic keys, not an invented cryptographic authority mechanism. Reuse the existing A1 update-ID derivation where applicable. Journal request/operation/transaction IDs remain distinct; namespaces/keys fit existing96-character terminal and128-character journal bounds.

### Institution manifest

All bank keys below use the `economy:bank/` prefix. Each bank maps to its own trusted system party and own capital accounts; it is not owned by whoever places a kiosk.

| Key suffix | Approved display name | Base | Services |
| --- | --- | --- | --- |
| crownstone | Crownstone Bank | CAD | Tier1+2 |
| orchard_direct | Orchard Direct | CAD | Tier1 |
| tidecrest | Tidecrest Bank | CAD | Tier1 |
| crosswind | Crosswind Bank | USD | Tier1 |
| lantern_union | Lantern Union | USD | Tier1+2 |
| mesa_trail | Mesa Trail Bank | USD | Tier1 |
| asterbridge | Asterbridge Bank | GBP | Tier1+2 |
| greenhaven | Greenhaven Bank | GBP | Tier1 |
| meridian_grove | Meridian Grove | EUR | Tier1+2 |
| slate_harbor | Slate Harbor Bank | EUR | Tier1 |
| fjordlight | Fjordlight Bank | DKK | Tier1 |
| harbor_meridian | Harbor Meridian | HKD | Tier1+2 |
| jade_terrace | Jade Terrace Bank | CNY | Tier1 |
| southern_arc | Southern Arc Bank | AUD | Tier1 |
| fernreach | Fernreach Bank | NZD | Tier1 |
| cedar_parcel | Cedar Parcel Bank | JPY | Tier1 |
| lakeward_cantonal | Lakeward Cantonal Bank | CHF | Tier1 |
| newhaven | Newhaven Bank | CHF | Tier1 |
| atlas_reserve | Atlas Reserve | USD | Tier2-only |
| obsidian_capital | Obsidian Capital Bank | EUR | Tier2-only |
| alpine_vault | Alpine Vault | CHF | Tier2-only |
| kintsugi_trust | Kintsugi Trust | JPY | Tier2-only |
| mariner_crest | Mariner Crest | AUD | Tier2-only |

Reserve `economy:lender/ironbridge` (Ironbridge Lending) and `economy:collector/waystone` (Waystone Recovery) as internal service keys/display labels, with no additional customer banks/account slots. These internal descriptive labels add no product or game parameter. Their capital base/operating reporting currency is the server currency captured at activation; later rebase changes display/valuation, not raw seeded account currency. Banks' capital base currency follows the roster. System owner UUIDs are derived/persisted from world+institution/service key; do not use raw player UUIDs or admit organization actors.

The approved issuer/network matrix is in the banking contract; art variants derive from that matrix, not all23×4 combinations. Cap is four open BANK accounts total, unique currencies, including protected primary; TRADING, debt facilities and system accounts do not consume it. Credit facilities are capped at three, with owner-wide exposure as approved.

## 4. Canonical data and service boundaries

Names below describe public contracts, not promises that Java classes already exist. Expose immutable request/result/snapshot types through server-session services, with asynchronous completion; never expose raw SQL, financial handler callbacks or posting constructors to untrusted callers.

Every mutation binds: world; original operation and current attempt IDs; authenticated actor; service/kind/canonical version; target IDs; expected revisions; exact currency/amount; policy/catalog/denomination/FX revisions; required quote; and permitted delivery target. Include semantic business intent in canonical bytes, not mutable server object references. Branch/session authority is refreshed per attempt; a new live lease may admit the identical old canonical intent without changing its original operation. A consumed lease cannot authorize a different intent. Missing and foreign targets remain indistinguishable to unauthorized callers.

| Service / owner | Inputs and authority | Detached result and invariant |
| --- | --- | --- |
| `Institutions` / A6-03 | Catalog revision; trusted system activation; owner-scoped account/provider queries | Capabilities, currency, assigned/unassigned state and explicit unsupported products |
| `BranchAdmission` / A6-05-01 | Actual live player/menu/kiosk; action, institution, dimension, anchor, generation, revision, reach≤6 | Short-lived one-action lease checked again before final mutation; decoration never grants bank authority |
| `BankAccounts` / A6-03 | Initialize/open/assign/close/switch; fresh owner grant and matching bank lease; account/currency/revisions; closure destination/quote | Existing account IDs, provider/version and lifecycle result; cap/uniqueness/primary/holds/claims remain atomic |
| `TierPricing` / A6-04 | Current authoritative assets/liabilities/marks/FX; institution/product/action | Versioned eligibility, reason components, fee/deposit policy and quote; no stale/unavailable zero substitutes |
| `IssuedCards` / A6-06 | Owner, account/facility, issuer/network, initial/replace/renew/revoke intent, lease and fee quote where required | Token identity/status plus protected delivery obligation; no new facility or starter reset on replacement |
| `CreditProfiles` / A6-07 | First actual issuance or scheduled score cycle; immutable reports/exposure snapshots | Score/components/reasons and revision, or explicit unestablished/unavailable status |
| `CreditFacilities` / A6-08 | Kiosk application/increase, owner/network, approved limit; merchant/advance draw through live token/payment admission; repayment/autopay intent | Captured contract, committed available credit, bucket balances, statement/minimum/due and balanced draw/payment outcomes |
| `Loans` / A6-09 | Kiosk application, lender/product/amount/term; score/tier/prime/funding snapshots; payoff/repayment | Deterministic offer/rejection, fixed contract/schedule and exact revision-bound payoff; unsecured only |
| `DebtLifecycle` / A6-10 | Original obligation/deadline, current payments, immutable report/correction intent, creditor sale/funding facts | Orthogonal arrears/default/report/collectability snapshots, never a second debt balance |
| `Collections` / A6-10/11 | Fresh eligible case(s), creditor, funding account/payment quote; debtor-only agent menu | Partial/full settlement with current creditor; acknowledgement only for committed qualifying payment; no extra minimum |
| `Queries` / A6-12 | Owner grant or separately audited operator inspection; world/owner-bound exclusive cursor | Bounded raw-currency history and formatted summaries; no foreign-party disclosure |

Network packets carry action/selection IDs, bounded user amount text and current menu/session revision. Server resolves actor/world, offered target IDs, current bank, permitted action and exact amounts. No client-supplied score, Tier, APR, financial postings, policy version authority, custody proof or system grant. Direct trusted APIs must enforce the same restricted player lifecycle admission, not call old permissive helpers as a bypass. Audited migration authority is a separate capability, never a generic operator-player mutation override.

Canonical intents survive disconnects/unknown outcomes; menu result publication requires the current player object/session/audience. Original outcome lookup may succeed even when new actions are gated. Busy/replaced menus cannot duplicate submission chains. Payoff quotes30 real seconds and FX/valuation quotes60 or underlying expiry, with state/boundary invalidation, use the exact approved contracts.

## 5. Persistent entities, facts and states

All financial authoritative records live in the existing world store and journal. Tables/indexes/projections are bounded query aids with explicit replay validation; entity NBT, items, client screens and caches are not debt authority. Assign concrete SQL migration numbers only when implementing afterV17, preserving the whole pending-chain backup/atomicity gate.

| Aggregate | Minimum persisted content | States / dimensions |
| --- | --- | --- |
| Institution/catalog | Stable key/system owner, base currency, capability/network matrix, version/effective instant, activation manifest and cash-account mapping | `DECLARED → ACTIVATING → ACTIVE`; `DISTRESSED` cash-health flag does not erase contracts |
| Account provider | Existing account ID/owner/currency/primary/revision, institution binding, legacy marker, switch authorization | `UNASSIGNED_PROVIDER → ASSIGNED`; close/tombstone history preserved; switch pending/consumed/cancelled/expired |
| Terminal binding | Kind/world/dimension/anchor/generation/revision/owner, bank/base currency, prior settings and migration witness | `UNBOUND_BANK → BOUND_DISABLED → ENABLED → REMOVED`; binding immutable after enable per placement |
| Issued token | ID, owner, institution, product/network, account/facility, issue/expiry, replaced-token link, A5 delivery case | `ACTIVE → REVOKED` or `EXPIRED`; delivery is independent pending/delivered/quarantined; all copies share status |
| Credit profile | First-issuance instant, starter-used identity, score/revision, cycle input/component snapshots and inquiry groups | `UNESTABLISHED → ESTABLISHED`; closed cards never reset profile/starter |
| Facility | Borrower/original/current creditor, contract currency, principal/interest buckets/residuals, limits/rates/term/anchor, funding reservation, policy versions | Lifecycle `ACTIVE / DEFAULTED / SETTLED`; draw access `ENABLED / SUSPENDED / CLOSED`; arrears `CURRENT / DELINQUENT` |
| Statement/obligation | Facility/period, immutable original principal/interest/minimum/due, outstanding satisfaction requirements and allocations | `NOT_DUE / DUE / PARTIALLY_SATISFIED / SATISFIED`; no duplicate monetary liability for requirements |
| Report | Original obligation/default identity, snapshot instant/input, due/default/report expiry, corrective references | Visibility `REPORTABLE / EXPIRED`; factual status current/missed/default/paid-default; append-only correction |
| Collection case | Facility/default/case IDs, creditor, frozen debt components, sale intent/basis/discount, acknowledgement/expiry | Sale `PENDING / SOLD`; collectability `COLLECTIBLE / UNCOLLECTABLE`; payment `UNPAID / PARTIAL / SETTLED` |
| Agent presentation | Debtor/case group, presentation UUID/generation, last live dimension/attempt, kill/not-before cooldown, reconciliation flags | No presentation / live / suppressed / cooldown; financial state never inferred from absence/death |
| Scheduled progress | Stable event/deadline/version/target, effective instant, ordered stage, page cursor, input/frontier witnesses and terminal outcome | `PENDING / APPLYING / COMPLETE / BLOCKED`; deferred funded payout may complete work while retaining payable |

A paid default remains reportable until its original7-year expiry. A collectible case can exist before sale; payments follow the current creditor. Collection expiry stops demands/agent/payment acceptance without silently forgiving historical borrower liability; creditor write-off is separate. Death changes only presentation cooldown. A current score reduction blocks approved new exposure but cannot manufacture default or demand an unscheduled payment.

### Journal vocabulary and version ownership

Use service namespace `economy:a6` and journal record names `economy:a6/<fact-name>`; initial new records are version1 with registered historical validators. Canonical operation kinds use distinct `<aggregate>/<verb>` names. These are reserved names, not newly registered code. Existing v1 A1–A5 records remain unchanged; changed account/terminal semantics require explicit new record/canonical versions and retained old decoders.

| Fact family | Required facts / owner |
| --- | --- |
| Catalog and capital | `catalog-publication`, `capital-manifest`, `capital-allocation`, `institution-account` / A6-03 |
| Calendar/progress | `calendar-activation`, `deadline-progress`, `deadline-completion`, `policy-effective` / A6-02-01 and consuming owner |
| Accounts/terminals | `provider-assignment`, `provider-switch`, `branch-admission-use`, `terminal-bank-binding` / A6-03,05-01 |
| Cards/profile | `card-issuance`, `card-status`, `credit-profile-opening`, `credit-inquiry`, `credit-score` / A6-06,07 |
| Contracts/money claims | `facility-opening`, `limit-change`, `credit-draw`, `interest-accrual`, `interest-settlement`, `deposit-yield`, `yield-payment`, `repayment`, `rounding-waiver` / A6-04,08,09 |
| Servicing | `statement`, `obligation-satisfaction`, `delinquency`, `default`, `credit-report`, `report-correction`, `debt-sale`, `collection-acknowledgement`, `collection-expiry`, `creditor-writeoff` / A6-10 |
| Presentation | `agent-cooldown`, `agent-presentation-intent` / A6-11; committed intent is not proof of physical spawn/delivery |

Every financial fact includes target IDs, before/after revisions or validated effect, amount/currency/residual, effective instant, policy/input snapshots and linked balanced transaction. Clock-linked facts retain their event/update identity. Corrections append with original references; no UPDATE/DELETE/replacement of historical facts. Exact record encodings are sealed by their implementing package before its first commit, with golden vectors and bounded decoder tests.

## 6. Accounting composition and atomicity

Do not use the global issuance/clearing/fee account as a bank's own capital. Extend A4's trusted target resolver and typed financial-account registration to distinguish institution cash, cash reservation, receivable, borrower liability, accrued payable/receivable, expense/income, acquisition discount/allowance and equity. Only explicitly cash-like registered balances are spendable; a receivable/equity/income/memo balance cannot fund a draw. System accounts have world+system-owner+role+currency identity, no player cap, and separate allowed-sign/authority policy. Customer account-owner queries cannot expose system holdings.

Preserve per-currency balanced A4 postings and account availability for monetary cash legs. Claim/recognition entries use the same journal transaction through the new typed financial extension with explicit normal-balance/sign and per-currency balancing, not a writable side database. Existing `PostingBatch` roles must not be falsely claimed to support these entries already. Borrower liability and creditor receivable represent one linked claim: reductions, creditor change and source receipts reconcile atomically. Do not count recognition legs as newly issued spendable supply.

| Operation | Atomic commit contents |
| --- | --- |
| Capital opening | Original world/manifest identity + one-time issuance/treasury/equity facts + currency allocation/FX/residual + activation progress; total28B before conversion. Per-bank400M for18; specialist4B for5; subprime400M; collector400M. Publish ACTIVE only after complete manifest; no repeat seed after retry/migration/restart |
| Card issuance | Eligibility/exposure/capital reservation + token + optional one-time profile650/starter2500 + fee debit + A5 delivery obligation/outcome; first-profile/starter race resolves once |
| Loan origination | Underwriting/captured fixed contract + cash principal transfer + equal linked claim/liability + schedule + lender capacity/reservation effect + outcome |
| Card draw | Live token/authority + current facility/limit/exposure + actual lender cash reservation consumption + merchant/owned-account payment + separate bucket principal/claim + original operation; later physical cash withdrawal has its own A5 protocol |
| Interest/yield | Earned exact accrual/residual and contractual debt or funded-yield payable + versioned inputs/completion; no free cash. Yield payout separately transfers funded own cash and reduces payable once |
| Repayment | Funding debit/explicit FX fee and clearing + creditor receipt + interest/principal/discount allocations + requirement satisfaction + possible collection acknowledgement/settlement + outcome |
| Default/sale | Close new draw access/release unused reservation + freeze earned debt/original default dates + report/case eligibility; a funded sale changes creditor and seller loss/collector basis once without copying borrower debt |
| Closure/provider switch | Current A3/A4 claims/primary/restrictions + required kiosk admission + yield/debt/autopay/card link checks + provider/catalog/posting effects + old-token revocation and any delivery obligation |

All cash debits require available funds after holds, protected line reservations and approved operating-floor policy. Card default/permanent facility closure releases unused reservations; recovery receipts on a closed line do not recreate them. Customer deposits cannot be spent as bank capital. Multiple product currencies require funded institution treasury FX and actual contract-currency reservations, never an assumed1:1 conversion.

A failed collector purchase leaves a frozen original-creditor case serviced at zero agency fee; collection/default dates still advance. Insufficient yield funds persist a payable/distressed state and a durable deferred-payment result, not skipped accrual, silent minting or a permanently blocked clock. Financial closure must settle or explicitly preserve all affected payable/delivery/debt links; do not discard an account merely to remove them.

## 7. Security, outcomes and failure contract

Live server thread: resolve player/source, menu/container/session, geometry/generation/reach, permitted institution/action and detached observations. Storage worker: resolve current owner/revisions/policy/quotes and execute one atomic intent. Detached calculations may use the existing bounded calculation queue; revalidate prepared snapshots before commit. Server publication: recheck current audience/session; a stale UI cannot undo a commit. Never touch Minecraft objects or wait/join on storage from a game tick.

Preserve A1 outcomes: `NOT_ACCEPTED`, `ACCEPTED_PENDING`, `COMMITTED`, `DURABLY_REJECTED`, `UNKNOWN`, and authorized original lookup. Domain reason codes include `WRONG_INSTITUTION`, `KIOSK_REQUIRED`, `STALE_REVISION`, `QUOTE_EXPIRED`, `UNASSIGNED_PROVIDER`, `TIER_INELIGIBLE`, `VALUATION_UNAVAILABLE`, `CREDIT_UNESTABLISHED`, `CREDIT_DENIED`, `EXPOSURE_LIMIT`, `CAPITAL_UNAVAILABLE`, `COLLECTION_EXPIRED`, `UNSUPPORTED_PRODUCT`. They explain authorized business failures without revealing another owner's existence or balances. A structurally valid underwritten denial may record its approved inquiry; early malformed/unauthorized denial does not.

Replays return original immutable results, not current authority or fresh physical-delivery permission. Lookup conflict/unknown never permits a new operation to bypass uncertainty. Unauthorized lookup is not proof of absence. Disconnect removes waiting/publication, not committed finance. Preserve explicit amount/FX/quote expiry/currency errors; do not silently round unsupported cash, change a requested product or clamp configuration.

Owners see only their accounts/contracts/reports; operators require live audited inspection. Internal economic jobs use a narrow registered system actor, not a forged player. Privileged migration/maintenance has a separate reason/audit capability, not unrestricted endpoint access.

## 8. Scheduling, bounded work and readiness

Use the A1 UTC wall clock/high-water/worker/operation machinery. One financial day1,200s; week8,400s; month33,600s; year436,800s. Origins, first due dates, rate versions, compounding and same-instant order are exactly the servicing contract. Audit/journal/economic order stay distinct. No sleep, `/time`, elapsed-tick or local-time clock. Downtime and integrated pause create ordinary catch-up debt.

A6 introduces indexed domain deadlines at arbitrary origination-derived instants; existing fixed-cadence `EconomicClock` cannot be treated as an unlimited per-facility scheduler. A6-02-01 owns the extension: a domain deadline provider exposes its next due instant and ordered target keys to the same chronological dispatcher. Merge A6 due events with existing FX/system updates by effective instant and explicit stage order, preserving old fixed-system definitions, signatures and update identities. Do not shift a facility anchor to the next world-day boundary, register one system per facility, poll/commit every empty second or use a separate wall-clock thread.

For an event spanning many owners, persist `(event, effectiveInstant, stage, exclusiveTargetCursor, completedEffects)` and page it. Each child mutation has its own deterministic event/target operation and transaction; the event frontier advances only after all required child outcomes are complete. Page boundaries are not financial period completion. Target selection must account for entities created/closed concurrently under the same temporal ordering, without missing an obligation or executing it twice. New financial mutations cannot overtake overdue prior stages; authoritative lookup/diagnostics remain possible. Treat deferred funded payout as a terminal outcome for processing, retaining its independent payable.

Operational starting bounds: at most16 target children selected per page; at most one economic child commit per worker turn with normal FIFO fairness; select a smaller page when record/payload budgets demand it. Keep existing1–64 records/128KiB transaction payload,16KiB record/canonical/result limits,64 canonical fields,63-result public page plus lookahead,64 registered systems and existing worker queue budgets. No all-player atomic statement/score/seed transaction. Maintain revisioned owner-wide exposure/valuation input summaries from authoritative facts so a request never scans an unbounded portfolio; rebuild/validate summaries in pages at a fixed journal frontier, and recheck the owner input revision before publication. Long-history scoring/report folds also page with resumable input frontiers rather than assuming every owner has only a few loans. Catalog/capital activation stages25 system parties through a durable manifest. Measure affected fixtures before increasing a bound; no silent truncation.

The financial origin may activate before products. Undelivered domains stay dormant/unregistered, not installed with missing dependencies that block startup. A6-07 delivers scoring computation/profile contracts with production recurring scoring dormant; A6-10-03 installs/accepts reporting and recurring scoring together. Shared clock acceptance must prove arbitrary deadline dispatch before downstream product activation. No source adapter for holdings/margin means explicit unsupported capability, not invented zero assets or absent-liability assumptions.

Retain historical simulation/policy implementations for backlog. Backward UTC waits under the retained target; forward jumps create reported debt, never silently skipped periods. Startup restores projections and processes installed due work before publishing finance-ready. Unknown facts/version, corrupt financial state or unresolved required prior work fails closed; incomplete optional future products do not impersonate installed failures.

## 9. Migration and compatibility map

| Existing state | Approved transition / owner |
| --- | --- |
| V17 world/history | Append contiguous migrations under existing stopped-world backup gate. Register old/new fact decoders; preserve originals and all IDs. No seed on a missing/corrupt DB fallback |
| Ten-currency registry/publications | Add CHF at the approved future A2 boundary with game baseline1CHF=1.60CAD; explicit historical absence, no backward-generated CHF rows / A6-03-01 |
| Three-account bound | Change shared bound, schema/query/protocol constraints and test fixtures coherently to four total; retain currency uniqueness/primary protection / A6-03 |
| Existing BANK/primary | Add UNASSIGNED_PROVIDER without moving money or creating accounts. Owner selects matching-currency bank at its kiosk; existing safe permitted services retain versioned transition behavior / A6-03 |
| New primary/open APIs | No login/remote/generic-card implicit new primary. Matching-currency kiosk initializes it after existing world chooser. Close old public/trusted permissive player mutation paths; preserve audited internal migration capability / A6-03,05-01,12 |
| Rebased primary | Retain ID/provider/protection with explicit non-native primary exception. New primary currency follows authorized denomination; institution hardware keeps its native cash and explicit FX. Currency collision retirement revokes/reconciles affected debit tokens and retains historical provider/claim links / A6-03,06,12 |
| Existing cash/eight generic cards | Keep IDs/art and access-only semantics; no automatic debt, score, issuance or owner assignment. CHF/new issued products are additive / A6-03-01,05,06 |
| Old terminal colors/items/placements | Preserve IDs, geometry, owner/settings/NBT/inventory and pending custody. Add UNBOUND_BANK; finish old intents using stored semantics, then explicit owner binding/native allowlist confirmation; never guess a bank from color / A6-05-01 |
| Item/agent state versus DB | Reuse A5 reserve/save/commit/delivery/reconciliation evidence; quarantine unknown physical effects. Agent presentation rebuilt from eligible cases/cooldowns, not journal spawn replay / A6-06,11,12 |
| Future securities/prime/margin | Optional trusted versioned providers, with raw values+source/version/instant and explicit unavailable/unsupported results; A8/B2/B4/B6 own implementation later |

Provider switching is the approved two-kiosk seven-day authorization protocol, preserving account ID and existing debt creditor. An account used for autopay/funding must have links explicitly updated/cancelled on closure, not silently redirected. Existing closure protections apply even if an account is at the wrong bank; provider migration cannot waive claims or protected primary status.

## 10. Package ownership and acceptance handoff

A6-01 owns this contract plus source inventory, identities and migration boundaries. Main agent owns shared interfaces/integration and all sequential Gradle/game work; substantial delegated work must have explicit disjoint file ownership, allowed checks and acceptance, preserve others' edits and use project profiles. All later packages read this contract and only their relevant policy/upstream references.

| Package | Delivered contract to preserve / later acceptance target |
| --- | --- |
| A6-02-01 →02 →03 | Versioned origin/deadline dispatcher/config → read-only backlog diagnostics → exact-boundary/offline/restart/fairness acceptance |
| A6-03-01 | CHF registry/FX/cash resources; exact supported denomination/lot/custody and old-publication compatibility |
| A6-03 |23 institutions and2 service parties, system account/settlement extension,28B manifest, account cap/provider/branch authority contracts |
| A6-04 | Complete net-worth/liability provider snapshot; tier/pricing/yield; no debt-created wealth or unfunded yield payout |
| A6-05 /05-01 | Approved issuer/network/product resource matrix;23-brand ATM/teller/kiosk assets, live binding/geometry and legacy migration |
| A6-06 | Owner-bound card lifecycle and custody; replacements revoke all old token copies without duplicating a facility |
| A6-07 | One first-profile/starter, immutable input scoring/limit/underwriting policy and dormant recurring computation |
| A6-08-01 →02 →03 | Card buckets/draw/statement/schedule → reconciling diagnostics → numerical/security/recovery acceptance |
| A6-09-01 →02 →03 | Funded fixed loan/amortization/repayment → captured-contract diagnostics → zero-rate/early-payoff/final-rounding acceptance |
| A6-10-01 →02 →03 | Orthogonal arrears/default/report/case clocks → explainable audit → cross-product boundaries/sales/expiry and scoring activation |
| A6-11 | Debtor-only partial/full/exit agent UI, bounded presentation and persisted cooldown; no entity-driven debt authority |
| A6-12 | End-to-end authenticated menus, exact restricted lifecycle admission and public provider APIs; unsupported downstream capabilities honest |
| A6-13 | Integrated release acceptance and user-facing release documentation; later phases remain uninvoked |

Mandatory witnesses in their owning implementation package: simultaneous fourth/fifth account and first-card issuance; wrong-bank/same-currency kiosk rejection;28B seed total/dedup and multi-currency capital reservations; card two-bucket compounding/minimum cap;13-month loan amortization/segmented early repayment; same-instant autopay/report/default/expiry; two-year acknowledgement versus seven-year reporting; paid-default visibility; expiry/default reservation release; restore/uncertain commit and no duplicate physical cards/agents; below-threshold specialist access; legacy placement/primary rebase compatibility; bounded long-backlog processing.

A6-01 verification is document/source review only. No Gradle, runtime loading, migration or finance simulation result is implied. Runnable implementation deliveries still require the prescribed build, runClient help/dry-run and affected runtime smoke checks, working runServer, registered main mod source set and development dependencies. Use disposable fixtures and the [verification guide](../../.codex/VERIFICATION.md); one owner runs checks and records actual evidence. Stop after each package's handoff.
