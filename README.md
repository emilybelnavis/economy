# Economy

Version **0.1.0** · Project ID `ca.emilybelnavis.mcmods.economy`

An early playable economy mod for **Minecraft 1.21.1**, **NeoForge 21.1.252**, and **Java 21**.
Install the JAR on both the server and clients. No additional mods are required.

## Banking and terminal screens

Carry any bank or credit card to use your own accounts. Cards do not provide borrowing or overdrafts.
Every new player starts with one permanent bank account in the world's selected currency.
At a **bank teller**, open up to two additional accounts in other currencies: three bank accounts
maximum, with no duplicate currencies. Only tellers can open or close accounts. The original
primary account and the world-currency account cannot be closed. Closing a funded secondary
account requires another owned bank account; debit, conversion, credit and ledger records commit
in one SQLite transaction. Amounts too small to survive conversion are rejected intact.

Right-click an ATM or teller and choose a page:

- **Deposit:** a 6×4 cash grid, destination-account selector, paged counts and totals per item,
  native-currency **Subtotal**, and converted **Deposit total**. Hover summaries for full details.
  Shift-click cash from your inventory or drag it into the grid. ATMs reject coins; tellers accept
  bills and coins. The accepted-currency whitelist also applies. Cash still in the grid is returned
  when leaving the deposit page or closing the screen.
- **Withdraw:** choose the source account and cash currency, enter the cash amount, then prepare
  a combination. The server finds the fewest enabled bills for the bill-covered amount. Adjust
  bill counts using only feasible exact combinations; the displayed account debit includes FX.
  ATMs require a multiple of their smallest enabled bill, and an exact bill-only combination.
  Teller coin change is calculated automatically with the fewest enabled coins; coin quantities
  cannot be edited. Sufficient funds, inventory space and current terminal settings are rechecked
  before the prepared plan commits.
- **History:** 15 records per page, newest first, in a scrollable log with stable sequence cursors.
  Tooltips show UTC `YYYY-MM-DD, HH:mm:ss`, Credit/Debit, transaction amount, resulting balance,
  and a short memo. Cash deposits show `Cash Deposit` and any source currency/exchange rate.
  The full financial audit remains in SQLite; human tooltips omit database implementation text.
- **Accounts:** teller-only opening and closure, destination selection and conversion preview.

An empty-hand right-click opens **Setup** for the terminal's placer or a server operator.
Other players cannot configure it. Set deposit-only, withdrawal-only or both; choose accepted
currencies; and enable individual dispensed denominations in any supported currencies. New
machines dispense the selected world currency by default and accept all supported currencies. Old
machines without recorded ownership can be configured only by operators. Settings apply from
all four machine sections and persist by dimension and anchor position.

### Selecting and changing world currency

For a new economy world, the **first player to join** receives a currency-selection screen.
The choice applies to that world and persists in SQLite. Other players cannot race the elected
player's selection. If that player leaves before choosing, another online player takes over.
Existing worlds with financial data retain their previous denomination when upgrading.

Changing this choice later is strongly discouraged. Back up the world, then an operator
(permission level 2, or singleplayer cheats) must run the **same command twice within 60 seconds**:

```text
/economy worldcurrency USD
/economy worldcurrency USD
```

The first invocation only warns; the second confirms. Confirmation is tied to the actor, target
and current world currency. Conversion is one SQLite transaction: world-currency bank accounts,
trading balances and market prices convert using the current FX snapshot with checked arithmetic.
Optional foreign accounts keep their currencies. A secondary account already using the target
currency merges into the permanent primary to preserve the no-duplicate rule. Stock share
quantities remain unchanged. Overflow or any failed operation rolls back the conversion.

Raw historical ledger/order/trade records retain their original currency for audit. Frozen
account-currency display projections and daily stock chart values convert transactionally;
later exchange-rate changes do not rewrite the displayed old history. Historical revaluation
uses bounded batches. Startup never silently rebases existing balances or prices. An older
world with an independently denominated market can use the twice-confirmed command, including
the current world code, to align its market explicitly.

`config/economy/banking.toml` now contains **exchange rates only**, with no world-currency setting.
Supported choices: CAD, EUR, GBP, AUD, NZD, USD, HKD, JPY, CNY and DKK. Accounts use integer minor
units (100 per major unit except whole yen). Dollar currencies display `$123.45 CAD` (or their own
code); GBP `£123.45`, EUR `€123.45`, DKK `123.45 kr`, CNY `CN¥123.45`, JPY `JP¥123`.

Foreign deposits group cash by currency before conversion and round down once per source
currency to destination minor units. Withdrawals round the required account debit **up**, so
rounding cannot create free cash. No overdrafts or zero-value cash deposits are allowed.
Rates are stored and cached in SQLite. The TOML rates seed and configure the daily rate batch;
they are fixed, editable defaults, not an automatic live market feed. Each daily batch writes
current and historical rates in one transaction. See [rate sources](docs/exchange-rates.md).

Commands operate on the primary bank account using minor units:

- `/economy balance`
- `/economy pay <online-player> <units>`
- `/economy history`

## Stock exchange

Right-click the **Stock Trading Computer**. Each player has exactly one separate trading
account in the selected world currency, which does not consume a bank-account slot. Select a bank account and use **Fund**;
foreign funds convert before reaching the trading balance. **Unfund** returns trading cash
to the selected bank account, converting when needed.

Scroll or search the company list, select a company, then use its buy/sell controls and chart.
The chart shows up to the last 14 in-game days of recorded market values, including only history
available since recording began. Daily snapshots and their original denomination persist in SQLite.
Enter a positive whole share quantity and buy or sell. Purchases require
available trading cash; sales require owned shares. The single database writer deterministically
sequences market orders. Cash, ownership, order/trade history and append-only ledger entries
settle atomically. Orders execute immediately against the quoted simulated market price.

The market includes 100 original Minecraft-style company names based on the first 100 companies
in the linked US500 list. Initial CAD prices use the December 31, 2025 USD close × 1.3706 USD/CAD,
rounded to cents. All 100 prices were obtained; none were invented. Those CAD baselines convert when the initial world currency is selected; subsequent world
currency changes also revalue active market prices. Quotes start at these historical baselines. There is no live brokerage connection or automatic price simulation;
`FinanceDatabase.updateStockPrice` supports explicit server-side price changes.
See [stock data and reproduction](docs/stock-data.md).

### Exchange-rate history

The stock exchange terminal has **Stocks** and **Exchange Rates** tabs. On Exchange Rates,
choose source and destination currencies to see the current configured cross rate and recorded
historical rates. Scroll through each 15-date page, then use **Older** or **Newest**. Hover a
row for the full rate. Values mean destination units per one major source-currency unit.
History uses only matching recorded dates; unavailable dates are not filled with today's rate.
CAD remains the fixed reference of1, including partial daily updates. Rate history is read from
SQLite asynchronously and is not cached; current rates use the existing cache. These are the
server's recorded configured rates, rather than a live external FX feed.

Stock and funding controls now have separate bounds. Input hints, suggestions, carets and button
labels are clipped inside their controls, including the short **Amount** funding hint.

## Persistence and migration

Each world stores financial data at `economy/economy.sqlite`, using WAL, FULL synchronization,
foreign keys and a 5-second busy timeout. One dedicated executor owns one writer connection;
no JDBC query runs on the Minecraft tick thread. Bank accounts/holdings use bounded selective
LRU caches, current FX and stock prices are cached, and only in-flight active orders are cached.
Transaction, trade/order and historical FX records remain database reads. The ledger is append-only
and paired financial movements share a unique transaction ID. Caches change only after commit.

Existing SavedData balances and retained transaction histories import automatically once, with
opening/gap reconciliation entries. The original SavedData file remains available; new operations
use SQLite. Back up a stopped world, or use a SQLite-aware backup including its WAL state.

SQLite makes **database money and share settlement** atomic. Physical Minecraft inventories and
world files have a separate save boundary: a process crash between reserving cash/stock and database
commit or item delivery is not covered by a durable inventory recovery journal. Ordinary failed
requests refund reservations; pending menus are locked and cannot submit a second operation.

## ATMs and cash registers

Craft an ATM with iron ingots in the top and bottom rows, and redstone / emerald / redstone
in the middle. Craft a cash register with a glass pane at top center, iron / redstone / iron
in the middle, and three iron ingots along the bottom. Both are in the **Economy** blocks tab. ATMs are two blocks wide, two blocks high, and one block deep. Leave the full 2×2 space clear, with a solid floor under both lower sections.
Use a pickaxe to recover an ATM.

1. Place a cash register on a chest or another single block inventory. The placer owns it.
2. Sneak-right-click with a stack of the product. The stack count sets the quantity per sale;
   the held stack is a template and is not consumed. Exact item components must match stock.
3. Look at the register and run `/economy shop price <units>`.
4. Put matching stock in the inventory directly below it.
5. Buyers right-click the register to purchase one batch using their bank balance.

Changing the product resets its price until the owner sets a new price. Owner clicks show
shop information. Insufficient funds, stock, or inventory space reject the whole purchase.
Only the owner can configure the register. Stock containers retain normal Minecraft access
rules: secure them using your server's usual land/container protection. This mod is not a
claim-protection system. Each half of a double chest is a separate stock inventory here.

## Terminal colours and new workstations

The original teal ATM and cash register remain available. Each also has 16 additional dye
accent colours, all keeping the original black body. In a crafting grid, combine any unplaced
ATM or register with one dye to obtain the corresponding coloured variant. These variants
keep the same ATM interactions, four-section placement, and register/shop ownership behavior.
All terminals and the new workstations appear in the **Economy** blocks tab. Inventory and
hotbar icons are centered and fitted to their slots.

The **Bank Teller Station** and **Stock Trading Computer** are facing-aware
blocks, each **2 blocks wide × 2 blocks high × 1 block deep**. The teller station has a counter, divider, service opening, and teller equipment;
the computer has twin chart displays, a keyboard, mouse, and tower. Both now provide functional
banking/trading screens.

All ATMs (teal and dyed), teller stations, and trading computers are placed with one item
and occupy four real block cells. Placement requires clear space and solid support below
both lower sections. Breaking any section removes the entire machine and yields one item
with the appropriate tool; Creative removal yields none. Cash registers keep their existing size.
Existing machines placed before 0.1.5 must be broken and placed again to use the larger shape.

Teller recipe: `IGI / IRI / III` (I = iron ingot, G = glass pane, R = redstone).
Computer recipe: `GGG / RCR / III` (C = comparator; other symbols as above).

## Integration API

Use `ca.emilybelnavis.mcmods.economy.api.EconomyApi.get(server)` on the server thread.
`balance`, `transfer`, `credit`, `debit` and `history` return `CompletableFuture` values.
**Never block the tick thread with `join` or `get`.** Completion runs on the database executor;
schedule Minecraft/player/inventory work with `server.execute`. Raw ledger history has an
exclusive sequence cursor and a bounded page size. The primary-account API remains a convenient
integration surface; `FinanceService.database` also offers owned-account, exchange and market operations.
Trusted callers must authorize issuance and charges. This is an API-breaking change in the alpha.
Integration mods must require `economy`; SQLite is bundled, so no extra dependency mod is needed.

## Cards and currency collections

The currency and card creative tabs contain **126 items**. Cash has its displayed face value
and can be deposited at terminals. Cards allow banking access but have no borrowing behavior.
There are no crafting recipes for the collections yet.

Credit cards: **Summit, Meridian, Aurora, Obsidian**.
Bank cards: **Harbor, Meadow, Tide, Ember**.
Credit cards now use four original styles loosely inspired by Visa, Mastercard, Amex, and Discover:
navy/gold Summit, overlapping-circle Meridian, framed pale-blue Aurora, and sunrise Obsidian.
Existing bank-card artwork is unchanged. Cards stack to one.

| Inspired set | Coin face values | Bill face values |
| --- | --- | --- |
| Euro | 1c, 2c, 5c, 10c, 20c, 50c, €1, €2 | €5, €10, €20, €50, €100, €200 |
| GBP | 1p, 2p, 5p, 10p, 20p, 50p, £1, £2 | £5, £10, £20, £50 |
| AUD | 5c, 10c, 20c, 50c, $1, $2 | $5, $10, $20, $50, $100 |
| NZD | 10c, 20c, 50c, $1, $2 | $5, $10, $20, $50, $100 |
| CAD | 5c, 10c, 25c, 50c, $1, $2 | $5, $10, $20, $50, $100 |
| USD | 1c, 5c, 10c, 25c, 50c, $1 | $1, $2, $5, $10, $20, $50, $100 |
| HKD | 10c, 20c, 50c, $1, $2, $5, $10 | $10, $20, $50, $100, $500, $1,000 |
| JPY | ¥1, ¥5, ¥10, ¥50, ¥100, ¥500 | ¥1,000, ¥2,000, ¥5,000, ¥10,000 |
| CNY | 1 jiao, 5 jiao, 1 yuan | 1, 5, 10, 20, 50, 100 yuan |
| DKK | 50 øre, 1, 2, 5, 10, 20 kroner | 50, 100, 200, 500 kroner |

The sets use original pixel artwork inspired by the currencies' colour families and forms,
with arches, crowns, stars, ferns, maple leaves, and an abstract eagle motif. The additional sets use skyline, mountain/blossom, pagoda, and bridge motifs. These are game
collectibles rather than reproductions of real banknotes. All names are searchable in Creative.

Each currency has its own **Economy - XXX Cash** tab: EUR, GBP, AUD, NZD, CAD, USD, HKD, JPY, CNY, and DKK. The **Bank & Credit Cards**
tab contains all eight cards. CAD includes both vertical and horizontal $5/$10/$20/$50/$100
bills; USD bills share one green palette. The other existing bill textures are preserved.
For example: `/give @s economy:summit_credit_card` or `/give @s economy:cad_bill_20`.
Tab icons: CAD horizontal $20, GBP £50, USD $100, EUR/AUD/NZD/HKD/CNY 20 bills, JPY ¥1,000,
and DKK 20-krone coin. JPY and DKK do not have 20-value bills in these collections.

Coin IDs for hundredth-based currencies use hundredths: `economy:eur_coin_100` is the Euro-inspired €1 coin. JPY coin IDs use whole yen, e.g. `jpy_coin_500`.

## Upgrading from 0.1.2

The legacy copper/silver/gold coins and small/medium/large banknotes, their denomination
configuration, and the copper minting recipe have been removed. Existing stacks of those
six removed items cannot be retained when old worlds are loaded; account balances are separate
and remain saved. No automatic item conversion into the new cash collections is performed.

## Development

With Java 21 selected:

```sh
./gradlew build
./gradlew runGameTestServer
./gradlew help --task runClient
./gradlew runClient --dry-run
./gradlew runClient
./gradlew runServer
```

Regenerate collectible art with `python3 tools/generate_collectibles.py`, and terminal variants
with `python3 tools/generate_terminal_variants.py`. Resize source geometry is stored under
`tools/model_sources/`; `python3 tools/generate_wide_machines.py` rebuilds the four model
parts, item models, and common collision geometry.
The build runs banking, layout, and collision checks. `./gradlew runGameTestServer` also runs
placement, obstruction, breaking, adjacency, and support-loss checks in an isolated test world. Development runs register the economy
mod's main source set. Server startup requires accepting Minecraft's EULA yourself.
