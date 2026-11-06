package dev.emily.economy.foundation;

import dev.emily.economy.debt.*;
import java.time.Instant;
import java.util.*;

/** Bounded descriptions of captured immutable history and independent clocks. */
public final class DebtReport {
    private DebtReport() {}
    public static List<String> cases(DebtDiagnostics.CasePage page) {
        var lines=new ArrayList<String>();
        lines.add("Collection cases at "+date(page.asOf())+" / journal frontier "+page.journalFrontier());
        for(var entry:page.entries())lines.addAll(debt(entry));
        if(page.entries().isEmpty())lines.add("No collection cases on this page.");
        if(page.nextCursor()!=null)lines.add("Next /economy debt cases "+page.nextCursor());
        return List.copyOf(lines);
    }
    private static List<String> debt(DebtDiagnostics.CaseView view) {
        var d=view.debt();
        return List.of("Case "+d.id()+" / "+d.kind()+" / facility "+d.facility()+" / revision "+d.revision(),
            "Creditor "+d.creditor().key()+" / original creditor "+d.origin().key()+" / sale "+d.sale(),
            "Principal "+CreditReport.money(d.currency(),d.principalMinor())+" / interest "+CreditReport.money(d.currency(),d.interestMinor())+" / state "+view.state()+" / payment "+d.payment(),
            "Default "+date(d.defaultAt())+" / acknowledged "+date(d.acknowledgedAt())+" / collection expiry "+date(d.collectionExpires()),
            "Collection eligible "+view.collectionEligible()+" / "+view.collectionReason()+" / collectability "+d.collectability(),
            "Default report expiry "+date(d.reportExpires())+" / visible "+view.reportVisible()+" / "+view.reportStatus());
    }
    public static List<String> reports(DebtDiagnostics.ReportPage page,UUID inspectedOwner) {
        var lines=new ArrayList<String>();
        lines.add("Payment report history at "+date(page.asOf())+" / journal frontier "+page.journalFrontier());
        for(var view:page.entries()) {
            var r=view.report();
            lines.add("Report "+r.id()+" revision "+r.revision()+" / "+r.kind()+" / facility "+r.facility()+" / obligation "+r.obligation());
            lines.add("Original due "+date(r.dueAt())+" / reported "+date(r.reportAt())+" / revision effective "+date(r.effectiveAt()));
            lines.add("Original required "+r.originalMinor()+" / unmet "+r.unmetMinor()+" minor units / snapshot met "+r.metAtSnapshot()+" / cured "+r.cured());
            lines.add("Payment-score eligible "+view.visible()+" / "+view.reason()+" / payment-score window expiry "+date(view.expiresAt()));
        }
        if(page.entries().isEmpty())lines.add("No report revisions on this page.");
        var next=page.nextCursor();
        if(next!=null)lines.add("Next /economy debt "+(inspectedOwner==null?"reports":"inspect_reports "+inspectedOwner)+" after "+next.effectiveAt()+" "+next.reportId()+" "+next.revision());
        return List.copyOf(lines);
    }
    public static List<String> events(UUID id,DebtDiagnostics.EventPage page,UUID inspectedOwner) {
        var lines=new ArrayList<String>();lines.add("Collection accounting history for "+id+" / journal frontier "+page.journalFrontier());
        for(var e:page.entries())lines.add("Receipt "+e.sequence()+"/"+e.position()+" v"+e.version()+" / "+e.kind()+" / "+date(e.effectiveAt())+" / amount "+e.amountMinor()+" / allowance "+e.allowanceMinor()+" contract-currency minor units");
        if(page.entries().isEmpty())lines.add("No accounting receipts on this page.");
        var next=page.nextCursor();if(next!=null)lines.add("Next /economy debt "+(inspectedOwner==null?"history":"inspect_history "+inspectedOwner)+" "+id+" after "+next.sequence()+" "+next.position());
        return List.copyOf(lines);
    }
    public static List<String> cardPayments(UUID id,dev.emily.economy.credit.CreditDiagnostics.PaymentPage page) {
        var lines=new ArrayList<String>();lines.add("Card payment history for "+id+" / journal frontier "+page.journalFrontier());
        for(var receipt:page.entries()) {
            var p=receipt.witness();
            lines.add("Payment "+receipt.sequence()+"/"+receipt.position()+" v"+receipt.version()+" / "+date(p.effective())+" / received "+CreditReport.money(p.currency(),p.amountMinor()));
            lines.add("Principal "+p.principalMinor()+" / interest "+p.interestMinor()+" contract-currency minor units / signed residual disposition "+p.waivedResidualMajor()+" major units / policy v"+p.policyVersion());
        }
        if(page.entries().isEmpty())lines.add("No captured payments on this page.");
        var next=page.nextCursor();if(next!=null)lines.add("Next /economy debt payments card "+id+" after "+next.sequence()+" "+next.position());
        return List.copyOf(lines);
    }
    public static List<String> loanPayments(UUID id,dev.emily.economy.loan.LoanDiagnostics.EventPage page) {
        var lines=new ArrayList<String>();lines.add("Loan payment history for "+id+" / journal frontier "+page.journalFrontier());
        for(var receipt:page.entries()) {
            var p=receipt.payment();
            lines.add("Payment "+receipt.sequence()+"/"+receipt.position()+" v"+receipt.version()+" / "+date(receipt.effectiveAt())+" / received "+p.amountMinor()+" contract-currency minor units");
            lines.add("Principal "+p.principalMinor()+" / interest "+p.interestMinor()+" / journal matches "+p.journalMatches()+" / signed residual disposition "+p.residualDispositionMajor()+" major units / additional interest waived "+p.additionalInterestWaivedMajor()+" major units");
        }
        if(page.entries().isEmpty())lines.add("No captured payments on this bounded event page.");
        var next=page.nextCursor();if(next!=null)lines.add("Next /economy debt payments loan "+id+" after "+next.sequence()+" "+next.position());
        return List.copyOf(lines);
    }
    public static List<String> operator(DebtDiagnostics.OperatorView view) {
        var lines=new ArrayList<String>();
        lines.add("Inspected "+view.kind()+" facility "+view.facility()+" / "+view.facilityStatus()+" / revision "+view.facilityRevision()+" / journal frontier "+view.journalFrontier()+" / "+date(view.asOf()));
        lines.add("Facility debt "+CreditReport.money(view.currency(),view.facilityDebtMinor())+" / oldest unmet due "+date(view.oldestUnmetDueAt())+" / default due "+date(view.defaultDueAt()));
        view.debt().ifPresent(d->lines.addAll(debt(d)));
        for(var o:view.obligations()) {
            var r=o.obligation();
            lines.add("Obligation "+r.id()+" revision "+r.revision()+" / due "+date(r.dueAt())+" / original "+r.originalMinor()+" / unmet "+r.unmetMinor()+" contract-currency minor units");
            lines.add("Arrears age "+o.arrearsAgeMillis()+" ms / "+o.reason()+" / report due "+date(o.reportDueAt())+" / payment-score expiry "+date(o.reportExpiresAt()));
            lines.add("Reported "+r.reported()+" / original snapshot met "+r.metAtReport()+" / effective "+date(r.effectiveAt()));
        }
        if(view.nextObligationCursor()!=null)lines.add("Next obligation page /economy debt inspect "+view.kind().name().toLowerCase(Locale.ROOT)+" <owner-uuid> "+view.facility()+" "+view.nextObligationCursor());
        lines.add("Sale reason "+view.saleReason()+" / next attempt "+date(view.nextSaleAttemptAt()));
        var r=view.reconciliation();
        lines.add("Shared creditor receivable expected "+r.expectedReceivableMinor()+" / posted "+r.postedReceivableMinor()+" / matches "+r.receivableMatches());
        lines.add("Borrower liability expected "+r.expectedLiabilityMinor()+" / posted "+r.postedLiabilityMinor()+" / matches "+r.liabilityMatches()+" / selected facility matches "+r.facilityMatches());
        for(var provider:view.providers())lines.add("Provider "+provider.provider()+" / "+provider.implementations()+" / completed "+date(provider.completedAt())+" / applying "+date(provider.applyingAt())+" / target "+provider.applyingCursor()+" / effects "+provider.completedEffects());
        lines.add("Debt policy v"+view.debtPolicyVersion()+" / score policy v"+view.scorePolicyVersion()+" / next policy boundary "+date(view.nextScoreBoundary())+" / "+view.scoreStatus());
        return List.copyOf(lines);
    }
    private static String date(Long time) {return time==null?"none":Instant.ofEpochMilli(time).toString();}
}
