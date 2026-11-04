package dev.emily.economy.journal;

import dev.emily.economy.account.AccountRebasePlanner;
import dev.emily.economy.api.Identifiers.AccountId;
import dev.emily.economy.api.Identifiers.OperationId;
import dev.emily.economy.api.Identifiers.PlayerId;
import dev.emily.economy.api.Identifiers.RequestId;
import dev.emily.economy.api.Money;
import dev.emily.economy.fx.DenominationChange;
import dev.emily.economy.fx.FxService;
import dev.emily.economy.storage.SqliteStorage;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static dev.emily.economy.api.EconomyCurrency.CAD;
import static dev.emily.economy.api.EconomyCurrency.USD;
import static dev.emily.economy.journal.Journal.*;
import static dev.emily.economy.journal.JournalFixtures.ACTOR;
import static org.junit.jupiter.api.Assertions.*;

class RebaseSettlementTest {
    private static final AccountRebasePlanner.ClaimsReader CLEAR =
            (reads, id) -> Optional.of(new AccountRebasePlanner.Claims(false, false, false));
    @TempDir Path directory;

    @Test void fundedCollisionsAtMaximumRosterRetireAndReplay() throws Exception {
        var store = new SqliteStorage();
        try {
            store.initialize(directory.toRealPath());
            var world = store.snapshot().worldId();
            var handlers = new HashMap<>(SameCurrency.registrations(world, (intent, instruction, reads) -> true));
            handlers.put(FxService.OPEN, FxService.opening(Clock.systemUTC()));
            var setup = new JournalEngine(store, (actor, kind) -> actor.equals(ACTOR), handlers);
            var opening = intent(FxService.OPEN, new CanonicalRequest(1, Map.of("currency", "CAD")));
            assertEquals(Status.COMMITTED, setup.submit(opening).status());
            for (int index = 0; index < RebaseSettlement.MAX_PRIMARIES; index++) {
                var owner = new PlayerId(UUID.randomUUID());
                var primary = new AccountId(UUID.randomUUID());
                var collision = new AccountId(UUID.randomUUID());
                store.transaction(tx -> {
                    tx.execute("INSERT INTO account_catalog VALUES(?,?,'BANK','CAD','OPEN',1,1)",
                            List.of(primary.value().toString(), owner.value().toString()));
                    tx.execute("INSERT INTO account_catalog VALUES(?,?,'BANK','USD','OPEN',1,0)",
                            List.of(collision.value().toString(), owner.value().toString()));
                    return null;
                });
                assertEquals(Status.COMMITTED, setup.submit(intent(SameCurrency.CREDIT,
                        new SameCurrency.Adjustment(owner, new SameCurrency.Revision(primary, 1),
                                new Money(CAD, 1_000)).request())).status());
                assertEquals(Status.COMMITTED, setup.submit(intent(SameCurrency.CREDIT,
                        new SameCurrency.Adjustment(owner, new SameCurrency.Revision(collision, 1),
                                new Money(USD, 500)).request())).status());
            }
            var session = UUID.randomUUID();
            var confirmation = new DenominationChange(world, session);
            var proof = confirmation.confirm(confirmation.first(ACTOR, USD, 0, true, 0), ACTOR, 0, true, 1);
            var plan = store.transaction(tx -> AccountRebasePlanner.plan(tx::query, world, session, ACTOR, proof,
                    RebaseSettlement.denomination(tx::query).orElseThrow(), CLEAR)).plan();
            assertEquals(RebaseSettlement.MAX_PRIMARIES, plan.mappings().size());

            var publication = opening.operationId().value();
            var registration = RebaseSettlement.registration(world, session, proof, plan, publication,
                    (actor, reads) -> true, CLEAR);
            var engine = new JournalEngine(store, (actor, kind) -> ACTOR.equals(actor),
                    Map.of(RebaseSettlement.KIND, registration));
            var request = intent(RebaseSettlement.KIND, RebaseSettlement.request(plan, publication));
            var outcome = engine.submit(request);
            assertEquals(Status.COMMITTED, outcome.status(), outcome.result().code());
            assertEquals(outcome, engine.submit(new Intent(request.operationId(), new RequestId(UUID.randomUUID()),
                    ACTOR, request.kind(), request.request(), null, null)));
            var envelope = engine.readAfter(outcome.sequence().value() - 1, 1).getFirst();
            assertEquals(1 + 6 * RebaseSettlement.MAX_PRIMARIES, envelope.records().size());
            assertEquals(new AccountRebasePlanner.Denomination(USD, 1),
                    store.transaction(tx -> RebaseSettlement.denomination(tx::query).orElseThrow()));
            for (var mapping : plan.mappings()) {
                var balance = store.transaction(tx -> Settlement.posted(tx::query, mapping.primary().id().value(), USD));
                assertEquals(1_200, balance);
                var collision = store.transaction(tx -> tx.query("SELECT status,revision FROM account_catalog WHERE account_id=?",
                        List.of(mapping.collision().id().value().toString()), 1).getFirst());
                assertEquals(Map.of("status", "CLOSED", "revision", "2"), collision);
            }
        } finally {
            store.close();
        }
    }

    private static Intent intent(Kind kind, CanonicalRequest request) {
        return new Intent(new OperationId(UUID.randomUUID()), new RequestId(UUID.randomUUID()),
                ACTOR, kind, request, null, null);
    }
}
