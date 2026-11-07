# A7-01 feasibility and shared-contract plan

Status: planning draft. A6-13 is accepted. Gameplay decisions are recorded in [planned A7–A10 scope](planned-a7-a10.md). This document prepares A7-01; it does not claim generation feasibility, select an implementation prompt or authorize later packages.

[Pinned-source generation research](a7-generation-research.md) identifies the pre-freeze layout window and candidate integration strategies. [Vanilla template inventory](a7-template-inventory.md) records candidate classifications, with animal pens and stables approved exclusions from building counts. Abandoned villages are approved exclusions and receive no A7 content. These are research checkpoints, not proof of guaranteed safe placement.

## Existing implementation evidence

- `InstitutionCatalog` supplies 23 stable customer institutions with fixed native currencies, tiers and capabilities. `Entry.tier1()` includes hybrids. Selection must use these entries, not duplicate their financial definitions.
- `BranchKioskBlock` currently uses one-wide/two-high geometry through `WideTerminalBlock`. Existing placements therefore need the approved legacy representation and explicit upgrade path.
- `TerminalLifecycle` admits loaded physical observations through a bounded server-thread queue. Existing placement handling requires an owner; generated hardware needs a deliberate trusted ownership/bootstrap design.
- `TerminalBanking.BIND` requires current placement identity/revision plus owner/operator authority and unresolved-custody protection. Generated structures cannot safely activate banking by setting a visual bank property or copying player configuration requests.
- Existing kiosk admission checks remain the authority for customer actions. A durable village branch is a new presentation entity, distinct from the existing branch-admission proof.
- Scoped source review found no village generation integration in the mod. The generation hook, vanilla template classification and terrain strategy are not yet proven.

Sources: [branch hardware](branch-hardware.md), [institutions](institutions.md), [storage](storage.md), and current source classes above. Historical schema descriptions are not allocations for new migrations; use the actual migration registry when implementing.

## Feasibility work, in dependency order

1. Inspect the pinned Minecraft 1.21.1 / NeoForge 21.1.252 generation sources and vanilla village resources. Identify an integration point after the original village layout is known but before required bank placement becomes unsafe. Record exact classes/hooks and threading constraints; pool weighting alone is insufficient.
2. Enumerate the five styles' start and building templates. Produce an explicit house/workplace classification, excluding farms, wells, decorations, roads, animal pens and stables. Count original selected building instances, not template variants or bank additions. Resolve composite pieces, empty pieces, abandoned-start detection and unknown templates before freezing the mapping. Approved zero-count policy: no bank for a non-abandoned village with zero qualifying houses/workplaces; exclude it from bank planning and branch requirements.
3. Define compact/larger building envelopes, entrances, path connections, support requirements, kiosk anchors and desk capacity. Reserve sites for the entire selected branch set. Demonstrate that the original house/workplace layout is preserved and the count does not change because bank sites were reserved.
4. Specify bounded deterministic interior-site and edge-site searches, terrain/collision checks and cross-chunk behavior. Decide how plans survive partial chunk generation without forcing ordinary production chunk loads or overwriting later player construction.
5. Specify generation-readiness and recovery outcomes using the approved order: interior, nearby edge with foundations/modest leveling, somewhat farther connected site, then a persistent pending branch plan with a reported reason. Do not silently reduce a selected branch set or claim a pending bank is placed. Define bounded distances/terrain changes and safe retry/operator recovery before A7-04; preserve buildings, farms, roads and player construction.
6. Freeze branch/bootstrap interfaces and handoffs below, including concurrency, persistence and recovery boundaries. Only then prepare independently selectable implementation prompts.

## Proposed shared records and boundaries

These are design proposals, not implemented API names or final schema.

| Record | Required information |
| --- | --- |
| Village identity | World identity, dimension, stable structure-start location; no loaded entity identity |
| Captured village plan | Seed algorithm version, original building count/classification revision, size, captured denomination/catalog/policy revisions, extra-branch result and ordered branch choices |
| Branch identity | Village identity plus stable branch slot; institution, native currency, compact/larger size, template revision, placement transform, bounds and service anchors |
| Hardware intent | Branch/anchor identity, physical generation, expected policy revision, intended institution and stable activation operation IDs |
| Staffing slot | Branch/desk identity, employee identity, assignment revision, vacancy evidence, persisted elapsed replacement ticks and pending replacement identity |

Use world identity to scope durable IDs; seed/location determine reproducible choices. Derive random streams by purpose and stable slot so staffing or decoration does not reroll institution selection. Capture a plan once: reloads use its choices rather than current catalog ordering or denomination. New villages capture current policy; existing branches retain their institution/native currency.

The storage worker owns durable records and journal operations. Generation/server adapters provide detached observations and apply physical changes on the appropriate Minecraft thread. Never access Minecraft objects from the storage worker, block a generation thread waiting for JDBC, or add another financial store. Exact generation-thread handoff must be established by source inspection.

Proposed lifecycle: planned → placement pending → hardware pending → active, with explicit conflict/repair-needed outcomes and retained removal history. A7-02 must specify exact transitions. Physical absence after completed placement is not permission to rebuild: player dismantling is allowed. Recovery needs evidence distinguishing interrupted initial placement from later removal.

Generated activation must use an internal server-only authority scoped to the recorded branch, exact anchors/generations and approved institution. It must compose existing placement/binding policies, preserve unresolved-custody checks and resolve original operation outcomes after uncertain commits. It grants no general operator or customer authority. No capital allocation, account opening or money creation occurs during branch creation.

Banker menu routing must obtain fresh server admission from valid physical service observations. NPC identity, profession, dialogue completion and client-supplied bank IDs grant no banking authority. Explanations use explicit selectable questions; direct kiosk access remains independent.

## Open decisions and technical findings to resolve

| Item | Required resolution |
| --- | --- |
| No eligible local bank / insufficient distinct banks | Define generation gating and durable pending recovery without blocking ordinary vanilla world generation or falsely claiming a banked village |
| Optional branch selected but no distinct provider remains | Approved: omit only that optional branch after the foreign-provider fallback is exhausted, report the reason and persist the selection/omission without rerolling; required branches still use the not-ready policy |
| No safe site after the approved search order | Persist the pending branch plan and report the reason; initial approved terrain limits are 3 blocks of leveling and 6 blocks of foundation depth, pending validation; nearby search extends up to 32 blocks and farther fallback up to 96 blocks outward from the original village boundary, with a safe path to the entrance; define precise measurements. Approved retries are operator-triggered and recheck the current site, preserve later construction and retain captured branch choices; no automatic placement retries or rebuilding dismantled banks |
| Denomination changes during unfinished generation | Define the plan-capture point and distinguish unfinished plans from newly generated villages |
| Template coverage | Map all supported starts/variants; define treatment of unknown or replaced templates without claiming modded/datapack compatibility |
| Interrupted placement versus player removal | Specify markers, progress and conflict rules that prevent automatic rebuilding of dismantled banks |
| Generated hardware ownership | Choose a server-side identity compatible with current policy, without making a nearby player the owner |
| Replacement timing | Implement the approved 24,000 running-server ticks with durable elapsed progress; distinguish death from unload, deduplicate replacements, verify loaded valid desks |
| Operator controls | Approved: branch-status inspection and pause/resume for banker replacements; pausing freezes elapsed progress and resuming continues from saved progress, retaining staffing limits and valid-desk checks. Define bounded command syntax/scope; commands must not create money or rebuild player removals implicitly |
| Kiosk upgrade | Approved: explicit “Upgrade kiosk” action in owner/operator settings, with no material cost; require valid extra space/support. Define safe rollback and binding/revision preservation; failure leaves the original kiosk functional |
| Banker behavior | Approved: villager-like behavior, no trading; define work/wandering/rest scheduling around persistent branch/workstation assignment and optional question-menu interaction |
| Path features | Approved: stairs and small bridges; define safe geometry, span/support limits and compatibility with terrain/preservation policies |
| Bank appearance | Approved: institution signs, lighting and interior furnishings for compact/larger branches in the local village style; finalize original assets and layouts |

## Package handoffs

| Package | Prerequisites and deliverable |
| --- | --- |
| A7-01 | Accepted A6-13 and approved A7 gameplay policies; source-backed feasibility report, template classification, frozen shared contracts and remaining policy decisions |
| A7-02 | Accepted A7-01; durable branch records, bounded diagnostics and recoverable trusted hardware activation; no village generation yet |
| A7-03 | Accepted A7-01 and A7-02 hardware contracts; wider kiosks, original assets and explicit safe upgrade preserving legacy placements |
| A7-04 | Accepted A7-02/A7-03 and proven A7-01 placement strategy; five-style templates, deterministic village plans and required branch generation |
| A7-05 | Accepted A7-04 branch/service anchors; desks, banker entities, bounded staffing/replacement and optional question/menu interaction; split at desk/staffing/dialogue boundaries if too large |
| A7-06 | Accepted preceding A7 packages; scoped composite evidence, user documentation and downstream handoff |

The six implementation prompts and shared scope/verification contract are prepared in the [A7 selection queue](../../prompts/A7-village-banking/EXECUTION.md). No dependent package starts merely because its predecessor was planned. None is selected; A7-01 is next selectable and must freeze technical interfaces before dependent implementation.

## Verification and completion criteria

This planning draft uses diff/link review only. No Gradle or game launch is required for prose.

A7-01 feasibility acceptance requires pinned-source evidence for the chosen hook and count mapping, a recoverable placement/activation design, and explicit treatment of each open case. Source review alone cannot prove terrain placement across all five styles. Any necessary generation witness uses disposable worlds and a bounded chosen scenario set; record failures and limits rather than adding automatic broad test gates.

All future packages inherit [VERIFICATION.md](../VERIFICATION.md): tests only for meaningful changed behavior, at most one focused invocation where useful, no automatic full suite. Ordinary runnable delivery runs `./gradlew build -x test`, `./gradlew help --task runClient` and `./gradlew runClient --dry-run` once. Keep Minecraft 1.21.1, NeoForge 21.1.252, Java 21, working client/server tasks, the registered main source set and required development runtime dependencies. One owner runs Gradle/game checks sequentially. Preserve worlds, backups, accounts and historical evidence.
