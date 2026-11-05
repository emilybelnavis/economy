package dev.emily.economy.client;

import com.mojang.logging.LogUtils;
import dev.emily.economy.Economy;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/** Distribution boundary for banking screens and original visual resources. */
@Mod(value = Economy.MOD_ID, dist = Dist.CLIENT)
public final class EconomyClient {
    public EconomyClient(net.neoforged.bus.api.IEventBus modBus) {
        BankingScreen.initialize(modBus);
        BankingArtModels.initialize(modBus);
        LogUtils.getLogger().info("Economy client entry point loaded");
    }
}
