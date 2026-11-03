# Lifecycle and request boundaries

## Ownership

`Economy` owns an identity-keyed service map, one entry per MinecraftServer. Starting,
post-tick and stopping events run on the server thread. Stopping removes the entry.
No client classes are referenced by common code. `EconomyClient` is CLIENT-only.
`ServerServices` captures its owning thread and assigns a fresh session UUID; no static
world state exists. Readiness is published by a server tick after staged recovery and final validation.
Requests reject immediately unless READY, including before initialization and after stop.
Initialization exceptions cause UNAVAILABLE, never replacement storage or an empty database.

Production uses [FoundationRuntime](recovery-clock.md), with SQLite owned by the storage worker.

## Workers and limits

The restart-only scheduling settings and exact ranges are in the [README](../../README.md).
One storage worker owns one connection. Default waiting limits are 256 storage and 64
calculation, with two calculation workers. The publication and total public outstanding
limit is the sum of both waiting limits and the configured worker counts. When economic
systems are registered, each worker path has one additional queue position reserved for
the single internal pipeline. A full queue rejects before acceptance. An authenticated
actor may hold up to half the requested path's configured queue capacity, capped at eight
and floored at one, including running work and unpublished results. The cap spans both paths.
Trusted unkeyed internal work shares global limits. FIFO applies within each worker queue.
An identical authorized original-operation lookup can share an outstanding read until another
storage write is queued. Writes are never coalesced.

Worker inputs and results must be detached and bounded by their operation. The journal
enforces its existing input, output and record bounds, and an economic output is limited to
8 KiB. The generic trusted supplier APIs require their callers to enforce their own data bounds.
Worker completion is published on the server thread. The configured tick budget limits callback
starts and cannot preempt a long callback. Live publication resolves a fresh player and menu
instance from server session and actor identities. A stale target skips delivery while
cache invalidation and original-operation lookup retain their independent roles.

Callers apply wait deadlines to their own copy of the completion future. Cancellation,
timeout and disconnect do not cancel accepted writes. A caller that loses its wait can
look up the original operation and retry with the same ID if absent. The configured SQLite
lock deadline applies to the connection, including recovery. The tick never joins unfinished
work. On stop, external admission and new economic stages cease. Already queued worker
tasks drain against one deadline. Pending delivery waits finish exceptionally when the server
stops. If the deadline expires, queued tasks are removed and running tasks receive interruption.
The storage adapter closes on its owning thread after running work returns. A stalled worker
can retain ownership past the deadline. The result stays unclean and the server cannot certify
a complete-world shutdown. Only explicitly retry uncommitted operations using their original IDs.

`ServerServices.metrics()` returns a detached server-thread snapshot of queue
capacity/depth/monotonic oldest age, startup recovery progress, economic sequence and interval
debt, wall-clock waiting, pipeline occupancy and unresolved work at a failed drain.
Queue depths are sampled while workers may advance.

## Operator diagnostics

`/economy status` shows availability and progress. An actual server console, remote console,
or player command source with permission level 2 can see the operator view and run
`/economy diagnostics`. Elevated command-block and function sources receive the public
view. Command output is private to the requester.

Storage identity, schema and last committed journal sequence are sampled on the storage
owner thread. After startup, an idle storage worker can refresh the sample once per second.
A failed sample preserves the last checkpoint and reports degraded health. A command never
queries SQLite directly. Queue telemetry also reports active worker age. A wait or active
task of at least five seconds is a possible stall, and incomplete shutdown is reported
in the server log with a count of unresolved work. Economic rate is measured between
monotonic samples of completed interval sequences when an economic system is registered.
There is no supported ETA.

Operation logs use a fixed event code and operation/request UUIDs. Admission, durable
business rejection, storage failure, uncertain outcome, and postcommit publication failure
have separate codes. Each code logs at most once per ten seconds per server. Suppressed
events still update the last failure. Command output and routine error logs exclude raw
exceptions, operation payloads, identities of players, and filesystem paths.

`RequestContext` is constructed by trusted server handlers: actor identity must come
from the authenticated sender, never payload claims. The server session UUID scopes
requests; correlation UUID ties responses to requests. Transport bounds are 16KiB;
domain services own permissions and semantic validation. No speculative network packets.
`FoundationResponse.READY` never means a durable financial commit.
