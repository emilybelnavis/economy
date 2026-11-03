package dev.emily.economy.fx;

import dev.emily.economy.api.Money;
import dev.emily.economy.api.MoneyFormatter;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Original accounting amount with optional separate, immutable display provenance. */
public record HistoricalMoney(Money original, FrozenDisplay frozen) {
    public HistoricalMoney {
        Objects.requireNonNull(original);
    }

    public record FrozenDisplay(Money converted, LocalDate rateDay, UUID publicationId) {
        public FrozenDisplay {
            Objects.requireNonNull(converted);
            Objects.requireNonNull(rateDay);
            Objects.requireNonNull(publicationId);
        }
    }

    public String format(Locale locale) {
        String amount = MoneyFormatter.format(original, locale);
        return frozen == null ? amount : amount + " (frozen display "
                + MoneyFormatter.format(frozen.converted(), locale) + ", rate day " + frozen.rateDay() + ")";
    }
}
