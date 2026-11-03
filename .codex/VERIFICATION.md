# Choose verification for the change

- **Docs or Codex configuration:** check Markdown links, file moves, TOML and Git exclusions. No game launch for docs-only edits.
- **Implementation:** run targeted tests first, then the required build and release checks. Use Java 21. For a runnable delivery, verify `./gradlew help --task runClient` and `./gradlew runClient --dry-run`; keep the mod registered and dependencies on development runtimes.
- **Platform, dependency or lifecycle changes:** inspect Gradle tasks if wiring changed. Run required isolated client/server smoke checks, verify real mod and driver loading and stop the server normally. A dry run alone does not establish runtime loading.
- **Storage, journal, recovery or clock:** select affected package tests. Use journal tests plus `JournalMigrationTest` for journal/schema changes; recovery tests plus `WorldBackupTest`, `WorldBackupClockTest` and `OfflineWalTest` for recovery/maintenance changes where relevant. Keep actual fixture-process termination separate from exception injection; never kill a user's game.
- **Worlds and packaging:** use disposable fixtures, never player worlds, and restore only to a new disposable path. Check the clean witness before general backup. Do not accept the EULA or package fixtures.

One owner runs Gradle and game checks sequentially. Reuse still-applicable evidence; rerun when changed code, a failure, or a required gate justifies it. Record commands, results, scope and caveats once in the package evidence.

Historical evidence: [A1-01](verification/A1-01.md) · [A1-02](verification/A1-02.md) · [A1-03](verification/A1-03.md) · [A1-04](verification/A1-04.md).

Current integrated foundation gate: [A1-05-03](verification/A1-05-03.md).
