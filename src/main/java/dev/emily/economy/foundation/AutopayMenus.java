package dev.emily.economy.foundation;

import dev.emily.economy.Economy;
import dev.emily.economy.account.AccountCatalog;
import dev.emily.economy.api.*;
import dev.emily.economy.api.Identifiers.*;
import dev.emily.economy.autopay.Autopay;
import dev.emily.economy.banking.*;
import dev.emily.economy.credit.CreditFacilities;
import dev.emily.economy.debt.DebtRecords.Kind;
import dev.emily.economy.institution.InstitutionCatalog;
import dev.emily.economy.loan.Loans;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import net.minecraft.server.level.ServerPlayer;

/** Owner-consented automatic-payment settings. The server resolves every product and funding account. */
public final class AutopayMenus {
    private static final String PAGE = "finance_autopay";
    private static final Set<String> ACTIONS = Set.of("auto_home", "auto_cards", "auto_loans", "auto_next",
            "auto_select", "auto_funding", "auto_mode", "auto_disable", "auto_confirm");
    private static final Map<BankingMenu, State> STATES = new WeakHashMap<>();
    private record Product(Kind kind, UUID id, EconomyCurrency currency, long revision, boolean available,
                           String label) {}
    private static final class State {
        Kind kind;
        UUID after;
        Map<UUID, Product> offered = Map.of();
        Product selected;
        Autopay.Settings settings;
        Map<AccountId, AccountIdentity> accounts = Map.of();
        AccountIdentity funding;
        Autopay.Configure preview;
        long previewRevision;
        long expiresAt;
    }
    private record Selection(Product product, Optional<Autopay.Settings> settings,
                             Map<AccountId, AccountIdentity> accounts) {}

    private AutopayMenus() {}
    public static Set<String> actions() { return ACTIONS; }
    public static boolean handles(String action) { return ACTIONS.contains(action); }
    public static void closed(BankingMenu menu) { STATES.remove(menu); }

    public static void action(ServerPlayer player, BankingMenu menu, String action, String value) {
        if (!handles(action) || !menu.valid(player) || menu.busy() || value == null || value.length() > 128
                || !Set.of(BankingMenu.Access.ATM, BankingMenu.Access.TELLER, BankingMenu.Access.KIOSK,
                        BankingMenu.Access.CARD).contains(menu.access())) return;
        var services = Economy.serverServices(player.server);
        if (services == null || services.state() != ServerServices.State.READY) return;
        var grant = FinanceMenuSupport.grant(player, services);
        if (grant == null) return;
        var audience = FinanceMenuSupport.audience(player, menu);
        var state = STATES.computeIfAbsent(menu, ignored -> new State());
        try {
            switch (action) {
                case "auto_home" -> home(audience, state);
                case "auto_cards", "auto_loans" -> {
                    state.kind = action.equals("auto_cards") ? Kind.CARD : Kind.LOAN;
                    state.after = null;
                    page(audience, services, grant, state);
                }
                case "auto_next" -> {
                    requireOffered(audience, action, value);
                    page(audience, services, grant, state);
                }
                case "auto_select" -> select(audience, services, grant, state, value);
                case "auto_funding" -> funding(audience, state, value);
                case "auto_mode" -> {
                    requireOffered(audience, action, value);
                    review(audience, grant, state, true, Autopay.Mode.valueOf(value));
                }
                case "auto_disable" -> {
                    requireOffered(audience, action, value);
                    if (state.settings == null || !state.settings.enabled()) throw new IllegalArgumentException("Automatic payments already off");
                    review(audience, grant, state, false, state.settings.mode());
                }
                case "auto_confirm" -> confirm(audience, services, grant, state);
                default -> { }
            }
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException invalid) {
            unavailable(audience);
        }
    }

    private static void home(FinanceMenuSupport.Audience audience, State state) {
        state.kind = null;
        state.offered = Map.of();
        clearSelection(state);
        audience.reply(PAGE, List.of("Automatic payments are OFF until you opt in separately for a facility.",
                "Choose a card or loan to review its named funding account and payment target."), "Automatic payments",
                List.of(choice("auto_cards", "", "Credit-card automatic payments"),
                        choice("auto_loans", "", "Loan automatic payments")));
    }

    private static void page(FinanceMenuSupport.Audience audience, ServerServices services,
                             PartyAuthorization.Grant grant, State state) {
        if (state.kind == null) throw new IllegalArgumentException("Choose cards or loans");
        clearSelection(state);
        audience.menu().setBusy(true);
        CompletionStage<Optional<List<Product>>> result;
        if (state.kind == Kind.CARD) {
            result = services.ledger().creditFacilities(grant, state.after, 16).thenApply(found -> found.map(cards ->
                    cards.stream().map(AutopayMenus::card).toList()));
        } else {
            result = services.ledger().loans(grant, state.after, 16).thenApply(found -> found.map(loans ->
                    loans.stream().map(AutopayMenus::loan).toList()));
        }
        result.whenComplete((found, error) -> {
            if (!audience.live()) return;
            if (error != null || found.isEmpty()) { unavailable(audience); return; }
            var products = found.orElseThrow();
            var offered = new LinkedHashMap<UUID, Product>();
            var choices = new ArrayList<BankingNetwork.Choice>();
            for (var product : products) {
                offered.put(product.id(), product);
                choices.add(choice("auto_select", product.id().toString(), product.label()));
            }
            state.offered = Map.copyOf(offered);
            if (products.size() == 16) {
                state.after = products.getLast().id();
                choices.add(choice("auto_next", "", "More facilities"));
            } else state.after = null;
            choices.add(choice("auto_home", "", "Choose cards or loans"));
            audience.reply(PAGE, List.of(products.isEmpty() ? "There are no facilities on this page."
                    : "Choose your facility to inspect or change automatic payments."), "Choose a facility", choices);
        });
    }

    private static void select(FinanceMenuSupport.Audience audience, ServerServices services,
                               PartyAuthorization.Grant grant, State state, String value) {
        requireOffered(audience, "auto_select", value);
        var offered = state.offered.get(UUID.fromString(value));
        if (offered == null) throw new IllegalArgumentException("Facility not offered");
        clearSelection(state);
        audience.menu().setBusy(true);
        current(services, grant, offered).thenCompose(product -> services.ledger().autopay(grant, product.kind(), product.id())
                .thenCompose(settings -> accounts(services, grant, product.currency())
                        .thenApply(accounts -> new Selection(product, settings, accounts.orElseThrow()))))
                .whenComplete((selection, error) -> {
                    if (!audience.live()) return;
                    if (error != null || selection == null) { unavailable(audience); return; }
                    state.selected = selection.product();
                    state.settings = selection.settings().orElse(null);
                    state.accounts = selection.accounts();
                    var lines = new ArrayList<String>();
                    lines.add(state.selected.label());
                    lines.add("Automatic payments " + (state.settings != null && state.settings.enabled() ? "ON" : "OFF"));
                    if (state.settings != null && state.settings.enabled()) {
                        lines.add("Payment target " + modeName(state.settings.mode()));
                        var source = state.accounts.get(state.settings.funding());
                        lines.add(source == null ? "The named funding account is currently unavailable. No replacement account is selected automatically."
                                : "Named funding account " + accountName(source));
                        lines.add("Settings effective " + Instant.ofEpochMilli(state.settings.effectiveAt()));
                    }
                    var choices = new ArrayList<BankingNetwork.Choice>();
                    if (state.selected.available()) {
                        lines.add("Select an owned open BANK account in " + state.selected.currency().code() + " to review enabling or changing payments.");
                        for (var account : state.accounts.values()) choices.add(choice("auto_funding", account.id().value().toString(), accountName(account)));
                        if (state.accounts.isEmpty()) lines.add("No open same-currency funding account is available. Manual FX repayments remain available.");
                    } else lines.add("This facility is closed, settled or defaulted. New automatic payments cannot be enabled.");
                    if (state.settings != null && state.settings.enabled()) choices.add(choice("auto_disable", "", "Review turning automatic payments OFF"));
                    choices.add(choice("auto_home", "", "Choose another facility"));
                    audience.reply(PAGE, lines, "Automatic-payment settings", choices);
                });
    }

    private static CompletionStage<Product> current(ServerServices services, PartyAuthorization.Grant grant, Product offered) {
        return services.readAccounts(grant, false, reads -> offered.kind() == Kind.CARD
                ? CreditFacilities.find(reads, offered.id()).filter(f -> f.party().equals(grant.target())).map(AutopayMenus::card)
                : Loans.find(reads, offered.id()).filter(l -> l.party().equals(grant.target())).map(AutopayMenus::loan))
                .thenApply(Optional::orElseThrow);
    }

    private static CompletionStage<Optional<Map<AccountId, AccountIdentity>>> accounts(ServerServices services,
            PartyAuthorization.Grant grant, EconomyCurrency currency) {
        return services.readAccounts(grant, false, reads -> {
            var result = new LinkedHashMap<AccountId, AccountIdentity>();
            for (var account : AccountCatalog.openBanks(reads, grant.target()))
                if (account.currency() == currency) result.put(account.id(), account);
            return Optional.of(Collections.unmodifiableMap(result));
        });
    }

    private static void funding(FinanceMenuSupport.Audience audience, State state, String value) {
        requireOffered(audience, "auto_funding", value);
        state.funding = state.accounts.get(new AccountId(UUID.fromString(value)));
        state.preview = null;
        if (state.funding == null || state.selected == null || !state.selected.available()) throw new IllegalArgumentException("Funding not offered");
        if (state.selected.kind() == Kind.LOAN) {
            audience.reply(PAGE, List.of("Named funding account " + accountName(state.funding),
                    "The loan target is total due installment arrears, capped by the outstanding debt."), "Choose loan payment target",
                    List.of(choice("auto_mode", Autopay.Mode.LOAN_DUE.name(), "Review due-installment payments")));
        } else {
            audience.reply(PAGE, List.of("Named funding account " + accountName(state.funding),
                    "Choose the card target you consent to pay at each due window.",
                    "Minimum is the default target. Statement and full-payoff targets may exceed the contractual minimum."), "Choose card payment target",
                    List.of(new BankingNetwork.Choice("auto_mode", Autopay.Mode.MINIMUM.name(), "Required minimum (default)", true),
                            choice("auto_mode", Autopay.Mode.STATEMENT.name(), "Statement balance after payments"),
                            choice("auto_mode", Autopay.Mode.FULL_PAYOFF.name(), "Current full payoff")));
        }
    }

    private static void review(FinanceMenuSupport.Audience audience, PartyAuthorization.Grant grant,
                               State state, boolean enabled, Autopay.Mode mode) {
        var product = Objects.requireNonNull(state.selected);
        if (enabled && (!product.available() || state.funding == null
                || product.kind() == Kind.CARD && mode == Autopay.Mode.LOAN_DUE
                || product.kind() == Kind.LOAN && mode != Autopay.Mode.LOAN_DUE))
            throw new IllegalArgumentException("Payment target unavailable");
        var owner = ((PartyRef.Player) grant.target().owner()).id();
        state.preview = new Autopay.Configure(grant.target().world(), owner, product.kind(), product.id(),
                state.settings == null ? 0 : state.settings.revision(), product.revision(), enabled,
                enabled ? state.funding.id() : null, enabled ? state.funding.revision() : 0, mode);
        state.expiresAt = Math.addExact(System.currentTimeMillis(), 30_000);
        var lines = new ArrayList<String>();
        lines.add(product.label());
        if (enabled) {
            lines.add("Turn automatic payments ON using " + accountName(state.funding));
            lines.add("Authorized target " + modeName(mode));
            lines.add(targetDescription(mode));
            lines.add("At each future due boundary the selected amount is debited all-or-nothing if funds suffice.");
            lines.add("An unsuccessful attempt retries once per financial day for two days after the original due date. Each financial day lasts 20 real minutes.");
            lines.add("Only the named same-currency account is used. There is no automatic FX, overdraft, account substitution or refinancing.");
            lines.add("Manual payments reduce the target. Paying only the minimum does not stop a still-unpaid statement or full-payoff target.");
            lines.add("A completed target stops that window. New spending belongs to a later due window.");
            lines.add("Opt-out, closure or default stops automatic payments. Being offline does not disable opted-in payments.");
            lines.add("Changing these settings cancels the old retry consent and applies to future due windows. Pay existing arrears manually until then.");
            lines.add("This confirmation authorizes future debits for the selected target. Normal contractual interest and due dates continue.");
        } else {
            lines.add("Turn automatic payments OFF and cancel pending automatic-payment retries for this facility.");
            lines.add("Existing debt, interest, due dates and reporting continue. Make manual repayments when required.");
        }
        lines.add("Review valid until " + Instant.ofEpochMilli(state.expiresAt));
        audience.reply(PAGE, lines, "Review automatic-payment consent",
                List.of(choice("auto_confirm", "", enabled ? "Confirm automatic payments ON" : "Confirm automatic payments OFF"),
                        choice("auto_home", "", "Cancel and choose another facility")));
        state.previewRevision = audience.menu().revision();
    }

    private static void confirm(FinanceMenuSupport.Audience audience, ServerServices services,
                                PartyAuthorization.Grant grant, State state) {
        requireOffered(audience, "auto_confirm", "");
        var request = state.preview;
        if (request == null || audience.menu().revision() != state.previewRevision || System.currentTimeMillis() >= state.expiresAt)
            throw new IllegalArgumentException("Review automatic-payment consent again");
        state.preview = null;
        FinanceMenuSupport.submit(audience, services, grant, Autopay.CONFIGURE, request.request(),
                (operation, attempt) -> services.ledger().configureAutopay(grant, operation, attempt, request));
    }

    private static Product card(CreditFacilities.Facility card) {
        return new Product(Kind.CARD, card.id(), card.currency(), card.revision(),
                card.status() == CreditFacilities.Status.ACTIVE && card.access() != CreditFacilities.Access.CLOSED,
                "Card · " + InstitutionCatalog.require(card.issuer()).name() + " · " + money(card.currency(), card.owedMinor()));
    }
    private static Product loan(Loans.Contract loan) {
        return new Product(Kind.LOAN, loan.id(), loan.currency(), loan.revision(), loan.status() == Loans.Status.ACTIVE,
                "Loan · " + InstitutionCatalog.require(loan.lender()).name() + " · " + money(loan.currency(), loan.owedMinor()));
    }
    private static String accountName(AccountIdentity account) {
        return account.currency().code() + (account.primary() ? " primary BANK account" : " BANK account");
    }
    private static String money(EconomyCurrency currency, long minor) { return new Money(currency, minor).majorUnits().toPlainString() + " " + currency.code(); }
    private static String modeName(Autopay.Mode mode) {
        return switch (mode) {
            case MINIMUM -> "required total minimum";
            case STATEMENT -> "immutable statement balance less later payments";
            case FULL_PAYOFF -> "fresh current full payoff at each attempt";
            case LOAN_DUE -> "total due installment arrears";
        };
    }
    private static String targetDescription(Autopay.Mode mode) {
        return switch (mode) {
            case MINIMUM -> "Pay the remaining required total minimum, capped by debt.";
            case STATEMENT -> "Pay that statement's remaining balance after subsequent payments, even if its minimum is already satisfied.";
            case FULL_PAYOFF -> "Pay the freshly calculated full debt at each retry, including contractual accrued amounts, only with sufficient funds.";
            case LOAN_DUE -> "Pay total due installment arrears, capped by debt. Partial funding never creates a partial automatic debit.";
        };
    }
    private static BankingNetwork.Choice choice(String action, String value, String label) { return new BankingNetwork.Choice(action, value, label, false); }
    private static void clearSelection(State state) {
        state.selected = null;
        state.settings = null;
        state.accounts = Map.of();
        state.funding = null;
        state.preview = null;
    }
    private static void requireOffered(FinanceMenuSupport.Audience audience, String action, String value) {
        if (!FinanceMenuSupport.offered(audience, action, value)) throw new IllegalArgumentException("Selection not offered");
    }
    private static void unavailable(FinanceMenuSupport.Audience audience) {
        audience.reply(PAGE, List.of("Refresh your facility and funding account before reviewing automatic-payment settings again."),
                "Automatic-payment settings unavailable", List.of(choice("auto_home", "", "Automatic-payment settings")));
    }
}
