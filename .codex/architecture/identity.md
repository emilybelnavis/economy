# Party identity and account authorization

`PartyRef` is a stable world and typed owner pair. Owner values and journal actor
values are distinct roles even when both carry the same UUID. Organization
references are reserved and denied by A3 admission. Built-in system actors may
act for their own system owner. Player commands and authenticated handlers must
resolve the live sender on the server thread. Inspection requires the original
command source to be that live player and requires current operator permission.

`PartyAuthorization` issues detached grants for a single owner, session and
mode. Resolve a fresh grant at request admission. Account handlers must resolve
their target and check actual ownership and revisions within the consistent
worker transaction or read snapshot. `ServerServices.submitOwned` and
`lookupOwned` bind the trusted actor, world and canonical owner before using the
existing bounded journal service. Denied worker operations follow the existing
admission diagnostics. Never decode grants, actor IDs, permissions or
registrations from client input.

`auditInspection` reserves its own operation kind and persists only an audit
receipt. A3-04 owns the inspected data query, its bounded paging and audience
recheck. Require a committed receipt before disclosure, bind the query to the
same authorized owner and recheck live operator permission and audience before
publication. An operation ID identifies one logical audit receipt. A replay
recovers that receipt and a new logical inspection gets a new ID.
