# Server guide

[Back to README](../README.md) · [Player guide](PLAYER_GUIDE.md)

No additional mods are required; SQLite JDBC is bundled. Each world stores its economy database and identity under `<world>/economy/` and must be owned by a single Minecraft server.

## First startup

New worlds initialize automatically and offer the first connected player a world-currency chooser. Use `/economy choose CAD` to select a currency, with tab completion and lowercase support. Read the upgrade procedure below before opening an existing economy world with a newer schema.

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

Internal GUI identifiers and journal details are hidden by default. Set `debugGui=true` in the same configuration file and restart the server when those diagnostics are needed.

## Status and diagnostics

Use `/economy status` to check availability and startup or catch-up progress. The server console and permission-level 2 operators can also run `/economy diagnostics` for storage and journal checkpoints, queue waits, possible stalls, and the most recent failure code. Storage checkpoints reflect the latest completed sample. Economic rates describe observed progress and are shown only when measurable. Detailed diagnostics also show the latest FX sample, including selection status, the committed day, backlog, and effective and pending policy revisions. During shutdown, check the server log for unresolved work.

Detailed diagnostics include the financial calendar’s UTC origin, elapsed whole days/months/years, retained clock target, next boundaries and catch-up state. `/economy diagnostics calendar` shows policy versions and active work in pages of eight registry entries; follow the printed `after` command for the next page. Reports use their displayed sample timestamp. Day/month/year values count completed periods from zero. Fixed-interval backlog is separate from the domain pending-event indicator, which is not a total debt or obligation count. These commands are read-only and remain available from cached samples while financial work is catching up.

## FX policy

After a world has an opening FX publication, in-game operators with permission level 2 can use
`/economy fx policy <operation-uuid> <currency> <baseline> <variation-ppm> <pull-ppm> <bounds-ppm>`.
Enter the baseline in permanent-reference units per major unit of the selected currency.
The edit takes effect at the next UTC midnight. Variation and pull each accept 0–1,000,000 ppm.
Bounds accept 0–999,999 ppm, with the resulting band limited to 0.000000000001–1,000,000.
For an uncertain result, repeat the same command with the same operation UUID.

## Wider kiosks and legacy upgrades

New branch kiosks occupy two blocks across and two blocks high. Existing narrow kiosks keep their original footprint when loaded. Owners or operators can sneak-right-click with an empty hand, open settings and select **Upgrade kiosk**. The upgrade is free and preserves the kiosk's bank, native currency and placement identity.

The extra column must be loaded, empty, supported and clear of entities. Protected/blocked space or unfinished cash/custody work prevents the upgrade. A blocked preflight leaves the narrow kiosk working. Successful upgrades invalidate stale banking sessions; reopen the kiosk after completion.

An interrupted upgrade can briefly disable service while its saved outcome is checked. Complete narrow or wide geometry can reconcile without adding blocks. A partial footprint stays disabled and is never filled or rebuilt automatically; an operator must restore a complete original or wide footprint before reconciliation can finish. Removing hardware never changes customer accounts, balances or debts.

## Village branch diagnostics

Use `/locate structure #economy:banks` or `/locate structure economy:bank` to find bank structure locations through Minecraft's normal structure search, including unexplored areas. Choose the world's currency first so new villages can capture their bank plans. Native coordinates identify the village start chunk; the bank is nearby. Villages without a reserved bank site are excluded. Existing placed banks gain this structure index when their source village chunks load and reconcile. As with vanilla structures, an indexed location can remain after demolition; a locate result does not guarantee active banking service.

Operators can find nearby branded branch kiosks with `/locate poi economy:bank` or `/locate poi #economy:banks`. Vanilla POI search covers 256 blocks in the current dimension and uses indexed, already discovered kiosks; it does not search unexplored terrain for new bank buildings. Generated and player-placed branded kiosks qualify, including legacy narrow forms. Neutral kiosks and satellite cells do not. Existing kiosks enter the index when their chunks load. A result identifies physical hardware, not a guarantee that banking service is active.

New eligible vanilla plains, desert, savanna, snowy and taiga villages can generate bank branches. Original houses and workplaces determine branch eligibility and capacity; abandoned and empty villages receive none. Existing explored villages are not retrofitted. The mid-century modern buildings have institution signs, local wood, lighting, furnishings and working kiosks. New branch templates include original two-wide banker desks in local wood. Active branches can staff only their captured desks; player-placed desks remain unbound and do not create bankers or grant banking access. Older vanilla desk furniture is preserved and is not automatically converted.

Terrain can leave a selected branch pending. Generation searches inside the village, nearby edges within 32 blocks, then connected sites within 96 blocks, with bounded foundations and leveling. Banking remains disabled until physical placement and trusted hardware activation complete. These commands require an authenticated operator or the server console:

- `/economy branch plans [after <village-id>]` and `/economy branch list [after <branch-id>]`: pages of up to 16 records.
- `/economy branch status <branch-id>`: captured bank/currency, plan revisions, service generations, loaded/unknown observations, staffing state and recent operation history.
- `/economy branch staffing <branch-id> pause|resume`: persist staffing pause state. Pausing prevents new hires and replacement progress; it does not remove an existing banker. Resuming retains already earned replacement time.
- `/economy branch retry <branch-id> <expected-revision>`: explicitly retry missing required-provider selection or a pending terrain site while retaining captured bank/currency choices. Terrain retry uses already loaded chunks, supported empty space and fresh obstruction checks; load the area before retrying. It does not clear later construction or force chunks to generate. Removed, conflicted or interrupted partial buildings cannot be rebuilt with this command.

Branch records and generated hardware never create customer accounts, capital or money. Keep the operation ID shown by a write command if its outcome is uncertain; do not assume it failed. Dismantling hardware never closes accounts or changes debts.

## Banker desks and staffing

Bankers belong to a branch and a captured desk slot, rather than to a nearby building or a workstation’s appearance. A desk occupies two blocks across and two blocks high, needs support beneath both lower cells, and must remain intact. Breaking any cell removes the desk with one item drop in Survival and none in Creative. Replacing it manually does not restore its former branch assignment.

Right-click a banker for a private dialogue. Banking actions use the branch’s available trusted kiosk and the normal player account permissions; a banker’s appearance does not provide access to someone else’s accounts. If the branch, desk or service is unavailable, access remains unavailable until it is safely verified again.

A positively observed banker death leaves the desk vacant. Replacement requires 24,000 eligible loaded ticks (about 20 minutes at 20 ticks per second), an active unpaused branch, an intact assigned desk and a clear safe staffing location. Unloaded chunks, missing entity observations and uncertain interrupted spawns do not count as death or authorize another employee. Removing a desk stops new staffing for that assignment. Nothing forces chunks to stay loaded.

Replacement time is saved in batches of at most 200 ticks. A crash can lose up to 200 ticks of recently earned time; it does not complete the wait early. Pause/resume and normal restart retain saved progress. Use the branch status and staffing controls above when investigating a vacant or uncertain slot; there is no automatic desk repair or legacy-furniture upgrade.

## Schema upgrades

New worlds initialize automatically. Worlds on schemas 1 through 38 require a validated complete-world backup before upgrading to schema 39. Prepare it while Minecraft is fully stopped:

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

The restore command validates the backup and refuses an existing destination. It preserves the current world; switching to the restored copy is a separate operator action. Restore supports clean schema 4 through 39 backups in this mod’s backup format. A restored world on an older schema still requires a pre-upgrade backup.

There is no online backup, automatic replacement, or retention/deletion service. Operators own backup storage and retention. Failed restores can leave a hidden staging directory without publishing the destination.

Economy requests become available after startup recovery and required catch-up. Ordinary Minecraft can continue during that work. With an economic system registered, ongoing catch-up processes UTC intervals in chronological order and gates affected writes until current. Sleep and `/time` do not advance economic time. Daily FX starts at the UTC midnight after the world’s opening publication. A fresh world offers the first connected player a denomination chooser.

Legacy economy databases are not imported. Incompatible, corrupt, or mismatched identity data makes the economy unavailable while ordinary Minecraft play can continue. Preserve the files and inspect the logged error rather than deleting them.

Storage requires filesystem support for exclusive file locks, atomic file publication, and directory synchronization. These checks have been exercised on macOS; other platforms have not been runtime-tested.

## Server integrations

Server integrations can consume `ca.emilybelnavis.mcmods.economy.api.BankingIntegration` version 1 through the server ledger service. It exposes versioned prime, owner valuation and fee quotes, physically authorized credit payment entry points and fixed/variable/secured capability reporting. Financial authority and existing journal/custody rules apply to integrations.

## Financial inspection commands

Player operators can inspect a selected owner's facility with `/economy credit inspect <owner-uuid> <facility-uuid> daily`, `payments` or `events`. Each operator page is audited. Use the reported `after <sequence> <position>` cursor for more records; payment allocations also accept an offset after that cursor. Reconciliation says incomplete if its bounded portfolio or journal window is exceeded. These commands provide no balance or limit editing.

Player-backed operators can use `/economy loan inspect <owner-uuid> <loan-uuid>` and `/economy loan inspect_application <owner-uuid> <application-uuid>`. Each inspection is audited; event pages show their next sequence/position cursor. Views include captured underwriting and funding, payment totals, bounded reconciliation and catch-up progress. These commands provide no financial editing or borrowing activation. If repayment reports accounting unavailable, an operator can use the inspection view to check claim reconciliation and portfolio completeness.

Player-backed operators can use `/economy debt inspect <card|loan> <owner-uuid> <facility-uuid>` for audited original obligations, precise clock reasons, reconciliation and provider progress. Operators can inspect report revisions with `/economy debt inspect_reports <owner-uuid>` and collection receipts with `/economy debt inspect_history <owner-uuid> <case-uuid>`. Each page is audited. The view shows installed scoring or catch-up status and the owner’s next score boundary.

Player operators with permission level 2 can run `/economy ledger reconcile <owner-uuid> <account-uuid>` to compare the current-currency balance with up to 63 journal postings. Larger histories return a limit message.
