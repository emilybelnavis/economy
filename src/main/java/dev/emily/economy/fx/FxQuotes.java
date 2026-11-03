package dev.emily.economy.fx;

import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.api.Money;
import dev.emily.economy.api.Identifiers.PlayerId;
import dev.emily.economy.api.Identifiers.WorldId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One trusted worker-confined book per server session. A4 owns durable consumption. */
public final class FxQuotes {
    public enum Side { EXACT_DEBIT, EXACT_CREDIT }
    public record Pair(EconomyCurrency source, EconomyCurrency target) {
        public Pair {
            if (source == null || target == null || source == target) throw new IllegalArgumentException("Invalid pair");
        }
    }

    public record Quote(UUID id, PlayerId actor, WorldId world, Pair pair, Side side, Money requested,
                        UUID publication, int pricingVersion, Instant expires, Money fee, Money debit, Money credit) {}

    private static final int CAPACITY = 1024;
    private static final int PRICING_VERSION = 1;
    private final WorldId world;
    private final Map<UUID, Quote> quotes = new HashMap<>();

    public FxQuotes(WorldId world) {
        this.world = java.util.Objects.requireNonNull(world);
    }

    /** Actor comes from authenticated server admission and publication from the world store. */
    public Quote issue(PlayerId actor, Pair pair, Side side, Money requested, FxFacts.Publication publication,
                       Instant now, Duration lifetime) {
        java.util.Objects.requireNonNull(actor);
        java.util.Objects.requireNonNull(side);
        if (publication == null) throw new IllegalStateException("FX_UNINITIALIZED");
        if (lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofSeconds(60)) > 0
                || requested.minorUnits() <= 0) throw new IllegalArgumentException("FX_INVALID_QUOTE");
        boolean exactDebit = side == Side.EXACT_DEBIT;
        var source = exactDebit ? pair.source() : pair.target();
        var target = exactDebit ? pair.target() : pair.source();
        if (requested.currency() != source) throw new IllegalArgumentException("FX_INVALID_QUOTE");

        quotes.values().removeIf(quote -> !fresh(quote, publication, PRICING_VERSION, now));
        if (quotes.size() >= CAPACITY) throw new IllegalStateException("FX_QUOTE_OVERLOAD");

        BigDecimal numerator = BigDecimal.valueOf(requested.minorUnits()).multiply(publication.rates().get(source))
                .multiply(BigDecimal.valueOf(target.minorUnitsPerMajor()));
        BigDecimal denominator = publication.rates().get(target).multiply(BigDecimal.valueOf(source.minorUnitsPerMajor()));
        long units = numerator.divide(denominator, 0, exactDebit ? RoundingMode.FLOOR : RoundingMode.CEILING).longValueExact();
        if (units <= 0) throw new IllegalArgumentException("FX_ZERO_CREDIT");
        var converted = new Money(target, units);
        var quote = new Quote(UUID.randomUUID(), actor, world, pair, side, requested, publication.updateId(),
                PRICING_VERSION, now.plus(lifetime), new Money(pair.source(), 0),
                exactDebit ? requested : converted, exactDebit ? converted : requested);
        quotes.put(quote.id(), quote);
        return quote;
    }

    /** Call inside A4 authoritative preparation, after original-operation lookup. */
    public Quote requireFresh(UUID id, PlayerId actor, WorldId requestWorld, FxFacts.Publication publication,
                              int currentPricingVersion, Instant processingStart) {
        var quote = quotes.get(id);
        if (quote == null || !quote.actor().equals(actor) || !quote.world().equals(requestWorld)
                || !fresh(quote, publication, currentPricingVersion, processingStart)) {
            throw new IllegalArgumentException("FX_QUOTE_UNAVAILABLE");
        }
        return quote;
    }

    private static boolean fresh(Quote quote, FxFacts.Publication publication, int pricing, Instant now) {
        return publication != null && quote.publication().equals(publication.updateId())
                && quote.pricingVersion() == pricing && now.isBefore(quote.expires());
    }
}
