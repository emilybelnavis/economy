package dev.emily.economy.probe;

import dev.emily.economy.cash.CashCatalog;
import dev.emily.economy.cash.CashStackIdentity;
import dev.emily.economy.api.Identifiers.WorldId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.authlib.GameProfile;
import java.util.UUID;

/** Exercises actual registered items and vanilla stack/component behavior in the server runtime. */
public final class CashCatalogProbe {
    private CashCatalogProbe() {}

    public static void verify(MinecraftServer server) {
        require(CashCatalog.all().size() == 118, "118 cash identities");
        require(CashCatalog.cards().size() == 8, "eight card identities");
        require(CashCatalog.tabIds().size() == 12, "twelve creative tab identities");
        net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(
                server.getWorldData().enabledFeatures(), true, server.registryAccess());
        for (var id : CashCatalog.tabIds()) {
            var key = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("economy", id);
            require(BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(key), "registered creative tab");
            require(!BuiltInRegistries.CREATIVE_MODE_TAB.get(key).getDisplayItems().isEmpty(), "populated creative tab");
        }
        for (var denomination : CashCatalog.all()) {
            var item = denomination.item().get();
            require(BuiltInRegistries.ITEM.getKey(item).toString().equals("economy:" + denomination.id()),
                    "registered cash identity");
            require(denomination.minorUnits() > 0, "positive denomination");
        }
        for (var entry : CashCatalog.cards().entrySet()) {
            var card = new ItemStack(entry.getValue().get());
            require(card.getMaxStackSize() == 1, "single-stack card");
            require(CashCatalog.hasBankingCard(card), "generic banking access");
        }
        require(!CashCatalog.hasBankingCard(new ItemStack(Items.PAPER)), "unrelated item has no banking access");
        var world = new WorldId(UUID.randomUUID());
        var lot = UUID.randomUUID();
        var raw = new ItemStack(CashCatalog.all().getFirst().item().get(), 16);
        require(CashStackIdentity.mark(raw, world).isEmpty(), "unmarked development cash collectible only");
        var marked = CashStackIdentity.marked(raw, world, lot);
        var split = marked.split(5);
        require(marked.getCount() == 11 && split.getCount() == 5, "vanilla split preserves quantity");
        require(CashStackIdentity.mark(marked, world).equals(CashStackIdentity.mark(split, world)), "split lot identity");
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "CashFixture"),
                ClientInformation.createDefault());
        player.getInventory().setItem(0, marked);
        require(player.getInventory().add(split), "vanilla inventory merge");
        require(player.getInventory().getItem(0).getCount() == 16 && split.isEmpty(), "merged quantity conservation");
        var otherLot = CashStackIdentity.marked(raw, world, UUID.randomUUID());
        require(!ItemStack.isSameItemSameComponents(marked, otherLot), "different lots cannot merge");
        require(CashStackIdentity.mark(marked, new WorldId(UUID.randomUUID())).isEmpty(), "world-bound identity");
        var saved = marked.save(server.registryAccess());
        var restored = ItemStack.parse(server.registryAccess(), saved).orElseThrow();
        require(restored.getCount() == 16 && CashStackIdentity.mark(restored, world).orElseThrow().lot().equals(lot),
                "persistent cash component serialization");
        System.out.println("CASH_CATALOG_PASS identities cards access vanilla split merge world binding persistent components");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Cash fixture failed: " + message);
    }
}
