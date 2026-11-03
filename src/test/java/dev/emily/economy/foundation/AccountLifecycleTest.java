package dev.emily.economy.foundation;

import dev.emily.economy.account.AccountCatalog;
import dev.emily.economy.account.AccountLifecycle;
import dev.emily.economy.account.AccountLifecycle.Close;
import dev.emily.economy.account.AccountLifecycle.ClosureReader;
import dev.emily.economy.account.AccountLifecycle.ClosureState;
import dev.emily.economy.account.AccountLifecycle.Open;
import dev.emily.economy.api.AccountIdentity.Status;
import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.api.Identifiers.*;
import dev.emily.economy.api.PartyRef;
import dev.emily.economy.fx.FxService;
import dev.emily.economy.journal.Journal;
import dev.emily.economy.journal.JournalEngine;
import dev.emily.economy.journal.JournalService;
import dev.emily.economy.storage.FailureHarness;
import dev.emily.economy.storage.FoundationStore;
import dev.emily.economy.storage.SqliteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static dev.emily.economy.api.EconomyCurrency.*;
import static org.junit.jupiter.api.Assertions.*;

class AccountLifecycleTest {
    @TempDir Path world;
    private final PlayerId player = new PlayerId(UUID.randomUUID());
    private final UUID session = UUID.randomUUID();

    private static final JournalService.Publisher PUBLISHER = new JournalService.Publisher() {
        @Override public void invalidate(Journal.Outcome committed) {}
        @Override public void publish(Journal.Outcome terminal) {}
    };

    @Test void queuedDuplicateAndLastSlotOpensSerialize() throws Exception {
        var store = new SqliteStorage();
        var services = new ServerServices(world.toRealPath(), store);
        try {
            await(services, services.readiness());
            var owner = owner(services.partyWorld(), player);
            var grant = grant(owner, services.sessionId());
            var journal = services.journal((actor, kind) -> actor.equals(player) && kind.equals(FxService.OPEN),
                    Map.of(FxService.OPEN, FxService.opening(Clock.systemUTC())), PUBLISHER);
            assertEquals(Journal.Status.COMMITTED, complete(services, journal.submit(selection())).status());
            assertEquals(Journal.Status.COMMITTED,
                    complete(services, services.initializePrimary(grant, operationId(), requestId(), PUBLISHER)).status());

            var duplicate = compete(services, grant, new Open(EUR, 1), new Open(EUR, 1));
            assertEquals(List.of("BANK_OPENED", "BANK_CURRENCY_EXISTS"), duplicate);
            var lastSlot = compete(services, grant, new Open(JPY, 1), new Open(USD, 1));
            assertEquals(List.of("BANK_OPENED", "BANK_CAP_REACHED"), lastSlot);

            var accounts = await(services, services.storage(() -> store.transaction(tx ->
                    AccountCatalog.history(tx::query, owner, null, 8))));
            assertEquals(3, accounts.size());
            assertEquals(3, accounts.stream().map(account -> account.currency()).distinct().count());
        } finally {
            assertTrue(services.stop(Duration.ofSeconds(5)));
        }
    }

    @Test void closureChecksProtectionClaimsAndDestinationWithoutChangingAccounts() throws Exception {
        var store = new SqliteStorage();
        store.initialize(world.toRealPath());
        try {
            select(store);
            var owner = owner(store.snapshot().worldId(), player);
            var primary = accountId();
            var denominationBank = accountId();
            var source = accountId();
            var foreign = accountId();
            store.transaction(tx -> {
                insertBank(tx, primary, player, EUR, true);
                insertBank(tx, denominationBank, player, CAD, false);
                insertBank(tx, source, player, JPY, false);
                insertBank(tx, foreign, new PlayerId(UUID.randomUUID()), USD, false);
                return null;
            });

            ClosureReader untouched = (reads, account) -> fail("Ledger must remain private for invalid sources");
            assertEquals("ACCOUNT_PROTECTED", close(store, owner, new Close(primary, 1, null, null), untouched));
            assertEquals("ACCOUNT_PROTECTED", close(store, owner, new Close(denominationBank, 1, null, null), untouched));
            assertEquals("ACCOUNT_STALE", close(store, owner, new Close(source, 2, null, null), untouched));
            assertEquals("ACTIVE_HOLD", close(store, owner, new Close(source, 1, null, null),
                    state(0, true, false, false)));
            assertEquals("RESTRICTED_FUNDS", close(store, owner, new Close(source, 1, null, null),
                    state(0, false, true, false)));
            assertEquals("UNRESOLVED_OBLIGATION", close(store, owner, new Close(source, 1, null, null),
                    state(0, false, false, true)));
            assertEquals("CLOSURE_STATE_UNAVAILABLE", close(store, owner, new Close(source, 1, null, null),
                    (reads, account) -> Optional.empty()));

            var funded = state(100, false, false, false);
            assertEquals("DESTINATION_UNAVAILABLE", close(store, owner, new Close(source, 1, foreign, 1L), funded));
            assertEquals("DESTINATION_STALE", close(store, owner, new Close(source, 1, primary, 2L), funded));
            assertEquals("FUNDED_SETTLEMENT_UNAVAILABLE", close(store, owner, new Close(source, 1, primary, 1L), funded));
            var unchanged = store.transaction(tx -> AccountCatalog.find(tx::query, owner, source).orElseThrow());
            assertEquals(Status.OPEN, unchanged.status());
            assertEquals(1, unchanged.revision());
        } finally {
            store.close();
        }
    }

    @Test void interruptedClosureRollsBackAndRetainsClosedIdentityAfterRestart() throws Exception {
        var harness = new FailureHarness();
        var store = harness.store;
        store.initialize(world.toRealPath());
        var source = accountId();
        var operationId = operationId();
        var request = new Close(source, 1, null, null);
        PartyRef owner;
        Journal.Outcome original;
        try {
            select(store);
            owner = owner(store.snapshot().worldId(), player);
            store.transaction(tx -> {
                insertBank(tx, accountId(), player, CAD, true);
                insertBank(tx, source, player, JPY, false);
                return null;
            });
            var attempt = closure(owner, session, operationId, request, state(0, false, false, false));
            var engine = attempt.engine(store, session);
            harness.fault.set(FailureHarness.Fault.BEFORE_COMMIT);
            assertEquals("ABSENT_RETRY_SAME_ID", engine.submit(attempt.intent()).result().code());
            assertEquals(Status.OPEN, store.transaction(tx -> AccountCatalog.find(tx::query, owner, source).orElseThrow().status()));
            assertTrue(engine.lookup(attempt.intent()).isEmpty());

            original = engine.submit(attempt.intent());
            assertEquals(Journal.Status.COMMITTED, original.status());
            assertEquals("BANK_CLOSED", original.result().code());
            assertEquals(original, engine.submit(attempt.intent()));
        } finally {
            store.close();
        }

        var reopened = new SqliteStorage();
        reopened.initialize(world.toRealPath());
        try {
            var newSession = UUID.randomUUID();
            var retry = closure(owner, newSession, operationId, request, (reads, account) -> fail("Replay must bypass ledger"));
            var engine = retry.engine(reopened, newSession);
            assertEquals(original, engine.lookup(retry.intent()).orElseThrow());
            assertEquals(original, engine.submit(retry.intent()));
            var closed = reopened.transaction(tx -> AccountCatalog.find(tx::query, owner, source).orElseThrow());
            assertEquals(Status.CLOSED, closed.status());
            assertEquals(2, closed.revision());
            assertEquals(JPY, closed.currency());
            var fact = engine.readAfter(original.sequence().value() - 1, 2).getFirst();
            assertEquals(player, fact.actor());
            assertEquals(owner.world(), fact.worldId());
            assertEquals(original.operationId(), fact.operationId());
            assertTrue(fact.records().stream().anyMatch(record -> record.payload().contains(source.value().toString())));
        } finally {
            reopened.close();
        }
    }

    private List<String> compete(ServerServices services, PartyAuthorization.Grant grant,
                                 Open first, Open second) throws Exception {
        var blocked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        services.storage(() -> {
            blocked.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            return null;
        });
        JournalService.Submission one;
        JournalService.Submission two;
        try {
            assertTrue(blocked.await(5, TimeUnit.SECONDS));
            one = open(services, grant, first);
            two = open(services, grant, second);
            assertEquals(Journal.Receipt.ACCEPTED_PENDING, one.receipt());
            assertEquals(Journal.Receipt.ACCEPTED_PENDING, two.receipt());
        } finally {
            release.countDown();
        }
        return List.of(complete(services, one).result().code(), complete(services, two).result().code());
    }

    private JournalService.Submission open(ServerServices services, PartyAuthorization.Grant grant, Open request) {
        return services.submitOwned(grant, AccountLifecycle.OPEN, operationId(), requestId(), request.request(),
                AccountLifecycle.opening(grant.target(), request), PUBLISHER);
    }

    private String close(SqliteStorage store, PartyRef owner, Close request, ClosureReader reader) {
        var attempt = closure(owner, session, operationId(), request, reader);
        return attempt.engine(store, session).submit(attempt.intent()).result().code();
    }

    private PartyOperation closure(PartyRef owner, UUID scope, OperationId id, Close request, ClosureReader reader) {
        return PartyOperation.owned(grant(owner, scope), AccountLifecycle.CLOSE, id, requestId(), request.request(),
                AccountLifecycle.closing(owner, request, reader));
    }

    private PartyAuthorization.Grant grant(PartyRef owner, UUID scope) {
        return PartyAuthorization.admit(owner.world(), scope, player, false, owner, false).orElseThrow();
    }

    private static PartyRef owner(WorldId world, PlayerId player) {
        return new PartyRef(world, new PartyRef.Player(player));
    }

    private static ClosureReader state(long minorUnits, boolean hold, boolean restricted, boolean obligation) {
        return (reads, account) -> Optional.of(new ClosureState(minorUnits, hold, restricted, obligation));
    }

    private static void insertBank(FoundationStore.Transaction tx, AccountId account, PlayerId owner,
                                   EconomyCurrency currency, boolean primary) {
        tx.execute("""
                INSERT INTO account_catalog(account_id,owner_id,purpose,currency,status,revision,is_primary)
                VALUES(?,?,'BANK',?,'OPEN',1,?)
                """, List.of(account.value().toString(), owner.value().toString(), currency.code(), primary ? "1" : "0"));
    }

    private void select(SqliteStorage store) {
        var engine = new JournalEngine(store, (actor, kind) -> actor.equals(player) && kind.equals(FxService.OPEN),
                Map.of(FxService.OPEN, FxService.opening(Clock.systemUTC())));
        assertEquals(Journal.Status.COMMITTED, engine.submit(selection()).status());
    }

    private Journal.Intent selection() {
        return new Journal.Intent(operationId(), requestId(), player, FxService.OPEN,
                new Journal.CanonicalRequest(1, Map.of("currency", "CAD")), null, null);
    }

    private static AccountId accountId() { return new AccountId(UUID.randomUUID()); }
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
