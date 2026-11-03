package dev.emily.economy.foundation;

import dev.emily.economy.api.AccountIdentity;
import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.api.FundingEligibility;
import dev.emily.economy.api.Money;
import dev.emily.economy.api.Identifiers.AccountId;
import dev.emily.economy.api.Identifiers.PlayerId;
import dev.emily.economy.api.PartyRef;
import dev.emily.economy.journal.Journal;
import dev.emily.economy.journal.JournalEngine;
import dev.emily.economy.storage.FoundationStore;
import dev.emily.economy.storage.SqliteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class AccountQueriesTest {
    @TempDir Path world;
    private final PlayerId owner = new PlayerId(UUID.randomUUID());
    private final PlayerId other = new PlayerId(UUID.randomUUID());

    @Test void ownedLookupHidesForeignAndMissingIdsAndRetainsClosedIdentity() throws Exception {
        var store = new SqliteStorage();
        var services = open(store);
        try {
            change(services, store, tx -> {
                insert(tx, owner, 1, "CAD", "CLOSED");
                insert(tx, other, 2, "JPY", "OPEN");
                insert(tx, owner, 4, "EUR", "OPEN");
            });
            var grant = grant(services, owner, owner, false);
            var closed = await(services, services.findOwnedAccount(grant, account(1))).orElseThrow();
            assertEquals(AccountIdentity.Status.CLOSED, closed.status());
            assertEquals(Optional.empty(), await(services, services.findOwnedAccount(grant, account(2))));
            assertEquals(Optional.empty(), await(services, services.findOwnedAccount(grant, account(3))));
            assertEquals(Optional.empty(), await(services, services.accountFunds(grant, account(2))));
            assertEquals(Optional.empty(), await(services, services.accountFunds(grant, account(3))));
            assertEquals(Optional.of(AccountQueries.FundsStatus.LEDGER_UNAVAILABLE),
                    await(services, services.accountFunds(grant, account(4))));
            assertEquals(Optional.of(FundingEligibility.Status.LEDGER_UNAVAILABLE),
                    await(services, services.evaluateFunding(grant, account(4), 1, new Money(EconomyCurrency.EUR, 1))));
        } finally {
            assertTrue(services.stop(Duration.ofSeconds(5)));
        }
    }

    @Test void keysetPagingBoundsRequestsAndRemainsStableAcrossClosureAndInsert() throws Exception {
        var store = new SqliteStorage();
        var services = open(store);
        try {
            change(services, store, tx -> {
                insert(tx, owner, 1, "CAD", "OPEN");
                insert(tx, owner, 3, "USD", "OPEN");
                insert(tx, other, 2, "JPY", "OPEN");
            });
            var grant = grant(services, owner, owner, false);
            assertThrows(IllegalArgumentException.class, () -> services.listOwnedAccounts(grant, null, 0));
            assertThrows(IllegalArgumentException.class, () ->
                    services.listOwnedAccounts(grant, null, AccountQueries.MAX_PAGE_SIZE + 1));
            assertThrows(IllegalArgumentException.class, () -> services.listOwnedAccounts(grant, "?", 1));
            assertThrows(IllegalArgumentException.class, () -> services.listOwnedAccounts(grant, "a".repeat(4096), 1));
            var first = await(services, services.listOwnedAccounts(grant, null, 1)).orElseThrow();
            assertEquals(List.of(account(1)), first.accounts().stream().map(AccountIdentity::id).toList());
            assertNotNull(first.nextCursor());
            var otherGrant = grant(services, other, other, false);
            assertEquals(Optional.empty(), await(services, services.listOwnedAccounts(otherGrant, first.nextCursor(), 1)));

            change(services, store, tx -> {
                tx.execute("UPDATE account_catalog SET status='CLOSED',revision=revision+1 WHERE account_id=?",
                        List.of(account(1).value().toString()));
                insert(tx, owner, 4, "EUR", "OPEN");
            });
            var second = await(services, services.listOwnedAccounts(grant, first.nextCursor(), 2)).orElseThrow();
            assertEquals(List.of(account(3), account(4)), second.accounts().stream().map(AccountIdentity::id).toList());
            assertNull(second.nextCursor());
        } finally {
            assertTrue(services.stop(Duration.ofSeconds(5)));
        }
    }

    @Test void inspectionAuditsAuthorizedCallsAndRechecksLiveAudienceBeforeDelivery() throws Exception {
        var store = new SqliteStorage();
        var services = open(store);
        try {
            change(services, store, tx -> insert(tx, owner, 1, "CAD", "OPEN"));
            var ordinary = grant(services, owner, owner, false);
            assertEquals(Optional.empty(), await(services, services.inspectAccount(ordinary, account(1), () -> true)));
            assertTrue(audits(services, store).isEmpty());

            var inspection = grant(services, other, owner, true);
            var inspected = await(services, services.inspectAccount(inspection, account(1), () -> true)).orElseThrow();
            assertEquals(account(1), inspected.id());
            var page = await(services, services.inspectAccounts(inspection, null, 1, () -> true)).orElseThrow();
            assertEquals(List.of(account(1)), page.accounts().stream().map(AccountIdentity::id).toList());

            // Keep the live source through the read, then revoke it before delivery.
            var testThread = Thread.currentThread();
            var liveChecks = new AtomicInteger();
            var revoked = services.inspectAccount(inspection, account(1), () -> {
                assertSame(testThread, Thread.currentThread());
                return liveChecks.getAndIncrement() < 2;
            });
            assertEquals(Optional.empty(), await(services, revoked));
            assertEquals(3, liveChecks.get());

            var audit = audits(services, store);
            assertEquals(3, audit.size());
            assertEquals(3, audit.stream().map(Journal.Envelope::operationId).distinct().count());
            for (var envelope : audit) {
                assertEquals(InspectionAudit.KIND, envelope.kind());
                assertEquals(other, envelope.actor());
                assertEquals("player:" + owner.value(), envelope.records().getFirst().payload());
                assertNotNull(envelope.auditInstant().value());
            }
        } finally {
            assertTrue(services.stop(Duration.ofSeconds(5)));
        }
    }

    private ServerServices open(SqliteStorage store) throws Exception {
        var services = new ServerServices(world.toRealPath(), store);
        await(services, services.readiness());
        return services;
    }

    private PartyAuthorization.Grant grant(ServerServices services, PlayerId actor, PlayerId target, boolean inspection) {
        var worldId = services.partyWorld();
        var party = new PartyRef(worldId, new PartyRef.Player(target));
        return PartyAuthorization.admit(worldId, services.sessionId(), actor, inspection, party, inspection).orElseThrow();
    }

    private static void change(ServerServices services, SqliteStorage store,
                               Consumer<FoundationStore.Transaction> fixture) throws Exception {
        await(services, services.storage(() -> store.transaction(tx -> {
            fixture.accept(tx);
            return null;
        })));
    }

    private static List<Journal.Envelope> audits(ServerServices services, SqliteStorage store) throws Exception {
        return await(services, services.storage(() -> new JournalEngine(store, (actor, kind) -> false, Map.of()).readAfter(0, 8)));
    }

    private static void insert(FoundationStore.Transaction tx, PlayerId owner, int serial, String currency, String status) {
        tx.execute("INSERT INTO account_catalog(account_id,owner_id,purpose,currency,status,revision,is_primary) "
                        + "VALUES(?,?,'BANK',?,?,1,0)",
                List.of(account(serial).value().toString(), owner.value().toString(), currency, status));
    }

    private static AccountId account(int serial) {
        return new AccountId(new UUID(0, serial));
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
