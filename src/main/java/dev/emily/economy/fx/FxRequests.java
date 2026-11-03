package dev.emily.economy.fx;

import dev.emily.economy.api.Identifiers.PlayerId;
import dev.emily.economy.api.Money;
import dev.emily.economy.api.RequestContext;
import dev.emily.economy.foundation.ServerServices;
import dev.emily.economy.storage.FoundationStore;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

/** Server-thread entry points for trusted handlers that derive context from the authenticated player. */
public final class FxRequests {
    private final ServerServices services;
    private final FoundationStore store;
    private final Clock wall;
    private FxQuotes quotes;

    public FxRequests(ServerServices services, FoundationStore store, Clock wall) {
        this.services = services;
        this.store = store;
        this.wall = wall;
    }

    public CompletionStage<FxQuotes.Quote> quote(RequestContext context, FxQuotes.Pair pair,
                                                 FxQuotes.Side side, Money amount) {
        PlayerId actor = authenticate(context);
        return services.storage(actor, () -> store.transaction(tx -> {
            if (quotes == null) quotes = new FxQuotes(store.snapshot().worldId());
            return quotes.issue(actor, pair, side, amount, FxService.latest(tx::query),
                    wall.instant(), Duration.ofSeconds(60));
        }));
    }

    public CompletionStage<FxPairHistory.Page> history(RequestContext context, FxQuotes.Pair pair,
                                                       FxPairHistory.Cursor cursor, int limit) {
        PlayerId actor = authenticate(context);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("FX history page size");
        return services.storage(actor, () -> store.transaction(tx ->
                FxPairHistory.page(tx::query, store.snapshot().worldId(), pair, cursor, limit)));
    }

    private PlayerId authenticate(RequestContext context) {
        if (!services.sessionId().equals(context.serverSessionId())) {
            throw new IllegalArgumentException("FX session mismatch");
        }
        return new PlayerId(context.actorId());
    }
}
