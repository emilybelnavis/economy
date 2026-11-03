package ca.emilybelnavis.mcmods.economy;

import java.util.*;

/** Standalone exact-change checks; does not start Minecraft or touch a world. */
public final class WithdrawalPlannerChecks {
    private static WithdrawalPlanner.Denomination bill(String id,long amount){return new WithdrawalPlanner.Denomination(id,amount,false);}
    private static WithdrawalPlanner.Denomination coin(String id,long amount){return new WithdrawalPlanner.Denomination(id,amount,true);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void rejects(Runnable action){try{action.run();throw new AssertionError("Expected an invalid cash plan");}catch(IllegalArgumentException expected){}}
    private static void exact(WithdrawalPlanner.Plan plan,List<WithdrawalPlanner.Denomination> denominations){
        long sum=0;int pieces=0;for(var d:denominations){int n=(d.coin()?plan.coins():plan.bills()).getOrDefault(d.id(),0);sum=Math.addExact(sum,Math.multiplyExact(d.minor(),n));pieces+=n;}
        check(sum==plan.amount(),"Plan must exactly preserve value");check(pieces==plan.pieces(),"Piece count must match");
    }
    public static void main(String[] args){
        var noncanonical=List.of(bill("six",6),bill("four",4));
        var eight=WithdrawalPlanner.plan(8,noncanonical,false);exact(eight,noncanonical);check(eight.bills().get("four")==2,"DP must solve a greedy dead end");
        check(WithdrawalPlanner.plan(12,noncanonical,false).pieces()==2,"ATM must minimize bills");
        var atm=List.of(bill("twenty",2000),bill("fifty",5000));
        rejects(()->WithdrawalPlanner.plan(5000,atm,false));
        rejects(()->WithdrawalPlanner.plan(2000,List.of(bill("six",6000),bill("four",4000)),false));
        var hundred=WithdrawalPlanner.plan(10000,atm,false);check(hundred.bills().get("fifty")==2,"Prefer two fifties");
        var twenties=WithdrawalPlanner.adjust(10000,atm,hundred,"fifty",-1,false);exact(twenties,atm);check(twenties.bills().get("twenty")==5,"Minus must skip an infeasible intermediate count");
        check(WithdrawalPlanner.adjust(10000,atm,hundred,"twenty",1,false).bills().get("twenty")==5,"Plus must find the nearest feasible count");
        rejects(()->WithdrawalPlanner.planWithBills(10000,atm,Map.of("twenty",1),false));
        var cad=List.of(bill("fifty",5000),bill("twenty",2000),bill("ten",1000),bill("five",500),coin("two",200),coin("one",100),coin("half",50),coin("quarter",25),coin("dime",10),coin("nickel",5));
        var teller=WithdrawalPlanner.plan(8775,cad,true);exact(teller,cad);check(teller.bills().values().stream().mapToInt(Integer::intValue).sum()==4,"Teller must maximize bill coverage");check(teller.coins().equals(Map.of("two",1,"half",1,"quarter",1)),"Coin remainder must use fewest coins");
        var forced=WithdrawalPlanner.planWithBills(8775,cad,Map.of("five",3),true);exact(forced,cad);check(forced.bills().get("five")==3,"Exact bill count must be honored");
        var coins=List.of(coin("four",4),coin("three",3),coin("one",1));check(WithdrawalPlanner.plan(6,coins,true).coins().equals(Map.of("three",2)),"Coins must use exact DP too");
        rejects(()->WithdrawalPlanner.planWithBills(6,coins,Map.of("three",1),true));
        rejects(()->WithdrawalPlanner.plan(0,atm,false));
        rejects(()->WithdrawalPlanner.plan(-1,atm,false));
        rejects(()->WithdrawalPlanner.plan(2305,List.of(bill("one",1)),false));
        rejects(()->WithdrawalPlanner.plan(1_000_001,List.of(bill("one",1)),false));
        check(WithdrawalPlanner.plan(2304,List.of(bill("one",1)),false).pieces()==2304,"Piece cap boundary must work");
        var huge=List.of(bill("huge",Long.MAX_VALUE));exact(WithdrawalPlanner.plan(Long.MAX_VALUE,huge,false),huge);
        // For every small allowed amount, compare ATM DP with an exhaustive two-denomination optimum.
        for(int amount=4;amount<=240;amount+=4){int best=Integer.MAX_VALUE;for(int six=0;six*6<=amount;six++)for(int four=0;six*6+four*4<=amount;four++)if(six*6+four*4==amount)best=Math.min(best,six+four);var plan=WithdrawalPlanner.plan(amount,noncanonical,false);check(plan.pieces()==best,"ATM optimum differs at "+amount);exact(plan,noncanonical);}
        System.out.println("Withdrawal planner checks passed");
    }
}
