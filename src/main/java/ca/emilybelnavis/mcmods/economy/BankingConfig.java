package ca.emilybelnavis.mcmods.economy;

import java.math.*;
import java.util.*;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Offline, server-owned rates: CAD per one major unit of each currency. */
public final class BankingConfig {
    public enum Currency { CAD, EUR, GBP, AUD, NZD, USD, HKD, JPY, CNY, DKK;
        public int scale() { return this == JPY ? 1 : 100; }
    }
    public static final ModConfigSpec SPEC;
    private static final Map<Currency, ModConfigSpec.ConfigValue<String>> RATES = new EnumMap<>(Currency.class);
    static {
        var b = new ModConfigSpec.Builder();
        b.comment("CAD per major currency unit. Snapshot: 2026-10-30. Bank of Canada daily averages; DKK = 100/478.89 from Danmarks Nationalbank.",
            "Cross rate A to B = rate[A] / rate[B]. Restart server after editing. Deposits round down once per source currency group to account minor units.")
            .push("exchange_rates");
        String[] rates = {"1", "1.5582", "1.8699", "0.9813", "0.7865", "1.3960", "0.1779", "0.008870", "0.2098", "0.20881622293"};
        int i = 0;
        for (Currency c : Currency.values()) {
            String rate = rates[i++];
            final boolean cad = c == Currency.CAD;
            RATES.put(c, b.define(c.name(), rate, v -> {
                try { var n = new BigDecimal(v.toString()); return (!cad || n.compareTo(BigDecimal.ONE) == 0) && n.signum() > 0 && n.compareTo(new BigDecimal("1000000")) <= 0 && n.scale() <= 12; }
                catch (RuntimeException e) { return false; }
            }));
        }
        b.pop(); SPEC = b.build();
    }
    public static Map<String,String> rates() {
        var result = new LinkedHashMap<String,String>();
        RATES.forEach((c,r) -> result.put(c.name(),r.get()));
        return Map.copyOf(result);
    }
    public static long convert(long minor, Currency from, Currency to) {
        return convert(minor, from.scale(), to.scale(), new BigDecimal(RATES.get(from).get()), new BigDecimal(RATES.get(to).get()));
    }
    public static long convert(long minor, int fromScale, int toScale, BigDecimal fromRate, BigDecimal toRate) {
        if (minor < 0) throw new IllegalArgumentException("Negative money");
        return BigDecimal.valueOf(minor).multiply(fromRate).multiply(BigDecimal.valueOf(toScale))
            .divide(toRate.multiply(BigDecimal.valueOf(fromScale)), 0, RoundingMode.DOWN).longValueExact();
    }
    public static String format(long minor, Currency c) { return Money.format(minor,c.name()); }
    private BankingConfig() {}
}
