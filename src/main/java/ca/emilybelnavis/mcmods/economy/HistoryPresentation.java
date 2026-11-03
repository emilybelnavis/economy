package ca.emilybelnavis.mcmods.economy;

import java.math.*;
import java.util.*;
import java.util.regex.*;
import ca.emilybelnavis.mcmods.economy.storage.FinanceDatabase;

/** Human-facing memo, separate from the full immutable financial audit record. */
public final class HistoryPresentation {
    public static String utcDate(long timestamp) {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd, HH:mm:ss").withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.ofEpochMilli(timestamp));
    }
    private static final Pattern NATIVE = Pattern.compile("native=(\\d+) ([A-Z]{3}) minor");
    private static final Pattern RATE = Pattern.compile("([A-Z]{3}) CAD/unit=([0-9.Ee+-]+)");
    public static String memo(FinanceDatabase.Entry entry, String accountCurrency) {
        if (entry.operation().equals("CASH_DEPOSIT")) {
            Map<String,BigDecimal> rates = new LinkedHashMap<>();
            var rate = RATE.matcher(entry.memo());
            while (rate.find()) rates.put(rate.group(1), new BigDecimal(rate.group(2)));
            var targetMatch = Pattern.compile("Cash deposit credit=\\d+ ([A-Z]{3}) minor").matcher(entry.memo());
            String target = targetMatch.find() ? targetMatch.group(1) : accountCurrency;
            var details = new ArrayList<String>();
            var nativeCash = NATIVE.matcher(entry.memo());
            while (nativeCash.find()) {
                String source = nativeCash.group(2);
                if (source.equals(target)) continue;
                BigDecimal from = rates.get(source), to = rates.get(target);
                if (from != null && to != null) details.add(source+" @ "+from.divide(to,12,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
            }
            return "Cash Deposit" + (details.isEmpty() ? "" : " — " + String.join(", ",details));
        }
        return switch(entry.operation()) {
            case "CASH_WITHDRAWAL" -> "Cash Withdrawal";
            case "WORLD_CURRENCY_OUT", "WORLD_CURRENCY_IN" -> "World Currency Conversion";
            case "WORLD_CURRENCY_MERGE_IN", "WORLD_CURRENCY_MERGE_OUT" -> "Bank Account Merge";
            default -> {
                String text = entry.memo(); int audit = text.indexOf(" [");
                if (audit >= 0) text = text.substring(0,audit);
                yield text.length() > 180 ? text.substring(0,180) : text;
            }
        };
    }
    private HistoryPresentation() {}
}
