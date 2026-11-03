# Current project state

**A1 foundation is complete and verified.** SQLite schema V5 (minimum V1); the initial reset is complete. A2-01 through A2-03-03 are complete and verified. The remaining packages await explicit invocation.

| Package | Prerequisite | Status |
| --- | --- | --- |
| [A1-01](A1-01.md) | Design checkpoint | Verified |
| [A1-02](A1-02.md) | A1-01 | Verified |
| [A1-03](A1-03.md) | A1-02 | Verified |
| [A1-04](A1-04.md) | A1-02, A1-03 | Verified |
| [A1-05-01](A1-05-01.md) scheduling/configuration | A1-04 | Verified |
| [A1-05-02](A1-05-02.md) diagnostics | A1-05-01 | Verified |
| [A1-05-03](A1-05-03.md) foundation acceptance | A1-05-02 | Verified |
| [A2-01](A2-01.md) currency registry and monetary types | A1-05-03 | Verified |
| [A2-02](A2-02.md) conversion and settlement rounding | A2-01 | Verified |
| [A2-03-01](A2-03-01.md) daily FX scheduling and configuration | A2-02 | Verified |
| [A2-03-02](A2-03-02.md) FX diagnostics | A2-03-01 | Verified |
| [A2-03-03](A2-03-03.md) FX foundation acceptance | A2-03-02 | Verified |
| [A2-04](A2-04.md) versioned quotes and pair history | A2-03-03 | Verified |
| [A2-05](A2-05.md) world denomination and change protocol | A2-04 | Implemented, chooser acceptance pending |

Load only the affected contracts: [lifecycle](architecture/lifecycle.md), [storage/migrations](architecture/storage.md), [journal](architecture/journal.md), [recovery/clock/backup](architecture/recovery-clock.md), [FX](architecture/fx.md). [Verification guide](VERIFICATION.md) selects checks; historical notes describe the delivered state at the time and do not override current contracts.

Use [the execution workflow](WORKFLOW.md) for package boundaries, handoffs and delegation. Only the user-selected prompt authorizes package execution.
