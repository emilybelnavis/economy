package dev.emily.economy.foundation;

import dev.emily.economy.account.AccountCatalog;
import dev.emily.economy.api.AccountIdentity.Purpose;
import dev.emily.economy.api.Identifiers.*;
import dev.emily.economy.api.PartyRef;
import dev.emily.economy.fx.FxService;
import dev.emily.economy.journal.Journal;
import dev.emily.economy.journal.JournalEngine;
import dev.emily.economy.journal.JournalService;
import dev.emily.economy.storage.FailureHarness;
import dev.emily.economy.storage.FoundationStore;
import dev.emily.economy.storage.SqliteStorage;
import dev.emily.economy.storage.StorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AccountCatalogTest {
    @TempDir Path world;
    private final PlayerId player = new PlayerId(UUID.randomUUID());

    private record Query(String sql, List<String> parameters) {}

    private static final JournalService.Publisher PUBLISHER = new JournalService.Publisher() {
        @Override public void invalidate(Journal.Outcome committed) {}
        @Override public void publish(Journal.Outcome terminal) {}
    };

    @Test void primaryWaitsForSelectionAndQueuedRequestsConvergeAcrossRestart() throws Exception {
        var store = new SqliteStorage();
        var services = open(store);
        var pendingId = operationId();
        var firstId = operationId();
        Journal.Outcome first;
        String tradingId;
        PartyRef target;
        var release = new CountDownLatch(1);
        try {
            target = owner(services);
            var grant = grant(services, target);
            var pending = complete(services, services.initializePrimary(grant, pendingId, requestId(), PUBLISHER));
            assertEquals(Journal.Status.DURABLY_REJECTED, pending.status());
            assertEquals("DENOMINATION_PENDING", pending.result().code());
            assertTrue(await(services, services.storage(() -> store.transaction(tx ->
                    AccountCatalog.primary(tx::query, target)))).isEmpty());

            selectCurrency(services, "JPY");
            assertEquals(pending, complete(services, services.initializePrimary(grant, pendingId, requestId(), PUBLISHER)));
            var started = new CountDownLatch(1);
            services.storage(() -> {
                started.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
                return null;
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var one = services.initializePrimary(grant, firstId, requestId(), PUBLISHER);
            var two = services.initializePrimary(grant, operationId(), requestId(), PUBLISHER);
            assertEquals(Journal.Receipt.ACCEPTED_PENDING, one.receipt());
            assertEquals(Journal.Receipt.ACCEPTED_PENDING, two.receipt());
            release.countDown();

            first = complete(services, one);
            var second = complete(services, two);
            assertEquals(Journal.Status.COMMITTED, first.status());
            assertEquals("ACCOUNT_READY", first.result().code());
            assertEquals(first.result().payload(), second.result().payload());
            var trading = complete(services, services.initializeTrading(grant, operationId(), requestId(), PUBLISHER));
            assertEquals("ACCOUNT_READY", trading.result().code());
            tradingId = trading.result().payload();
            assertNotEquals(first.result().payload(), tradingId);
            var primaryId = new AccountId(UUID.fromString(first.result().payload()));
            assertEquals(primaryId, await(services, services.findOwnedAccount(grant, primaryId)).orElseThrow().id());
            var stranger = new PlayerId(UUID.randomUUID());
            var strangerOwner = new PartyRef(target.world(), new PartyRef.Player(stranger));
            var strangerGrant = PartyAuthorization.admit(target.world(), services.sessionId(), stranger,
                    false, strangerOwner, false).orElseThrow();
            assertTrue(await(services, services.findOwnedAccount(strangerGrant, primaryId)).isEmpty());
            var rows = await(services, services.storage(() -> store.transaction(tx -> tx.query(
                    "SELECT account_id,purpose,currency,is_primary,revision FROM account_catalog WHERE owner_id=? ORDER BY purpose",
                    List.of(player.value().toString()), 2))));
            assertEquals(2, rows.size());
            assertEquals(Map.of("account_id", first.result().payload(), "purpose", "BANK", "currency", "JPY",
                    "is_primary", "1", "revision", "1"), rows.getFirst());
            assertEquals("TRADING", rows.getLast().get("purpose"));
            assertNull(rows.getLast().get("currency"));
        } finally {
            release.countDown();
            assertTrue(services.stop(Duration.ofSeconds(5)));
        }

        var reopenedStore = new SqliteStorage();
        var reopened = open(reopenedStore);
        try {
            assertEquals(target, owner(reopened));
            var grant = grant(reopened, target);
            assertEquals(first, await(reopened, reopened.lookupPrimaryOperation(grant, firstId, requestId())).orElseThrow());
            assertEquals(first, complete(reopened, reopened.initializePrimary(grant, firstId, requestId(), PUBLISHER)));
            assertEquals(first.result().payload(), complete(reopened,
                    reopened.initializePrimary(grant, operationId(), requestId(), PUBLISHER)).result().payload());
            assertEquals(tradingId, complete(reopened,
                    reopened.initializeTrading(grant, operationId(), requestId(), PUBLISHER)).result().payload());
        } finally {
            assertTrue(reopened.stop(Duration.ofSeconds(5)));
        }
    }

    @Test void schemaCapsOpenBanksAndPagesRetainedHistoryByOwnerAndId() throws Exception {
        var store = new SqliteStorage();
        store.initialize(world.toRealPath());
        var owner = new PartyRef(store.snapshot().worldId(), new PartyRef.Player(player));
        var lookup = new AtomicReference<Query>();
        try {
            store.transaction(tx -> {
                insert(tx, account(1), player, "BANK", "CAD", "OPEN", true);
                insert(tx, account(2), player, "BANK", "EUR", "OPEN", false);
                insert(tx, account(3), player, "TRADING", null, "OPEN", false);
                assertThrows(StorageException.class, () ->
                        insert(tx, account(4), player, "BANK", "EUR", "OPEN", false));
                assertThrows(StorageException.class, () ->
                        insert(tx, account(4), player, "BANK", "JPY", "OPEN", true));
                assertThrows(StorageException.class, () ->
                        insert(tx, account(4), player, "TRADING", null, "OPEN", false));
                insert(tx, account(4), player, "BANK", "JPY", "OPEN", false);
                assertThrows(StorageException.class, () ->
                        insert(tx, account(5), player, "BANK", "USD", "OPEN", false));

                var other = new PlayerId(UUID.randomUUID());
                insert(tx, account(5), other, "BANK", "CAD", "OPEN", true);
                for (int i = 10; i < 510; i++) {
                    insert(tx, account(i), player, "BANK", "EUR", "CLOSED", false);
                    insert(tx, account(i + 1_000), other, "BANK", "EUR", "CLOSED", false);
                }
                assertThrows(StorageException.class, () ->
                        tx.execute("DELETE FROM account_catalog WHERE account_id=?", List.of(account(10).value().toString())));
                assertThrows(StorageException.class, () -> tx.execute(
                        "UPDATE account_catalog SET status='OPEN',revision=revision+1 WHERE account_id=?",
                        List.of(account(10).value().toString())));
                return null;
            });

            var firstPage = store.transaction(tx -> AccountCatalog.history(tx::query, owner, null, 4));
            var nextPage = store.transaction(tx -> AccountCatalog.history((sql, parameters, maximum) -> {
                lookup.set(new Query(sql, parameters));
                assertTrue(maximum <= 5);
                return tx.query(sql, parameters, maximum);
            }, owner, account(4), 4));
            assertEquals(4, firstPage.size());
            assertEquals(4, nextPage.size());
            assertEquals(account(10), nextPage.getFirst().id());
            assertEquals(account(13), nextPage.getLast().id());
        } finally {
            store.close();
        }
        var query = lookup.get();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + world.toRealPath().resolve("economy/economy.sqlite"));
             var statement = connection.prepareStatement("EXPLAIN QUERY PLAN " + query.sql())) {
            for (int i = 0; i < query.parameters().size(); i++) {
                statement.setString(i + 1, query.parameters().get(i));
            }
            try (var plan = statement.executeQuery()) {
                assertTrue(plan.next());
                String detail = plan.getString("detail");
                assertTrue(detail.contains("SEARCH") && detail.contains("owner_id=?") && detail.contains("account_id>?"), detail);
                assertFalse(plan.next(), "Owner lookup must avoid scanning and sorting retained history");
            }
        }
    }

    @Test void uncertainCommitRecoversAccountAndOutcomeTogether() throws Exception {
        var harness = new FailureHarness();
        var store = harness.store;
        store.initialize(world.toRealPath());
        try {
            var opening = new JournalEngine(store, (actor, kind) -> actor.equals(player) && kind.equals(FxService.OPEN),
                    Map.of(FxService.OPEN, FxService.opening(Clock.systemUTC())));
            var selection = new Journal.Intent(operationId(), requestId(), player, FxService.OPEN,
                    new Journal.CanonicalRequest(1, Map.of("currency", "CAD")), null, null);
            assertEquals(Journal.Status.COMMITTED, opening.submit(selection).status());

            var worldId = store.snapshot().worldId();
            var owner = new PartyRef(worldId, new PartyRef.Player(player));
            var session = UUID.randomUUID();
            var grant = PartyAuthorization.admit(worldId, session, player, false, owner, false).orElseThrow();
            var attempt = PartyOperation.owned(grant, AccountCatalog.INITIALIZE_PRIMARY, operationId(), requestId(),
                    new Journal.CanonicalRequest(1, Map.of()), AccountCatalog.initialization(owner, Purpose.BANK));
            var journal = attempt.engine(store, session);

            harness.fault.set(FailureHarness.Fault.BEFORE_COMMIT);
            assertEquals("ABSENT_RETRY_SAME_ID", journal.submit(attempt.intent()).result().code());
            assertTrue(store.transaction(tx -> AccountCatalog.primary(tx::query, owner)).isEmpty());

            harness.fault.set(FailureHarness.Fault.AFTER_COMMIT);
            var committed = journal.submit(attempt.intent());
            assertEquals(Journal.Status.COMMITTED, committed.status());
            assertEquals(committed, journal.lookup(attempt.intent()).orElseThrow());
            assertEquals(committed, journal.submit(attempt.intent()));
            assertEquals(committed.result().payload(), store.transaction(tx ->
                    AccountCatalog.primary(tx::query, owner).orElseThrow().id().value().toString()));
            assertEquals(2, journal.readAfter(0, 3).size());
        } finally {
            store.close();
        }
    }

    private ServerServices open(SqliteStorage store) throws Exception {
        var services = new ServerServices(world.toRealPath(), store);
        await(services, services.readiness());
        return services;
    }

    private PartyRef owner(ServerServices services) {
        return new PartyRef(services.partyWorld(), new PartyRef.Player(player));
    }

    private PartyAuthorization.Grant grant(ServerServices services, PartyRef target) {
        return PartyAuthorization.admit(services.partyWorld(), services.sessionId(), player, false, target, false).orElseThrow();
    }

    private void selectCurrency(ServerServices services, String code) throws Exception {
        var journal = services.journal((actor, kind) -> actor.equals(player) && kind.equals(FxService.OPEN),
                Map.of(FxService.OPEN, FxService.opening(Clock.systemUTC())), PUBLISHER);
        var selection = new Journal.Intent(operationId(), requestId(), player, FxService.OPEN,
                new Journal.CanonicalRequest(1, Map.of("currency", code)), null, null);
        assertEquals(Journal.Status.COMMITTED, complete(services, journal.submit(selection)).status());
    }

    private static int insert(FoundationStore.Transaction tx, AccountId id, PlayerId owner,
                              String purpose, String currency, String status, boolean primary) {
        return tx.execute("INSERT INTO account_catalog(account_id,owner_id,purpose,currency,status,revision,is_primary) "
                        + "VALUES(?,?,?,?,?,1,?)",
                java.util.Arrays.asList(id.value().toString(), owner.value().toString(), purpose, currency, status,
                        primary ? "1" : "0"));
    }

    private static AccountId account(int serial) { return new AccountId(new UUID(0, serial)); }
    private static RequestId requestId() { return new RequestId(UUID.randomUUID()); }
    private static OperationId operationId() { return new OperationId(UUID.randomUUID()); }

    private static Journal.Outcome complete(ServerServices services, JournalService.Submission submission) throws Exception {
        return await(services, submission.completion()).outcome();
    }

    private static <T> T await(ServerServices services, CompletionStage<T> stage) throws Exception {
        var future = stage.toCompletableFuture();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!future.isDone() && System.nanoTime() < deadline) {
            services.tick();
            Thread.sleep(1);
        }
        return future.get(1, TimeUnit.SECONDS);
    }
}
