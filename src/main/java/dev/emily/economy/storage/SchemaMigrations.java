package dev.emily.economy.storage;

import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ordered trusted SQL; the complete pending chain shares one transaction. */
final class SchemaMigrations {
    static final int MINIMUM_SUPPORTED = 1;
    static final int CURRENT = 27;
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
            new Migration(6, "player account catalog and constraints", AccountSchema.statements()),
            new Migration(7, "ledger balance projection", List.of(LedgerProjection.CREATE_TABLE)),
            new Migration(8, "ledger holds", HoldSchema.statements()),
            new Migration(9, "durable FX quote consumption", FxSettlementSchema.statements()),
            new Migration(10, "indexed account posting history", List.of(LedgerHistorySchema.INDEX)),
            new Migration(11, "merchant payments and authorization deadlines", List.of(
                    "CREATE TABLE payment_merchants(actor TEXT PRIMARY KEY, enabled INTEGER NOT NULL CHECK(enabled IN (0,1)))",
                    "CREATE TABLE payment_clock(singleton INTEGER PRIMARY KEY CHECK(singleton=1), millis INTEGER NOT NULL)",
                    "INSERT INTO payment_clock VALUES(1,0)",
                    "CREATE TABLE payment_invoices(id TEXT PRIMARY KEY, merchant TEXT NOT NULL, payer TEXT NOT NULL, source TEXT NOT NULL, target TEXT NOT NULL, currency TEXT NOT NULL, units INTEGER NOT NULL, state TEXT NOT NULL, hold TEXT, deadline INTEGER)",
                    "CREATE INDEX payment_expiry ON payment_invoices(state,deadline,id)",
                    "CREATE TABLE payment_receipts(operation_id TEXT PRIMARY KEY, invoice_id TEXT NOT NULL REFERENCES payment_invoices(id), action TEXT NOT NULL, UNIQUE(invoice_id,action))",
                    "CREATE TABLE journal_retry_intents(operation_id TEXT PRIMARY KEY, actor TEXT NOT NULL, service TEXT NOT NULL, operation_type TEXT NOT NULL, canonical_request TEXT NOT NULL)")),
            new Migration(12, "durable custody and owner claims", CustodySchema.statements()),
            new Migration(13, "world-bound cash issuance quantities", CashIssuanceSchema.statements()),
            new Migration(14, "generation-bound terminal policy", TerminalPolicySchema.statements()),
            new Migration(15, "reserved deposits and original cash returns",
                    java.util.stream.Stream.concat(DepositSchema.statements().stream(),
                            DepositReturnSchema.statements().stream()).toList()),
            new Migration(16, "exact withdrawals and committed delivery intents", WithdrawalSchema.statements()),
            new Migration(17, "owner-priced register sales and stock custody", RegisterSalesSchema.statements()),
            new Migration(18, "financial calendar and paged domain deadlines", FinancialCalendarSchema.statements()),
            new Migration(19, "minor-unit CHF posting history", List.of("DROP INDEX ledger_posting_account", LedgerHistorySchema.V2_INDEX)),
            new Migration(20, "institution capital and branch-bound account providers", institutionStatements()),
            new Migration(21,"wealth pricing and funded deposit yield",pricingStatements()),
            new Migration(22,"explicit bank terminal binding and legacy migration",TerminalBankingSchema.statements()),
            new Migration(23,"owner-bound issued debit card lifecycle",dev.emily.economy.card.CardSchema.statements()),
            new Migration(24,"dormant credit profiles and score policy history",dev.emily.economy.credit.CreditProfileSchema.statements()),
            new Migration(25,"funded credit facilities and immutable statements",dev.emily.economy.credit.CreditFacilitySchema.statements()),
            new Migration(26,"bounded credit diagnostics and immutable servicing witnesses",dev.emily.economy.credit.CreditAuditFacts.statements()),
            new Migration(27,"ordered immutable credit lifecycle event source",List.of(dev.emily.economy.credit.CreditEvents.INDEX)));

    private static List<String> pricingStatements() {
        var sql=new java.util.ArrayList<String>();
        sql.addAll(dev.emily.economy.pricing.PricingSchema.statements());
        sql.addAll(dev.emily.economy.interest.InterestSchema.statements());
        return List.copyOf(sql);
    }

    private static List<String> institutionStatements() {
        var sql = new java.util.ArrayList<String>();
        sql.addAll(dev.emily.economy.institution.InstitutionSchema.statements());
        sql.addAll(dev.emily.economy.institution.ProviderSchema.statements());
        sql.add("DROP TRIGGER account_insert_guard");
        sql.add(AccountSchema.fourAccountGuard());
        sql.add("DROP INDEX ledger_posting_account");
        sql.add(LedgerHistorySchema.V3_INDEX);
        return List.copyOf(sql);
    }

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
