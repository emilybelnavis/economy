package dev.emily.economy.fx;

import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.api.Identifiers.*;
import dev.emily.economy.clock.EconomicClock;
import dev.emily.economy.journal.Journal;
import dev.emily.economy.journal.JournalEngine;
import dev.emily.economy.storage.StorageException;
import dev.emily.economy.storage.FoundationStore;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static dev.emily.economy.fx.FxFacts.*;

/** Worker-side journal handlers and bounded reads over immutable, indexed FX facts. */
public final class FxService {
    public static final String SYSTEM = "economy:fx";
    public static final Journal.Kind OPEN = new Journal.Kind(SYSTEM, "economy:fx-open");
    public static final Journal.Kind EDIT = new Journal.Kind(SYSTEM, "economy:fx-edit");
    static final long DAY_MILLIS = 86_400_000;
    private static final String PUBLICATIONS = "SELECT payload FROM journal_records WHERE record_type='" + PUBLICATION + "'";

    private FxService() {}

    public static EconomicClock.SystemDefinition definition() {
        return new EconomicClock.SystemDefinition(SYSTEM, DAY_MILLIS, 0, 1, Map.of(
                1, new EconomicClock.Version(1, "fx-daily-v1", update -> {
                    throw new IllegalStateException("FX requires detached publication inputs");
                }),
                2, new EconomicClock.Version(2, "fx-daily-v2-chf", update -> {
                    throw new IllegalStateException("FX requires detached publication inputs");
                })), Set.of());
    }

    public static EconomicClock.DeferredSystem deferred() {
        return new EconomicClock.DeferredSystem(reads -> origin(reads::query), FxService::prepare, reads -> {
            var opening = readPublication(reads::query, PUBLICATIONS + " ORDER BY " + DAY_KEY + " LIMIT 1", List.of());
            return opening == null ? 1 : opening.version();
        });
    }

    public static Long origin(JournalEngine.ReadView reads) {
        var opening = readPublication(reads, PUBLICATIONS + " ORDER BY " + DAY_KEY + " LIMIT 1", List.of());
        return opening == null ? null : opening.day().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    public static Publication latest(JournalEngine.ReadView reads) {
        return readPublication(reads, PUBLICATIONS + " ORDER BY " + DAY_KEY + " DESC LIMIT 1", List.of());
    }

    public static Publication publication(JournalEngine.ReadView reads, LocalDate day) {
        return readPublication(reads, PUBLICATIONS + " AND " + DAY_KEY + "=? LIMIT 1", List.of(dayKey(day)));
    }

    private static Publication readPublication(JournalEngine.ReadView reads, String sql, List<String> parameters) {
        var rows = reads.query(sql, parameters, 1);
        return rows.isEmpty() ? null : Publication.decode(rows.getFirst().get("payload"));
    }

    public static Policy policy(JournalEngine.ReadView reads, LocalDate day) {
        var rows = reads.query("SELECT payload FROM journal_records WHERE record_type='" + POLICY
                        + "' AND " + DAY_KEY + "<=? ORDER BY " + DAY_KEY + " DESC,sequence DESC LIMIT 1",
                List.of(dayKey(day)), 1);
        return rows.isEmpty() ? null : Policy.decode(rows.getFirst().get("payload"));
    }

    static Policy nextPolicy(JournalEngine.ReadView reads, LocalDate afterDay) {
        var rows = reads.query("SELECT " + DAY_KEY + " AS day FROM journal_records WHERE record_type='" + POLICY
                        + "' AND " + DAY_KEY + ">? ORDER BY " + DAY_KEY + " LIMIT 1",
                List.of(dayKey(afterDay)), 1);
        return rows.isEmpty() ? null : policy(reads, LocalDate.parse(rows.getFirst().get("day")));
    }

    private static EconomicClock.Calculation prepare(EconomicClock.Update update, EconomicClock.Queries reads) {
        if (update.version() != 1 && update.version() != 2) throw new IllegalArgumentException("Unsupported FX simulation version");
        LocalDate day = utcDay(Instant.ofEpochMilli(update.endMillis()));
        var previous = publication(reads::query, day.minusDays(1));
        var policy = policy(reads::query, day);
        if (previous == null || policy == null || previous.reference() != policy.reference()
                || publication(reads::query, day) != null) {
            throw new StorageException(StorageException.Kind.SCHEMA, "FX publication or policy checkpoint mismatch");
        }
        if (update.version() == 1 && (previous.version() != 1 || policy.version() != 1)
                || previous.version() != policy.version())
            throw new StorageException(StorageException.Kind.SCHEMA, "FX historical membership/version mismatch");
        return new Calculation(update, day, previous, policy);
    }

    private record Calculation(EconomicClock.Update update, LocalDate day, Publication previous, Policy policy)
            implements EconomicClock.Calculation {
        @Override
        public EconomicClock.Output compute() {
            var rates = FxMath.advance(previous.reference(), previous.rates(), policy.settings(), update.randomSeed());
            if (update.version() == 2 && previous.version() == 1) {
                var expandedRates = new EnumMap<EconomyCurrency, BigDecimal>(EconomyCurrency.class);
                expandedRates.putAll(rates);
                BigDecimal baseline = rates.get(EconomyCurrency.CAD).multiply(new BigDecimal("1.6"))
                        .setScale(FxMath.SCALE, RoundingMode.HALF_EVEN);
                var settings = new EnumMap<EconomyCurrency, FxMath.Settings>(EconomyCurrency.class);
                settings.putAll(policy.settings());
                settings.put(EconomyCurrency.CHF, new FxMath.Settings(baseline, 3_000, 100_000, 50_000));
                expandedRates.put(EconomyCurrency.CHF, baseline);
                UUID policyId = UUID.nameUUIDFromBytes(("economy:chf-opening-policy|" + update.id()).getBytes(StandardCharsets.UTF_8));
                var activation = new Policy(day, policyId, previous.reference(), settings);
                var next = new Publication(day, previous.reference(), update.id(), policyId, expandedRates);
                return new EconomicClock.Output(next.encode(), tx -> {}, List.of(activation.record(), next.record()));
            }
            var next = new Publication(day, previous.reference(), update.id(), policy.id(), rates);
            return new EconomicClock.Output(next.encode(), tx -> {}, List.of(next.record()));
        }
    }

    /** Trusted selection registration for the future chooser or an isolated fixture. */
    public static JournalEngine.Registration opening(Clock wall) {
        return new JournalEngine.Registration(1, (intent, reads) -> {
            if (latest(reads) != null) return reject("FX_ALREADY_OPEN");
            var day = utcDay(wall.instant());
            EconomyCurrency reference;
            try {
                reference = EconomyCurrency.fromCode(intent.request().fields().get("currency"));
            } catch (IllegalArgumentException invalid) {
                return reject("FX_INVALID_CURRENCY");
            }
            var id = intent.operationId().value();
            var policy = new Policy(day, id, reference, FxMath.defaults(reference));
            var publication = new Publication(day, reference, id, id, FxMath.opening(reference));
            return new JournalEngine.Commit(List.of(policy.record(), publication.record()),
                    new Journal.Result(1, "FX_OPENED", publication.encode()), tx -> {});
        });
    }

    /** Bind this registration only behind authenticated, server-derived admin authorization. */
    public static JournalEngine.Registration editing(Clock wall) {
        return new JournalEngine.Registration(1, (intent, reads) -> {
            var effectiveDay = utcDay(wall.instant()).plusDays(1);
            var current = latest(reads);
            if (current == null) return reject("FX_UNINITIALIZED");
            if (!effectiveDay.isAfter(current.day())) return reject("FX_BOUNDARY_PASSED");
            var previous = policy(reads, effectiveDay);
            if (previous == null || previous.reference() != current.reference()) {
                throw new StorageException(StorageException.Kind.SCHEMA, "FX policy checkpoint mismatch");
            }
            var fields = intent.request().fields();
            var settings = new EnumMap<>(previous.settings());
            try {
                var currency = EconomyCurrency.fromCode(fields.get("currency"));
                if (currency == current.reference()) return reject("FX_FIXED_REFERENCE");
                if (!current.rates().containsKey(currency)) return reject("FX_RATE_UNAVAILABLE");
                String baseline = fields.getOrDefault("baseline", "");
                if (!baseline.matches("[0-9]{1,7}(\\.[0-9]{1,12})?")) return reject("FX_INVALID_POLICY");
                settings.put(currency, new FxMath.Settings(new BigDecimal(baseline),
                        Integer.parseInt(fields.get("variation")), Integer.parseInt(fields.get("pull")),
                        Integer.parseInt(fields.get("bounds"))));
            } catch (IllegalArgumentException invalid) {
                return reject("FX_INVALID_POLICY");
            }
            var policy = new Policy(effectiveDay, intent.operationId().value(), current.reference(), settings);
            return new JournalEngine.Commit(List.of(policy.record()),
                    new Journal.Result(1, "FX_POLICY_SAVED", effectiveDay.toString()), tx -> {});
        });
    }

    /** Published availability, not registry membership; legacy days deliberately lack CHF. */
    public static List<EconomyCurrency> availableCurrencies(JournalEngine.ReadView reads) {
        var current = latest(reads);
        return current == null ? List.of() : EconomyCurrency.all().stream().filter(current.rates()::containsKey).toList();
    }

    /** After legacy backlog is current, install the CHF algorithm at a strictly future UTC boundary. */
    public static void prepareChfActivation(FoundationStore store, EconomicClock clock, Clock wall) {
        var state = store.transaction(tx -> {
            var current = latest(tx::query);
            if (current == null || current.version() == 2) return false;
            var scheduled = tx.query("SELECT version FROM economic_versions WHERE system_id=? AND version=2 LIMIT 1",
                    List.of(SYSTEM), 1);
            return scheduled.isEmpty();
        });
        if (state) {
            long boundary = utcDay(wall.instant()).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            clock.scheduleVersion(SYSTEM, 2, boundary);
        }
    }

    public static LocalDate chfActivationDay(JournalEngine.ReadView reads) {
        var opening = readPublication(reads, PUBLICATIONS + " ORDER BY " + DAY_KEY + " LIMIT 1", List.of());
        if (opening != null && opening.version() == 2) return opening.day();
        var rows = reads.query("SELECT effective_end_ms FROM economic_versions WHERE system_id=? AND version=2 ORDER BY effective_end_ms LIMIT 1",
                List.of(SYSTEM), 1);
        return rows.isEmpty() ? null : LocalDate.ofEpochDay(Long.parseLong(rows.getFirst().get("effective_end_ms")) / DAY_MILLIS);
    }

    private static JournalEngine.Reject reject(String code) {
        return new JournalEngine.Reject(new Journal.Result(1, code, ""));
    }

    private static LocalDate utcDay(Instant instant) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC);
    }
}
