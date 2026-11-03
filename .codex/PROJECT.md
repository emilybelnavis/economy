# Economy project

## Identity

Group/package `ca.emilybelnavis.mcmods.economy`; mod/resource ID `economy`.
Minecraft1.21.1, NeoForge21.1.252, Java21, ModDevGradle2.0.148; version controlled in build.gradle.
SQLite JDBC3.46.1.0 is implementation + jarJar + additionalRuntimeClasspath for development runs.
All runs register main and dev-only GameTest sources. No integration mods are bundled.

## Finance architecture

- `storage/FinanceDatabase`: one FIFO executor and JDBC connection per world. All filesystem/JDBC
  initialization and queries run on that executor. WAL/FULL/FK/busy_timeout5000. Shutdown alone
  waits for drain. Balances/shares/order settlement/ledger updates use one transaction.
- Tables: accounts, append-only ledger, exchange_rates, historical_rates, stocks, stock_prices,
  stock_price_history, holdings, orders, trades, metadata. Ledger account/time and sequence indexes;
  transaction UUID ties paired movements. SQL triggers reject ledger UPDATE/DELETE.
- Account/holding LRU caches capped256 owners, invalidated only after commit and evicted at logout.
  FX/current price caches; no history cache. Only active in-flight orders enter the order cache.
- `FinanceService`: lifecycle, old SavedData export/import, ready gate, daily configured FX batches,
  retry after failure, async primary-account API. Captures Minecraft state on server thread.
- `api/EconomyApi`: future-based breaking alpha API, no blocking on tick thread. Raw history uses
  sequence cursors. Existing numeric legacy balances are preserved, not rescaled.
- `banking/BankData`, `Ledger`: legacy migration reader/test fixtures; no new live financial writes.
  Original file `emily_economy_bank` retained. Idempotent SQL marker prevents repeat imports.
  Legacy saved currency preserved per imported owner; new players use current configured currency.
- `FinanceDefinitions`: immutable cached bundled JSON currency/stock catalogs. JSON scale is a
  factor (100 or1); database scales are decimal places (2 or0), converted explicitly.
- `CashCatalog`: cached cash values from currencies.json, 118 cash items plus8 card items.
- `BankingConfig`: config/economy/banking.toml contains dated editable CAD-per-unit rates only; world choice is SQLite metadata.
  CAD anchor fixed1. Deposits group bycurrency FLOOR; cash source debits CEILING; all checkedlong.
- Three BANK accounts max/owner, distinct currencies; primary/default-currency accounts protected.
  One separate world/market-currency TRADING account. Account creation/closure UI only at tellers. DB also enforces
  ownership/count/duplicates/protection; closure settles FX and retires account, retaining ledger.
- `BankingMenu`: 24 transient deposit slots and36 inventory slots, HOME/DEPOSIT/WITHDRAW/HISTORY/
  ACCOUNTS/CONFIG pages. Card, owner, reach, terminal mode, whitelist and denomination checks.
  Async cash reservation/refund/delivery; request lock; previews server-owned; full requests bounded.
- `TerminalSettings`: nonfinancial SavedData owner/mode/accepted currencies/dispensed item IDs,
  keyed dimension+wide-machine anchor. Placer/op only; legacy unowned terminals operator-only.
- `TradingMenu`: funding/unfunding and whole-share buy/sell; FIFO immediate market settlement.
  Historical base prices remain fixed until explicit server API price update; no live feed.
- `FinanceAction`/`FinanceSnapshot`: menu-ID scoped bounded networking. Client never supplies
  balances/rates/prices; server revalidates accounts/items/quantities. Dist.CLIENT screens only.
- `RegisterEntity`: async shop payment with reserved stock and rollback, no blocking queries.

## Data and verification

`tools/collectibles.json` remains artwork source. Runtime currency definitions are bundled at
`data/economy/finance/currencies.json`. Stock definitions/provenance at stocks.json; importer
`tools/import_stock_prices.py`; sources and class choices in docs/stock-data.md.100/100 historical
closes2025-12-31, USD/CAD1.3706, HALF_UP CADcents; original Minecraft-style display names.
Standalone FinanceDatabaseChecks cover SQL rollback/auth/cash/FX, three-account rules, cache
publication, append-only history, migration, cursor paging, poisoned-connection handling and FIFO
concurrent settlement. GameTests cover cash grids, ATMs/tellers, ownership, codec bounds and
quantified withdrawals plus existing machine placement/loot tests.

Physical inventory files and SQLite do not share a crash transaction. Ordinary failures refund
reserved items; a crash between reservation/commit/delivery needs a durable inventory outbox
not implemented here. This limit must remain visible in release notes.

## Machines and artwork

17 ATMs and teller/trading workstations are 2w×2h×1d four-tile machines. `WideMachineBlock`
handles placement/support/anchor cleanup and exact generated collision shapes. Registers remain
single-block and support17 accent variants. Resource IDs/textures unchanged. All36 inventory
models remain centered. Generators: generate_terminal_variants, generate_wide_machines,
generate_machine_shapes, center_item_models; immutable model_sources preserve art geometry.
Ten currency creative tabs plus blocks and cards. Original assets and localization remain
separate from generated data. Read scoped Java/resources AGENTS before changes.

## 2026-11-03 world selection, presentation and withdrawals

World currency moved from TOML to SQLite metadata. New economy world remains unselected;
first join-event elects selector synchronously before futures complete. WorldCurrencyMenu/client
WorldCurrencyScreen prompt cannot be dismissed ordinarily; chooser can be replaced on disconnect.
WorldCurrencies owns operator/cheat command `/economy worldcurrency CODE`, requiring actor-bound
same-source/same-target double confirmation within1200ticks via tested CurrencyChangeConfirmation.
Old financial worlds infer their saved denomination; startup never converts existing money/prices.
Initial empty-world selection converts CAD seed prices only. Explicit rebase handles world-bank,
trading, current market/active order prices, target bank collision merge and frozen history/chart
projections in one checked SQL transaction. Optional foreign accounts remain; share counts remain.
Raw ledger/order/trade source currency persists. ledger_display_values is a separate mutable
materialization; displays remain fixed across subsequent FX changes. Ledger/day projection
migrations use1000-row keyset batches; standalone2105ledger/2002dayprice fixtures verified boundaries.
Only successful commit publishes cached currency directly, without a fallible follow-up query.

Money.format implements requested symbols/suffixes for all10currencies. History presentation
adds short Cash Deposit FX memos, UTC second dates and Credit/Debit;15-entry backend pages,
scrollable6-row viewport. FinanceButton/FinanceEditBox provide flat dark/teal themed native controls.
TradingScreen uses scrollable11-row list and separate selection/chart/buy/sell pane, with up to14
in-game days from daily_stock_prices. Daily records carry last known recorded values forward;
no pre-history data is invented. Independent legacy market denomination is retained until
explicitly aligned; Order.currency prevents falsely relabelling old order prices.

WithdrawalPlanner computes exact plans on CPU workers; SQL remains on its writer. ATM amount
must be a multiple of smallest enabled bill AND exactly representable. Teller maximizes bill
coverage, minimizes bill count, then minimizes automatic coin change. Clients cannot edit coins.
Prepared server plan, account, terminal settings and quoted cost are revalidated on commit.
Bill edits expose feasible directions and skip impossible quantities. DP bounds:1million scaled
states and2304pieces; overflow and oversized requests reject. New GameTests cover amount-first,
20/50 noncanonical combinations, impossible edits, automatic teller coins and coin tampering.

## 2026-11-03 exchange terminal history page and clipping

FinanceEditBox now uses native getInnerWidth minus8, a pose inset and an inner scissor instead
of temporarily mutating layout bounds. Native cursor scrolling/click mapping matches the inset;
long hints/suggestions cannot spill across controls. FinanceButton also clips its label.
TradingScreen reserves separate stock/rate tabs, search, list/chart, trade and funding bounds;
funding uses a short Amount hint and wider field. Stocks show9scroll rows below the tabs.
RATES page selects from/to pairs and displays current cross rate plus8visible rows from15date
pages with Newest/Older. Stale pair snapshots are hidden; server revision guards prevent old
responses overwriting new choices. TradingMenu issues historical SQL reads only on RATES.
FinanceDatabase.exchangeRateHistory uses bounded prepared SQL joins, exclusive ISO date cursor,
12-place HALF_UP display ratios and implicit CAD1 reference for partial batches. Historical
records are never cached or synthesized from current rates. New ExchangeHistoryChecks verify
cross/inverse/same/reference pairs, missing dates, old/current separation and paging. Added
GameTest verifies rates page/cursor/return to100stocks/reach rejection using a fresh fixture.
