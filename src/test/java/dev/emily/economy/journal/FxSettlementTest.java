package dev.emily.economy.journal;

import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.api.Identifiers.AccountId;
import dev.emily.economy.api.Identifiers.OperationId;
import dev.emily.economy.api.Identifiers.RequestId;
import dev.emily.economy.api.Money;
import dev.emily.economy.fx.FxQuotes;
import dev.emily.economy.storage.FailureHarness;
import dev.emily.economy.storage.SqliteStorage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static dev.emily.economy.api.EconomyCurrency.CAD;
import static dev.emily.economy.api.EconomyCurrency.USD;
import static dev.emily.economy.journal.Journal.*;
import static dev.emily.economy.journal.JournalFixtures.ACTOR;
import static org.junit.jupiter.api.Assertions.*;

class FxSettlementTest {
    private final AccountId source = new AccountId(UUID.randomUUID());
    private final AccountId target = new AccountId(UUID.randomUUID());
    @TempDir Path world;

    @Test void fractionalPayoutAndFeePostOnceEvenAfterRollback() throws Exception {
        var harness = new FailureHarness();
        var store = harness.store;
        try {
            store.initialize(world.toRealPath());
            seed(store);
            var quote = quote(store, 2_000, 100, 1_599);
            var fresh = new AtomicBoolean(true);
            var engine = engine(store, quote, fresh);
            var intent = exchange(quote);

            harness.fault.set(FailureHarness.Fault.BEFORE_COMMIT);
            assertEquals("ABSENT_RETRY_SAME_ID", engine.submit(intent).result().code());
            assertEquals(10_000, balance(store, source.value(), CAD));
            assertEquals(0, balance(store, Settlement.feeAccount(CAD), CAD));

            var settled = engine.submit(intent);
            assertEquals(Status.COMMITTED, settled.status());
            assertEquals("FX_QUOTE_CONSUMED", engine.submit(exchange(quote)).result().code());
            fresh.set(false);
            assertEquals(settled, engine.submit(intent));
            assertEquals("FX_QUOTE_STALE", engine.submit(exchange(quote)).result().code());
            assertEquals(7_900, balance(store, source.value(), CAD));
            assertEquals(1_500, balance(store, target.value(), USD));
            assertEquals(100, balance(store, Settlement.feeAccount(CAD), CAD));
            assertEquals(2_000, balance(store, Settlement.clearingAccount(CAD), CAD));
            assertEquals(-1_500, balance(store, Settlement.clearingAccount(USD), USD));
            assertEquals(6, engine.readAfter(0, 10).getLast().records().size());
        } finally {
            store.close();
        }
    }

    @Test void zeroPayoutHasNoTargetLeg() throws Exception {
        var store = new SqliteStorage();
        try {
            store.initialize(world.toRealPath());
            seed(store);
            var quote = quote(store, 100, 0, 50);
            var engine = engine(store, quote, new AtomicBoolean(true));
            assertEquals(Status.COMMITTED, engine.submit(exchange(quote)).status());
            assertEquals(9_900, balance(store, source.value(), CAD));
            assertEquals(0, balance(store, target.value(), USD));
            assertEquals(3, engine.readAfter(0, 10).getLast().records().size());
        } finally {
            store.close();
        }
    }

    private void seed(SqliteStorage store) {
        store.transaction(tx -> {
            tx.execute("INSERT INTO account_catalog VALUES(?,?,'BANK','CAD','OPEN',1,1)",
                    List.of(source.value().toString(), ACTOR.value().toString()));
            tx.execute("INSERT INTO account_catalog VALUES(?,?,'BANK','USD','OPEN',1,0)",
                    List.of(target.value().toString(), ACTOR.value().toString()));
            return null;
        });
        var engine = new JournalEngine(store, (actor, kind) -> ACTOR.equals(actor),
                SameCurrency.registrations(store.snapshot().worldId(), (intent, instruction, reads) -> true));
        var request = new SameCurrency.Adjustment(ACTOR, new SameCurrency.Revision(source, 1), new Money(CAD, 10_000));
        assertEquals(Status.COMMITTED, engine.submit(intent(SameCurrency.CREDIT, request.request())).status());
    }

    private static JournalEngine engine(SqliteStorage store, FxQuotes.Quote quote, AtomicBoolean fresh) {
        var registration = FxSettlement.registration(store.snapshot().worldId(), (id, actor, world, reads) -> {
            if (!fresh.get()) throw new IllegalArgumentException("Fixture expired quote");
            return quote;
        },
                (intent, exchange, reads) -> true);
        return new JournalEngine(store, (actor, kind) -> ACTOR.equals(actor), Map.of(FxSettlement.EXCHANGE, registration));
    }

    private Intent exchange(FxQuotes.Quote quote) {
        return intent(FxSettlement.EXCHANGE, new FxSettlement.Exchange(quote.id(), ACTOR,
                new SameCurrency.Revision(source, 1), new SameCurrency.Revision(target, 1)).request());
    }

    private static Intent intent(Kind kind, CanonicalRequest request) {
        return new Intent(new OperationId(UUID.randomUUID()), new RequestId(UUID.randomUUID()), ACTOR, kind, request, null, null);
    }

    private static FxQuotes.Quote quote(SqliteStorage store, long debit, long fee, long rawCredit) {
        return new FxQuotes.Quote(UUID.randomUUID(), ACTOR, store.snapshot().worldId(), new FxQuotes.Pair(CAD, USD),
                FxQuotes.Side.EXACT_DEBIT, new Money(CAD, debit), UUID.randomUUID(), 1,
                Instant.MAX, new Money(CAD, fee), new Money(CAD, debit), new Money(USD, rawCredit));
    }

    private static long balance(SqliteStorage store, UUID account, EconomyCurrency currency) {
        return store.transaction(tx -> Settlement.posted(tx::query, account, currency));
    }
}
