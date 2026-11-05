package dev.emily.economy.storage;

import dev.emily.economy.api.Identifiers.WorldId;
import dev.emily.economy.foundation.StorageLifecycle;
import java.util.List;
import java.util.Map;

/** Trusted internal worker-only API. SQL and transaction callbacks must never come from packets. */
public interface FoundationStore extends StorageLifecycle {
    record Snapshot(WorldId worldId, int schemaVersion, String journalMode, int synchronous,
                    boolean foreignKeys, int busyTimeoutMillis) {}
    Snapshot snapshot();
    @FunctionalInterface interface CommitObserver {
        dev.emily.economy.journal.JournalEngine.Decision prepare(dev.emily.economy.journal.Journal.Intent intent,
                dev.emily.economy.journal.JournalEngine.ReadView reads, dev.emily.economy.journal.JournalEngine.Commit commit);
    }
    default dev.emily.economy.journal.JournalEngine.Decision decorateCommit(dev.emily.economy.journal.Journal.Intent intent,
            dev.emily.economy.journal.JournalEngine.ReadView reads, dev.emily.economy.journal.JournalEngine.Commit commit) { return commit; }

    /** Read-only checkpoint from the owned connection, outside any write transaction. */
    long lastCommittedSequence();
    default dev.emily.economy.recovery.RecordCatalog recordCatalog() { return dev.emily.economy.recovery.RecordCatalog.core(); }
    <T> T transaction(Work<T> work);
    /** Worker-only recovery, retaining exclusive world ownership; never creates a missing DB. */
    void recover();
    @FunctionalInterface interface Work<T> { T run(Transaction transaction) throws Exception; }
    interface Transaction {
        int execute(String sql, List<String> parameters);
        /** Immutable string rows, at most 1024 rows / 1MiB of returned UTF-8 data. */
        List<Map<String, String>> query(String sql, List<String> parameters, int maxRows);
    }
}
