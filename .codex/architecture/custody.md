# Custody foundation

Schema V12 stores world-local custody cases. Version-one journal snapshots
retain authenticated owner, exact serialized components/counts, bounded source
and destination identities, frozen original financial intent, reservation,
delivery attempt, save evidence, acknowledgment, reason, revision and
compensation operation. Every transition uses the existing journal transaction.

Trusted server integrations obtain a journal through `LedgerService.custody`.
Admission authenticates the actor. Worker authority verifies ownership and
operator privileges against current state. Capacity and evidence callbacks
consume detached admission observations and persisted data. They must never
read Minecraft objects from the worker. Financial handlers compose postings
and custody state in one commit. The COMMIT operation ID equals the original
financial operation ID. Retry and lookup use that same custody COMMIT request.
The original financial handler is invoked with its frozen original intent.

Reserve before taking custody, observe a reservation save, then commit.
Capacity for the entire batch is required before financial commit and again
before delivery or collection. Postcommit capacity loss or disconnect yields
a claim only with proof that the items remain undelivered. Delivery attempts
have operation-bound identities. Replays return the original result.
Adapters must check the persisted attempt marker before applying Minecraft
effects and must never turn a replayed result into another delivery.

Acknowledgment records receipt by the adapter. Completion additionally
requires independent persisted delivery evidence. `CustodySaveWitness` reads
at most 1 MiB from one known relative world file, rejects links and compares
the expected SHA-256. The trusted serializer must include world, custody and
generation markers and exact item state in the expected bytes. File matching
alone does not interpret inventory semantics. Actual player save/load across server processes and container NBT
serialization with SQLite restore pairing are verified by the opt-in
inventoryProbe fixture. A5-02-03 additionally verifies real chest chunk-region save/reload,
known unavailable chunks and moved items. Live terminal adapters retain
their separate integration checks.

Before ServerServices publishes READY, startup journals quarantine for at most
16 cases per pass. All restored cases, including claims and completed cases,
require external evidence review. This catches an older claim ledger restored
beside newer delivered inventory. The rest of the economy can proceed after
the bounded scan, while quarantined cases cannot collect.

Claims are owner-bound, paginated by exclusive ID, retained indefinitely and
removed from collection visibility when a delivery attempt starts.
Operators can close confirmed delivery, redeliver confirmed loss through a
claim, or atomically compensate confirmed loss. Each requires verified evidence
and a nonblank reason. Inconclusive cases remain quarantined. The compensation
operation survives quarantine and prevents another compensation or redelivery.

NeoForge save notifications alone do not establish persistence. Player save
notification follows a replacement helper whose failure result is discarded.
Level save notification precedes completion of pending chunk IO. Live adapters
must observe the actual persisted inventory marker before certifying a save.

## Diagnostics

LedgerService exposes case/backlog pages, recorded witness observation and
exact-operation audit lookup. The authenticated owner scope or live operator
permission is checked before any inventory or save evidence is returned.
Pages contain at most 63 cases with one lookahead row and an exclusive ID
cursor. Quarantine and unresolved filters are applied before pagination.

Each witness request reads at most 1 MiB from one recorded location. File
scan limits are reported separately from missing evidence or a mismatched
snapshot. Observation cannot change case state. Audit correlation includes
the immutable journaled case snapshot so later reconciliation cannot overwrite
the evidence associated with the original operation.
