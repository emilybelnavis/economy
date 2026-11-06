package dev.emily.economy.foundation;

import dev.emily.economy.Economy;
import dev.emily.economy.account.AccountCatalog;
import dev.emily.economy.api.*;
import dev.emily.economy.api.Identifiers.AccountId;
import dev.emily.economy.banking.BankingMenu;
import dev.emily.economy.banking.BankingNetwork;
import dev.emily.economy.debt.*;
import dev.emily.economy.institution.InstitutionCatalog;
import java.time.Instant;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Remote owner collection views and reviewed payments through the shared durable banking intent. */
public final class FinanceCollections {
    private static final Set<String> ACTIONS = Set.of("finance_debt_home", "finance_debt_next", "finance_debt_case",
            "finance_debt_funding", "finance_debt_amount", "finance_debt_confirm");
    private static final Map<BankingMenu, State> STATES = new WeakHashMap<>();

    private static final class State {
        UUID after;
        Set<UUID> offeredCases = Set.of();
        DebtRecords.Case selected;
        Map<AccountId, AccountIdentity> accounts = Map.of();
        AccountIdentity funding;
        DebtPayments.Quote payment;
        DebtPayments.FxQuote fx;
        long previewRevision;
    }

    private FinanceCollections() {}
    public static Set<String> actions() { return ACTIONS; }
    public static boolean handles(String action) { return ACTIONS.contains(action); }
    public static void closed(BankingMenu menu) { STATES.remove(menu); }

    public static void action(ServerPlayer player, BankingMenu menu, String action, String value) {
        if (!handles(action) || !menu.valid(player) || menu.busy()) return;
        var services = Economy.serverServices(player.server);
        if (services == null || services.state() != ServerServices.State.READY) return;
        var grant = FinanceMenuSupport.grant(player, services);
        if (grant == null) return;
        var audience = FinanceMenuSupport.audience(player, menu);
        var state = STATES.computeIfAbsent(menu, ignored -> new State());
        try {
            switch (action) {
                case "finance_debt_home" -> {
                    state.after = null;
                    page(audience, services, grant, state);
                }
                case "finance_debt_next" -> {
                    requireOffered(audience, action, value);
                    page(audience, services, grant, state);
                }
                case "finance_debt_case" -> select(audience, services, grant, state, value);
                case "finance_debt_funding" -> funding(audience, state, value);
                case "finance_debt_amount" -> preview(audience, services, grant, state, value);
                case "finance_debt_confirm" -> confirm(audience, services, grant, state);
                default -> { }
            }
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException invalid) {
            unavailable(audience);
        }
    }

    private static void page(FinanceMenuSupport.Audience audience, ServerServices services,
                             PartyAuthorization.Grant grant, State state) {
        state.selected = null;
        state.funding = null;
        clearQuote(state);
        audience.menu().setBusy(true);
        services.ledger().collectionCases(grant, state.after, 16).whenComplete((found, error) -> {
            if (!audience.live()) return;
            if (error != null || found.isEmpty()) { unavailable(audience); return; }
            var cases = found.orElseThrow();
            var offered = new HashSet<UUID>();
            var choices = new ArrayList<BankingNetwork.Choice>();
            for (var debt : cases) {
                offered.add(debt.id());
                choices.add(choice("finance_debt_case", debt.id().toString(), debt.kind() + " · "
                        + creditor(debt) + " · " + money(debt.currency(), debt.debtMinor())));
            }
            state.offeredCases = Set.copyOf(offered);
            if (cases.size() == 16) {
                state.after = cases.getLast().id();
                choices.add(choice("finance_debt_next", "", "More collection cases"));
            }
            audience.reply("finance_debt", List.of(cases.isEmpty() ? "There are no collection cases on this page." : "Select your case to view its current creditor and protected repayment terms.",
                    "Collection expiry and credit-report expiry have independent dates."), "Collections", choices);
        });
    }

    private static void select(FinanceMenuSupport.Audience audience, ServerServices services,
                               PartyAuthorization.Grant grant, State state, String value) {
        requireOffered(audience, "finance_debt_case", value);
        var id = UUID.fromString(value);
        if (!state.offeredCases.contains(id)) throw new IllegalArgumentException("Case not offered");
        clearQuote(state);
        state.funding = null;
        audience.menu().setBusy(true);
        services.ledger().collectionCase(grant, id).whenComplete((found, error) -> {
            if (!audience.live()) return;
            if (error != null || found.isEmpty()) { unavailable(audience); return; }
            state.selected = found.orElseThrow();
            var lines = caseLines(state.selected);
            if (!DebtLifecycle.demands(state.selected, System.currentTimeMillis())) {
                audience.reply("finance_debt", lines, "Case has no current collection payment", List.of(choice("finance_debt_home", "", "Collection cases")));
                return;
            }
            ownedAccounts(services, grant).whenComplete((accounts, failed) -> {
                if (!audience.live()) return;
                if (failed != null || accounts.isEmpty()) { unavailable(audience); return; }
                state.accounts = accounts.orElseThrow();
                var choices = new ArrayList<BankingNetwork.Choice>();
                for (var account : state.accounts.values()) choices.add(choice("finance_debt_funding", account.id().value().toString(),
                        account.currency().code() + " account" + (account.currency() == state.selected.currency() ? "" : " · explicit FX quote")));
                choices.add(choice("finance_debt_home", "", "Collection cases"));
                audience.reply("finance_debt", lines, "Choose payment funding", choices);
            });
        });
    }

    private static java.util.concurrent.CompletionStage<Optional<Map<AccountId, AccountIdentity>>> ownedAccounts(
            ServerServices services, PartyAuthorization.Grant grant) {
        var owner = ((PartyRef.Player) grant.target().owner()).id();
        return services.readAccounts(grant, false, reads -> {
            var result = new LinkedHashMap<AccountId, AccountIdentity>();
            for (var row : reads.query("SELECT account_id FROM account_catalog WHERE owner_id=? AND purpose='BANK' AND status='OPEN' ORDER BY account_id LIMIT 4",
                    List.of(owner.value().toString()), 4)) {
                var id = new AccountId(UUID.fromString(row.get("account_id")));
                AccountCatalog.find(reads, grant.target(), id).ifPresent(account -> result.put(id, account));
            }
            return Optional.of(Map.copyOf(result));
        });
    }

    private static void funding(FinanceMenuSupport.Audience audience, State state, String value) {
        requireOffered(audience, "finance_debt_funding", value);
        state.funding = state.accounts.get(new AccountId(UUID.fromString(value)));
        if (state.funding == null || state.selected == null) throw new IllegalArgumentException("Funding not offered");
        clearQuote(state);
        audience.reply("finance_debt", List.of("Current frozen debt " + money(state.selected.currency(), state.selected.debtMinor()),
                "Enter the exact payment in " + state.selected.currency().code() + ". Enter the full balance for full settlement.",
                "A committed positive partial payment before collection expiry renews the two-financial-year collection window."),
                "Collection payment amount", List.of(choice("finance_debt_amount", "", "Review payment")));
    }

    private static void preview(FinanceMenuSupport.Audience audience, ServerServices services,
                                PartyAuthorization.Grant grant, State state, String value) {
        requireOffered(audience, "finance_debt_amount", "");
        if (state.selected == null || state.funding == null || !DebtLifecycle.demands(state.selected, System.currentTimeMillis()))
            throw new IllegalStateException("Case unavailable");
        long amount = CreditMenus.paymentAmount(value, state.selected.currency());
        if (amount > state.selected.debtMinor()) throw new IllegalArgumentException("Payment exceeds debt");
        clearQuote(state);
        audience.menu().setBusy(true);
        if (state.funding.currency() == state.selected.currency()) {
            services.ledger().quoteCollectionPayment(grant, state.selected.id(), state.funding.id(), amount).whenComplete((found, error) -> {
                if (!audience.live()) return;
                if (error != null || found.isEmpty() || !matches(state, found.orElseThrow())) { unavailable(audience); return; }
                state.payment = found.orElseThrow();
                review(audience, state);
            });
        } else {
            services.ledger().quoteCollectionPaymentFx(grant, state.selected.id(), state.funding.id(), amount).whenComplete((quote, error) -> {
                if (!audience.live()) return;
                if (error != null || quote == null || !matches(state, quote.payment())) { unavailable(audience); return; }
                state.fx = quote;
                state.payment = quote.payment();
                review(audience, state);
            });
        }
    }

    private static boolean matches(State state, DebtPayments.Quote quote) {
        return quote.caseId().equals(state.selected.id()) && quote.caseRevision() == state.selected.revision()
                && quote.funding().equals(state.funding.id()) && quote.fundingRevision() == state.funding.revision();
    }

    private static void review(FinanceMenuSupport.Audience audience, State state) {
        var lines = new ArrayList<>(caseLines(state.selected));
        lines.add("Debt receives " + money(state.selected.currency(), state.payment.amountMinor()));
        if (state.fx == null) {
            lines.add("Account debit " + money(state.selected.currency(), state.payment.amountMinor()));
            lines.add("Payment fee 0 " + state.selected.currency().code());
        } else {
            var fx = state.fx.fx();
            lines.add("Converted payment debit " + money(fx.debit().currency(), fx.debit().minorUnits()));
            lines.add("Separate FX fee " + money(fx.fee().currency(), fx.fee().minorUnits()));
            lines.add("Total account debit " + money(fx.debit().currency(), Math.addExact(fx.debit().minorUnits(), fx.fee().minorUnits())));
        }
        lines.add("Quote expires " + Instant.ofEpochMilli(expires(state)));
        if (state.payment.amountMinor() == state.selected.debtMinor()) lines.add("Full payment settles this case. Its paid-default report remains until the original report-expiry date.");
        else lines.add("A successful positive partial payment acknowledges this debt and renews collection expiry to two financial years after settlement time.");
        lines.add("Viewing, exiting or a failed payment never acknowledges debt or extends collection expiry.");
        audience.reply("finance_debt", lines, "Review collection payment", List.of(choice("finance_debt_confirm", "", "Confirm payment")));
        state.previewRevision = audience.menu().revision();
    }

    private static void confirm(FinanceMenuSupport.Audience audience, ServerServices services,
                                PartyAuthorization.Grant grant, State state) {
        requireOffered(audience, "finance_debt_confirm", "");
        if (state.payment == null || audience.menu().revision() != state.previewRevision || System.currentTimeMillis() >= expires(state))
            throw new IllegalStateException("Collection quote stale");
        if (state.fx == null) {
            var quote = state.payment;
            FinanceMenuSupport.submit(audience, services, grant, DebtPayments.PAY, quote.request(),
                    (operation, attempt) -> services.ledger().repayCollection(grant, operation, attempt, quote));
        } else {
            var quote = state.fx;
            FinanceMenuSupport.submit(audience, services, grant, DebtPayments.PAY, quote.canonical(),
                    (operation, attempt) -> services.ledger().repayCollectionFx(grant, operation, attempt, quote));
        }
    }

    private static List<String> caseLines(DebtRecords.Case debt) {
        long now = System.currentTimeMillis();
        return List.of(debt.kind() + " · " + creditor(debt), "Frozen debt " + money(debt.currency(), debt.debtMinor()),
                "Collection state " + DebtLifecycle.caseState(debt), "Credit report " + DebtLifecycle.bureauStatus(debt, now),
                "Original default " + Instant.ofEpochMilli(debt.defaultAt()),
                "Collection expiry " + Instant.ofEpochMilli(debt.collectionExpires()),
                "Original report expiry " + Instant.ofEpochMilli(debt.reportExpires()),
                "No new interest, late fees or collector fees accrue after default.");
    }

    private static long expires(State state) {
        return state.fx == null ? state.payment.expiresAt() : Math.min(state.payment.expiresAt(), state.fx.fx().expires().toEpochMilli());
    }

    private static void clearQuote(State state) { state.payment = null; state.fx = null; }

    private static String creditor(DebtRecords.Case debt) {
        return InstitutionCatalog.require(debt.creditor(), debt.currency()).name();
    }

    private static String money(EconomyCurrency currency, long minor) {
        return new Money(currency, minor).majorUnits().toPlainString() + " " + currency.code();
    }

    private static BankingNetwork.Choice choice(String action, String value, String label) {
        return new BankingNetwork.Choice(action, value, label, false);
    }

    private static void requireOffered(FinanceMenuSupport.Audience audience, String action, String value) {
        if (!FinanceMenuSupport.offered(audience, action, value)) throw new IllegalArgumentException("Collection choice not offered");
    }

    private static void unavailable(FinanceMenuSupport.Audience audience) {
        audience.reply("finance_debt", List.of("Refresh your collection case and funding account before reviewing another payment."),
                "Collection request unavailable", List.of(choice("finance_debt_home", "", "Collection cases")));
    }
}
