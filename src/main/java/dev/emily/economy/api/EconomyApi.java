package dev.emily.economy.api;

import dev.emily.economy.banking.BankData;
import net.minecraft.server.MinecraftServer;
import java.util.List;
import java.util.UUID;

/** Server-thread-only economy. Values are integral currency units, never floating point. */
public interface EconomyApi {
    static EconomyApi get(MinecraftServer server) { return BankData.get(server); }
    long balance(UUID account);
    void transfer(UUID from, UUID to, long amount, String memo);
    /** Caller must authorize issuance (for example an administrator or trusted reward system). */
    void credit(UUID account, long amount, String memo);
    /** Caller must authorize the charge before invoking this operation. */
    void debit(UUID account, long amount, String memo);
    /** Immutable snapshot, oldest first, containing at most the latest 100 account entries. */
    List<Transaction> history(UUID account);
}
