package dev.emily.economy.credit;

import dev.emily.economy.journal.Journal;
import dev.emily.economy.journal.JournalEngine;
import java.util.Objects;

/** Production new-exposure admission remains dormant until the downstream lifecycle is accepted. */
public final class CreditSafety {
    public static final String STATUS="CREDIT_LIFECYCLE_UNAVAILABLE";
    private CreditSafety() {}
    /** Apply inside the journal handler so original committed outcomes still replay before this gate. */
    public static JournalEngine.Registration production(JournalEngine.Registration foundation){
        Objects.requireNonNull(foundation);
        return new JournalEngine.Registration(foundation.canonicalVersion(),(intent,reads)->
                new JournalEngine.Decline(new Journal.Result(1,STATUS,"Default/reporting lifecycle is not installed.")));
    }
}
