# Deferred organization identity and delegated access

Status is deferred. Phase assignment is TBD. This planning note does not authorize implementation.

A3 provides stable, typed account owner references, player and system authorization, and audit actors. The later integration task owns organization registration, initial authority assignment, delegated capabilities, revocation, and access to shared merchant and treasury accounts.

Use planned MineColonies and casino support as integration inputs when the task is scheduled. Establish how each integration maps its own entities and authority into the shared account API, handles authority changes, and preserves account and audit history. Verify that revocation takes effect for later actions and that callers cannot impersonate another owner or integration.

Decide separately whether the base mod should expose player-created organizations, membership management or shared banking controls. Review that gameplay on its own merits before assigning it to a phase.
