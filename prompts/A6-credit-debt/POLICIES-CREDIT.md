# Approved credit, pricing and funding policy

**Status: approved by the user on 2026-11-04, with total funding quadrupled to 28 billion.** This is a normative D06/D07/D08/D09/D12 policy contract under the [consolidated approval record](POLICY-REVIEW.md). The other approved policies remain unchanged. Approval is not implementation or verification evidence; execute only the selected A6 package and its gates.

All money in these tables is in **current server-currency major units**, not cents and not each institution's home currency. Convert limits and exposures through one current authoritative A2 FX snapshot when the facility currency differs. Existing debts retain their contract currency; no historical repricing. Amounts settle in checked currency minor units. Value each liability/committed limit in server currency using the captured A2 quote and round exposure upward to its minor unit; convert maximum offered facility amounts downward to the facility minor unit so conversion cannot exceed a ceiling. The starter entitlement is a 2,500 server-currency limit budget, not 2,500 units of an arbitrarily weaker issuer currency. Rate values are game configuration, not claims about real-world lenders or credit bureaus.

## Deterministic credit score — D06

Credit is unestablished until the first actual credit-card issuance atomically creates score 650 and the first 2,500 limit. Its opening issuance is exempt from inquiry penalties. A bank/debit card, failed application or quote does not establish a profile. An unestablished player cannot take an unsecured loan yet.

At each approved world-aligned 28-day score boundary, compute a fresh score from immutable reported inputs rather than adding a delta to the previous score:

`score = clamp(300, 900, 650 + paymentPoints + utilizationPoints + agePoints - inquiryPoints - defaultPoints)`

Only reports effective at or before the boundary count. Historical recomputation uses the policy version and inputs captured for that boundary. Corrected reports replace the effective interpretation while preserving original audit facts; they do not replay prior financial effects.

| Component | Approved policy |
| --- | --- |
| Payment history | Inspect obligations with report dates in the trailing 364 financial days. A fully met minimum/installment at its due+2-day reporting snapshot adds 4 points; an unmet obligation subtracts 15. Sum and clamp this component to -90 through +100. A later cure stops future arrears but does not turn that historical missed snapshot into an on-time payment. |
| Default history | For each still-reportable default, subtract 120 while default age is less than 364 days, 90 from age 364 to less than 728 days, and 60 from age 728 until its original 2,548-day reporting expiry. Cap the total default deduction at 360. Payment or sale never resets default age. The default remains one dated report with the approved seven-year expiry; each recalculation applies its current deduction to the fixed 650 baseline, never subtracts that deduction from the previous score again. |
| Avoid double punishment | Obligations belonging to a facility with a still-reportable default contribute no positive or negative payment-history points while that default is reportable. Its default component applies instead. Other facilities continue to contribute normally. |
| Account age | `min(90, 2 * floor(daysSinceFirstCreditIssuance / 28))`. Closing an account, replacing a card or changing bank does not reset this date. |
| Hard inquiries | Five points per distinct accepted application inquiry in the trailing 84 days, capped at 30. Group all the owner's accepted credit-card/loan underwriting submissions into one inquiry per world-aligned seven-day bucket. Preview/soft quotes, malformed or unauthorized submissions, retries and first profile-establishing issuance create no additional inquiry. A structurally valid application accepted for underwriting counts even if its business decision is denial. |

Utilization uses the time-weighted mean ratio during the preceding 28-day cycle: total posted revolving principal plus posted and accrued interest divided by the total limit of active revolving facilities. Use an event-time-weighted ratio across authoritative balance/limit changes in the cycle, splitting at financial-day boundaries for bounded accumulation. Weight by elapsed active seconds, including first/last partial days; do not infer a full day of utilization from a last-second cutoff snapshot. Store daily area/duration accumulators and sum them over the cycle. A cycle with no active revolving facilities has utilization points zero; active facilities with zero owed amounts have exactly0% utilization and earn the0% row. Missing required snapshots make the recalculation unavailable; they are not assumed to be zero. A defaulted/closed facility is excluded from this denominator and handled by default/liability rules instead.

| Mean revolving utilization | Points |
| --- | ---: |
| Exactly 0% | +30 |
| Greater than 0%, at most 10% | +60 |
| Greater than 10%, at most 30% | +30 |
| Greater than 30%, at most 50% | 0 |
| Greater than 50%, at most 75% | -30 |
| Greater than 75%, at most 100% | -60 |
| Greater than 100% | -90 |

No randomness, player-name exceptions, real-money inputs or unpublished score modifiers. The profile records the six components and their source periods so the owner can understand the result. Score movement remains once per 28-day cycle even though reports arrive at due+2 days.

## Revolving limits — D06/D07

The table is the **combined committed credit-card limit across every issuer**, not a fresh allowance per bank. New applications may use only the remaining headroom. Maximum offered new limit is that headroom; the applicant may request a lower positive limit in whole major currency units, subject to the 500 minimum. If headroom is below 500, deny the new facility rather than issue a zero/tiny limit. Existing limits never increase automatically; an increase is a new underwritten request at the issuing bank kiosk.

| Current score | Combined revolving limit ceiling |
| --- | ---: |
| 300–499 | No new facility/increase |
| 500–579 | 1,000 |
| 580–649 | 2,500 |
| 650–699 | 5,000 |
| 700–749 | 15,000 |
| 750–799 | 50,000 |
| 800–849 | 150,000 |
| 850–900 | 500,000 |

**Starter exception:** an unestablished player's first card is exactly 2,500 at score 650, not the table's 5,000. The first issuance consumes 2,500 of the combined ceiling; another bank cannot grant another starter entitlement. A second facility is separately underwritten against the same 5,000 total and all existing exposure.

A reduced score does not automatically reduce an existing contractual limit, erase debt, trigger default or make a current balance negative. It prevents new cards/increases beyond the new ceiling. New draws still require their existing facility status, current contractual headroom, aggregate exposure and issuer liquidity. For card issuance/increases/draws the owner-wide exposure ceiling is the larger of the current score-band revolving ceiling and its permitted bank total-exposure ceiling at the current service Tier, or the permitted subprime ceiling when the score is below the bank floor. A score below500 permits no new draw/increase. A new draw increases exposure only when debt would exceed its already counted committed line; otherwise it consumes the existing reservation rather than adding principal a second time. Lowered score/exposure blocks further draws without forcing repayment or default of a current facility. A materially over-limit facility caused by interest can repay normally but cannot draw; interest alone is not a missed payment. Any optional later limit-reduction product requires a separately specified versioned policy.

## Loan underwriting — D07/D08

Bank and subprime offers are deterministic. The below score floors, terms, amount ceilings and aggregate exposure checks are additional to issuer/Tier admission, funding, account status and stale-input checks. A currently delinquent, defaulted or collectible facility blocks all new credit and limit increases until the arrears are cured or the case is settled/uncollectable; its remaining report still affects score afterward. Do not deny repayment or ordinary banking.

For the following tables, **total committed exposure** is the greater of contractual limit or current owed amount for each open revolving facility (never add its drawn balance to the limit again), outstanding nonrevolving loan principal and accrued/posted unpaid loan interest, plus any closed/defaulted revolving balance and all remaining collections liabilities. Count each liability exactly once after debt sale. A new application is admitted only if both the requested principal is within its product ceiling and `currentExposure + newPrincipal <= exposureCeiling`. All lenders share this owner-wide check. Uncollectable unpaid liabilities remain included until explicitly extinguished under the consolidated debt policy; a collection expiry is not automatic debt cancellation.

| Score | Tier 1 bank max new principal | Tier 1 total exposure ceiling | Score premium above base APR | Permitted bank terms |
| --- | ---: | ---: | ---: | --- |
| 300–619 | Denied | No new credit | — | — |
| 620–649 | 25,000 | 50,000 | +6.00 percentage points | 1–2 years |
| 650–699 | 100,000 | 150,000 | +4.00 pp | 1–3 years |
| 700–749 | 500,000 | 750,000 | +2.00 pp | 1–5 years |
| 750–799 | 2,000,000 | 3,000,000 | +1.00 pp | 1–5 years |
| 800–849 | 10,000,000 | 15,000,000 | +0.50 pp | 1–5 years |
| 850–900 | 50,000,000 | 75,000,000 | +0.00 pp | 1–5 years |

For an eligible Tier 2 service customer, multiply the table's maximum principal and exposure ceiling by four, retain its score/term restrictions, and divide its score premium by four. Tier 1 base remains `prime + 2 pp`; Tier 2 base remains `prime / 4`. Tier 2 membership does not erase poor credit or waive lender capital checks. Minimum bank principal is 1,000; terms are whole years.

| Score | Subprime max new principal | Total exposure ceiling | APR | Permitted terms |
| --- | ---: | ---: | ---: | --- |
| 300–499 | Denied | No new credit | — | — |
| 500–549 | 10,000 | 15,000 | 34.99% | 1–3 years |
| 550–599 | 25,000 | 40,000 | 29.99% | 1–5 years |
| 600–649 | 50,000 | 75,000 | 24.99% | 1–8 years |
| 650–900 | 100,000 | 150,000 | 19.99% | 1–8 years |

Minimum subprime principal is 500. All applications show the captured score, requested amount/term, permitted ceiling, APR and the precise denial reason. There is no application fee, income claim, hidden random approval or credit-score multiplier beyond these tables. Loans remain fixed at origination; customer Tier/score/prime changes affect later offers, not existing contracts. Use the approved `APR/13`, `13 * years` amortization convention.

## Initial prime, fees and deposit yield — D08/D09

Initial prime is **6.00% APR**, effective at the A6 lending activation boundary. It is versioned game configuration, not a live real-world rate. Configuration accepts prime from 0% to 50% inclusive, with at most four decimal places in percentage-point notation; invalid input rejects rather than clamps. A change takes effect only at a future financial-day boundary through A1 version scheduling. A8 may supply later prime publications; absent that adapter, the last committed configured prime remains in force. A card's approved network APR is independent of prime and Tier.

| Service | Tier 1 | Tier 2 |
| --- | ---: | ---: |
| FX conversion fee | 0.50% of exchanged source principal | 0 |
| Trading fee when B4 supplies execution | 0.25% of executed cash notional, both buys and sells | 0 |
| Deposit nominal APR | 0.50% | 1.00% |

No minimum flat fee, maximum fee, separate spread, account-maintenance fee, loan application/origination fee, prepayment fee or late fee in this initial policy. Other explicit lifecycle fees are owned by the consolidated card policy. A zero-value/nonexecuted/cancelled operation incurs no fee. A same-currency internal transfer is not FX and incurs no FX fee. Capture service Tier, supplying institution, currency, policy version and quote revision in each quote and revalidate at settlement.

FX fee settles in source currency, rounded upward once to the source minor unit, as a separate balanced fee posting. The full quoted source principal is converted by existing A2 rules; the source account must fund principal plus fee. Trading fee settles in the trade's cash currency; compute a per-order cumulative exact-fee accumulator so rounding across partial fills does not multiply fees, post only the incremental rounded amount, and refund no already-earned execution fee on cancellation. Tier 2's fee is exactly zero. Revenue belongs to the institution providing the quoted service, never a second market-owned bank.

Deposit yield uses world-aligned financial-day boundaries and the approved UTC calendar. For each eligible open BANK account, integrate its eligible posted balance over elapsed seconds between every authoritative ledger balance change. Its principal basis for a financial day is sum(balance×durationSeconds)/1,200 across that day. Add the prior-day-close unpaid accrued deposit-interest balance to that basis, then multiply by depositAPR/364; current-day unposted yield is not itself compounded until the next day. Split segments at Tier/rate-effective boundaries using the captured version. This gives unchanged balances the standard daily-compounding formula and apportions a transfer across accounts without either multiplying the same money's earning duration or creating a deposit-before-cutoff bonus. New accounts earn only for actual funded elapsed seconds after opening, including a partial first day. Held/reserved balances remain the owner's deposit and earn yield once; TRADING, loan balances, lender capital, cash items and duplicate representations do not. When a non-boundary closure occurs, settle its pending time-weighted segment pro-rata before final payout; no sub-day yield disappears. Tier changes apply at the next world financial-day boundary, prospectively. Deposit yield is based on account service status, so a below-threshold specialist's read/repay/withdraw/close-only account earns zero future deposit APR from that boundary while existing unpaid earned yield remains payable.

Pay out accrued yield every world-aligned 28-day boundary, retaining sub-minor-unit residuals. Final account closure realizes any payable whole minor units, preserves an auditable sub-minor residual disposition to the institution's rounding account, and cannot bypass closure/funding protections. The consolidated precision contract owns the numeric scale and settlement mode; this policy selects **HALF_EVEN** for yield payout and the final amortization adjustment, with 34 significant decimal digits, an 18-decimal-place major-unit accrual scale and HALF_EVEN internal operations. Posted yield uses HALF_EVEN with a retained residual; contractual minimums and scheduled installments use CEILING to the currency minor unit, with a final installment capped to exact payoff. Do not round the daily rate to a displayed percentage. Historical yield uses captured rates, Tiers and balances.

The 1.00% Tier 2 deposit APR is below the initial 1.50% best bank-loan APR, avoiding a guaranteed deposit/loan carry loop at initial settings. The policy does not silently rewrite an existing fixed loan if an administrator later chooses rates that permit carry.

## Finite institution funding — D12

This is an explicitly approved **one-time addition of 28,000,000,000 server-currency units to world money supply** at A6 institution activation. The user approved this visible economic design decision, not money concealed inside origination. The opening event posts through the approved issuance/treasury/equity journal contracts and existing A4 balanced postings, with world identity, allocation manifest, version and immutable operation IDs. It is never inferred from a missing table or repeated on restart. No player balance is changed by the seed itself.

| Institution class | Count | Own-capital cash allocation each | Total server-currency equivalent |
| --- | ---: | ---: | ---: |
| Tier 1 institutions, including hybrids | 18 | 400,000,000 | 7,200,000,000 |
| Tier 2-only specialists | 5 | 4,000,000,000 | 20,000,000,000 |
| Shared initial subprime lender | 1 | 400,000,000 | 400,000,000 |
| Shared initial collection agency | 1 | 400,000,000 | 400,000,000 |
| **Total** | **25** | — | **28,000,000,000** |

The shared subprime lender and collection agency are service/system parties, not two additional customer banks or account slots. A6-01 assigns their stable fictional identifiers. The initial seed is allocated in server currency, then converted once to each institution's approved base currency at the captured opening FX publication. Retain conversion residuals in the opening treasury rounding account. Missing opening FX prevents that allocation; it cannot substitute 1:1. Opening issuance and each allocation retain durable identities, and partial activation keeps dependent credit services unavailable until the manifest is complete.

System institutions may hold bounded own-capital subaccounts in every supported product currency; these do not consume player BANK slots. Before committing a foreign-currency card limit, loan, yield payout or collection purchase, explicitly exchange the required institution-owned cash through A2/A4 clearing at the captured authoritative quote. No player-service FX fee applies to this institution treasury conversion; record conversion/residual facts and never fabricate a funding currency. A revolving limit reservation holds the actual facility-currency cash, not a fluctuating unconverted server-currency promise; draws consume that reservation, and an increase funds a new reservation. Sale and due-yield queues retry with versioned quotes if treasury conversion is unavailable.

Customer deposits are segregated player balances; they are not bank-owned lending capital and cannot be spent by the bank. Existing balances are merely attributed during migration, never copied into own-capital cash. All card draws, cash advances, loans, deposit-yield payouts and debt purchases must be funded from the providing institution's own liquid cash, with atomic checks and explicit account transfers/receivable/liability/expense facts. Origination does not create unbacked spendable money. The borrower's net worth changes by the received cash minus an equal liability.

An issued revolving line reserves its **undrawn** limit against issuer capital. A draw consumes reserved cash and creates the funded receivable; principal repayment restores liquidity and the undrawn reservation. No bank can issue lines whose combined required reservations exceed its own available cash after other committed obligations. A loan reserves and disburses its whole principal atomically. Receivables are assets, not immediately spendable liquid cash; an institution cannot lend the same cash twice because it owns a receivable.

Retain 10% of each institution's opening cash seed, rounded upward to its base-currency minor unit, as an operating liquidity floor for interest and ordinary institutional expenses. New loan disbursements, new/increased revolving commitments and debt purchases must leave this floor intact after all undrawn commitments and due payables. Already promised, funded card limits may draw their reserved cash even if doing so crosses the operating floor; it may not consume customer deposits or another facility's reservation. Principal receipts replenish own cash; collected interest and service fees fund earnings; deposit yield is an expense paid out of that cash.

If there is insufficient cash for deposit interest, do not mint, silently skip accrual, freeze the global clock or pretend payment succeeded. Record the contractual accrued payable and the institution's distressed status; interest continues under its published contract and the payable remains shown separately from spendable balance. Future payout work pays oldest due yield first, then stable account-ID order within a due boundary, only using available own cash after protected card reservations. New credit and increases are disabled while due yield is unpaid. A durable deferred-payment outcome completes that day's economic processing; the liability is retained for later settlement. Existing player deposits remain available through their ordinary protected account balances.

There is **no automatic replenishment or bank failure/depositor haircut** in this release. Operators may make a separately authorized, fully journaled transfer from an already funded treasury or approve another visible issuance event; neither is triggered automatically by low capital. Capital/funding diagnostics expose own cash, undrawn reservations, due payables, receivables, losses and the exact failed admission condition.

## Sale to collections and loss treatment — D12

The collection agency purchases each eligible defaulted receivable for **10% of its outstanding principal**, rounded down to the debt currency's minor unit; existing unpaid contractual interest transfers with the claim but does not increase the purchase price. At least one minor unit is paid for a positive principal whose computed price rounds to zero. A pure-interest residual with zero principal transfers for zero consideration and remains auditable. The borrower's debt amount is unchanged by the discount.

Atomically transfer creditor/receivable ownership once, debit the collector's own cash, credit the seller's cash, and book the seller's difference between transferred carrying value and sale proceeds as a realized loss. The collector records acquired receivable face value and its acquisition discount/valuation allowance separately; its equity does not increase by the discount on the day of sale. Subsequent payments reduce face liability/receivable and release the appropriate discount into realized recovery income under a deterministic proportional basis, with the final payment taking the remaining rounding residual. No debt copy, automatic second charge or new borrower account is created.

Post-default interest and new fees stop under the consolidated servicing policy; only the frozen posted principal and earned unpaid interest transfer. If the collector cannot fund the purchase while retaining its operating floor, default and closure still occur on time, with sale status COLLECTIONS_PENDING_SALE. The originating lender retains ownership; the agency services the case as its authorized collection agent with **zero service fee**, and borrower payments go to the current lender. Queue the sale for a later day only while the case remains collectible and the seller still owns the same receivable; retry by the original sale intent. Entity behavior depends on collection eligibility, not on successful sale. This explicit servicing fallback avoids making capital exhaustion postpone default or spawn duplicate debt.

At a collectible debt's final settlement, clear the liability and creditor claim once. At uncollectability, stop demands and record a 100% collectability allowance/write-off against the owner institution's carrying asset; this accounting loss does not itself cancel the borrower's historical obligation or restart reporting. The consolidated debt policy determines extinguishment/revival, while default dates, reporting expiry and payment history remain immutable. Defaults and allowed write-offs never create a negative player cash balance or withdraw cash without an authorized payment.

## Numerical acceptance fixtures

- First issuance creates score 650 and one 2,500 limit. With no other facilities, score 650 has only 2,500 remaining aggregate revolving headroom; a retry or another issuer cannot create another first-card privilege.
- At one cycle age, one on-time reported obligation, 8% mean utilization, no inquiry/default gives `650 + 4 + 60 + 2 = 716`. One missed obligation with the same utilization/age gives 697; a newly defaulted facility is excluded from payment points and gives `650 + 0 + 0 + 2 - 120 = 532` when it is the only revolving facility. A single default is more harmful than a single miss.
- At score 650, Tier 1 bank base 8.00% plus score premium 4.00 pp gives 12.00% APR, maximum new principal 100,000 and exposure ceiling 150,000. An eligible Tier 2 customer at the same score gets base 1.50% plus 1.00 pp = 2.50% APR, principal ceiling 400,000 and exposure ceiling 600,000. Both still require issuer liquidity.
- Score 480 is denied bank and subprime credit; score 560 may qualify for subprime 25,000 maximum, 29.99% APR and a 1–5 year term. Score 650 with committed exposure 140,000 cannot add a 20,000 Tier 1 bank loan despite its 100,000 per-loan ceiling.
- A Tier 1 FX principal of 100.00 incurs a separate 0.50 source-currency fee; source debit is 100.50. Tier 2 debit is 100.00, with exactly zero fee. A 100.00 filled trade incurs 0.25 in trade currency before any existing settlement rounding.
- With unchanged eligible deposit 1,000, Tier 1 yield over 28 full days before payout is `1000*((1+0.005/364)^28-1)` and Tier 2 uses 0.01. Daily payout rounding cannot compound an invented cent. No payment is created when the bank lacks funding; the payable and distressed state remain durable.
- Seed allocation totals exactly 28,000,000,000 before FX residual allocation. Restart and uncertain commit cannot repeat it. A bank with 100,000 own liquid cash and 70,000 undrawn card commitments cannot commit a new 40,000 loan even if its score table permits that amount.
- A default with principal 1,000 and unpaid interest 50 sells for 100. The borrower still owes 1,050; the seller books proceeds 100 and loss 950; collector basis is 100 with face 1,050 and discount 950. An unfunded collector services the original lender's case without delaying default or erasing any debt.

These examples are planning acceptance targets, not executed verification evidence. Later numeric tests must use the consolidated precision, deadline and repayment contracts.
