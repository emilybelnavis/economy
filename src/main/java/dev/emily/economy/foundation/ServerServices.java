package dev.emily.economy.foundation;

import java.nio.file.Path;
import dev.emily.economy.storage.StorageException;
import dev.emily.economy.storage.FoundationStore;
import dev.emily.economy.journal.Journal;
import dev.emily.economy.journal.JournalEngine;
import dev.emily.economy.journal.JournalService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.emily.economy.api.Identifiers.ActorId;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** One instance per server. Minecraft objects must never be captured in worker suppliers. */
public final class ServerServices {
    public enum State { INITIALIZING, READY, UNAVAILABLE, STOPPING, STOPPED }
    public static final int STORAGE_CAPACITY = 256;
    public static final int CALCULATION_CAPACITY = 64;
    private final UUID sessionId = UUID.randomUUID();
    private final Thread owner = Thread.currentThread();
    private final StorageLifecycle storage;
    private final ThreadPoolExecutor storageWorker;
    private final SchedulingConfig config;
    private final ThreadPoolExecutor calculations;
    private final ArrayBlockingQueue<TimedTask> callbacks;
    private final int publicationCapacity;
    private final Map<ActorId, Integer> actorPending = new HashMap<>();
    private final Map<Object, CompletionStage<?>> sharedReads = new HashMap<>();
    private final boolean scheduleEconomy;
    private CompletableFuture<StorageLifecycle.EconomicStep<?>> prepared;
    private EconomicCalculation<?> calculating;
    private long nextEconomicProbe;
    private record EconomicCalculation<T>(CompletableFuture<T> result,
                                          java.util.function.Function<T, StorageLifecycle.EconomicProgress> commit) {
        StorageLifecycle.EconomicProgress finish() { return commit.apply(result.join()); }
    }
    private CompletableFuture<StorageLifecycle.EconomicProgress> committed;
    private StorageLifecycle.EconomicProgress economy;
    private boolean economyObserved;
    private int unresolvedAtShutdown;
    private final OperationDiagnostics operationDiagnostics = new OperationDiagnostics();
    private final Map<Thread, Long> activeStorage = new ConcurrentHashMap<>();
    private final Map<Thread, Long> activeCalculations = new ConcurrentHashMap<>();
    private volatile DiagnosticsReport.StorageDetails storageDetails;
    private long nextDiagnosticsSample;
    private long rateSampleTime;
    private long rateSampleSequence;
    private Double observedRate;
    private CompletableFuture<Void> diagnosticProbe;

    private record TimedTask(long enqueued, Runnable action) implements Runnable {
        public void run() { action.run(); }
    }
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private final CountDownLatch disposed = new CountDownLatch(1);
    private final AtomicBoolean closed = new AtomicBoolean();
    private CompletableFuture<StorageLifecycle.Progress> initialized;
    private volatile StorageLifecycle.Progress progress = new StorageLifecycle.Progress("storage", 0, 0, false);
    private volatile State state = State.INITIALIZING;
    private volatile Throwable failure;
    private volatile Throwable disposalFailure;
    private boolean stoppedCleanly;

    public ServerServices(Path worldDirectory, StorageLifecycle storage) {
        this(worldDirectory, storage, SchedulingConfig.defaults());
    }

    public ServerServices(Path worldDirectory, StorageLifecycle storage, SchedulingConfig config) {
        this.storage = storage;
        this.config = config;
        scheduleEconomy = storage.schedulesEconomy();
        economy = new StorageLifecycle.EconomicProgress(0, 0, false, !scheduleEconomy);
        publicationCapacity = config.storageQueueCapacity() + config.calculationQueueCapacity()
                + config.calculationWorkers() + 1;
        callbacks = new ArrayBlockingQueue<>(publicationCapacity);
        // Reserve one waiting position per worker path for the single internal economic step.
        int reserved = scheduleEconomy ? 1 : 0;
        calculations = worker(config.calculationWorkers(), config.calculationQueueCapacity() + reserved, "economy-calculation");
        storageWorker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(config.storageQueueCapacity() + reserved), runnable -> {
                    Thread thread = new Thread(() -> {
                        try { runnable.run(); }
                        finally {
                            try { this.storage.close(); }
                            catch (Exception e) { disposalFailure = e; failure = e; }
                            finally { closed.set(true); disposed.countDown(); }
                        }
                    }, "economy-storage");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        initialized = runOn(() -> {
            try { storage.initialize(worldDirectory); return startupStep(); }
            catch (Exception e) { throw new CompletionException(e); }
        }, storageWorker);
    }

    private static ThreadPoolExecutor worker(int threads, int capacity, String name) {
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity), runnable -> {
                    Thread thread = new Thread(runnable, name);
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** Bind a trusted journal registration set to this server's existing storage worker. */
    public JournalService journal(
            JournalEngine.AccessPolicy access,
            Map<Journal.Kind, JournalEngine.Registration> handlers,
            JournalService.Publisher publisher) {
        checkOwner();
        if (!(storage instanceof FoundationStore store))
            throw new IllegalStateException("Journal requires persistent foundation storage");
        return new JournalService(this,
                new JournalEngine(store, access, handlers), publisher);
    }

    public UUID sessionId() { return sessionId; }
    public StorageLifecycle.Progress progress() { return progress; }
    public State state() { return state; }
    public Throwable failure() { return failure; }
    public OperationDiagnostics operationDiagnostics() { return operationDiagnostics; }

    public List<String> diagnostics(boolean privileged, boolean detailed) {
        checkOwner();
        var last = operationDiagnostics.lastFailure();
        String code = failure == null ? null : failureCode(failure);
        UUID correlation = null;
        if (last != null && (code == null || (code.startsWith("STORAGE_")
                && (last.code().equals(OperationDiagnostics.Event.OPERATION_STORAGE_FAILED.name())
                || last.code().equals(OperationDiagnostics.Event.OPERATION_OUTCOME_UNKNOWN.name()))))) {
            code = last.code();
            correlation = last.correlation();
        }
        var details = storageDetails;
        if (details != null && state != State.READY) {
            String health = state == State.UNAVAILABLE ? "unavailable" : "closed";
            details = new DiagnosticsReport.StorageDetails(details.worldId(), details.schemaVersion(),
                    details.lastCommittedSequence(), health);
        }
        return DiagnosticsReport.render(privileged, detailed, state, metrics(), details, code, correlation, observedRate);
    }

    public static String failureCode(Throwable error) {
        if (error == null) return "LIFECYCLE_FAILURE";
        if (error instanceof StorageException storageError) return "STORAGE_" + storageError.kind();
        if (error instanceof TimeoutException) return "DRAIN_TIMEOUT";
        return "LIFECYCLE_FAILURE";
    }

    private StorageLifecycle.Progress startupStep() {
        var step = storage.startupStep();
        if (step.ready()) sampleStorage();
        return step;
    }

    private void sampleStorage() {
        if (!(storage instanceof FoundationStore store)) return;
        try {
            var snapshot = store.snapshot();
            long sequence = store.lastCommittedSequence();
            storageDetails = new DiagnosticsReport.StorageDetails(snapshot.worldId().value(),
                    snapshot.schemaVersion(), sequence, "healthy");
        } catch (RuntimeException error) {
            // A failed read-only sample never changes admission or replaces the last known checkpoint.
            var previous = storageDetails;
            if (previous != null) storageDetails = new DiagnosticsReport.StorageDetails(
                    previous.worldId(), previous.schemaVersion(), previous.lastCommittedSequence(), "degraded");
        }
    }

    private void sampleDiagnostics() {
        long now = System.nanoTime();
        if (nextDiagnosticsSample != 0 && now - nextDiagnosticsSample < 0) return;
        nextDiagnosticsSample = now + TimeUnit.SECONDS.toNanos(1);
        if (scheduleEconomy && economyObserved) {
            long sequence = economy.completedSequence();
            if (rateSampleTime != 0 && sequence >= rateSampleSequence) {
                observedRate = (sequence - rateSampleSequence) * 1_000_000_000.0 / (now - rateSampleTime);
            }
            rateSampleTime = now;
            rateSampleSequence = sequence;
        }
        if (storage instanceof FoundationStore && (diagnosticProbe == null || diagnosticProbe.isDone())
                && storageWorker.getQueue().isEmpty() && activeStorage.isEmpty()) {
            diagnosticProbe = runOn(() -> { sampleStorage(); return null; }, storageWorker);
        }
    }
    public CompletionStage<Void> readiness() { return ready.minimalCompletionStage(); }

    public void requireReady() {
        checkOwner();
        if (state != State.READY) throw new IllegalStateException("Economy is " + state, failure);
    }

    public SchedulingMetrics metrics() {
        checkOwner();
        int reserved = scheduleEconomy ? 1 : 0;
        return new SchedulingMetrics(queueMetrics(storageWorker.getQueue(), config.storageQueueCapacity() + reserved, activeStorage),
                queueMetrics(calculations.getQueue(), config.calculationQueueCapacity() + reserved, activeCalculations),
                queueMetrics(callbacks, publicationCapacity, Map.of()), progress, economy,
                prepared != null || calculating != null || committed != null, unresolvedAtShutdown);
    }

    private static SchedulingMetrics.Queue queueMetrics(BlockingQueue<? extends Runnable> queue, int capacity,
                                                         Map<Thread, Long> active) {
        TimedTask oldest = (TimedTask) queue.peek();
        long now = System.nanoTime();
        long activeAge = 0;
        for (long started : active.values()) activeAge = Math.max(activeAge, now - started);
        return new SchedulingMetrics.Queue(capacity, queue.size(),
                oldest == null ? 0 : Math.max(0, now - oldest.enqueued()), activeAge);
    }

    /** Trusted internal work. Futures complete on the server tick. Never join on the server thread. */
    public <T> CompletionStage<T> calculate(Supplier<T> immutableWork) {
        return calculate(null, immutableWork);
    }

    /** Share admission fairly across authenticated actor identities. */
    public <T> CompletionStage<T> calculate(ActorId actor, Supplier<T> immutableWork) {
        return submit(calculations, config.calculationQueueCapacity(), actor, immutableWork);
    }

    public <T> CompletionStage<T> storage(Supplier<T> immutableWork) {
        requireReady();
        sharedReads.clear();
        return submit(storageWorker, config.storageQueueCapacity(), null, immutableWork);
    }

    public <T> CompletionStage<T> storage(ActorId actor, Supplier<T> immutableRead) {
        return submit(storageWorker, config.storageQueueCapacity(), actor, immutableRead);
    }

    /** Equal keys must have identical authorization and result types. A write ends the sharing window. */
    @SuppressWarnings("unchecked")
    public <T> CompletionStage<T> shareStorageRead(ActorId actor, Object key, Supplier<T> immutableRead) {
        requireReady();
        var existing = (CompletionStage<T>) sharedReads.get(key);
        if (existing != null) return existing;
        var result = storage(actor, immutableRead);
        sharedReads.put(key, result);
        result.whenComplete((value, error) -> sharedReads.remove(key, result));
        return result;
    }

    public <T> CompletionStage<T> economicWrite(ActorId actor, Supplier<T> immutableWrite) {
        requireReady();
        if (!economy.ready()) {
            return CompletableFuture.failedStage(new StorageException(StorageException.Kind.ECONOMIC_CATCH_UP,
                    "Economic catch-up in progress"));
        }
        sharedReads.clear();
        return submit(storageWorker, config.storageQueueCapacity(), actor, () -> {
            storage.requireEconomicCurrent();
            return immutableWrite.get();
        });
    }

    private <T> CompletionStage<T> submit(ThreadPoolExecutor executor, int capacity, ActorId actor, Supplier<T> work) {
        requireReady();
        int actorLimit = Math.max(1, Math.min(8, capacity / 2));
        if (pending.size() >= publicationCapacity
                || executor.getQueue().size() >= capacity
                || (actor != null && actorPending.getOrDefault(actor, 0) >= actorLimit)) {
            return CompletableFuture.failedStage(new StorageException(StorageException.Kind.OVERLOAD, "Work admission limit reached"));
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        pending.add(result);
        if (actor != null) actorPending.merge(actor, 1, Integer::sum);
        result.whenComplete((value, error) -> {
            pending.remove(result);
            if (actor != null) actorPending.computeIfPresent(actor, (key, count) -> count == 1 ? null : count - 1);
        });
        runOn(work, executor).whenComplete((value, error) -> deliver(() -> {
            if (error instanceof StorageException storageError && storageError.fatal()) {
                failure = error;
                state = State.UNAVAILABLE;
            }
            if (error == null) result.complete(value);
            else result.completeExceptionally(error);
        }));
        return result.minimalCompletionStage();
    }

    private <T> CompletableFuture<T> runOn(Supplier<T> work, ThreadPoolExecutor executor) {
        CompletableFuture<T> result = new CompletableFuture<>();
        var active = executor == storageWorker ? activeStorage : activeCalculations;
        executor.execute(new TimedTask(System.nanoTime(), () -> {
            var thread = Thread.currentThread();
            active.put(thread, System.nanoTime());
            try { result.complete(work.get()); }
            catch (Throwable error) { result.completeExceptionally(error); }
            finally { active.remove(thread); }
        }));
        return result;
    }

    private void deliver(Runnable callback) {
        synchronized (callbacks) {
            if (state == State.STOPPING || state == State.STOPPED) return;
            // Total outstanding work is capped at the callback queue capacity.
            if (!callbacks.offer(new TimedTask(System.nanoTime(), callback)))
                throw new IllegalStateException("Completion bound violated");
        }
    }

    private void advanceEconomy() {
        if (!scheduleEconomy) return;
        if (prepared != null) {
            if (!prepared.isDone()) return;
            var step = prepared.join();
            prepared = null;
            economy = step.progress();
            economyObserved = true;
            if (step.calculation() != null) {
                startCalculation(step);
                return;
            }
            if (economy.ready() || economy.waitingForWall())
                nextEconomicProbe = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(250);
        }
        if (calculating != null) {
            var calculation = calculating;
            if (!calculation.result().isDone()) return;
            // Observe failures before the next storage task is accepted.
            calculation.result().join();
            calculating = null;
            sharedReads.clear();
            committed = runOn(calculation::finish, storageWorker);
            return;
        }
        if (committed != null) {
            if (!committed.isDone()) return;
            economy = committed.join();
            committed = null;
        }
        if (nextEconomicProbe == 0 || System.nanoTime() - nextEconomicProbe >= 0) {
            nextEconomicProbe = 0;
            prepared = runOn(storage::prepareEconomicStep, storageWorker);
        }
    }

    private <T> void startCalculation(StorageLifecycle.EconomicStep<T> step) {
        calculating = new EconomicCalculation<>(runOn(step.calculation(), calculations), step.commit());
    }

    /** Starts callbacks within the configured budget; an individual callback must itself be short. */
    public void tick() {
        checkOwner();
        if (state == State.INITIALIZING && initialized.isDone()) {
            try {
                progress = initialized.join();
                if (progress.ready()) {
                    state = State.READY;
                    nextDiagnosticsSample = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
                    ready.complete(null);
                }
                else initialized = runOn(this::startupStep, storageWorker);
            }
            catch (CompletionException e) {
                failure = e.getCause(); state = State.UNAVAILABLE; ready.completeExceptionally(failure);
            }
        }
        if (state == State.READY) {
            try {
                advanceEconomy();
                sampleDiagnostics();
            }
            catch (CompletionException e) {
                prepared = null;
                calculating = null;
                committed = null;
                if (!(e.getCause() instanceof StorageException error)
                        || error.kind() != StorageException.Kind.LOCK_TIMEOUT) {
                    failure = e.getCause();
                    state = State.UNAVAILABLE;
                }
            }
        }
        long start = System.nanoTime();
        while (state != State.STOPPING && state != State.STOPPED && System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(config.callbackBudgetMillis())) {
            Runnable callback = callbacks.poll();
            if (callback == null) break;
            callback.run();
        }
    }

    /** Only the stopping lifecycle may wait. Never used by ticks or request handlers. */
    public boolean stop() { return stop(Duration.ofSeconds(config.drainSeconds())); }

    public boolean stop(Duration timeout) {
        checkOwner();
        if (state == State.STOPPED) return stoppedCleanly;
        if (state == State.STOPPING) return false;
        state = State.STOPPING;
        ready.completeExceptionally(new IllegalStateException("Server stopping"));
        long deadline = System.nanoTime() + timeout.toNanos();
        calculations.shutdown();
        // The storage thread disposes its adapter after all accepted work.
        storageWorker.shutdown();
        boolean drained = await(storageWorker, deadline) & await(calculations, deadline);
        if (drained) {
            try { drained = disposed.await(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); drained = false; }
            drained &= closed.get() && disposalFailure == null;
        }
        if (!drained) {
            unresolvedAtShutdown = pending.size() + (metrics().economicStepInFlight() ? 1 : 0);
            if (failure == null) failure = new TimeoutException(
                    "Shutdown drain failed; " + unresolvedAtShutdown + " work items without publication");
            storageWorker.shutdownNow();
            calculations.shutdownNow();
        }
        pending.forEach(f -> f.completeExceptionally(new CancellationException("Server stopped")));
        synchronized (callbacks) { callbacks.clear(); }
        sharedReads.clear();
        prepared = null;
        calculating = null;
        committed = null;
        stoppedCleanly = drained;
        state = State.STOPPED;
        return drained;
    }

    private boolean await(ExecutorService executor, long deadline) {
        try { return executor.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
    }

    private void checkOwner() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Requires server thread");
    }
}
