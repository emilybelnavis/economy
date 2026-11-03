# Project instructions

- Target Minecraft 1.21.1, NeoForge 21.1.252, and Java 21. Keep these versions pinned.
- Once scaffolded, build with `./gradlew build`; inspect tasks with `./gradlew tasks --all`.
- Configure working `runClient` and `runServer` tasks, register this mod's main source set
  in development runs, and include required mod dependencies on the development runtime classpath.
- Before delivering a runnable mod, verify `runClient` is available with
  `./gradlew help --task runClient` and `./gradlew runClient --dry-run`.
- Keep code readable and assets original or appropriately licensed. Do not copy incompatible code.
- Keep README user-facing; durable architecture and verification notes belong in `.codex/`.
- Provide an economy API for separate integration mods; do not add specific integrations unless requested.

## Context efficiency

- Locate filenames and symbols with `rg --files` or `rg -l` before reading contents.
- Inspect matching sections with `rg -n -C`; read complete files only when structure requires it.
- Do not repeatedly read unchanged files. Exclude caches, build outputs, logs, and saves from routine searches.
- Consult `.codex/PROJECT.md` for unfamiliar subsystems and `.codex/VERIFICATION.md` when selecting checks.
- Use explicit paths or `rg --no-ignore` when deliberately investigating excluded generated artifacts.
- Run targeted checks before broader verification; summarize results and unresolved issues concisely.
- Use a fresh chat for unrelated work.

## Delegation

- Main agent: planning, coordination, and integration; GPT-6.1 Sol, medium reasoning.
- Researcher: bounded discovery and dependency tracing; GPT-6 Luna, low reasoning.
- Worker: bounded implementation and authorized checks; GPT-6.1 Sol, low reasoning.
- Use `.codex/agents/` profiles only for substantial independent work; handle straightforward tasks locally.
- Assign narrow scope, acceptance criteria, allowed checks, and explicit file ownership or read-only boundaries.
- Never assign overlapping edits or concurrent Gradle/game runs. Only the main agent delegates.
- Respect task-specific testing limits across all roles. Do not silently substitute unavailable models.
- Project defaults do not override explicit session choices or change an existing chat's model.
