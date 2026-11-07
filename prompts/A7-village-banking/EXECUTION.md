# A7 selection queue

User approval recorded 2026-11-06: **A7-01 through A7-06 are approved for implementation** within their documented scope and dependency order. No package is currently selected or implemented. Approval is separate from execution selection, implementation and verification. Read [CONTRACT.md](CONTRACT.md) and only the explicitly selected package plus its stated prerequisites/references.

| Order | Package | Prerequisite | Status |
| --- | --- | --- | --- |
| 1 | [A7-01](A7-01.md) contracts and generation feasibility | Accepted A6-13; approved A7 policies | Approved; ready for selection; feasibility not accepted |
| 2 | [A7-02](A7-02.md) persistent branches and activation | Accepted A7-01 | Approved; awaiting prerequisite |
| 3 | [A7-03](A7-03.md) wider kiosks and legacy upgrades | Accepted A7-02; frozen A7-01 geometry contract | Approved; awaiting prerequisite |
| 4 | [A7-04](A7-04.md) templates, paths and village generation | Accepted A7-01–03 | Approved; awaiting prerequisites |
| 5 | [A7-05](A7-05.md) desks, bankers and dialogue | Accepted A7-04; existing A7 branch/bootstrap contracts | Approved; awaiting prerequisite |
| 6 | [A7-06](A7-06.md) integration and documentation | Accepted A7-01–05 | Approved; awaiting prerequisites |

This approval covers A7 only. A7-01 must still resolve technical feasibility, exact building envelopes/floor plans and shared interfaces; approval does not assert those deliverables already exist or permit silently changing approved gameplay policies.

Execute dependent packages sequentially and stop at each handoff. If A7-01 discovers an infeasible contract, record the limitation and settle it before dependent implementation. If a package proves too large, propose independently verifiable child IDs at a handoff rather than silently executing several packages. A7-05 has natural desk, staffing and dialogue boundaries; the six-package organization does not require combining their verification or running the full suite.

Next selectable package: **A7-01**. Later phases and B-series work remain outside this queue.
