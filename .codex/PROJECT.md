# Current project state

**A1 foundation is complete and verified.** SQLite schema V4 (minimum V1); the initial reset is complete. A2–A8 and B1–B9 are planning only. Start A2 only through a separately invoked package.

| Package | Prerequisite | Status |
| --- | --- | --- |
| [A1-01](A1-01.md) | Design checkpoint | Verified |
| [A1-02](A1-02.md) | A1-01 | Verified |
| [A1-03](A1-03.md) | A1-02 | Verified |
| [A1-04](A1-04.md) | A1-02, A1-03 | Verified |
| [A1-05-01](A1-05-01.md) scheduling/configuration | A1-04 | Verified |
| [A1-05-02](A1-05-02.md) diagnostics | A1-05-01 | Verified |
| [A1-05-03](A1-05-03.md) foundation acceptance | A1-05-02 | Verified |

Load only the affected contracts: [lifecycle](architecture/lifecycle.md), [storage/migrations](architecture/storage.md), [journal](architecture/journal.md), [recovery/clock/backup](architecture/recovery-clock.md). [Verification guide](VERIFICATION.md) selects checks; historical notes describe the delivered state at the time and do not override current contracts.

Use [the execution workflow](WORKFLOW.md) for package boundaries, handoffs and delegation. Only the user-selected prompt authorizes package execution.
