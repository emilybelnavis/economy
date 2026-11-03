# Mandatory feature-to-phase reimplementation map

Every F-ID must be reimplemented in its assigned phase(s). Existing code is behavioral evidence only. Track design status, implementation status and eventual verification separately. No feature may disappear because the old project spans phases inconsistently. A deliberate change must identify the old behavior, proposed replacement, reason and approval status.

| Feature IDs | Primary phase | Follow-through and required behavior |
| --- | --- | --- |
| F01 | A1 | Rebuild platform, lifecycle, client boundary, runnable development tasks, mod registration and runtime dependencies; A8/B9 validate delivery |
| F02 | A1 | Persistence/thread/cache/recovery architecture; A4 money atomicity; B4 shared cash/custody settlement |
| F03 | A1 | Approved fresh financial data and no legacy importer; A1-01 alone may remove old root contents under the explicitly authorized reset policy. Runtime initialization never deletes/imports old databases. Rebuild explicit migrations for future redesigned schemas; A8 migration/restore acceptance. The old import capability remains inventoried but is an explicit approved exclusion. |
| F04 | A2 | Ten currencies, exact arithmetic, cash-value definitions and formatting; A5 physical items; A8 presentation |
| F05 | A2 | Configured baseline, conversions, rounding, recorded history and quote API; A7 simulation; A8 history UI |
| F06 | A2 | World selection/denomination and rebase contract; A3 account collision/protection; A4 atomic accounting; A8 chooser/admin UI; B1/B3/B4 market/history currency consequences |
| F07 | A3 | Ownership, primary/additional accounts, account limit and unique-currency/protection/closure behavior; A4 funded closure; A5 teller access; B2 trading-account replacement |
| F08 | A4 | Credits/debits, P2P, transfers, conversion; A5 payment workflows; B2/B4 trading funding and settlement |
| F09 | A4 | Durable audit, paired IDs, raw/display currency separation, history paging; A1 journal infrastructure; A8 history screens |
| F10 | A5 | All cash/card items, value, artwork variants, tabs, stack/access behavior and acquisition-policy decision; A2 currency definitions; A6 cards never imply credit automatically |
| F11–F12 | A5 | Full deposit and exact withdrawal behaviors, previews, enabled denominations, coin restrictions, reservation/refund and recoverable fulfillment |
| F13 | A5 | Owner/op settings, modes, currency/denomination whitelists and per-dimension machine identity |
| F14 | A5 | Full shop configuration, batch/component matching, underlying stock container, pricing, owner permissions, payment and item fulfillment |
| F15 | B1 | Preserve catalog identities/names/source provenance and seed-data workflow while separating companies/securities/listings; expand to 800 listings |
| F16 | B2/B4 | B2 persistent holdings/funding/queries; B4 buys/sells, execution and atomic settlement; replace unbounded implicit counterparty with explicit model |
| F17 | B3 | Daily history/chart data and price administration, then autonomous prices/OHLCV; B4 chart UI; B9 retention |
| F18 | A5/A8/B4 | A5 banking/terminal workflows; A8 polished banking/FX/history/chooser; B4 searchable trading/chart/funding UI; maintain clipping/localization/accessibility contracts |
| F19 | A1 | Network envelope and server-authority contracts; concrete banking validation A5 and trading validation B4; A8/B9 abuse/reconnect acceptance |
| F20 | A4/A5/A8 | A4 financial API and pay/balance/history; A5 shop pricing; A8 worldcurrency/general admin and public API stabilization; B4 market APIs |
| F21 | A5 | ATM/register/teller physical machines, recipes/colors/placement/loot; B4 trading computer; geometry may be prepared earlier but future functionality stays unavailable |
| F22 | A5 | Cash/cards/banking/register assets and generators; B4 computer assets; A8/B9 resource QA; no asset generation during planning |
| F23 | A1 | Plan verification infrastructure; each phase reimplements relevant coverage; A8/B9 recovery/integration/performance gates; run nothing now |

## Coverage ledger for each future phase

For each assigned feature record: current behavior; redesigned contract; originating F-ID; owned subprompt; upstream/downstream contract; acceptance scenarios; deliberate changes; pending decisions; implementation status; verification status. Each phase exit lists completed and still-deferred rows with an explicit owning later phase. UI polish can wait for A8/B9, but critical authorization, durability and usable milestone workflows cannot.

No Stage B cash database, alternate FX engine or direct balance edits. No loans bundled into A5 because a card says credit. No stock execution in B1 because source data already has prices. No scope creep into colony tax/government/warfare integrations.
