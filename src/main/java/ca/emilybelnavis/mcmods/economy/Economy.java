package ca.emilybelnavis.mcmods.economy;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.*;

@Mod(Economy.ID)
public final class Economy {
    public static final String ID = "economy";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredBlock<Block> ATM = BLOCKS.register("atm", () -> new AtmBlock( BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredBlock<Block> REGISTER = BLOCKS.register("cash_register", () -> new TerminalBlock(true, BlockBehaviour.Properties.of().strength(2.5f).noOcclusion()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RegisterEntity>> REGISTER_ENTITY = ENTITIES.register("cash_register", () -> BlockEntityType.Builder.of(RegisterEntity::new, TerminalVariants.registerBlocks()).build(null));
    static {
        TerminalVariants.init();
        ITEMS.registerSimpleBlockItem(ATM);
        ITEMS.registerSimpleBlockItem(REGISTER);
    }
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("economy", () -> CreativeModeTab.builder()
        .title(net.minecraft.network.chat.Component.translatable("itemGroup.economy"))
        .icon(() -> new ItemStack(ATM.get()))
        .displayItems((parameters, output) -> {
            output.accept(ATM.get());
            output.accept(REGISTER.get());
            TerminalVariants.ATMS.values().forEach(output::accept);
            TerminalVariants.REGISTERS.values().forEach(output::accept);
            output.accept(TerminalVariants.BANK_TELLER.get());
            output.accept(TerminalVariants.STOCK_COMPUTER.get());
        }).build());
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EUR_TAB = currencyTab("eur", "eur_bill_20", TAB);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GBP_TAB = currencyTab("gbp", "gbp_bill_50", EUR_TAB);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> AUD_TAB = currencyTab("aud", "aud_bill_20", GBP_TAB);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> NZD_TAB = currencyTab("nzd", "nzd_bill_20", AUD_TAB);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CAD_TAB = currencyTab("cad", "cad_bill_20_horizontal", NZD_TAB);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> USD_TAB = currencyTab("usd", "usd_bill_100", CAD_TAB);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> HKD_TAB = currencyTab("hkd", "hkd_bill_20", USD_TAB);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> JPY_TAB = currencyTab("jpy", "jpy_bill_1000", HKD_TAB);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CNY_TAB = currencyTab("cny", "cny_bill_20", JPY_TAB);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DKK_TAB = currencyTab("dkk", "dkk_coin_2000", CNY_TAB);
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CARDS_TAB = TABS.register("cards", () -> CreativeModeTab.builder()
        .title(net.minecraft.network.chat.Component.translatable("itemGroup.economy.cards"))
        .withTabsBefore(DKK_TAB.getKey())
        .icon(() -> new ItemStack(CollectibleItems.CARDS.get("summit_credit_card").get()))
        .displayItems((parameters, output) -> CollectibleItems.CARDS.values().forEach(output::accept))
        .build());

    private static DeferredHolder<CreativeModeTab, CreativeModeTab> currencyTab(String code, String iconId,
            DeferredHolder<CreativeModeTab, CreativeModeTab> previousTab) {
        return TABS.register(code, () -> CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.economy." + code))
            .withTabsBefore(previousTab.getKey())
            .icon(() -> new ItemStack(CollectibleItems.SERIES.get(code).stream()
                .filter(item -> item.getId().getPath().equals(iconId)).findFirst().orElseThrow().get()))
            .displayItems((parameters, output) -> CollectibleItems.SERIES.get(code).forEach(output::accept))
            .build());
    }

    public Economy(IEventBus bus, ModContainer container) {
        CollectibleItems.initAll();
        ITEMS.register(bus); BLOCKS.register(bus); ENTITIES.register(bus); TABS.register(bus);
        NeoForge.EVENT_BUS.addListener(EconomyCommands::register);
    }
}
