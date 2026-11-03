# A1-05 — Approved operations and acceptance requirements

Reference only. Execute an individual A1-05 subprompt from the [phase index](README.md). Basic worker/thread safety already applies from earlier packages.

## Scheduling defaults and constraints


| Initial setting | Approved default |
| --- | ---: |
| Storage workers/connections | 1 |
| Calculation workers | 2 |
| Pending storage tasks | 256 |
| Pending calculation tasks | 64 |
| Server callback-start processing budget | 1 ms/tick |
| SQLite lock timeout | 5 seconds |
| Graceful shutdown drain target | 30 seconds |

Defaults are approved starting limits subject to later measurement, not proven capacity. Bound payload/result/batch sizes as well as task counts. Callback budget controls starting work, not unsafe interruption; callbacks must remain small. Set remaining numeric payload/range limits transparently during implementation review, without presenting them as already approved.

Use separate bounded storage, pure-calculation and server-publication paths. Explicitly reject before acceptance on saturation; never silently discard accepted work. Coalesce safe redundant reads, prevent monopolization by one player/integration, and submit catch-up in bounded chronological batches. Accepted writes retain operation lookup; cancelled waits/timeouts/disconnects do not undo commits. Re-resolve live player/menu identity before callbacks; never access stale Minecraft objects. Financial cancellation is a later explicit domain operation.

Shutdown: stop external admission; stop scheduling new batches; complete or safely roll back current transaction; drain within target; close on owning worker and dispose. Report unresolved work/unclean shutdown if target expires. Do not force-close a running connection from another thread or claim timeout safely cancels a write. Queued uncommitted operations can retry original IDs after recovery. Explicit escalation behavior must preserve these constraints.

Validate configuration and fail initialization with specific errors; no silent clamping. Worker/queue/storage changes require restart initially; document defaults and valid ranges. No gameplay fees/interest/leverage settings in A1.


## Diagnostics and reporting

Read-only /economy status and /economy diagnostics use console or operator permission level 2. Report lifecycle, financial-world identity/schema/storage health, last committed sequence, queue capacity/depth/oldest age, recovery stage/checkpoint, completed economic frontier/backlog/rate and sanitized last failure/correlation. Players receive only availability/progress. Label ETA estimated; omit if unsupported. Logs separate admission/business/storage/unknown-outcome/postcommit-delivery failures, rate-limit repeated errors and avoid private payloads/history/internal paths in player messages.


## Foundation gate

A1 acceptance requires disposable-fixture evidence for load/dev dependencies/runClient help and dry-run, world isolation, schema upgrade/rollback, atomic journal/dedup, restart/replay/restore, bounded overload/threading, clock ordering/resume, shutdown and clean release packaging. Actual process interruption is distinct from exception injection and neither proves arbitrary hardware-fault protection. Full 800-listing catch-up benchmark occurs in later simulation phases; A1 validates machinery only.



Every assigned F-ID has a reimplemented foundation contract or explicit later-phase owner; all checks report evidence/limits; the mod starts, safely persists fixture records, reloads/migrates/recovers and stops. No usable money, currencies, accounts, cash, shop or trading systems ship in A1. Legacy import is explicitly excluded; supported future redesign schemas still migrate. Return the final five-package dependency/acceptance checklist and outstanding decisions; claim A1 implementation completion only after all five packages and required checks are complete.
