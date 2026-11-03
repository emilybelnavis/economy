package dev.emily.economy.fx;

import dev.emily.economy.api.CurrencyConversion;
import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.journal.Journal;
import dev.emily.economy.recovery.RecordCatalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Version one journal payloads. The first ten characters are the indexed UTC date. */
public final class FxFacts {
    public static final String PUBLICATION = "economy:fx-publication";
    public static final String POLICY = "economy:fx-policy";
    public static final String DAY_KEY = "substr(payload,1,10)";

    private FxFacts() {}

    public static RecordCatalog register(RecordCatalog catalog) {
        return catalog.with(new RecordCatalog.Key(PUBLICATION, 1), Publication::decode)
                .with(new RecordCatalog.Key(POLICY, 1), Policy::decode);
    }

    public record Publication(LocalDate day, EconomyCurrency reference, UUID updateId, UUID policyId,
                              Map<EconomyCurrency, BigDecimal> rates) {
        public Publication {
            dayKey(day);
            Objects.requireNonNull(updateId);
            Objects.requireNonNull(policyId);
            rates = new CurrencyConversion.Rates(reference, rates).perMajorUnit();
            if (rates.size() != EconomyCurrency.all().size() || rates.values().stream().anyMatch(rate ->
                    rate.scale() != 12 || rate.compareTo(BigDecimal.valueOf(1_000_000)) > 0)) {
                throw new IllegalArgumentException("Incomplete or unrepresentable FX publication");
            }
        }

        public CurrencyConversion.Rates conversionRates() {
            return new CurrencyConversion.Rates(reference, rates);
        }

        String encode() {
            StringBuilder value = new StringBuilder(dayKey(day)).append('|').append(reference.code())
                    .append('|').append(updateId).append('|').append(policyId);
            for (var currency : EconomyCurrency.all()) value.append('|').append(rates.get(currency).toPlainString());
            return value.toString();
        }

        Journal.Record record() { return new Journal.Record(PUBLICATION, 1, encode()); }

        public static Publication decode(String payload) {
            String[] parts = payload.split("\\|", -1);
            if (parts.length != 4 + EconomyCurrency.all().size()) throw new IllegalArgumentException("FX publication fields");
            var rates = new EnumMap<EconomyCurrency, BigDecimal>(EconomyCurrency.class);
            int index = 4;
            for (var currency : EconomyCurrency.all()) rates.put(currency, new BigDecimal(parts[index++]));
            var publication = new Publication(LocalDate.parse(parts[0]), EconomyCurrency.fromCode(parts[1]),
                    UUID.fromString(parts[2]), UUID.fromString(parts[3]), rates);
            if (!publication.encode().equals(payload)) throw new IllegalArgumentException("Noncanonical FX publication");
            return publication;
        }
    }

    public record Policy(LocalDate effectiveDay, UUID id, EconomyCurrency reference,
                         Map<EconomyCurrency, FxMath.Settings> settings) {
        public Policy {
            dayKey(effectiveDay);
            Objects.requireNonNull(id);
            Objects.requireNonNull(reference);
            settings = Map.copyOf(settings);
            if (settings.size() != EconomyCurrency.all().size()
                    || settings.get(reference).baseline().compareTo(BigDecimal.ONE) != 0) {
                throw new IllegalArgumentException("Incomplete FX policy or invalid reference");
            }
        }

        String encode() {
            StringBuilder value = new StringBuilder(dayKey(effectiveDay)).append('|').append(id)
                    .append('|').append(reference.code());
            for (var currency : EconomyCurrency.all()) {
                var setting = settings.get(currency);
                value.append('|').append(setting.baseline().toPlainString()).append(',')
                        .append(setting.variationPpm()).append(',').append(setting.pullPpm())
                        .append(',').append(setting.boundsPpm());
            }
            return value.toString();
        }

        Journal.Record record() { return new Journal.Record(POLICY, 1, encode()); }

        public static Policy decode(String payload) {
            String[] parts = payload.split("\\|", -1);
            if (parts.length != 3 + EconomyCurrency.all().size()) throw new IllegalArgumentException("FX policy fields");
            var settings = new EnumMap<EconomyCurrency, FxMath.Settings>(EconomyCurrency.class);
            int index = 3;
            for (var currency : EconomyCurrency.all()) {
                String[] fields = parts[index++].split(",", -1);
                if (fields.length != 4) throw new IllegalArgumentException("FX settings fields");
                settings.put(currency, new FxMath.Settings(new BigDecimal(fields[0]), Integer.parseInt(fields[1]),
                        Integer.parseInt(fields[2]), Integer.parseInt(fields[3])));
            }
            var policy = new Policy(LocalDate.parse(parts[0]), UUID.fromString(parts[1]),
                    EconomyCurrency.fromCode(parts[2]), settings);
            if (!policy.encode().equals(payload)) throw new IllegalArgumentException("Noncanonical FX policy");
            return policy;
        }
    }

    static String dayKey(LocalDate day) {
        String key = day.toString();
        if (day.getYear() < 1970 || key.length() != 10) throw new IllegalArgumentException("Unsupported FX UTC day");
        return key;
    }
}
