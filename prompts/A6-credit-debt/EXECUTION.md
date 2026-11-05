# A6 execution preparation

## Approval and current state

On 2026-11-04 the user stated: “all of the draft prompts have been reviewed and are approved for execution. Prep everything for execution.” This approves the 23 A6 prompts, their stated scope, acceptance gates and explicitly written recommendations. It supersedes their earlier draft-only and pending-approval labels. It does not select an implementation package in this preparation turn, fabricate missing policy values, authorize world resets or start A7/Stage B.

**Preparation complete; no A6 package started or verified.** A6-01 is ready to select. All remaining packages are approved and queued behind prerequisites. A completed A1–A5 gate is upstream evidence, not A6 evidence. No code, assets, dependencies, schema, worlds, README release text or changelog changed in this preparation.

Approval precedence: this record and the user's later instructions → approved specification/branding → package scope and recorded A6-01 contracts. Earlier dated statements that these drafts are unapproved are historical. Preserve shared contracts and bring material conflicts forward; do not ask again for the same prompt approval.

## Approved recommendations

- Original names, palettes, motifs, network names and roster home currencies, including Alpine Vault CHF. A6-01 assigns stable IDs; issuer/network compatibility still needs a concrete matrix.
- CHF two-decimal ledger precision; notes 10/20/50/100/200/1,000; coins 0.05/0.10/0.20/0.50/1/2/5. Electronic balances are not restricted to 0.05 multiples. Original artwork, not official-note reproductions.
- Card minimum uses pre-interest principal: min(total due, ceil(2%*principal + period interest)); no interest-free purchase grace; oldest arrears, then interest, then highest-APR principal payment allocation. Positive representable payments only, capped at full payoff. Arrears carry-forward detail remains to specify.
- Default at the oldest unmet due date +84 days. Reporting snapshots at due+2 days, allowing cure before reporting. Two-year collection clock begins at default/transfer; positive partial payment before expiry resets collectability, never the original seven-year reporting expiry. Merely opening dialogue acknowledges nothing.
- Fixed-at-origination loans with captured prime/rate; unsecured initial products; explicit unsupported secured/variable/margin capabilities. No implicit repricing or hidden minting.
- Net worth is approved supported assets minus all liabilities, counted once at authoritative FX/marks; borrowed proceeds are offset by debt; unavailable/stale valuations cannot qualify.
- One visible agent per debtor aggregating eligible cases; per-debtor seven-day kill cooldown; online-only spawning, no forced chunks, dimension/relogin reconciliation, no loot/XP farming.
- Nontransferable borrowing authority, replacement revokes old token, no recipes creating valid issued cards. Origination-anchored statements, world-aligned scoring; due/payment settlement before reporting before scoring.
- Four BANK accounts total including primary; retain unique currencies and protected primary. Bank-bound kiosk admission for opening/closing/replacement; kiosk base currency follows its bank. Preserve existing IDs/worlds through explicit migration rather than automatic color-to-bank guessing.

All original user requirements remain approved: UTC 20-minute days including downtime; 7/28/364-day week/month/year; APR/13 and 13 loan payments/year; 23 institutions, Tier 2 threshold100M, first credit score650/limit2,500, specified card APRs, terms, reporting/collections and branch rules. See the specification for exact units and complete coverage.

## Approved inputs for A6-01

The user approved [all remaining policies](POLICY-REVIEW.md) on 2026-11-04, with institution funding quadrupled from the reviewed7B to **28B**. All other policies are accepted unchanged. The detailed [credit/funding](POLICIES-CREDIT.md), [banking](POLICIES-BANKING.md) and [servicing](POLICIES-SERVICING.md) contracts are normative specification inputs. There are no remaining policy-approval blockers; do not ask again for these choices.

A6-01 still must inventory current contracts and produce the shared interface/state/fact/migration design and handoff. Implementing a policy is separate from approving it. If a new incompatibility is demonstrated during that work, report its exact impact and continue unaffected work.

## Package queue

Execute in the table order as a conservative serial path; independent-ready packages may be selected separately. Each package owns its scoped acceptance and handoff. Do not claim completion based on approval. Update this table and the project index only from actual results; failed gates retain blockers.

| Package | State | Prerequisites |
| --- | --- | --- |
| [A6-01](A6-01.md) | Ready to select; contract checkpoint | Verified A1–A5 |
| [A6-02-01](A6-02-01.md) | Approved; waiting for prerequisites | A6-01 |
| [A6-02-02](A6-02-02.md) | Approved; waiting for prerequisites | A6-02-01 |
| [A6-02-03](A6-02-03.md) | Approved; waiting for prerequisites | A6-02-02 |
| [A6-03-01](A6-03-01.md) | Approved; waiting for prerequisites | A6-01 |
| [A6-03](A6-03.md) | Approved; waiting for prerequisites | A6-01, A6-02-03, A6-03-01 |
| [A6-04](A6-04.md) | Approved; waiting for prerequisites | A6-03 |
| [A6-05](A6-05.md) | Approved; waiting for prerequisites | A6-01, A6-03 |
| [A6-05-01](A6-05-01.md) | Approved; waiting for prerequisites | A6-03, A6-05 |
| [A6-06](A6-06.md) | Approved; waiting for prerequisites | A6-03, A6-05, A6-05-01 |
| [A6-07](A6-07.md) | Approved; waiting for prerequisites | A6-01, A6-02-03, A6-03 |
| [A6-08-01](A6-08-01.md) | Approved; waiting for prerequisites | A6-04, A6-06, A6-07 |
| [A6-08-02](A6-08-02.md) | Approved; waiting for prerequisites | A6-08-01 |
| [A6-08-03](A6-08-03.md) | Approved; waiting for prerequisites | A6-08-02 |
| [A6-09-01](A6-09-01.md) | Approved; waiting for prerequisites | A6-04, A6-07 |
| [A6-09-02](A6-09-02.md) | Approved; waiting for prerequisites | A6-09-01 |
| [A6-09-03](A6-09-03.md) | Approved; waiting for prerequisites | A6-09-02 |
| [A6-10-01](A6-10-01.md) | Approved; waiting for prerequisites | A6-08-03, A6-09-03 |
| [A6-10-02](A6-10-02.md) | Approved; waiting for prerequisites | A6-10-01 |
| [A6-10-03](A6-10-03.md) | Approved; waiting for prerequisites | A6-10-02 |
| [A6-11](A6-11.md) | Approved; waiting for prerequisites | A6-05, A6-10-03 |
| [A6-12](A6-12.md) | Approved; waiting for prerequisites | A6-05-01, A6-06, A6-08-03, A6-09-03, A6-10-03, A6-11 |
| [A6-13](A6-13.md) | Approved; waiting for prerequisites | A6-12 |

## First-package launch

Copy into the next execution chat:

> Execute A6-01 under the recorded A6 approval. Read prompts/A6-credit-debt/A6-01.md, EXECUTION.md, SPECIFICATION.md and BRANDING.md, plus only its stated current upstream contracts. Apply the approved POLICY-REVIEW.md and its credit/banking/servicing contracts (including28B funding), then produce the shared contract/interface/migration checkpoint; do not re-request approval for written recommendations already accepted. Preserve existing code, worlds and backups. This package is contracts only: review its document diff, do not run Gradle or generate assets. Write its handoff/status and stop before A6-02-01 or A6-03-01.

For later packages use “Execute <exact A6 ID> under the recorded A6 approval,” confirm prerequisite evidence and load only that package/references. Existing project one-package boundaries remain in force; phase approval eliminates repeated approval requests, not verification.

## Checks and handoffs

Main agent owns shared interfaces, integration and all sequential Gradle/game tasks. Substantial independent work may use the existing economy profiles with explicit disjoint file ownership, checks and acceptance; never silently substitute unavailable models/settings. Routine preparation stays with the main agent, with bounded read-only review where useful.

Contracts/docs-only packages: inspect diff and local links. Runnable implementation deliveries: meaningful targeted checks plus `./gradlew build`, `./gradlew help --task runClient`, `./gradlew runClient --dry-run`, following [the verification guide](../../.codex/VERIFICATION.md). Keep runServer, registered main mod source set and development runtime dependencies; runtime smoke checks when affected. One check owner, disposable fixtures only, no duplicate unaffected gates.

Each completed package records `.codex/<id>.md`, affected architecture contracts, actual evidence in `.codex/verification/<id>.md` when checks run, and project/phase queue status. Keep measured outcomes separate from designed guarantees. Do not precreate success evidence or migration versions before implementation. A6-13 owns final integration acceptance and usable release documentation; A7/B2/B4/B6/A8 remain later handoffs.
