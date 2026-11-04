package dev.emily.economy.journal;

import dev.emily.economy.account.AccountLifecycle;
import dev.emily.economy.api.Identifiers.*;
import dev.emily.economy.api.Money;
import dev.emily.economy.api.PartyRef;
import dev.emily.economy.fx.FxQuotes;
import dev.emily.economy.fx.FxService;
import dev.emily.economy.storage.SqliteStorage;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.emily.economy.api.EconomyCurrency.*;
import static dev.emily.economy.journal.JournalFixtures.ACTOR;
import static org.junit.jupiter.api.Assertions.*;

class FundedClosureTest {
    @TempDir Path world;

    @Test void zeroPayoutStillDrainsAndClosesInOneTransaction() throws Exception {
        var store = new SqliteStorage();
        try {
            store.initialize(world.toRealPath());
            var worldId = store.snapshot().worldId();
            var owner = new PartyRef(worldId, new PartyRef.Player(ACTOR));
            var source = new AccountId(UUID.randomUUID());
            var destination = new AccountId(UUID.randomUUID());
            var now = Instant.parse("2026-10-01T00:00:00Z");
            var opening = new JournalEngine(store, (actor, kind) -> true,
                    Map.of(FxService.OPEN, FxService.opening(Clock.fixed(now, ZoneOffset.UTC))));
            assertEquals(Journal.Status.COMMITTED, opening.submit(intent(FxService.OPEN,
                    new Journal.CanonicalRequest(1, Map.of("currency", "CAD")))).status());
            store.transaction(tx -> {
                tx.execute("INSERT INTO account_catalog VALUES(?,?,'BANK','CAD','OPEN',1,1)",
                        List.of(destination.value().toString(), ACTOR.value().toString()));
                tx.execute("INSERT INTO account_catalog VALUES(?,?,'BANK','JPY','OPEN',1,0)",
                        List.of(source.value().toString(), ACTOR.value().toString()));
                return null;
            });
            var ledger = new JournalEngine(store, (actor, kind) -> true,
                    SameCurrency.registrations(worldId, (intent, instruction, reads) -> true));
            var amount = new Money(JPY, 1);
            var credit = new SameCurrency.Adjustment(ACTOR, new SameCurrency.Revision(source, 1), amount);
            assertEquals(Journal.Status.COMMITTED, ledger.submit(intent(SameCurrency.CREDIT, credit.request())).status());
            var quotes = new FxQuotes(worldId);
            var quote = store.transaction(tx -> quotes.issue(ACTOR, new FxQuotes.Pair(JPY, CAD),
                    FxQuotes.Side.EXACT_DEBIT, amount, FxService.latest(tx::query), now, Duration.ofSeconds(60)));

            var request = new FundedClosure.Instruction(new AccountLifecycle.Close(source, 1, destination, 1L), quote.id());
            var registration = FundedClosure.registration(owner, request,
                    (id, actor, world, reads) -> quotes.requireFresh(id, actor, world, FxService.latest(reads),
                            quote.pricingVersion(), now),
                    (reads, account) -> Optional.of(new FundedClosure.Claims(false, false)),
                    (intent, reads) -> true);
            var engine = new JournalEngine(store, (actor, kind) -> true, Map.of(AccountLifecycle.CLOSE, registration));
            var fields = new HashMap<>(request.request().fields());
            fields.put("economy:owner", Journal.actorKey(ACTOR));
            var operation = intent(AccountLifecycle.CLOSE, new Journal.CanonicalRequest(1, fields));
            var closed = engine.submit(operation);
            assertEquals(Journal.Status.COMMITTED, closed.status(), closed.result().code());
            assertEquals(closed, engine.submit(operation));
            var envelope = engine.readAfter(0, 10).getLast();
            assertEquals(closed.transactionId(), envelope.transactionId());
            assertEquals(2, envelope.records().stream().filter(record -> record.type().equals("economy:posting")).count());
            assertTrue(envelope.records().stream().anyMatch(record -> record.type().equals("economy:bank-lifecycle")));
            store.transaction(tx -> {
                assertEquals(0, Settlement.posted(tx::query, source.value(), JPY));
                assertEquals(0, Settlement.posted(tx::query, destination.value(), CAD));
                assertFalse(Settlement.openBank(tx::query, source.value(), JPY));
                return null;
            });
        } finally {
            store.close();
        }
    }

    private static Journal.Intent intent(Journal.Kind kind, Journal.CanonicalRequest request) {
        return new Journal.Intent(new OperationId(UUID.randomUUID()), new RequestId(UUID.randomUUID()),
                ACTOR, kind, request, null, null);
    }
}
