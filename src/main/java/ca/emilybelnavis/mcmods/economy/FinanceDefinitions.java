package ca.emilybelnavis.mcmods.economy;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import ca.emilybelnavis.mcmods.economy.storage.FinanceDatabase;

/** Immutable JSON catalogs, loaded once on the mod loading thread. */
public final class FinanceDefinitions {
    private static final JsonObject CURRENCIES = load("currencies");
    private static final JsonObject STOCKS = load("stocks");
    private static JsonObject load(String name) {
        String path = "/data/economy/finance/"+name+".json";
        try (var in = FinanceDefinitions.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing finance definitions: "+path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) { throw new IllegalStateException("Cannot load "+path, e); }
    }
    public static void initialize() { scales(); stocks(); }
    public static Map<String,Integer> scales() {
        var result = new LinkedHashMap<String,Integer>();
        for (var element : CURRENCIES.getAsJsonArray("currencies")) {
            var c = element.getAsJsonObject();
            int scale = c.get("scale").getAsInt();
            if (scale != 1 && scale != 100) throw new IllegalStateException("Unsupported currency minor-unit scale");
            result.put(c.get("code").getAsString(), scale == 1 ? 0 : 2);
        }
        return Map.copyOf(result);
    }
    public static Map<String,String> rates() { return BankingConfig.rates(); }
    public static JsonArray cashItems() { return CURRENCIES.getAsJsonArray("cash").deepCopy(); }
    public static List<FinanceDatabase.StockDefinition> stocks() {
        var result = new ArrayList<FinanceDatabase.StockDefinition>();
        for (var e : STOCKS.getAsJsonArray("stocks")) {
            var s=e.getAsJsonObject();
            if (s.get("status").getAsString().equals("priced")) result.add(new FinanceDatabase.StockDefinition(s.get("id").getAsString(),s.get("name").getAsString(),s.get("baseCadMinor").getAsLong()));
        }
        return List.copyOf(result);
    }
    private FinanceDefinitions() {}
}
