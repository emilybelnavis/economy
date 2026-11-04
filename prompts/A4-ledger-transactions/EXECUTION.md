# A4 execution contract

All six packages are approved. Execute one explicitly invoked ID at a time. A4-01 may start first after checking its upstream prerequisites. Each subsequent package requires the preceding package's accepted handoff. The main agent owns shared contracts, integration, and sequential verification.

Use the [phase design](DESIGN.md) for the shared financial model and subsystem boundaries.


## Shared rules

- Use Minecraft 1.21.1, NeoForge 21.1.252, and Java 21. Apply the same server authority on integrated and dedicated servers.
- Reuse A1 journal admission, operation identity, outcomes, and replay. Resolve original outcomes before live quote checks. Commit financial facts, projections, and success outcomes together. Persist terminal business rejections through the journal. Retry unknown results with the original ID.
- Apply A2 monetary, quote, and denomination contracts and A3 account, claim, and lifecycle contracts. Check live authority and relevant revisions within the authoritative settlement boundary.
- Keep postings balanced per currency, checked for overflow, and expressed in whole coins. Use full capture and explicit release. Partial capture is deferred for future planning.
- Credit FX fees to the game. Round FX payouts and rebased balances down, including zero. Preserve raw history. For funded closure, apply A3 destination eligibility and commit settlement and closure together.
- Leave cash items to A5, custody to its owning phase, and external integrations to separate packages.

## Completion gate

Follow the selected prompt's scheduling and configuration, diagnostics, and foundation acceptance sections. Record acceptance evidence and update the project status and package handoff under the project workflow. Identify any prerequisite or acceptance case still unresolved before a dependent package starts.

For a runnable delivery, configure both development runs, register the main source set, and include required development runtime dependencies. Run the build, runClient task help, and runClient dry run as prescribed by the project. Stop after the selected package's handoff.
