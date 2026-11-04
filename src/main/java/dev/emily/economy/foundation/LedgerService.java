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

    LedgerService(ServerServices services, FoundationStore store) {
        this.services = services;
        this.quotes = new FxRequests(services, store, Clock.systemUTC());
    }

    public record PrimaryFunds(AccountIdentity account, AccountFunds funds) {}

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
