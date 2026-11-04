# A5 implementation plan

Status: proposed. A4-06 is verified. Package IDs below are reserved for separate
execution after their contracts are accepted. This plan changes no gameplay.

## Dependencies and ownership

- A1 owns the world-scoped journal, operation lookup, recovery/readiness and
  worker queue. A5 owns commerce workflow records and Minecraft custody.
- A2 owns currencies, published FX and rounding. Deposit floors each original
  currency subtotal and returns skipped groups. Withdrawal ceils the account
  debit. Bind publication, denomination and applicable pricing revisions to
  each preview. Reject missing rates, overflow and stale bindings.
- A3 owns party grants, first-interaction primary initialization, account
  eligibility and revisions. A5 authenticates the teller interaction before
  requesting account lifecycle actions.
- A4 owns postings, funds, holds, quote consumption and ledger history. Bind
  trusted journal registrations with live authority. A5 owns invoices, receipts,
  expiry policy and delivery. Confirm the composition of hold capture, merchant
  credit, fee/FX postings and receipt in one transaction before A5-01 begins.
- Later phases own share custody, returns on shares and trading computer
  activation. A5 supplies a commerce API without a separate banking database.

## Rules shared by every package

Target Minecraft 1.21.1, NeoForge 21.1.252 and Java 21. Support integrated and
dedicated servers. Resolve world, actor, reach, menu, terminal and current
configuration on the server thread. Snapshot bounded inputs for worker jobs.
Commit persistent facts on the world worker and apply inventory/menu changes
on the server thread after rechecking the session. Committed obligations survive
disconnect. Return typed pending, conflict, stale, unauthorized, capacity and
recovery outcomes without exposing another party's records.

Bind each mutation to its original operation ID and canonical payload. An
identical retry reads the prior result. A different payload conflicts. Network
attempt IDs are separate. Use checked minor-unit arithmetic and reject overflow
before effects. Version persisted facts and configuration, and give diagnostics
stable reason codes and operation references. Validate persisted state during
startup before opening affected workflows. Expose bounded pages and queue work.
Expiry, scan limits and permission defaults require explicit configuration
decisions. Keep diagnostics free of unrelated account or inventory contents.

## Coverage

| ID | Prior behavior | Proposed contract and owner | Acceptance and handoff |
| --- | --- | --- | --- |
| F10 | 118 cash items, eight single-stack cards, twelve tabs, alternate CAD bill art, no collectible recipes | A5-03 preserves identities, denomination mapping and generic banking access without credit | Count registry and tabs, check art and card access, hand stable identities to A5-05/06 |
| F11 | 24-slot deposit, preview, reservation and returns | A5-05 preserves counts, subtotals, ATM bills and teller bills/coins, retains original skipped cash | Close, change page or disconnect during preview/commit, reconcile pending value; A5-09 tests real menus |
| F12 | Exact amount-first withdrawal, editable bill mix, automatic teller coins | A5-06 uses bounded exact planning and immutable quote binding | Check 20/50 bills, infeasible totals, forged edits and stale settings; hand delivery to A5-02 |
| F13 | Owner/op modes, accepted currencies and denomination whitelist | A5-04 revisions settings by dimension and anchor | Deny unauthorized changes, invalidate previews after edits and anchor replacement |
| F14 | Register template/batch, component match, stock below, seller primary, rollback/drop | A5-07 reserves exact stock and binds payment to seller and price revision | Race for last batch, reject component mismatch, test rollback and delivery fallback; preserve lack of claim integration |
| F18 | Banking, setup, accounts/history and FX screens | A5-09 exposes banking portions using authoritative A2–A5 snapshots and preserves dark/teal controls and clipping | Exercise onboarding and pagination; stock list, chart, trade and trade funding go to trading phase |
| F19 | Scoped bounded packets, server validation, busy/stale guards, client-only screens | A5-09 enforces session, menu, revision and request bounds | Reject replay after menu reuse, forged quantities and remote interaction; verify dedicated startup |
| F20 | Shop price command and server-thread callback API | A5-07 shares owner/config checks with register UI; A5-01 supplies commerce API over A4 | Command and menu agree on price revision; existing ledger commands stay with A4 |
| F21 | 17 ATM and 17 register variants, teller, four-cell machines, single-block register | A5-08 preserves support, rotation, cleanup, one drop, recipes, recoloring, tags and loot | Test orientations, partial placement and each broken cell; trading activation is B4 |
| F22 | Original banking art, localization and asset tooling | A5-03 owns collectibles, A5-08 machines, A5-09 screens | Compare models, centered inventory views, language keys and asset provenance |

## Ordered implementation packages

Each package starts with its listed dependencies and the shared rules above.
Verify its own gate and record a handoff before invoking the next package.

1. **A5-01 Payment lifecycle and merchant API** (A4-06). Persist merchant
   identity, invoice terms, expiry, hold reference, postings and receipt.
   Proposed first version permits one full capture or cancellation per
   authorization and one full refund after capture. A refund is a new linked
   operation against frozen original terms. Define fee ownership, FX reversal,
   funding and expiry rules before coding. Only authorized participants may
   act or read. Gate on duplicate/concurrent transitions, insufficient funds,
   restart expiry, unauthorized receipt lookup and stale or consumed FX quotes.
   Exclude inventory and screens.
2. **A5-02 Custody and recovery** (A5-01). Persist reservation identity,
   authenticated original intent, expected stack components and counts,
   source/location, save witnesses, financial result, intended delivery ID and
   acknowledgment. Bind financial result and durable delivery intent to one
   journal transaction. Reconcile reservation without commit, commit without
   delivery, delivery without acknowledgment, acknowledgment without matching
   save, and inconsistent restores. Inspect bounded known locations, including
   player, container, drop and unloaded-chunk cases. Quarantine ambiguity and
   record audited operator resolution. Gate on restart at every boundary and
   preservation of value and identity across retry. Exclude terminal policies.
3. **A5-03 Cash and cards** (A5-02). Rebuild registry, tabs, CAD variants,
   localization and server-side access eligibility. Define issuance and
   authenticity for all denomination identities, including compatibility with
   stack splitting/merging and previously saved items. Gate on complete catalog
   and invalid or replayed monetary identities. Exclude settlement menus.
4. **A5-04 Terminal policy** (A5-03). Store world-scoped dimension/anchor
   settings and a placement generation with owner, revision, mode and
   currency/denomination lists. Validate permissions and bounds on every
   configuration request. Gate on stale edits, ownership, replacement and
   config reload. Exclude geometry and cash settlement.
5. **A5-05 Deposits** (A5-04). Bind 24-slot inputs and frozen account/terminal
   revisions to the quote. Reserve originals before posting and return skipped
   groups or rejected reservations via custody. Gate on mixed-currency rounding,
   ATM coin rejection, page/close return, disconnect and restart with held items.
6. **A5-06 Withdrawals** (A5-05). Bind selected amount and feasible
   denomination mix to account, terminal and FX revisions. ATM checks bill
   multiples then exact feasibility. Teller maximizes bill value, minimizes
   bill count among feasible plans, then fills with coins. For enabled 20/50
   bills, 60 uses three 20s, 70 uses one of each and 30 is infeasible at an ATM.
   Validate edits server-side and derive coins automatically. Reserve intended
   delivery before debit. Gate on those examples, forged edits, full inventory,
   stale plans and bounded solver work at configured limits.
7. **A5-07 Register sales** (A5-06). Snapshot exact component match and batch
   size, seller primary, price and source container. Require inventory capacity for the full batch at final server-side admission.
   Reject insufficient capacity before charging or removing stock. Reserve
   the full batch before settlement, recheck container identity and price
   revision, then use
   A5-01 and A5-02 for payment and delivery. Gate on concurrent last-stock
   purchase, missing seller account, broken/replaced source, full inventory
   and rollback. Verify that an inventory with room for only part of the batch
   leaves buyer funds and stock intact. Retire automatic purchase drops.
   Use the approved claim policy for known undelivered goods after settlement.
   Exclude external claim-protection integrations.
8. **A5-08 Blocks and assets** (A5-07). Rebuild machine placement, shapes,
   anchor resolution, break behavior and assets. Use placement generation to
   prevent stale actions after replacement. Gate on geometry/GameTests,
   recoloring, loot and exactly one block drop on teardown.
9. **A5-09 Menus and phase acceptance** (A5-08). Wire player interactions to
   services, implement bounded revisioned snapshots and reject stale responses.
   Run integrated/dedicated workflow checks, crash and restore matrix, and
   bounded-work checks against configured limits. For runnable delivery run
   build, runClient help and runClient dry-run, and verify main source-set
   registration and required dev dependencies for client/server.

## Decisions requiring approval

| Decision | Recommendation and consequence |
| --- | --- |
| Partial and repeated capture/refund | Start with full transitions. Partial transitions require remaining-amount tracking, cumulative rounding and more recovery cases. |
| Fees, FX and refund funding | Freeze original fee and FX terms. Define who funds a refund if seller proceeds are spent and whether fees return before authorizing refunds. Live FX introduces exchange gains/losses that need an explicit owner. |
| Expiry | Persist an economy-clock deadline and release holds through a journaled bounded worker job. Decide pause/catch-up behavior and deadline ordering against capture. |
| Ambiguous delivery | Quarantine and require audited reconciliation. Automatic redelivery or compensation without conclusive evidence can duplicate value. |
| Delivery fallback | Approved. Reject purchases and withdrawals that cannot fit at final admission. Use a durable claim for known undelivered items after settlement. Automatic register drops are retired. |
| Cash identity and legacy items | Prototype a stack-compatible serial/quantity model and test split, merge, duplication and save restore. Approve treatment of unmarked legacy cash before enabling conversion or redemption. |
| Commerce trust and capacity | Limit mutation to registered trusted integrations and authenticated live grants. Approve merchant enrollment, queue and scan limits, solver cap and operator recovery permissions before public exposure. |

Approved inputs are the verified upstream contracts and required legacy feature
coverage. The transition model, custody guarantees, fallbacks and defaults in
this plan are proposals. Physical delivery across independent saves is verified
only to the extent demonstrated by the future recovery matrix.

## Execution splits

The first subpackage owns the underlying state transitions and policy.
Diagnostics consume its stable outcomes and read models. Acceptance verifies
the two preceding deliveries together. Execute each split in numerical order,
with the parent complete only after acceptance. Unsplit packages carry their
local configuration, rejection reasons and targeted checks in one delivery.

| Package | Execution boundary |
| --- | --- |
| A5-01 | **A5-01-01 Scheduling and configuration:** invoice/hold transitions, expiry admission and bounded release, merchant and pricing policy, receipt/API contracts. **A5-01-02 Diagnostics:** authorized invoice/hold inspection, overdue backlog and release failures, audit correlation. **A5-01-03 Foundation acceptance:** capture/cancel/expiry races, replay after restart, full refund funding and FX/fee conservation, scoped receipt access. |
| A5-02 | **A5-02-01 Scheduling and configuration:** custody facts, startup readiness, bounded reconciliation, quarantine and authorized resolution. **A5-02-02 Diagnostics:** operation-level witnesses, quarantined cases, backlog and scan-limit visibility. **A5-02-03 Foundation acceptance:** interrupt at each persistence boundary, restore mismatched saves, exercise moved items and unavailable chunks, prove retries retain original identity. |
| A5-03 | Keep together. Catalog, stack/authenticity policy and asset checks share one boundary. Gate on the approved legacy policy before redemption is enabled. |
| A5-04 | Keep together. Owner configuration, revision invalidation and rejection reasons belong to the same settings transaction. |
| A5-05 | Keep together. Deposit inherits recovery scheduling and diagnostics from A5-02. Verify reservation, skipped returns and settlement as one workflow. |
| A5-06 | Keep together. Planner bounds, rejection reasons and exact delivery form one workflow using A5-02. |
| A5-07 | Keep together. Register sale inherits expiry and custody infrastructure. Keep its price/stock policy and race checks with the sale workflow. |
| A5-08 | Keep together. Geometry, variants, assets and GameTests share the block lifecycle boundary. |
| A5-09 | Keep together as UI integration and phase acceptance. Exercise A5-01 and A5-02 diagnostics through real player workflows; run the release checks once. |

A5-02-03 uses controlled custody fixtures. A5-09 repeats the relevant crash
cases through deposits, withdrawals and register sales. A5-01-03 may use a
controlled clock so deadline races are deterministic.

## Decision checkpoints

| Needed before | Question for the project owner | Proposed starting rule |
| --- | --- | --- |
| A5-01-01 | Should the first release allow partial captures/refunds? | Full capture and one full refund only. |
| A5-01-01 | Who can create merchant invoices, and who can issue refunds? Should player-owned registers qualify automatically? | A register owner configures sales for that register. Trusted integrations need explicit grants. Merchant or authorized operator initiates refunds, with audit. |
| A5-01-01 | Do purchases charge fees or support cross-currency checkout in the first release? If enabled, who pays, and are fees returned on refund? | Start with zero fee and same-currency checkout. Preserve frozen original terms for future FX. If FX is required at launch, define fee payer, destination, reversal postings and who covers any shortfall before enabling it. |
| A5-01-01 | If a seller spends proceeds, should a refund fail, wait, or be covered by someone else? | Reject for insufficient refundable funds and allow retry with the same refund intent once funded. Define retryable admission separately from an accepted journal mutation. |
| A5-01-01 | How long should an authorization reserve funds, and should downtime count? | Use a persisted economy-clock deadline, reject capture at or after it, and release via bounded catch-up. Confirm the desired duration and player-visible downtime behavior before setting defaults. |
| A5-02-01 | Where should committed purchases or withdrawals go if a player disconnects or has no space? Is a claim screen acceptable where a ground drop cannot be reconciled? | Approved. Reject when the full batch or withdrawal cannot fit at final admission. Use a durable claim for known undelivered items after settlement and operator review for uncertainty. |
| A5-02-01 | Who can resolve quarantined cases, and may they redeliver, compensate or close a case? | Restrict to authorized operators. Require a recorded reason and evidence for any value-changing resolution. |
| A5-03 | Should cash saved before A5 remain redeemable? If so, how should worlds distinguish legitimate old cash from copies? | Ask whether any surviving world contains cash players expect to keep. Gate redemption until a migration or explicit collectible-only policy is approved. Preserve item identities and art. |

Implementation owners can establish queue/page bounds, solver limits and scan
budgets from fixtures and measurements. Use existing configuration conventions
and document the chosen limits at each handoff. Before implementation, the
owner of A5-01 must inspect the trusted journal composition and specify any
needed A4 extension. Before enabling cash or goods, the owner of A5-02 must
verify which Minecraft save witnesses are actually observable. These are
technical investigations. Escalate only if the findings force a change to
approved gameplay or recovery policy.

### A5-01 approval record

The project owner approved the A5-01 proposal. The first release supports one
full capture and one full refund, same-currency checkout and zero fees.
Register owners configure sales for their own registers. Trusted integrations
require explicit grants, and merchants or authorized operators may initiate
audited refunds. An underfunded refund can be retried with the same intent
once funded. Authorization uses a persisted economy-clock deadline, rejects
capture at or after that deadline and releases holds through bounded catch-up.

The approved authorization duration is five minutes, including server
downtime. On restart, capture rejects at or after the persisted deadline,
even if the bounded catch-up job has yet to release the hold. Confirm atomic composition of capture, postings and receipt
before A5-01-01 implementation. This approval covers A5-01 only.

### A5-02 delivery approval

Check inventory capacity before charging for a register purchase or debiting a
withdrawal. Reject for insufficient space and allow the player to retry. If
capacity disappears or the player disconnects after settlement, create a
durable claim for items known to be undelivered. Route uncertain delivery to
operator review. Only authorized operators may resolve a case. They may
close it when evidence confirms delivery, or redeliver or compensate when
evidence confirms loss. Record the evidence and operator’s reason for each
resolution. Cases with inconclusive evidence remain open.

### A5-03 legacy cash decision

The earlier mod stayed in IDE development. A5 has no released legacy cash to
migrate. Preserve the existing item identities and art. Only cash recognized
by A5's issuance protocol is redeemable. Treat unmarked development items
as collectibles and preserve existing development worlds and backups.

### A5-01 transaction checkpoint

Source review confirmed that JournalEngine writes journal facts, applies
projections and records the operation outcome within one store transaction.
Holds.capture already pairs hold transition and settlement projections, but
its result contains only the hold outcome. A5-01-01 needs a trusted capture
registration that composes the existing hold and posting logic with the
invoice transition and receipt fact/projection in a single journal commit.
Keep the existing standalone hold behavior. Verify atomic rollback by
injecting a failure in the receipt projection. This checkpoint is a source
review; transaction behavior with the new receipt remains an acceptance test.

### Outstanding phase readiness items

- **Register currency compatibility (A5-07).** The approved same-currency
  rule must apply to buyer and seller primaries. Specify the rejection and
  player guidance when they differ, including after a denomination change.
  Banking deposit and withdrawal FX retains the approved A2 rules.
- **Deposit returns and claims (A5-02/A5-05/A5-09).** Define where skipped
  cash and cancelled reservations go if their original slots are occupied.
  Specify claim discovery, ownership, retention and retry after another full
  inventory. Route uncertain delivery to operator review.
- **Cash authenticity (A5-03).** Prove how authentic quantities survive
  stack split, merge and save/restore and how duplicate copies are detected.
  Define issuance admission and recovery of debit-to-issuance before
  enabling redemption. Keep unmarked development items collectible.
- **Clock and retry details (A5-01-01).** Bind five-minute deadlines to a
  persisted time source that accounts for downtime. Specify behavior at
  startup, at the exact deadline and when wall time moves backward. Keep an
  underfunded refund retryable without writing a terminal operation outcome.
- **Live custody boundaries (A5-02/A5-07/A5-08).** Establish what can be
  proved when a hopper moves stock, a container or terminal breaks, a chunk
  unloads, or a player dies during a pending action. Identify affected items
  without scanning the whole world. If proof is unavailable, use the
  approved quarantine policy. Test a paired restore and each skewed restore
  direction.
- **Acceptance handoff (each package).** Before invocation, expand the
  selected package into an executable prompt with exact prerequisites,
  affected contracts and checks. Add receipt rollback, retry and expiry
  boundary tests to A5-01. Include claim collection and stale terminal
  sessions through the normal UI in A5-09. Keep production cash and sales
  gated until their custody and integration checks pass.

### A5-06 withdrawal capacity requirement

Require space for the entire exact withdrawal in the player's inventory.
Check capacity during confirmation and revalidate it at final server-side
admission before submitting the debit. If the cash cannot fit, reject the
withdrawal without debiting the account or issuing cash. Gate on insufficient
space at confirmation and capacity lost between preview and admission.
The approved custody recovery policy covers interruptions after admission
and settlement.

### A5-05 deposit return approval

Return original cash from cancelled deposits and skipped groups directly
when it fits. Put any remaining cash known to be undelivered in a durable
claim. Route uncertain delivery to operator review. Verify partial capacity,
full inventory and claim collection without duplicating original cash.
