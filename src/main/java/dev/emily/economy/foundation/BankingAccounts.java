package dev.emily.economy.foundation;

import dev.emily.economy.Economy;
import dev.emily.economy.account.AccountLifecycle;
import dev.emily.economy.account.AccountCatalog;
import dev.emily.economy.api.*;
import dev.emily.economy.api.Identifiers.*;
import dev.emily.economy.banking.BankingMenu;
import dev.emily.economy.banking.BankingNetwork;
import dev.emily.economy.banking.BankingController;
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
    private static final Set<String> ACTIONS = Set.of("setup", "accounts", "accounts_next", "manage_accounts", "account_select", "account_open", "account_close_preview", "account_close_confirm",
            "history", "history_next", "fx", "exchange_preview", "exchange_confirm", "settings", "configure", "bank_retry");
    private static final Map<BankingMenu, State> STATES = new WeakHashMap<>();
    private static final class State {
        AccountId selected;
        String accountCursor;
        String historyCursor;
        Map<UUID, AccountIdentity> offered = Map.of();
        Set<EconomyCurrency> openCurrencies = Set.of();
        FxSettlement.Exchange exchange;
        FxQuotes.Quote quote;
        FundedClosure.Instruction closure;
        long closureRevision;
        long closureTerminalRevision;
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
                case "manage_accounts" -> manage(audience, services, grant, state);
                case "account_select" -> select(audience, services, grant, state, value);
                case "account_open" -> open(audience, services, grant, state, value);
                case "account_close_preview" -> closePreview(audience, services, grant, state, value);
                case "account_close_confirm" -> closeConfirm(audience, services, grant, state);
                case "history", "history_next" -> history(audience, services, grant, state, action.equals("history_next"));
                case "fx" -> fx(audience, services, grant, state);
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
        if (!audience.menu().page().equals("accounts") || audience.menu().choices().stream().noneMatch(choice -> choice.action().equals("setup")))
            throw new IllegalArgumentException("Open account from My accounts");
        var request = new Journal.CanonicalRequest(1, Map.of());
        var op = new OperationId(UUID.randomUUID()); var attempt = new RequestId(UUID.randomUUID());
        var kind = dev.emily.economy.account.AccountCatalog.INITIALIZE_PRIMARY;
        start(audience, services, grant, state, ownedIntent(grant, kind, op, attempt, request),
                fresh -> services.initializePrimary(fresh, op, attempt, null), "accounts");
    }
    private record OwnerAccounts(List<AccountIdentity> accounts, EconomyCurrency worldCurrency) {}
    /** Query open identities directly so retained closed history never hides an active account. */
    private static java.util.concurrent.CompletionStage<Optional<OwnerAccounts>> ownerAccounts(ServerServices services, PartyAuthorization.Grant grant) {
        return services.readAccounts(grant, false, reads -> {
            var player = (PartyRef.Player) grant.target().owner();
            var ids = reads.query("SELECT account_id FROM account_catalog WHERE owner_id=? AND purpose='BANK' AND status='OPEN' ORDER BY account_id LIMIT 4",
                    List.of(player.id().value().toString()), 4);
            var accounts = ids.stream().map(row -> AccountCatalog.find(reads, grant.target(),
                    new AccountId(UUID.fromString(row.get("account_id")))).orElseThrow()).toList();
            var denomination = RebaseSettlement.denomination(reads).map(value -> value.currency()).orElse(null);
            return Optional.of(new OwnerAccounts(accounts, denomination));
        });
    }
    private static void accounts(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, boolean next) {
        audience.menu().setBusy(true); state.openCurrencies = Set.of(); state.closure = null;
        ownerAccounts(services, grant).whenComplete((found, error) -> {
            if (!audience.live()) return;
            if (error != null || found.isEmpty()) { unavailable(audience, "accounts"); return; }
            state.offered = offeredAccounts(found.orElseThrow().accounts());
            state.accountCursor = null;
            var choices = accountChoices(state, "history");
            if (choices.isEmpty()) choices.add(new BankingNetwork.Choice("setup", "", "Open account", false));
            else choices.add(new BankingNetwork.Choice("manage_accounts", "", "Manage account", false));
            reply(audience, "accounts", List.of(), choices.size() == 1 && state.offered.isEmpty() ? "Open your first account." : "Choose an account to view its transactions.", choices);
        });
    }
    static Set<EconomyCurrency> availableCurrencies(List<AccountIdentity> accounts) {
        var banks = accounts.stream().filter(account -> account.purpose() == AccountIdentity.Purpose.BANK && account.status() == AccountIdentity.Status.OPEN).toList();
        if (banks.size() >= AccountIdentity.MAX_OPEN_BANK_ACCOUNTS) return Set.of();
        var available = EnumSet.allOf(EconomyCurrency.class);
        banks.forEach(account -> available.remove(account.currency()));
        return Collections.unmodifiableSet(available);
    }
    static boolean mayClose(AccountIdentity account, EconomyCurrency worldCurrency, AccountFunds funds, boolean teller) {
        return teller && account.purpose() == AccountIdentity.Purpose.BANK && account.status() == AccountIdentity.Status.OPEN
                && !account.primary() && account.currency() != worldCurrency
                && funds.reservedMinorUnits() == 0 && funds.restrictedMinorUnits() == 0;
    }
    private static void manage(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state) {
        state.closure = null; state.openCurrencies = Set.of();
        audience.menu().setBusy(true);
        ownerAccounts(services, grant).whenComplete((found, error) -> {
            if (!audience.live()) return;
            if (error != null || found.isEmpty()) { unavailable(audience, "manage"); return; }
            var snapshot = found.orElseThrow();
            state.offered = offeredAccounts(snapshot.accounts());
            manageBalances(audience, services, grant, state, snapshot, List.copyOf(state.offered.values()), 0, new ArrayList<>());
        });
    }
    private static void manageBalances(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state,
                                       OwnerAccounts snapshot, List<AccountIdentity> accounts, int index, List<BankingNetwork.Choice> rows) {
        boolean teller = audience.menu().access() == BankingMenu.Access.TELLER;
        if (index == accounts.size()) {
            state.openCurrencies = teller ? availableCurrencies(accounts) : Set.of();
            for (var currency : EconomyCurrency.all()) if (state.openCurrencies.contains(currency))
                rows.add(new BankingNetwork.Choice("account_open", currency.code(), currency.code(), false));
            String status = accounts.size() >= AccountIdentity.MAX_OPEN_BANK_ACCOUNTS ? "Maximum of 3 accounts reached."
                    : !teller ? "Visit a teller to open or close accounts." : "Choose a currency to open an account, or an account to close.";
            reply(audience, "manage", List.of(), status, rows);
            return;
        }
        var account = accounts.get(index);
        services.ledger().funds(grant, account.id()).whenComplete((funds, error) -> {
            if (!audience.live()) return;
            if (error != null || funds.isEmpty()) { unavailable(audience, "manage"); return; }
            var balance = funds.orElseThrow();
            String label = account.currency().code() + (account.primary() ? " primary" : " account") + " — " + money(balance.posted());
            rows.add(new BankingNetwork.Choice("account_close_preview", account.id().value().toString(), label,
                    mayClose(account, snapshot.worldCurrency(), balance, teller)));
            manageBalances(audience, services, grant, state, snapshot, accounts, index + 1, rows);
        });
    }
    private static Map<UUID, AccountIdentity> offeredAccounts(List<AccountIdentity> accounts) {
        var offered = new LinkedHashMap<UUID, AccountIdentity>();
        for (var account : accounts) if (account.purpose() == AccountIdentity.Purpose.BANK && account.status() == AccountIdentity.Status.OPEN)
            offered.put(account.id().value(), account);
        return Collections.unmodifiableMap(offered);
    }
    private static List<BankingNetwork.Choice> accountChoices(State state, String page) {
        var choices = new ArrayList<BankingNetwork.Choice>();
        for (var account : state.offered.values()) choices.add(new BankingNetwork.Choice("account_select",
                account.id().value() + "|" + page, account.currency().code() + (account.primary() ? " primary account" : " account"), account.id().equals(state.selected)));
        return choices;
    }
    /** Offers only authenticated owner accounts; the selected account determines the transaction currency. */
    public static void cashAccounts(ServerPlayer player, BankingMenu menu, String page, List<String> lines, String status) {
        var services = Economy.serverServices(player.server);
        if (services == null || !menu.valid(player)) return;
        var grant = grant(player, services);
        if (grant == null) return;
        var state = STATES.computeIfAbsent(menu, ignored -> new State());
        var audience = new Audience(player, menu, menu.session(), menu.revision());
        menu.setBusy(true);
        services.ledger().bankingContext(new PlayerId(player.getUUID()), menu.anchor(), state.selected).whenComplete((context, error) -> {
            if (!audience.live()) return;
            if (error != null || context.account() == null) { unavailable(audience, page); return; }
            state.selected = context.account().id();
            ownerAccounts(services, grant).whenComplete((found, accountError) -> {
                if (!audience.live()) return;
                if (accountError != null || found.isEmpty()) { unavailable(audience, page); return; }
                state.offered = offeredAccounts(found.orElseThrow().accounts());
                var display = new ArrayList<String>();
                display.add("Selected: " + context.account().currency().code() + " account");
                display.addAll(lines);
                String pageStatus = page.equals("deposit") && context.terminal() != null && context.terminal().kind() == TerminalPolicy.Kind.ATM
                        && !context.terminal().settings().mode().deposits()
                        ? "Deposits disabled on this ATM. Its owner can enable them." : status;
                reply(audience, page, display, pageStatus, accountChoices(state, page));
            });
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
        var fields = value.split("\\|", -1);
        if (fields.length > 2) throw new IllegalArgumentException("Account selection");
        String page = fields.length == 2 ? fields[1] : "history";
        if (!Set.of("history", "deposit", "withdraw", "fx").contains(page)
                || (!audience.menu().page().equals("accounts") && !audience.menu().page().equals(page)))
            throw new IllegalArgumentException("Account selection page");
        if (!BankingController.canChangeAccount(audience.menu())) throw new IllegalStateException("Resolve the current cash transaction first.");
        var selected = offered(state, fields[0]);
        audience.menu().setBusy(true);
        services.findOwnedAccount(grant, selected.id()).whenComplete((found, error) -> {
            if (!audience.live()) return;
            if (error != null || found.isEmpty() || found.orElseThrow().status() != AccountIdentity.Status.OPEN) { unavailable(audience, page); return; }
            if (!BankingController.canChangeAccount(audience.menu())) {
                reply(audience, audience.menu().page(), audience.menu().lines(), "Resolve the current cash transaction first."); return;
            }
            state.selected = selected.id(); state.historyCursor = null; state.exchange = null; state.quote = null; state.closure = null;
            audience.menu().setBusy(false);
            switch (page) {
                case "history" -> history(audience, services, grant, state, false);
                case "fx" -> fx(audience, services, grant, state);
                default -> BankingController.action(audience.player(), audience.menu(), page, "");
            }
        });
    }
    private static void open(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, String value) {
        var currency = EconomyCurrency.fromCode(value.trim().toUpperCase(Locale.ROOT));
        if (!audience.menu().page().equals("manage") || audience.menu().access() != BankingMenu.Access.TELLER
                || !state.openCurrencies.contains(currency)) throw new IllegalArgumentException("Choose an offered currency at a teller");
        audience.menu().setBusy(true);
        services.ledger().bankingContext(new PlayerId(audience.player().getUUID()), audience.menu().anchor(), null).whenComplete((context, error) -> {
            if (!audience.live()) return;
            if (error != null || context.account() == null || context.terminal() == null || context.terminal().kind() != TerminalPolicy.Kind.TELLER) {
                reply(audience, "manage", List.of(), "Open your first account from My accounts, then visit a teller."); return;
            }
            var open = new AccountLifecycle.Open(currency, context.account().revision());
            var operation = new OperationId(UUID.randomUUID()); var attempt = new RequestId(UUID.randomUUID());
            start(audience, services, grant, state, ownedIntent(grant, AccountLifecycle.OPEN, operation, attempt, open.request()),
                    fresh -> services.submitOwned(fresh, AccountLifecycle.OPEN, operation, attempt, open.request(),
                            AccountLifecycle.opening(fresh.target(), open), null), "manage");
        });
    }
    private static void history(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, boolean next) {
        if (next && state.historyCursor == null) { reply(audience, "history", audience.menu().lines(), "End of history"); return; }
        if (!next) state.historyCursor = null;
        audience.menu().setBusy(true);
        services.ledger().bankingContext(new PlayerId(audience.player().getUUID()), audience.menu().anchor(), state.selected).whenComplete((context, contextError) -> {
            if (!audience.live()) return;
            if (contextError != null || context.account() == null) { unavailable(audience, "history"); return; }
            services.ledger().funds(grant, context.account().id()).whenComplete((funds, fundsError) -> {
                if (!audience.live()) return;
                if (fundsError != null || funds.isEmpty()) { unavailable(audience, "history"); return; }
                services.ledger().history(grant, context.account().id(), state.historyCursor, 8).whenComplete((found, error) -> {
                    if (!audience.live()) return;
                    if (error != null || found.isEmpty()) { unavailable(audience, "history"); return; }
                    var page = found.orElseThrow(); state.historyCursor = page.nextCursor();
                    var lines = new ArrayList<String>();
                    lines.add("Transaction History");
                    lines.add("Balance: " + money(new Money(funds.orElseThrow().currency(), funds.orElseThrow().availableMinorUnits())));
                    for (var entry : page.entries()) {
                        lines.add("#" + entry.sequence().value() + " " + money(entry.amount()));
                        lines.add("Transaction " + entry.transactionId().value());
                    }
                    if (page.entries().isEmpty()) lines.add("No transactions yet.");
                    reply(audience, "history", lines, state.historyCursor == null ? "All entries shown" : "More history available", List.of());
                });
            });
        });
    }
    private static void closePreview(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state, String value) {
        if (audience.menu().access() != BankingMenu.Access.TELLER || !audience.menu().page().equals("manage")
                || !BankingController.canChangeAccount(audience.menu())) throw new IllegalArgumentException("Choose an account in Manage account at a teller");
        var offered = state.offered.get(UUID.fromString(value));
        if (offered == null || audience.menu().choices().stream().noneMatch(choice -> choice.action().equals("account_close_preview")
                && choice.value().equals(value) && choice.selected())) throw new IllegalArgumentException("Choose an eligible offered account");
        state.selected = offered.id();
        state.closure = null;
        audience.menu().setBusy(true);
        services.ledger().bankingContext(new PlayerId(audience.player().getUUID()), audience.menu().anchor(), state.selected).whenComplete((context, error) -> {
            if (!audience.live()) return;
            if (error != null || context.account() == null || context.terminal() == null
                    || context.terminal().kind() != TerminalPolicy.Kind.TELLER || !context.terminal().active()
                    || !context.terminal().generation().equals(audience.menu().generation())) { unavailable(audience, "manage"); return; }
            var source = context.account();
            if (source.status() != AccountIdentity.Status.OPEN) { unavailable(audience, "manage"); return; }
            if (source.primary()) { reply(audience, "manage", List.of(), failureMessage("ACCOUNT_PROTECTED")); return; }
            services.ledger().primaryFunds(grant).thenCombine(services.ledger().funds(grant, source.id()), (primary, funds) -> {
                if (primary.isEmpty() || funds.isEmpty()) throw new IllegalStateException("Account unavailable");
                return Map.entry(primary.orElseThrow(), funds.orElseThrow());
            }).whenComplete((found, fundsError) -> {
                if (!audience.live()) return;
                if (fundsError != null) { unavailable(audience, "manage"); return; }
                var destination = found.getKey().account(); var funds = found.getValue();
                if (funds.reservedMinorUnits() != 0 || funds.restrictedMinorUnits() != 0) {
                    reply(audience, "manage", List.of(), failureMessage("ACTIVE_HOLD")); return;
                }
                var close = new AccountLifecycle.Close(source.id(), source.revision(), destination.id(), destination.revision());
                state.closureTerminalRevision = context.terminal().revision();
                if (funds.postedMinorUnits() == 0) {
                    showClosure(audience, state, new FundedClosure.Instruction(close, null),
                            List.of("Close your " + source.currency().code() + " account?", "The account is empty.",
                                    "Its transaction history will be preserved.", "Closing it frees one of your three account slots."));
                } else services.ledger().quote(grant, new RequestId(UUID.randomUUID()),
                        new FxQuotes.Pair(source.currency(), destination.currency()), FxQuotes.Side.EXACT_DEBIT, funds.posted())
                        .whenComplete((quote, quoteError) -> {
                            if (!audience.live()) return;
                            if (quoteError != null) { unavailable(audience, "manage"); return; }
                            long credited = quote.credit().minorUnits() - quote.credit().minorUnits() % quote.credit().currency().minorUnitsPerMajor();
                            showClosure(audience, state, new FundedClosure.Instruction(close, quote.id()),
                                    List.of("Close your " + source.currency().code() + " account?",
                                            "Transfer balance: " + money(funds.posted()),
                                            "To primary: " + money(new Money(destination.currency(), credited)),
                                            "Fee: " + money(quote.fee()), "History is preserved. Confirm before the quote expires."));
                        });
            });
        });
    }
    private static void showClosure(Audience audience, State state, FundedClosure.Instruction closure, List<String> lines) {
        state.closure = closure;
        reply(audience, "close", lines, "Review account closure", List.of(
                new BankingNetwork.Choice("account_close_confirm", "", "Confirm closure", false)));
        state.closureRevision = audience.menu().revision();
    }
    private static void closeConfirm(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state) {
        if (audience.menu().access() != BankingMenu.Access.TELLER || !audience.menu().page().equals("close")
                || audience.menu().revision() != state.closureRevision || state.closure == null
                || !BankingController.canChangeAccount(audience.menu())) throw new IllegalArgumentException("Review closure first");
        var closure = state.closure;
        var anchor = audience.menu().anchor(); var generation = audience.menu().generation();
        long terminalRevision = state.closureTerminalRevision;
        var operation = new OperationId(UUID.randomUUID()); var attempt = new RequestId(UUID.randomUUID());
        start(audience, services, grant, state, ownedIntent(grant, AccountLifecycle.CLOSE, operation, attempt, closure.request()),
                fresh -> {
                    boolean live = audience.menu().valid(audience.player()) && audience.menu().page().equals("close");
                    return services.ledger().close(fresh, operation, attempt, closure, BankingClosureClaims::read,
                            (intent, reads) -> live && TerminalPolicy.matches(reads, fresh.target().world(), anchor, generation, terminalRevision));
                }, "close");
        state.closure = null;
    }

    private static void fx(Audience audience, ServerServices services, PartyAuthorization.Grant grant, State state) {
        state.exchange = null; state.quote = null;
        audience.menu().setBusy(true);
        services.ledger().bankingContext(new PlayerId(audience.player().getUUID()), audience.menu().anchor(), state.selected).whenComplete((context, error) -> {
            if (!audience.live()) return;
            if (error != null || context.fx() == null || context.account() == null) { unavailable(audience, "fx"); return; }
            state.selected = context.account().id();
            ownerAccounts(services, grant).whenComplete((found, accountError) -> {
                if (!audience.live()) return;
                if (accountError != null || found.isEmpty()) { unavailable(audience, "fx"); return; }
                state.offered = offeredAccounts(found.orElseThrow().accounts());
                var choices = accountChoices(state, "fx");
                int destinations = 0;
                for (var account : state.offered.values()) {
                    if (account.currency() == context.account().currency()) continue;
                    choices.add(new BankingNetwork.Choice("destination", account.id().value().toString(), account.currency().code() + " account", false));
                    destinations++;
                }
                var lines = destinations == 0
                        ? List.of("Open another currency account at a teller to exchange money.")
                        : List.of("From: " + context.account().currency().code() + " account", "Choose a destination account and enter the amount to exchange.", "Review the amount received and fee before confirming.");
                reply(audience, "fx", lines, destinations == 0 ? "Another currency account is needed" : "Choose accounts and amount", choices);
            });
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
            if (!terminal.generation().equals(audience.menu().generation()) || !terminal.active()) {
                reply(audience, "settings", List.of("Reopen this terminal to see its current settings."), "Terminal changed", List.of()); return;
            }
            var lines = terminal.kind() == TerminalPolicy.Kind.ATM
                    ? List.of("Changes apply to this ATM only.", "Choose its services and accepted currencies.")
                    : terminal.kind() == TerminalPolicy.Kind.TELLER
                    ? List.of("Tellers always provide deposits and withdrawals.", "All currencies, bills and coins are supported.")
                    : List.of("Registers do not handle cash.", "Set product prices from the register screen.");
            String form = terminal.settings().mode().name() + ";" + terminal.settings().currencies().stream()
                    .map(EconomyCurrency::code).sorted().collect(java.util.stream.Collectors.joining(",")) + ";all";
            var controls = terminal.kind() == TerminalPolicy.Kind.ATM
                    ? List.of(new BankingNetwork.Choice("configure", form, "Save ATM settings", true)) : List.<BankingNetwork.Choice>of();
            reply(audience, "settings", lines, terminal.kind() == TerminalPolicy.Kind.ATM
                    ? "Only this ATM is affected by these settings." : "Cash services are automatic.", controls);
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
            } catch (IllegalArgumentException invalid) { reply(audience, "settings",
                    List.of("Enter BOTH CAD, DEPOSIT CAD, WITHDRAW CAD, or OFF."), "Check the setting and try again"); }
        });
    }
    static TerminalPolicy.Settings parseSettings(String value, TerminalPolicy.Kind kind) {
        return parseSettings(value, kind, CashCatalog.all());
    }
    static TerminalPolicy.Settings parseSettings(String value, TerminalPolicy.Kind kind, java.util.List<CashCatalog.Denomination> catalog) {
        var simple = value.trim().split("\\s+", -1);
        if (simple.length == 1 && simple[0].equalsIgnoreCase("OFF")) return TerminalPolicy.Settings.disabled();
        if (simple.length == 2 && simple[0].matches("(?i)BOTH|DEPOSIT|WITHDRAW"))
            value = simple[0].toUpperCase(Locale.ROOT) + ";" + simple[1].toUpperCase(Locale.ROOT) + ";all";
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
        if (!Set.of(dev.emily.economy.account.AccountCatalog.INITIALIZE_PRIMARY, AccountLifecycle.OPEN, AccountLifecycle.CLOSE,
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
        if (outcome != null && outcome.status() == Journal.Status.COMMITTED) lines.add(switch (page) {
            case "setup" -> "Your primary account is ready.";
            case "manage" -> "Your account is ready.";
            case "accounts" -> "Your account is ready.";
            case "close" -> "Your account was closed. Its history is preserved.";
            case "fx" -> "Your exchange is complete.";
            case "settings" -> "The terminal settings were saved.";
            default -> "Done.";
        });
        else if (outcome != null && outcome.status() == Journal.Status.DURABLY_REJECTED)
            lines.add(failureMessage(outcome.result().code()));
        lines.add("Operation " + intent.operationId().value());
        if (outcome != null) lines.add(outcome.result().code().replace('_', ' ').toLowerCase(Locale.ROOT));
        if (!terminal) lines.add("Keep the original operation. Choose Retry banking before another mutation.");
        String resultStatus = !terminal ? "Banking outcome needs checking"
                : outcome.status() == Journal.Status.DURABLY_REJECTED ? failureMessage(outcome.result().code())
                : intent.kind().equals(AccountLifecycle.CLOSE) ? "Account closed. An account slot is now available." : "Banking operation resolved";
        if (page.equals("close")) reply(audience, page, lines, resultStatus, List.of());
        else reply(audience, page, lines, resultStatus);
        if (outcome != null && outcome.status() == Journal.Status.COMMITTED
                && intent.kind().equals(TerminalPolicy.Action.CONFIGURE.kind()))
            action(audience.player(), audience.menu(), "settings", "");
        if (outcome != null && outcome.status() == Journal.Status.COMMITTED) {
            if (intent.kind().equals(AccountLifecycle.CLOSE)) state.selected = null;
            if (intent.kind().equals(AccountLifecycle.OPEN) || intent.kind().equals(AccountLifecycle.CLOSE))
                action(audience.player(), audience.menu(), "manage_accounts", "");
            else if (intent.kind().equals(AccountCatalog.INITIALIZE_PRIMARY))
                action(audience.player(), audience.menu(), "accounts", "");
        }
    }
    static String failureMessage(String code) {
        return switch (code) {
            case "BANK_CAP_REACHED" -> "Limit: 3 open accounts. Close one at a teller first.";
            case "BANK_CURRENCY_EXISTS" -> "You already have an open account in this currency.";
            case "ACCOUNT_PROTECTED" -> "Your primary and world-currency accounts cannot be closed.";
            case "ACTIVE_HOLD", "RESTRICTED_FUNDS" -> "This account has reserved or restricted funds. Resolve them before closing.";
            case "UNRESOLVED_OBLIGATION" -> "Resolve pending payments, refunds or item deliveries before closing.";
            case "FX_QUOTE_STALE", "FX_QUOTE_CONSUMED", "FX_CLOSURE_AMOUNT", "ACCOUNT_STALE", "DESTINATION_STALE" -> "The account or quote changed. Review the closure or exchange again.";
            case "PRIMARY_REQUIRED" -> "Open your first account from My accounts.";
            default -> "Request declined. Refresh your accounts and review your choices.";
        };
    }

    private static void reply(Audience audience, String page, List<String> lines, String status) {
        if (!audience.live()) return;
        audience.menu().setBusy(false);
        audience.menu().publish(page, lines, status);
    }
    private static void reply(Audience audience, String page, List<String> lines, String status, List<BankingNetwork.Choice> choices) {
        if (!audience.live()) return;
        audience.menu().setBusy(false);
        audience.menu().publish(page, lines, status, choices);
    }
    private static void unavailable(Audience audience, String page) {
        reply(audience, page, List.of("The account or terminal is unavailable. Refresh My accounts and try again."), "Banking view unavailable", List.of());
    }
    private static String money(Money value) { return value.majorUnits().toPlainString() + " " + value.currency().code(); }
    private static String friendlyMode(TerminalPolicy.Mode mode) {
        return switch (mode) {
            case BOTH -> "deposits and withdrawals";
            case DEPOSIT -> "deposits only";
            case WITHDRAW -> "withdrawals only";
            case DISABLED -> "off";
        };
    }
}
