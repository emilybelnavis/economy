package dev.emily.economy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

/** Display-only collectibles, deliberately separate from valued currency. */
public final class CollectibleItems {
    private static final Map<String, DeferredItem<DecorativeItem>> cards = new LinkedHashMap<>();
    private static final Map<String, List<DeferredItem<DecorativeItem>>> series = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<DecorativeItem>> CARDS = Collections.unmodifiableMap(cards);
    public static final Map<String, List<DeferredItem<DecorativeItem>>> SERIES = Collections.unmodifiableMap(series);
    private static boolean initialized;

    private CollectibleItems() {}

    /** Called from the mod constructor before attaching the item register. */
    public static void initAll() {
        if (initialized) return;
        cards.put("summit_credit_card", register("summit_credit_card", 1));
        cards.put("meridian_credit_card", register("meridian_credit_card", 1));
        cards.put("aurora_credit_card", register("aurora_credit_card", 1));
        cards.put("obsidian_credit_card", register("obsidian_credit_card", 1));
        cards.put("harbor_bank_card", register("harbor_bank_card", 1));
        cards.put("meadow_bank_card", register("meadow_bank_card", 1));
        cards.put("tide_bank_card", register("tide_bank_card", 1));
        cards.put("ember_bank_card", register("ember_bank_card", 1));
        registerSeries("eur",
            "eur_coin_1",
            "eur_coin_2",
            "eur_coin_5",
            "eur_coin_10",
            "eur_coin_20",
            "eur_coin_50",
            "eur_coin_100",
            "eur_coin_200",
            "eur_bill_5",
            "eur_bill_10",
            "eur_bill_20",
            "eur_bill_50",
            "eur_bill_100",
            "eur_bill_200");
        registerSeries("gbp",
            "gbp_coin_1",
            "gbp_coin_2",
            "gbp_coin_5",
            "gbp_coin_10",
            "gbp_coin_20",
            "gbp_coin_50",
            "gbp_coin_100",
            "gbp_coin_200",
            "gbp_bill_5",
            "gbp_bill_10",
            "gbp_bill_20",
            "gbp_bill_50");
        registerSeries("aud",
            "aud_coin_5",
            "aud_coin_10",
            "aud_coin_20",
            "aud_coin_50",
            "aud_coin_100",
            "aud_coin_200",
            "aud_bill_5",
            "aud_bill_10",
            "aud_bill_20",
            "aud_bill_50",
            "aud_bill_100");
        registerSeries("nzd",
            "nzd_coin_10",
            "nzd_coin_20",
            "nzd_coin_50",
            "nzd_coin_100",
            "nzd_coin_200",
            "nzd_bill_5",
            "nzd_bill_10",
            "nzd_bill_20",
            "nzd_bill_50",
            "nzd_bill_100");
        registerSeries("cad",
            "cad_coin_5",
            "cad_coin_10",
            "cad_coin_25",
            "cad_coin_50",
            "cad_coin_100",
            "cad_coin_200",
            "cad_bill_5",
            "cad_bill_10",
            "cad_bill_20",
            "cad_bill_50",
            "cad_bill_100",
            "cad_bill_5_horizontal",
            "cad_bill_10_horizontal",
            "cad_bill_20_horizontal",
            "cad_bill_50_horizontal",
            "cad_bill_100_horizontal");
        registerSeries("usd",
            "usd_coin_1",
            "usd_coin_5",
            "usd_coin_10",
            "usd_coin_25",
            "usd_coin_50",
            "usd_coin_100",
            "usd_bill_1",
            "usd_bill_2",
            "usd_bill_5",
            "usd_bill_10",
            "usd_bill_20",
            "usd_bill_50",
            "usd_bill_100");
        registerSeries("hkd",
            "hkd_coin_10",
            "hkd_coin_20",
            "hkd_coin_50",
            "hkd_coin_100",
            "hkd_coin_200",
            "hkd_coin_500",
            "hkd_coin_1000",
            "hkd_bill_10",
            "hkd_bill_20",
            "hkd_bill_50",
            "hkd_bill_100",
            "hkd_bill_500",
            "hkd_bill_1000");
        registerSeries("jpy",
            "jpy_coin_1",
            "jpy_coin_5",
            "jpy_coin_10",
            "jpy_coin_50",
            "jpy_coin_100",
            "jpy_coin_500",
            "jpy_bill_1000",
            "jpy_bill_2000",
            "jpy_bill_5000",
            "jpy_bill_10000");
        registerSeries("cny",
            "cny_coin_10",
            "cny_coin_50",
            "cny_coin_100",
            "cny_bill_1",
            "cny_bill_5",
            "cny_bill_10",
            "cny_bill_20",
            "cny_bill_50",
            "cny_bill_100");
        registerSeries("dkk",
            "dkk_coin_50",
            "dkk_coin_100",
            "dkk_coin_200",
            "dkk_coin_500",
            "dkk_coin_1000",
            "dkk_coin_2000",
            "dkk_bill_50",
            "dkk_bill_100",
            "dkk_bill_200",
            "dkk_bill_500");
        initialized = true;
    }

    private static void registerSeries(String code, String... ids) {
        List<DeferredItem<DecorativeItem>> items = new ArrayList<>();
        for (String id : ids) items.add(register(id, 64));
        series.put(code, List.copyOf(items));
    }

    private static DeferredItem<DecorativeItem> register(String id, int stackSize) {
        return Economy.ITEMS.register(id, () -> new DecorativeItem(new Item.Properties().stacksTo(stackSize)));
    }
}
