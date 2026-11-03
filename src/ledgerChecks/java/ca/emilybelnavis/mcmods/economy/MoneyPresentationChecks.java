package ca.emilybelnavis.mcmods.economy;

import ca.emilybelnavis.mcmods.economy.storage.FinanceDatabase;
public final class MoneyPresentationChecks {
    public static void main(String[] args) {
        for (String currency : new String[]{"CAD","AUD","NZD","USD","HKD"}) check(Money.format(12345,currency).equals("$123.45 "+currency),currency);
        check(Money.format(12345,"GBP").equals("£123.45"),"GBP");
        check(Money.format(12345,"EUR").equals("€123.45"),"EUR");
        check(Money.format(12345,"DKK").equals("123.45 kr"),"DKK");
        check(Money.format(12345,"CNY").equals("CN¥123.45"),"CNY");
        check(Money.format(12345,"JPY").equals("JP¥12345"),"JPY");
        check(Money.format(-1,"CAD").equals("$-0.01 CAD"),"signed cents");
        check(HistoryPresentation.utcDate(0).equals("1970-01-01, 00:00:00"),"UTC seconds");
        var entry=new FinanceDatabase.Entry(1,"CASH_DEPOSIT","account",0,8895,8895,
            "Terminal cash deposit [Cash deposit credit=8895 CAD minor; CAD CAD/unit=1; native=50000 HKD minor; HKD CAD/unit=0.1779; each currency rounding=FLOOR]","tx","CAD");
        check(HistoryPresentation.memo(entry,"CAD").equals("Cash Deposit — HKD @ 0.1779"),"short FX memo");
        var plain=new FinanceDatabase.Entry(2,"CASH_DEPOSIT","account",0,500,500,
            "Cash deposit credit=500 CAD minor; CAD CAD/unit=1; native=500 CAD minor","tx","CAD");
        check(HistoryPresentation.memo(plain,"CAD").equals("Cash Deposit"),"domestic memo");
        System.out.println("Money presentation checks passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
