package dev.emily.economy.foundation;

import dev.emily.economy.Economy;
import dev.emily.economy.api.*;
import dev.emily.economy.banking.*;
import dev.emily.economy.credit.*;
import dev.emily.economy.card.CreditCardDesigns;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Physical credit use. Advances and subsequent physical withdrawals have distinct confirmations. */
public final class CreditUse {
    private static final Map<BankingMenu, LedgerService.CreditDrawQuote> QUOTES = new WeakHashMap<>();
    private CreditUse() {}
    public static boolean handles(String action) { return Set.of("credit_advance", "credit_adv_preview", "credit_adv_confirm").contains(action); }
    public static UUID heldToken(ServerPlayer player, dev.emily.economy.api.Identifiers.WorldId world) {
        return java.util.stream.Stream.of(player.getMainHandItem(), player.getOffhandItem())
                .filter(stack -> CreditCardDesigns.byId(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).isPresent())
                .map(stack -> dev.emily.economy.card.CardStackIdentity.mark(stack, world))
                .flatMap(Optional::stream).map(dev.emily.economy.card.CardStackIdentity.Mark::token).findFirst().orElse(null);
    }
    public static void closed(BankingMenu menu) { QUOTES.remove(menu); }
    public static void action(ServerPlayer player, BankingMenu menu, String action, String value) {
        var audience = FinanceMenuSupport.audience(player, menu);
        if (!audience.live() || menu.busy() || (menu.access() != BankingMenu.Access.ATM && menu.access() != BankingMenu.Access.TELLER)) return;
        var services = Economy.serverServices(player.server);
        if (services == null || services.state() != ServerServices.State.READY) return;
        var grant = FinanceMenuSupport.grant(player, services);
        if (grant == null) return;
        try {
            if (action.equals("credit_advance")) {
                QUOTES.remove(menu);
                audience.reply("credit_advance", List.of("Hold your active credit card and select an owned account using My accounts.",
                        "The advance credits that account. Cash withdrawal is a separate review and confirmation.",
                        "Maximum outstanding advances are 20% of your credit limit. No advance fee."), "Enter advance amount",
                        List.of(new BankingNetwork.Choice("credit_adv_preview", "", "Review advance", false)));
            } else if (action.equals("credit_adv_preview")) {
                if (!FinanceMenuSupport.offered(audience, action, "")) return;
                var account = BankingAccounts.selected(menu);
                var token = heldToken(player, grant.target().world());
                if (account == null || token == null) throw new IllegalArgumentException();
                menu.setBusy(true);
                // Resolve currency first, then parse exact currency minor units.
                services.ledger().ownedBankAccount(grant, account).whenComplete((found, error) -> {
                    if (!audience.live()) return;
                    if (error != null || found.isEmpty()) { unavailable(audience); return; }
                    try {
                        long amount = new java.math.BigDecimal(value).multiply(java.math.BigDecimal.valueOf(found.orElseThrow().currency().minorUnitsPerMajor())).longValueExact();
                        services.ledger().quoteCreditDraw(grant, token, menu.anchor(), menu.generation(), account, amount, false).whenComplete((quote, failure) -> {
                            if (!audience.live()) return;
                            if (failure != null || quote.isEmpty()) { unavailable(audience); return; }
                            var q = quote.orElseThrow(); QUOTES.put(menu, q);
                            audience.reply("credit_advance", List.of("Advance " + CreditReport.money(q.facility().currency(), q.request().amountMinor()),
                                    "Advance APR " + q.facility().advance().apr().multiply(new java.math.BigDecimal("100")).toPlainString() + "%",
                                    "Interest accrues daily from the advance. No grace period or advance fee.",
                                    "This credits the selected bank account. Cash needs a separate withdrawal.",
                                    "Quote expires " + java.time.Instant.ofEpochMilli(q.request().expiresAt())), "Review advance",
                                    List.of(new BankingNetwork.Choice("credit_adv_confirm", "", "Confirm advance", false)));
                        });
                    } catch (RuntimeException invalid) { unavailable(audience); }
                });
            } else if (FinanceMenuSupport.offered(audience, action, "")) {
                var q = Objects.requireNonNull(QUOTES.remove(menu));
                var proof = CreditDrawAdmission.capture(player, menu, q.request(), q.terminal());
                FinanceMenuSupport.submit(audience, services, grant, CreditFacilities.DRAW, q.request().request(),
                        (operation, attempt) -> services.ledger().drawCredit(grant, operation, attempt, q.request(), proof));
            }
        } catch (RuntimeException invalid) { unavailable(audience); }
    }
    private static void unavailable(FinanceMenuSupport.Audience audience) {
        audience.reply("credit_advance", List.of("Refresh the selected account, held card and advance amount."), "Advance unavailable", List.of());
    }
}
