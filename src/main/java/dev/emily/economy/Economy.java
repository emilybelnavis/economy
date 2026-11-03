package dev.emily.economy;

import com.mojang.logging.LogUtils;
import dev.emily.economy.foundation.DiagnosticsReport;
import dev.emily.economy.foundation.EconomyCommands;
import dev.emily.economy.foundation.SchedulingMetrics;
import dev.emily.economy.foundation.ServerServices;
import dev.emily.economy.foundation.StorageLifecycle;
import dev.emily.economy.foundation.SchedulingConfig;
import dev.emily.economy.storage.SqliteStorage;
import dev.emily.economy.storage.StorageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import dev.emily.economy.recovery.FoundationRuntime;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Set;

@Mod(Economy.MOD_ID)
public final class Economy {
    public static final String MOD_ID = "economy";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<MinecraftServer, ServerServices> servers = new IdentityHashMap<>();
    private final Set<MinecraftServer> invalidConfiguration = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final SchedulingMetrics.Queue NO_QUEUE = new SchedulingMetrics.Queue(0, 0, 0);
    private static final SchedulingMetrics NO_METRICS = new SchedulingMetrics(NO_QUEUE, NO_QUEUE, NO_QUEUE,
            new StorageLifecycle.Progress("storage", 0, -1, false),
            new StorageLifecycle.EconomicProgress(0, 0, false, false), false, 0);

    private final Map<MinecraftServer,FoundationRuntime> runtimes = new IdentityHashMap<>();
    private final Map<MinecraftServer,dev.emily.economy.recovery.ShutdownErrors> shutdownErrors = new IdentityHashMap<>();
    private final Map<MinecraftServer,Boolean> drained = new IdentityHashMap<>();
    public Economy() {
        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener(this::starting);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::stopping);
        NeoForge.EVENT_BUS.addListener(this::stopped);
        LOGGER.info("Economy foundation loaded");
        try { LOGGER.info("SQLite JDBC driver loaded: {}", SqliteStorage.verifyDriver()); }
        catch (StorageException e) { LOGGER.error("event={} component=sqlite_driver", ServerServices.failureCode(e)); }
    }
    private void commands(RegisterCommandsEvent event) {
        EconomyCommands.register(event.getDispatcher(), (source, view) -> {
            var server = source.getServer();
            var services = servers.get(server);
            if (services != null) return services.diagnostics(view.privileged(), view.detailed());
            return DiagnosticsReport.render(view.privileged(), view.detailed(), ServerServices.State.UNAVAILABLE,
                    NO_METRICS, null, invalidConfiguration.contains(server) ? "CONFIGURATION_INVALID" : null,
                    null, null);
        });
    }

    private void starting(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        if (servers.containsKey(server)) throw new IllegalStateException("Duplicate server lifecycle");
        SchedulingConfig scheduling;
        try {
            scheduling = SchedulingConfig.load(FMLPaths.CONFIGDIR.get().resolve("economy-scheduling.properties"));
        } catch (SchedulingConfig.InvalidConfiguration e) {
            invalidConfiguration.add(server);
            LOGGER.error("event=CONFIGURATION_INVALID reason={}", e.safeReason());
            return;
        }
        var runtime = new FoundationRuntime(FoundationRuntime.Configuration.core(), scheduling.sqliteLockTimeoutMillis());
        servers.put(server, new ServerServices(server.getWorldPath(LevelResource.ROOT), runtime, scheduling));
        runtimes.put(server, runtime);
        LOGGER.info("Economy foundation initializing");
    }
    private void tick(ServerTickEvent.Post event) {
        ServerServices services = servers.get(event.getServer());
        if (services != null) {
            var previous = services.state();
            var previousPhase = services.progress().phase();
            services.tick();
            if (!services.progress().phase().equals(previousPhase)) LOGGER.info("Economy startup progress: {} ({}/{})", DiagnosticsReport.recoveryPhase(services.progress().phase()), services.progress().completed(), services.progress().target());
            if (services.state() != previous) {
                LOGGER.info("Economy foundation state: {}", services.state());
                if (services.failure() != null)
                    LOGGER.error("event={} lifecycle=unavailable", ServerServices.failureCode(services.failure()));
            }
        }
    }
    private void stopping(ServerStoppingEvent event) {
        shutdownErrors.put(event.getServer(),new dev.emily.economy.recovery.ShutdownErrors());
        ServerServices services = servers.remove(event.getServer());
        if (services != null) {
            boolean wasReady = services.state() == ServerServices.State.READY;
            boolean clean = services.stop();
            drained.put(event.getServer(), wasReady && clean);
            LOGGER.info("Economy foundation stopped; drained={}", clean);
            if (!clean) LOGGER.error("event={} lifecycle=shutdown unresolved={}",
                    ServerServices.failureCode(services.failure()), services.metrics().unresolvedAtShutdown());
        }
    }
    private void stopped(ServerStoppedEvent event) {
        invalidConfiguration.remove(event.getServer());
        FoundationRuntime runtime=runtimes.remove(event.getServer());
        ServerServices orphan=servers.remove(event.getServer());
        if(orphan!=null) {orphan.stop();drained.remove(event.getServer());}
        var errors=shutdownErrors.remove(event.getServer());
        boolean saveFailed=errors==null || errors.failed() || event.getServer().isCurrentlySaving();
        if(errors!=null) errors.close();
        boolean clean=Boolean.TRUE.equals(drained.remove(event.getServer()));
        if(runtime==null || !clean || saveFailed) {LOGGER.warn("Complete-world clean shutdown not certified");return;}
        // The JDBC worker has terminated. A single bounded post-save I/O job writes the clean witness.
        var done=new java.util.concurrent.CompletableFuture<Void>();
        Thread worker=new Thread(() -> {
            try {runtime.afterMinecraftStopped();done.complete(null);}
            catch(Exception e){done.completeExceptionally(e);}
        },"economy-post-save");
        worker.setDaemon(true);worker.start();
        try {done.get(30,java.util.concurrent.TimeUnit.SECONDS);LOGGER.info("Economy complete-world clean shutdown recorded");}
        catch(Exception e){worker.interrupt();LOGGER.error("event=POST_SAVE_FAILURE lifecycle=shutdown");}
    }
}
