package dev.emily.economy.banking;

import dev.emily.economy.api.*;
import dev.emily.economy.api.Identifiers.*;
import dev.emily.economy.cash.WithdrawalInventory;
import dev.emily.economy.foundation.BankingAccounts;
import dev.emily.economy.foundation.LedgerService;
import dev.emily.economy.journal.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;

/** Live menu orchestration around the exact, journaled withdrawal service. */
public final class BankingWithdrawals {
    private static final Set<String> ACTIONS = Set.of("withdraw", "withdraw_preview", "withdraw_confirm");
    private static final Map<BankingMenu, State> STATES = new WeakHashMap<>();
    private static final class State {
        Withdrawals.Preview preview;
        WorldId world;
        long previewRevision;
        UUID reservation;
        boolean committing;
        boolean cancelling;
        boolean complete;
    }
    record Parsed(Money cash, Map<String, Integer> edits) {}
    private BankingWithdrawals() {}
    public static boolean handles(String action) { return ACTIONS.contains(action); }
    public static void action(ServerPlayer player, BankingMenu menu, String action, String value) {
        if (!handles(action) || !menu.valid(player) || menu.busy()) return;
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > 2048) return;
        var state = STATES.computeIfAbsent(menu, ignored -> new State());
        try {
            switch (action) {
                case "withdraw" -> {
                    if (state.reservation != null && !state.complete) throw new IllegalStateException("Pending withdrawal");
                    state.preview = null;
                    if (state.complete) { state.reservation = null; state.complete = false; state.committing = false; }
                    show(player, menu, List.of("Enter a cash currency and amount, such as CAD 60.",
                            "ATMs issue bills; tellers can also issue coins.",
                            "Optional bill edits follow a semicolon: CAD 60;economy:cad_bill_20=3",
                            "An empty edit list asks a teller for coins: CAD 1;",
                            "Preview shows the entire cash batch and bank debit before confirmation."), "Choose a withdrawal amount");
                }
                case "withdraw_preview" -> preview(player, menu, state, parse(value));
                case "withdraw_confirm" -> confirm(player, menu, state);
                default -> { }
            }
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException invalid) {
            show(player, menu, List.of("Check the cash currency, amount and bill edits. Nothing was debited."), "Invalid withdrawal request");
        }
    }
    static Parsed parse(String value) {
        var parts = value.trim().split(";", -1);
        if (parts.length > 2) throw new IllegalArgumentException("Withdrawal fields");
        var amountFields = parts[0].trim().split("\\s+", -1);
        if (amountFields.length != 2 || amountFields[1].length() > 32) throw new IllegalArgumentException("Withdrawal amount");
        var currency = EconomyCurrency.fromCode(amountFields[0].toUpperCase(Locale.ROOT));
        long minor = new BigDecimal(amountFields[1]).movePointRight(currency.fractionDigits()).longValueExact();
        if (minor <= 0) throw new IllegalArgumentException("Positive cash amount required");
        Map<String, Integer> edits = null;
        if (parts.length == 2) {
            var values = new TreeMap<String, Integer>();
            if (!parts[1].isBlank()) {
                var lines = parts[1].split(",", -1);
                if (lines.length > WithdrawalPlanner.MAX_DENOMINATIONS) throw new IllegalArgumentException("Bill edit bound");
                for (var line : lines) {
                    var fields = line.trim().split("=", -1);
                    if (fields.length != 2 || fields[0].length() > 96
                            || !fields[0].matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Bill edit identity");
                    int count = Integer.parseInt(fields[1]);
                    if (count < 0 || count > WithdrawalPlanner.MAX_ITEMS || values.putIfAbsent(fields[0], count) != null)
                        throw new IllegalArgumentException("Bill edit quantity");
                }
            }
            edits = Collections.unmodifiableMap(values);
        }
        return new Parsed(new Money(currency, minor), edits);
    }
    private static void preview(ServerPlayer player, BankingMenu menu, State state, Parsed parsed) {
        var services = BankingController.services(player);
        var actor = BankingController.actor(player);
        long revision = menu.revision(); var session = menu.session();
        state.preview = null;
        menu.setBusy(true);
        services.ledger().bankingContext(actor, menu.anchor(), BankingAccounts.selected(menu)).whenComplete((context, error) -> {
            if (!live(player, menu, session, revision)) return;
            if (error != null || !usable(context, menu)) { unavailable(player, menu); return; }
            state.world = context.world();
            var binding = new Withdrawals.Binding(context.account().id().value(), context.account().revision(), context.account().currency(),
                    menu.anchor(), menu.generation(), context.terminal().revision(), context.fx().revision());
            int free = freeSlots(player);
            boolean markerCapacity = WithdrawalInventory.fits(player, 1);
            services.ledger().previewWithdrawal(actor, binding, parsed.cash(), parsed.edits(),
                    (candidate, reads) -> candidate.equals(binding),
                    (candidate, reads) -> markerCapacity && stacks(candidate) <= free).whenComplete((quote, quoteError) -> {
                if (!live(player, menu, session, revision)) return;
                if (quoteError != null || !WithdrawalInventory.fits(player, stacks(quote))) { unavailable(player, menu); return; }
                state.preview = quote;
                var lines = new ArrayList<String>();
                lines.add("Cash: " + money(quote.cash())); lines.add("Bank debit: " + money(quote.debit()));
                for (var line : quote.plan().lines()) lines.add(line.count() + " × " + line.denomination().id());
                lines.add("The entire batch needs " + stacks(quote) + " free inventory slots.");
                lines.add("Choose Confirm withdrawal to debit and receive this exact batch.");
                show(player, menu, lines, "Withdrawal preview");
                state.previewRevision = menu.revision();
            });
        });
    }
    private static boolean usable(LedgerService.BankingContext context, BankingMenu menu) {
        return context != null && context.account() != null && context.account().purpose() == AccountIdentity.Purpose.BANK
                && context.account().status() == AccountIdentity.Status.OPEN && context.terminal() != null && context.fx() != null
                && context.terminal().active() && context.terminal().generation().equals(menu.generation())
                && context.terminal().kind() != TerminalPolicy.Kind.REGISTER && context.terminal().settings().mode().withdrawals();
    }
    private static void confirm(ServerPlayer player, BankingMenu menu, State state) {
        if (state.preview == null || !menu.page().equals("withdraw") || menu.revision() != state.previewRevision
                || state.reservation != null) throw new IllegalArgumentException("Current preview required");
        var quote = state.preview;
        if (!WithdrawalInventory.fits(player, stacks(quote))) { unavailable(player, menu); return; }
        var operation = new OperationId(UUID.randomUUID()); var actor = BankingController.actor(player);
        var intent = new Journal.Intent(operation, new RequestId(UUID.randomUUID()), actor, Withdrawals.Action.RESERVE.kind(),
                Withdrawals.reserveRequest(quote), null, null);
        BankingController.submit(player, menu, intent,
                () -> journal(player, menu, quote, Withdrawals.Action.RESERVE, false), outcome -> {
                    if (outcome.status() != Journal.Status.COMMITTED) {
                        if (outcome.status() == Journal.Status.DURABLY_REJECTED) {
                            state.preview = null;
                            show(player, menu, List.of("The withdrawal was rejected before debit.", outcome.result().code(), "Request a new preview after checking the terminal and account."), "Withdrawal not reserved");
                        }
                        return;
                    }
                    state.reservation = operation.value();
                    if (!menu.valid(player)) { cancel(player, menu, state); return; }
                    commit(player, menu, state);
                });
    }
    private static void commit(ServerPlayer player, BankingMenu menu, State state) {
        var services = BankingController.services(player); var actor = BankingController.actor(player);
        services.ledger().bankingContext(actor, menu.anchor(), BankingAccounts.selected(menu)).whenComplete((context, error) -> {
            if (!menu.valid(player) || error != null || !sameBinding(context, menu, state.preview)
                    || !WithdrawalInventory.fits(player, stacks(state.preview))) {
                cancel(player, menu, state); return;
            }
            state.committing = true;
            var intent = new Journal.Intent(new OperationId(UUID.randomUUID()), new RequestId(UUID.randomUUID()), actor,
                    Withdrawals.Action.COMMIT.kind(), Withdrawals.request(state.reservation, "menu", "confirmed"), null, null);
            BankingController.submit(player, menu, intent,
                    () -> journal(player, menu, state.preview, Withdrawals.Action.COMMIT, false), outcome -> {
                        if (outcome.status() == Journal.Status.COMMITTED) {
                            state.complete = true;
                            BankingController.deliver(player, menu, state.world, state.reservation);
                        } else if (outcome.status() == Journal.Status.DURABLY_REJECTED) {
                            state.committing = false;
                            cancel(player, menu, state);
                        }
                    });
        });
    }
    private static boolean sameBinding(LedgerService.BankingContext context, BankingMenu menu, Withdrawals.Preview preview) {
        if (!usable(context, menu)) return false;
        var binding = preview.binding();
        return context.account().id().value().equals(binding.account()) && context.account().revision() == binding.accountRevision()
                && context.account().currency() == binding.currency() && context.terminal().revision() == binding.terminalRevision()
                && context.fx().revision().equals(binding.fxRevision());
    }
    private static JournalService journal(ServerPlayer player, BankingMenu menu, Withdrawals.Preview preview,
                                          Withdrawals.Action action, boolean cleanup) {
        var services = BankingController.services(player); var actor = BankingController.actor(player);
        boolean live = !cleanup && menu.valid(player) && menu.page().equals("withdraw");
        boolean capacity = !cleanup && live && WithdrawalInventory.fits(player, stacks(preview));
        return services.ledger().withdrawals(action, (candidate, kind) -> candidate.equals(actor),
                (requested, intent, current, reads) -> intent.actor().equals(actor) && (cleanup || live)
                        && (current == null || current.owner().equals(actor))
                        && (current == null || current.preview().binding().equals(preview.binding())),
                (requested, intent, current, reads) -> capacity, null);
    }
    private static void cancel(ServerPlayer player, BankingMenu menu, State state) {
        if (state.reservation == null || state.cancelling || state.complete || state.committing) return;
        state.cancelling = true;
        var actor = BankingController.actor(player);
        var intent = new Journal.Intent(new OperationId(UUID.randomUUID()), new RequestId(UUID.randomUUID()), actor,
                Withdrawals.Action.CANCEL.kind(), Withdrawals.request(state.reservation, "no-debit", "menu closed or preview changed"), null, null);
        BankingController.submit(player, menu, intent,
                () -> journal(player, menu, state.preview, Withdrawals.Action.CANCEL, true), outcome -> {
                    if (outcome.status() == Journal.Status.COMMITTED) {
                        state.complete = true;
                        show(player, menu, List.of("The withdrawal was cancelled before debit or cash issuance."), "Withdrawal cancelled");
                    }
                });
    }
    public static void closed(BankingMenu menu) {
        var state = STATES.remove(menu);
        if (state != null && menu.player() instanceof ServerPlayer player && !state.committing) cancel(player, menu, state);
    }
    private static int stacks(Withdrawals.Preview preview) {
        return preview.plan().lines().stream().mapToInt(line -> Math.addExact(line.count(), 63) / 64).sum();
    }
    private static int freeSlots(ServerPlayer player) {
        int count = 0;
        for (int index = 0; index < 36; index++) if (player.getInventory().getItem(index).isEmpty()) count++;
        return count;
    }
    private static boolean live(ServerPlayer player, BankingMenu menu, UUID session, long revision) {
        return menu.valid(player) && menu.session().equals(session) && menu.revision() == revision;
    }
    private static void unavailable(ServerPlayer player, BankingMenu menu) {
        show(player, menu, List.of("The terminal, account, cash plan or full inventory capacity changed.",
                "Check terminal settings, make room for the entire batch and request a new preview.", "Nothing was debited."), "Withdrawal unavailable");
    }
    private static void show(ServerPlayer player, BankingMenu menu, List<String> lines, String status) {
        if (!menu.valid(player)) return;
        menu.setBusy(false); menu.publish("withdraw", lines, status);
    }
    private static String money(Money value) { return value.majorUnits().toPlainString() + " " + value.currency().code(); }
}
