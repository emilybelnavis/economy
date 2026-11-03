package ca.emilybelnavis.mcmods.economy;

import java.math.BigDecimal;

/** One formatting policy for terminal, account and market amounts. */
public final class Money {
    public static String format(long minor, String currency) {
        boolean yen = currency.equals("JPY");
        String amount = BigDecimal.valueOf(minor, yen ? 0 : 2).setScale(yen ? 0 : 2).toPlainString();
        return switch (currency) {
            case "CAD", "AUD", "NZD", "USD", "HKD" -> "$" + amount + " " + currency;
            case "GBP" -> "£" + amount;
            case "EUR" -> "€" + amount;
            case "DKK" -> amount + " kr";
            case "CNY" -> "CN¥" + amount;
            case "JPY" -> "JP¥" + amount;
            default -> throw new IllegalArgumentException("Unknown currency: " + currency);
        };
    }
    private Money() {}
}
