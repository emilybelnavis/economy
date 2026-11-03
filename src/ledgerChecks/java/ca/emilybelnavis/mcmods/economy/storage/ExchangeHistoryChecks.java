package ca.emilybelnavis.mcmods.economy.storage;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;

/** Historical pair reads use recorded matching dates, not the current rate cache. */
public final class ExchangeHistoryChecks {
    public static void main(String[] args) throws Exception {
        var file=Files.createTempDirectory("economy-exchange-history-").resolve("fixture.sqlite");
        try(var db=new FinanceDatabase(file,"CAD",Map.of("CAD",2,"USD",2,"EUR",2),
                Map.of("CAD","1","USD","1.25","EUR","1.5"),List.of())) {
            for(int i=0;i<20;i++) {
                String date=LocalDate.of(2026,10,1).plusDays(i).toString();
                db.updateRates(Map.of("CAD","1","USD","1.25","EUR","1.5"),date).join();
            }
            var first=db.exchangeRateHistory("USD","EUR","",15).join();
            check(first.size()==15&&first.getFirst().date().equals("2026-10-20"),"descending bounded page");
            check(first.getFirst().rate().equals("0.833333333333"),"cross rate from historical pair");
            var next=db.exchangeRateHistory("USD","EUR",first.getLast().date(),15).join();
            check(next.size()==5&&next.getFirst().date().equals("2026-10-05"),"exclusive date cursor");
            db.updateRates(Map.of("USD","2"),"2026-10-21").join();
            check(db.exchangeRateHistory("USD","CAD","",15).join().getFirst().rate().equals("2"),"implicit CAD reference for partial batch");
            check(db.exchangeRateHistory("CAD","USD","",15).join().getFirst().rate().equals("0.5"),"inverse CAD reference");
            check(db.exchangeRateHistory("USD","EUR","",15).join().getFirst().date().equals("2026-10-20"),"missing pair date is not fabricated");
            check(db.exchangeRateHistory("USD","EUR","",15).join().getFirst().rate().equals("0.833333333333"),"current rate update does not rewrite history");
            check(db.exchangeRateHistory("EUR","EUR","",15).join().getFirst().rate().equals("1"),"same-currency rate");
            check(db.exchangeRateHistory("CAD","CAD","",15).join().size()==15,"reference dates bounded");
            check(db.exchangeRateHistory("USD","EUR","2026-01-01",15).join().isEmpty(),"empty range");
            expectFailure(()->db.exchangeRateHistory("BAD","EUR","",15).join());
            expectFailure(()->db.exchangeRateHistory("USD","EUR","not-a-date",15).join());
        }
        System.out.println("Exchange history checks passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void expectFailure(Runnable action){try{action.run();}catch(RuntimeException expected){return;}throw new AssertionError("Expected rejection");}
}
