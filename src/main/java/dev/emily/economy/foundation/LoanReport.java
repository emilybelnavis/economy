package dev.emily.economy.foundation;

import dev.emily.economy.loan.*;
import java.time.Instant;
import java.util.*;

/** Bounded labels from captured contracts and receipts, without raw private payloads. */
public final class LoanReport {
    private LoanReport() {}
    public static List<String> owner(LoanDiagnostics.ContractView view){
        var l=view.contract();var s=l.servicing();var lines=new ArrayList<String>();
        lines.add("Loan "+l.id()+" / "+l.lender().key()+" / "+l.status()+" / revision "+l.revision());
        lines.add("Fixed unsecured "+l.product()+"; currency "+l.currency().code()+"; original principal "+CreditReport.money(l.currency(),l.originalPrincipalMinor()));
        lines.add("Captured APR "+l.apr().movePointRight(2).stripTrailingZeros().toPlainString()+"%; prime "+l.primeApr()+" v"+l.primeVersion()+"; policy v"+l.policyVersion());
        lines.add("Captured score "+l.capturedScore()+" / profile revision "+l.profileRevision()+" / Tier "+l.tier()+". Later rates do not reprice this contract.");
        lines.add("Term "+l.years()+" financial years; 13 payments/year; installment "+CreditReport.money(l.currency(),s.installmentMinor()));
        lines.add("Posted principal "+CreditReport.money(l.currency(),s.principalMinor())+"; posted interest "+CreditReport.money(l.currency(),s.interestMinor()));
        lines.add("Saved accrued interest "+s.accruedMajor()+"; signed residual "+s.residualMajor()+" "+l.currency().code());
        lines.add("As-of exact debt: "+(view.current()?view.exactDebtMajor()+" "+l.currency().code():"unavailable; saved amounts only"));
        lines.add("Outstanding required "+CreditReport.money(l.currency(),view.requiredMinor())+"; past due "+CreditReport.money(l.currency(),view.pastDueMinor())+"; oldest due "+date(view.oldestDueAt()));
        lines.add("Next monthly close "+(l.status()!=Loans.Status.ACTIVE?"none":date(l.nextMonth()))+"; overdue boundaries "+view.overdueMonths()+"; "+view.availability());
        if(l.status()==Loans.Status.DEFAULTED)lines.add("Debt is frozen at default. Repayment uses its collection case and current creditor.");
        lines.add("Claim: "+(s.closed()?"SETTLED":"unsecured lender receivable")+"; collateral: unsupported (B6 adapter required).");
        return List.copyOf(lines);
    }
    public static List<String> schedule(LoanDiagnostics.SchedulePage page){
        var lines=new ArrayList<String>();lines.add("Original projected schedule v"+page.version()+" / "+page.scheduleHash()+". Early payments may shorten actual payoff; historical rows are unchanged.");
        for(var row:page.entries())lines.add("#"+row.ordinal()+" due "+date(row.dueAt())+": payment "+row.paymentMinor()+", principal "+row.principalMinor()+", interest "+row.interestMinor()+", remaining principal "+row.remainingPrincipalMinor()+" "+page.currency().code()+" minor units");
        if(page.entries().isEmpty())lines.add("No schedule rows on this page.");
        if(page.nextOrdinal()!=null)lines.add("Next schedule page: after "+page.nextOrdinal());return List.copyOf(lines);
    }
    public static List<String> installments(Loans.InstallmentPage page){
        var lines=new ArrayList<String>();lines.add("Frozen monthly installments v1; amounts are contract-currency minor units, earned interest in major units.");
        for(var row:page.installments())lines.add("#"+row.ordinal()+" due "+date(row.dueAt())+": original required "+row.originalMinor()+", earned "+row.earnedMajor()+", posted interest "+row.postedInterestMinor()+", debt at close "+row.debtMinor());
        if(page.installments().isEmpty())lines.add("No closed installments on this page.");
        if(page.nextOrdinal()!=null)lines.add("Next installments page: after "+page.nextOrdinal());return List.copyOf(lines);
    }
    public static List<String> payoff(Loans.PaymentQuote quote){
        var r=quote.request();return List.of("Full payoff preview: "+CreditReport.money(quote.payoff().source().currency(),r.amountMinor()),
            "Calculated "+date(r.capturedAt())+"; expires "+date(r.expiresAt())+"; loan revision "+r.loanRevision()+"; funding revision "+r.fundingRevision()+"; ledger revision "+r.fundingLedgerRevision(),
            "Preview only. No debit or interest waiver occurred. Different funding currency needs a separate explicit FX and fee quote before payment.",
            "A successful fresh full payoff alone waives additional unposted interest earned within this quote window. State changes require a new confirmation.");
    }
    public static List<String> application(Loans.ApplicationDecision d){
        return List.of("Application "+d.application()+": "+d.reason()+" at "+date(d.effectiveAt()),
            "Lender "+d.lender().key()+" / "+d.product()+"; requested "+CreditReport.money(d.currency(),d.requestedMinor())+" / "+d.years()+" years",
            "Captured profile "+(d.established()?"score "+d.capturedScore()+" revision "+d.profileRevision():"not established")+"; Tier "+d.tier()+"; policy v"+d.policyVersion(),
            "Captured prime "+d.primeApr()+" v"+d.primeVersion()+"; APR "+d.apr()+"; maximum term "+d.maximumYears(),
            "Captured world exposure "+d.currentExposureWorldMinor()+"; maximum principal "+d.maximumPrincipalWorldMinor()+"; exposure ceiling "+d.totalExposureCeilingWorldMinor()+" "+d.worldCurrency().code()+" minor; FX publication "+d.fxPublication());
    }
    public static List<String> operator(LoanDiagnostics.OperatorView view){
        var lines=new ArrayList<>(owner(view.contract()));var r=view.reconciliation();
        lines.add("Inspection journal frontier "+view.journalFrontier()+" at "+date(view.asOf()));
        lines.add("Loan owner debt / liability: "+r.ownerCurrencyDebtMinor()+" / "+r.borrowerLiabilityMinor()+"; match="+r.ownerMatches());
        lines.add("Shared lender card+loan debt / receivable: "+r.issuerCurrencyDebtMinor()+" / "+r.issuerReceivableMinor()+"; match="+r.issuerMatches());
        lines.add("Owner journal sum "+r.ownerJournalMinor()+"; complete="+r.ownerJournalComplete()+"; match="+r.ownerJournalMatches());
        lines.add("Lender journal sum "+r.issuerJournalMinor()+"; complete="+r.issuerJournalComplete()+"; match="+r.issuerJournalMatches());
        if(!r.ownerFacilitiesComplete()||!r.issuerFacilitiesComplete()||!r.ownerJournalComplete()||!r.issuerJournalComplete())lines.add("Reconciliation incomplete: bounded history/portfolio exceeded. Partial totals are not full balances.");
        lines.add("Monthly provider: "+view.provider());
        lines.add(view.currentPrimeStatus());
        view.application().ifPresent(d->lines.addAll(application(d)));
        view.funding().ifPresent(f->lines.add("Captured funding: "+f));
        if(view.funding().isEmpty())lines.add("Original funding witness unavailable; no reconstruction from current capital.");
        for(var e:view.events().entries()){
            lines.add("Event "+e.sequence()+":"+e.position()+" v"+e.version()+" "+e.kind()+" at "+date(e.effectiveAt())+" "+new TreeMap<>(e.details()));
            var p=e.payment();if(p!=null){
                lines.add("Received "+p.amountMinor()+" = principal "+p.principalMinor()+" + interest "+p.interestMinor()+" contract-currency minor; journal match="+p.journalMatches());
                lines.add("Recognized interest "+p.recognizedInterestMinor()+" minor; signed residual disposition "+p.residualDispositionMajor()+"; additional interest waived "+p.additionalInterestWaivedMajor()+" major.");
                lines.add("Allocation count "+p.allocations().size()+"; totals exclude FX fees.");
            }
        }
        var next=view.events().nextCursor();if(next!=null)lines.add("Next events: after "+next.sequence()+" "+next.position());
        return List.copyOf(lines);
    }
    private static String date(Long time){return time==null?"none":Instant.ofEpochMilli(time).toString();}
}
