package dev.emily.economy.journal;

import dev.emily.economy.api.*;
import dev.emily.economy.api.Identifiers.*;
import dev.emily.economy.storage.FoundationStore;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static dev.emily.economy.journal.Journal.*;
import static dev.emily.economy.journal.JournalEngine.*;
import static dev.emily.economy.journal.PostingBatch.Role.*;

/** Worker-side exact withdrawal quote, pre-debit intent and atomic financial/cash/custody commit. */
public final class Withdrawals {
    public static final String FACT = "economy:withdrawal";
    public static final int MAX_LINES = 24;
    public static final int MAX_STACKS = 36;
    private static final SystemActorId RECOVERY = new SystemActorId(
            UUID.fromString("225d3dad-73e3-4b30-a121-81df44fc8564"));

    public enum State { RESERVED, COMMITTED, CANCELLED, QUARANTINED }
    public enum Action {
        RESERVE, COMMIT, CANCEL, QUARANTINE;
        public Kind kind() { return new Kind("economy:withdrawals", "economy:" + name().toLowerCase(Locale.ROOT)); }
    }
    public record Binding(UUID account, long accountRevision, EconomyCurrency currency, TerminalPolicy.Anchor terminal,
                          UUID generation, long terminalRevision, String fxRevision) {
        public Binding {
            Objects.requireNonNull(account); Objects.requireNonNull(currency); Objects.requireNonNull(terminal);
            Objects.requireNonNull(generation); bounded(fxRevision, 256);
            if (accountRevision < 1 || terminalRevision < 1) throw invalid("WITHDRAWAL_REVISION");
        }
    }
    public record FxSnapshot(String revision, CurrencyConversion.Rates rates) {
        public FxSnapshot { bounded(revision, 256); Objects.requireNonNull(rates); }
    }
    public record Preview(Binding binding, Money cash, boolean edited, Map<String,Integer> billEdits,
                          WithdrawalPlanner.Plan plan, Money debit) {
        public Preview {
            Objects.requireNonNull(binding); Objects.requireNonNull(cash); Objects.requireNonNull(plan); Objects.requireNonNull(debit);
            billEdits = Collections.unmodifiableMap(new TreeMap<>(billEdits));
            if ((!edited && !billEdits.isEmpty()) || cash.minorUnits() <= 0 || plan.currency() != cash.currency()
                    || plan.minorUnits() != cash.minorUnits() || debit.currency() != binding.currency()
                    || debit.minorUnits() <= 0 || debit.minorUnits() % binding.currency().minorUnitsPerMajor() != 0)
                throw invalid("WITHDRAWAL_PREVIEW");
            validatePlan(plan);
        }
    }
    public record Reservation(UUID id, ActorId owner, State state, Preview preview,
                              String evidence, String reason, long revision) {}
    @FunctionalInterface public interface FxReader { FxSnapshot current(ReadView reads); }
    @FunctionalInterface public interface Authority {
        boolean allows(Action action, Intent intent, Reservation current, ReadView reads);
    }
    /** Requires full output capacity at confirmation and again immediately before monetary admission. */
    @FunctionalInterface public interface Capacity {
        boolean fits(Action action, Intent intent, Reservation current, ReadView reads);
    }
    /** Composes the intended delivery and quantity locks into the same commit as issuance and debit. */
    @FunctionalInterface public interface Delivery {
        Commit prepare(Intent intent, Reservation current, List<CashIssuance.Lot> lots, ReadView reads) throws Exception;
    }

    private Withdrawals() {}

    public static Preview preview(ReadView reads, WorldId world, ActorId owner, Binding binding, Money cash,
                                  Map<String,Integer> billEdits, Map<String,WithdrawalPlanner.Denomination> catalog,
                                  FxReader fx) {
        var result = calculate(reads, world, owner, binding, cash, billEdits, Map.copyOf(catalog), fx);
        var id = new UUID(0, 0);
        new Intent(new OperationId(id), new RequestId(id), owner, Action.RESERVE.kind(), reserveRequest(result), null, null);
        return result;
    }

    private static Preview calculate(ReadView reads, WorldId world, ActorId owner, Binding binding, Money cash,
                                     Map<String,Integer> edits, Map<String,WithdrawalPlanner.Denomination> catalog,
                                     FxReader fx) {
        if (!(owner instanceof PlayerId player)) throw invalid("WITHDRAWAL_ACCOUNT");
        if (reads.query("""
                SELECT account_id FROM account_catalog WHERE account_id=? AND owner_id=? AND purpose='BANK'
                AND currency=? AND status='OPEN' AND revision=?
                """, List.of(binding.account().toString(), player.value().toString(), binding.currency().name(),
                Long.toString(binding.accountRevision())), 1).isEmpty()) throw invalid("WITHDRAWAL_ACCOUNT_STALE");
        var terminal = TerminalPolicy.find(reads, world, binding.terminal(), owner, true)
                .orElseThrow(() -> invalid("WITHDRAWAL_TERMINAL"));
        if (!terminal.active() || !terminal.generation().equals(binding.generation())
                || terminal.revision() != binding.terminalRevision()) throw invalid("WITHDRAWAL_TERMINAL_STALE");
        if (terminal.kind() == TerminalPolicy.Kind.REGISTER || !terminal.settings().mode().withdrawals())
            throw invalid("WITHDRAWAL_MODE");
        if (!terminal.settings().currencies().contains(cash.currency())) throw invalid("WITHDRAWAL_CURRENCY");
        var publication = fx.current(reads);
        if (publication == null || !publication.revision().equals(binding.fxRevision())) throw invalid("WITHDRAWAL_FX_STALE");
        var denominations = new ArrayList<WithdrawalPlanner.Denomination>();
        for (var id : terminal.settings().denominations()) {
            var denomination = catalog.get(id);
            if (denomination == null) throw invalid("WITHDRAWAL_DENOMINATION");
            if (denomination.currency() == cash.currency()) denominations.add(denomination);
        }
        boolean atm = terminal.kind() == TerminalPolicy.Kind.ATM;
        var plan = WithdrawalPlanner.plan(cash.currency(), cash.minorUnits(), denominations, atm, edits);
        var raw = CurrencyConversion.withdrawalDebit(cash, binding.currency(), publication.rates()).minorUnits();
        long quantum = binding.currency().minorUnitsPerMajor();
        long debit = Math.multiplyExact(Math.addExact(raw, quantum - 1) / quantum, quantum);
        return new Preview(binding, cash, edits != null, edits == null ? Map.of() : edits, plan, new Money(binding.currency(), debit));
    }

    public static CanonicalRequest reserveRequest(Preview preview) {
        var fields = new TreeMap<String,String>();
        fields.put("binding", encodeBinding(preview.binding()));
        fields.put("cash", preview.cash().currency() + "|" + preview.cash().minorUnits());
        fields.put("edited", Boolean.toString(preview.edited()));
        fields.put("edits", encodeCounts(preview.billEdits()));
        fields.put("plan", encodePlan(preview.plan()));
        fields.put("debit", Long.toString(preview.debit().minorUnits()));
        return new CanonicalRequest(1, fields);
    }
    public static CanonicalRequest request(UUID reservation, String evidence, String reason) {
        Objects.requireNonNull(reservation); bounded(evidence, 256); bounded(reason, 256);
        return new CanonicalRequest(1, Map.of("reservation", reservation.toString(), "evidence", evidence, "reason", reason));
    }
    public static Registration registration(Action action, WorldId world,
            Map<String,WithdrawalPlanner.Denomination> catalog, FxReader fx,
            Authority authority, Capacity capacity, Delivery delivery) {
        Objects.requireNonNull(action); Objects.requireNonNull(world); Objects.requireNonNull(fx);
        Objects.requireNonNull(authority); Objects.requireNonNull(capacity); Objects.requireNonNull(delivery);
        var immutable = Map.copyOf(catalog);
        return new Registration(1, (intent, reads) -> prepare(action, world, immutable, fx, authority, capacity, delivery, intent, reads));
    }

    private static Decision prepare(Action action, WorldId world, Map<String,WithdrawalPlanner.Denomination> catalog,
                                    FxReader fx, Authority authority, Capacity capacity, Delivery delivery,
                                    Intent intent, ReadView reads) throws Exception {
        if (!intent.kind().equals(action.kind())) return reject("WITHDRAWAL_REQUEST");
        if (action == Action.RESERVE) {
            Preview actual;
            try {
                var supplied = decodeRequest(intent.request());
                actual = calculate(reads, world, intent.actor(), supplied.binding(), supplied.cash(),
                        supplied.edited() ? supplied.billEdits() : null, catalog, fx);
                if (!reserveRequest(actual).equals(intent.request())) return reject("WITHDRAWAL_PREVIEW");
            } catch (IllegalArgumentException | ArithmeticException | NullPointerException invalid) {
                return rejected(invalid);
            }
            var reservation = new Reservation(intent.operationId().value(), intent.actor(), State.RESERVED, actual, "pending", "reserved", 1);
            if (!authority.allows(action, intent, reservation, reads)) return decline("WITHDRAWAL_ACCESS");
            if (!capacity.fits(action, intent, reservation, reads)) return decline("WITHDRAWAL_CAPACITY");
            return commit(action, reservation, null, true);
        }
        UUID id; String evidence; String reason;
        try {
            var f = intent.request().fields();
            id = UUID.fromString(f.get("reservation")); evidence = f.get("evidence"); reason = f.get("reason");
            if (!request(id, evidence, reason).equals(intent.request())) return reject("WITHDRAWAL_REQUEST");
        } catch (IllegalArgumentException | NullPointerException malformed) { return reject("WITHDRAWAL_REQUEST"); }
        var current = raw(reads, id).orElse(null);
        if (current == null || (action != Action.QUARANTINE && !current.owner().equals(intent.actor()))
                || !authority.allows(action, intent, current, reads)) return decline("WITHDRAWAL_ACCESS");
        if (current.state() != State.RESERVED) return reject("WITHDRAWAL_STATE");
        Commit composed = null;
        State next;
        if (action == Action.CANCEL) next = State.CANCELLED;
        else if (action == Action.QUARANTINE) next = State.QUARANTINED;
        else if (action == Action.COMMIT) {
            try {
                var checked = calculate(reads, world, current.owner(), current.preview().binding(), current.preview().cash(),
                        current.preview().edited() ? current.preview().billEdits() : null, catalog, fx);
                if (!reserveRequest(checked).equals(reserveRequest(current.preview()))) return reject("WITHDRAWAL_STALE");
            } catch (IllegalArgumentException | ArithmeticException stale) { return rejected(stale); }
            if (!capacity.fits(action, intent, current, reads)) return decline("WITHDRAWAL_CAPACITY");
            var debit = current.preview().debit();
            var legs = List.of(new PostingBatch.Leg(current.preview().binding().account(), ACCOUNT, debit.currency(), debit.negate()),
                    new PostingBatch.Leg(Settlement.issuanceAccount(debit.currency()), ISSUANCE, debit.currency(), debit));
            var decision = Settlement.prepare(reads, legs, PostingBatch.Authority.REDEEM, null);
            if (!(decision instanceof Commit financial)) {
                if (decision instanceof Reject rejection && rejection.result().code().equals("INSUFFICIENT_FUNDS"))
                    return new Retry(rejection.result());
                return decision;
            }
            var lots = lots(current, world);
            var issuance = CashIssuance.composeIssuance(world, lots, financial, reads);
            if (!(issuance instanceof Commit issued)) return issuance;
            var intended = delivery.prepare(intent, current, lots, reads);
            if (intended == null) return decline("WITHDRAWAL_DELIVERY");
            var records = new ArrayList<>(issued.records());
            records.addAll(intended.records());
            composed = new Commit(records, issued.result(), tx -> {
                issued.projections().apply(tx);
                intended.projections().apply(tx);
            });
            next = State.COMMITTED;
        } else return reject("WITHDRAWAL_REQUEST");
        var changed = new Reservation(current.id(), current.owner(), next, current.preview(), evidence, reason,
                Math.addExact(current.revision(), 1));
        return commit(action, changed, composed, false);
    }

    /** Stable issuance identities are independent of retry attempt IDs. */
    public static List<CashIssuance.Lot> lots(Reservation reservation, WorldId world) {
        var values = new ArrayList<CashIssuance.Lot>();
        for (var line : reservation.preview().plan().lines()) {
            var id = UUID.nameUUIDFromBytes(("economy:withdrawal:" + reservation.id() + ":" + line.denomination().id())
                    .getBytes(StandardCharsets.UTF_8));
            values.add(new CashIssuance.Lot(world, id, line.denomination().id(), line.count(), line.count(),
                    CashIssuance.State.ACTIVE, 1, "withdrawal:" + reservation.id()));
        }
        return List.copyOf(values);
    }

    private static Commit commit(Action action, Reservation value, Commit base, boolean insert) {
        var records = new ArrayList<Journal.Record>();
        if (base != null) records.addAll(base.records());
        records.add(new Journal.Record(FACT, 1, encode(action, value)));
        return new Commit(records, new Result(1, "WITHDRAWAL_" + action.name(), value.id().toString()), tx -> {
            if (base != null) base.projections().apply(tx);
            if (insert) tx.execute("INSERT INTO withdrawals VALUES(?,?,?,?,?,?,?)", values(value));
            else {
                int changed = tx.execute("UPDATE withdrawals SET state=?,preview=?,evidence=?,reason=?,revision=? WHERE id=? AND revision=?",
                        List.of(value.state().name(), encodePreview(value.preview()), value.evidence(), value.reason(),
                                Long.toString(value.revision()), value.id().toString(), Long.toString(value.revision() - 1)));
                if (changed != 1) throw new IllegalStateException("Withdrawal revision changed");
            }
        });
    }

    public static Optional<Reservation> find(ReadView reads, ActorId actor, UUID id) {
        return raw(reads, id).filter(value -> value.owner().equals(actor));
    }
    public static boolean reconcileBatch(FoundationStore store) {
        var rows = store.transaction(tx -> tx.query("SELECT id,revision FROM withdrawals WHERE state='RESERVED' ORDER BY id LIMIT 16",
                List.of(), 16));
        var action = Action.QUARANTINE;
        var engine = new JournalEngine(store, (actor, kind) -> actor.equals(RECOVERY) && kind.equals(action.kind()),
                Map.of(action.kind(), registration(action, store.snapshot().worldId(), Map.of(), reads -> null,
                        (a, intent, current, reads) -> intent.actor().equals(RECOVERY),
                        (a, intent, current, reads) -> false, (intent, current, lots, reads) -> null)));
        for (var row : rows) {
            var id = UUID.fromString(row.get("id"));
            var op = UUID.nameUUIDFromBytes(("economy:withdrawal-recovery:" + id + ":" + row.get("revision")).getBytes(StandardCharsets.UTF_8));
            var result = engine.submit(new Intent(new OperationId(op), new RequestId(UUID.randomUUID()), RECOVERY,
                    action.kind(), request(id, "startup-unknown", "reserved delivery requires reconciliation"), null, null));
            if (result.status() != Status.COMMITTED) throw new IllegalStateException("Withdrawal reconciliation failed");
        }
        return store.transaction(tx -> tx.query("SELECT id FROM withdrawals WHERE state='RESERVED' LIMIT 1", List.of(), 1).isEmpty());
    }

    private static Optional<Reservation> raw(ReadView reads, UUID id) {
        return reads.query("SELECT * FROM withdrawals WHERE id=?", List.of(id.toString()), 1).stream().map(Withdrawals::read).findFirst();
    }
    private static Reservation read(Map<String,String> row) {
        return new Reservation(UUID.fromString(row.get("id")), actor(row.get("owner")), State.valueOf(row.get("state")),
                decodePreview(row.get("preview")), row.get("evidence"), row.get("reason"), Long.parseLong(row.get("revision")));
    }
    private static List<String> values(Reservation value) {
        return List.of(value.id().toString(), actorKey(value.owner()), value.state().name(), encodePreview(value.preview()),
                value.evidence(), value.reason(), Long.toString(value.revision()));
    }
    private static void validatePlan(WithdrawalPlanner.Plan plan) {
        if (plan.lines().isEmpty() || plan.lines().size() > MAX_LINES) throw invalid("WITHDRAWAL_PLAN_LIMIT");
        long sum = 0; int items = 0; int stacks = 0;
        var ids = new HashSet<String>();
        var bills = new TreeMap<String,Integer>(); var coins = new TreeMap<String,Integer>();
        for (var line : plan.lines()) {
            var d = line.denomination();
            if (!ids.add(d.id()) || d.currency() != plan.currency() || line.count() < 1 || line.count() > CashIssuance.MAX_ISSUED)
                throw invalid("WITHDRAWAL_PLAN");
            sum = Math.addExact(sum, Math.multiplyExact(d.minorUnits(), line.count()));
            items = Math.addExact(items, line.count()); stacks = Math.addExact(stacks, (line.count() + 63) / 64);
            (d.bill() ? bills : coins).put(d.id(), line.count());
        }
        if (sum != plan.minorUnits() || items != plan.itemCount() || !bills.equals(plan.bills()) || !coins.equals(plan.coins()))
            throw invalid("WITHDRAWAL_PLAN");
        if (stacks > MAX_STACKS) throw invalid("WITHDRAWAL_STACK_LIMIT");
    }
    private static String encodeBinding(Binding b) {
        var a = b.terminal();
        return b.account() + "|" + b.accountRevision() + "|" + b.currency() + "|" + a.dimension() + "|" + a.x() + "|" + a.y()
                + "|" + a.z() + "|" + b.generation() + "|" + b.terminalRevision() + "|" + pack(b.fxRevision());
    }
    private static Binding decodeBinding(String value) {
        var p = value.split("\\|", -1);
        if (p.length != 10) throw invalid("WITHDRAWAL_BINDING");
        return new Binding(UUID.fromString(p[0]), Long.parseLong(p[1]), EconomyCurrency.valueOf(p[2]),
                new TerminalPolicy.Anchor(p[3], Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6])),
                UUID.fromString(p[7]), Long.parseLong(p[8]), unpack(p[9]));
    }
    private static String encodeCounts(Map<String,Integer> values) {
        return values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "|" + e.getValue()).reduce((a,b) -> a + "\n" + b).orElse("");
    }
    private static Map<String,Integer> decodeCounts(String value) {
        var result = new TreeMap<String,Integer>();
        if (value.isEmpty()) return result;
        for (var row : value.split("\n", -1)) {
            var p = row.split("\\|", -1);
            if (p.length != 2 || result.put(p[0], Integer.parseInt(p[1])) != null) throw invalid("WITHDRAWAL_EDITS");
        }
        return result;
    }
    private static String encodePlan(WithdrawalPlanner.Plan plan) {
        return plan.lines().stream().map(line -> {
            var d = line.denomination();
            return d.id() + "|" + d.currency() + "|" + d.minorUnits() + "|" + d.bill() + "|" + line.count();
        }).reduce((a,b) -> a + "\n" + b).orElse("");
    }
    private static Preview decodeRequest(CanonicalRequest request) {
        var f = request.fields();
        if (!f.keySet().equals(Set.of("binding","cash","edited","edits","plan","debit"))) throw invalid("WITHDRAWAL_REQUEST");
        var binding = decodeBinding(f.get("binding"));
        var cash = f.get("cash").split("\\|", -1);
        if (cash.length != 2) throw invalid("WITHDRAWAL_CASH");
        var money = new Money(EconomyCurrency.valueOf(cash[0]), Long.parseLong(cash[1]));
        var edits = decodeCounts(f.get("edits"));
        if (!f.get("edited").equals("true") && !f.get("edited").equals("false")) throw invalid("WITHDRAWAL_EDITS");
        var lines = new ArrayList<WithdrawalPlanner.Line>();
        var bills = new TreeMap<String,Integer>(); var coins = new TreeMap<String,Integer>(); int count = 0;
        for (var row : f.get("plan").split("\n", -1)) {
            var p = row.split("\\|", -1);
            if (p.length != 5 || (!p[3].equals("true") && !p[3].equals("false"))) throw invalid("WITHDRAWAL_PLAN");
            var d = new WithdrawalPlanner.Denomination(p[0], EconomyCurrency.valueOf(p[1]), Long.parseLong(p[2]), Boolean.parseBoolean(p[3]));
            int quantity = Integer.parseInt(p[4]); count = Math.addExact(count, quantity);
            lines.add(new WithdrawalPlanner.Line(d, quantity));
            (d.bill() ? bills : coins).put(d.id(), quantity);
        }
        var plan = new WithdrawalPlanner.Plan(money.currency(), money.minorUnits(), bills, coins, lines, count);
        var result = new Preview(binding, money, Boolean.parseBoolean(f.get("edited")), edits, plan,
                new Money(binding.currency(), Long.parseLong(f.get("debit"))));
        if (!reserveRequest(result).equals(request)) throw invalid("WITHDRAWAL_REQUEST");
        return result;
    }
    private static String encodePreview(Preview preview) {
        return reserveRequest(preview).fields().entrySet().stream()
                .map(e -> e.getKey() + "=" + pack(e.getValue())).reduce((a,b) -> a + "\n" + b).orElse("");
    }
    private static Preview decodePreview(String value) {
        var fields = new TreeMap<String,String>();
        for (var row : value.split("\n", -1)) {
            int split = row.indexOf('=');
            if (split < 1 || fields.put(row.substring(0, split), unpack(row.substring(split + 1))) != null)
                throw invalid("WITHDRAWAL_PREVIEW");
        }
        return decodeRequest(new CanonicalRequest(1, fields));
    }
    private static String encode(Action action, Reservation value) {
        return action + "|" + value.id() + "|" + pack(actorKey(value.owner())) + "|" + value.state()
                + "|" + pack(encodePreview(value.preview())) + "|" + pack(value.evidence()) + "|" + pack(value.reason()) + "|" + value.revision();
    }
    public static void validateFact(String value) {
        var p = value.split("\\|", -1);
        if (p.length != 8) throw invalid("WITHDRAWAL_FACT");
        Action.valueOf(p[0]); UUID.fromString(p[1]); actor(unpack(p[2])); State.valueOf(p[3]);
        decodePreview(unpack(p[4])); bounded(unpack(p[5]), 256); bounded(unpack(p[6]), 256);
        if (Long.parseLong(p[7]) < 1) throw invalid("WITHDRAWAL_REVISION");
    }
    private static String pack(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String unpack(String value) { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
    private static void bounded(String value, int max) {
        if (value == null || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > max)
            throw invalid("WITHDRAWAL_FIELD");
    }
    private static IllegalArgumentException invalid(String code) { return new IllegalArgumentException(code); }
    private static Decision rejected(RuntimeException error) {
        var code = error.getMessage();
        return reject(code != null && (code.startsWith("WITHDRAWAL_") || code.startsWith("WITHDRAW_")) ? code : "WITHDRAWAL_REQUEST");
    }
    private static Reject reject(String code) { return new Reject(new Result(1, code, "")); }
    private static Decline decline(String code) { return new Decline(new Result(1, code, "")); }
}
