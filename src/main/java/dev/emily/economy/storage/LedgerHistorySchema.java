package dev.emily.economy.storage;

import java.sql.Connection;
import java.sql.SQLException;

/** V10 index for bounded account history reads of immutable postings. */
final class LedgerHistorySchema {
    static final String INDEX = """
            CREATE INDEX ledger_posting_account ON journal_records(substr(payload,1,36),sequence,position)
            WHERE record_type='economy:posting' AND record_version=1
            """.strip();

    private LedgerHistorySchema() {}

    static void validate(Connection connection) throws SQLException {
        try (var query = connection.prepareStatement(
                "SELECT sql FROM sqlite_schema WHERE type='index' AND name='ledger_posting_account'");
             var rows = query.executeQuery()) {
            if (!rows.next() || !INDEX.equals(rows.getString(1))) {
                throw new StorageException(StorageException.Kind.SCHEMA, "Missing or changed ledger history index");
            }
        }
    }
}
