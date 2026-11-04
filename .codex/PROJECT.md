# Current project state

**A1 foundation is complete and verified.** SQLite schema V17 (minimum V1); the initial reset is complete. A2-01 through A2-03-03 are complete and verified. A3-01 through A3-05 are verified. A4-01 through A4-06 are verified. A5-01-01 through A5-02-03 and A5-03 through A5-09 are verified. Later packages await explicit invocation.

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
| [A2-05](A2-05.md) world denomination and change protocol | A2-04 | Accepted |
| [A3-01](A3-01.md) parties and authorization | A1 | Verified |
| [A3-02](A3-02.md) account catalog and products | A3-01, A2 registry and denomination read | Verified |
| [A3-03](A3-03.md) account lifecycle and limits | A3-02 | Verified |
| [A3-04](A3-04.md) account queries and funds | A3-03, A2 monetary types | Verified |
| [A3-05](A3-05.md) account rebase planning | A3-04, A2-05 confirmation contract | Verified |
| [A4-06](A4-06.md) history and public contracts | A4-05, A3 queries | Verified, including live commands |
| [A5-01-01](A5-01-01.md) merchant payment foundation | A4-06 | Verified |
| [A5-01-02](A5-01-02.md) payment diagnostics | A5-01-01 | Verified |
| [A5-01-03](A5-01-03.md) payment foundation acceptance | A5-01-02 | Verified |
| [A5-02-01](A5-02-01.md) custody foundation | A5-01-03 | Verified |
| [A5-02-02](A5-02-02.md) custody diagnostics | A5-02-01 | Verified |
| [A5-02-03](A5-02-03.md) custody foundation acceptance | A5-02-02 | Verified |
| [A5-03](A5-03.md) cash and cards | A5-02-03 | Verified |
| [A5-04](A5-04.md) terminal policy | A5-03 | Verified |
| [A5-05](A5-05.md) deposits | A5-04 | Verified |
| [A5-06](A5-06.md) withdrawals | A5-05 | Verified |
| [A5-07](A5-07.md) register sales | A5-06 | Verified |
| [A5-08](A5-08.md) physical terminals and assets | A5-07 | Verified |
| [A5-09](A5-09.md) menus and phase acceptance | A5-08 | Verified |

Load only the affected contracts: [lifecycle](architecture/lifecycle.md), [storage/migrations](architecture/storage.md), [journal](architecture/journal.md), [recovery/clock/backup](architecture/recovery-clock.md), [FX](architecture/fx.md), [identity](architecture/identity.md), [accounts](architecture/accounts.md). [custody](architecture/custody.md), [cash](architecture/cash.md), [terminals](architecture/terminals.md), [deposits](architecture/deposits.md), [withdrawals](architecture/withdrawals.md), [registers](architecture/registers.md), [banking menus](architecture/banking.md). [Verification guide](VERIFICATION.md) selects checks; historical notes describe the delivered state at the time and do not override current contracts.

Use [the execution workflow](WORKFLOW.md) for package boundaries, handoffs and delegation. Only the user-selected prompt authorizes package execution.
