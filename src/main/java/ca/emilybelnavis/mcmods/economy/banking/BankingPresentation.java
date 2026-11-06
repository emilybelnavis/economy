package ca.emilybelnavis.mcmods.economy.banking;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Removes internal identifiers and protocol details before normal GUI snapshots reach clients. */
final class BankingPresentation {
    private static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,}");
    private BankingPresentation() {}

    static List<String> lines(List<String> source) {
        var result = new ArrayList<String>();
        boolean hideOutcomeCode = false;
        for (String line : source) {
            if (line == null) continue;
            if (line.startsWith("Operation ") || line.startsWith("Original operation:")) {
                hideOutcomeCode = true;
                continue;
            }
            if (hideOutcomeCode && (CODE.matcher(line).matches() || line.matches("[a-z0-9 ]{3,}"))) {
                hideOutcomeCode = false;
                continue;
            }
            hideOutcomeCode = false;
            if (line.startsWith("Transaction ") && UUID.matcher(line.substring("Transaction ".length())).matches()) continue;
            if (line.startsWith("Terminal revision ") || UUID.matcher(line).matches()) continue;
            String clean = line;
            if (clean.startsWith("History for ")) clean = UUID.matcher(clean).replaceFirst("").stripTrailing();
            if (clean.matches("#[0-9]+ .+")) clean = clean.substring(clean.indexOf(' ') + 1);
            if (clean.matches("[1-8]\\. .* — .*") && UUID.matcher(clean).find()) {
                String number = clean.substring(0, clean.indexOf('.') + 1);
                String kind = clean.substring(clean.lastIndexOf('—') + 1).strip();
                clean = number + " " + switch (kind) {
                    case "register-goods-v1" -> "Register purchase";
                    case "withdrawal-cash-v1" -> "Cash withdrawal";
                    case "deposit-original-v1" -> "Deposit return";
                    default -> "Protected items";
                };
            }
            clean = UUID.matcher(clean).replaceAll("account");
            if (!clean.isBlank()) result.add(clean);
        }
        return List.copyOf(result);
    }

    static String status(String source) {
        if (source == null || source.isBlank()) return "Ready";
        if (source.contains("retry preserves operation")) return "Outcome needs checking. Use Retry.";
        if (UUID.matcher(source).find()) return "Request details are available when GUI debugging is enabled.";
        if (CODE.matcher(source).matches()) {
            String words = source.replace('_', ' ').toLowerCase(Locale.ROOT);
            return Character.toUpperCase(words.charAt(0)) + words.substring(1) + ".";
        }
        return source;
    }
}
