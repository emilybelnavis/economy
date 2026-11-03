# A2 shared execution contract

Prerequisite for every package is verified A1 completion, including A1-05.
Use the current [project index](../../.codex/PROJECT.md), [journal](../../.codex/architecture/journal.md),
[clock and recovery](../../.codex/architecture/recovery-clock.md) and
[storage](../../.codex/architecture/storage.md) contracts. Use one world store
and the same server authority on integrated and dedicated servers. Derive actors
from authenticated server context. Admit bounded work on the server thread,
use detached values on workers and publish results after commit. Handle
unavailable storage, overload, disconnect and uncertain outcomes through A1.
Resolve unknown writes with the original operation ID. Preserve historical
record meanings and use the existing backup gate for schema upgrades.

## Approved initialization boundary

A2-03 provides a server-internal opening calculation and same-transaction
publication contract. A2-05 wires the actual chooser. An uninitialized world
has no FX reference, opening day or FX catch-up debt. The opening operation
captures one UTC day when the storage worker starts saving. In one journal
transaction it establishes the initial denomination, permanent reference and
complete opening rate set. A save crossing midnight retains its opening day.
The next UTC day is then due. Duplicate committed submissions return the same
result. A retry permitted after confirmed rollback or absence can capture a new
day. Later denomination changes leave the permanent reference and prior rates
intact. A2-03 and A2-04 can use a trusted fixture for the selection boundary.

## Review status and delivery

The package drafts were reviewed and approved on 2026-11-03. Details
labeled proposed describe defaults accepted in this review. Any specification
work explicitly assigned to a package remains part of that package. Implementation packages
exclude later-phase account effects, financial posting and physical cash unless
explicitly stated. Preserve worlds and backups. Follow the project handoff and
scoped verification workflow. Runnable deliveries must pin Minecraft 1.21.1,
NeoForge 21.1.252 and Java 21, configure runClient and runServer with the main
source set and required runtime dependencies, and run ./gradlew build,
./gradlew help --task runClient and ./gradlew runClient --dry-run.
