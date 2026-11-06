package dev.emily.economy.banking;

import dev.emily.economy.Economy;
import dev.emily.economy.api.*;
import dev.emily.economy.api.Identifiers.*;
import dev.emily.economy.cash.*;
import dev.emily.economy.foundation.*;
import dev.emily.economy.journal.*;
import dev.emily.economy.terminal.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleMenuProvider;

/** Normal server-authoritative player entry points. No packet supplies actor, world, money postings or evidence. */
public final class BankingController {
    private static final String PENDING = "economy_banking_cash_pending";
    private static final Map<BankingMenu, Flow> FLOWS = new WeakHashMap<>();
    static final class Flow {
        LedgerService.BankingContext context;
        Deposits.Preview deposit;
        long depositRevision;
        long saleRevision;
        RegisterSales.Quote quote;
        LedgerService.CreditDrawQuote credit;
        dev.emily.economy.credit.CreditDrawAdmission.Proof creditProof;
        RegisterInventory.Snapshot stock;
        UUID reservation;
        Runnable retry;
        boolean inFlight;
        boolean ownsInputs;
        Set<UUID> claims = Set.of();
        List<UUID> claimOrder = List.of();
        UUID claimCursor;
    }
    private BankingController() {}
    public static ServerServices services(ServerPlayer player) { return Economy.serverServices(player.server); }
    public static PlayerId actor(ServerPlayer player) { return new PlayerId(player.getUUID()); }
    public static boolean canChangeAccount(BankingMenu menu) {
        var flow = FLOWS.get(menu);
        return (flow == null || flow.reservation == null && !flow.inFlight) && BankingWithdrawals.canChangeAccount(menu);
    }
    public static void open(ServerPlayer player, BlockPos clicked) {
        var anchor = TerminalGeometry.resolveAnchor(player.serverLevel(), clicked);
        if (!(player.serverLevel().getBlockEntity(anchor) instanceof TerminalBlockEntity entity)
                || !TerminalLifecycle.matches(player.serverLevel(), anchor, entity.generation())) {
            player.displayClientMessage(Component.literal("Terminal is still preparing. Try again shortly."), true); return;
        }
        if ((entity.kind() == TerminalPolicy.Kind.ATM || entity.kind() == TerminalPolicy.Kind.KIOSK || entity.kind() == TerminalPolicy.Kind.TELLER) && player.getMainHandItem().isEmpty() && player.isShiftKeyDown()
                && mayConfigure(entity.owner(), player.getUUID(), player.hasPermissions(2))) {
            openAdmin(player, anchor); return;
        }
        var access = switch (entity.kind()) {
            case ATM -> BankingMenu.Access.ATM;
            case TELLER -> BankingMenu.Access.TELLER;
            case KIOSK -> BankingMenu.Access.KIOSK;
            case REGISTER -> BankingMenu.Access.REGISTER;
        };
        open(player, new TerminalPolicy.Anchor(player.serverLevel().dimension().location().toString(), anchor.getX(), anchor.getY(), anchor.getZ()), entity.generation(), access);
    }
    public static boolean openAdmin(ServerPlayer player, BlockPos clicked) {
        var anchor = TerminalGeometry.resolveAnchor(player.serverLevel(), clicked);
        if (!(player.serverLevel().getBlockEntity(anchor) instanceof TerminalBlockEntity entity)
                || !TerminalLifecycle.matches(player.serverLevel(), anchor, entity.generation())
                || !mayConfigure(entity.owner(), player.getUUID(), player.hasPermissions(2))) return false;
        open(player, new TerminalPolicy.Anchor(player.serverLevel().dimension().location().toString(), anchor.getX(), anchor.getY(), anchor.getZ()),
                entity.generation(), BankingMenu.Access.ADMIN);
        return true;
    }
    public static boolean mayConfigure(UUID owner, UUID player, boolean operator) {
        return operator || player.equals(owner);
    }
    public static void openCard(ServerPlayer player) {
        var position=player.blockPosition();
        var anchor=new TerminalPolicy.Anchor(player.serverLevel().dimension().location().toString(),position.getX(),position.getY(),position.getZ());
        var services=services(player);if(services==null || services.worldId()==null)return;
        var mark=java.util.stream.Stream.of(player.getMainHandItem(),player.getOffhandItem())
                .map(stack->dev.emily.economy.card.CardStackIdentity.mark(stack,services.worldId())).flatMap(Optional::stream).findFirst().orElse(null);
        if(mark==null)return;
        services.ledger().usableCardAccess(actor(player),services.worldId(),mark.token()).whenComplete((found,error)->{
            if(player.server.getPlayerList().getPlayer(player.getUUID())!=player || services.state()!=ServerServices.State.READY || services.worldId()==null)return;
            boolean held=java.util.stream.Stream.of(player.getMainHandItem(),player.getOffhandItem()).anyMatch(stack->
                    dev.emily.economy.card.CardStackIdentity.mark(stack,services.worldId()).filter(mark::equals).isPresent());
            if(error!=null || found.isEmpty() || !held || !heldIssuedCard(player,services.worldId(),found.orElseThrow())) {player.displayClientMessage(Component.literal("This card is unavailable, expired or revoked."),true);return;}
            open(player,anchor,UUID.randomUUID(),BankingMenu.Access.CARD,mark.token());
        });
    }
    private static boolean heldIssuedCard(ServerPlayer player,WorldId world,dev.emily.economy.card.CardAccess token) {
        return java.util.stream.Stream.of(player.getMainHandItem(),player.getOffhandItem()).anyMatch(stack->
                dev.emily.economy.card.CardStackIdentity.mark(stack,world).filter(mark->mark.token().equals(token.id())).isPresent()
                && dev.emily.economy.card.CardCatalog.find(stack).filter(item->item.id().equals(token.design())).isPresent());
    }
    public static void checkedAction(ServerPlayer player,BankingMenu menu,String action,String value) {
        if(menu.issuedToken()==null){action(player,menu,action,value);return;}
        var services=services(player);if(services==null || services.worldId()==null)return;
        long revision=menu.revision();var session=menu.session();menu.setBusy(true);
        services.ledger().usableCardAccess(actor(player),services.worldId(),menu.issuedToken()).whenComplete((found,error)->{
            if(player.server.getPlayerList().getPlayer(player.getUUID())!=player || !menu.valid(player) || menu.revision()!=revision || !menu.session().equals(session))return;
            menu.setBusy(false);
            if(error!=null || found.isEmpty() || !heldIssuedCard(player,services.worldId(),found.orElseThrow())){player.closeContainer();return;}
            action(player,menu,action,value);
        });
    }

    public static void openWorldSetup(ServerPlayer player, UUID lease) {
        var position = player.blockPosition();
        open(player, new TerminalPolicy.Anchor(player.serverLevel().dimension().location().toString(), position.getX(), position.getY(), position.getZ()), lease, BankingMenu.Access.WORLD_SETUP);
    }
    private static void open(ServerPlayer player, TerminalPolicy.Anchor anchor, UUID generation, BankingMenu.Access access) {
        open(player,anchor,generation,access,null);
    }
    private static void open(ServerPlayer player, TerminalPolicy.Anchor anchor, UUID generation, BankingMenu.Access access,UUID issuedToken) {
        if (services(player) == null || services(player).state() != ServerServices.State.READY) return;
        var session = UUID.randomUUID();
        var holder = new BankingMenu[1];
        player.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> holder[0] =
                new BankingMenu(id, inventory, anchor, generation, session, access),
                Component.literal(access == BankingMenu.Access.REGISTER ? "Register" : access == BankingMenu.Access.ADMIN ? "Terminal Configuration" : "Banking")),
                buffer -> holder[0].writeOpening(buffer));
        if(holder[0]!=null && issuedToken!=null)holder[0].setIssuedToken(issuedToken);
        if (holder[0] != null && access != BankingMenu.Access.WORLD_SETUP) {
            try { recoverInputs(player, holder[0]); }
            catch (RuntimeException unavailable) {
                player.displayClientMessage(Component.literal("Retained originals could not be verified. They remain protected for save review."), false);
            }
        }
        if (holder[0] != null) holder[0].publish(access == BankingMenu.Access.WORLD_SETUP ? "setup"
                : access == BankingMenu.Access.ADMIN ? "settings" : "home",
                access == BankingMenu.Access.WORLD_SETUP
                ? List.of("Choose this world's bank currency.", "The first connected player's choice sets the opening denomination.", "Available currencies: " + EconomyCurrency.all().stream().map(EconomyCurrency::code).collect(java.util.stream.Collectors.joining(", ")))
                : access == BankingMenu.Access.REGISTER ? List.of()
                : access == BankingMenu.Access.ADMIN ? List.of("Configure the terminal's cash services.", "Debug details are controlled by debugGui in economy-scheduling.properties.")
                : List.of(), "How can we help?");
        if (holder[0] != null && access == BankingMenu.Access.KIOSK)
            BankingAccounts.action(player, holder[0], "manage_accounts", "");
        if (holder[0] != null && access == BankingMenu.Access.ADMIN)
            BankingAccounts.action(player, holder[0], "settings", "");
    }
    public static void action(ServerPlayer player, BankingMenu menu, String action, String value) {
        if (!menu.valid(player) || player.server.getPlayerList().getPlayer(player.getUUID()) != player) return;
        if (!menu.allows(action)) { report(menu, menu.page(), menu.lines(), "This control is not available here."); return; }
        if (action.equals("banking")) {
            var anchor = new BlockPos(menu.anchor().x(), menu.anchor().y(), menu.anchor().z());
            if (!(player.serverLevel().getBlockEntity(anchor) instanceof TerminalBlockEntity entity)) return;
            var access = switch (entity.kind()) {
                case ATM -> BankingMenu.Access.ATM;
                case TELLER -> BankingMenu.Access.TELLER;
            case KIOSK -> BankingMenu.Access.KIOSK;
                case REGISTER -> BankingMenu.Access.REGISTER;
            };
            open(player, menu.anchor(), menu.generation(), access);
            return;
        }
        if (menu.worldSetup()) {
            if (action.equals("setup") && !value.isBlank() && Economy.chooseWorld(player, menu.generation(), value.trim().toUpperCase(Locale.ROOT))) player.closeContainer();
            return;
        }
        if (Set.of("retry", "bank_retry").contains(action)) {
            if (player.getPersistentData().getCompound("economy_banking_pending").hasUUID("operation")) {
                BankingAccounts.action(player, menu, "bank_retry", value); return;
            }
            if (player.getPersistentData().getCompound(PENDING).hasUUID("operation")) action = "retry";
        }
        if (menu.remote() && Set.of("deposit", "deposit_preview", "deposit_confirm", "withdraw", "withdraw_preview", "withdraw_confirm", "shop", "buy_preview", "buy_confirm", "settings", "configure", "price").contains(action)) {
            report(menu, "home", List.of("Visit a physical terminal for this action."), "Terminal required"); return;
        }
        if (player.getPersistentData().getCompound(PENDING).hasUUID("operation")
                || player.getPersistentData().getCompound("economy_banking_pending").hasUUID("operation")) {
            if (!Set.of("auto_home", "auto_cards", "auto_loans", "auto_next", "auto_select", "finance_home", "finance_wealth", "finance_report", "finance_report_next", "finance_cases", "finance_cases_next", "finance_debt_home", "finance_debt_next", "finance_debt_case", "finance_loan_home", "finance_loan_next", "finance_loan_view", "finance_loan_schedule", "finance_loan_sched_next", "finance_loan_inst", "finance_loan_inst_next", "retry", "bank_retry", "home", "accounts", "manage_accounts", "history", "fx", "claims", "claims_next", "credit_profile", "credit_history", "credit_next", "credit_facilities", "credit_facilities_next", "credit_facility", "credit_statements", "credit_statements_next").contains(action)) {
                report(menu, menu.page(), menu.lines(), "Resolve the original pending operation with Retry before another mutation."); return;
            }
        }
        if (!menu.busy() && menu.page().equals("deposit") && Set.of("home", "manage_accounts", "setup", "accounts", "history", "fx", "withdraw", "shop", "claims", "settings", "credit_profile", "credit_history", "credit_facilities", "credit_facilities_next", "credit_facility", "credit_statements", "credit_statements_next").contains(action)) {
            var prior = FLOWS.get(menu);
            if (prior != null && prior.reservation != null) { report(menu, "deposit", menu.lines(), "Resolve the current reservation first."); return; }
            returnInputs(player, menu);
        }
        if (FinanceActions.handles(action)) {
            if (!menu.busy() && menu.page().equals("deposit")) {
                var prior = FLOWS.get(menu);
                if (prior != null && prior.reservation != null) return;
                returnInputs(player, menu);
            }
            if (action.equals("finance_home")) {
                var choices = new ArrayList<BankingNetwork.Choice>();
                choices.add(new BankingNetwork.Choice("credit_profile", "", "Credit profile", false));
                choices.add(new BankingNetwork.Choice("credit_facilities", "", "Credit facilities and statements", false));
                choices.add(new BankingNetwork.Choice("finance_credit_pay", "", "Repay credit card", false));
                choices.add(new BankingNetwork.Choice("finance_credit_cards", "", "Credit tokens", false));
                choices.add(new BankingNetwork.Choice("finance_loan_home", "", "Loans and repayments", false));
                choices.add(new BankingNetwork.Choice("auto_home", "", "Automatic payments", false));
                choices.add(new BankingNetwork.Choice("finance_wealth", "", "Net worth and tier", false));
                choices.add(new BankingNetwork.Choice("finance_report", "", "Credit report", false));
                choices.add(new BankingNetwork.Choice("finance_debt_home", "", "Collection payments", false));
                if (menu.allows("credit_advance")) choices.add(new BankingNetwork.Choice("credit_advance", "", "Credit cash advance", false));
                if (menu.access() == BankingMenu.Access.KIOSK) {
                    choices.add(new BankingNetwork.Choice("finance_credit_apply", "", "Apply for credit", false));
                    choices.add(new BankingNetwork.Choice("finance_loan_apply", "", "Apply for a loan", false));
                    choices.add(new BankingNetwork.Choice("finance_credit_raise", "", "Request credit limit increase", false));
                }
                menu.publish("finance_home", List.of("Choose a service. Applications and card replacement require the issuer branch."), "Bank and credit services", choices);
            } else if (AutopayMenus.handles(action)) AutopayMenus.action(player,menu,action,value);
            else if (CreditMenus.handles(action)) CreditMenus.action(player, menu, action, value);
            else if (LoanMenus.handles(action)) LoanMenus.action(player, menu, action, value);
            else if (FinanceCollections.handles(action)) FinanceCollections.action(player, menu, action, value);
            else if (FinanceReports.handles(action)) FinanceReports.action(player, menu, action, value);
            else CreditUse.action(player, menu, action, value);
            return;
        }
        if (BankingAccounts.handles(action)) { BankingAccounts.action(player, menu, action, value); return; }
        if (BankingWithdrawals.handles(action)) { BankingWithdrawals.action(player, menu, action, value); return; }
        var flow = FLOWS.computeIfAbsent(menu, ignored -> new Flow());
        if (menu.busy() && !action.equals("retry")) return;
        try {
            switch (action) {
                case "home" -> {
                    if (menu.access() == BankingMenu.Access.KIOSK) BankingAccounts.action(player, menu, "manage_accounts", "");
                    else report(menu, "home", List.of(), "How can we help?");
                }
                case "deposit" -> {
                    flow.deposit = null;
                    BankingAccounts.cashAccounts(player, menu, "deposit", List.of("Add cash in the left slots.", "Review before depositing."), "Add cash, then choose Review");
                }
                case "deposit_preview" -> depositPreview(player, menu, flow);
                case "deposit_confirm" -> depositConfirm(player, menu, flow);
                case "shop", "buy_preview" -> salePreview(player, menu, flow);
                case "buy_confirm" -> saleConfirm(player, menu, flow);
                case "price" -> price(player, menu, value);
                case "claims", "claims_next" -> claims(player, menu, flow, action.endsWith("next"));
                case "collect" -> {
                    var text = value.trim();
                    var id = text.matches("[1-8]") ? flow.claimOrder.get(Integer.parseInt(text) - 1) : UUID.fromString(text);
                    if (!flow.claims.contains(id)) throw new IllegalArgumentException("Select a claim from the current page.");
                    withContext(player, menu, context -> deliver(player, menu, context.world(), id));
                }
                case "retry" -> {
                    if (flow.retry != null) flow.retry.run(); else lookupPending(player, menu);
                }
                case "cancel" -> { flow.deposit = null; flow.quote = null; returnInputs(player, menu); report(menu, "home", List.of("Your unsubmitted items were returned."), "Cancelled"); }
                default -> report(menu, menu.page(), menu.lines(), "Unsupported action");
            }
        } catch (RuntimeException failure) { fail(menu, failure); }
    }
    public static void report(BankingMenu menu, String page, List<String> lines, String status) {
        if (!(menu.player() instanceof ServerPlayer player) || !menu.valid(player)) return;
        menu.setBusy(false);
        menu.publish(page, lines.stream().limit(32).map(line -> shortText(line, 256)).toList(), shortText(status, 256));
    }
    private static String money(Money value) {
        return java.math.BigDecimal.valueOf(value.minorUnits(), value.currency().fractionDigits()).toPlainString() + " " + value.currency().code();
    }
    private static String shortText(String value, int maximum) { return value == null ? "Unavailable" : value.substring(0, Math.min(maximum, value.length())); }
    static void fail(BankingMenu menu, Throwable error) {
        var cause = error; while (cause.getCause() != null) cause = cause.getCause();
        com.mojang.logging.LogUtils.getLogger().warn("event=BANKING_REQUEST_FAILED session={} reason={}",
                menu.session(), shortText(cause.getMessage(), 128));
        var player = menu.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        String status = player != null && services(player).debugGui()
                ? shortText(cause.getMessage(), 200) : menu.page().equals("deposit") ? depositProblem(cause)
                : "Request could not be completed. Try again or use Retry if prompted.";
        report(menu, menu.page(), menu.lines(), status);
    }
    static String depositProblem(Throwable failure) {
        String code = failure.getMessage() == null ? "" : failure.getMessage();
        return switch (code) {
            case "PRICING_BATCH_LIMIT" -> "Review at most four exchange currencies per deposit. Split this cash into smaller batches.";
            case "FX_CASH_BUDGET_UNREPRESENTABLE" -> "This cash total cannot exactly cover the exchange and rounded fee. Adjust the cash amount or deposit it into an account in the same currency.";
            case "TIER2_UNAVAILABLE", "SERVICE_ACCOUNT_UNAVAILABLE" -> "This bank requires current Tier 2 qualification for deposits. Your existing funds remain available.";
            case "PRICING_QUOTE_STALE", "CASH_PRICING_STALE", "PRICING_CONTEXT_UNAVAILABLE" -> "Account pricing changed or is unavailable. Review again shortly.";
            case "DEPOSIT_MODE" -> "Deposits are disabled on this ATM. Its owner can enable them with an empty-hand right-click.";
            case "DEPOSIT_DENOMINATION" -> "This terminal does not accept that cash. ATMs accept bills only; check its accepted currencies.";
            case "DEPOSIT_ACCOUNT", "DEPOSIT_ACCOUNT_STALE" -> "Choose an open account, then review again. Create your primary account first if needed.";
            case "DEPOSIT_TERMINAL", "DEPOSIT_TERMINAL_STALE" -> "The terminal changed or is unavailable. Reopen it and try again.";
            case "DEPOSIT_FX_STALE" -> "Exchange rates changed or are unavailable. Review again shortly.";
            case "DEPOSIT_SLOT_COUNT" -> "Place cash in the deposit slots before choosing Review.";
            case "DEPOSIT_WORLD" -> "This marked cash belongs to another world and cannot be deposited here.";
            case "DEPOSIT_UNRECOGNIZED", "DEPOSIT_CASH_RESERVED" -> "This marked cash has no available issued value, or is reserved by another transaction.";
            case "DEPOSIT_COMPONENT_BUDGET", "DEPOSIT_FIELD", "JOURNAL_REQUEST_BOUNDS" -> "This batch has too much item data. Try fewer cash stacks.";
            case "DEPOSIT_INPUT_CHANGED" -> "The cash changed after review. Review the current cash again.";
            default -> "Deposit could not be reviewed or completed. Your cash remains protected; retry if prompted.";
        };
    }
    static BlockPos position(BankingMenu menu) { return new BlockPos(menu.anchor().x(), menu.anchor().y(), menu.anchor().z()); }
    static void withContext(ServerPlayer player, BankingMenu menu, Consumer<LedgerService.BankingContext> action) {
        menu.setBusy(true); var revision = menu.revision();
        services(player).ledger().bankingContext(actor(player), menu.anchor(), BankingAccounts.selected(menu)).whenComplete((context, error) -> {
            if (!menu.valid(player) || menu.revision() != revision) return;
            if (error != null) { fail(menu, error); return; }
            try { FLOWS.computeIfAbsent(menu, ignored -> new Flow()).context = context; action.accept(context); }
            catch (RuntimeException failure) { fail(menu, failure); }
        });
    }
    public static Journal.Intent intent(ServerPlayer player, Journal.Kind kind, Journal.CanonicalRequest request) {
        return new Journal.Intent(new OperationId(UUID.randomUUID()), new RequestId(UUID.randomUUID()), actor(player), kind, request, null, null);
    }
    /** A same-menu retry rebuilds detached admission but retains the exact canonical operation. */
    public static void submit(ServerPlayer player, BankingMenu menu, Journal.Intent intent,
                              Supplier<JournalService> factory, Consumer<Journal.Outcome> completed) {
        var flow = FLOWS.computeIfAbsent(menu, ignored -> new Flow());
        if (flow.inFlight) return;
        boolean reachedSubmission = false;
        var oldPending = player.getPersistentData().getCompound(PENDING);
        boolean retainedOriginal = oldPending.hasUUID("operation") && oldPending.getUUID("operation").equals(intent.operationId().value());
        try {
            if (player.getPersistentData().getCompound("economy_banking_pending").hasUUID("operation"))
                throw new IllegalStateException("Resolve the pending banking operation first.");
            var pending = player.getPersistentData().getCompound(PENDING);
            if (pending.hasUUID("operation") && !pending.getUUID("operation").equals(intent.operationId().value()))
                throw new IllegalStateException("Resolve the pending operation before starting another.");
            var journal = factory.get(); // A local pre-admission failure must never manufacture an unknown operation.
            var tag = new CompoundTag(); tag.putUUID("world", services(player).worldId().value()); tag.putUUID("actor", player.getUUID()); tag.putUUID("operation", intent.operationId().value()); tag.putUUID("request", intent.requestId().value());
            tag.putString("service", intent.kind().service()); tag.putString("type", intent.kind().operationType());
            var fields = new CompoundTag(); intent.request().fields().forEach(fields::putString); tag.put("fields", fields);
            player.getPersistentData().put(PENDING, tag);
            menu.setBusy(true);
            flow.retry = () -> submit(player, menu, intent, factory, completed);
            inputsChanged(player, menu);
            boolean cleanup = Set.of("economy:cancel", "economy:quarantine", "economy:saved", "economy:claim", "economy:delivery_saved", "economy:begin_delivery", "economy:collect", "economy:complete").contains(intent.kind().operationType());
            if (player.server.getPlayerList().getPlayer(player.getUUID()) == player && !cleanup)
                BankingSaves.player(player, services(player).worldId(), intent.operationId().value());
            else if (player.server.getPlayerList().getPlayer(player.getUUID()) != player && !Set.of("economy:cancel", "economy:quarantine", "economy:saved", "economy:claim", "economy:delivery_saved").contains(intent.kind().operationType()))
                throw new IllegalStateException("Player session ended before admission");
            flow.inFlight = true;
            reachedSubmission = true;
            journal.submit(intent).completion().whenComplete((delivery, error) -> {
                flow.inFlight = false;
                if (error != null) { fail(menu, error); return; }
                var outcome = delivery.outcome();
                if (outcome.status() == Journal.Status.COMMITTED || outcome.status() == Journal.Status.DURABLY_REJECTED) {
                    var pendingNow = player.getPersistentData().getCompound(PENDING);
                    if (pendingNow.hasUUID("operation") && pendingNow.getUUID("operation").equals(intent.operationId().value())) player.getPersistentData().remove(PENDING);
                    flow.retry = null;
                    try { completed.accept(outcome); } catch (RuntimeException failure) { fail(menu, failure); }
                } else report(menu, menu.page(), menu.lines(), outcome.result().code() + " — retry preserves operation " + intent.operationId().value());
            });
        } catch (RuntimeException error) {
            flow.inFlight = false;
            if (!reachedSubmission && !retainedOriginal) {
                var current = player.getPersistentData().getCompound(PENDING);
                if (current.hasUUID("operation") && current.getUUID("operation").equals(intent.operationId().value())) player.getPersistentData().remove(PENDING);
                flow.retry = null;
                if (intent.kind().operationType().equals("economy:reserve") && Objects.equals(flow.reservation, intent.operationId().value())) {
                    flow.reservation = null; flow.ownsInputs = false;
                }
            }
            fail(menu, error);
        }
    }
    private static void lookupPending(ServerPlayer player, BankingMenu menu) {
        var tag = player.getPersistentData().getCompound(PENDING);
        if (tag.hasUUID("world") && (!tag.getUUID("world").equals(services(player).worldId().value()) || !tag.getUUID("actor").equals(player.getUUID())))
            throw new IllegalStateException("Pending operation belongs to a different saved world or actor.");
        if (!tag.hasUUID("operation")) { report(menu, menu.page(), menu.lines(), "No pending operation"); return; }
        var fields = new TreeMap<String,String>(); var stored = tag.getCompound("fields"); stored.getAllKeys().forEach(key -> fields.put(key, stored.getString(key)));
        var original = new Journal.Intent(new OperationId(tag.getUUID("operation")), new RequestId(tag.getUUID("request")), actor(player),
                new Journal.Kind(tag.getString("service"), tag.getString("type")), new Journal.CanonicalRequest(1, fields), null, null);
        services(player).journal((who, kind) -> who.equals(actor(player)), Map.of(), null).lookup(original).whenComplete((found, error) -> {
            if (error != null) { fail(menu, error); return; }
            if (found.isPresent() && (found.orElseThrow().status() == Journal.Status.COMMITTED || found.orElseThrow().status() == Journal.Status.DURABLY_REJECTED)) {
                player.getPersistentData().remove(PENDING);
                report(menu, "claims", List.of("Original operation: " + original.operationId().value(), found.orElseThrow().result().code(), "Inspect Claims for retained delivery. Unknown saved inventories require operator review."), "Original outcome resolved");
            }
            else if (found.isEmpty()) {
                player.getPersistentData().put("economy_banking_cash_last_absent", tag.copy());
                player.getPersistentData().remove(PENDING);
                report(menu, "claims", List.of("The ordered original-outcome lookup confirms no committed result.",
                        "No replacement operation was submitted. Retained originals still require their current claim or evidence review."), "Original operation was not committed");
            } else report(menu, menu.page(), menu.lines(), "The original request conflicts. Its identity is retained for operator review.");
        });
    }
    private static void claims(ServerPlayer player, BankingMenu menu, Flow flow, boolean next) {
        if (next && flow.claimCursor == null) { report(menu, "claims", menu.lines(), "End of claims"); return; }
        if (!next) flow.claimCursor = null;
        menu.setBusy(true);
        services(player).ledger().custodyClaims(actor(player), flow.claimCursor, 8).whenComplete((page, error) -> {
            if (error != null) { fail(menu, error); return; }
            if (!menu.valid(player)) return;
            flow.claimOrder = page.cases().stream().map(Custody.Case::id).toList();
            flow.claims = page.cases().stream().map(Custody.Case::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
            flow.claimCursor = page.nextCursor();
            var choices = new ArrayList<BankingNetwork.Choice>();
            for (var value : page.cases()) {
                String kind = value.terms().items().split("[|\\n]", 2)[0];
                String label = claimLabel(value, kind);
                choices.add(new BankingNetwork.Choice("collect", value.id().toString(), label, false));
            }
            menu.setBusy(false);
            menu.publish("claims", choices.isEmpty() ? List.of("You have no items waiting to be collected.")
                    : List.of("Click a batch to put it in your inventory.", "Make room for the complete batch before collecting."),
                    choices.isEmpty() ? "Nothing to collect" : "Click a batch to collect. Make room in your inventory first.", choices);
        });
    }
    private static String claimLabel(Custody.Case value, String kind) {
        String label = switch (kind) {
            case "register-goods-v1" -> "Purchased items";
            case "withdrawal-cash-v1" -> "Cash withdrawal";
            case "deposit-original-v1" -> "Returned deposit cash";
            case "issued-card-v1" -> "Issued card";
            default -> "Returned items";
        };
        try {
            var fields = value.terms().items().split("[|\\n]", -1);
            int count = switch (kind) {
                case "register-goods-v1" -> Integer.parseInt(fields[1]);
                case "deposit-original-v1" -> Integer.parseInt(fields[4]);
                case "withdrawal-cash-v1" -> WithdrawalDelivery.claims(value).stream().mapToInt(CashIssuance.Claim::count).sum();
                default -> 0;
            };
            return count > 0 ? label + " (" + count + (count == 1 ? " item)" : " items)") : label;
        } catch (IllegalArgumentException | IndexOutOfBoundsException unavailable) { return label; }
    }
    private static void depositPreview(ServerPlayer player, BankingMenu menu, Flow flow) {
        withContext(player, menu, context -> {
            if (context.account() == null) throw new IllegalStateException("DEPOSIT_ACCOUNT");
            if (context.terminal() == null) throw new IllegalStateException("DEPOSIT_TERMINAL");
            if (context.fx() == null) throw new IllegalStateException("DEPOSIT_FX_STALE");
            var binding = new Deposits.Binding(context.account().id().value(), context.account().revision(), context.account().currency(),
                    menu.anchor(), menu.generation(), context.terminal().revision(), context.fx().revision());
            var slots = DepositInventory.snapshot(player, menu.depositInputs(), context.world());
            boolean live = menu.valid(player) && menu.page().equals("deposit");
            services(player).ledger().previewDeposit(actor(player), binding, slots, (b, reads) -> live).whenComplete((preview, error) -> {
                if (error != null) { fail(menu, error); return; }
                if (!menu.valid(player)) return;
                flow.deposit = preview;
                var lines = new ArrayList<String>();
                lines.add("Bank credit: " + money(preview.credit()));
                for (var group : preview.groups()) {
                    lines.add(group.count() + " items " + money(group.subtotal()) + " → " + (group.skipped() ? "returned unchanged" : money(group.credit())));
                    if(group.pricing()!=null&&!group.skipped()) {
                        var price=group.pricing().split("\\|",-1);
                        lines.add("Source principal: "+money(new Money(group.currency(),Long.parseLong(price[1])))+"; FX fee: "+money(new Money(group.currency(),Long.parseLong(price[3]))));
                    }
                }
                if (preview.credit().minorUnits() == 0) {
                    boolean chfAccount = preview.credit().currency() == EconomyCurrency.CHF;
                    boolean chfGroup = preview.groups().stream().anyMatch(group -> group.currency() == EconomyCurrency.CHF);
                    if (chfAccount || chfGroup) {
                        String minimum = money(new Money(preview.credit().currency(), 1));
                        lines.add((chfAccount ? "Each currency group" : "Each CHF group")
                                + " must credit at least " + minimum + (chfAccount ? "." : ". Other legacy groups retain whole-unit rounding."));
                        report(menu, "deposit", lines, "No credit yet. Add more cash to a currency group.");
                    } else {
                        lines.add("Each currency group must credit at least 1 " + preview.credit().currency().code() + ". Add more cash.");
                        report(menu, "deposit", lines, "No credit yet. Add cash worth at least 1 " + preview.credit().currency().code() + ".");
                    }
                } else report(menu, "deposit", lines, "Preview ready. Confirm to deposit this cash.");
                flow.depositRevision = menu.revision();
            });
        });
    }
    private static void depositConfirm(ServerPlayer player, BankingMenu menu, Flow flow) {
        var preview = Objects.requireNonNull(flow.deposit, "Preview the deposit first.");
        if (!menu.page().equals("deposit") || menu.revision() != flow.depositRevision || flow.reservation != null)
            throw new IllegalStateException("This preview expired. Preview the deposit again.");
        if (!DepositInventory.snapshot(player, menu.depositInputs(), flow.context.world()).equals(preview.slots())) throw new IllegalStateException("Inputs changed; preview again.");
        var reserve = intent(player, Deposits.Action.RESERVE.kind(), Deposits.reserveRequest(preview));
        flow.reservation = reserve.operationId().value(); flow.ownsInputs = true;
        submit(player, menu, reserve, () -> depositJournal(player, menu, Deposits.Action.RESERVE, false, false), outcome -> {
            if (outcome.status() != Journal.Status.COMMITTED) {
                flow.reservation = null; flow.ownsInputs = false;
                if (!menu.valid(player) && player.server.getPlayerList().getPlayer(player.getUUID()) == player) returnInputs(player, menu);
                report(menu, "deposit", menu.lines(), outcome.result().code()); return;
            }
            try {
                if (player.server.getPlayerList().getPlayer(player.getUUID()) != player) { quarantineDeposit(player, menu, flow); return; }
                DepositInventory.reserve(player, menu.depositInputs(), flow.context.world(), flow.reservation, preview.slots());
                flow.ownsInputs = false;
                inputsChanged(player, menu);
                var proof = BankingSaves.player(player, flow.context.world(), flow.reservation);
                if (!menu.valid(player)) { cancelDeposit(player, menu, flow, proof); return; }
                var saved = intent(player, Deposits.Action.SAVED.kind(), Deposits.request(flow.reservation, proof.token(), "saved exact originals"));
                submit(player, menu, saved, () -> depositJournal(player, menu, Deposits.Action.SAVED, true, true), savedOutcome -> {
                    if (savedOutcome.status() != Journal.Status.COMMITTED) { report(menu, "deposit", menu.lines(), savedOutcome.result().code()); return; }
                    if (!menu.valid(player)) { cancelDeposit(player, menu, flow, proof); return; }
                    var commit = intent(player, Deposits.Action.COMMIT.kind(), Deposits.request(flow.reservation, proof.token(), "confirmed deposit"));
                    submit(player, menu, commit, () -> depositJournal(player, menu, Deposits.Action.COMMIT, false, true), committed -> {
                        if (committed.status() != Journal.Status.COMMITTED) { cancelDeposit(player, menu, flow, proof); return; }
                        DepositInventory.consumeCommitted(player, flow.reservation, preview.payable());
                        BankingSaves.player(player, flow.context.world(), flow.reservation);
                        finishDepositReturns(player, menu, flow, returns -> report(menu, "deposit", depositReturnMessages(returns), "Thank you for using our banking service."));
                    });
                });
            } catch (RuntimeException error) { quarantineDeposit(player, menu, flow); fail(menu, error); }
        });
    }
    private static JournalService depositJournal(ServerPlayer player, BankingMenu menu, Deposits.Action action, boolean cleanup, boolean proof) {
        boolean live = cleanup || (menu.valid(player) && menu.page().equals("deposit"));
        var owner = actor(player);
        return services(player).ledger().deposits(action, (who, kind) -> who.equals(owner),
                (a, i, current, reads) -> live && i.actor().equals(owner) && current.owner().equals(owner),
                (a, i, current, evidence, reads) -> proof, null);
    }
    private static void cancelDeposit(ServerPlayer player, BankingMenu menu, Flow flow, BankingSaves.Proof proof) {
        submit(player, menu, intent(player, Deposits.Action.CANCEL.kind(), Deposits.request(flow.reservation, proof.token(), "closed before settlement")),
                () -> depositJournal(player, menu, Deposits.Action.CANCEL, true, true), outcome -> {
                    if (outcome.status() == Journal.Status.COMMITTED) finishDepositReturns(player, menu, flow, returns -> report(menu, "deposit", List.of("Your cash was returned or is waiting in Collect items."), "Cancelled"));
                    else report(menu, "deposit", menu.lines(), outcome.result().code());
                });
    }
    private static void quarantineDeposit(ServerPlayer player, BankingMenu menu, Flow flow) {
        if (flow.reservation == null) return;
        submit(player, menu, intent(player, Deposits.Action.QUARANTINE.kind(), Deposits.request(flow.reservation, "unknown", "save or physical effect uncertain")),
                () -> depositJournal(player, menu, Deposits.Action.QUARANTINE, true, false), outcome -> report(menu, "claims", List.of("Uncertain originals are held for operator evidence review."), outcome.result().code()));
    }
    static List<String> depositReturnMessages(List<DepositReturns.Return> returns) {
        var lines = new ArrayList<String>();
        if (returns.stream().anyMatch(value -> value.delivered() > 0))
            lines.add("Unredeemable bills were returned.");
        if (returns.stream().anyMatch(value -> value.state() == DepositReturns.State.CLAIMED))
            lines.add("Remaining bills are waiting in Collect items.");
        return List.copyOf(lines);
    }
    private static void finishDepositReturns(ServerPlayer player, BankingMenu menu, Flow flow, Consumer<List<DepositReturns.Return>> finished) {
        services(player).ledger().depositReturns(actor(player), flow.reservation).whenComplete((returns, error) -> {
            if (error != null) { fail(menu, error); return; }
            var pending = returns.stream().filter(value -> value.state() == DepositReturns.State.PENDING).findFirst();
            if (pending.isEmpty()) {
                try { DepositInventory.clearDelivered(player, flow.reservation); } catch (IllegalStateException claimedOriginals) { /* Claims retain their exact original stacks. */ }
                BankingSaves.player(player, flow.context.world(), flow.reservation);
                flow.reservation = null; flow.deposit = null; finished.accept(returns); return;
            }
            var value = pending.orElseThrow();
            var begin = intent(player, DepositReturns.Action.BEGIN.kind(), DepositReturns.request(value.deposit(), value.slot(), value.revision(), UUID.randomUUID(), value.count(),
                    new DepositReturns.Evidence(DepositReturns.Proof.UNDELIVERED, "held original"), "direct original return"));
            // BEGIN attempt is the operation identity, never a client value.
            begin = new Journal.Intent(begin.operationId(), begin.requestId(), begin.actor(), begin.kind(),
                    DepositReturns.request(value.deposit(), value.slot(), value.revision(), begin.operationId().value(), value.count(),
                            new DepositReturns.Evidence(DepositReturns.Proof.UNDELIVERED, "held original"), "direct original return"), null, null);
            var start = begin;
            boolean held = depositHeld(player, value);
            submit(player, menu, start, () -> returnJournal(player, DepositReturns.Action.BEGIN, held), begun -> {
                if (begun.status() != Journal.Status.COMMITTED) { report(menu, "claims", menu.lines(), begun.result().code()); return; }
                var returned = DepositInventory.returnOnce(player, value.deposit(), value.slot(), start.operationId().value(), value.count());
                var proof = BankingSaves.player(player, flow.context.world(), start.operationId().value());
                var complete = intent(player, DepositReturns.Action.COMPLETE.kind(), DepositReturns.request(value.deposit(), value.slot(), value.revision() + 1,
                        start.operationId().value(), returned.delivered(), new DepositReturns.Evidence(DepositReturns.Proof.SAVED_AND_UNDELIVERED, proof.token()), "saved direct return and remainder"));
                submit(player, menu, complete, () -> returnJournal(player, DepositReturns.Action.COMPLETE, true), completed -> {
                    if (completed.status() == Journal.Status.COMMITTED) finishDepositReturns(player, menu, flow, finished);
                    else report(menu, "claims", menu.lines(), completed.result().code());
                });
            });
        });
    }
    private static boolean depositHeld(ServerPlayer player, DepositReturns.Return value) {
        var tag = player.getPersistentData().getCompound("economy_deposit_originals").getCompound(value.deposit().toString()).getCompound(Integer.toString(value.slot()));
        var stack = net.minecraft.world.item.ItemStack.parseOptional(player.registryAccess(), tag.getCompound("stack"));
        return !stack.isEmpty() && stack.getCount() == value.count() && BankingInventory.encode(player, stack).equals(value.components());
    }
    private static JournalService returnJournal(ServerPlayer player, DepositReturns.Action action, boolean proof) {
        var owner = actor(player);
        return services(player).ledger().depositReturns(action, (who, kind) -> who.equals(owner),
                (a, i, current, reads) -> current.owner().equals(owner), (a, current, delivered, evidence, reads) -> proof, null);
    }
    private static void salePreview(ServerPlayer player, BankingMenu menu, Flow flow) {
        withContext(player, menu, context -> {
            if (context.price() == null || context.terminal() == null || context.terminal().kind() != TerminalPolicy.Kind.REGISTER) throw new IllegalStateException("This register has no configured sale.");
            var template = RegisterInventory.decodeTemplate(player.server, context.price().template());
            flow.stock = RegisterInventory.snapshot(player.server, menu.anchor().dimension(), position(menu), template, context.price().batch());
            var source = source(flow.stock); boolean live = menu.valid(player);
            services(player).ledger().quoteSale(actor(player), menu.anchor(), menu.generation(), context.terminal().revision(),
                    (below, item, reads) -> source, (anchor, reads) -> live, dev.emily.economy.foundation.CreditUse.heldToken(player, context.world()) != null).whenComplete((quote, error) -> {
                if (error != null) { fail(menu, error); return; }
                if (!menu.valid(player)) return;
                flow.quote = quote;
                var lines = new ArrayList<>(List.of("Product " + template.getHoverName().getString(), "Batch " + quote.price().batch(), "Price " + money(quote.price().amount()), "Available " + source.quantity()));
                flow.credit = null; flow.creditProof = null;
                var token = dev.emily.economy.foundation.CreditUse.heldToken(player, context.world());
                if (token == null) {
                    report(menu, "shop", lines, "Review the full batch, then Confirm");
                    flow.saleRevision = menu.revision();
                } else {
                    var audience = dev.emily.economy.foundation.FinanceMenuSupport.audience(player, menu);
                    var grant = dev.emily.economy.foundation.FinanceMenuSupport.grant(player, services(player));
                    menu.setBusy(true);
                    services(player).ledger().quoteCreditDraw(grant, token, menu.anchor(), menu.generation(), null, 0, true).whenComplete((credit, failure) -> {
                        if (!audience.live()) return;
                        if (failure != null || credit.isEmpty()) { fail(menu, failure == null ? new IllegalArgumentException("Credit unavailable") : failure); return; }
                        flow.credit = credit.orElseThrow();
                        lines.add("Pay using held credit card. Purchase APR " + flow.credit.facility().purchase().apr().multiply(new java.math.BigDecimal("100")).toPlainString() + "%");
                        lines.add("Interest accrues daily. Statement every 28 days, minimum due 14 days later.");
                        lines.add("Minimum is 2% of principal plus period interest, capped by debt. No fixed floor.");
                        lines.add("Missed requirements may default after 84 days. No purchase fee.");
                        report(menu, "shop", lines, "Review credit purchase, then Confirm");
                        flow.saleRevision = menu.revision();
                    });
                }
            });
        });
    }
    private static RegisterSales.Source source(RegisterInventory.Snapshot stock) {
        return new RegisterSales.Source(stock.sourceGeneration(), stock.revision(), stock.available(), stock.template(), true, stock.fingerprint());
    }
    private static void saleConfirm(ServerPlayer player, BankingMenu menu, Flow flow) {
        var quote = Objects.requireNonNull(flow.quote, "Preview this sale first.");
        if (!menu.page().equals("shop") || menu.revision() != flow.saleRevision || flow.reservation != null)
            throw new IllegalStateException("This sale preview expired. Preview again.");
        if (flow.credit != null) flow.creditProof = dev.emily.economy.credit.CreditDrawAdmission.capture(player, menu, flow.credit.request(), flow.credit.terminal());
        var reserve = intent(player, RegisterSales.Action.RESERVE.kind(), RegisterSales.reserveRequest(quote, flow.credit != null));
        flow.reservation = reserve.operationId().value();
        submit(player, menu, reserve, () -> saleJournal(player, menu, flow, RegisterSales.Action.RESERVE, false, false), outcome -> {
            if (outcome.status() != Journal.Status.COMMITTED) { flow.reservation = null; report(menu, "shop", menu.lines(), outcome.result().code()); return; }
            if (!menu.valid(player)) {
                submit(player, menu, intent(player, RegisterSales.Action.CANCEL.kind(), RegisterSales.request(flow.reservation, "not-removed", "closed before removal")),
                        () -> saleJournal(player, menu, flow, RegisterSales.Action.CANCEL, true, true), cancelled -> { if (cancelled.status() == Journal.Status.COMMITTED) releaseFlow(menu, flow.reservation); report(menu, "shop", menu.lines(), cancelled.result().code()); }); return;
            }
            try {
                flow.stock = RegisterInventory.reserve(player.server, player, flow.stock, flow.reservation);
                BankingSaves.source(player, position(menu).below(), flow.context.world(), flow.reservation).whenComplete((proof, error) -> {
                    if (error != null) { quarantineSale(player, menu, flow); fail(menu, error); return; }
                    var saved = intent(player, RegisterSales.Action.SAVED.kind(), RegisterSales.request(flow.reservation, proof.token(), "source originals saved"));
                    submit(player, menu, saved, () -> saleJournal(player, menu, flow, RegisterSales.Action.SAVED, true, true), savedOutcome -> {
                        if (savedOutcome.status() != Journal.Status.COMMITTED) { quarantineSale(player, menu, flow); return; }
                        if (!menu.valid(player)) { cancelSale(player, menu, flow); return; }
                        var request = RegisterSales.request(flow.reservation, proof.token(), "confirmed exact stock");
                        if (flow.credit != null) {
                            var fields = new java.util.TreeMap<>(request.fields());
                            fields.put("credit_request", new java.util.TreeMap<>(flow.credit.request().request().fields()).toString());
                            request = new Journal.CanonicalRequest(request.version(), fields);
                        }
                        var commit = intent(player, RegisterSales.Action.COMMIT.kind(), request);
                        submit(player, menu, commit, () -> saleJournal(player, menu, flow, RegisterSales.Action.COMMIT, false, true), committed -> {
                            if (committed.status() == Journal.Status.COMMITTED) deliver(player, menu, flow.context.world(), flow.reservation);
                            else cancelSale(player, menu, flow);
                        });
                    });
                });
            } catch (RuntimeException failed) { quarantineSale(player, menu, flow); fail(menu, failed); }
        });
    }
    private static JournalService saleJournal(ServerPlayer player, BankingMenu menu, Flow flow, RegisterSales.Action action, boolean cleanup, boolean proof) {
        var snapshot = action == RegisterSales.Action.QUARANTINE ? flow.stock :
                RegisterInventory.snapshot(player.server, menu.anchor().dimension(), position(menu), RegisterInventory.decodeTemplate(player.server, flow.quote.price().template()), flow.quote.price().batch());
        var observed = source(snapshot); boolean live = cleanup || menu.valid(player);
        boolean fits = RegisterInventory.fitsBuyer(player, RegisterInventory.decodeTemplate(player.server, snapshot.template()), snapshot.batch());
        var owner = actor(player);
        return services(player).ledger().registerSales(action, (who, kind) -> who.equals(owner),
                (a, i, current, reads) -> live && i.actor().equals(owner), (who, reads) -> false,
                (a, i, current, reads) -> fits, (below, template, reads) -> observed,
                (a, i, current, evidence, reads) -> proof, null,
                flow.credit == null || action != RegisterSales.Action.COMMIT ? RegisterSaleSettlement::checkout
                    : services(player).ledger().creditCheckout(dev.emily.economy.foundation.FinanceMenuSupport.grant(player, services(player)), flow.credit.request(), flow.creditProof));
    }
    private static void cancelSale(ServerPlayer player, BankingMenu menu, Flow flow) {
        // Known held originals become an owner-bound seller claim; no speculative source insertion.
        boolean held = RegisterInventory.reservationMatches(player.server, flow.stock, flow.reservation);
        if (!held) { quarantineSale(player, menu, flow); return; }
        submit(player, menu, intent(player, RegisterSales.Action.CANCEL.kind(), RegisterSales.request(flow.reservation, "held", "cancelled before payment")),
                () -> saleJournal(player, menu, flow, RegisterSales.Action.CANCEL, true, true), cancelled -> {
                    if (cancelled.status() == Journal.Status.COMMITTED) releaseFlow(menu, flow.reservation);
                    report(menu, "claims", List.of("Unpaid originals are retained for the seller."), cancelled.result().code());
                });
    }
    private static void quarantineSale(ServerPlayer player, BankingMenu menu, Flow flow) {
        submit(player, menu, intent(player, RegisterSales.Action.QUARANTINE.kind(), RegisterSales.request(flow.reservation, "unknown", "source or outcome uncertain")),
                () -> saleJournal(player, menu, flow, RegisterSales.Action.QUARANTINE, true, false), outcome -> {
                    if (outcome.status() == Journal.Status.COMMITTED) releaseFlow(menu, flow.reservation);
                    report(menu, "claims", List.of("Uncertain source originals require operator evidence review."), outcome.result().code());
                });
    }
    private static void price(ServerPlayer player, BankingMenu menu, String value) {
        long units = Long.parseLong(value.trim()); if (units < 1) throw new IllegalArgumentException("Positive whole-unit price required.");
        var product = player.getMainHandItem().copy(); if (product.isEmpty()) throw new IllegalArgumentException("Hold the exact product batch.");
        boolean operator = player.hasPermissions(2);
        menu.setBusy(true);
        services(player).ledger().registerPriceContext(actor(player), menu.anchor(), (who, reads) -> operator).whenComplete((found, error) -> {
            if (error != null) { fail(menu, error); return; }
            if (!menu.valid(player) || found.isEmpty()) { report(menu, "shop", List.of("An owned register and seller primary are required."), "Not authorized"); return; }
            var context = found.orElseThrow();
            RegisterInventory.initializeSource(player.server, menu.anchor().dimension(), position(menu));
            var stock = RegisterInventory.snapshot(player.server, menu.anchor().dimension(), position(menu), product, product.getCount());
            var request = RegisterSales.configureRequest(menu.anchor(), menu.generation(), context.terminal().revision(),
                    context.price() != null && context.price().generation().equals(menu.generation()) ? context.price().revision() : 0,
                    stock.template(), stock.batch(), new Money(context.seller().currency(), Math.multiplyExact(units, context.seller().currency().minorUnitsPerMajor())));
            submit(player, menu, intent(player, RegisterSales.Action.CONFIGURE.kind(), request), () -> {
                boolean live = menu.valid(player); boolean op = player.hasPermissions(2); var owner = actor(player);
                return services(player).ledger().registerSales(RegisterSales.Action.CONFIGURE, (who, kind) -> who.equals(owner),
                        (a, i, current, reads) -> live, (who, reads) -> op, (a, i, c, reads) -> false,
                        (below, template, reads) -> null, (a, i, c, proof, reads) -> false, null);
            }, outcome -> report(menu, "shop", List.of("Product: " + product.getHoverName().getString(), "Batch: " + product.getCount()), outcome.result().code()));
        });
    }
    /** Financial success is resolved first; only a current custody state can authorize physical delivery. */
    public static void deliver(ServerPlayer player, BankingMenu menu, WorldId world, UUID custody) {
        services(player).ledger().custodyCase(actor(player), custody, (who, reads) -> false).whenComplete((found, error) -> {
            if (error != null) { fail(menu, error); return; }
            if (found.isEmpty()) { report(menu, "claims", List.of(), "Delivery case unavailable"); return; }
            var current = found.orElseThrow();
            if (current.state() != Custody.State.COMMITTED && current.state() != Custody.State.CLAIM) {
                report(menu, "claims", List.of("Current delivery: " + current.state(), "No replayed outcome authorizes another item delivery."), "Delivery requires current evidence"); return;
            }
            if (current.terms().items().startsWith("register-goods-v1")) {
                var source = current.terms().source().split(":", -1);
                var saleId = UUID.fromString(source[source.length - 1]);
                services(player).ledger().registerSale(actor(player), saleId).whenComplete((sale, failure) -> {
                    if (failure != null || sale.isEmpty()) { report(menu, "claims", List.of(), "Original stock record unavailable"); return; }
                    var reservation = sale.orElseThrow(); var price = reservation.quote().price();
                    try {
                        if (!price.anchor().dimension().equals(player.serverLevel().dimension().location().toString())) throw new IllegalStateException("Visit the original stock dimension to collect.");
                        var stock = RegisterInventory.snapshot(player.server, price.anchor().dimension(), new BlockPos(price.anchor().x(), price.anchor().y(), price.anchor().z()),
                                RegisterInventory.decodeTemplate(player.server, price.template()), price.batch());
                        if (!stock.sourceGeneration().equals(reservation.quote().source().generation()) || !RegisterInventory.reservationMatches(player.server, stock, saleId))
                            throw new IllegalStateException("Original stock is unavailable; operator evidence review is required.");
                        startDelivery(player, menu, world, current, stock, saleId);
                    } catch (RuntimeException invalid) { fail(menu, invalid); }
                });
            } else startDelivery(player, menu, world, current, null, null);
        });
    }
    private static boolean capacity(ServerPlayer player, Custody.Case value, RegisterInventory.Snapshot stock) {
        if(value.terms().items().startsWith("issued-card-v1")) {dev.emily.economy.card.CardInventory.decode(value);return dev.emily.economy.card.CardInventory.fits(player);}
        if (stock != null) return RegisterInventory.fitsBuyer(player, RegisterInventory.decodeTemplate(player.server, stock.template()), stock.batch());
        if (value.terms().items().startsWith("withdrawal-cash-v1")) return WithdrawalInventory.fits(player, WithdrawalDelivery.claims(value).size());
        if (BankingInventory.supports(value)) return BankingInventory.fits(player,
                value.terms().items().startsWith("deposit-original-v1|") ? BankingInventory.depositClaimItems(player, value)
                        : List.of(BankingInventory.decode(player, value.terms().items().substring("menu-original-v1|".length()))));
        return false;
    }
    private static void startDelivery(ServerPlayer player, BankingMenu menu, WorldId world, Custody.Case current, RegisterInventory.Snapshot stock, UUID sale) {
        boolean online = player.server.getPlayerList().getPlayer(player.getUUID()) == player && menu.valid(player);
        if (!online || !capacity(player, current, stock)) {
            if (current.state() == Custody.State.CLAIM) { report(menu, "claims", List.of("The complete batch must fit in your main inventory."), "Claim remains protected"); return; }
            var evidence = new Custody.Evidence(Custody.EvidenceKind.UNDELIVERED, "not materialized", current.terms().destination(), "admitted-no-insertion");
            custodyStep(player, menu, current, Custody.Action.CLAIM, null, evidence, true, true,
                    outcome -> { if (outcome.status() == Journal.Status.COMMITTED) releaseFlow(menu, current.id());
                        report(menu, "claims", List.of("Paid goods remain in your owner-bound claim."), outcome.result().code()); }); return;
        }
        var action = current.state() == Custody.State.CLAIM ? Custody.Action.COLLECT : Custody.Action.BEGIN_DELIVERY;
        var attempt = UUID.randomUUID();
        var request = Custody.request(current.id(), attempt, Custody.Evidence.unknown(), "full batch delivery");
        var begin = new Journal.Intent(new OperationId(attempt), new RequestId(UUID.randomUUID()), actor(player), action.kind(), request, null, null);
        submit(player, menu, begin, () -> {
            boolean admitted = menu.valid(player) && capacity(player, current, stock); var owner = actor(player);
            return services(player).ledger().custody(action, (who, kind) -> who.equals(owner),
                    (a, i, value, reads) -> admitted && value.terms().owner().equals(owner), (value, evidence, reads) -> false,
                    null, (i, value, reads) -> admitted, null);
        }, outcome -> {
            if (outcome.status() != Journal.Status.COMMITTED) { report(menu, "claims", menu.lines(), outcome.result().code()); return; }
            services(player).ledger().custodyCase(actor(player), current.id(), (who, reads) -> false).whenComplete((fresh, error) -> {
                if (error != null || fresh.isEmpty()) { fail(menu, new IllegalStateException("Current delivery attempt unavailable")); return; }
                var delivering = fresh.orElseThrow();
                if (delivering.state() != Custody.State.DELIVERING || !attempt.equals(delivering.deliveryAttempt())) { report(menu, "claims", menu.lines(), "Delivery attempt changed"); return; }
                if(delivering.terms().items().startsWith("issued-card-v1") && (!menu.valid(player)
                        || player.server.getPlayerList().getPlayer(player.getUUID())!=player)) {
                    var undelivered=new Custody.Evidence(Custody.EvidenceKind.UNDELIVERED,"card-insertion-not-started",delivering.terms().destination(),"server-before-insertion");
                    custodyStep(player,menu,delivering,Custody.Action.CLAIM,attempt,undelivered,true,true,ignoredCardClaim->{});
                    return;
                }
                boolean delivered;
                try {
                    if (player.server.getPlayerList().getPlayer(player.getUUID()) != player) delivered = false;
                    else if (stock != null) delivered = RegisterInventory.deliverOnce(player.server, player, stock, sale, attempt) != RegisterInventory.Result.UNDELIVERED;
                    else if(delivering.terms().items().startsWith("issued-card-v1"))delivered=dev.emily.economy.card.CardInventory.deliverOnce(player,world,delivering)!=dev.emily.economy.card.CardInventory.Result.UNDELIVERED;
                    else if (delivering.terms().items().startsWith("withdrawal-cash-v1")) delivered = WithdrawalInventory.deliverOnce(player, delivering) != WithdrawalInventory.Result.UNDELIVERED;
                    else delivered = BankingInventory.deliverOnce(player, delivering);
                    var proof = BankingSaves.player(player, world, attempt);
                    if (!delivered) {
                        var evidence = new Custody.Evidence(Custody.EvidenceKind.UNDELIVERED, proof.reference(), delivering.terms().destination(), proof.token());
                        custodyStep(player, menu, delivering, Custody.Action.CLAIM, attempt, evidence, true, true,
                                claimed -> { if (claimed.status() == Journal.Status.COMMITTED) releaseFlow(menu, current.id());
                                    report(menu, "claims", List.of("Nothing was inserted; the whole batch remains claimable."), claimed.result().code()); }); return;
                    }
                    if (stock == null) completeDelivery(player, menu, world, delivering, proof, null, null);
                    else BankingSaves.source(player, stock.anchor().below(), world, attempt).whenComplete((sourceProof, failure) -> {
                        if (failure != null) { quarantineDelivery(player, menu, delivering); fail(menu, failure); }
                        else {
                            var sourceHash = sourceProof.token().substring(sourceProof.token().lastIndexOf(':') + 1);
                            var paired = new BankingSaves.Proof(proof.reference() + "|source=" + sourceProof.reference() + "#" + sourceHash,
                                    proof.location(), proof.token());
                            completeDelivery(player, menu, world, delivering, paired, stock, sale);
                        }
                    });
                } catch (RuntimeException uncertain) { quarantineDelivery(player, menu, delivering); fail(menu, uncertain); }
            });
        });
    }
    private static void completeDelivery(ServerPlayer player, BankingMenu menu, WorldId world, Custody.Case current,
                                         BankingSaves.Proof proof, RegisterInventory.Snapshot stock, UUID sale) {
        var evidence = new Custody.Evidence(Custody.EvidenceKind.DELIVERY_SAVED, proof.reference(), current.terms().destination(), proof.token());
        custodyStep(player, menu, current, Custody.Action.DELIVERY_SAVED, current.deliveryAttempt(), evidence, true, true, outcome -> {
            if (outcome.status() != Journal.Status.COMMITTED) { report(menu, "claims", menu.lines(), outcome.result().code()); return; }
            services(player).ledger().custodyCase(actor(player), current.id(), (who, reads) -> false).whenComplete((done, error) -> {
                if (error != null || done.isEmpty() || done.orElseThrow().state() != Custody.State.DELIVERED) return;
                if (stock != null) RegisterInventory.clearDelivered(player.server, player, stock, sale, done.orElseThrow());
                else if(current.terms().items().startsWith("issued-card-v1"))dev.emily.economy.card.CardInventory.clearDelivered(player,world,done.orElseThrow());
                else if (current.terms().items().startsWith("withdrawal-cash-v1")) WithdrawalInventory.clearDelivered(player, done.orElseThrow());
                else BankingInventory.clearDelivered(player, done.orElseThrow());
                BankingSaves.player(player, world, current.id());
                if (stock != null) BankingSaves.source(player, stock.anchor().below(), world, current.id());
                var flow = FLOWS.get(menu); if (flow != null) { flow.quote = null; flow.reservation = null; flow.claims = Set.of(); flow.claimOrder = List.of(); }
                if (!menu.valid(player)) return;
                menu.setBusy(false);
                menu.publish("claims", List.of("The complete batch is now in your inventory.", "Choose Collect items to check for more."), "Delivery complete", List.of());
            });
        });
    }
    private static void quarantineDelivery(ServerPlayer player, BankingMenu menu, Custody.Case current) {
        custodyStep(player, menu, current, Custody.Action.QUARANTINE, current.deliveryAttempt(), Custody.Evidence.unknown(), true, false,
                outcome -> report(menu, "claims", List.of("Delivery state is uncertain. Operator evidence review is required."), outcome.result().code()));
    }
    private static void custodyStep(ServerPlayer player, BankingMenu menu, Custody.Case current, Custody.Action action, UUID attempt,
                                    Custody.Evidence evidence, boolean admitted, boolean proof, Consumer<Journal.Outcome> next) {
        var owner = actor(player);
        submit(player, menu, intent(player, action.kind(), Custody.request(current.id(), attempt, evidence, "banking saved delivery")), () ->
                services(player).ledger().custody(action, (who, kind) -> who.equals(owner),
                        (a, i, value, reads) -> admitted && value.terms().owner().equals(owner) && value.id().equals(current.id()),
                        (value, actual, reads) -> proof && actual.equals(evidence), null, (i, value, reads) -> admitted, null), next);
    }
    private static void recoverInputs(ServerPlayer player, BankingMenu newMenu) {
        boolean active = FLOWS.entrySet().stream().anyMatch(entry -> entry.getKey() != newMenu && entry.getKey().player() == player && entry.getValue().ownsInputs);
        if (active) return;
        var saved = player.getPersistentData().getList("economy_banking_menu_inputs", Tag.TAG_COMPOUND).copy();
        var inputs = new net.minecraft.world.SimpleContainer(24);
        if (saved.size() > 24) throw new IllegalStateException("Saved input bounds");
        for (int index = 0; index < saved.size(); index++) {
            var entry = saved.getCompound(index); int slot = entry.getInt("slot");
            if (slot < 0 || slot >= 24 || !inputs.getItem(slot).isEmpty()) throw new IllegalStateException("Saved input slots");
            inputs.setItem(slot, net.minecraft.world.item.ItemStack.parseOptional(player.registryAccess(), entry.getCompound("stack")));
        }
        player.getPersistentData().remove("economy_banking_menu_inputs");
        var ids = new ArrayList<>(BankingInventory.stashInputs(player, inputs));
        var stashes = player.getPersistentData().getCompound("economy_banking_originals");
        for (var key : stashes.getAllKeys()) if (stashes.getCompound(key).getString("state").equals("HELD")) {
            var id = UUID.fromString(key); if (!ids.contains(id)) ids.add(id);
        }
        if (!saved.isEmpty() || !ids.isEmpty()) BankingSaves.player(player, services(player).worldId(), UUID.randomUUID());
        if (!ids.isEmpty() && !player.getPersistentData().getCompound(PENDING).hasUUID("operation")
                && !player.getPersistentData().getCompound("economy_banking_pending").hasUUID("operation")) {
            BankingSaves.player(player, services(player).worldId(), UUID.randomUUID());
            BankingReturns.create(player, newMenu, services(player).worldId(), ids);
        }
    }
    public static boolean canInput(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        if (!BankingInventory.canStageInputs(player)) return false;
        try { BankingInventory.encode(player, stack); return true; } catch (RuntimeException oversized) { return false; }
    }
    private static void releaseFlow(BankingMenu menu, UUID reservation) {
        var flow = FLOWS.get(menu);
        if (flow != null && Objects.equals(flow.reservation, reservation)) {
            flow.reservation = null; flow.quote = null; flow.deposit = null; flow.ownsInputs = false;
        }
    }
    /** Persist transient input ownership with the same player inventory save, never a ground drop. */
    public static void inputsChanged(ServerPlayer player, BankingMenu menu) {
        if (FLOWS.entrySet().stream().anyMatch(entry -> entry.getKey() != menu && entry.getKey().player() == player && entry.getValue().ownsInputs)) return;
        var list = new ListTag();
        for (int slot = 0; slot < 24; slot++) if (!menu.depositInputs().getItem(slot).isEmpty()) {
            var entry = new CompoundTag(); entry.putInt("slot", slot); entry.put("stack", menu.depositInputs().getItem(slot).save(player.registryAccess())); list.add(entry);
        }
        player.getPersistentData().put("economy_banking_menu_inputs", list);
    }
    private static void returnInputs(ServerPlayer player, BankingMenu menu) {
        var ids = BankingInventory.stashInputs(player, menu.depositInputs()); inputsChanged(player, menu);
        var world = services(player).worldId(); if (world == null) return;
        BankingSaves.player(player, world, UUID.randomUUID());
        if (!ids.isEmpty()) BankingReturns.create(player, menu, world, ids);
    }
    public static void closed(ServerPlayer player, BankingMenu menu) {
        BankingAccounts.closed(menu); BankingWithdrawals.closed(menu); AutopayMenus.closed(menu); CreditMenus.closed(menu); LoanMenus.closed(menu); FinanceReports.closed(menu); FinanceCollections.closed(menu); CreditUse.closed(menu);
        if (player.server.getPlayerList().getPlayer(player.getUUID()) != player) return;
        var flow = FLOWS.get(menu);
        if (flow == null || !flow.ownsInputs) {
            try { returnInputs(player, menu); }
            catch (RuntimeException unavailable) {
                com.mojang.logging.LogUtils.getLogger().warn("Banking menu originals retained; save verification unavailable for {}", player.getUUID());
            }
        }
        // An accepted reservation retains this menu's inputs until its callback saves originals or quarantines uncertainty.
    }
}
