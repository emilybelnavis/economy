# A1-05-01 — Bounded scheduling and configuration

Status: execution-ready when **A1-04 is implemented and verified**. Run only on explicit invocation; do not advance automatically.
Apply the [A1 execution contract](CONTRACT.md) and read only the indicated section of the [approved requirements](A1-05-requirements.md#scheduling-defaults-and-constraints).

Read **Scheduling defaults and constraints** in the requirements reference and the relevant lifecycle and clock architecture sections.

- Integrate ongoing bounded chronological catch-up and separate storage, calculation and publication paths. Specify fairness, safe coalescing, admission/rejection, deadlines and drain policy.
- Validate restart-only worker/queue/storage configuration with documented defaults and ranges; fail initialization specifically on invalid values. Keep cancellation, original operation lookup and live player/menu re-resolution semantics.
- Publish a small immutable metrics interface for A1-05-02 (queue capacity/depth/age, recovery and economic progress). Capture only detached data and establish the interface before delegating implementation.
- Verify targeted configuration, saturation without partial effects, cancellation, ordering/resume, thread boundaries and stalled-worker/drain behavior on disposable fixtures. Do not defer basic safety to diagnostics. Coverage: F02, F19, F23.

Record scoped results, unresolved limitations and the next package in the handoff. Stop at this boundary.
