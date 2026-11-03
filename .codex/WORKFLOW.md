# Execute one package

1. Resolve the user-selected ID; do not load unrelated prompts. Confirm prerequisites using the current project index and actual implementation evidence. Keep design work separate from explicit execution.
2. Establish interfaces and acceptance checks. Split oversized upcoming packages at independently verifiable boundaries with explicit dependencies. Execute dependent packages in sequence, one at a time.
3. Delegate only substantial independent work. The main agent owns shared interfaces, integration, and verification. Use at most two `economy_worker` agents with disjoint files and one `economy_researcher` for a bounded read-only question/review. Name allowed files, checks and expected result. Close finished agent threads when possible.
4. Finish scoped checks and stop. Update the project and phase statuses. Write a short `.codex/<prompt-id>.md` handoff (aim for one screen): outcome, changed contracts, blockers, and next-package needs. Link to current subsystem architecture and `.codex/verification/<prompt-id>.md` for evidence. Preserve historical notes and use the root changelog for user-facing releases.
5. Default to one chat per phase; restart at a completed package boundary if the chat becomes cumbersome. Give the next chat the selected prompt ID and current handoff.

## Model and usage

Use GPT-6.1 Sol medium for the main agent, Sol low for workers and the researcher. Sol low is a conservative initial choice for bounded research/review; it keeps all project roles on the chosen model. Consider higher effort only for demonstrated need. Profiles pin their own settings, so change the worker profile or choose another explicitly configured profile if an experiment needs higher effort. Project defaults apply in a trusted project and may not change an already open chat or supersede a user selection. Confirm the displayed model/effort in a fresh project chat and inspect spawned threads when trialing the roles. Report unavailable settings rather than silently switching models.

Use **A1-05-01** as the pilot on its next explicit invocation. In its evidence, record elapsed time, rough discovery/implementation/checks/debugging/documentation time (mark overlaps and unknowns), agents used, and actual usage from the dashboard when available. Prior user-reported elapsed times were 9:07, 17:40, 23:01 and 53:24 for A1-01–04; those packages differ in scope and their usage is unknown. Compare comparable work before increasing delegation. Never estimate credits from elapsed minutes alone.
