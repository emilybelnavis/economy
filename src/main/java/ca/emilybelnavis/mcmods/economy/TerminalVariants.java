package ca.emilybelnavis.mcmods.economy;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;

/** Fixed registered colours preserve the original teal IDs and shop block-entity data. */
public final class TerminalVariants {
    public static final Map<DyeColor, DeferredBlock<Block>> ATMS = new EnumMap<>(DyeColor.class);
    public static final Map<DyeColor, DeferredBlock<Block>> REGISTERS = new EnumMap<>(DyeColor.class);
    public static final DeferredBlock<Block> BANK_TELLER = Economy.BLOCKS.register("bank_teller_station",
        () -> new WorkstationBlock(true, BlockBehaviour.Properties.of().strength(3).noOcclusion()));
    public static final DeferredBlock<Block> STOCK_COMPUTER = Economy.BLOCKS.register("stock_trading_computer",
        () -> new WorkstationBlock(false, BlockBehaviour.Properties.of().strength(2.5f).noOcclusion()));
    private static boolean initialized;
    private TerminalVariants() {}
    public static void init() {
        if (initialized) return;
        for (DyeColor colour : DyeColor.values()) {
            String name=colour.getName();
            DeferredBlock<Block> atm=Economy.BLOCKS.register(name+"_atm", () -> new AtmBlock(BlockBehaviour.Properties.of()
                .strength(3.5f).requiresCorrectToolForDrops().noOcclusion()));
            DeferredBlock<Block> register=Economy.BLOCKS.register(name+"_cash_register", () -> new TerminalBlock(true,
                BlockBehaviour.Properties.of().strength(2.5f).noOcclusion()));
            ATMS.put(colour,atm); REGISTERS.put(colour,register);
            Economy.ITEMS.registerSimpleBlockItem(atm); Economy.ITEMS.registerSimpleBlockItem(register);
        }
        Economy.ITEMS.registerSimpleBlockItem(BANK_TELLER);
        Economy.ITEMS.registerSimpleBlockItem(STOCK_COMPUTER);
        initialized=true;
    }
    public static Block[] registerBlocks() {
        return Stream.concat(Stream.of(Economy.REGISTER.get()), REGISTERS.values().stream().map(holder -> holder.get())).toArray(Block[]::new);
    }
}
