package ca.emilybelnavis.mcmods.economy.banking;

import ca.emilybelnavis.mcmods.economy.api.Transaction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Persistent ledger owned by the server overworld, shared across dimensions. */
public final class BankData extends SavedData {
    private static final String DATA_NAME = "emily_economy_bank";
    private final Ledger ledger = new Ledger();
    private MinecraftServer server;
    private ca.emilybelnavis.mcmods.economy.BankingConfig.Currency currency;
    public ca.emilybelnavis.mcmods.economy.BankingConfig.Currency currency() { checkThread(); return currency; }

    private BankData() {}

    public static BankData get(MinecraftServer server) {
        Objects.requireNonNull(server);
        if (!server.isSameThread()) throw new IllegalStateException("Economy access requires the server thread");
        var factory = new SavedData.Factory<>(BankData::new, BankData::load, null);
        BankData data = server.overworld().getDataStorage().computeIfAbsent(factory, DATA_NAME);
        data.server = server;
        if (data.currency == null) {
            data.currency = ca.emilybelnavis.mcmods.economy.BankingConfig.Currency.CAD;
            data.setDirty();
        }
        return data;
    }
    /** Immutable legacy migration snapshots, captured only on the server thread. */
    public Map<UUID, Long> exportBalances() { checkThread(); return Map.copyOf(ledger.balances); }
    public Map<UUID, List<Transaction>> exportHistory() {
        checkThread();
        Map<UUID, List<Transaction>> result = new HashMap<>();
        ledger.histories.forEach((owner, entries) -> result.put(owner, List.copyOf(entries)));
        return Map.copyOf(result);
    }
    private void checkThread() {
        if (server == null || !server.isSameThread()) throw new IllegalStateException("Economy access requires the server thread");
    }
    public long balance(UUID account) { checkThread(); return ledger.balance(account); }
    public List<Transaction> history(UUID account) { checkThread(); return ledger.history(account); }
    public void credit(UUID account, long amount, String memo) {
        checkThread(); ledger.credit(account, amount, memo); setDirty();
    }
    public void debit(UUID account, long amount, String memo) {
        checkThread(); ledger.debit(account, amount, memo); setDirty();
    }
    public void transfer(UUID from, UUID to, long amount, String memo) {
        checkThread(); ledger.transfer(from, to, amount, memo); setDirty();
    }

    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        ListTag accounts = new ListTag();
        for (var account : ledger.balances.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Account", account.getKey());
            tag.putLong("Balance", account.getValue());
            ListTag history = new ListTag();
            for (Transaction entry : ledger.history(account.getKey())) {
                CompoundTag item = new CompoundTag();
                item.putUUID("Id", entry.id());
                item.putLong("Time", entry.timestampMillis());
                item.putString("Type", entry.type().name());
                item.putLong("Amount", entry.amount());
                item.putLong("Balance", entry.balance());
                entry.counterparty().ifPresent(id -> item.putUUID("Counterparty", id));
                item.putString("Memo", entry.memo());
                history.add(item);
            }
            tag.put("History", history);
            accounts.add(tag);
        }
        root.putInt("Version", 1);
        if (currency != null) root.putString("Currency", currency.name());
        root.put("Accounts", accounts);
        return root;
    }

    private static BankData load(CompoundTag root, HolderLookup.Provider registries) {
        BankData data = new BankData();
        if (root.contains("Currency")) data.currency = ca.emilybelnavis.mcmods.economy.BankingConfig.Currency.valueOf(root.getString("Currency"));
        if (root.getInt("Version") != 1) throw new IllegalStateException("Unsupported economy data version");
        ListTag accounts = root.getList("Accounts", Tag.TAG_COMPOUND);
        for (int i = 0; i < accounts.size(); i++) {
            CompoundTag tag = accounts.getCompound(i);
            UUID account = tag.getUUID("Account");
            long balance = tag.getLong("Balance");
            if (balance < 0 || data.ledger.balances.containsKey(account)) {
                throw new IllegalStateException("Invalid or duplicate persisted economy account");
            }
            data.ledger.balances.put(account, balance);
            ListTag history = tag.getList("History", Tag.TAG_COMPOUND);
            for (int j = Math.max(0, history.size() - Ledger.HISTORY_LIMIT); j < history.size(); j++) {
                CompoundTag item = history.getCompound(j);
                String memo = item.getString("Memo");
                if (memo.length() > 256) throw new IllegalStateException("Invalid persisted economy memo");
                Transaction entry = new Transaction(item.getUUID("Id"), item.getLong("Time"),
                        Transaction.Type.valueOf(item.getString("Type")), item.getLong("Amount"),
                        item.getLong("Balance"), item.hasUUID("Counterparty")
                        ? Optional.of(item.getUUID("Counterparty")) : Optional.empty(), memo);
                data.ledger.append(account, entry);
            }
        }
        return data;
    }
}
