package ca.emilybelnavis.mcmods.economy;

import java.util.*;

/** Bounded exact cash change. Teller plans maximize bill coverage before minimizing pieces. */
public final class WithdrawalPlanner {
    public static final int MAX_PIECES = 2304;
    public static final int MAX_STATES = 1_000_000;
    public record Denomination(String id, long minor, boolean coin) {
        public Denomination { if (id == null || id.isBlank() || minor <= 0) throw new IllegalArgumentException("Invalid denomination"); }
    }
    public record Plan(long amount, Map<String,Integer> bills, Map<String,Integer> coins, int pieces) {
        public Plan { bills=Map.copyOf(bills); coins=Map.copyOf(coins); }
    }
    private static final int UNREACHABLE=MAX_PIECES+1;
    private record Table(int[] counts,int[] choices,List<Denomination> denominations) {}
    private final long amount, unit;
    private final int target;
    private final boolean teller;
    private final Map<String,Denomination> catalog;
    private final Table bills, coins;

    private WithdrawalPlanner(long amount,List<Denomination> denominations,Set<String> fixed,boolean teller) {
        if(amount<=0)throw new IllegalArgumentException("Enter a positive withdrawal amount.");
        this.amount=amount;this.teller=teller;
        catalog=new TreeMap<>();
        for(var denomination:denominations) {
            if(!teller&&denomination.coin())continue;
            if(catalog.putIfAbsent(denomination.id(),denomination)!=null)throw new IllegalArgumentException("Duplicate denomination.");
        }
        if(catalog.isEmpty())throw new IllegalArgumentException("No cash denominations are enabled for this currency.");
        long smallestBill=catalog.values().stream().filter(d->!d.coin()).mapToLong(Denomination::minor).min().orElse(0);
        if(!teller&&(smallestBill==0||amount%smallestBill!=0))throw new IllegalArgumentException("ATM amount must be a multiple of its smallest enabled bill.");
        long gcd=0;for(var d:catalog.values())gcd=gcd(gcd,d.minor());unit=gcd;
        if(amount%unit!=0)throw new IllegalArgumentException("The amount cannot be represented by the enabled cash.");
        long states=amount/unit;
        if(states>MAX_STATES)throw new IllegalArgumentException("Withdrawal amount is too large for one transaction.");
        target=(int)states;
        for(String id:fixed)if(!catalog.containsKey(id)||catalog.get(id).coin())throw new IllegalArgumentException("Only enabled bills can be adjusted.");
        bills=table(catalog.values().stream().filter(d->!d.coin()&&!fixed.contains(d.id())).toList());
        coins=table(catalog.values().stream().filter(Denomination::coin).toList());
    }
    private static long gcd(long a,long b){while(b!=0){long r=a%b;a=b;b=r;}return a;}
    private Table table(List<Denomination> denominations) {
        // One deterministic art variant per value avoids extra DP work while preserving explicitly fixed variants.
        Map<Long,Denomination> unique=new TreeMap<>(Comparator.reverseOrder());
        for(var d:denominations)unique.putIfAbsent(d.minor(),d);
        List<Denomination> values=List.copyOf(unique.values());
        int[] counts=new int[target+1], choices=new int[target+1];Arrays.fill(counts,UNREACHABLE);Arrays.fill(choices,-1);counts[0]=0;
        for(int value=1;value<=target;value++)for(int i=0;i<values.size();i++) {
            long step=values.get(i).minor()/unit;if(step>value)continue;
            int candidate=counts[value-(int)step]+1;
            if(candidate<counts[value]){counts[value]=candidate;choices[value]=i;}
        }
        return new Table(counts,choices,values);
    }
    private Plan solve(Map<String,Integer> fixed) {
        long fixedValue=0;int fixedPieces=0;
        for(var entry:fixed.entrySet()) {
            var denomination=catalog.get(entry.getKey());
            if(denomination==null||denomination.coin()||entry.getValue()==null||entry.getValue()<0||entry.getValue()>MAX_PIECES)throw new IllegalArgumentException("Invalid bill quantity.");
            fixedValue=Math.addExact(fixedValue,Math.multiplyExact(denomination.minor(),entry.getValue()));
            fixedPieces=Math.addExact(fixedPieces,entry.getValue());
        }
        if(fixedValue>amount||fixedPieces>MAX_PIECES)throw new IllegalArgumentException("Selected bills exceed the withdrawal amount.");
        int remaining=(int)((amount-fixedValue)/unit);
        for(int billValue=remaining;billValue>=0;billValue--) {
            int coinValue=remaining-billValue;
            if(!teller&&coinValue!=0)break;
            int pieces=fixedPieces+bills.counts()[billValue]+coins.counts()[coinValue];
            if(bills.counts()[billValue]>=UNREACHABLE||coins.counts()[coinValue]>=UNREACHABLE||pieces>MAX_PIECES)continue;
            var billPlan=new TreeMap<String,Integer>();fixed.forEach((id,count)->{if(count>0)billPlan.put(id,count);});
            reconstruct(bills,billValue,billPlan);
            var coinPlan=new TreeMap<String,Integer>();reconstruct(coins,coinValue,coinPlan);
            return new Plan(amount,billPlan,coinPlan,pieces);
        }
        throw new IllegalArgumentException("No exact cash combination is available for this amount and bill selection.");
    }
    private void reconstruct(Table table,int value,Map<String,Integer> result) {
        while(value>0){int index=table.choices()[value];if(index<0)throw new IllegalStateException("Unreachable cash plan");var denomination=table.denominations().get(index);result.merge(denomination.id(),1,Integer::sum);value-=(int)(denomination.minor()/unit);}
    }
    public static Plan plan(long amount,List<Denomination> denominations,boolean teller) {
        return planWithBills(amount,denominations,Map.of(),teller);
    }
    /** Listed bill counts are fixed exactly; all other bill counts and all coin counts are optimized. */
    public static Plan planWithBills(long amount,List<Denomination> denominations,Map<String,Integer> fixedBills,boolean teller) {
        try{return new WithdrawalPlanner(amount,List.copyOf(denominations),Set.copyOf(fixedBills.keySet()),teller).solve(Map.copyOf(fixedBills));}
        catch(ArithmeticException overflow){throw new IllegalArgumentException("Withdrawal amount or quantity is too large.",overflow);}
    }
    /** Finds the nearest feasible count in the requested direction, skipping impossible intermediate counts. */
    public static Plan adjust(long amount,List<Denomination> denominations,Plan current,String id,int direction,boolean teller) {
        if(direction!=1&&direction!=-1)throw new IllegalArgumentException("Choose plus or minus.");
        var planner=new WithdrawalPlanner(amount,List.copyOf(denominations),Set.of(id),teller);
        var denomination=planner.catalog.get(id);
        int maximum=(int)Math.min(MAX_PIECES,amount/denomination.minor());
        for(int count=current.bills().getOrDefault(id,0)+direction;count>=0&&count<=maximum;count+=direction) {
            try{return planner.solve(Map.of(id,count));}catch(IllegalArgumentException ignored){}
        }
        throw new IllegalArgumentException("No exact cash combination exists in that direction.");
    }
    private WithdrawalPlanner(){throw new AssertionError();}
}
