# A1-05-03 — Foundation acceptance

Status: execution-ready when **A1-05-02 is implemented and verified**. Run only on explicit invocation; do not advance automatically.
Apply the [A1 execution contract](CONTRACT.md) and read only the indicated section of the [approved requirements](A1-05-requirements.md#foundation-gate).

Read **Foundation gate** in the requirements reference and the relevant current architecture and verification guidance.

- Run the complete required A1 acceptance checks on the integrated state. Include targeted evidence for thread/queue bounds, sequential world isolation, supported upgrades and rollback, atomic journal/dedup, restart/replay/restore, clock ordering/resume and shutdown. Keep exception injection distinct from actual fixture process termination.
- Verify the release build and fixture exclusion, client/server registration and real dependency loading in isolated development runs, and required runClient help and dry-run. Report actual results and limits. Never use player worlds.
- Account for F01/F02/F03/F09/F19/F23 and explicitly assign deferred behavior. Document configuration, backup/restore and known limits in the appropriate references, then provide the A2/A3/A4/A5 contract handoff and outstanding decisions. Complete A1 only when all prerequisites and required checks pass. No gameplay work or full-market benchmark.

Record scoped results, unresolved limitations and the next package in the handoff. Stop at this boundary.
