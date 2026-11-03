package ca.emilybelnavis.mcmods.economy.api;

import ca.emilybelnavis.mcmods.economy.FinanceService;
import ca.emilybelnavis.mcmods.economy.storage.FinanceDatabase;
import net.minecraft.server.MinecraftServer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Asynchronous integral-money API. Never block the server tick waiting for a future.
 * Futures complete on the storage worker; schedule world/player changes on the server thread.
 */
public interface EconomyApi {
    static EconomyApi get(MinecraftServer server) { return FinanceService.get(server); }
    CompletableFuture<Long> balance(UUID owner);
    CompletableFuture<Void> transfer(UUID from, UUID to, long amount, String memo);
    /** Caller must authorize issuance. */
    CompletableFuture<Void> credit(UUID owner, long amount, String memo);
    /** Caller must authorize the charge. */
    CompletableFuture<Void> debit(UUID owner, long amount, String memo);
    /** Latest entries, newest first. */
    CompletableFuture<List<Transaction>> history(UUID owner);
    /** Stable sequence cursor, exclusive; zero starts at the newest entry. */
    CompletableFuture<List<FinanceDatabase.Entry>> history(UUID owner, long beforeSequence, int limit);
}
