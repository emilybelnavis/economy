package dev.emily.economy.journal;

import dev.emily.economy.api.Identifiers.*;
import dev.emily.economy.storage.SqliteStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static dev.emily.economy.journal.Journal.*;
import static dev.emily.economy.journal.JournalFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class JournalProcessTest {
    @TempDir Path temporary;
    @Test void processDeathBeforeAndAfterCommitResolvesThroughOriginalOperation() throws Exception {
        for (String boundary : new String[]{"before", "after"}) {
            Path world = Files.createDirectory(temporary.toRealPath().resolve(boundary)); createWorld(world); var request = intent();
            Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-cp",
                    System.getProperty("economy.test.classpath"), JournalProcessProbe.class.getName(), world.toString(), boundary, request.operationId().value().toString())
                    .redirectErrorStream(true).start();
            try {
                String line = CompletableFuture.supplyAsync(() -> {
                    try { return new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream())).readLine(); }
                    catch (Exception e) { throw new CompletionException(e); }
                }).get(15, TimeUnit.SECONDS);
                assertEquals("BOUNDARY", line);
                process.destroyForcibly(); assertTrue(process.waitFor(10, TimeUnit.SECONDS));
                var store = new SqliteStorage(dev.emily.economy.storage.FixtureRecords.catalog()); store.initialize(world); var engine = engine(store, new AtomicInteger());
                var prior = engine.lookup(request);
                if (boundary.equals("before")) { assertTrue(prior.isEmpty()); assertEquals(0, counter(store)); }
                else { assertEquals(Status.COMMITTED, prior.orElseThrow().status()); assertEquals(1, counter(store)); }
                var result = engine.submit(request); assertEquals(Status.COMMITTED, result.status()); assertEquals(1, counter(store));
                if (prior.isPresent()) assertEquals(prior.orElseThrow(), result);
                assertEquals(1, count(store, "journal_transactions")); assertEquals(2, count(store, "journal_records")); store.close();
            } finally {
                if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
            }
        }
    }
}
