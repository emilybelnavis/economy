package dev.emily.economy.foundation;

import dev.emily.economy.Economy;
import dev.emily.economy.account.AccountLifecycle;
import dev.emily.economy.api.*;
import dev.emily.economy.api.Identifiers.*;
import dev.emily.economy.banking.BankingMenu;
import dev.emily.economy.banking.BankingSaves;
import dev.emily.economy.cash.CashCatalog;
import dev.emily.economy.fx.FxQuotes;
import dev.emily.economy.journal.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Authenticated owner views and noncash banking actions. No transport field chooses the actor or world. */
public final class BankingAccounts {
    private static final String PENDING = "economy_banking_pending";
    private static final Set<String> ACTIONS = Set.of("setup", "accounts", "accounts_next", "account_select", "account_open",
            "history", "history_next", "fx", "exchange_preview", "exchange_confirm", "settings", "configure", "bank_retry");
    private static final Map<BankingMenu, State> STATES = new WeakHashMap<>();
    private static final class State {
        AccountId selected;
        String accountCursor;
        String historyCursor;
        Map<UUID, AccountIdentity> offered = Map.of();
        FxSettlement.Exchange exchange;
        FxQuotes.Quote quote;
        Function<PartyAuthorization.Grant, JournalService.Submission> retry;
    }
    private record Audience(ServerPlayer player, BankingMenu menu, UUID session, long revision) {
        boolean live() { return menu.valid(player) && menu.session().equals(session) && menu.revision() == revision; }
    }
    private BankingAccounts() {}
    public static boolean handles(String action) { return ACTIONS.contains(action); }
    public static AccountId selected(BankingMenu menu) { var state = STATES.get(menu); return state == null ? null : state.selected; }
    public static void closed(BankingMenu menu) { STATES.remove(menu); }

    public static void action(ServerPlayer player, BankingMenu menu, String action, String value) {
        if (!handles(action) || !menu.valid(player) || menu.busy()) return;
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > 2048) return;
        var services = Economy.serverServices(player.server);
        if (services == null || services.state() != ServerServices.State.READY) return;
        var grant = grant(player, services);
        if (grant == null) return;
        var state = STATES.computeIfAbsent(menu, ignored -> new State());
        var audience = new Audience(player, menu, menu.session(), menu.revision());
        try {
            switch (action) {
                case "setup" -> setup(audience, services, grant, state);
                case "accounts", "accounts_next" -> accounts(audience, services, grant, state, action.equals("accounts_next"));
                case "account_select" -> select(audience, services, grant, state, value);
                case "account_open" -> open(audience, services, grant, state, value);
                case "history", "history_next" -> history(audience, services, grant, state, action.equals("history_next"));
                case "fx" -> fx(audience, services);
                case "exchange_preview" -> exchangePreview(audience, services, grant, state, value);
                case "exchange_confirm" -> exchangeConfirm(audience, services, grant, state);
                case "settings" -> settings(audience, services);
                case "configure" -> configure(audience, services, grant, state, value);
                case "bank_retry" -> retry(audience, services, grant, state);
                default -> { }
            }
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException invalid) {
            reply(audience, menu.page(), List.of("The request was not submitted. Check the values and current terminal."), "Invalid banking request");
        }
    }
    private static PartyAuthorization.Grant grant(ServerPlayer player, ServerServices services) {
        var world = services.partyWorld();
        if (world == null) return null;
        var target = new PartyRef(world, new PartyRef.Player(new PlayerId(player.getUUID())));
        return PartyAuthorization.player(player, Economy::serverServices, target).orElse(null);
    }
    private static void setup(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state) {
        var request = new Journal.CanonicalRequest(1, Map.of());
        var op = new OperationId(UUID.randomUUID()); var attempt = new RequestId(UUID.randomUUID());
        var kind = dev.emily.economy.account.AccountCatalog.INITIALIZE_PRIMARY;
        start(audience, services, grant, state, ownedIntent(grant, kind, op, attempt, request),
                fresh -> services.initializePrimary(fresh, op, attempt, null), "setup");
    }
    private static void accounts(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, boolean next) {
        if (next && state.accountCursor == null) { reply(audience, "accounts", List.of("There are no more accounts."), "End of accounts"); return; }
        if (!next) state.accountCursor = null;
        audience.menu().setBusy(true);
        services.listOwnedAccounts(grant, state.accountCursor, 10).whenComplete((found, error) -> {
            if (!audience.live()) return;
            if (error != null || found.isEmpty()) { unavailable(audience, "accounts"); return; }
            var page = found.orElseThrow();
            var offered = new LinkedHashMap<UUID, AccountIdentity>();
            var lines = new ArrayList<String>();
            int number = 1;
            for (var account : page.accounts()) {
                if (account.purpose() != AccountIdentity.Purpose.BANK) continue;
                offered.put(account.id().value(), account);
                lines.add(number++ + ". " + account.currency().code() + (account.primary() ? " primary" : " bank") + " — " + account.status().name().toLowerCase(Locale.ROOT));
                lines.add(account.id().value().toString());
            }
            state.offered = Collections.unmodifiableMap(offered);
            state.accountCursor = page.nextCursor();
            if (lines.isEmpty()) lines.add("No bank account yet. Choose Setup to create your primary account.");
            lines.add("Select an account by its number or ID. Open another currency at a teller.");
            reply(audience, "accounts", lines, state.accountCursor == null ? "All accounts shown" : "More accounts available");
        });
    }
    private static AccountIdentity offered(State state, String value) {
        var text = value.trim();
        if (text.matches("[1-9][0-9]?")) {
            int index = Integer.parseInt(text) - 1;
            var accounts = List.copyOf(state.offered.values());
            if (index >= accounts.size()) throw new IllegalArgumentException("Account selection");
            return accounts.get(index);
        }
        var account = state.offered.get(UUID.fromString(text));
        if (account == null) throw new IllegalArgumentException("Account not offered");
        return account;
    }
    private static void select(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, String value) {
        var selected = offered(state, value);
        state.selected = selected.id(); state.historyCursor = null; state.exchange = null; state.quote = null;
        audience.menu().setBusy(true);
        services.findOwnedAccount(grant, selected.id()).thenCompose(found -> {
            if (found.isEmpty()) throw new IllegalArgumentException("Account unavailable");
            return services.ledger().funds(grant, selected.id());
        }).whenComplete((funds, error) -> {
            if (!audience.live()) return;
            if (error != null || funds.isEmpty()) { unavailable(audience, "accounts"); return; }
            var valueFunds = funds.orElseThrow();
            reply(audience, "accounts", List.of("Selected " + selected.currency().code() + " account", selected.id().value().toString(),
                    "Available: " + money(new Money(valueFunds.currency(), valueFunds.availableMinorUnits())),
                    "Reserved: " + money(new Money(valueFunds.currency(), valueFunds.reservedMinorUnits())),
                    "Choose History, Deposit, Withdraw or FX."), "Account selected");
        });
    }
    private static void open(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, String value) {
        var currency = EconomyCurrency.fromCode(value.trim().toUpperCase(Locale.ROOT));
        audience.menu().setBusy(true);
        services.ledger().bankingContext(new PlayerId(audience.player().getUUID()), audience.menu().anchor(), null).whenComplete((context, error) -> {
            if (!audience.live()) return;
            if (error != null || context.account() == null || context.terminal() == null || context.terminal().kind() != TerminalPolicy.Kind.TELLER) {
                reply(audience, "accounts", List.of("Create a primary account first, then visit a teller to open another currency."), "Teller required"); return;
            }
            var open = new AccountLifecycle.Open(currency, context.account().revision());
            var operation = new OperationId(UUID.randomUUID()); var attempt = new RequestId(UUID.randomUUID());
            start(audience, services, grant, state, ownedIntent(grant, AccountLifecycle.OPEN, operation, attempt, open.request()),
                    fresh -> services.submitOwned(fresh, AccountLifecycle.OPEN, operation, attempt, open.request(),
                            AccountLifecycle.opening(fresh.target(), open), null), "accounts");
        });
    }
    private static void history(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, boolean next) {
        if (next && state.historyCursor == null) { reply(audience, "history", List.of("There are no more entries."), "End of history"); return; }
        if (!next) state.historyCursor = null;
        audience.menu().setBusy(true);
        services.ledger().bankingContext(new PlayerId(audience.player().getUUID()), audience.menu().anchor(), state.selected).whenComplete((context, contextError) -> {
            if (!audience.live()) return;
            if (contextError != null || context.account() == null) { unavailable(audience, "history"); return; }
            services.ledger().history(grant, context.account().id(), state.historyCursor, 8).whenComplete((found, error) -> {
                if (!audience.live()) return;
                if (error != null || found.isEmpty()) { unavailable(audience, "history"); return; }
                var page = found.orElseThrow(); state.historyCursor = page.nextCursor();
                var lines = new ArrayList<String>();
                lines.add("History for " + context.account().currency().code() + " " + context.account().id().value());
                for (var entry : page.entries()) {
                    lines.add("#" + entry.sequence().value() + " " + money(entry.amount()));
                    lines.add("Transaction " + entry.transactionId().value());
                }
                if (page.entries().isEmpty()) lines.add("No transactions yet.");
                reply(audience, "history", lines, state.historyCursor == null ? "All entries shown" : "More history available");
            });
        });
    }
    private static void fx(Audience audience, ServerServices services) {
        audience.menu().setBusy(true);
        services.ledger().bankingContext(new PlayerId(audience.player().getUUID()), audience.menu().anchor(), null).whenComplete((context, error) -> {
            if (!audience.live()) return;
            if (error != null || context.fx() == null) { unavailable(audience, "fx"); return; }
            var lines = new ArrayList<String>(); var rates = context.fx().rates();
            lines.add("Current rates in " + rates.reference().code() + " per currency unit");
            for (var currency : EconomyCurrency.all()) lines.add("1 " + currency.code() + " = " + rates.perMajorUnit().get(currency).toPlainString() + " " + rates.reference().code());
            lines.add("To exchange: select a source account, then enter a target account number or ID and a whole-unit amount.");
            lines.add("Example: 2 10");
            reply(audience, "fx", lines, "Rates from the current world publication");
        });
    }
    private static void exchangePreview(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, String value) {
        var fields = value.trim().split("\\s+", -1);
        if (fields.length != 2) throw new IllegalArgumentException("Exchange input");
        var target = offered(state, fields[0]);
        long units = Long.parseLong(fields[1]);
        if (units < 1) throw new IllegalArgumentException("Exchange amount");
        state.exchange = null; state.quote = null;
        audience.menu().setBusy(true);
        services.ledger().bankingContext(new PlayerId(audience.player().getUUID()), audience.menu().anchor(), state.selected).whenComplete((context, error) -> {
            if (!audience.live()) return;
            if (error != null || context.account() == null) { unavailable(audience, "fx"); return; }
            var source = context.account();
            services.findOwnedAccount(grant, target.id()).whenComplete((found, targetError) -> {
                if (!audience.live()) return;
                if (targetError != null || found.isEmpty()) { unavailable(audience, "fx"); return; }
                var destination = found.orElseThrow();
                try {
                    if (source.status() != AccountIdentity.Status.OPEN || destination.status() != AccountIdentity.Status.OPEN
                            || source.currency() == destination.currency()) throw new IllegalArgumentException("Exchange accounts");
                    var amount = new Money(source.currency(), Math.multiplyExact(units, source.currency().minorUnitsPerMajor()));
                    services.ledger().quote(grant, new RequestId(UUID.randomUUID()), new FxQuotes.Pair(source.currency(), destination.currency()),
                            FxQuotes.Side.EXACT_DEBIT, amount).whenComplete((quote, quoteError) -> {
                        if (!audience.live()) return;
                        if (quoteError != null) { unavailable(audience, "fx"); return; }
                        state.quote = quote;
                        state.exchange = new FxSettlement.Exchange(quote.id(), new PlayerId(audience.player().getUUID()),
                                new SameCurrency.Revision(source.id(), source.revision()), new SameCurrency.Revision(destination.id(), destination.revision()));
                        long credited = quote.credit().minorUnits() - quote.credit().minorUnits() % quote.credit().currency().minorUnitsPerMajor();
                        reply(audience, "fx", List.of("Debit " + money(quote.debit()), "Credit " + money(new Money(quote.credit().currency(), credited)),
                                "Fee " + money(quote.fee()), "Quote expires " + quote.expires(), "Choose Confirm exchange before this quote expires."), "Exchange preview");
                    });
                } catch (IllegalArgumentException | ArithmeticException invalid) { reply(audience, "fx", List.of("Choose two open accounts in different currencies and a positive whole-unit amount."), "Invalid exchange"); }
            });
        });
    }
    private static void exchangeConfirm(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state) {
        if (state.exchange == null || state.quote == null || !audience.menu().page().equals("fx")) throw new IllegalArgumentException("Preview required");
        var exchange = state.exchange;
        var operation = new OperationId(UUID.randomUUID()); var attempt = new RequestId(UUID.randomUUID());
        var intent = new Journal.Intent(operation, attempt, grant.actor(), FxSettlement.EXCHANGE, exchange.request(), null, null);
        start(audience, services, grant, state, intent, fresh -> services.ledger().exchange(fresh, operation, attempt, exchange), "fx");
        state.exchange = null; state.quote = null;
    }
    private static void settings(Audience audience, ServerServices services) {
        audience.menu().setBusy(true);
        var actor = new PlayerId(audience.player().getUUID()); boolean operator = audience.player().hasPermissions(2);
        services.ledger().terminalSettings(actor, (who, reads) -> operator, audience.menu().anchor()).whenComplete((found, error) -> {
            if (!audience.live()) return;
            if (error != null || found.isEmpty()) { reply(audience, "settings", List.of("Only this terminal's owner or an operator may edit its settings."), "Owner access required"); return; }
            var terminal = found.orElseThrow();
            var lines = new ArrayList<String>();
            lines.add("Mode: " + terminal.settings().mode()); lines.add("Allowed currencies: " + terminal.settings().currencies());
            lines.add("Allowed cash types: " + terminal.settings().denominations().size());
            lines.add("Enter MODE;CURRENCIES;CASH TYPES"); lines.add("Example: BOTH;CAD,USD;all");
            lines.add("Modes: BOTH, DEPOSIT, WITHDRAW, DISABLED");
            lines.add("Use a comma-separated cash item list instead of all to restrict denominations.");
            lines.add("Registers use DISABLED cash mode. Set their product price from Shop.");
            reply(audience, "settings", lines, "Terminal revision " + terminal.revision());
        });
    }
    private static void configure(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, String value) {
        var actor = new PlayerId(audience.player().getUUID());
        boolean operator = audience.player().hasPermissions(2);
        audience.menu().setBusy(true);
        services.ledger().terminalSettings(actor, (who, reads) -> operator, audience.menu().anchor()).whenComplete((found, error) -> {
            if (!audience.live()) return;
            if (error != null || found.isEmpty()) { reply(audience, "settings", List.of("Only the owner or an operator may edit settings."), "Owner access required"); return; }
            try {
                var current = found.orElseThrow();
                if (!current.generation().equals(audience.menu().generation())) throw new IllegalArgumentException("Placement changed");
                var settings = parseSettings(value, current.kind());
                var operation = new OperationId(UUID.randomUUID()); var attempt = new RequestId(UUID.randomUUID());
                var request = TerminalPolicy.configureRequest(current.anchor(), current.generation(), current.revision(), settings);
                var intent = new Journal.Intent(operation, attempt, actor, TerminalPolicy.Action.CONFIGURE.kind(), request, null, null);
                start(audience, services, grant, state, intent, fresh -> {
                    boolean live = audience.menu().valid(audience.player());
                    boolean currentOperator = audience.player().hasPermissions(2);
                    return services.ledger().terminalPolicy(TerminalPolicy.Action.CONFIGURE,
                            (who, kind) -> who.equals(actor), (action, candidate, snapshot, reads) -> live && candidate.equals(intent),
                            (who, reads) -> currentOperator, null).submit(intent);
                }, "settings");
            } catch (IllegalArgumentException invalid) { reply(audience, "settings", List.of("Use MODE;CAD,USD;all or a valid comma-separated cash item list."), "Invalid settings"); }
        });
    }
    static TerminalPolicy.Settings parseSettings(String value, TerminalPolicy.Kind kind) {
        return parseSettings(value, kind, CashCatalog.all());
    }
    static TerminalPolicy.Settings parseSettings(String value, TerminalPolicy.Kind kind, java.util.List<CashCatalog.Denomination> catalog) {
        var fields = value.trim().split(";", -1);
        if (fields.length != 3) throw new IllegalArgumentException("Settings fields");
        var mode = TerminalPolicy.Mode.valueOf(fields[0].trim().toUpperCase(Locale.ROOT));
        if (mode == TerminalPolicy.Mode.DISABLED) return TerminalPolicy.Settings.disabled();
        if (kind == TerminalPolicy.Kind.REGISTER) throw new IllegalArgumentException("Register cash mode");
        var currencies = EnumSet.noneOf(EconomyCurrency.class);
        for (var code : fields[1].split(",", -1)) currencies.add(EconomyCurrency.fromCode(code.trim().toUpperCase(Locale.ROOT)));
        var denominations = new TreeSet<String>();
        if (fields[2].trim().equalsIgnoreCase("all")) {
            for (var entry : catalog) if (currencies.contains(entry.currency()) && (kind != TerminalPolicy.Kind.ATM || entry.bill())) denominations.add("economy:" + entry.id());
        } else {
            if (fields[2].split(",", -1).length > TerminalPolicy.MAX_DENOMINATIONS) throw new IllegalArgumentException("Cash type bound");
            for (var id : fields[2].split(",", -1)) {
                var key = id.trim(); if (!key.startsWith("economy:")) key = "economy:" + key;
                final String wanted = key;
                var entry = catalog.stream().filter(item -> ("economy:" + item.id()).equals(wanted)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown cash type"));
                if (!currencies.contains(entry.currency()) || kind == TerminalPolicy.Kind.ATM && !entry.bill()) throw new IllegalArgumentException("Cash type currency");
                denominations.add(key);
            }
        }
        return new TerminalPolicy.Settings(mode, currencies, denominations);
    }
    private static Journal.Intent ownedIntent(PartyAuthorization.Grant grant, Journal.Kind kind, OperationId operation,
                                              RequestId attempt, Journal.CanonicalRequest request) {
        var fields = new HashMap<>(request.fields());
        fields.put("economy:owner", Journal.actorKey(grant.actor()));
        return new Journal.Intent(operation, attempt, grant.actor(), kind,
                new Journal.CanonicalRequest(request.version(), fields), null, null);
    }
    private static void start(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state,
                              Journal.Intent intent, Function<PartyAuthorization.Grant, JournalService.Submission> submit, String page) {
        if (!audience.live()) return;
        if (audience.player().getPersistentData().contains(PENDING)) {
            reply(audience, page, List.of("A previous banking operation needs its original outcome checked. Choose Retry banking."), "Previous operation unresolved"); return;
        }
        var tag = new CompoundTag();
        tag.putUUID("world", grant.target().world().value()); tag.putUUID("actor", audience.player().getUUID());
        tag.putUUID("operation", intent.operationId().value()); tag.putUUID("request", intent.requestId().value());
        tag.putString("service", intent.kind().service()); tag.putString("type", intent.kind().operationType());
        tag.putInt("version", intent.request().version());
        var fields = new CompoundTag(); intent.request().fields().forEach(fields::putString); tag.put("fields", fields);
        if (audience.player().getPersistentData().getCompound("economy_banking_cash_pending").hasUUID("operation"))
            throw new IllegalStateException("Resolve the pending cash operation first.");
        audience.player().getPersistentData().put(PENDING, tag);
        state.retry = submit;
        audience.menu().setBusy(true);
        boolean reachedSubmission = false;
        try {
            BankingSaves.player(audience.player(), grant.target().world(), intent.operationId().value());
            reachedSubmission = true;
            // The same original operation and canonical request are retained if admission or publication is uncertain.
            submit.apply(grant).completion().whenComplete((delivery, error) -> finish(audience, grant.target().world(), state, intent, page,
                    error == null ? delivery.outcome() : null));
        } catch (Exception unavailable) {
            if (!reachedSubmission) {
                audience.player().getPersistentData().remove(PENDING); state.retry = null;
                reply(audience, page, List.of("The request was not submitted because its save could not be verified."), "Banking save unavailable");
            } else reply(audience, page, List.of("Your original operation is retained. Choose Retry banking to check it."), "Banking admission unavailable");
        }
    }
    private static Journal.Intent pending(ServerPlayer player, WorldId world) {
        var tag = player.getPersistentData().getCompound(PENDING);
        if (!tag.hasUUID("world") || !tag.getUUID("world").equals(world.value())
                || !tag.hasUUID("actor") || !tag.getUUID("actor").equals(player.getUUID())) throw new IllegalArgumentException("Pending banking owner");
        var kind = new Journal.Kind(tag.getString("service"), tag.getString("type"));
        if (!Set.of(dev.emily.economy.account.AccountCatalog.INITIALIZE_PRIMARY, AccountLifecycle.OPEN,
                FxSettlement.EXCHANGE, TerminalPolicy.Action.CONFIGURE.kind()).contains(kind)) throw new IllegalArgumentException("Pending banking kind");
        var fieldsTag = tag.getCompound("fields");
        if (fieldsTag.size() > 64) throw new IllegalArgumentException("Pending banking fields");
        var fields = new HashMap<String, String>();
        for (var key : fieldsTag.getAllKeys()) fields.put(key, fieldsTag.getString(key));
        return new Journal.Intent(new OperationId(tag.getUUID("operation")), new RequestId(tag.getUUID("request")),
                new PlayerId(player.getUUID()), kind, new Journal.CanonicalRequest(tag.getInt("version"), fields), null, null);
    }
    private static void retry(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state) {
        if (!audience.player().getPersistentData().contains(PENDING)) {
            reply(audience, audience.menu().page(), List.of("There is no unresolved banking operation."), "Nothing to retry"); return;
        }
        var intent = pending(audience.player(), grant.target().world());
        audience.menu().setBusy(true);
        var session = services.sessionId(); var world = grant.target().world();
        services.journal((actor, kind) -> grant.allows(world, session, actor, kind), Map.of(), null).lookup(intent).whenComplete((found, error) -> {
            if (error != null) { finish(audience, world, state, intent, audience.menu().page(), null); return; }
            if (found.isPresent()) { finish(audience, world, state, intent, audience.menu().page(), found.orElseThrow()); return; }
            if (!audience.live()) return;
            if (state.retry == null) {
                audience.player().getPersistentData().put("economy_banking_last_absent", audience.player().getPersistentData().getCompound(PENDING).copy());
                audience.player().getPersistentData().remove(PENDING);
                reply(audience, audience.menu().page(), List.of("Operation " + intent.operationId().value(),
                        "The ordered original-outcome lookup confirms no committed result. No replacement operation was submitted.",
                        "You may prepare a fresh request."), "Original operation was not committed");
                return;
            }
            try {
                BankingSaves.player(audience.player(), world, intent.operationId().value());
                state.retry.apply(grant).completion().whenComplete((delivery, submitError) -> finish(audience, world, state, intent,
                        audience.menu().page(), submitError == null ? delivery.outcome() : null));
            } catch (Exception unavailable) { finish(audience, world, state, intent, audience.menu().page(), null); }
        });
    }
    private static void finish(Audience audience, WorldId world, State state, Journal.Intent intent, String page, Journal.Outcome outcome) {
        boolean terminal = outcome != null && (outcome.status() == Journal.Status.COMMITTED || outcome.status() == Journal.Status.DURABLY_REJECTED);
        if (terminal) {
            var saved = audience.player().getPersistentData().getCompound(PENDING);
            if (saved.hasUUID("operation") && saved.getUUID("operation").equals(intent.operationId().value())) {
                audience.player().getPersistentData().remove(PENDING);
                // A disconnected old ServerPlayer must never overwrite a newer login's inventory file.
                if (audience.player().server.getPlayerList().getPlayer(audience.player().getUUID()) == audience.player()) {
                    try { BankingSaves.player(audience.player(), world, intent.operationId().value()); }
                    catch (Exception ignored) { /* A persisted old marker is safe: exact lookup will resolve it again. */ }
                }
            }
            state.retry = null;
        }
        if (!audience.live()) return;
        var lines = new ArrayList<String>();
        lines.add("Operation " + intent.operationId().value());
        if (outcome != null) lines.add(outcome.result().code().replace('_', ' ').toLowerCase(Locale.ROOT));
        if (!terminal) lines.add("Keep the original operation. Choose Retry banking before another mutation.");
        reply(audience, page, lines, terminal ? "Banking operation resolved" : "Banking outcome needs checking");
    }
    private static void reply(Audience audience, String page, List<String> lines, String status) {
        if (!audience.live()) return;
        audience.menu().setBusy(false);
        audience.menu().publish(page, lines, status);
    }
    private static void unavailable(Audience audience, String page) {
        reply(audience, page, List.of("The account, terminal or current publication is unavailable. Refresh and try again."), "Banking view unavailable");
    }
    private static String money(Money value) { return value.majorUnits().toPlainString() + " " + value.currency().code(); }
}
