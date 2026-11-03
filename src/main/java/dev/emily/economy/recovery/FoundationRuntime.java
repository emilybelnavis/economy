package dev.emily.economy.recovery;

import dev.emily.economy.clock.EconomicClock;
import dev.emily.economy.foundation.SchedulingConfig;
import dev.emily.economy.storage.*;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;

/** Storage recovery -> compatibility/migrations -> projections -> catch-up -> consistent publication. */
public final class FoundationRuntime implements FoundationStore {
    public record Configuration(RecordCatalog records,List<ProjectionManager.Definition> projections,List<EconomicClock.SystemDefinition> systems,Clock wall) {
        public Configuration { Objects.requireNonNull(records); projections=List.copyOf(projections); systems=List.copyOf(systems); Objects.requireNonNull(wall); }
        public static Configuration core() { return new Configuration(RecordCatalog.core(),List.of(),List.of(),Clock.systemUTC()); }
    }
    private final Configuration configuration;
    private final SqliteStorage store;
    private ProjectionManager projections;
    private EconomicClock clock;
    private int phase;
    private boolean ready;
    private boolean economicProjectionsPending;
    private Path world;
    private volatile WorldShutdown.Evidence closedEvidence;
    public FoundationRuntime(Configuration configuration) { this(configuration, SchedulingConfig.defaults().sqliteLockTimeoutMillis()); }
    public FoundationRuntime(Configuration configuration, int sqliteLockTimeoutMillis) {
        this.configuration = configuration;
        this.store = new SqliteStorage(configuration.records(), sqliteLockTimeoutMillis);
    }
    public FoundationRuntime() { this(Configuration.core()); }
    public void initialize(Path world) {
        this.world=world.toAbsolutePath().normalize();store.beginInitialization(world);
        try { WorldShutdown.active(this.world); } catch(java.io.IOException e) { throw new StorageException(StorageException.Kind.CORRUPTION_IO,"Cannot invalidate clean-world marker",e); }
    }
    public Progress startupStep() {
        if(phase==0) {
            var progress=store.advanceInitialization();
            if(!progress.ready()) return progress;
            store.cleanShutdownEligible(false);
            projections=new ProjectionManager(store,configuration.projections());
            clock=new EconomicClock(store,configuration.wall(),configuration.systems());
            phase=1;
        }
        if(phase==1 || phase==3) {
            var progress=projections.step(16);
            if(!progress.ready()) return new Progress("projection:"+progress.name(),progress.applied(),progress.target(),false);
            if(phase==3) {
                long before=store.transaction(tx->Long.parseLong(tx.query("SELECT global_sequence FROM economic_metadata",List.of(),1).getFirst().get("global_sequence")));
                var finalClock=clock.runBatch(16);
                if(!finalClock.ready()) {phase=2;return new Progress("catch-up",finalClock.completedSequence(),finalClock.outstandingIntervals(),false);}
                if(before!=finalClock.completedSequence())return new Progress("projection-final-validation",0,1,false);
                ready=true;store.cleanShutdownEligible(true);phase=4;return new Progress("ready",1,1,true);
            }
            clock.prepare();phase=2;
        }
        if(phase==2) {
            var progress=clock.runBatch(16);
            if(!progress.ready()) return new Progress(progress.waitingForWall()?"waiting-for-wall-clock":"catch-up",progress.completedSequence(),progress.outstandingIntervals(),false);
            phase=3;return new Progress("projection-final-validation",0,1,false);
        }
        return new Progress("ready",1,1,ready);
    }
    @Override
    public boolean schedulesEconomy() {
        return !configuration.systems().isEmpty();
    }

    @Override
    public void requireEconomicCurrent() {
        if (schedulesEconomy() && (!clock.observeCurrentDebt().ready() || economicProjectionsPending)) {
            throw new StorageException(StorageException.Kind.ECONOMIC_CATCH_UP, "Economic catch-up is in progress");
        }
    }

    @Override
    public EconomicStep<EconomicClock.Output> prepareEconomicStep() {
        var prepared = clock.prepareStep();
        var work = prepared.work();
        var progress = economicProgress(prepared.progress());
        if (work == null) return new EconomicStep<>(progress, null, null);
        return new EconomicStep<>(progress, work::calculate, output -> {
            economicProjectionsPending = true;
            return economicProgress(clock.commit(work, output));
        });
    }

    private EconomicProgress economicProgress(EconomicClock.Progress progress) {
        if (progress.ready() && economicProjectionsPending) {
            economicProjectionsPending = !projections.step(16).ready();
            progress = clock.currentProgress();
        }
        return new EconomicProgress(progress.completedSequence(), progress.outstandingIntervals(),
                progress.waitingForWall(), progress.ready() && !economicProjectionsPending);
    }

    public RecordCatalog recordCatalog() { return configuration.records(); }
    public Snapshot snapshot() { return store.snapshot(); }
    public long lastCommittedSequence() { return store.lastCommittedSequence(); }
    public <T>T transaction(Work<T> work) { return store.transaction(work); }
    public void recover() { store.recover(); }
    public void close() throws Exception {
        WorldShutdown.Evidence evidence=null;
        try {
            if(ready) evidence=store.transaction(tx -> new WorldShutdown.Evidence(store.snapshot().worldId().value().toString(),store.snapshot().schemaVersion(),
                    Long.parseLong(tx.query("SELECT COALESCE(max(sequence),0) AS sequence FROM journal_transactions",List.of(),1).getFirst().get("sequence")),
                    Long.parseLong(tx.query("SELECT global_sequence FROM economic_metadata",List.of(),1).getFirst().get("global_sequence"))));
        } finally { store.close(); }
        closedEvidence=evidence;
    }
    /** Invoke only after Minecraft has finished saving this world's inventories/chunks. */
    public void afterMinecraftStopped() throws java.io.IOException {
        if(closedEvidence==null) throw new java.io.IOException("No clean completed foundation shutdown");
        WorldShutdown.complete(world,closedEvidence);
    }
    public ProjectionManager projections() { if(!ready) throw new IllegalStateException("Foundation not ready");return projections; }
    public EconomicClock clock() { if(!ready) throw new IllegalStateException("Foundation not ready");return clock; }
}
