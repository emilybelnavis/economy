package dev.emily.economy.journal;

import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.api.Money;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

/** Posting facts prepared by trusted journal handlers from resolved account data. */
public final class PostingBatch {
    private PostingBatch() {}

    public enum Role { ACCOUNT, ISSUANCE, CLEARING, FEE }
    public enum Authority { TRANSFER, ISSUE, REDEEM, FX }

    public record Leg(UUID account, Role role, EconomyCurrency accountCurrency, Money amount) {}

    /**
     * Authority comes from the server handler's authorization decision.
     * Account identity, role and currency must be resolved by that handler.
     * Journal admission binds the returned facts to one durable transaction.
     */
    public static JournalEngine.Decision prepare(List<Leg> legs, Authority authority) {
        if (legs.size() < 2 || legs.size() > 64) return reject("POSTING_COUNT");
        if (authority == null) return reject("POSTING_AUTHORITY");
        var credits = new EnumMap<EconomyCurrency, Long>(EconomyCurrency.class);
        var debits = new EnumMap<EconomyCurrency, Long>(EconomyCurrency.class);
        var records = new ArrayList<Journal.Record>();
        boolean hasIssuance = false;
        boolean hasClearing = false;
        try {
            for (Leg leg : legs) {
                if (leg.account() == null || leg.role() == null) return reject("POSTING_ROLE");
                if (leg.amount() == null || leg.accountCurrency() != leg.amount().currency()) {
                    return reject("POSTING_CURRENCY");
                }
                long units = leg.amount().minorUnits();
                if (units % leg.accountCurrency().minorUnitsPerMajor() != 0) return reject("POSTING_FRACTION");
                if (units == 0) return reject("POSTING_SIGN");
                if (leg.role() == Role.ISSUANCE) {
                    hasIssuance = true;
                    if (authority != Authority.ISSUE && authority != Authority.REDEEM) {
                        return reject("POSTING_AUTHORITY");
                    }
                }
                if (leg.role() == Role.CLEARING || leg.role() == Role.FEE) {
                    if (authority != Authority.FX) return reject("POSTING_AUTHORITY");
                    if (leg.role() == Role.FEE && units < 0) return reject("POSTING_SIGN");
                    hasClearing |= leg.role() == Role.CLEARING;
                }
                if ((authority == Authority.ISSUE && (units < 0) != (leg.role() == Role.ISSUANCE))
                        || (authority == Authority.REDEEM && (units > 0) != (leg.role() == Role.ISSUANCE))) {
                    return reject("POSTING_SIGN");
                }
                var totals = units > 0 ? credits : debits;
                totals.merge(leg.accountCurrency(), units > 0 ? units : Math.negateExact(units), Math::addExact);
                records.add(new Journal.Record("economy:posting", 1,
                        leg.account() + "|" + leg.role().name() + "|" + leg.accountCurrency().name() + "|" + units + "|" + authority.name()));
            }
        } catch (ArithmeticException overflow) {
            return reject("POSTING_OVERFLOW");
        }
        if (((authority == Authority.ISSUE || authority == Authority.REDEEM) && !hasIssuance)
                || (authority == Authority.FX && !hasClearing)) {
            return reject("POSTING_AUTHORITY");
        }
        if (!credits.equals(debits)) return reject("POSTING_IMBALANCE");
        return new JournalEngine.Commit(records, new Journal.Result(1, "POSTED", ""), transaction -> {});
    }

    private static JournalEngine.Reject reject(String code) {
        return new JournalEngine.Reject(new Journal.Result(1, code, ""));
    }
}
