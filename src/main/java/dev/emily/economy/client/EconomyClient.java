package dev.emily.economy.client;

import com.mojang.logging.LogUtils;
import dev.emily.economy.Economy;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/** Distribution boundary for future client features; no gameplay screens in the foundation. */
@Mod(value = Economy.MOD_ID, dist = Dist.CLIENT)
public final class EconomyClient {
    public EconomyClient(net.neoforged.bus.api.IEventBus modBus) { dev.emily.economy.client.BankingScreen.initialize(modBus); LogUtils.getLogger().info("Economy client entry point loaded"); }
}
