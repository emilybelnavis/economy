# Economy phase prompts

Select one exact prompt or ID. Reading a prompt, reference or index never authorizes execution. Phase approval and implementation status are recorded separately below; unapproved phases remain planning only. A1–A6 are complete in their recorded accepted scopes; see [current project state](../.codex/PROJECT.md). A7–A10 remain planning only. Stage B implementation follows the Economy Core Alpha gate; dataset/specification preparation may overlap once schemas are agreed.

| Phase | Required upstream work | Status |
| --- | --- | --- |
| [A1 Foundation](A1-foundation/README.md) | Design checkpoint | Accepted foundation |
| [A2 currencies fx](A2-currencies-fx/README.md) | A1 | Accepted in recorded scope |
| [A3 accounts identity](A3-accounts-identity/README.md) | A1; A2 currency contracts before integration | Accepted in recorded scope |
| [A4 ledger transactions](A4-ledger-transactions/README.md) | A1–A3 | Accepted in recorded scope |
| [A5 payments commerce](A5-payments-commerce/README.md) | A4 | Accepted in recorded scope |
| [A6 banks, credit and debt](A6-credit-debt/README.md) | Verified A1–A5; execute A6 prerequisites in order | Accepted through A6-13 |
| [A7 village banking](A7-village-banking/README.md) | Accepted A6; A1 recovery and A5 custody | Planning only |
| [A8 economic simulation](A8-economic-simulation/README.md) | A2, A6; A1 clock and A4 accounting | Planning only |
| [A9 basic item valuation](A9-item-valuation/README.md) | A2 currency/FX; preceding A8 handoff | Planning only |
| [A10 core integration](A10-core-integration/README.md) | A1–A9 | Planning only |
| [B1 market registries](B1-market-registries/README.md) | A1, A2 | Planning only |
| [B2 brokerage custody](B2-brokerage-custody/README.md) | A4, B1 | Planning only |
| [B3 prices liquidity](B3-prices-liquidity/README.md) | A8, B1; B2 participant custody contracts as needed | Planning only |
| [B4 orders settlement](B4-orders-settlement/README.md) | B2, B3; approved Stage A core | Planning only |
| [B5 international arbitrage](B5-international-arbitrage/README.md) | B4 | Planning only |
| [B6 margin risk](B6-margin-risk/README.md) | A6, B4 | Planning only |
| [B7 shorts lending](B7-shorts-lending/README.md) | B6 | Planning only |
| [B8 advanced markets](B8-advanced-markets/README.md) | B5–B7 | Planning only |
| [B9 release hardening](B9-release-hardening/README.md) | B1–B8 | Planning only |

Planning references: [design checkpoint](planning/00-design-foundation.md), [feature inventory](planning/01-current-feature-inventory.md), [feature ownership](planning/02-feature-phase-map.md), [decision register](planning/03-decision-register.md), [template](planning/04-prompt-template.md).

Release checkpoints: A1–A4 prototype; A1–A10 Economy Core Alpha; B1–B4 Stock Market Alpha; B1–B7 Beta; B1–B9 full release. Preserve assigned gameplay features or record an approved deliberate change. Tax/government, MineColonies-specific features and warfare are separate future modules.

Track approved plans and execution prompts here. Put temporary drafts in ignored `scratch/`. Keep IDs stable and link prerequisites explicitly. Update this index when phase status changes.

## Phase remapping and verification

A7-economic-simulation moved to A8-economic-simulation; A8-core-integration moved to A10-core-integration. A7 village banking and A9 item valuation are new. Completed A1–A6 package IDs are unchanged; future-phase references use the new numbering. No implementation is authorized by this reorganization.

Apply [the project verification policy](../.codex/VERIFICATION.md) to all phases and sub-tasks. Prose uses diff/link review; implementation uses proportionate focused checks, not automatic full-suite gates. See [shared planning boundaries](../.codex/architecture/planned-a7-a10.md).
