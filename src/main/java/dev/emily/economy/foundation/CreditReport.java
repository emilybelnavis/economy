package dev.emily.economy.foundation;

import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.credit.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Bounded owner-facing labels; token identities and raw financial payloads never reach the screen. */
public final class CreditReport {
    private CreditReport() {}
    public static List<String> owner(CreditDiagnostics.FacilityView v){
        var c=v.currency();var lines=new ArrayList<String>();
        lines.add(v.issuer()+" · "+v.network());lines.add("Status: "+v.status()+" / "+v.access());
        lines.add("Limit: "+money(c,v.limitMinor()));lines.add("Contractual available credit: "+money(c,v.availableCreditMinor()));
        lines.add("New spending still requires current eligibility and a valid card.");
        lines.add("Current posted balance: "+money(c,v.currentBalanceMinor()));
        lines.add("Posted principal: "+money(c,v.principalMinor()));lines.add("Posted interest: "+money(c,v.postedInterestMinor()));
        lines.add("Exact balance including rounding carry: "+v.exactBalanceMajor().toPlainString()+" "+c.code());
        bucket(lines,c,v.purchase());bucket(lines,c,v.advance());
        lines.add("Outstanding required minimum: "+money(c,v.requiredMinor()));lines.add("Past-due minimum: "+money(c,v.pastDueMinor()));
        lines.add("Oldest unmet due: "+date(v.dueAt()));
        if(v.latestStatement()!=null){var s=v.latestStatement();lines.add("Latest frozen statement balance: "+money(c,s.balanceMinor()));lines.add("Statement closed: "+date(s.closedAt())+"; due: "+date(s.dueAt()));}
        else lines.add("No statement has closed yet.");
        lines.add("Next daily charge: "+date(v.nextDay()));lines.add("Next statement: "+date(v.nextStatement()));
        if(!v.scheduleCurrent())lines.add("Scheduled work is pending; amounts are the saved snapshot.");
        return List.copyOf(lines);
    }
    private static void bucket(List<String> lines,EconomyCurrency currency,CreditDiagnostics.BucketView b){
        lines.add(b.kind()+": principal "+money(currency,b.principalMinor())+"; posted interest "+money(currency,b.postedInterestMinor()));
        lines.add("APR: "+b.apr().movePointRight(2).stripTrailingZeros().toPlainString()+"%; period earned: "+b.periodEarnedMajor().toPlainString()+" "+currency.code());
        lines.add("Signed rounding carry: "+b.residualMajor().toPlainString()+" "+currency.code());
    }
    public static List<String> statement(CreditFacilities.StatementSnapshot snapshot,EconomyCurrency currency){
        var s=snapshot.statement();
        return List.of("Statement "+s.id(),"Closed: "+date(s.closedAt())+"; due: "+date(s.dueAt()),
            "Frozen statement balance: "+money(currency,s.debtMinor()), "Principal at close: "+money(currency,s.principalMinor()),
            "Interest earned in this period: "+s.periodInterestMajor().toPlainString()+" "+currency.code(),
            "New minimum: "+money(currency,s.newRequirementMinor())+"; total required at close: "+money(currency,s.totalRequiredMinor()),
            "Statement version: 1. Later payments do not rewrite these amounts.");
    }
    public static List<String> operator(CreditDiagnostics.OperatorView view,String section,int allocationOffset){
        if(!Set.of("daily","payments","events").contains(section)||allocationOffset<0||allocationOffset>260)throw new IllegalArgumentException("Credit report section/cursor");
        var lines=new ArrayList<String>();var f=view.facility();var c=f.currency();var r=view.reconciliation();
        lines.add("Credit facility "+f.id()+" / revision "+f.revision()+"; journal frontier "+view.journalFrontier());
        lines.add("Saved current balance: "+money(c,f.currentBalanceMinor())+"; frozen latest statement: "+(f.latestStatement()==null?"None":money(c,f.latestStatement().balanceMinor())));
        lines.add("Owner currency debt / borrower liability: "+r.ownerCurrencyDebtMinor()+" / "+r.borrowerLiabilityMinor()+" minor; match="+r.ownerMatches());
        lines.add("Issuer currency debt / receivable: "+r.issuerCurrencyDebtMinor()+" / "+r.issuerReceivableMinor()+" minor; match="+r.issuerMatches());
        lines.add("Owner journal sum: "+r.ownerJournalMinor()+" minor; complete="+r.ownerJournalComplete()+"; match="+r.ownerJournalMatches());
        lines.add("Issuer journal sum: "+r.issuerJournalMinor()+" minor; complete="+r.issuerJournalComplete()+"; match="+r.issuerJournalMatches());
        if(!r.ownerFacilitiesComplete()||!r.issuerFacilitiesComplete()||!r.ownerJournalComplete()||!r.issuerJournalComplete())lines.add("Reconciliation incomplete: a bounded history window was exceeded; partial totals are not full balances.");
        lines.add("Backlog: "+view.schedule().overdueDays()+" daily charge(s), "+view.schedule().overdueStatements()+" statement(s)");
        for(var p:view.providers())lines.add(p.provider()+": completed="+date(p.completedAt())+" applying="+date(p.applyingAt())+" cursor="+p.applyingCursor());
        CreditDiagnostics.EventCursor next;
        switch(section){
            case "daily"->{
                for(var receipt:view.daily().entries()){
                    var w=receipt.witness();lines.add("Daily receipt "+receipt.sequence()+":"+receipt.position()+" v"+receipt.version()+" policy "+w.policyVersion()+" at "+date(w.effective()));
                    lines.add("Purchase basis "+w.beforePurchase().principalMinor()+" principal + "+w.beforePurchase().interestMinor()+" interest minor; residual "+w.beforePurchase().residualMajor()+"; APR "+w.beforePurchase().apr());
                    lines.add("Purchase earned "+w.purchase().earnedMajor()+" "+c.code()+"; posted "+w.purchase().postedMinor()+" minor; retained "+w.purchase().bucket().residualMajor());
                    lines.add("Advance basis "+w.beforeAdvance().principalMinor()+" principal + "+w.beforeAdvance().interestMinor()+" interest minor; residual "+w.beforeAdvance().residualMajor()+"; APR "+w.beforeAdvance().apr());
                    lines.add("Advance earned "+w.advance().earnedMajor()+" "+c.code()+"; posted "+w.advance().postedMinor()+" minor; retained "+w.advance().bucket().residualMajor()+". Stored calculation verified.");
                }
                if(view.daily().entries().isEmpty())lines.add("No captured daily inputs on this page. Legacy events may contain totals only.");next=view.daily().nextCursor();
            }
            case "payments"->{
                if(view.payments().entries().size()>1)throw new IllegalArgumentException("Payment report uses one receipt per page");
                for(var receipt:view.payments().entries()){
                    var w=receipt.witness();lines.add("Payment receipt "+receipt.sequence()+":"+receipt.position()+" v"+receipt.version()+" at "+date(w.effective()));
                    lines.add("Received "+money(c,w.amountMinor())+"; principal "+money(c,w.principalMinor())+"; interest "+money(c,w.interestMinor()));
                    lines.add("Signed payoff residual disposition: "+w.waivedResidualMajor()+" "+c.code());
                    if(allocationOffset>w.allocations().size())throw new IllegalArgumentException("Allocation offset");
                    int end=Math.min(w.allocations().size(),allocationOffset+16);
                    for(var a:w.allocations().subList(allocationOffset,end))lines.add("Allocation: "+(a.statementId().isEmpty()?"unbilled":a.statementId())+" / "+a.kind()+" / "+(a.interest()?"interest":"principal")+" / "+money(c,a.minor()));
                    if(end<w.allocations().size())lines.add("More allocations: repeat the same receipt page with offset "+end);
                }
                if(view.payments().entries().isEmpty())lines.add("No captured payment allocation on this page. Legacy allocations are unavailable.");next=view.payments().nextCursor();
            }
            default->{for(var event:view.legacyEvents().entries())lines.add("Event "+event.sequence()+":"+event.position()+" v"+event.version()+" "+event.kind()+" at "+date(event.effectiveAt())+" "+new TreeMap<>(event.details()));
                if(view.legacyEvents().entries().isEmpty())lines.add("No legacy-format events on this page.");next=view.legacyEvents().nextCursor();}
        }
        if(next!=null)lines.add("Next "+section+" page: after "+next.sequence()+" "+next.position());
        return List.copyOf(lines);
    }
    public static String money(EconomyCurrency c,long minor){return BigDecimal.valueOf(minor).divide(BigDecimal.valueOf(c.minorUnitsPerMajor())).toPlainString()+" "+c.code();}
    private static String date(Long value){return value==null?"None":Instant.ofEpochMilli(value).toString();}
}
