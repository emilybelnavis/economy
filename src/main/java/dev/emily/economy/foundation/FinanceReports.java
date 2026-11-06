package dev.emily.economy.foundation;

import dev.emily.economy.Economy;
import dev.emily.economy.banking.*;
import dev.emily.economy.debt.DebtLifecycle;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Owner-only bounded reports with server-retained exclusive cursors. */
public final class FinanceReports {
    private static final Map<BankingMenu, State> STATES = new WeakHashMap<>();
    private static final class State { UUID cases; DebtLifecycle.ReportCursor reports; }
    private FinanceReports() {}
    public static boolean handles(String action) {
        return Set.of("finance_wealth", "finance_cases", "finance_cases_next", "finance_report", "finance_report_next").contains(action);
    }
    public static void closed(BankingMenu menu) { STATES.remove(menu); }
    public static void action(ServerPlayer player, BankingMenu menu, String action, String value) {
        var audience = FinanceMenuSupport.audience(player, menu);
        if (!audience.live() || menu.busy()) return;
        if (action.endsWith("_next") && !FinanceMenuSupport.offered(audience, action, value)) return;
        var services = Economy.serverServices(player.server);
        if (services == null || services.state() != ServerServices.State.READY) return;
        var grant = FinanceMenuSupport.grant(player, services);
        if (grant == null) return;
        var state = STATES.computeIfAbsent(menu, ignored -> new State());
        menu.setBusy(true);
        if (action.equals("finance_wealth")) {
            services.ledger().valuation(grant).whenComplete((found, error) -> {
                if (!audience.live()) return;
                if (error != null || found.isEmpty()) { unavailable(audience); return; }
                var snapshot = found.orElseThrow();
                var lines = new ArrayList<String>();
                lines.add("Tier 2 begins at 100,000,000 in the current server currency.");
                if (snapshot.netWorth() != null) lines.add("Net worth " + snapshot.netWorth().toPlainString() + " " + snapshot.denomination());
                lines.add("Qualification " + (snapshot.available() ? snapshot.eligible() ? "Tier 2 eligible" : "Tier 1" : "unavailable"));
                lines.add("Availability " + snapshot.reason());
                lines.add("Valued at " + snapshot.valuedAt() + " with policy v" + snapshot.policyVersion());
                lines.add("Includes supported assets and liabilities. Trading and collateral lending are unavailable.");
                audience.reply("finance_wealth", lines, "Net worth and tier", List.of());
            });
        } else if (action.startsWith("finance_cases")) {
            if (action.equals("finance_cases")) state.cases = null;
            services.ledger().debtCases(grant, state.cases, 3).whenComplete((found, error) -> {
                if (!audience.live()) return;
                if (error != null || found.isEmpty()) { unavailable(audience); return; }
                var page = found.orElseThrow(); state.cases = page.nextCursor();
                var choices = state.cases == null ? List.<BankingNetwork.Choice>of()
                        : List.of(new BankingNetwork.Choice("finance_cases_next", "", "Next cases", false));
                var lines = new ArrayList<>(DebtReport.cases(page));
                lines.add("Use the Collection Agent for protected partial or full payments on collectible cases.");
                audience.reply("finance_cases", lines, "Collections", choices);
            });
        } else {
            if (action.equals("finance_report")) state.reports = null;
            services.ledger().debtReportPage(grant, state.reports, 3).whenComplete((found, error) -> {
                if (!audience.live()) return;
                if (error != null || found.isEmpty()) { unavailable(audience); return; }
                var page = found.orElseThrow(); state.reports = page.nextCursor();
                audience.reply("finance_report", DebtReport.reports(page, null), "Credit report",
                        state.reports == null ? List.of() : List.of(new BankingNetwork.Choice("finance_report_next", "", "Next reports", false)));
            });
        }
    }
    private static void unavailable(FinanceMenuSupport.Audience audience) {
        audience.reply("finance_report", List.of("Refresh this owner view when services are ready."), "Report unavailable", List.of());
    }
}
