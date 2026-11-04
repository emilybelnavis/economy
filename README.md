# Economy

A NeoForge economy mod under development. The mod provides a server-side bank ledger, daily FX rates, and payment and history commands for initialized accounts.

Requires Minecraft **1.21.1**, NeoForge **21.1.252**, and **Java 21**.

## Build and run

Select Java 21 in your environment, then run:

```sh
./gradlew build
./gradlew runClient
./gradlew runServer
```

The mod jar is written to `build/libs/economy-0.1.0.jar`. Development client and server data are kept separately in `run/client` and `run/server`. Use disposable development worlds.

No additional mods are required; SQLite JDBC is bundled. Each world stores its foundation database and identity under `<world>/economy/` and must be owned by a single Minecraft server. Trusted server integrations can initialize accounts and authorize ledger funding. Collectible cash, cards and physical banking terminals are included. Banking and purchase menus are available; stock trading remains outside this release.

## Scheduling configuration

Create `config/economy-scheduling.properties` in the Minecraft instance directory to override settings. Use one `name=value` entry per line and restart the server to apply changes. Missing entries use the defaults below. Invalid values, unknown keys, or unreadable files leave the economy unavailable and log a configuration error. Storage uses one worker and one SQLite connection.

| Setting | Default | Accepted range |
| --- | ---: | ---: |
| `calculationWorkers` | 2 | 1–16 |
| `storageQueueCapacity` | 256 tasks | 1–4096 |
| `calculationQueueCapacity` | 64 tasks | 1–4096 |
| `callbackBudgetMillis` | 1 ms per tick | 1–10 ms |
| `sqliteLockTimeoutMillis` | 5000 ms | 1–60000 ms |
| `drainSeconds` | 30 s | 1–300 s |

The callback budget limits when the server starts additional callbacks in a tick. A callback that has already started runs to completion. The drain setting controls how long economy waits for its workers to shut down.

## Status and diagnostics

Use `/economy status` to check availability and startup or catch-up progress. The server console and permission-level 2 operators can also run `/economy diagnostics` for storage and journal checkpoints, queue waits, possible stalls, and the most recent failure code. Storage checkpoints reflect the latest completed sample. Economic rates describe observed progress and are shown only when measurable. Detailed diagnostics also show the latest FX sample, including selection status, the committed day, backlog, and effective and pending policy revisions. During shutdown, check the server log for unresolved work.

## Ledger commands

- `/economy ledger balance` shows posted, available, and reserved funds on your primary account.
- `/economy ledger pay <recipient-uuid> <coins>` pays another player whose primary account uses the same currency. Keep the retry command printed at submission to check or safely retry an uncertain result.
- `/economy ledger history <account-uuid> ["cursor"]` lists signed postings for your own account, including closed accounts. Transaction IDs link the entries of one settlement.
- Player operators with permission level 2 can run `/economy ledger reconcile <owner-uuid> <account-uuid>` to compare the current-currency balance with up to 63 journal postings. Larger histories return a limit message.

## FX policy

After a world has an opening FX publication, in-game operators with permission level 2 can use
`/economy fx policy <operation-uuid> <currency> <baseline> <variation-ppm> <pull-ppm> <bounds-ppm>`.
Enter the baseline in permanent-reference units per major unit of the selected currency.
The edit takes effect at the next UTC midnight. Variation and pull each accept 0–1,000,000 ppm.
Bounds accept 0–999,999 ppm, with the resulting band limited to 0.000000000001–1,000,000.
For an uncertain result, repeat the same command with the same operation UUID.

## Cash and cards

Creative inventory includes 118 cash collectibles and eight banking cards across twelve tabs, including both Canadian bill styles. Cards provide generic banking access without a credit facility.

Cash taken from the Creative inventory or created with commands is accepted as newly minted cash when deposited. Existing world-marked cash still has its issued quantity checked against the world ledger and custody evidence. Trusted server APIs support deposit previews and returns, plus exact withdrawals with full-batch capacity checks.

## Schema upgrades

New worlds initialize automatically. Redesigned worlds with schemas 1 through 16 require a validated complete-world backup before upgrading to schema 17. Prepare it while Minecraft is fully stopped:

```sh
./gradlew prepareEconomyUpgrade -PeconomyWorld=/absolute/path/to/world
```

Backups are placed beside the world in `<world-name>-economy-upgrade-backups/`. Then start Minecraft normally. Missing or changed backups prevent upgrades; they do not erase data. The utility refuses a world whose Minecraft or economy ownership lock is held. It does not copy a running world.

## Complete-world backup and restore

Stop Minecraft normally and wait for the log message `Economy complete-world clean shutdown recorded`. Then create a backup:

```sh
./gradlew backupEconomyWorld -PeconomyWorld=/absolute/path/to/world
```

Backups include the world and economy together, with checksums, version/identity information, journal position, and economic checkpoint. They appear under the sibling `<world-name>-economy-upgrade-backups/<backup-id>/` directory.

Restore into a **new, separate directory**:

```sh
./gradlew restoreEconomyWorld -PeconomyBackup=/absolute/path/to/backup-id -PeconomyRestoreDestination=/absolute/path/to/new-world
```

The restore command validates the backup and refuses an existing destination. It preserves the current world; switching to the restored copy is a separate operator action. Restore supports clean schema 4 through 10 backups in this mod’s backup format. A restored world on an older schema still requires a pre-upgrade backup.

There is no online backup, automatic replacement, or retention/deletion service. Operators own backup storage and retention. Failed restores can leave a hidden staging directory without publishing the destination.

Economy requests become available after startup recovery and required catch-up. Ordinary Minecraft can continue during that work. With an economic system registered, ongoing catch-up processes UTC intervals in chronological order and gates affected writes until current. Sleep and `/time` do not advance economic time. Daily FX starts at the UTC midnight after the world’s opening publication. A fresh world offers the first connected player a denomination chooser.

Legacy economy databases are not imported. Incompatible, corrupt, or mismatched identity data makes the economy unavailable while ordinary Minecraft play can continue. Preserve the files and inspect the logged error rather than deleting them.

Storage requires filesystem support for exclusive file locks, atomic file publication, and directory synchronization. These checks have been exercised on macOS; other platforms have not been runtime-tested.

Register owners can use `/economy shop price <units>` while looking at their register and holding the exact product batch. The price uses the seller’s primary-account currency. Purchases require matching buyer currency and room for the entire batch; known undelivered paid goods remain protected claims. Open Shop to preview and buy the exact batch.


## Physical terminals

The Economy Terminals creative tab contains an ATM and register in a neutral finish and all 16 dye colors, plus a teller. ATMs and tellers occupy two blocks across and two high; both lower cells need solid support. Place them facing you with the whole space clear. Breaking any machine cell removes the entire machine and returns one item in survival. Registers occupy one block and can sit directly on the stock chest below.

Craft a neutral ATM with iron ingots, glass pane, redstone and a chest. The teller also needs a hopper; registers use iron ingots, glass pane, redstone and a chest in a smaller pattern. Combine any ATM or register with a dye to recolor it. A broken or recolored machine receives fresh owner and placement settings when placed again.

ATMs and tellers open banking screens; registers open the shop and protected-purchase claims. The Stock Trading Computer is available as a decorative block only and has no trading screen or economic behavior. Operators can look at a ready terminal and use `/economy admin config` to open its privileged configuration screen.

Internal GUI identifiers and journal details are hidden by default. Set `debugGui=true` in `config/economy-scheduling.properties` and restart the server when those diagnostics are needed.


## Banking and shops

Right-click a teller or ATM, or use any bank card for account, history, FX and claim access. Cards grant access without credit. The banking home page lets you choose My accounts, Deposit, Withdrawal, Exchange or Collect items. Bank cards offer the services available remotely. If you have no bank account, My accounts shows one Open a new account button. Otherwise, click an account to see its transactions, or choose Open a new account to open account management. Manage account is also available from transaction history. History and collection lists have bounded Next pages.

You can have three open bank accounts, including your permanent primary. Manage account lists your accounts with their currencies and balances. At a teller, choose Close account beside a secondary account, review any balance transfer to your primary, then confirm. To add an account, select an available currency and choose Open account. Opening is disabled when you already have three accounts. Pending payments, refunds, holds or item deliveries can block closure. Closed transaction history is retained.

Only Deposit shows the player inventory. Deposit and Withdrawal each provide an account selector.

Tellers always provide deposits and withdrawals in every currency, with bills and coins. New ATMs default to deposits and withdrawals for all currencies and supported bills. The player who placed an ATM can right-click it with an empty hand to configure its services and currencies without operator permissions. Save applies only to that ATM; each ATM retains its own settings. Choose Use terminal in configuration to open banking. An operator can also use `/economy admin config` while looking at a terminal. ATMs accept bills only.

For a deposit, place cash in the 24 input slots, choose Review and check the bank credit, then confirm. Issued cash is redeemed against its remaining world quantity; unmarked Creative or command cash is minted and consumed by that one saved deposit. Changing pages or closing before reservation returns the originals; anything that cannot fit is retained as an owner claim.

For a withdrawal, choose your account and enter an amount, then Review and Confirm. The account determines the currency. A teller can dispense coins. The complete batch must fit before debit. A register owner holds the product batch and enters a whole-unit price in Shop. Buyers preview the exact batch and price before confirming.

Exchange has From and To account selectors. Enter a whole-unit amount in the source account’s currency and review the debit, credit and fee before confirming. Stock trading accounts and screens are excluded.

Collect items lists cash and goods waiting for delivery. Click a batch to collect it when the whole batch fits in your inventory. Check payment resolves an uncertain request using its existing operation identity. Restored or uncertain physical deliveries require operator evidence review; the mod does not automatically create replacement items or repeat a charge.
