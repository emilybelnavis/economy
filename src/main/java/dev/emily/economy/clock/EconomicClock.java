package dev.emily.economy.clock;

import dev.emily.economy.api.Identifiers.*;
import dev.emily.economy.journal.*;
import dev.emily.economy.storage.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** UTC interval debt, durable completions and deterministic versioned simulation inputs. No game-time input. */
public final class EconomicClock {
    public record Update(UUID id, String system, long startMillis, long endMillis, int version, long randomSeed) {}
    public record Output(String payload, JournalEngine.Projection effects) {
        public Output {
            EconomicRecord.validateOutput(payload);
            Objects.requireNonNull(effects);
        }
    }
    @FunctionalInterface public interface Simulation { Output compute(Update update) throws Exception; }
    public record Version(int number, String implementationId, Simulation simulation) {
        public Version { if (number < 1 || implementationId == null || !implementationId.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IllegalArgumentException("Simulation version"); Objects.requireNonNull(simulation); }
    }
    public record SystemDefinition(String id, long cadenceMillis, long originMillis, int initialVersion,
                                   Map<Integer, Version> versions, Set<String> dependsOn) {
        public SystemDefinition {
            new Journal.Kind(id, "foundation:interval");
            if (cadenceMillis < 1 || originMillis < 0 || versions.isEmpty() || versions.size() > 16 || !versions.containsKey(initialVersion)) throw new IllegalArgumentException("Clock definition");
            Math.addExact(originMillis, cadenceMillis);
            versions = Map.copyOf(versions); dependsOn = Set.copyOf(dependsOn);
            for (var entry : versions.entrySet()) if (entry.getKey() != entry.getValue().number()) throw new IllegalArgumentException("Version key");
        }
    }
    public record Progress(long completedSequence, long outstandingIntervals, boolean waitingForWall, boolean ready) {}
    private record Input(Update update, Version implementation, long sequence) {}
    public record PreparedStep(Progress progress, Work work) {}
    private static final Journal.Kind KIND = new Journal.Kind("foundation:clock", "foundation:interval");
    private final FoundationStore store;
    private final Clock wall;
    private final Map<String, SystemDefinition> definitions;
    private final Map<String, Integer> order = new HashMap<>();
    private final JournalEngine journal;
    public EconomicClock(FoundationStore store, Clock wall, Collection<SystemDefinition> systems) {
        this.store = store; this.wall = wall;
        if (systems.size() > 64) throw new IllegalArgumentException("System registry bound");
        Map<String, SystemDefinition> copy = new TreeMap<>();
        for (var system : systems) if (copy.putIfAbsent(system.id(), system) != null) throw new IllegalArgumentException("Duplicate system");
        definitions = Map.copyOf(copy);
        while (order.size() < definitions.size()) {
            boolean advanced = false;
            for (var system : copy.values()) {
                if (!definitions.keySet().containsAll(system.dependsOn())) throw new IllegalArgumentException("Missing dependency");
                if (!order.containsKey(system.id()) && order.keySet().containsAll(system.dependsOn())) { order.put(system.id(), order.size()); advanced = true; }
            }
            if (!advanced) throw new IllegalArgumentException("Cyclic clock dependencies");
        }
        journal = journal((intent, reads) -> {
            var input = input(intent, reads::query);
            return plan(intent, input, Objects.requireNonNull(input.implementation().simulation().compute(input.update())));
        });
    }
    public void prepare() {
        store.transaction(tx -> {
            var persisted = tx.query("SELECT * FROM economic_systems ORDER BY system_id", List.of(), 64);
            for (var row : persisted) if (!definitions.containsKey(row.get("system_id"))) throw failure("Missing persisted economic system " + row.get("system_id"));
            for (var definition : definitions.values()) {
                var existing = tx.query("SELECT * FROM economic_systems WHERE system_id=?", List.of(definition.id()), 1);
                if (existing.isEmpty()) {
                    tx.execute("INSERT INTO economic_systems VALUES(?,?,?,?,0)", List.of(definition.id(), str(definition.cadenceMillis()), str(definition.originMillis()), str(definition.originMillis())));
                    var version = definition.versions().get(definition.initialVersion());
                    tx.execute("INSERT INTO economic_versions VALUES(?,?,?,?)", List.of(definition.id(), str(version.number()), signature(definition,version), str(Math.addExact(definition.originMillis(), definition.cadenceMillis()))));
                } else {
                    var row = existing.getFirst();
                    if (num(row,"cadence_ms") != definition.cadenceMillis() || num(row,"origin_ms") != definition.originMillis()) throw failure("Cadence/origin change requires explicit design");
                    var latest = tx.query("SELECT * FROM economic_updates WHERE system_id=? ORDER BY economic_sequence DESC LIMIT 1", List.of(definition.id()), 1);
                    if (latest.isEmpty()) {
                        if (num(row,"frontier_ms") != definition.originMillis() || num(row,"last_sequence") != 0) throw failure("Invalid initial clock checkpoint");
                    } else if (num(row,"frontier_ms") != num(latest.getFirst(),"interval_end_ms") || num(row,"last_sequence") != num(latest.getFirst(),"economic_sequence")) throw failure("Clock checkpoint disagrees with completion");
                }
                var versions = tx.query("SELECT * FROM economic_versions WHERE system_id=? ORDER BY effective_end_ms", List.of(definition.id()), 16);
                if (Integer.parseInt(versions.getFirst().get("version")) != definition.initialVersion() || num(versions.getFirst(),"effective_end_ms") != Math.addExact(definition.originMillis(),definition.cadenceMillis())) throw failure("Initial version schedule changed");
                for (var version : versions) {
                    long boundary=num(version,"effective_end_ms");
                    if(boundary<=definition.originMillis()||(boundary-definition.originMillis())%definition.cadenceMillis()!=0)throw failure("Invalid simulation effective boundary");
                    var implementation = definition.versions().get(Integer.parseInt(version.get("version")));
                    if (implementation == null || !signature(definition,implementation).equals(version.get("implementation_id"))) throw failure("Historical simulator missing or changed");
                }
            }
            var meta = tx.query("SELECT * FROM economic_metadata", List.of(), 1).getFirst();
            if (!tx.query("SELECT u.update_id FROM economic_updates u WHERE NOT EXISTS(SELECT 1 FROM journal_records r WHERE r.sequence=u.journal_sequence AND r.record_type='foundation:economic-update') LIMIT 1",List.of(),1).isEmpty()) throw failure("Completion lacks authoritative economic record");
            var count = tx.query("SELECT count(*) AS count,COALESCE(max(economic_sequence),0) AS maximum FROM economic_updates", List.of(), 1).getFirst();
            if (num(meta,"global_sequence") != num(count,"maximum") || num(count,"count") != num(count,"maximum")) throw failure("Economic sequence/completion mismatch");
            return null;
        });
    }
    /** Explicit future boundary; overdue intervals always retain the historical scheduled version. */
    public void scheduleVersion(String system, int version, long effectiveEndMillis) {
        var definition = Objects.requireNonNull(definitions.get(system)); var implementation = Objects.requireNonNull(definition.versions().get(version));
        store.transaction(tx -> {
            long frontier = num(tx.query("SELECT frontier_ms FROM economic_systems WHERE system_id=?", List.of(system), 1).getFirst(),"frontier_ms");
            long observed = num(tx.query("SELECT observed_wall_ms FROM economic_metadata", List.of(), 1).getFirst(),"observed_wall_ms");
            if (effectiveEndMillis <= Math.max(Math.max(frontier, observed), wall.millis()) || effectiveEndMillis < definition.originMillis()
                    || (effectiveEndMillis - definition.originMillis()) % definition.cadenceMillis() != 0) throw new IllegalArgumentException("Version boundary must be aligned and strictly in the future");
            tx.execute("INSERT INTO economic_versions VALUES(?,?,?,?)", List.of(system, str(version), signature(definition,implementation), str(effectiveEndMillis)));
            return null;
        });
    }
    public Progress runBatch(int maximumUpdates) {
        if (maximumUpdates < 1 || maximumUpdates > 16) throw new IllegalArgumentException("Clock batch bound");
        long now = Math.max(0, wall.millis());
        store.transaction(tx -> { observe(tx, now); return null; });
        for (int i = 0; i < maximumUpdates; i++) {
            var intent = store.transaction(tx -> nextIntent(tx, now));
            if (intent == null) break;
            submit(journal, intent);
        }
        return progress(now);
    }

    public PreparedStep prepareStep() {
        long now = Math.max(0, wall.millis());
        return store.transaction(tx -> {
            observe(tx, now);
            var progress = progress(tx, now);
            var intent = nextIntent(tx, now);
            return new PreparedStep(progress, intent == null ? null : new Work(intent, input(intent, tx::query)));
        });
    }

    public static final class Work {
        private final Journal.Intent intent;
        private final Input prepared;

        private Work(Journal.Intent intent, Input prepared) {
            this.intent = intent;
            this.prepared = prepared;
        }

        public Output calculate() {
            try {
                return Objects.requireNonNull(prepared.implementation().simulation().compute(prepared.update()));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new IllegalStateException("Economic simulation failed for " + prepared.update().id(), e);
            }
        }

    }

    public Progress commit(Work work, Output output) {
        long now = Math.max(0, wall.millis());
        if (work.prepared.update().endMillis() > now) return progress(now);
        var stagedJournal = journal((request, reads) -> {
            var current = input(request, reads::query);
            if (!work.prepared.equals(current)) throw failure("Economic inputs changed before commit");
            return plan(request, current, Objects.requireNonNull(output));
        });
        submit(stagedJournal, work.intent);
        return progress(Math.max(0, wall.millis()));
    }

    private void observe(FoundationStore.Transaction tx, long now) {
        tx.execute("UPDATE economic_metadata SET observed_wall_ms=max(observed_wall_ms,CAST(? AS INTEGER)) WHERE singleton=1", List.of(str(now)));
    }

    private Journal.Intent nextIntent(FoundationStore.Transaction tx, long now) {
        var next = tx.query("SELECT * FROM economic_systems", List.of(), 64).stream()
                .filter(row -> nextEnd(row) <= now)
                .min(Comparator.<Map<String,String>>comparingLong(this::nextEnd)
                        .thenComparingInt(row -> order.get(row.get("system_id"))).thenComparing(row -> row.get("system_id"))).orElse(null);
        if (next == null) return null;
        String system = next.get("system_id");
        long end = nextEnd(next);
        int version = Integer.parseInt(tx.query("SELECT version FROM economic_versions WHERE system_id=? AND effective_end_ms<=? ORDER BY effective_end_ms DESC LIMIT 1", List.of(system,str(end)), 1).getFirst().get("version"));
        UUID update = stable("update|" + store.snapshot().worldId().value() + "|" + system + "|" + end + "|" + version);
        return new Journal.Intent(new OperationId(stable("operation|"+update)), new RequestId(UUID.randomUUID()), actor(system), KIND,
                new Journal.CanonicalRequest(1, Map.of("system",system,"start",next.get("frontier_ms"),"end",str(end),"version",str(version))), update, null);
    }

    private long nextEnd(Map<String, String> row) {
        return Math.addExact(num(row, "frontier_ms"), definitions.get(row.get("system_id")).cadenceMillis());
    }

    private JournalEngine journal(JournalEngine.Handler planner) {
        return new JournalEngine(store, (actor, kind) -> actor instanceof SystemActorId && kind.equals(KIND),
                Map.of(KIND, new JournalEngine.Registration(1, planner)));
    }

    private static void submit(JournalEngine engine, Journal.Intent intent) {
        var outcome = engine.submit(intent);
        if (outcome.status() != Journal.Status.COMMITTED) throw new StorageException(StorageException.Kind.UNSAFE_CONNECTION,
                "Economic update unresolved; retain original ID " + intent.economicUpdateId());
    }

    private Progress progress(long now) {
        return store.transaction(tx -> progress(tx, now));
    }
    public Progress currentProgress() {
        return progress(Math.max(0, wall.millis()));
    }

    /** Persist newly observed debt even if an intervening rewind precedes the scheduler's next poll. */
    public Progress observeCurrentDebt() {
        long now = Math.max(0, wall.millis());
        return store.transaction(tx -> {
            var progress = progress(tx, now);
            if (progress.outstandingIntervals() > 0) observe(tx, now);
            return progress;
        });
    }

    private Progress progress(FoundationStore.Transaction tx, long now) {
        var meta = tx.query("SELECT * FROM economic_metadata", List.of(), 1).getFirst();
        long target = Math.max(now, num(meta, "observed_wall_ms"));
        long debt = 0;
        boolean waiting = false;
        for (var row : tx.query("SELECT * FROM economic_systems", List.of(), 64)) {
            long frontier = num(row, "frontier_ms");
            long cadence = definitions.get(row.get("system_id")).cadenceMillis();
            debt = Math.addExact(debt, Math.max(0, target - frontier) / cadence);
            waiting |= (num(row, "last_sequence") > 0 && now < frontier) || (target > now && target - frontier >= cadence);
        }
        return new Progress(num(meta, "global_sequence"), debt, waiting, debt == 0 && !waiting);
    }

    @FunctionalInterface
    private interface Queries {
        List<Map<String, String>> query(String sql, List<String> parameters, int maximumRows);
    }
    private Input input(Journal.Intent intent, Queries reads) {
        String system = intent.request().fields().get("system"); var definition = Objects.requireNonNull(definitions.get(system));
        var state = reads.query("SELECT * FROM economic_systems WHERE system_id=?", List.of(system), 1).getFirst();
        long start = Long.parseLong(intent.request().fields().get("start")), end = Long.parseLong(intent.request().fields().get("end"));
        int version = Integer.parseInt(intent.request().fields().get("version"));
        var scheduled = reads.query("SELECT * FROM economic_versions WHERE system_id=? AND effective_end_ms<=? ORDER BY effective_end_ms DESC LIMIT 1", List.of(system,str(end)),1).getFirst();
        if (start != num(state,"frontier_ms") || end != Math.addExact(start,definition.cadenceMillis()) || Integer.parseInt(scheduled.get("version")) != version || !actor(system).equals(intent.actor())) throw failure("Economic request/checkpoint mismatch");
        var implementation = definition.versions().get(version);
        if (implementation == null || !signature(definition,implementation).equals(scheduled.get("implementation_id"))) throw failure("Historical simulator unavailable");
        var meta = reads.query("SELECT * FROM economic_metadata", List.of(), 1).getFirst();
        long sequence = Math.addExact(num(meta,"global_sequence"),1);
        long seed = randomSeed(meta.get("seed"), intent.economicUpdateId(), version);
        var update = new Update(intent.economicUpdateId(),system,start,end,version,seed);
        return new Input(update, implementation, sequence);
    }
    private JournalEngine.Decision plan(Journal.Intent intent, Input input, Output output) {
        var update = input.update();
        String system = update.system();
        long start = update.startMillis(), end = update.endMillis(), sequence = input.sequence(), seed = update.randomSeed();
        int version = update.version();
        var fact = new EconomicRecord(system,start,end,version,sequence,seed,update.id(),output.payload());
        return new JournalEngine.Commit(List.of(new Journal.Record(EconomicRecord.TYPE,1,fact.encode())), new Journal.Result(1,"ECONOMIC_UPDATED",str(sequence)), tx -> {
            output.effects().apply(tx);
            var journalRow = tx.query("SELECT transaction_id,sequence FROM journal_transactions WHERE world_id=? AND operation_id=?", List.of(store.snapshot().worldId().value().toString(),intent.operationId().value().toString()),1).getFirst();
            tx.execute("INSERT INTO economic_updates VALUES(?,?,?,?,?,?,?,?,?,?)", List.of(update.id().toString(),str(sequence),system,str(start),str(end),str(version),str(seed),output.payload(),journalRow.get("transaction_id"),journalRow.get("sequence")));
            tx.execute("UPDATE economic_metadata SET global_sequence=? WHERE singleton=1", List.of(str(sequence)));
            tx.execute("UPDATE economic_systems SET frontier_ms=?,last_sequence=? WHERE system_id=?",List.of(str(end),str(sequence),system));
        });
    }
    /** Pin algorithm identity and same-time dependency meaning together across restarts. */
    public static String signature(SystemDefinition definition,Version version) {
        String text=definition.id()+"|"+definition.cadenceMillis()+"|"+definition.originMillis()+"|"+version.implementationId()+"|"+String.join("\n",new TreeSet<>(definition.dependsOn()));
        try {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    private SystemActorId actor(String system) { return new SystemActorId(stable("actor|"+store.snapshot().worldId().value()+"|"+system)); }
    private static UUID stable(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)); }
    private static long randomSeed(String seed, UUID id, int version) {
        try { return java.nio.ByteBuffer.wrap(java.security.MessageDigest.getInstance("SHA-256").digest((seed+"|"+id+"|"+version).getBytes(StandardCharsets.UTF_8))).getLong(); }
        catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private static long num(Map<String,String> row,String field) { return Long.parseLong(row.get(field)); }
    private static String str(long value) { return Long.toString(value); }
    private static StorageException failure(String message) { return new StorageException(StorageException.Kind.SCHEMA,message); }
}
