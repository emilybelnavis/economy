# Design checkpoint — domain, invariants and architecture

Act as the economy mod's domain architect. Prepare a design specification and a sequence of future prompts, not code. Do not run builds/tests, generate assets, migrate data, or modify runtime files. Read only explicitly supplied planning files and relevant source evidence.

We will reimplement every existing feature in the phase assigned in the feature map. None of the old banking, persistence, cash, shop, stock, UI, asset, command or API implementation marks a redesigned phase complete. Preserve the inventory separately from decisions about implementation reuse.

## Required outputs

1. A glossary distinguishing owner, organization, account, currency balance, posting, journal transaction, hold, payment, loan, collateral claim, company, security, listing, custody position, order, execution and settlement.
2. Logical module ownership, public service boundaries, atomic settlement coordination, and dependency direction. Banking owns money; custody owns shares and return obligations; trading cannot mutate either directly.
3. Financial invariants: balanced postings per currency with explicit clearing accounts; checked fixed-point arithmetic; no unauthorized negative available funds; reserved funds cannot be spent twice; no duplicate request effects; balanced securities movements and lending inventory; no acknowledgment before durable commit.
4. A persistence design comparing journal authority and balance projections, schema versions, restore/replay, deterministic simulation recovery, and backup consistency. SQLite is now explicitly approved, with the contracts in the A1 prompts and decision register; do not re-run storage selection.
5. Server-thread/worker ownership, immutable inputs/results, bounded queues, overload behavior, shutdown and callback publication. Off-thread persistence must not force tick-thread waits.
6. A crash matrix for cash deposits, withdrawals and shop fulfillment. Analyze reservation, financial commit, item delivery, Minecraft save, reconnect and retry. Do not claim an outbox alone makes Minecraft inventory and database atomic; specify deduplication and reconciliation evidence.
7. Clock semantics for wall time, economic time, game days, sleep/time changes, restarts and offline catch-up.
8. A proposed gameplay model covering account access, money issuance, cash supply, merchants, economic sinks, loans, counterparties, defaults and terminal access.
9. Existing-world, registry-ID, artwork and behavior compatibility choices, with explicit approval needed before destructive migration work is ever requested.
10. The decision register, dependency graph, phase gates, measurable performance budget proposals and a bounded prompt breakdown for A1.

Separate source facts, user requirements, recommendations and unresolved choices. Ask only consequential questions; give concrete alternatives and implications. Do not invent approved values for performance, fees, interest, leverage, exchange allocation or recovery guarantees. Future runnable deliveries must include working runClient/runServer wiring, mod registration, runtime dependencies and runClient verification; do not execute any of that during planning.

## Approved foundation baseline — 2026-11-03

All five A1 design packages were approved in the planning chat. Design approval is not implementation authorization, a completed implementation, or verified test evidence.

- Fresh economy data; no legacy importer. The historical A1-01 one-time root reset is complete; no other execution or runtime initialization may wipe data. Future databases created by the redesign still require schema upgrades.
- One Minecraft server solely owns each financial world. SQLite at <world>/economy/economy.sqlite; one bounded storage worker and owned connection. No shared multi-server economy or multiple storage backends initially.
- Authoritative immutable journal; projections, operation identity and durable outcome commit atomically. Accounting owns money; custody owns securities; trading coordinates through their services. Checked arithmetic, per-currency balanced postings and reconciled securities are later-phase domain invariants.
- Real-world elapsed time, including all offline time, processed gradually without truncation. Gate affected financial actions until relevant catch-up completes; ordinary Minecraft play can continue. Sleep and /time do not advance economic time.
- Expected maximum 15 simultaneous players. Target: process seven offline days within 30 seconds at the eventual 800-listing scale. This is a future performance target, not a verified guarantee or deadline permitting skipped effects; longer absences may take longer.
- Complete-world stopped-server backup/restore initially. Leave an extension boundary for online backups; do not implement online backups in A1.
- All existing gameplay is reimplemented in its assigned phase. No currency/accounts/money movement/cash/shops/stocks in A1. Existing functionality does not mark a redesign package complete.
