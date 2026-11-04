# A4 — Double-entry ledger and transaction engine

Status: A4-01 through A4-06 verified. See the A4-06 handoff for the A5 integration boundary.

See the [phase design](DESIGN.md) for architecture and settlement behavior.

See the [execution contract](EXECUTION.md) for activation, shared rules, and completion gates.

## Goal and dependencies

Prepare a complete, reviewable specification for A4. Required upstream work: A1–A3. Do not treat existing code as completed redesign work. Supply approved upstream contracts explicitly; mark missing contracts unresolved.

Fixed baseline: Minecraft 1.21.1, NeoForge 21.1.252, Java 21; server-authoritative behavior on both integrated and dedicated servers. Stage A owns money/FX/credit; custody owns shares/return obligations; no market-owned parallel banking database.

## Scope to flesh out

Balanced journal postings; counteraccounts/issuance/redemption; atomic transfers/deposits/withdrawals/FX settlement; durable holds/releases; caller-supplied idempotency keys and payload checks; failures/recovery; history queries and reconciliation.

## Mandatory current-feature reimplementation

Inventory coverage: F08, F09, F06 financial rebase, F20 financial API/commands.

Reimplement P2P primary payments, owned-account transfers, credit/debit, funded closure, raw history/cursors and paired transaction identity. Existing single-sided credits/debits and trade entries must become balanced accounting. Design explicit FX clearing, fees and residuals. Rebase must preserve raw history and convert approved projections atomically. Cash item handling stays A5.

All assigned existing behavior must appear in the coverage table even when replacement architecture differs. Record any proposed behavior change and its approval status. Existing implementation and existing tests do not satisfy the new phase gate.

**Hold scope decision.** A4 supports full capture and explicit release. Partial capture is deferred for future planning when a workflow needs to settle less than the reserved amount. That planning should decide whether to release the remainder immediately or keep it reserved.

**FX scope decision.** Exchange fees go to a game-owned fee account. Round fractional exchange payouts down to whole coins. Fractional coins cannot be posted or paid out.


**Funded closure decision.** Use the A3 destination and eligibility contract. If the confirmed exchange rounds down to zero whole coins, settle with a zero payout and close the source account in the same transaction.


**Rebase decision.** Round converted balances down to whole coins, including zero. Preserve raw transaction history in its original units.


## Feature coverage

| Inventory | Existing behavior | Replacement and acceptance owner | Handoff |
|---|---|---|---|
| F08 | Primary payments and owned-account transfers | Balanced same-currency settlement and live eligibility in A4-04 | A4-06 service |
| F08 | Credit, debit, and trade entries | Balanced counterentries in A4-01 and A4-05 | A5 cash and market consumers |
| F08 | Funded closure | Atomic settlement and close in A4-05 | A4-06 service |
| F09 | Raw history, cursors, paired identity | Durable facts in A4-02 and bounded history in A4-06 | API consumers |
| F06 | Financial rebase | Atomic conversion and original history in A4-05 | A3 lifecycle and A4-06 history |
| F20 | Financial API and commands | Shared authorization and settlement entry points in A4-06 | A5 and integrations |

## Approved execution order

1. [A4-01 · Posting model](A4-01.md)
2. [A4-02 · Durable settlement](A4-02.md)
3. [A4-03 · Holds](A4-03.md)
4. [A4-04 · Same-currency operations](A4-04.md)
5. [A4-05 · FX, funded closure and rebase](A4-05.md)
6. [A4-06 · History and public contracts](A4-06.md)

## Phase acceptance scenarios

Duplicate same request returns same outcome; reused key/different payload rejects; overspend and overflow reject atomically; every currency balances independently; replay/restart reconciles; trusted issuance records source and authority. Define unknown commit-result retry behavior.

Include concrete numerical or state-transition examples where useful. Describe future unit, integration, restart/crash and performance checks appropriate to each subprompt. Distinguish proposed guarantees from verified behavior. Future runnable deliveries require configured runClient/runServer, mod registration, required development runtime dependencies, and runClient help/dry-run verification; run no checks during this planning task.
