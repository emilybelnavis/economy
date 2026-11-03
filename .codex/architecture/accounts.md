# Account identity and catalog

The world database scopes the catalog. `AccountIdentity` carries the stable
account UUID, typed owner, purpose, status, revision and optional currency.
BANK is single-currency. The permanent primary is OPEN and counts toward the
three-open-BANK limit. OPEN BANK currencies are unique per owner. Closed BANK
rows retain their IDs and metadata and cannot reopen. A later opening uses a
new ID. TRADING is a separate permanent identity with no eligible currency
until B2 supplies its policy.

The account table, initialization journal fact and terminal outcome are
written together. An authorized equivalent retry returns the durable outcome.
A different logical initialization after creation commits a resolution fact
without creating another account. A1 handles uncertain commits and actor-bound
lookup. The opening FX reference also denotes the current denomination until
A4 implements durable rebase. BANK initialization reads it in the same
transaction that decides account creation.

`ServerServices.findOwnedAccount` admits a fresh owner grant and makes missing
and cross-owner IDs indistinguishable. Trusted worker calls to `AccountCatalog`
must use an owner in the transaction's actual world. History reads use an
owner/ID keyset index with a maximum of 64 rows. A3-04 owns audience and cursor
policy for externally exposed queries.

## Lifecycle

`AccountLifecycle.Open` carries a currency and expected primary revision.
Opening requires initialized banking. It checks the denomination and reads at
most three indexed open BANK rows to decide uniqueness and cap under the
journal transaction. The primary revision anchors the opening request across
denomination changes.

`AccountLifecycle.Close` carries the source revision and optional destination
and revision. Eligibility protects the primary and every BANK account in the
current denomination. It reads source, claims, and any destination in one
snapshot. A missing authoritative closure state blocks closure. A funded
source requires a distinct, open, same-owner BANK destination at the specified
revision. Any supplied destination is validated even for an empty source.

The close registration commits only for an eligible empty source. A4 must
rerun eligibility with authoritative balances and claims inside the funded
settlement transaction. A5 owns player-facing teller admission. The account
transition, versioned journal record, and original outcome share one commit.

## Queries and funds

Owner lookups and pages use a fresh detached grant and check owner and world
scope in the worker snapshot. An absent and foreign account ID produce the
same empty result. History pages include closed identities and use 1–63
results plus a single lookahead. A versioned cursor binds world, owner, and
the last returned account ID. Each page sees its own committed snapshot.
Closing an account preserves its position. New IDs that sort before the
cursor appear when paging starts again.

Operator lookup and paging use the live player-backed command source. Each
query allocates a new audit operation ID and commits an inspection receipt
with the exact query scope in canonical fields. Read and publication follow
only after current audience checks. Ordinary owner requests require no
operator receipt. Trusted owner callers must recheck the live audience when
publishing to a player.

`AccountFunds` v1 represents the three disjoint, nonnegative categories in
one currency. The posted sum uses checked minor-unit arithmetic.
`FundingEligibility` is advisory and leaves reservation and settlement to
A4. Account funds remain explicitly unavailable until the ledger exists.

## Rebase planning

A2 issues a detached `DenominationChange.Confirmed` proof only after the
second confirmation. The internal worker planner checks world, session,
actor, and current denomination revision. Each result carries the original
intent and the expected versions of the primary and optional collision.
The target currency is the confirmed target. When present, the collision
retires and its successor is the surviving primary ID.

The planner performs one indexed roster and collision query with a 257-row
lookahead and checks claims on at most 512 affected accounts. More than 256
primaries blocks planning. The limit bounds internal reads and results. A4
must separately size its atomic write against A1's 64-record and 128 KiB
transaction bounds, 16 KiB per record, and measured ledger costs.

A4 must recheck live authority and rerun the planner in the settlement
transaction, comparing the full result with the expected plan. Include the
entire roster so that new primaries, new collisions and changed revisions
invalidate stale plans. Retire a collision before changing its primary's
currency within the same transaction so uniqueness remains enforced.
Authorize and validate postings and denomination effects in that same
transaction. This planner has no apply operation.
