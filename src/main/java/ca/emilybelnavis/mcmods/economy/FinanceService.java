package ca.emilybelnavis.mcmods.economy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import ca.emilybelnavis.mcmods.economy.api.*;
import ca.emilybelnavis.mcmods.economy.banking.BankData;
import ca.emilybelnavis.mcmods.economy.storage.FinanceDatabase;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

/** One asynchronous SQLite store per running server; game state is captured on its main thread. */
public final class FinanceService implements EconomyApi {
    private static final Map<MinecraftServer, FinanceService> SERVICES = new IdentityHashMap<>();
    public final FinanceDatabase database;
    private final CompletableFuture<Void> ready;
    private String rateDate;
    private boolean rateUpdatePending;
    private long retryRateAt;
    private FinanceService(MinecraftServer server) {
        BankData legacy = BankData.get(server);
        var balances = legacy.exportBalances();
        String legacyCurrency = legacy.currency().name();
        Map<UUID, List<FinanceDatabase.LegacyEntry>> histories = new HashMap<>();
        legacy.exportHistory().forEach((owner, entries) -> histories.put(owner, entries.stream().map(t ->
            new FinanceDatabase.LegacyEntry(t.id().toString(), t.timestampMillis(), t.type().name(), t.amount(), t.balance(), t.memo())).toList()));
        Map<String, String> configuredRates = FinanceDefinitions.rates();
        database = new FinanceDatabase(server.getWorldPath(LevelResource.ROOT).resolve("economy/economy.sqlite"),
            BankingConfig.CURRENCY.get().name(), FinanceDefinitions.scales(), configuredRates, FinanceDefinitions.stocks());
        rateDate = LocalDate.now(ZoneOffset.UTC).toString();
        String initialDate = rateDate;
        ready = database.ready().thenCompose(v -> database.importLegacy(balances, histories, legacyCurrency))
            .thenCompose(v -> database.updateRates(configuredRates, initialDate));
        ready.whenComplete((ignored, failure) -> {
            if (failure != null) org.slf4j.LoggerFactory.getLogger(FinanceService.class).error("Economy database initialization failed", failure);
        });
    }
    public static FinanceService get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Finance service lookup requires the server thread");
        return SERVICES.computeIfAbsent(server, FinanceService::new);
    }
    public CompletableFuture<Void> ready() { return ready; }
    public static void onStarted(MinecraftServer server) { get(server); }
    public static void onStopped(MinecraftServer server) {
        RegisterEntity.clearReservations();
        FinanceService service = SERVICES.remove(server);
        if (service != null) service.database.close();
    }
    public static void onLogout(ServerPlayer player) {
        FinanceService service = SERVICES.get(player.server);
        if (service != null) service.ready.thenCompose(v -> service.database.evict(player.getUUID()));
    }
    public static void tick(MinecraftServer server) {
        FinanceService service = SERVICES.get(server);
        if (service == null) return;
        String date = LocalDate.now(ZoneOffset.UTC).toString();
        if (!date.equals(service.rateDate) && !service.rateUpdatePending && server.getTickCount() >= service.retryRateAt) {
            service.rateUpdatePending = true;
            Map<String, String> configuredRates = FinanceDefinitions.rates();
            service.ready.thenCompose(v -> service.database.updateRates(configuredRates, date)).whenComplete((ignored, failure) -> server.execute(() -> {
                service.rateUpdatePending = false;
                if (failure == null) service.rateDate = date;
                else {
                    service.retryRateAt = server.getTickCount() + 1200;
                    org.slf4j.LoggerFactory.getLogger(FinanceService.class).error("Economy exchange-rate update failed; retrying in one minute", failure);
                }
            }));
        }
    }

    public static <T> void complete(ServerPlayer player, CompletionStage<T> stage, BiConsumer<T, Throwable> callback) {
        stage.whenComplete((value, failure) -> player.server.execute(() -> callback.accept(value, unwrap(failure))));
    }
    public static Throwable unwrap(Throwable failure) {
        while ((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause() != null)
            failure = failure.getCause();
        return failure;
    }
    private CompletableFuture<FinanceDatabase.Account> primary(UUID owner) {
        return ready.thenCompose(v -> database.accounts(owner)).thenApply(accounts -> accounts.stream()
            .filter(FinanceDatabase.Account::primary).findFirst().orElseThrow());
    }
    @Override public CompletableFuture<Long> balance(UUID owner) { return primary(owner).thenApply(FinanceDatabase.Account::balance); }
    @Override public CompletableFuture<Void> transfer(UUID from, UUID to, long amount, String memo) {
        return ready.thenCompose(v -> database.pay(from, to, amount, memo)).thenApply(a -> null);
    }
    @Override public CompletableFuture<Void> credit(UUID owner, long amount, String memo) {
        return primary(owner).thenCompose(a -> database.credit(owner, a.id(), amount, memo, "CREDIT")).thenApply(a -> null);
    }
    @Override public CompletableFuture<Void> debit(UUID owner, long amount, String memo) {
        return primary(owner).thenCompose(a -> database.debit(owner, a.id(), amount, memo, "DEBIT")).thenApply(a -> null);
    }
    @Override public CompletableFuture<List<FinanceDatabase.Entry>> history(UUID owner, long before, int limit) {
        return primary(owner).thenCompose(a -> database.history(owner, a.id(), before, limit));
    }
    @Override public CompletableFuture<List<Transaction>> history(UUID owner) {
        return history(owner, 0, 100).thenApply(entries -> entries.stream().filter(e -> e.delta() != 0).map(e ->
            new Transaction(UUID.nameUUIDFromBytes((e.account() + ":" + e.sequence()).getBytes(StandardCharsets.UTF_8)),
                e.timestamp(), e.delta() > 0 ? Transaction.Type.CREDIT : Transaction.Type.DEBIT,
                Math.abs(e.delta()), e.balance(), Optional.empty(), e.memo())).toList());
    }
}
