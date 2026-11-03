# A3 accounts, identity and permissions

Draft for review. The preservation requirements below come from the A3 brief. Approved gameplay decisions are listed below. Execution packages remain drafts pending explicit invocation and verified prerequisites. A1 is verified. The A2 design is approved, with execution and integration evidence still pending.

## Dependencies and ownership

- **A1** supplies authenticated request context, per-world lifecycle, the bounded single storage worker, immutable journal, durable outcomes, and migration and recovery rules. Target Minecraft 1.21.1, NeoForge 21.1.252 and Java 21 on both dedicated and integrated servers.
- **A2** owns currency IDs, checked minor units, quotes, rates, denomination revisions, chooser/disconnect behavior, and the actor-bound double confirmation. The permanent FX reference and historical facts survive denomination changes. A3-02 requires the delivered registry contract. A3-05 additionally requires the delivered denomination change contract, including the conversion inputs A4 will consume.
- **A3** owns party identifiers, player authorization, stable account identities, product constraints, protection, lifecycle eligibility and authorized discovery. The account product defines which currencies are eligible. Currency balance keys remain separate from account identity.
- **A4** owns balances from balanced postings, holds, movement, funded closure and the atomic rebase transaction. **A5** owns physical teller access, cash and merchant interaction. **A6** owns credit and debt policy. **A8** owns chooser/admin presentation. **B2** owns securities custody and the brokerage transition. Market consumers use Stage A funding services and custody services through their respective interfaces.

## Proposed specification

**Identity and authority.** Use typed party keys scoped to the world. Players retain Minecraft UUIDs. Reserve typed organization identifiers for later integration. Merchant and treasury are account purposes with explicit owners. Distinguish the authenticated actor, owner and account on every durable mutation. Display names are mutable and unsuitable as authority or stable lookup keys. Players act for themselves. System accounts require trusted internal authority. Only server operators may use A3 administrative access to inspect another party’s accounts. Resolve operator status from the trusted server command source and audit administrative access. This authority does not authorize spending or bypass account protections. Organization creation, role assignment, delegated access and revocation belong to the [deferred integration task](../planning/deferred-organization-identity.md). Reject organization-facing requests until an authorized integration exists.

Resolve sender, session and interaction context on the server thread. Transfer bounded immutable inputs to the storage worker. Check ownership and entity revisions at the serialized decision point, including before disclosing original operation outcomes. Recheck the publication audience when delivering sensitive data. Use existing A1 readiness, overload, retry and postcommit publication semantics.

**Products and lifecycle.** Use immutable account IDs, typed purposes, explicit owner and status, and monotonically advancing entity revisions. A player's bank accounts are each single-currency under the legacy workflow. Count the primary within three open bank accounts and enforce uniqueness per player and currency. Provision the permanent primary idempotently after denomination selection on first banking initialization. Its ID survives rebase. Protect the primary and any bank account matching the current world denomination at commit time. Keep a separate trading funding identity, one per player, outside the bank cap. Keep product eligibility explicit. Defer organization, merchant and treasury opening policies to their integrations. Closed accounts remain addressable to authorized audit readers and never reactivate. A later opening uses a new ID.

**Funds and closure.** Define posted balance per account and currency as the sum of available, reserved and restricted funds for the ordinary nonnegative products. Reserve funds against identified obligations. Restrict funds through identified policy claims. Categories are mutually exclusive. A4 defines any signed system-account exceptions. Store amounts as checked A2 minor units, and let A2 determine conversion rounding. For example, 10,000 minor units with 2,500 reserved and 500 restricted leaves 7,000 available. Overflow, stale revisions, active holds or unresolved obligations block closure. For example, closing a funded EUR account requires a distinct open bank destination owned by the same player. A4 validates the quote and credits the destination in the same transaction as source settlement and closure. A3 can report eligibility, but teller opening and closure stay unavailable to players until the access boundary is wired in A5. Funded closure stays unavailable until A4 supplies settlement.

**Rebase.** A3 supplies a revision-bound mapping to A4. For a change from CAD to USD, preserve the primary ID, change its active currency to USD, and merge the existing USD bank account into it if present. Retire the USD account with a successor reference, retaining original currency metadata and historical IDs. Preserve unrelated foreign accounts, such as EUR. The merger must leave no duplicate active bank currencies and retain the primary. With CAD primary, USD and EUR, the result has two open bank accounts. Block a rebase with unresolved holds or obligations in affected accounts until A4 supplies an approved policy. Recompute mappings and protection under the write transaction. Commit denominations, postings, account transitions and successor links together in A4. A failed or stale rebase leaves the prior accounts and denomination intact. A3 never rewrites historical currency amounts or A2 frozen displays.

## Feature coverage

| ID | Historical behavior | Proposed replacement and owning package | Acceptance and handoff |
| --- | --- | --- | --- |
| F06 | First-player chooser, replacement on disconnect, actor-bound double-confirmed rebase | Consume the approved A2 denomination revision and confirmed change contract in A3-05 | Wrong actor or stale revision cannot apply a mapping. A2 owns protocol and A8 owns UI. |
| F06 | Foreign accounts survive, target collisions merge, historical currency and frozen projections survive | A3-05 defines primary survival, source retirement and successor mapping | CAD/USD/EUR example above, no-collision case and stale-plan rejection. A4 commits monetary and account effects atomically. A2 owns frozen history, with B1/B3/B4 owning market consequences. |
| F07 | UUID ownership, permanent primary, up to three distinct-currency BANK accounts and primary/world protection | A3-01 authority, A3-02 products, A3-03 transitions | Concurrent duplicate openings or attempts to take the last slot yield at most one success. Primary and current denomination accounts reject ordinary closure. |
| F07 | Separate TRADING account | A3-02 defines one funding identity outside BANK limits | Bank cap excludes trading, and trading cannot be selected as a bank closure destination. B2 owns brokerage transition. |
| F07 | Teller-only opening/closure and funded conversion into a selected owned bank account | A3-03 defines lifecycle and A3-04 defines eligibility and access contracts | Non-teller requests are rejected. Foreign-owned, stale or closed destinations fail. Closed IDs retain audit references. A4 owns funded settlement and A5 owns physical access. |

No behavior changes are approved by this draft. See the approved policy entries below for decisions made during review.

## Common execution contract for future prompts

Each package requires explicit invocation, verified prerequisites and approved decisions applicable to its scope. Read this specification and only the relevant A1/A2 contracts. Use the A1 storage migration and validated backup policy for schema changes. Persist each accepted mutation's records, projections and outcome atomically with versioned canonical requests and original operation IDs. Early authorization, malformed input and overload failures do not consume an ID. An equivalent authorized replay returns the original durable outcome. A changed payload conflicts. A disconnected client can query or retry the original ID. An uncertain commit follows A1 recovery, and delivery failure never triggers a second mutation.

Use server-owned time for UTC audit records and journal sequence for commit order. Use revisions for optimistic checks. Keep caps, permissions and state checks in the transaction or consistent read snapshot. Bound request and response sizes using A1 limits and report private diagnostics through existing codes and counters without account data in routine logs. No new settings unless the approved package identifies an operator need. Each package must define relevant unit and storage integration fixtures, restart/recovery cases and bounded query or load checks. For runnable deliveries run the required Java 21 build, runClient help and dry run, with configured runClient/runServer, registered mod source set and required runtime dependencies. Record scope and results in the package handoff.

## Package split decision

Keep five packages. Each package includes its diagnostics and acceptance gate. The three-part split is warranted when a package introduces a persistent schedule or independently managed configuration whose behavior, operator visibility and recovery need separate verification. A3 currently introduces neither.

| Package | Trigger and configuration | Diagnostics and acceptance in the same package |
| --- | --- | --- |
| A3-01 | Authenticated player requests and trusted internal calls. | Record private authorization outcomes. Verify ownership checks, world isolation and retry disclosure. |
| A3-02 | First authorized use after denomination selection. Fixed product rules. | Distinguish denomination pending from provision failure. Verify concurrent and post-restart provisioning. |
| A3-03 | Explicit lifecycle requests. Fixed three-account cap. | Distinguish duplicate currency, cap, protection and stale state for authorized callers. Verify races and interrupted writes. |
| A3-04 | On-demand queries using A1 worker bounds. | Surface ledger unavailability and query admission failures. Verify access, bounds and paging under change. |
| A3-05 | Explicit confirmed A2 intent. A4 owns application. | Record stale mapping, unavailable prerequisites and encumbrance blocks. Verify account mapping with fixtures and hand off atomic application to A4. |

Revisit the split only if review introduces scheduled provisioning, grant expiry, scheduled rebase or independently managed policy configuration. In that case split the affected package in this order: scheduling and configuration, diagnostics, foundation acceptance.

## Ordered future prompts

### A3-01 — Parties and authorization

Owner is A3 identity. Requires verified A1. Define world-scoped typed party references and audit actors. Expose trusted player ownership checks, server-operator-only administrative access and an internal system authority boundary using detached request context. Reject forged actor claims, unauthorized lookups and mutation attempts, and cross-world requests. Verify that a server operator can perform scoped administrative inspection and that an ordinary player or forged operator claim is denied. Verify authorized retry disclosure and durable audit identity across restart. Exclude organization registration, grants, accounts and money.

### A3-02 — Account catalog and product constraints

Owner is A3 account structure. Requires A3-01 and the verified A2 currency registry and denomination-read contract for primary provisioning. Define account metadata, active currency eligibility, stable IDs, statuses, revisions and indexed uniqueness. Provide trusted account lookup and idempotent primary initialization under the owning party's authority. Commit account creation and its journal record together. Verify before/after denomination initialization, competing initialization, separate trading identity and retained IDs across restart. Measure bounded indexed reads against closed-account history. Exclude teller actions, balances and account conversion.

### A3-03 — Lifecycle and limits

Owner is A3 lifecycle. Requires A3-02. Define trusted opening, closure eligibility and transition contracts. Apply currency uniqueness, three-account cap, ownership, expected revision and current protection in the serialized write. Recheck eligibility in the eventual A4 closure transaction. Use fixtures or trusted internal callers to verify lifecycle while player opening and closure remain gated pending A5. Verify competing opens, same-ID replay after disconnect, stale destination rejection, no partial state on interrupted writes and durable audit references. Keep the three-account limit fixed for this design. Exclude physical access, funded closure and live rebase.

### A3-04 — Funds vocabulary and account queries

Owner is A3 read and integration contracts. Requires A3-03 and A2 monetary types. Define a versioned per-currency view and funding eligibility interface for A4 and B2. Until a ledger is available return a distinct unavailable status for financial queries. Use bounded indexed owner queries with stable cursor ordering and authorize each page and stable-ID lookup against a consistent read snapshot. Return an indistinguishable result for unauthorized or absent IDs in ordinary discovery. Test malformed cursors, stale views, cross-owner requests, checked arithmetic against fixture values, no monetary mutation entry points, and paging under concurrent lifecycle changes. Exclude ledger projections, public packet/UI wiring and money operations.

### A3-05 — Rebase mapping and integration handoff

Owner is A3 rebase account policy. Requires A3-04 and verified A2-05. Define the trusted planner inputs and revision-bound mapping, including survivor, retired account, old/new currency and protection. Consume authorized change intent and read bounded data through the worker. Require A4 to revalidate all affected rows under the write boundary. Verify no collision, collision, preserved foreign accounts, stale mappings, idempotent intent reuse, and rejection when the ledger preconditions cannot be evaluated. Define a volume fixture to measure planning bounds and identify rebase transaction limits before A4 integration. Exclude executing live rebase, FX calculation, denomination mutation and postings. Deliver account contracts and cross-phase acceptance fixtures for A4 and A5.

## Decisions before execution

### Approved A3-01 policy

A3 exposes audited cross-party account inspection to server operators only. A3 exposes no operator account mutations. Later packages must explicitly define and authorize each operator mutation they expose and enforce the A3-01 operator boundary.

### Approved A3-02 policy

Create the permanent primary bank account on the player’s first authorized banking use after world currency selection. Initialize it in the current world currency and resolve concurrent first-use requests to the same account ID. A4 must reject an incoming account-based payment if the recipient has not initialized their primary account. The rejection creates no account and moves no funds. Verify that the payment can proceed as a new intent after the recipient initializes banking, subject to ordinary payment checks.

A3 defines one separate trading funding identity per player, outside the BANK cap. B2 decides which currencies it can hold before brokerage funding is enabled.

### Approved closure and rebase eligibility

Block closure when the source has an active hold, restricted funds or an unresolved obligation. Block a world rebase when such a claim affects an account being redenominated or merged. Claims on unaffected accounts stay in place. Give the authorized requester the reason and allow a new attempt after resolution. A4 rechecks eligibility inside the settlement transaction.

### Approved rebase collision rule

Keep the permanent primary account ID and change its active currency to the new world denomination. Merge any existing player BANK account in that currency into the primary and retire its ID with an audit successor link. Preserve unrelated foreign BANK accounts. Trading and other account products stay outside this conversion pending their own policies. A4 applies the account transitions, postings and denomination revision atomically.

### Technical decisions and dependency gates

- **A3-01 and A3-04** can specify ownership checks, query bounds, cursor behavior and non-disclosing errors from A1's existing contracts. These are technical design choices. They do not need separate gameplay input.
- **A3-02** needs the delivered A2 registry and a verified denomination read for primary provisioning. **A3-05** needs the delivered A2 confirmed-intent and denomination-revision contract. Resolve exact input fields against those implementations.
- **A4** must specify funded closure and rebase postings, conversion inputs and rounding under the A2 contract, treatment of claims if allowed, and transaction sizing for a world-wide rebase. A3 can finish the metadata contracts and fixture mapping before those money operations are enabled.
- **A5** must specify the authoritative teller interaction and disconnect policy. If a write has already been accepted into the worker queue, retain A1's outcome and retry semantics.

The proposed package gates and acceptance scenarios have not been executed.
