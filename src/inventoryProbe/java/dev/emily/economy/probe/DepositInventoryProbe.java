package dev.emily.economy.probe;

import com.mojang.authlib.GameProfile;
import dev.emily.economy.api.Identifiers.WorldId;
import dev.emily.economy.cash.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.PlayerDataStorage;
import java.util.UUID;

/** Actual server inventory effects, including capacity changes and saved replay markers. */
public final class DepositInventoryProbe {
    private DepositInventoryProbe() {}
    public static void run(MinecraftServer server) throws Exception {
        var world = new WorldId(UUID.randomUUID());
        var profile = new GameProfile(UUID.randomUUID(), "DepositFixture");
        var player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        var input = new SimpleContainer(24);
        var cash = CashStackIdentity.marked(new ItemStack(CashCatalog.all().getFirst().item().get(), 4), world, UUID.randomUUID());
        input.setItem(0, cash);
        for (int i = 1; i < 24; i++) input.setItem(i, cash.copyWithCount(1));
        var originals = DepositInventory.snapshot(player, input, world);
        require(originals.size() == 24, "24 actual input slots");
        var deposit = UUID.randomUUID();
        DepositInventory.reserve(player, input, world, deposit, originals);
        require(input.isEmpty(), "originals moved into saved player custody");
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        player.getInventory().setItem(0, cash.copyWithCount(62));
        var attempt = UUID.randomUUID();
        var result = DepositInventory.returnOnce(player, deposit, 0, attempt, 4);
        require(result.delivered() == 2 && result.remaining() == 2, "partial capacity preserves exact remainder");
        require(DepositInventory.returnOnce(player, deposit, 0, attempt, 4).replay(), "same attempt returns observation");
        require(player.getInventory().getItem(0).getCount() == 64, "replay cannot duplicate cash");
        var field = MinecraftServer.class.getDeclaredField("playerDataStorage");
        field.setAccessible(true);
        var storage = (PlayerDataStorage) field.get(server);
        storage.save(player);
        var restored = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        require(storage.load(restored).isPresent(), "real saved reservation data");
        var replay = DepositInventory.returnOnce(restored, deposit, 0, attempt, 4);
        require(replay.replay() && replay.delivered() == 2 && replay.remaining() == 2, "saved replay and remainder");
        require(restored.getInventory().getItem(0).getCount() == 64, "saved inventory quantity");
        DepositInventory.consumeCommitted(restored, deposit, java.util.List.of(originals.get(1)));
        DepositInventory.consumeCommitted(restored, deposit, java.util.List.of(originals.get(1)));
        boolean consumedDenied = false;
        try { DepositInventory.returnOnce(restored, deposit, 1, UUID.randomUUID(), 1); }
        catch (IllegalStateException expected) { consumedDenied = true; }
        require(consumedDenied, "settled originals cannot be returned again");
        System.out.println("DEPOSIT_INVENTORY_PASS 24 slots original custody partial capacity saved replay without duplication");
    }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException("Deposit inventory fixture failed: " + message);
    }
}
