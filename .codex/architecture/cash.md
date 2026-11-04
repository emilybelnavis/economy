# Cash and cards

CashCatalog restores the 118 original cash IDs and eight card IDs from
75fd816. All 126 item models and textures are byte-identical to that source.
Face values use A2 currency minor units. Both Canadian bill artworks retain
their distinct original item IDs. There are twelve original tab identities.
The main Economy tab currently provides a cash/card overview while terminal
items await their own package. Collectibles retain their recipe-free behavior.

Cards stack to one. hasBankingCard provides generic access eligibility, with
no account funding or credit facility implied. Account/session authority
remains the responsibility of the server interaction.

CashStackIdentity stores world and issuance lot in a persistent, synchronized
data component. A missing marker leaves development cash collectible.
Recognition detaches an immutable CashIssuance.Claim on the server thread.
A marker is only a lookup identity. Trusted journal admission must validate
catalog identity, custody, the financial intent and available lot quantity.

Schema V13 cash_issuance stores issued and remaining quantities per world/lot
and exact item identity. Issuance and redemption compose supplied financial
postings in the same journal transaction as quantity facts. The original
financial operation UUID and full canonical intent are preserved. Underfunded
operations retain intent for retry. Journal replay returns the original result.

Vanilla splits preserve the lot component, and same-lot stacks can merge.
Different lots remain separate because their components differ. Redemption
aggregates up to 64 observed stacks and cannot consume more than the lot's
remaining quantity. Each observed stack has 1–64 units, each lot has at most
4096 issued units. Historical facts and operation outcomes retain the audit.

Identical copies cannot be individually distinguished from their genuine
counterparts. They share one finite lot balance. Aggregate overdraw and
redemption after exhaustion reject. Custody evidence is required to admit
physical quantity and prevent speculative replacement. This contract promises
quantity conservation rather than individual clone identification.

Startup quarantines outstanding restored lots in journaled batches of 16
before service readiness. This applies to paired and skewed restores alike.
Only explicit trusted authority with verified custody evidence and a reason
can reconcile a lot. Unknown proof keeps it quarantined. An exhausted restored
lot remains exhausted and cannot be redeemed again.

LedgerService.cashIssuance binds trusted admission, catalog validation, live
authority, evidence and financial composition. Deposit and withdrawal gameplay
are later packages and remain gated by their custody integration checks.
