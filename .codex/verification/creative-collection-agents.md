# Creative Collection Agents — 2026-11-06

User-requested standalone enhancement; no later phase selected. [Current contract](../architecture/collection-agent.md#creative-placement).

Implemented vanilla summon eligibility, deferred spawn egg with vanilla model and original colours, both creative tabs, persistent stationary unbound representatives, and visitor-owned collection dialogue. Managed encounters retain transient entities and live lease checks. No schema, financial API, dependency or development-run wiring changes.

Java 21 checks, sequentially:
- `./gradlew build -x test` — passed.
- `./gradlew help --task runClient` — passed; development client task available.
- `./gradlew runClient --dry-run` — passed; expected preparation and client launch graph.
- `./gradlew test --tests 'ca.emilybelnavis.mcmods.economy.collection.CollectionDialoguePolicyTest' --tests 'ca.emilybelnavis.mcmods.economy.foundation.CollectionPresenceAdmissionTest'` — passed, 6 tests. Existing coverage checks menu identities, offered actions, bounded transport, amounts and owner admission; it does not establish in-game placement or restart persistence.
- `git diff --check` — passed.

Build initially could not access the Gradle wrapper cache inside the sandbox; reran with approved cache access. No full suite, Minecraft launch or player-world access. Egg appearance, physical placement, multiple visitors and save/reload behavior still require in-game smoke verification. Source review confirms Java 21, NeoForge 21.1.252/Minecraft 1.21.1 pins, registered main mod source set, client/server configurations and SQLite development runtime remain intact. Pre-existing A7 planning edits were preserved.
