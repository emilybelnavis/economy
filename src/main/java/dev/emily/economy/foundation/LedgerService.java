package dev.emily.economy.foundation;

import dev.emily.economy.account.AccountCatalog;
import dev.emily.economy.account.AccountLifecycle;
import dev.emily.economy.api.AccountFunds;
import dev.emily.economy.api.AccountIdentity;
import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.api.Identifiers.*;
import dev.emily.economy.api.Money;
import dev.emily.economy.api.PartyRef;
import dev.emily.economy.api.RequestContext;
import dev.emily.economy.fx.FxQuotes;
import dev.emily.economy.fx.FxRequests;
import dev.emily.economy.journal.*;
import dev.emily.economy.storage.FoundationStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import java.math.BigInteger;
import java.time.Clock;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiPredicate;
import java.util.function.Function;

/** Financial entry points for commands and trusted server integrations. Call on the server thread. */
public final class LedgerService {
    private static final JournalService.Publisher NO_PUBLICATION = new JournalService.Publisher() {
        public void invalidate(Journal.Outcome outcome) {}
        public void publish(Journal.Outcome outcome) {}
    };

    private final ServerServices services;
    private final FxRequests quotes;
    private final FoundationStore store;

    LedgerService(ServerServices services, FoundationStore store) {
        this.services = services;
        this.store = store;
        this.quotes = new FxRequests(services, store, Clock.systemUTC());
    }

    public record PrimaryFunds(AccountIdentity account, AccountFunds funds) {}
    public record BankingContext(WorldId world, AccountIdentity account, TerminalPolicy.Snapshot terminal,
                                 Deposits.FxSnapshot fx, RegisterSales.Price price) {}
    public CompletionStage<BankingContext> bankingContext(PlayerId actor, TerminalPolicy.Anchor anchor, AccountId selected) {
        return services.storage(actor, () -> store.transaction(tx -> {
            var world = store.snapshot().worldId();
            var owner = new PartyRef(world, new PartyRef.Player(actor));
            var account = selected == null ? AccountCatalog.primary(tx::query, owner).orElse(null)
                    : AccountCatalog.find(tx::query, owner, selected).orElse(null);
            var terminal = TerminalPolicy.find(tx::query, world, anchor, actor, true)
                    .map(value -> TerminalPolicy.effective(value, terminalCatalog())).orElse(null);
            return new BankingContext(world, account, terminal, depositFx(tx::query),
                    RegisterSales.price(tx::query, world, anchor).orElse(null));
        }));
    }
    public CompletionStage<Optional<Deposits.Reservation>> deposit(PlayerId actor, UUID id) {
        return services.storage(actor, () -> store.transaction(tx -> Deposits.find(tx::query, actor, id)));
    }


    public record Reconciliation(EconomyCurrency currency, long posted, BigInteger journal, boolean complete) {
        public boolean matches() {
            return complete && journal.equals(BigInteger.valueOf(posted));
        }
    }

    public CompletionStage<Optional<AccountFunds>> funds(PartyAuthorization.Grant grant, AccountId account) {
        return services.readAccounts(grant, false, reads -> AccountCatalog.find(reads, grant.target(), account)
                .flatMap(identity -> bankFunds(reads, identity)));
    }

    public CompletionStage<Optional<PrimaryFunds>> primaryFunds(PartyAuthorization.Grant grant) {
        return services.readAccounts(grant, false, reads -> AccountCatalog.primary(reads, grant.target())
                .flatMap(account -> bankFunds(reads, account).map(funds -> new PrimaryFunds(account, funds))));
    }

    static Optional<AccountFunds> bankFunds(JournalEngine.ReadView reads, AccountIdentity account) {
        if (account.purpose() != AccountIdentity.Purpose.BANK) return Optional.empty();
        var id = account.id().value();
        var currency = account.currency();
        long reserved = Holds.reserved(reads, id, currency);
        long posted = Settlement.posted(reads, id, currency);
        return Optional.of(new AccountFunds(currency, Math.subtractExact(posted, reserved), reserved, 0));
    }

    public CompletionStage<Optional<LedgerHistory.Page>> history(
            PartyAuthorization.Grant grant, AccountId account, String cursor, int limit) {
        return services.readAccounts(grant, false, reads -> LedgerHistory.page(reads, grant.target(), account, cursor, limit));
    }

    public CompletionStage<Optional<LedgerHistory.Page>> inspectHistory(CommandSourceStack source,
            Function<MinecraftServer, ServerServices> sessions, PartyRef target, AccountId account, String cursor, int limit) {
        return inspect(source, sessions, target, Map.of("ledger_history", account.value().toString()),
                reads -> LedgerHistory.page(reads, target, account, cursor, limit));
    }

    public CompletionStage<Optional<Reconciliation>> reconcile(CommandSourceStack source,
            Function<MinecraftServer, ServerServices> sessions, PartyRef target, AccountId account) {
        return inspect(source, sessions, target, Map.of("ledger_reconcile", account.value().toString()), reads ->
                AccountCatalog.find(reads, target, account).flatMap(identity -> bankFunds(reads, identity).flatMap(funds ->
                        LedgerHistory.page(reads, target, account, null, LedgerHistory.MAX_PAGE_SIZE).map(page -> {
                            var journal = page.entries().stream().map(LedgerHistory.Entry::amount)
                                    .filter(amount -> amount.currency() == funds.currency())
                                    .map(amount -> BigInteger.valueOf(amount.minorUnits()))
                                    .reduce(BigInteger.ZERO, BigInteger::add);
                            return new Reconciliation(funds.currency(), funds.postedMinorUnits(), journal, page.nextCursor() == null);
                        }))));
    }

    private <T> CompletionStage<Optional<T>> inspect(CommandSourceStack source,
            Function<MinecraftServer, ServerServices> sessions, PartyRef target, Map<String, String> scope,
            Function<JournalEngine.ReadView, Optional<T>> query) {
        var grant = PartyAuthorization.inspection(source, sessions, target);
        if (grant.isEmpty() || sessions.apply(source.getServer()) != services) {
            return CompletableFuture.completedStage(Optional.empty());
        }
        return services.inspectAccounts(grant.get(), scope,
                () -> sessions.apply(source.getServer()) == services && PartyAuthorization.inspection(source, sessions, target).isPresent(),
                query);
    }

    /** Snapshot expected revisions for a fresh payment. Keep the resulting instruction for retries. */
    public CompletionStage<Optional<SameCurrency.Payment>> planPayment(
            PartyAuthorization.Grant grant, PlayerId recipient, long wholeCoins) {
        if (wholeCoins <= 0) throw new IllegalArgumentException("Positive amount required");
        return services.readAccounts(grant, false, reads -> {
            var owner = (PartyRef.Player) grant.target().owner();
            var target = new PartyRef(grant.target().world(), new PartyRef.Player(recipient));
            return AccountCatalog.primary(reads, grant.target()).flatMap(debit ->
                    AccountCatalog.primary(reads, target).map(credit -> new SameCurrency.Payment(
                            owner.id(), revision(debit), recipient, revision(credit),
                            new Money(debit.currency(), Math.multiplyExact(wholeCoins, debit.currency().minorUnitsPerMajor())))));
        });
    }

    public JournalService.Submission submit(PartyAuthorization.Grant grant, OperationId operation,
                                            RequestId attempt, SameCurrency.Instruction instruction) {
        var intent = ownedIntent(grant, operation, attempt, instruction);
        var handlers = SameCurrency.registrations(grant.target().world(), (request, decoded, reads) -> true);
        return ownedJournal(grant, intent, handlers.get(intent.kind())).submit(intent);
    }

    public CompletionStage<Optional<Journal.Outcome>> lookup(PartyAuthorization.Grant grant, OperationId operation,
            RequestId attempt, SameCurrency.Instruction instruction) {
        var intent = ownedIntent(grant, operation, attempt, instruction);
        return ownedJournal(grant, intent, null).lookup(intent);
    }

    public CompletionStage<FxQuotes.Quote> quote(PartyAuthorization.Grant grant, RequestId attempt,
                                                  FxQuotes.Pair pair, FxQuotes.Side side, Money amount) {
        var player = requireOwner(grant);
        return quotes.quote(new RequestContext(player.value(), services.sessionId(), attempt.value()), pair, side, amount);
    }

    public JournalService.Submission exchange(PartyAuthorization.Grant grant, OperationId operation,
                                               RequestId attempt, FxSettlement.Exchange exchange) {
        if (!requireOwner(grant).equals(exchange.owner())) throw new IllegalArgumentException("Exchange owner");
        var intent = new Journal.Intent(operation, attempt, grant.actor(), FxSettlement.EXCHANGE, exchange.request(), null, null);
        var handler = FxSettlement.registration(grant.target().world(), quotes::requireFresh, (request, decoded, reads) -> true);
        return ownedJournal(grant, intent, handler).submit(intent);
    }

    public CompletionStage<Optional<Journal.Outcome>> lookupExchange(PartyAuthorization.Grant grant,
            OperationId operation, RequestId attempt, FxSettlement.Exchange exchange) {
        if (!requireOwner(grant).equals(exchange.owner())) throw new IllegalArgumentException("Exchange owner");
        var intent = new Journal.Intent(operation, attempt, grant.actor(), FxSettlement.EXCHANGE, exchange.request(), null, null);
        return ownedJournal(grant, intent, null).lookup(intent);
    }

    public JournalService.Submission close(PartyAuthorization.Grant grant, OperationId operation, RequestId attempt,
            FundedClosure.Instruction instruction, FundedClosure.ClaimsReader claims,
            BiPredicate<Journal.Intent, JournalEngine.ReadView> authority) {
        requireOwner(grant);
        var handler = FundedClosure.registration(grant.target(), instruction, quotes::requireFresh, claims, authority);
        return services.submitOwned(grant, AccountLifecycle.CLOSE, operation, attempt, instruction.request(), handler, null);
    }

    public CompletionStage<Optional<Journal.Outcome>> lookupClosure(PartyAuthorization.Grant grant,
            OperationId operation, RequestId attempt, FundedClosure.Instruction instruction) {
        requireOwner(grant);
        return services.lookupOwned(grant, AccountLifecycle.CLOSE, operation, attempt, instruction.request(),
                new JournalEngine.Registration(1, (intent, reads) -> {
                    throw new IllegalStateException("Lookup cannot prepare closure");
                }));
    }

    /** Bind a trusted credit or debit handler with separate admission and live adjustment policies. */
    public JournalService adjustments(Journal.Kind kind, JournalEngine.AccessPolicy access,
                                       SameCurrency.Authority authority, JournalService.Publisher publisher) {
        if (!kind.equals(SameCurrency.CREDIT) && !kind.equals(SameCurrency.DEBIT)) {
            throw new IllegalArgumentException("Credit or debit kind required");
        }
        var handler = SameCurrency.registrations(services.partyWorld(), authority).get(kind);
        return trustedJournal(kind, access, handler, publisher);
    }

    /**
     * For trusted server code. The caller derives actor and policy from authenticated server authority.
     * The access policy gates replay and lookup. The registration checks live worker-side business authority.
     */
    public JournalService trustedJournal(Journal.Kind kind, JournalEngine.AccessPolicy access,
                                         JournalEngine.Registration registration, JournalService.Publisher publisher) {
        return services.journal((actor, requested) -> kind.equals(requested) && access.allows(actor, requested),
                Map.of(kind, registration), publisher == null ? NO_PUBLICATION : publisher);
    }

    /** Bind a payment action to authenticated server admission and live worker authority. */
    public JournalService merchantPayments(MerchantPayments.Action action, JournalEngine.AccessPolicy admission,
                                           MerchantPayments.Authority authority, JournalService.Publisher publisher) {
        return trustedJournal(action.kind(), admission,
                MerchantPayments.registration(action, authority, Clock.systemUTC()), publisher);
    }

    public CompletionStage<Optional<MerchantPayments.ReceiptView>> paymentReceipt(
            ActorId actor, UUID operation, MerchantPayments.ReceiptAuthority authority) {
        return services.storage(actor, () -> store.transaction(tx ->
                MerchantPayments.receipt(tx::query, actor, operation, authority)));
    }

    public record PaymentInspection(PaymentDiagnostics.Page page, String releaseWorkerFailure) {}

    /** Invoke with a server-authenticated actor and trusted live operator resolver. */
    public CompletionStage<PaymentInspection> inspectPayments(ActorId actor, PaymentDiagnostics.Operators operators,
                                                              UUID after, int limit, boolean overdueOnly) {
        String failure = services.paymentReleaseFailure();
        return services.storage(actor, () -> store.transaction(tx -> {
            boolean operator = operators.allows(actor, tx::query);
            var page = PaymentDiagnostics.page(tx::query, actor, (a, reads) -> operator,
                    after, limit, overdueOnly, Clock.systemUTC());
            return new PaymentInspection(page, operator ? failure : null);
        }));
    }

    public CompletionStage<Optional<PaymentDiagnostics.Audit>> inspectPaymentOperation(
            ActorId actor, PaymentDiagnostics.Operators operators, UUID invoice, UUID operation) {
        return services.storage(actor, () -> store.transaction(tx ->
                PaymentDiagnostics.audit(tx::query, actor, operators, invoice, operation)));
    }

    /** Trusted adapters bind immutable intent, external evidence and live server authority. */
    public JournalService custody(Custody.Action action, JournalEngine.AccessPolicy admission,
                                  Custody.Authority authority, Custody.EvidenceVerifier verifier,
                                  Custody.Financial financial, Custody.Capacity capacity,
                                  JournalService.Publisher publisher) {
        return trustedJournal(action.kind(), admission,
                Custody.registration(action, authority, verifier, financial, capacity), publisher);
    }

    public CompletionStage<Custody.Page> custodyClaims(ActorId authenticatedOwner, UUID after, int limit) {
        return services.storage(authenticatedOwner, () -> store.transaction(tx ->
                Custody.claims(tx::query, authenticatedOwner, after, limit)));
    }

    public CompletionStage<Optional<Custody.Case>> custodyCase(ActorId actor, UUID custody,
                                                              PaymentDiagnostics.Operators operators) {
        return services.storage(actor, () -> store.transaction(tx ->
                Custody.find(tx::query, actor, custody, operators.allows(actor, tx::query))));
    }

    public CompletionStage<CustodyDiagnostics.Page> inspectCustody(ActorId actor,
            PaymentDiagnostics.Operators operators, CustodyDiagnostics.Filter filter, UUID after, int limit) {
        return services.storage(actor, () -> store.transaction(tx ->
                CustodyDiagnostics.page(tx::query, actor, operators, filter, after, limit)));
    }

    public CompletionStage<Optional<CustodyDiagnostics.Witness>> inspectCustodyWitness(ActorId actor,
            PaymentDiagnostics.Operators operators, UUID custody, CustodyDiagnostics.Slot slot) {
        var world = services.worldDirectory();
        return services.storage(actor, () -> store.transaction(tx ->
                CustodyDiagnostics.witness(tx::query, actor, operators, custody, slot, world, store.snapshot().worldId())));
    }

    public CompletionStage<Optional<CustodyDiagnostics.Audit>> inspectCustodyOperation(ActorId actor,
            PaymentDiagnostics.Operators operators, UUID custody, UUID operation) {
        return services.storage(actor, () -> store.transaction(tx ->
                CustodyDiagnostics.audit(tx::query, actor, operators, custody, operation)));
    }

    /** No gameplay redemption is enabled. Trusted integrations must prove custody and value backing. */
    public JournalService cashIssuance(CashIssuance.Action action, JournalEngine.AccessPolicy admission,
            CashIssuance.Authority authority, CashIssuance.EvidenceVerifier verifier,
            CashIssuance.Financial financial, JournalService.Publisher publisher) {
        var world = services.partyWorld();
        var items = dev.emily.economy.cash.CashCatalog.all().stream()
                .map(value -> "economy:" + value.id()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        CashIssuance.Authority catalogAuthority = (requested, intent, lots, reads) ->
                lots.stream().allMatch(lot -> items.contains(lot.item()))
                        && authority.allows(requested, intent, lots, reads);
        return trustedJournal(action.kind(), admission,
                CashIssuance.registration(action, world, catalogAuthority, verifier, financial), publisher);
    }

    /** Server placement/session admission and current owner/operator policy share one journal boundary. */
    public JournalService terminalPolicy(TerminalPolicy.Action action, JournalEngine.AccessPolicy admission,
            TerminalPolicy.Authority authority, TerminalPolicy.Operators operators,
            JournalService.Publisher publisher) {
        var catalog = terminalCatalog();
        return trustedJournal(action.kind(), admission,
                TerminalPolicy.registration(action, services.partyWorld(), catalog, authority, operators), publisher);
    }

    public CompletionStage<Optional<TerminalPolicy.Snapshot>> terminalSettings(ActorId actor,
            TerminalPolicy.Operators operators, TerminalPolicy.Anchor anchor) {
        return services.storage(actor, () -> store.transaction(tx ->
                TerminalPolicy.find(tx::query, store.snapshot().worldId(), anchor, actor,
                        operators.allows(actor, tx::query)).map(value -> TerminalPolicy.effective(value, terminalCatalog()))));
    }

    private static Map<String, TerminalPolicy.Denomination> terminalCatalog() {
        return dev.emily.economy.cash.CashCatalog.all().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(value -> "economy:" + value.id(),
                        value -> new TerminalPolicy.Denomination(value.currency(), value.bill())));
    }

    private static Map<String, Deposits.Denomination> depositCatalog() {
        return dev.emily.economy.cash.CashCatalog.all().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(value -> "economy:" + value.id(),
                        value -> new Deposits.Denomination(value.currency(), value.minorUnits(), value.bill())));
    }

    private static Deposits.FxSnapshot depositFx(JournalEngine.ReadView reads) {
        var publication = dev.emily.economy.fx.FxService.publication(reads, java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        return publication == null ? null : new Deposits.FxSnapshot(
                publication.day() + "/" + publication.updateId() + "/" + publication.policyId(), publication.conversionRates());
    }

    public CompletionStage<Deposits.Preview> previewDeposit(PlayerId actor, Deposits.Binding binding,
            List<Deposits.Slot> slots, java.util.function.BiPredicate<Deposits.Binding, JournalEngine.ReadView> admission) {
        var catalog = depositCatalog();
        var originals = List.copyOf(slots);
        return services.economicWrite(actor, () -> store.transaction(tx -> {
            if (!admission.test(binding, tx::query)) throw new IllegalArgumentException("DEPOSIT_ACCESS");
            return Deposits.preview(tx::query, store.snapshot().worldId(), actor, binding, originals, catalog,
                    LedgerService::depositFx);
        }));
    }

    public JournalService deposits(Deposits.Action action, JournalEngine.AccessPolicy admission,
            Deposits.Authority authority, Deposits.EvidenceVerifier evidence, JournalService.Publisher publisher) {
        return trustedJournal(action.kind(), admission, Deposits.registration(action, services.partyWorld(),
                depositCatalog(), LedgerService::depositFx, authority, evidence), publisher);
    }

    public JournalService depositReturns(DepositReturns.Action action, JournalEngine.AccessPolicy admission,
            DepositReturns.Authority authority, DepositReturns.EvidenceVerifier evidence, JournalService.Publisher publisher) {
        return trustedJournal(action.kind(), admission, DepositReturns.registration(action, services.partyWorld(),
                authority, evidence), publisher);
    }

    public CompletionStage<List<DepositReturns.Return>> depositReturns(PlayerId actor, UUID deposit) {
        return services.storage(actor, () -> store.transaction(tx -> DepositReturns.list(tx::query, actor, deposit)));
    }

    private static Map<String, WithdrawalPlanner.Denomination> withdrawalCatalog() {
        return dev.emily.economy.cash.CashCatalog.all().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(value -> "economy:" + value.id(),
                        value -> new WithdrawalPlanner.Denomination("economy:" + value.id(),
                                value.currency(), value.minorUnits(), value.bill())));
    }

    private static Withdrawals.FxSnapshot withdrawalFx(JournalEngine.ReadView reads) {
        var publication = depositFx(reads);
        return publication == null ? null : new Withdrawals.FxSnapshot(publication.revision(), publication.rates());
    }

    public CompletionStage<Withdrawals.Preview> previewWithdrawal(PlayerId actor, Withdrawals.Binding binding,
            Money cash, Map<String, Integer> billEdits,
            java.util.function.BiPredicate<Withdrawals.Binding, JournalEngine.ReadView> admission,
            java.util.function.BiPredicate<Withdrawals.Preview, JournalEngine.ReadView> capacity) {
        var catalog = withdrawalCatalog();
        var edits = billEdits == null ? null : Map.copyOf(billEdits);
        return services.economicWrite(actor, () -> store.transaction(tx -> {
            if (!admission.test(binding, tx::query)) throw new IllegalArgumentException("WITHDRAWAL_ACCESS");
            var preview = Withdrawals.preview(tx::query, store.snapshot().worldId(), actor, binding, cash,
                    edits, catalog, LedgerService::withdrawalFx);
            if (!capacity.test(preview, tx::query)) throw new IllegalArgumentException("WITHDRAWAL_CAPACITY");
            return preview;
        }));
    }

    public JournalService withdrawals(Withdrawals.Action action, JournalEngine.AccessPolicy admission,
            Withdrawals.Authority authority, Withdrawals.Capacity capacity, JournalService.Publisher publisher) {
        return trustedJournal(action.kind(), admission, Withdrawals.registration(action, services.partyWorld(),
                withdrawalCatalog(), LedgerService::withdrawalFx, authority, capacity, WithdrawalDelivery::prepare), publisher);
    }

    public CompletionStage<Optional<Withdrawals.Reservation>> withdrawal(PlayerId actor, UUID reservation) {
        return services.storage(actor, () -> store.transaction(tx -> Withdrawals.find(tx::query, actor, reservation)));
    }

    public CompletionStage<Optional<RegisterSales.Reservation>> registerSale(PlayerId actor, UUID id) {
        return services.storage(actor, () -> store.transaction(tx -> RegisterSales.find(tx::query, actor, id)));
    }
    public record RegisterPriceContext(TerminalPolicy.Snapshot terminal, RegisterSales.Price price, AccountIdentity seller) {}

    public CompletionStage<Optional<RegisterPriceContext>> registerPriceContext(PlayerId actor,
            TerminalPolicy.Anchor anchor, RegisterSales.Operators operators) {
        return services.storage(actor, () -> store.transaction(tx -> {
            var terminal = TerminalPolicy.find(tx::query, store.snapshot().worldId(), anchor, actor,
                    operators.allows(actor, tx::query)).orElse(null);
            if (terminal == null || !terminal.active() || terminal.kind() != TerminalPolicy.Kind.REGISTER
                    || !(terminal.owner() instanceof PlayerId owner)) return Optional.empty();
            var party = new PartyRef(store.snapshot().worldId(), new PartyRef.Player(owner));
            var seller = AccountCatalog.primary(tx::query, party).orElse(null);
            if (seller == null) return Optional.empty();
            return Optional.of(new RegisterPriceContext(terminal,
                    RegisterSales.price(tx::query, store.snapshot().worldId(), anchor).orElse(null), seller));
        }));
    }

    public CompletionStage<RegisterSales.Quote> quoteSale(PlayerId buyer, TerminalPolicy.Anchor anchor,
            UUID generation, long terminalRevision, RegisterSales.SourceReader sources,
            java.util.function.BiPredicate<TerminalPolicy.Anchor, JournalEngine.ReadView> admission) {
        return services.economicWrite(buyer, () -> store.transaction(tx -> {
            if (!admission.test(anchor, tx::query)) throw new IllegalArgumentException("SALE_ACCESS");
            return RegisterSales.quote(tx::query, store.snapshot().worldId(), buyer, anchor, generation, terminalRevision, sources);
        }));
    }

    public JournalService registerSales(RegisterSales.Action action, JournalEngine.AccessPolicy admission,
            RegisterSales.Authority authority, RegisterSales.Operators operators, RegisterSales.Capacity capacity,
            RegisterSales.SourceReader sources, RegisterSales.EvidenceVerifier evidence, JournalService.Publisher publisher) {
        return trustedJournal(action.kind(), admission, RegisterSales.registration(action, services.partyWorld(),
                authority, operators, capacity, sources, evidence,
                RegisterSaleSettlement::checkout,
                (intent, sale, uncertain, reads) -> RegisterSaleSettlement.returnOriginals(intent, sale, uncertain, reads, sources)), publisher);
    }

    private Journal.Intent ownedIntent(PartyAuthorization.Grant grant, OperationId operation, RequestId attempt,
                                       SameCurrency.Instruction instruction) {
        var player = requireOwner(grant);
        var kind = switch (instruction) {
            case SameCurrency.Payment pay when pay.source().equals(player) -> SameCurrency.PAY;
            case SameCurrency.Transfer transfer when transfer.owner().equals(player) -> SameCurrency.TRANSFER;
            default -> throw new IllegalArgumentException("Unauthorized owner instruction");
        };
        return new Journal.Intent(operation, attempt, player, kind, instruction.request(), null, null);
    }

    private JournalService ownedJournal(PartyAuthorization.Grant grant, Journal.Intent intent,
                                         JournalEngine.Registration handler) {
        var world = grant.target().world();
        var session = services.sessionId();
        return services.journal((actor, kind) -> kind.equals(intent.kind()) && grant.allows(world, session, actor, kind),
                handler == null ? Map.of() : Map.of(intent.kind(), handler), NO_PUBLICATION);
    }

    private PlayerId requireOwner(PartyAuthorization.Grant grant) {
        if (grant.target().owner() instanceof PartyRef.Player player
                && player.id().equals(grant.actor()) && grant.allowsOwnership(services.partyWorld(), services.sessionId())) {
            return player.id();
        }
        throw new IllegalArgumentException("Player ownership required");
    }

    private static SameCurrency.Revision revision(AccountIdentity account) {
        return new SameCurrency.Revision(account.id(), account.revision());
    }
}
