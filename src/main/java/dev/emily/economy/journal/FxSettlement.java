package dev.emily.economy.journal;

import dev.emily.economy.account.AccountCatalog;
import dev.emily.economy.api.AccountIdentity;
import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.api.Identifiers.AccountId;
import dev.emily.economy.api.Identifiers.PlayerId;
import dev.emily.economy.api.Identifiers.WorldId;
import dev.emily.economy.api.Money;
import dev.emily.economy.api.PartyRef;
import dev.emily.economy.fx.FxQuotes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.emily.economy.journal.PostingBatch.Role.*;

/** Quoted exchange preparation and one-time consumption on the journal worker. */
public final class FxSettlement {
    public static final String FACT = "economy:fx-settlement";
    public static final Journal.Kind EXCHANGE = new Journal.Kind("economy:ledger", "economy:exchange");

    private FxSettlement() {}

    @FunctionalInterface
    public interface QuoteReader {
        FxQuotes.Quote requireFresh(UUID id, PlayerId actor, WorldId world, JournalEngine.ReadView reads);
    }

    @FunctionalInterface
    public interface Authority {
        boolean allows(Journal.Intent intent, Exchange exchange, JournalEngine.ReadView reads);
    }

    public record Exchange(UUID quote, PlayerId owner, SameCurrency.Revision source, SameCurrency.Revision target) {
        public Journal.CanonicalRequest request() {
            return new Journal.CanonicalRequest(1, Map.of("quote", quote.toString(), "owner", owner.value().toString(),
                    "source", encode(source), "target", encode(target)));
        }
    }

    public static JournalEngine.Registration registration(WorldId world, QuoteReader quotes, Authority authority) {
        return new JournalEngine.Registration(1, (intent, reads) -> {
            Exchange exchange;
            try {
                var fields = intent.request().fields();
                exchange = new Exchange(id(fields.get("quote")), new PlayerId(id(fields.get("owner"))),
                        revision(fields.get("source")), revision(fields.get("target")));
                if (!exchange.request().equals(intent.request())) return reject("FX_REQUEST");
            } catch (IllegalArgumentException malformed) {
                return reject("FX_REQUEST");
            }
            if (!(intent.actor() instanceof PlayerId actor) || !authority.allows(intent, exchange, reads)) {
                return reject("AUTHORITY_REVOKED");
            }
            var owner = new PartyRef(world, new PartyRef.Player(exchange.owner()));
            var source = AccountCatalog.find(reads, owner, exchange.source().id()).orElse(null);
            var target = AccountCatalog.find(reads, owner, exchange.target().id()).orElse(null);
            if (source == null || target == null || source.revision() != exchange.source().revision()
                    || target.revision() != exchange.target().revision()) return reject("STALE_ACCOUNT");
            return prepare(reads, quotes, exchange.quote(), actor, world, source, target, false);
        });
    }

    /** Returns both the posting effects and durable quote consumption for composition by funded closure. */
    public static JournalEngine.Decision prepare(JournalEngine.ReadView reads, QuoteReader quotes, UUID quoteId,
                                                  PlayerId actor, WorldId world, AccountIdentity source,
                                                  AccountIdentity target, boolean drain) {
        FxQuotes.Quote quote;
        try {
            quote = quotes.requireFresh(quoteId, actor, world, reads);
        } catch (IllegalArgumentException stale) {
            return reject("FX_QUOTE_STALE");
        }
        if (!quote.id().equals(quoteId) || !quote.actor().equals(actor) || !quote.world().equals(world)) {
            return reject("FX_QUOTE_STALE");
        }
        if (!reads.query("SELECT quote_id FROM ledger_fx_quotes WHERE quote_id=?", List.of(quoteId.toString()), 1).isEmpty()) {
            return reject("FX_QUOTE_CONSUMED");
        }
        var from = quote.pair().source();
        var to = quote.pair().target();
        if (!source.owner().world().equals(world) || !target.owner().world().equals(world)
                || source.id().equals(target.id()) || source.status() != AccountIdentity.Status.OPEN
                || target.status() != AccountIdentity.Status.OPEN || !source.eligibleCurrency(from)
                || !target.eligibleCurrency(to)) return reject("FX_ACCOUNT");
        if (quote.debit().currency() != from || quote.credit().currency() != to || quote.fee().currency() != from
                || quote.debit().minorUnits() <= 0 || quote.credit().minorUnits() < 0 || quote.fee().minorUnits() < 0) {
            return reject("FX_QUOTE_AMOUNT");
        }

        long debit = quote.debit().minorUnits();
        long fee = quote.fee().minorUnits();
        long rawCredit = quote.credit().minorUnits();
        long credit = rawCredit - rawCredit % to.minorUnitsPerMajor();
        long remainder = debit % from.minorUnitsPerMajor();
        if (fee % from.minorUnitsPerMajor() != 0
                || (quote.side() == FxQuotes.Side.EXACT_DEBIT && remainder != 0)
                || (quote.side() == FxQuotes.Side.EXACT_CREDIT && (rawCredit != credit || drain))) {
            return reject("FX_QUOTE_AMOUNT");
        }

        long total;
        try {
            if (remainder != 0) debit = Math.addExact(debit, from.minorUnitsPerMajor() - remainder);
            total = Math.addExact(debit, fee);
        } catch (ArithmeticException overflow) {
            return reject("POSTING_OVERFLOW");
        }
        if (drain && Settlement.posted(reads, source.id().value(), from) != total) {
            return reject("FX_CLOSURE_AMOUNT");
        }

        var legs = new ArrayList<PostingBatch.Leg>();
        legs.add(leg(source.id().value(), ACCOUNT, from, -total));
        legs.add(leg(Settlement.clearingAccount(from), CLEARING, from, debit));
        if (fee != 0) legs.add(leg(Settlement.feeAccount(from), FEE, from, fee));
        if (credit != 0) {
            legs.add(leg(Settlement.clearingAccount(to), CLEARING, to, -credit));
            legs.add(leg(target.id().value(), ACCOUNT, to, credit));
        }
        var settlement = Settlement.prepare(reads, legs, PostingBatch.Authority.FX, null);
        if (!(settlement instanceof JournalEngine.Commit commit)) return settlement;
        var facts = new ArrayList<>(commit.records());
        facts.add(new Journal.Record(FACT, 1, String.join("|", quoteId.toString(), quote.publication().toString(),
                Integer.toString(quote.pricingVersion()), from.name(), to.name(), quote.side().name(),
                Long.toString(debit), Long.toString(fee), Long.toString(rawCredit), Long.toString(credit))));
        return new JournalEngine.Commit(facts, new Journal.Result(1, "FX_SETTLED", ""), tx -> {
            commit.projections().apply(tx);
            tx.execute("INSERT INTO ledger_fx_quotes(quote_id) VALUES(?)", List.of(quoteId.toString()));
        });
    }

    public static void validateFact(String payload) {
        var fields = payload.split("\\|", -1);
        if (fields.length != 10) throw new IllegalArgumentException("FX fact fields");
        id(fields[0]);
        id(fields[1]);
        var pair = new FxQuotes.Pair(EconomyCurrency.valueOf(fields[3]), EconomyCurrency.valueOf(fields[4]));
        var side = FxQuotes.Side.valueOf(fields[5]);
        long debit = Long.parseLong(fields[6]);
        long fee = Long.parseLong(fields[7]);
        long rawCredit = Long.parseLong(fields[8]);
        long credit = Long.parseLong(fields[9]);
        if (Integer.parseInt(fields[2]) < 1 || debit <= 0 || fee < 0 || rawCredit < 0 || credit < 0
                || debit % pair.source().minorUnitsPerMajor() != 0 || fee % pair.source().minorUnitsPerMajor() != 0
                || credit != rawCredit - rawCredit % pair.target().minorUnitsPerMajor()
                || (side == FxQuotes.Side.EXACT_CREDIT && rawCredit != credit)) {
            throw new IllegalArgumentException("FX fact amounts");
        }
    }

    private static PostingBatch.Leg leg(UUID account, PostingBatch.Role role, EconomyCurrency currency, long amount) {
        return new PostingBatch.Leg(account, role, currency, new Money(currency, amount));
    }

    private static String encode(SameCurrency.Revision value) {
        return value.id().value() + "|" + value.revision();
    }

    private static SameCurrency.Revision revision(String value) {
        if (value == null) throw new IllegalArgumentException("Missing FX revision");
        var fields = value.split("\\|", -1);
        if (fields.length != 2) throw new IllegalArgumentException("FX revision");
        return new SameCurrency.Revision(new AccountId(id(fields[0])),
                Long.parseLong(fields[1]));
    }

    private static UUID id(String value) {
        if (value == null) throw new IllegalArgumentException("Missing FX identity");
        var id = UUID.fromString(value);
        if (!id.toString().equals(value)) throw new IllegalArgumentException("FX identity");
        return id;
    }

    private static JournalEngine.Reject reject(String code) {
        return new JournalEngine.Reject(new Journal.Result(1, code, ""));
    }
}
