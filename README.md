# Economy

Version **0.1.0** · Project ID `ca.emilybelnavis.mc-mods.economy`

An early playable economy mod for **Minecraft 1.21.1**, **NeoForge 21.1.252**, and **Java 21**.
Install the JAR on both the server and clients. No additional mods are required.

## Banking

Player accounts start at zero, use UUIDs, and persist with the overworld across dimensions.
The latest 100 transactions per account are retained; `/economy history` shows the latest ten.
Accounts and shop data follow Minecraft's normal world-save cycle. Hard crashes can lose
unsaved changes; this version does not provide a crash-durable financial journal.

- `/economy balance` — balance in whole units.
- `/economy pay <online-player> <units>` — digital transfer.
- `/economy history` — recent transactions.

Right-click any of the four sections of an ATM for balance/history controls. Physical currency and cards
are decorative for now; cash deposits and withdrawals will return when currency functionality
is implemented. Existing account balances and the integration API remain available.

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

The **Bank Teller Station** and **Stock Trading Computer** are decorative, facing-aware
blocks, each **2 blocks wide × 2 blocks high × 1 block deep**. The teller station has a counter, divider, service opening, and teller equipment;
the computer has twin chart displays, a keyboard, mouse, and tower. No teller GUI, stock
market, or trading logic is implemented yet.

All ATMs (teal and dyed), teller stations, and trading computers are placed with one item
and occupy four real block cells. Placement requires clear space and solid support below
both lower sections. Breaking any section removes the entire machine and yields one item
with the appropriate tool; Creative removal yields none. Cash registers keep their existing size.
Existing machines placed before 0.1.5 must be broken and placed again to use the larger shape.

Teller recipe: `IGI / IRI / III` (I = iron ingot, G = glass pane, R = redstone).
Computer recipe: `GGG / RCR / III` (C = comparator; other symbols as above).

## Integration API

Use `dev.emily.economy.api.EconomyApi.get(server)` on the server thread. The API supports
`balance`, `transfer`, `credit`, `debit`, and immutable `history` snapshots. Amounts are positive
whole units; invalid amounts/overflow throw `IllegalArgumentException`, and insufficient funds
throw `IllegalStateException`. Account IDs are UUIDs. Callers must authorize charges and money
issuance themselves. Integration mods should declare a required dependency on `economy`.
The API is experimental in 0.1.x. No separate integration mods are included.

## Decorative cards and currency collections

The currency and card creative tabs contain **126 collectible items**. These new items
have no account, credit, withdrawal, deposit, exchange-rate, or payment behavior yet. Their
values are visual face values only. There are no crafting recipes for the collections yet.

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
and remain saved. No automatic conversion into the decorative collections is performed.

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
