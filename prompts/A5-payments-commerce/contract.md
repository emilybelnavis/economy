# A5 shared contract

This contract consolidates the approved A5 decisions. The [phase plan](PLAN.md)
owns package order, feature coverage and verification scope. Each approved
execution prompt owns its implementation boundary.

## Authority and transaction ownership

A1 owns world-scoped journaling and recovery. A2 owns currencies, FX
publications and rounding. A3 owns parties and account lifecycle. A4 owns
balances, holds and postings. A5 owns invoices, receipts and physical custody.
Use the existing world store. Minecraft 1.21.1, NeoForge 21.1.252 and Java 21
apply to both integrated and dedicated servers.

Authenticate the actor and live authority at admission. For terminal actions,
validate menu session, reach, dimension, anchor generation and applicable
settings, quote and account revisions. Pass bounded immutable intent to the
world worker and apply Minecraft effects on the server thread. Disconnect
ends the session while committed obligations persist. Keep the original
operation ID and canonical intent across retries. A different intent with
the same operation ID conflicts.

## Payments

An open invoice can be authorized by reserving funds. Authorization permits
one full capture or cancellation. Expiry releases the hold through bounded
catch-up. The persisted five-minute deadline includes downtime. Capture at or
after the deadline rejects even while release is pending. Capture commits the
hold transition, ledger postings, invoice transition and receipt together.
The first release supports same-currency checkout and zero fees.

Register owners configure their own sales. Trusted integrations require
explicit grants. Merchants and authorized operators may initiate one audited
full refund of a captured payment. An underfunded refund can be retried with
the original intent once funded. Replays of committed operations return their
original result. Authorize receipt reads and bound diagnostic queries.

## Cash, terminals and sales

Preserve the cash and card item identities. Unmarked development cash remains
collectible. Redeem only quantities recognized by A5 issuance. Generic cards
grant banking access without credit. Preserve existing worlds and backups.

Deposits accept bills at ATMs and bills or coins at tellers. Apply A2 rounding
to source-currency subtotals and return the original skipped or cancelled
cash. Return what fits directly and place the confirmed undelivered remainder
in an owner-bound durable claim.

Withdrawals require an exact valid denomination plan. ATMs issue bills.
Tellers maximize feasible bill value, then minimize bill count and fill with
coins automatically. Use the A2 ceiling rule for the account debit.
Register sales require an exact component match and full batch from the source
container, priced at the current owner-configured revision and payable to the
seller primary. Reject incompatible checkout currencies.

Check capacity for the entire withdrawal or register batch at final admission.
If capacity is insufficient, reject before debit, cash issuance or stock
removal. Automatic purchase drops are retired. If a committed delivery is
confirmed undelivered after a capacity change or disconnect, use a durable
claim.

## Recovery and acceptance

Retain authenticated intent, original operation ID, reservation identity and
save witness, journal result, intended delivery identity, delivery
acknowledgment and reconciliation evidence. A delivery acknowledgment alone
does not prove that Minecraft saved the delivered inventory. On an unknown
financial outcome, resolve the original operation before taking further action.

Quarantine uncertain deliveries. Only authorized operators may close cases
with evidence of delivery or redeliver or compensate with evidence of loss.
Record the evidence and reason for each resolution. Inconclusive cases stay
open.

Each package verifies its contract at its own completion gate. The payment
foundation must prove atomic rollback and expiry behavior. The custody
foundation must establish observable save witnesses and exercise paired and
skewed restores. Cash issuance must demonstrate authenticity across stack
splits, merges and restores. Phase acceptance repeats recovery scenarios
through the normal player entry points. These are verification obligations
for implementation.
