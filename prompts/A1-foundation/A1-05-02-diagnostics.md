# A1-05-02 — Status and diagnostics

Status: execution-ready when **A1-05-01 is implemented and verified**. Run only on explicit invocation; do not advance automatically.
Apply the [A1 execution contract](CONTRACT.md) and read only the indicated section of the [approved requirements](A1-05-requirements.md#diagnostics-and-reporting).

Read **Diagnostics and reporting** in the requirements reference and the A1-05-01 metrics handoff.

- Use the agreed metrics interface to implement read-only status and diagnostics for console/operators, with availability/progress only for players. Add structured, rate-limited operation-correlated logs and safe error reporting.
- Verify permissions, sanitization, meaningful lifecycle and queue/recovery/clock reporting, and omission of unsupported ETA. Expose stalls and unresolved shutdown without inventing throughput guarantees. No banking gameplay commands. Coverage: F02, F19, F23.

Record scoped results, unresolved limitations and the next package in the handoff. Stop at this boundary.
