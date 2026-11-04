package dev.emily.economy.journal;

import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.api.Identifiers.*;
import dev.emily.economy.storage.SqliteStorage;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.emily.economy.journal.Journal.*;
import static dev.emily.economy.journal.TerminalPolicy.*;
import static dev.emily.economy.journal.TerminalPolicy.Action.*;
import static org.junit.jupiter.api.Assertions.*;

class TerminalPolicyTest {
    @TempDir Path world;
    private final PlayerId owner = new PlayerId(UUID.randomUUID());
    private final PlayerId operator = new PlayerId(UUID.randomUUID());
    private final PlayerId stranger = new PlayerId(UUID.randomUUID());
    private final Anchor anchor = new Anchor("minecraft:overworld", 10, 64, 20);
    private final Map<String, Denomination> catalog = Map.of(
            "economy:cad_bill_5", new Denomination(EconomyCurrency.CAD, true),
            "economy:cad_coin_25", new Denomination(EconomyCurrency.CAD, false),
            "economy:eur_bill_5", new Denomination(EconomyCurrency.EUR, true));
    private boolean live = true;

    @Test void ownerOperatorLiveAuthorityAndOptimisticRevisionAreRequired() throws Exception {
        var store = open();
        try {
            var generation = UUID.randomUUID();
            var place = intent(PLACE, owner, placeRequest(anchor, generation, owner, TerminalPolicy.Kind.ATM, null, 0));
            assertEquals(Status.COMMITTED, engine(store, PLACE).submit(place).status());
            var initial = snapshot(store, owner);
            assertEquals(defaultSettings(TerminalPolicy.Kind.ATM, catalog), initial.settings());
            var settings = new Settings(Mode.BOTH, Set.of(EconomyCurrency.CAD), Set.of("economy:cad_bill_5"));
            assertEquals(Status.NOT_ACCEPTED, submit(store, CONFIGURE, stranger,
                    configureRequest(anchor, generation, 1, settings)).status());
            assertTrue(store.transaction(tx -> TerminalPolicy.find(tx::query, store.snapshot().worldId(),
                    anchor, stranger, false)).isEmpty());
            live = false;
            var edit = intent(CONFIGURE, owner, configureRequest(anchor, generation, 1, settings));
            assertEquals(Status.NOT_ACCEPTED, engine(store, CONFIGURE).submit(edit).status());
            live = true;
            var configured = engine(store, CONFIGURE).submit(edit);
            assertEquals(Status.COMMITTED, configured.status());
            assertEquals(configured, engine(store, CONFIGURE).submit(edit));
            var conflict = new Intent(edit.operationId(), new RequestId(UUID.randomUUID()), owner, edit.kind(),
                    configureRequest(anchor, generation, 1, Settings.disabled()), null, null);
            assertEquals("OPERATION_CONFLICT", engine(store, CONFIGURE).submit(conflict).result().code());
            assertEquals(Status.DURABLY_REJECTED, submit(store, CONFIGURE, owner,
                    configureRequest(anchor, generation, 1, settings)).status());
            assertEquals(Status.COMMITTED, submit(store, CONFIGURE, operator,
                    configureRequest(anchor, generation, 2, Settings.disabled())).status());
            assertEquals(3L, snapshot(store, owner).revision());
        } finally { store.close(); }
    }

    @Test void replacementRemovalAndReloadRejectStaleOrReusedGenerations() throws Exception {
        var store = open();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Intent placement;
        try {
            placement = intent(PLACE, owner, placeRequest(anchor, first, owner, TerminalPolicy.Kind.TELLER, null, 0));
            assertEquals(Status.COMMITTED, engine(store, PLACE).submit(placement).status());
            assertEquals(Status.COMMITTED, submit(store, REMOVE, owner, removeRequest(anchor, first, 1)).status());
            assertEquals(Status.DURABLY_REJECTED, submit(store, CONFIGURE, owner,
                    configureRequest(anchor, first, 2, Settings.disabled())).status());
            assertEquals(Status.COMMITTED, submit(store, PLACE, stranger,
                    placeRequest(anchor, second, stranger, TerminalPolicy.Kind.ATM, first, 2)).status());
            assertFalse(store.<Boolean>transaction(tx -> TerminalPolicy.matches(tx::query, store.snapshot().worldId(), anchor, first, 1)));
            assertEquals(Status.DURABLY_REJECTED, submit(store, REMOVE, stranger, removeRequest(anchor, first, 1)).status());
            assertEquals(Status.COMMITTED, engine(store, PLACE).submit(placement).status());
            assertEquals(second, snapshot(store, stranger).generation());
        } finally { store.close(); }
        var reopened = open();
        try {
            assertEquals(second, snapshot(reopened, stranger).generation());
            assertTrue(reopened.<Boolean>transaction(tx -> TerminalPolicy.matches(tx::query, reopened.snapshot().worldId(), anchor, second, 3)));
            assertEquals(Status.DURABLY_REJECTED, submit(reopened, PLACE, stranger,
                    placeRequest(anchor, first, stranger, TerminalPolicy.Kind.TELLER, second, 3)).status());
            assertEquals(second, snapshot(reopened, stranger).generation());
        } finally { reopened.close(); }
    }

    @Test void whitelistAndModeValidationFollowTerminalCapabilities() throws Exception {
        var store = open();
        try {
            var generation = UUID.randomUUID();
            submit(store, PLACE, owner, placeRequest(anchor, generation, owner, TerminalPolicy.Kind.ATM, null, 0));
            assertEquals(Status.DURABLY_REJECTED, submit(store, CONFIGURE, owner,
                    configureRequest(anchor, generation, 1, new Settings(Mode.DEPOSIT,
                            Set.of(EconomyCurrency.CAD), Set.of("economy:cad_coin_25")))).status());
            assertEquals(Status.DURABLY_REJECTED, submit(store, CONFIGURE, owner,
                    configureRequest(anchor, generation, 1, new Settings(Mode.WITHDRAW,
                            Set.of(EconomyCurrency.CAD), Set.of("economy:eur_bill_5")))).status());
            assertEquals(Status.DURABLY_REJECTED, submit(store, CONFIGURE, owner,
                    configureRequest(anchor, generation, 1, new Settings(Mode.BOTH,
                            Set.of(EconomyCurrency.CAD), Set.of("economy:unknown")))).status());
            assertEquals(1L, snapshot(store, owner).revision());
            var tellerGeneration = UUID.randomUUID();
            assertEquals(Status.COMMITTED, submit(store, PLACE, owner,
                    placeRequest(anchor, tellerGeneration, owner, TerminalPolicy.Kind.TELLER, generation, 1)).status());
            assertEquals(Status.DURABLY_REJECTED, submit(store, CONFIGURE, owner,
                    configureRequest(anchor, tellerGeneration, 2, new Settings(Mode.BOTH,
                            Set.of(EconomyCurrency.CAD), Set.of("economy:cad_coin_25")))).status());
            assertEquals(Status.COMMITTED, submit(store, CONFIGURE, owner,
                    configureRequest(anchor, tellerGeneration, 2, defaultSettings(TerminalPolicy.Kind.TELLER, catalog))).status());
            var otherDimension = new Anchor("minecraft:the_nether", anchor.x(), anchor.y(), anchor.z());
            assertTrue(store.transaction(tx -> TerminalPolicy.find(tx::query, store.snapshot().worldId(),
                    otherDimension, owner, false)).isEmpty());
            var registerGeneration = UUID.randomUUID();
            submit(store, PLACE, owner, placeRequest(otherDimension, registerGeneration, owner,
                    TerminalPolicy.Kind.REGISTER, null, 0));
            assertEquals(Status.DURABLY_REJECTED, submit(store, CONFIGURE, owner,
                    configureRequest(otherDimension, registerGeneration, 1, new Settings(Mode.BOTH,
                            Set.of(EconomyCurrency.CAD), Set.of("economy:cad_bill_5")))).status());
            var oversized = java.util.stream.IntStream.range(0, 119).mapToObj(i -> "economy:denomination_" + i)
                    .collect(java.util.stream.Collectors.toSet());
            assertThrows(IllegalArgumentException.class,
                    () -> new Settings(Mode.BOTH, Set.of(EconomyCurrency.CAD), oversized));
            assertThrows(IllegalArgumentException.class, () -> new Anchor("invalid", 0, 64, 0));
            assertThrows(IllegalArgumentException.class, () -> new Anchor("minecraft:overworld", 30_000_001, 64, 0));
        } finally { store.close(); }
    }

    @Test void defaultsAndLegacyTellerServicesAreCompleteWithoutRewritingStoredSettings() throws Exception {
        var store = open();
        try {
            var teller = UUID.randomUUID();
            assertEquals(Status.COMMITTED, submit(store, PLACE, owner, placeRequest(anchor, teller, owner, TerminalPolicy.Kind.TELLER, null, 0)).status());
            var full = defaultSettings(TerminalPolicy.Kind.TELLER, catalog);
            assertEquals(Mode.BOTH, full.mode());
            assertEquals(EnumSet.allOf(EconomyCurrency.class), full.currencies());
            assertEquals(catalog.keySet(), full.denominations());
            assertEquals(full, snapshot(store, owner).settings());
            assertEquals(Status.DURABLY_REJECTED, submit(store, CONFIGURE, owner,
                    configureRequest(anchor, teller, 1, Settings.disabled())).status());
            assertEquals(Status.DURABLY_REJECTED, submit(store, CONFIGURE, operator,
                    configureRequest(anchor, teller, 1, new Settings(Mode.DEPOSIT, Set.of(EconomyCurrency.CAD), Set.of("economy:cad_coin_25")))).status());
            // Simulate a previously saved teller, retaining its original revision and historical projection.
            store.transaction(tx -> tx.execute("UPDATE terminal_settings SET mode='DISABLED',currencies='',denominations=''", List.of()));
            var historical = snapshot(store, owner);
            var usable = effective(historical, catalog);
            assertEquals(full, usable.settings());
            assertEquals(historical.revision(), usable.revision());
            assertEquals(historical.generation(), usable.generation());
            assertEquals(Mode.DISABLED, snapshot(store, owner).settings().mode());
            assertEquals(Status.COMMITTED, submit(store, REMOVE, owner, removeRequest(anchor, teller, 1)).status());
            assertFalse(effective(snapshot(store, owner), catalog).active());
            assertEquals(Settings.disabled(), defaultSettings(TerminalPolicy.Kind.REGISTER, catalog));
        } finally { store.close(); }
    }

    @Test void twoAtmSettingsRemainIndependentAndPersistAcrossReload() throws Exception {
        var store = open();
        var other = new Anchor(anchor.dimension(), anchor.x() + 4, anchor.y(), anchor.z());
        var first = UUID.randomUUID(); var second = UUID.randomUUID();
        var restricted = new Settings(Mode.WITHDRAW, Set.of(EconomyCurrency.CAD), Set.of("economy:cad_bill_5"));
        try {
            assertEquals(Status.COMMITTED, submit(store, PLACE, owner, placeRequest(anchor, first, owner, TerminalPolicy.Kind.ATM, null, 0)).status());
            assertEquals(Status.COMMITTED, submit(store, PLACE, stranger, placeRequest(other, second, stranger, TerminalPolicy.Kind.ATM, null, 0)).status());
            var defaults = defaultSettings(TerminalPolicy.Kind.ATM, catalog);
            assertEquals(Mode.BOTH, defaults.mode());
            assertEquals(EnumSet.allOf(EconomyCurrency.class), defaults.currencies());
            assertEquals(Set.of("economy:cad_bill_5", "economy:eur_bill_5"), defaults.denominations());
            assertEquals(Status.NOT_ACCEPTED, submit(store, CONFIGURE, stranger, configureRequest(anchor, first, 1, restricted)).status());
            assertEquals(Status.COMMITTED, submit(store, CONFIGURE, owner, configureRequest(anchor, first, 1, restricted)).status());
            assertEquals(defaults, store.transaction(tx -> find(tx::query, store.snapshot().worldId(), other, stranger, false)).orElseThrow().settings());
        } finally { store.close(); }
        var reopened = open();
        try {
            assertEquals(restricted, effective(snapshot(reopened, owner), catalog).settings());
            assertEquals(defaultSettings(TerminalPolicy.Kind.ATM, catalog), reopened.transaction(tx -> find(tx::query, reopened.snapshot().worldId(), other, stranger, false)).orElseThrow().settings());
            assertEquals(Status.COMMITTED, submit(reopened, REMOVE, owner, removeRequest(anchor, first, 2)).status());
            assertEquals(Status.COMMITTED, submit(reopened, PLACE, owner, placeRequest(anchor, UUID.randomUUID(), owner, TerminalPolicy.Kind.ATM, first, 3)).status());
            assertEquals(defaultSettings(TerminalPolicy.Kind.ATM, catalog), snapshot(reopened, owner).settings());
        } finally { reopened.close(); }
    }

    private SqliteStorage open() throws Exception {
        var store = new SqliteStorage(); store.initialize(world.toRealPath()); return store;
    }
    private Snapshot snapshot(SqliteStorage store, ActorId actor) {
        return store.transaction(tx -> TerminalPolicy.find(tx::query, store.snapshot().worldId(), anchor, actor,
                actor.equals(operator))).orElseThrow();
    }
    private JournalEngine engine(SqliteStorage store, Action action) {
        return new JournalEngine(store, (actor, kind) -> kind.equals(action.kind()), Map.of(action.kind(),
                TerminalPolicy.registration(action, store.snapshot().worldId(), catalog,
                        (a, intent, current, reads) -> live, (actor, reads) -> actor.equals(operator))));
    }
    private Outcome submit(SqliteStorage store, Action action, ActorId actor, CanonicalRequest request) {
        return engine(store, action).submit(intent(action, actor, request));
    }
    private static Intent intent(Action action, ActorId actor, CanonicalRequest request) {
        return new Intent(new OperationId(UUID.randomUUID()), new RequestId(UUID.randomUUID()),
                actor, action.kind(), request, null, null);
    }
}
