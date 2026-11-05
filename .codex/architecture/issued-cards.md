# Issued debit card contract — A6-06

## Identities, schema and facts

Schema V23 adds `issued_cards` and `issued_card_fee_quotes`. Token rows persist world/token/owner/issuer/account/product/network/tier/provider epoch/status/issued UTC/expiry/revision/previous token/custody ID. A unique active-debit index enforces one ACTIVE token per BANK account. Only DEBIT is implemented; CREDIT is a reserved distinct product and cannot be constructed/admitted through this handler. No facility, score or balance database is introduced.

Token UUID = original issue operation UUID = custody UUID. New v1 fact `economy:issued_card` encodes the15 token columns in schema order, pipe-separated; its registered validator checks exact fields and stable approved issuer/tier. Status changes append a new fact/revision, never rewrite history. `economy:cards/economy:issue` and `/economy:revoke` use canonical v1 intents. Issue binds server lease/session/action/issuer/account and account/provider/denomination/Tier revisions, previous token and complete fee witness (opaque capture bound by SHA256). Revoke binds authenticated owner/token/revision. Lease consumption, token changes, monetary projections, custody and outcome commit atomically.

`issued_card_fee_quotes` consumes each paid quote once with its operation ID, including same-currency quotes. `economy:card-replacement-fee` v1 records17 fields: fee ID/world/owner/issuer/funding account/funding revision/denomination revision/world currency/world amount/funding currency/principal/FX surcharge/FX quote ID/expiry/card account/FX publication/pricing version. Existing posting v1–3 and service-pricing validators remain unchanged. Exact schema validation runs at open/recovery/offline inspection; complete-world backup/restore recognizes V23. V22 migration is additive and requires the existing stopped-world upgrade backup.

## Lifecycle and pricing

First issuance is free after account/provider assignment at that issuer kiosk; no login, generic card, ATM, teller or unmarked item can issue. `BranchAdmission.Action.CARD_ISSUE/CARD_REPLACE` use A6-05-01 live actor/world/session/anchor/dimension/generation/reach/revision revocation checks. Same-currency institutions cannot substitute (including Lakeward/Newhaven CHF). Current account/provider/bank capability and specialist Tier eligibility are checked independently of terminal ownership and artwork. Presentation Tier is derived from a fresh eligibility snapshot.

Expiry is issued UTC +1,820×1,200,000ms (five financial years). `usable` derives expiry at each check; effective EXPIRED does not require a periodic write and does not close the account. Renewal/replacement is free at/after expiry−28×1,200,000ms, including naturally expired tokens. Earlier owner-requested replacement costs25 world-major units; insufficient funds leaves the old token usable. Revoked/lost tokens retain their expiry and replacement linkage. Provider changes create a new epoch and a new issuer's first card is free.

Remote owner lost reporting immediately revokes the token, free. All copies share its status. Assignment/target switch/closure atomically compose a bounded one-token revocation; source switch authorization alone retains access. Rebase collision retirement revokes the retired account's token; a rebased permanent primary keeps its token/account/provider exception. Fresh validation also checks current OPEN account, issuer and debit epoch, so stale rows cannot revive access.

`CardReplacementPricing` holds≤1,024 short-lived server-session quotes. Same-currency principal is exactly25×world minor units. Foreign funding uses existing `FxRequests` EXACT_CREDIT for25 world-major units with CEILING source debit, captured publication and ordinary source-bank FX pricing. Combined principal+fee is displayed once. Explicit unassigned legacy funding keeps the existing legacy FX pricing; no new legacy pricing exception is invented here. Quote capture binds target card/funding identities, owner/world/issuer, funding/provider/denomination revisions, time and canonical inputs. Preparation refreshes FX/Tier/pricing, rechecks availability and consumes both card and FX quote IDs in the same issuance transaction.

The base service fee transfers funding-currency cash to the issuer's typed CASH and recognizes INCOME/customer EXPENSE; no fee is charged to a credit facility. `FeeSettlement.charge` factors the existing cash/income recognition; `composeCash` overlays the base transfer before the separate ordinary FX surcharge, preventing aggregate overdraft. Facts retain world25/source conversion and publication; no world-currency cash is minted by this conversion. First issuance, maintenance, revocation and account/card closure add no charge.

## Items, owner API and menus

`CardDesigns` is a pure approved resource catalog. `CardCatalog` registers28 `economy:issued_<bank>_debit_t1|t2` single-stack items using A6-05 models. They have no usable-token creative tab, recipe or loot acquisition. `CardStackIdentity` reads exactly two canonical UUID references (world/token) from bounded CUSTOM_DATA. Items contain no account UUID, owner secret, score, limit or private data. Public art/tooltips show issuer/product/Tier; authenticated menus display expiry/status.

`LedgerService` exposes owner-granted account card lookup, fee quote, issue/revoke submissions and live owned-token validation. It never accepts client financial postings or evidence. Item entry and every issued-card packet validate the current owned token through the worker and recheck the actual held design/marker. Generic cards retain their prior independent access; issued cards cannot enter through that fallback. Issued-card FX also checks token usability at final worker preparation. Normal owner account queries/repayment contracts remain independent of possession.

Protocol4 adds cards/card_select/card_preview/card_confirm/card_lost. Only KIOSK allows issuance/replacement preview/confirm; ordinary authenticated banking surfaces allow owner card views/lost reporting. Issuer review uses server-offered account/funding choices and final sealed proof. Kiosks can collect protected issued items. Pending operations retain original canonical intent and operation ID; unknown outcomes resolve by ordered lookup. An absent issue outcome requires a new live issuer request, never automatic resubmission with a dead lease. Credit-card/loan applications remain unavailable.

## Physical delivery and recovery

Issue atomically creates `Custody.committedDelivery`, items payload `issued-card-v1\n<world>|<token>|<item>`, source `issued-card:<token>`, destination canonical owner actor. No item is inserted during financial preparation or replay. Collection rereads COMMITTED/CLAIM, journals BEGIN_DELIVERY/COLLECT, rereads the exact DELIVERING attempt, then uses `CardInventory` to insert one item with an operation-bound saved marker. Full inventory or a disconnected/dead/replaced audience before insertion produces a proven undelivered owner claim. A stale disconnected player is not saved over a newer login.

`BankingSaves.player` verifies the actual player inventory/marker save; only current DELIVERED custody permits marker cleanup. Any partial/uncertain materialization quarantines; retries never speculate another usable token. SQLite commit, inventory insertion and disk saves are non-atomic and explicitly bridged by A5 custody witnesses. Physical clones are not individually distinguishable; their one token record supplies shared status and no duplicate facility/benefit.

Startup reconciliation scans≤16 cases per worker turn with a weak per-store cursor. It retains a strictly verified card CLAIM with UNDELIVERED evidence and terminal DELIVERED cards. A COMMITTED token case with no begun attempt/ack/witness becomes a journaled CLAIM because protocol proves insertion never started. Validation matches authoritative token/world/owner/payload/source/destination and original immutable COMMITTED issue outcome/canonical intent. DELIVERING/ACKNOWLEDGED/forged cases quarantine as before. No raw prefix skips validation; restart resets the cursor and safely rescans. Legacy non-card custody retains existing reconciliation semantics.

No recurring credit/debt domain, first-score entitlement, loan/advance, merchant credit draw or agent behavior is activated. A6-08 owns facility-linked credit products, token presentation and atomic credit/profile establishment; A6-12 owns integrated applications.

## A6-07 boundary

[Credit profiles](credit-profiles.md) remain absent on debit issuance, including replacement and collection. Only future actual credit-card origination may atomically consume the one650/2,500 starter contract. Current debit behavior, tokens and custody remain unchanged.

## A6-08-01 extension

A6-08-01 adds separate facility-linked credit tokens and network appearances. The historical debit v1 token/fact remains debit-only. Credit uses the same protected item delivery bridge; see [credit facilities](credit-facilities.md).
