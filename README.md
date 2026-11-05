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

Detailed diagnostics include the financial calendar’s UTC origin, elapsed whole days/months/years, retained clock target, next boundaries and catch-up state. `/economy diagnostics calendar` shows policy versions and active work in pages of eight registry entries; follow the printed `after` command for the next page. Reports use their displayed sample timestamp. Day/month/year values count completed periods from zero. Fixed-interval backlog is separate from the domain pending-event indicator, which is not a total debt or obligation count. These commands are read-only and remain available from cached samples while financial work is catching up.

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

Creative inventory includes 131 cash collectibles and eight banking cards across thirteen tabs, including both Canadian bill styles. Cards provide generic banking access without a credit facility.

CHF is the eleventh currency, with two-decimal electronic balances. Its game opening calibration is 1 CHF = 1.60 CAD, not a live market quote. Fresh worlds include CHF immediately; existing worlds add it at the next UTC FX publication after catch-up, without changing the world currency or inventing historical CHF rates. Operator diagnostics show whether its rate is available or scheduled.

Original CHF cash includes 10, 20, 50, 100, 200 and 1,000 notes, plus 0.05, 0.10, 0.20, 0.50, 1, 2 and 5 coins. Tellers support notes and coins; ATMs use notes only. Withdrawals must be exactly representable with enabled cash: 1.02 CHF is rejected rather than silently changed. Electronic balances can still hold 0.01 CHF. CHF cash conversions and CHF account settlement preserve account cents; other legacy currency groups keep their existing whole-unit rounding.

Cash taken from the Creative inventory or created with commands is accepted as newly minted cash when deposited. Existing world-marked cash still has its issued quantity checked against the world ledger and custody evidence. Trusted server APIs support deposit previews and returns, plus exact withdrawals with full-batch capacity checks.

## Schema upgrades

New worlds initialize automatically. Redesigned worlds with schemas 1 through 20 require a validated complete-world backup before upgrading to schema 21. Prepare it while Minecraft is fully stopped:

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

The restore command validates the backup and refuses an existing destination. It preserves the current world; switching to the restored copy is a separate operator action. Restore supports clean schema 4 through 21 backups in this mod’s backup format. A restored world on an older schema still requires a pre-upgrade backup.

There is no online backup, automatic replacement, or retention/deletion service. Operators own backup storage and retention. Failed restores can leave a hidden staging directory without publishing the destination.

Economy requests become available after startup recovery and required catch-up. Ordinary Minecraft can continue during that work. With an economic system registered, ongoing catch-up processes UTC intervals in chronological order and gates affected writes until current. Sleep and `/time` do not advance economic time. Daily FX starts at the UTC midnight after the world’s opening publication. A fresh world offers the first connected player a denomination chooser.

Legacy economy databases are not imported. Incompatible, corrupt, or mismatched identity data makes the economy unavailable while ordinary Minecraft play can continue. Preserve the files and inspect the logged error rather than deleting them.

Storage requires filesystem support for exclusive file locks, atomic file publication, and directory synchronization. These checks have been exercised on macOS; other platforms have not been runtime-tested.

Register owners can use `/economy shop price <units>` while looking at their register and holding the exact product batch. The price uses the seller’s primary-account currency. Purchases require matching buyer currency and room for the entire batch; known undelivered paid goods remain protected claims. Open Shop to preview and buy the exact batch.


## Physical terminals

The Economy Terminals creative tab contains an ATM and register in a neutral finish and all 16 dye colors, plus a teller and branch kiosk. ATMs and tellers occupy two blocks across and two high; both lower cells need solid support. Place them facing you with the whole space clear. Breaking any machine cell removes the entire machine and returns one item in survival. Branch kiosks are one block wide and two high, with solid support under their lower anchor. Registers occupy one block and can sit directly on the stock chest below.

Craft a neutral ATM with iron ingots, glass pane, redstone and a chest. The teller also needs a hopper; registers use iron ingots, glass pane, redstone and a chest in a smaller pattern. Register dye recipes remain available. ATM dye recipes are retired; existing colored machine IDs and items remain compatible. Craft an unbound kiosk with iron/glass/iron, iron/redstone/iron, iron/chest/iron. A broken or recolored machine receives fresh owner and placement settings when placed again.

Owners configure ATMs, tellers and kiosks by sneaking and right-clicking with an empty hand. Select a bank, review its native currency and confirm. New and legacy unbound machines cannot start cash or FX operations. Pending old transactions and claims remain protected. Enable native-currency cash settings separately after binding; ATM uses bills, teller uses bills and coins. A binding is fixed after cash service has first been enabled for that placement.

ATMs and tellers open banking screens; kiosks open branch account services; registers open the shop and protected-purchase claims. The Stock Trading Computer is available as a decorative block only and has no trading screen or economic behavior. Operators can look at a ready terminal and use `/economy admin config` to open its privileged configuration screen. `/economy admin binding` reports the looked-at terminal's bank, currency, migration state, generation, geometry and blocking cash work without changing it.

Internal GUI identifiers and journal details are hidden by default. Set `debugGui=true` in `config/economy-scheduling.properties` and restart the server when those diagnostics are needed.


## Banking and shops

Right-click a teller or ATM, or use any bank card for account, history, FX and claim access. Cards grant access without credit. The banking home page lets you choose My accounts, Deposit, Withdrawal, Exchange or Collect items. Bank cards offer the services available remotely. Click an existing account to see its transactions, or open account management to view its balance. Manage account is also available from transaction history. History and collection lists have bounded Next pages.

The account limit is four open bank accounts, including the permanent primary, with one open account per currency. Existing accounts, balances and history remain available. Opening, closing, assigning a bank and switching providers require the appropriate branded branch kiosk. New primaries require a Tier 1/hybrid bank matching the world currency. Legacy accounts can be assigned at a matching-currency kiosk; provider switching requires a source visit then the named target within seven financial days. Tellers, ATMs and remote cards continue to serve existing accounts.

The institution catalog contains 23 fictional banks, including two CHF retail banks. Existing accounts retain an explicit legacy, unassigned provider until branch assignment becomes available. Institution capital is funded once per world using the selected world currency and a captured FX publication; it does not credit player accounts.

Original issuer and network art now covers all 23 banks, approved card variants, matching native-currency ATMs and tellers, and distinct branch kiosk designs. Bank-specific hardware is selected in authenticated terminal settings. Issued debit cards are available at the account issuer's kiosk; credit facilities remain unavailable. Existing collectible cards remain available.


Assigned bank accounts use the bank's service tier. Tier 2 qualification requires at least 100,000,000 in the current world currency after supported assets and unpaid liabilities are valued together. Five hybrid banks and five specialists offer Tier 2; unavailable valuations cannot grant it. Existing unassigned accounts retain their earlier access and pricing during migration.

Tier 1 FX costs 0.50% of the source principal, rounded upward to a source minor unit; Tier 2 FX is free. Previews show principal, fee and the amount received. A branded terminal accepts only its bank's native cash currency per batch. Some cash totals cannot exactly fit principal plus the rounded fee; adjust the total or deposit into an account in that currency first.

Eligible assigned deposits earn 0.50% nominal APR at Tier 1 or 1.00% at Tier 2. Yield uses the funded time during each 20-minute financial day, with payouts at world-aligned 28-day boundaries. It is paid from the bank's own cash. Earned unpaid yield stays separate from spendable funds when a payout must wait; no replacement money is minted. Specialist accounts below qualification retain protected access but earn no future deposit yield. Bank loans and securities trading still await their separate packages. Credit-card and loan application screens remain unavailable.

Use **Issued cards** to view an account's debit token. First issuance is free at the account's bank kiosk. Tokens last five financial years; renewal is free within 28 financial days before expiry or afterward. Earlier replacement costs 25 in the world's currency, converted into your selected funding account with a combined FX preview. Replacement revokes every copy of the old token only when the new issuance and fee commit. Report lost is free from an authenticated banking menu.

Receive committed cards through **Collect items**, including at the kiosk. Full inventory leaves an owner claim; uncertain delivery stays protected for evidence review. Copied items share one token status and cannot grant another owner's access or duplicate benefits. Issued debit cards create no credit score, loan or debt; the eight old generic collectibles retain their access-only behavior.

**Credit profile** shows whether credit is established and provides saved scores, component explanations and paged history. Login, debit cards, previews and failed applications do not establish credit. The first real credit-card issuance starts at 650 with one 2,500 starter limit across issuers. Scheduled score updates remain unavailable until reporting is enabled. Lower score ceilings never erase existing debt or enlarge spending room.

Credit-facility processing now supports funded limits, purchase and advance balances, daily compound interest, monthly statements and repayments. Advances are limited to 20% of the facility limit in outstanding principal. Application, advance and repayment screens will arrive in later packages. The tested foundation APIs keep new production credit issuance, increases and draws unavailable until default/reporting safety is installed. Reads and protected existing-debt actions remain available. Reporting, collections and automatic payments are not enabled.

**Credit facilities** lists your issued facilities. Select one to review its limit, contractual headroom, purchase/advance rates, current balance, rounding carry, outstanding minimum and past-due amount. **Statements** shows the original dated amounts in the facility currency; later payments do not rewrite them. A pending-work label means the view shows the saved balance while scheduled processing catches up.

`/economy credit facility <facility-uuid>` reads your own facility. Player operators can inspect a selected owner's facility with `/economy credit inspect <owner-uuid> <facility-uuid> daily`, `payments` or `events`. Each operator page is audited. Use the reported `after <sequence> <position>` cursor for more records; payment allocations also accept an offset after that cursor. Reconciliation says incomplete if its bounded portfolio or journal window is exceeded. These commands provide no balance or limit editing.

Only Deposit shows the player inventory. Deposit and Withdrawal each provide an account selector.

ATMs and tellers start disabled and unbound. Their owner or an operator selects a bank, confirms its native currency and enables cash services. Each machine accepts or dispenses that bank's cash only; ATMs use bills and tellers can use coins. Select any owned account and review FX when its currency differs. Sneak-right-click with an empty hand to configure, then choose Use terminal to return to banking.

For a deposit, place cash in the 24 input slots, choose Review and check the bank credit, then confirm. Issued cash is redeemed against its remaining world quantity; unmarked Creative or command cash is minted and consumed by that one saved deposit. Changing pages or closing before reservation returns the originals; anything that cannot fit is retained as an owner claim.

For a withdrawal, choose your account and enter an amount, then Review and Confirm. The bank determines the cash currency; a different account currency requires an explicit FX preview and its normal tier fee. Same-currency cross-bank service has no surcharge. A teller can dispense coins. The complete batch must fit before debit. A register owner holds the product batch and enters a whole-unit price in Shop. Buyers preview the exact batch and price before confirming.

Exchange has From and To account selectors. CHF pairs accept exact source-currency cents; other pairs retain whole-unit input. Review the debit, credit and fee before confirming. Stock trading accounts and screens are excluded.

Collect items lists cash and goods waiting for delivery. Click a batch to collect it when the whole batch fits in your inventory. Check payment resolves an uncertain request using its existing operation identity. Restored or uncertain physical deliveries require operator evidence review; the mod does not automatically create replacement items or repeat a charge.
