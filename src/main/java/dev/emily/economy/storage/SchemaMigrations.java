package dev.emily.economy.storage;

import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ordered trusted SQL; the complete pending chain shares one transaction. */
final class SchemaMigrations {
    static final int MINIMUM_SUPPORTED = 1;
    static final int CURRENT = 6;
    static final int APPLICATION_ID = 0x45434F4E;
    record Migration(int version, String name, List<String> statements) {
        Migration { statements = List.copyOf(statements); }
    }
    static final List<Migration> REGISTRY = List.of(
            new Migration(1, "foundation identity and migration history", List.of(
                    "CREATE TABLE foundation_metadata (singleton INTEGER PRIMARY KEY CHECK(singleton=1), world_id TEXT NOT NULL, schema_version INTEGER NOT NULL, product TEXT NOT NULL CHECK(product='economy-foundation'))",
                    "CREATE TABLE migration_history (version INTEGER PRIMARY KEY, name TEXT NOT NULL, applied_at_utc TEXT NOT NULL)")),
            new Migration(2, "explicit world metadata protocol", List.of(
                    "ALTER TABLE foundation_metadata ADD COLUMN identity_protocol INTEGER NOT NULL DEFAULT 1 CHECK(identity_protocol=1)")),
            new Migration(3, "immutable journal and durable operation outcomes", JournalSchema.statements()),
            new Migration(4, "projection generations and deterministic economic checkpoints", RecoverySchema.statements()),
            new Migration(5, "bounded FX publication and policy lookups", FxSchema.statements()),
            new Migration(6, "player account catalog and constraints", AccountSchema.statements()));

    static void apply(Connection connection, UUID worldId, int from, int target, List<Migration> registry) {
        int expected = 1;
        for (Migration migration : registry) {
            if (migration.version() != expected++) throw new StorageException(StorageException.Kind.SCHEMA, "Migration registry is not contiguous");
        }
        if (target != registry.size() || from >= target) throw new StorageException(StorageException.Kind.SCHEMA, "Invalid migration target");
        boolean canReset = true;
        try {
            connection.setAutoCommit(false);
            for (Migration migration : registry) {
                if (migration.version() <= from) continue;
                for (String sql : migration.statements()) {
                    try (Statement statement = connection.createStatement()) { statement.execute(sql); }
                }
                if (migration.version() == 1) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO foundation_metadata(singleton,world_id,schema_version,product) VALUES(1,?,1,'economy-foundation')")) {
                        statement.setString(1, worldId.toString()); statement.executeUpdate();
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO migration_history VALUES(?,?,?)")) {
                    statement.setInt(1, migration.version()); statement.setString(2, migration.name());
                    statement.setString(3, Instant.now().toString()); statement.executeUpdate();
                }
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("UPDATE foundation_metadata SET schema_version=" + migration.version() + " WHERE singleton=1");
                    statement.execute("PRAGMA user_version=" + migration.version());
                    statement.execute("PRAGMA application_id=" + APPLICATION_ID);
                }
            }
            connection.commit();
        } catch (Throwable e) {
            try { connection.rollback(); }
            catch (SQLException rollback) {
                canReset = false;
                e.addSuppressed(rollback);
                try { connection.close(); } catch (SQLException close) { e.addSuppressed(close); }
            }
            if (e instanceof Error error) throw error;
            throw new StorageException(StorageException.Kind.MIGRATION,
                    "Migration failed; no automatic retry. Inspect schema before restart", e);
        } finally {
            if (canReset) {
                try { connection.setAutoCommit(true); }
                catch (SQLException e) { throw StorageException.sql(e); }
            }
        }
    }
}
