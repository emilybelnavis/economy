# Project instructions

- Pin Minecraft 1.21.1, NeoForge 21.1.252 and Java 21.
- Keep working `runClient` and `runServer` tasks, register the mod's main source set, and include required development runtime dependencies.
- Before delivering a runnable mod, run `./gradlew build`, `./gradlew help --task runClient`, and `./gradlew runClient --dry-run`. See [.codex/VERIFICATION.md](.codex/VERIFICATION.md) for scoped checks.
- Keep code readable and assets original or appropriately licensed. Provide an API for separate integration mods; add specific integrations only on request.
- Keep README user-facing. Preserve the root changelog; update it for usable releases and user-facing release changes, not routine internal tasks.
- The one-time reset is complete. Preserve existing code, worlds and backups.

## Context and execution

- Exclude `prompts/`, caches, outputs, logs and worlds from routine discovery. Git tracking does not make prompts active instructions. Use scoped `rg --files` / `rg -l` and targeted reads; avoid repeated reads of unchanged material.
- For explicit prompt work, resolve only the selected ID and its stated prerequisites/references; do not ingest the rest of the folder. Reading a prompt does not authorize execution. Implement only the explicitly invoked package; verify and hand off before starting its dependents.
- Use [.codex/PROJECT.md](.codex/PROJECT.md) for current state and subsystem links, and [.codex/WORKFLOW.md](.codex/WORKFLOW.md) for package boundaries, handoffs and timing. Use a fresh chat at phase boundaries or after a completed package if needed.

## Delegation

- Main agent: GPT-6.1 Sol medium, shared contracts, integration and final checks. For substantial independent work, delegate to up to two `economy_worker` agents and one `economy_researcher` using the project profiles. Routine work stays with the main agent.
- Establish shared interfaces first. Assign narrow acceptance criteria, explicit disjoint write ownership or read-only boundaries, and allowed checks. Only the main agent delegates. One owner runs Gradle/game tasks sequentially.
- Respect the user's settings and task-specific check limits. Do not silently substitute unavailable models.
