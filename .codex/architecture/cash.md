# Cash and cards

CashCatalog preserves the118 original cash IDs and eight card IDs from
75fd816, and adds13 original CHF cash items under [the CHF contract](chf.md). All 126 item models and textures are byte-identical to that source.
Face values use A2 currency minor units. Both Canadian bill artworks retain
their distinct original item IDs. The twelve original tab identities remain, plus the new CHF tab.
The main Economy tab currently provides a cash/card overview while terminal
items await their own package. Collectibles retain their recipe-free behavior.

Cards stack to one. hasBankingCard provides generic access eligibility, with
no account funding or credit facility implied. Account/session authority
remains the responsibility of the server interaction.

CashStackIdentity stores world and issuance lot in a persistent, synchronized
data component. A missing marker identifies Creative/command cash for the
deposit mint path; a present marker from another world remains invalid.
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

Deposit snapshots explicitly distinguish unmarked cash from marked issuance.
Unmarked originals retain a deterministic request identity and their exact
serialized components. They are removed and save-verified before COMMIT, then
the deposit atomically applies the balanced issuance/account posting without
creating a reusable active lot. Mixed deposits redeem only their marked lots.
The deposit journal retains the unmarked flag and original components so replay
cannot credit the same reservation twice. Because vanilla stacks do not retain
their Creative or `/give` origin, any holder may deposit unmarked catalog cash;
the trust boundary is that only operators can create those recipe-free items.

Startup quarantines outstanding restored lots in journaled batches of 16
before service readiness. This applies to paired and skewed restores alike.
Only explicit trusted authority with verified custody evidence and a reason
can reconcile a lot. Unknown proof keeps it quarantined. An exhausted restored
lot remains exhausted and cannot be redeemed again.

LedgerService.cashIssuance binds trusted admission, catalog validation, live
authority, evidence and financial composition. Deposit and withdrawal gameplay
are later packages and remain gated by their custody integration checks.

## CHF extension

The current catalog has131 cash items/eight cards and13 tabs. CHF notes10/20/50/100/200/1000 and coins0.05/0.10/0.20/0.50/1/2/5 use100 minor units/major. Editable original64px pixel art/provenance is in tools/chf-assets. All old item artwork/IDs remain unchanged. Actual registered CHF item/component serialization and vanilla splitting are checked; financial issuance/redemption retains the existing world/lot/custody/save authority. New CHF settlement uses strict v2 facts; unmarked cash remains the existing operator-created-cash trust boundary. See [CHF precision and versioning](chf.md).

## A6-06 issued debit tokens

[Issued-card lifecycle and custody](issued-cards.md) adds28 distinct debit items while preserving all eight generic collectibles. Token items carry world/token references only; issuer/owner/account/status/expiry/provider checks are authoritative. First issuance/renewal/replacement use issuer kiosks and A5 protected delivery; debit never establishes credit or a score.
