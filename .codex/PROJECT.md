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
- `BankingConfig`: config/economy/banking.toml, default CAD and dated editable CAD-per-unit rates.
  CAD anchor fixed1. Deposits group bycurrency FLOOR; cash source debits CEILING; all checkedlong.
- Three BANK accounts max/owner, distinct currencies; primary/default-currency accounts protected.
  One separate CAD TRADING account. Account creation/closure UI only at tellers. DB also enforces
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
