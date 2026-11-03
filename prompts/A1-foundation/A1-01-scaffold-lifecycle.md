# A1.1 — Scaffold, development runs and server lifecycle

Status: implemented and verified. Original package requirements below; do not repeat completed work automatically.
Prerequisite: design checkpoint. For explicitly requested work, apply the [A1 execution contract](CONTRACT.md).


## Historical one-time reset (completed; no authorization to repeat)

The original execution authorized a clean rebuild only in the exact economy Git root on the new redesign branch, preserving .git, prompts/ and AGENTS.md. The baseline had to be recoverable from main, with no nested repository/mount or active server affected. External symlink targets and Git history were outside scope. This action has already been performed. A rerun may repair the existing scaffold only; never delete project contents, worlds, or backups.

## Approved A1-01 decisions

Use Minecraft 1.21.1, NeoForge 21.1.252 and Java 21. Register main mod sources in working runClient/runServer; bundle required runtime dependencies and explicitly supply the SQLite driver on development runtimes when storage is introduced. Verify help --task runClient and runClient --dry-run during this package execution, plus actual client/dedicated-server dependency loading; dry-run is not a runtime proof.

Create one server-scoped service container with initializing, ready, unavailable, stopping and stopped states. Gate requests on readiness, dispose of all per-world state, and never replace failed storage with an empty economy. Keep Minecraft access on the server thread and storage/calculation work on workers, passing immutable values. Bound queues from first introduction. Define server-authenticated request context, payload bounds, correlation and validation ownership without speculative gameplay packets. The historical rebuild was performed directly at the Git root.

Initial limits approved in A1-05 apply when each subsystem appears: one storage worker/connection, two calculation workers, 256 pending storage tasks, 64 pending calculation tasks, 1 ms callback-start budget per tick, 5-second SQLite lock timeout and 30-second graceful drain target. A1-05 integrates and measures these; it does not postpone safe scheduling.

## Outcome and prerequisites

Implement a fresh mod that loads on client and dedicated server and creates one isolated foundation service container per running server. Prerequisite: agreed mod identity and architecture boundaries. The scaffold and one-time reset are already complete; only repair the existing scaffold if explicitly requested.

## Deliverables to implement

- Build wrapper/plugin/toolchain and metadata pinned to the platform baseline; resource namespace and source/package layout; separate client distribution entry points from server/common code.
- Working runClient and runServer tasks, registered main mod source set, required dependencies on development runtime, and correct release packaging. The approved SQLite dependency is added with storage in A1.2; it must never be omitted from development runs afterward.
- Separate test source sets/fixtures that cannot ship in the release artifact.
- Server-scoped service ownership and lifecycle states: initializing, ready, unavailable, stopping and stopped. Readiness futures gate future requests; storage failures must not silently start a new empty world.
- Lifecycle interfaces for storage initialization, shutdown/drain and disposal. Use minimal fixtures in this work package; real storage arrives A1.2 and commit recovery A1.3–A1.4.
- Server-thread ownership of Minecraft access and asynchronous worker handoff contracts. All queues must be bounded from their introduction; final instrumentation is A1.5.
- Basic foundation request/response contracts: actor/server context, bounded payloads, correlation and validation ownership. Do not invent gameplay packets or screens before their phases.

## Reimplementation coverage

F01: rebuild platform/development wiring. F02: rebuild per-server/world ownership contracts. F19: rebuild server/client boundary and validation contracts; concrete banking/trading requests are A5/B4. F23: rebuild isolated verification infrastructure.

## Acceptance checks — implement and run

Client and dedicated-server loading; registered mod visible in development runs; help --task runClient and runClient --dry-run succeed; two sequential worlds never share service state; requests before readiness/after stop reject; failed initialization reports unavailable; repeated shutdown safely disposes once. Actual dependency class loading is checked after A1.2 supplies the approved driver.

## Exclusions and handoff

No financial entities beyond interface placeholders, currencies, accounts, transfers, journal contents, blocks/items or gameplay UI. Pass lifecycle, source layout, service ownership and thread contracts to A1.2. Apply the approved initial limits from A1.5 and pass measurement/instrumentation integration there.
