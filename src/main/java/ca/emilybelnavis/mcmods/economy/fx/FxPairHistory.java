package ca.emilybelnavis.mcmods.economy.fx;

import ca.emilybelnavis.mcmods.economy.api.Identifiers.WorldId;
import ca.emilybelnavis.mcmods.economy.journal.JournalEngine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded worker reads. A missing expected day is explicit and never synthesized. */
public final class FxPairHistory {
    private FxPairHistory() {}

    public record Cursor(WorldId world, FxQuotes.Pair pair, LocalDate upper, LocalDate exclusive) {
        public Cursor {
            Objects.requireNonNull(world);
            Objects.requireNonNull(pair);
            FxFacts.dayKey(upper);
            FxFacts.dayKey(exclusive);
            if (exclusive.isAfter(upper)) throw new IllegalArgumentException("Invalid FX cursor");
        }
    }
    /** The exact directed rate is target units per source unit, expressed as a ratio. */
    public record Day(LocalDate date, BigDecimal numerator, BigDecimal denominator, boolean currencyUnavailable) {
        public Day(LocalDate date, BigDecimal numerator, BigDecimal denominator) { this(date, numerator, denominator, false); }
        public boolean missing() { return numerator == null; }
    }
    public record Page(List<Day> days, Cursor next) {
        public Page { days = List.copyOf(days); }

        /** Every date in a page is within the committed publication range. */
        public List<LocalDate> unexpectedMissingDays() {
            return days.stream().filter(day -> day.missing() && !day.currencyUnavailable()).map(Day::date).toList();
        }
    }

    public static Day day(JournalEngine.ReadView reads, FxQuotes.Pair pair, LocalDate date) {
        var publication = FxService.publication(reads, date);
        if (publication == null) return new Day(date, null, null);
        if (!publication.rates().containsKey(pair.source()) || !publication.rates().containsKey(pair.target()))
            return new Day(date, null, null, true);
        return new Day(date, publication.rates().get(pair.source()), publication.rates().get(pair.target()));
    }

    /** Call with one consistent read transaction and a server-derived world identity. */
    public static Page page(JournalEngine.ReadView reads, WorldId world, FxQuotes.Pair pair, Cursor cursor, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("FX history page size");
        if (cursor != null && (!cursor.world().equals(world) || !cursor.pair().equals(pair)))
            throw new IllegalArgumentException("FX cursor scope");
        var latest = FxService.latest(reads);
        if (latest == null) throw new IllegalStateException("FX_UNINITIALIZED");
        var origin = FxService.origin(reads);
        var first = LocalDate.ofEpochDay(origin / FxService.DAY_MILLIS);
        var upper = cursor == null ? latest.day() : cursor.upper();
        if (upper.isAfter(latest.day()) || upper.isBefore(first)
                || (cursor != null && !cursor.exclusive().isAfter(first))) {
            throw new IllegalArgumentException("FX cursor range");
        }
        LocalDate date = cursor == null ? upper : cursor.exclusive().minusDays(1);
        var days = new ArrayList<Day>();
        while (!date.isBefore(first) && days.size() < limit) {
            days.add(day(reads, pair, date));
            date = date.minusDays(1);
        }
        Cursor next = date.isBefore(first) ? null : new Cursor(world, pair, upper, date.plusDays(1));
        return new Page(days, next);
    }
}
