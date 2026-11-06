package dev.emily.economy.loan;

import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.api.Identifiers.WorldId;
import dev.emily.economy.credit.CreditTreasury;
import dev.emily.economy.institution.InstitutionCatalog;
import dev.emily.economy.journal.*;
import dev.emily.economy.recovery.RecordCatalog;

/** Temporary origination funding reservation; the loan commit disburses it with one posting overlay. */
public final class LoanTreasury {
    public static final String FACT="economy:a6/loan-treasury";
    private LoanTreasury() {}
    public static JournalEngine.Decision prepare(JournalEngine.ReadView reads,WorldId world,InstitutionCatalog.InstitutionId lender,
            EconomyCurrency currency,long amount,long effective){
        var decision=CreditTreasury.reserve(reads,world,lender,currency,amount,effective);
        if(!(decision instanceof JournalEngine.Commit commit))return decision;
        return new JournalEngine.Commit(commit.records().stream().map(r->r.type().equals(CreditTreasury.FACT)?new Journal.Record(FACT,1,r.payload()):r).toList(),commit.result(),commit.projections());
    }
    public static RecordCatalog register(RecordCatalog catalog){return catalog.with(new RecordCatalog.Key(FACT,1),CreditTreasury::validateLoanFunding);}
}
