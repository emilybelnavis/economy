# A1 — Foundation

A1-01 through A1-04 are implemented and verified. A1-05 is split into three ordered packages; none has started. The one-time reset is complete.
Use the [current state and contracts](../../.codex/PROJECT.md) for implementation context. Select a single prompt explicitly; this index does not authorize execution.

| Package | Prerequisite | Status |
| --- | --- | --- |
| [A1-01](A1-01-scaffold-lifecycle.md) | Design checkpoint | Implemented and verified |
| [A1-02](A1-02-identifiers-storage-migrations.md) | A1-01 | Implemented and verified |
| [A1-03](A1-03-durable-journal-commits.md) | A1-02 | Implemented and verified |
| [A1-04](A1-04-recovery-backup-clock.md) | A1-02, A1-03 | Implemented and verified |
| [A1-05-01](A1-05-01-scheduling-configuration.md) | A1-04 | Execution-ready |
| [A1-05-02](A1-05-02-diagnostics.md) | A1-05-01 | Execution prompt; waiting for predecessor |
| [A1-05-03](A1-05-03-foundation-acceptance.md) | A1-05-02 | Execution prompt; waiting for predecessor |

[A1 execution contract](CONTRACT.md) · [Approved A1-05 requirements](A1-05-requirements.md)

A1 supplies a loadable, durable foundation and test fixtures. Currencies and FX are A2; accounts A3; postings and balances A4; physical payments/commerce A5; simulation A8; markets Stage B.
