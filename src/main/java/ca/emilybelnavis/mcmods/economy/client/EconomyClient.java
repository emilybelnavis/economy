package ca.emilybelnavis.mcmods.economy.client;

import ca.emilybelnavis.mcmods.economy.Economy;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Economy.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EconomyClient {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(Economy.BANKING_MENU.get(), BankingScreen::new);
        event.register(Economy.TRADING_MENU.get(), TradingScreen::new);
        event.register(Economy.WORLD_CURRENCY_MENU.get(), WorldCurrencyScreen::new);
    }
}
