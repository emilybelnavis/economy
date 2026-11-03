# Java source instructions

- Keep Minecraft client classes out of common/server entry points; use the NeoForge client distribution boundary.
- The server owns balances, currency movement, shop ownership, stock, and transaction results.
  Validate client requests against server state and permissions.
- Use exact integer monetary units with checked arithmetic; avoid floating-point balances.
- Coordinate inventory and account changes so failed operations cannot duplicate or lose value.
- Persist accounts by stable player UUID; record data versions and migration decisions.
- Keep the public economy API independent of client UI and optional integration implementations.
- Record cross-cutting design decisions in `.codex/PROJECT.md` as implementation develops.
