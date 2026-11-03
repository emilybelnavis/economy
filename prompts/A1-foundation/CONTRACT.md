# Approved A1 execution contract

Read this contract only when executing a selected A1 package. It supplements AGENTS.md.
Verify the stated predecessor and current handoff before changing code. Implement, verify, and hand off only the selected package.
The initial reset is complete. Preserve the existing worktree, worlds, and backups. No legacy importer or further reset.
Integrated and dedicated servers share authoritative rules. Never place client classes in common/server paths.

- One Minecraft server solely owns each financial world. SQLite at <world>/economy/economy.sqlite; one bounded storage worker and owned connection. No shared multi-server economy or multiple storage backends initially.
- Authoritative immutable journal; projections, operation identity and durable outcome commit atomically. Accounting owns money; custody owns securities; trading coordinates through their services. Checked arithmetic, per-currency balanced postings and reconciled securities are later-phase domain invariants.
- Real-world elapsed time, including all offline time, processed gradually without truncation. Gate affected financial actions until relevant catch-up completes; ordinary Minecraft play can continue. Sleep and /time do not advance economic time.
- Expected maximum 15 simultaneous players. Target: process seven offline days within 30 seconds at the eventual 800-listing scale. This is a future performance target, not a verified guarantee or deadline permitting skipped effects; longer absences may take longer.
- Complete-world stopped-server backup/restore initially. Leave an extension boundary for online backups; do not implement online backups in A1.
- All existing gameplay is reimplemented in its assigned phase. No currency/accounts/money movement/cash/shops/stocks in A1. Existing functionality does not mark a redesign package complete.


Use disposable worlds/databases and kill only processes created for the fixture. Never accept the Minecraft EULA on the user's behalf or replace an existing world. Do not publish artifacts. Follow the current verification guide and the selected package's acceptance requirements. Preserve the mod's pinned development/runtime configuration.
