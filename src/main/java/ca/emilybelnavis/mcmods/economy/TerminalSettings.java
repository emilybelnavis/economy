package ca.emilybelnavis.mcmods.economy;

import java.util.*;
import ca.emilybelnavis.mcmods.economy.BankingConfig.Currency;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-owned machine metadata, stored separately from the monetary ledger. */
public final class TerminalSettings extends SavedData {
    public enum Mode { DEPOSIT, WITHDRAW, BOTH }

    public record Settings(UUID owner, Mode mode, Set<Currency> accepted, Set<String> dispensedItems) {
        public Settings {
            Objects.requireNonNull(mode);
            accepted = Set.copyOf(accepted);
            dispensedItems = Set.copyOf(dispensedItems);
        }
        public boolean accepts(CashCatalog.Cash cash) { return cash != null && accepted.contains(cash.currency()); }
        public boolean dispenses(CashCatalog.Cash cash) {
            return cash != null && dispensedItems.contains(BuiltInRegistries.ITEM.getKey(cash.item()).toString());
        }
        public boolean permitsDeposit() { return mode != Mode.WITHDRAW; }
        public boolean permitsWithdraw() { return mode != Mode.DEPOSIT; }
    }

    private record Key(String dimension, long anchor) {}
    private final Map<Key, Settings> terminals = new HashMap<>();
    private TerminalSettings() {}

    private static TerminalSettings data(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Terminal access requires the server thread");
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(TerminalSettings::new, TerminalSettings::load, null), "emily_economy_terminals");
    }
    private static boolean terminal(BlockState state) {
        return state.getBlock() instanceof AtmBlock
            || state.getBlock() instanceof WorkstationBlock workstation && workstation.isTeller();
    }
    public static BlockPos anchor(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!terminal(state)) throw new IllegalArgumentException("No banking terminal at position");
        return WideMachineBlock.anchor(pos, state);
    }
    private static Key key(ServerLevel level, BlockPos anchor) {
        return new Key(level.dimension().location().toString(), anchor.asLong());
    }
    private static Settings defaults(UUID owner, boolean teller) {
        Set<String> items = new HashSet<>();
        // All art variants remain selectable; ATMs can dispense bills only.
        for (var item : CollectibleItems.SERIES.get(BankingConfig.CURRENCY.get().name().toLowerCase(Locale.ROOT))) {
            var cash = CashCatalog.find(new net.minecraft.world.item.ItemStack(item.get()));
            if (cash != null && (teller || !cash.coin())) items.add(BuiltInRegistries.ITEM.getKey(cash.item()).toString());
        }
        return new Settings(owner, Mode.BOTH, EnumSet.allOf(Currency.class), items);
    }
    public static Settings get(ServerLevel level, BlockPos pos) {
        BlockPos root = anchor(level, pos);
        BlockState state = level.getBlockState(pos);
        TerminalSettings data = data(level);
        Key key = key(level, root);
        Settings settings = data.terminals.get(key);
        if (settings == null) {
            settings = defaults(null, state.getBlock() instanceof WorkstationBlock);
            data.terminals.put(key, settings);
            data.setDirty();
        }
        return settings;
    }
    public static boolean canConfigure(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!terminal(level.getBlockState(pos))) return false;
        UUID owner = get(level, pos).owner();
        return player.hasPermissions(2) || player.getUUID().equals(owner);
    }
    public static boolean configure(ServerPlayer player, BlockPos pos, Settings settings) {
        if (!canConfigure(player, pos)) return false;
        ServerLevel level = player.serverLevel();
        Settings existing = get(level, pos);
        // Configuration cannot transfer ownership or enable coin dispensing on an ATM.
        boolean teller = level.getBlockState(pos).getBlock() instanceof WorkstationBlock;
        Set<String> validItems = new HashSet<>();
        for (Currency currency : Currency.values()) {
            for (var item : CollectibleItems.SERIES.get(currency.name().toLowerCase(Locale.ROOT))) {
                var cash = CashCatalog.find(new net.minecraft.world.item.ItemStack(item.get()));
                String id = BuiltInRegistries.ITEM.getKey(item.get()).toString();
                if (cash != null && (teller || !cash.coin()) && settings.dispensedItems().contains(id)) validItems.add(id);
            }
        }
        TerminalSettings data = data(level);
        data.terminals.put(key(level, anchor(level, pos)), new Settings(existing.owner(), settings.mode(), settings.accepted(), validItems));
        data.setDirty();
        return true;
    }
    static void placed(ServerLevel level, BlockPos pos, UUID owner) {
        BlockState state = level.getBlockState(pos);
        if (!terminal(state)) return;
        TerminalSettings data = data(level);
        data.terminals.put(key(level, WideMachineBlock.anchor(pos, state)), defaults(owner, state.getBlock() instanceof WorkstationBlock));
        data.setDirty();
    }
    static void removed(ServerLevel level, BlockPos anchor) {
        TerminalSettings data = data(level);
        if (data.terminals.remove(key(level, anchor)) != null) data.setDirty();
    }
    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putInt("Version", 1);
        ListTag entries = new ListTag();
        terminals.forEach((key, settings) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Dimension", key.dimension());
            entry.putLong("Anchor", key.anchor());
            if (settings.owner() != null) entry.putUUID("Owner", settings.owner());
            entry.putString("Mode", settings.mode().name());
            ListTag accepted = new ListTag();
            settings.accepted().stream().sorted().forEach(c -> accepted.add(StringTag.valueOf(c.name())));
            entry.put("Accepted", accepted);
            ListTag dispensed = new ListTag();
            settings.dispensedItems().stream().sorted().forEach(id -> dispensed.add(StringTag.valueOf(id)));
            entry.put("Dispensed", dispensed);
            entries.add(entry);
        });
        root.put("Terminals", entries);
        return root;
    }
    private static TerminalSettings load(CompoundTag root, HolderLookup.Provider registries) {
        if (root.getInt("Version") != 1) throw new IllegalStateException("Unsupported terminal metadata version");
        TerminalSettings data = new TerminalSettings();
        ListTag entries = root.getList("Terminals", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            Set<Currency> accepted = EnumSet.noneOf(Currency.class);
            ListTag currencies = entry.getList("Accepted", Tag.TAG_STRING);
            for (int j = 0; j < currencies.size(); j++) accepted.add(Currency.valueOf(currencies.getString(j)));
            Set<String> dispensed = new HashSet<>();
            ListTag items = entry.getList("Dispensed", Tag.TAG_STRING);
            for (int j = 0; j < items.size(); j++) dispensed.add(items.getString(j));
            data.terminals.put(new Key(entry.getString("Dimension"), entry.getLong("Anchor")),
                new Settings(entry.hasUUID("Owner") ? entry.getUUID("Owner") : null,
                    Mode.valueOf(entry.getString("Mode")), accepted, dispensed));
        }
        return data;
    }
}
