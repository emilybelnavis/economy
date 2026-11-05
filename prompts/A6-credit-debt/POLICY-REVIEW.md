# A6 missing-policy review

Status: **approved by the user on 2026-11-04**. The user directed: “quadruple the bank funding to 28 billion. remaining proposed policy set approved as well.” All allocations below are four times the reviewed values; all other policies are approved unchanged. This record and the three linked detailed contracts are normative additions to the A6 specification. No remaining policy-approval blockers are recorded; A6-01 contract/interface document checkpoint is delivered in [CONTRACT.md](CONTRACT.md); no runtime behavior is implemented or verified.

## Approved policy set

| Area | Approved policy | Detailed contract |
| --- | --- | --- |
| Pricing | Starting prime 6%; Tier1 bank base8%, Tier2 base1.5%; Tier1 FX0.50%, trading0.25%; Tier2 zero; deposit APR0.50%/1.00% | [Credit, pricing and funding](POLICIES-CREDIT.md) |
| Credit/underwriting | Score300–900 from explicit report/utilization/age/inquiry components. Combined card ceilings1,000–500,000 by score; bank loan floor620, subprime500; Tier1 bank principal25,000–50M, Tier2 four times those ceilings. First card remains score650/limit2,500 | [Score/limit tables](POLICIES-CREDIT.md) |
| Funding | Explicit one-time finite28B server-currency issuance into institution capital:18×400M Tier1 banks,5×4B specialists,400M subprime,400M collector. No ongoing automatic recapitalization and no use of customer deposits as free lender capital | [Funding and debt-sale accounting](POLICIES-CREDIT.md) |
| Tier/account policy | Authoritative eligible balances minus unpaid debt; daily reevaluation plus action checks; immediate downgrade with safe access to existing funds/debt. Preserve primary and four unique-currency accounts; explicit matching-bank onboarding/migration | [Wealth, institutions and branches](POLICIES-BANKING.md) |
| Cards/CHF/hardware | Initial issued cards free, replacements25 server-currency units; issuer-bound active token; approved CHF seed1CHF=1.60CAD, explicitly a game seed, not a market quote. Branded station migration and exact recipes/geometry/access are specified | [Cards, CHF and terminals](POLICIES-BANKING.md) |
| Servicing | No late/default fees, no post-default interest; old minimums retain original due dates; paid defaults remain reported to original7y expiry; collection expiry cannot revive debt; manual repayment with opt-in autopay; full payoff has a30-second fixed quote with an explicitly audited waiver of intervening unposted interest | [Servicing and schedule](POLICIES-SERVICING.md) |
| Agent | One nonhostile20-health agent per debtor, no loot/XP, bounded loaded-chunk spawn/path search, persistent7-day kill cooldown and debtor-only dialogue/reminders | [Agent behavior](POLICIES-SERVICING.md) |

All rates and money are game policy. Monetary amounts are major units of the current server currency unless a currency is explicitly named; converting amounts uses authoritative A2 quotes and minor-unit rounding. This is not a claim about real financial institutions, rates or real credit bureau formulas.

The28B seed is new finite in-game money, not merely a bookkeeping relabeling of existing player balances. The user explicitly accepted this issuance, with all funding allocations quadrupled. Initial prime/rates are versioned configuration; fixed loan contracts retain their captured rates. Changing defaults later requires a new effective version and cannot recalculate old charges or create replacement operation identities.

## Blocker coverage and ownership

| Decision | Approved contract owner |
| --- | --- |
| D01 minimum basis/carry-forward | Servicing |
| D02 repayment/grace/early payoff | Servicing |
| D03 default/cure/post-default accrual | Servicing |
| D04 reporting/corrections/paid defaults | Servicing; credit scoring consumes reports |
| D05 acknowledgement/expiry/revival | Servicing |
| D06 scoring and card limits | Credit |
| D07 loan underwriting and exposure | Credit |
| D08 prime and captured rates | Credit |
| D09 fee/deposit rates and yield | Credit |
| D10 wealth/tier/downgrade | Banking |
| D11 issuer matrix/accounts/migration | Banking |
| D12 lender funding/collection sale | Credit; Servicing defines frozen-case fallback |
| D13 agent lifecycle/gameplay | Servicing |
| D14 cards/replacement/advances | Banking; Servicing defines payment execution |
| D15 timing/cutoffs/precision/autopay | Servicing; Credit defines score/deposit inputs |
| D16 CHF seed/cash/change | Banking |
| D17 branded hardware/kiosk migration | Banking |

Where a detailed contract overlaps another: Servicing owns deadline, payment, default, reporting/collection and debt-quote behavior; Credit owns score bands, product credit limits, exposure, rates/fees and institution capital; Banking owns player account/card-token limits, issuer/network compatibility, net-worth admission, terminal binding and physical cash policy. Separate quote lifetimes are intentional: debt payoff30 real seconds; bank FX/valuation60 real seconds or their underlying A2 quote expiry, whichever is sooner. A quote is always invalidated by a relevant state revision or expired debt-collection eligibility.

## Execution handoff after approval

All D01–D17 policy inputs in this set are approved. A6-01 applied these contracts in [CONTRACT.md](CONTRACT.md), with current upstream inventory, shared identifiers/state vocabulary and persistent fact/migration/interface boundaries. It does not need another approval for these choices. Policy approval is not evidence that A6-01 or a downstream implementation is complete.

Execute one package at a time under the existing phase approval, with actual prerequisite evidence and its scoped checks. Preserve the approved calendar, APRs, starter entitlement, roster, four-account cap, kiosk visit requirements and all balances/worlds. Material newly discovered incompatibilities must be reported with a concrete proposed resolution; do not resurrect superseded draft blockers.
