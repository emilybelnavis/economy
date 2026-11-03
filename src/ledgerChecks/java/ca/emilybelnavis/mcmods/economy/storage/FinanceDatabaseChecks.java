package ca.emilybelnavis.mcmods.economy.storage;

import java.nio.file.Files;
import java.sql.DriverManager;
import java.util.*;

/** Standalone JDBC integration checks; run with sqlite-jdbc and its runtime dependencies. */
public final class FinanceDatabaseChecks {
    public static void main(String[] args) throws Exception {
        var file=Files.createTempDirectory("economy-db-check").resolve("finance.sqlite");
        var owner=UUID.randomUUID();var other=UUID.randomUUID();String primary;
        try(var db=new FinanceDatabase(file,"CAD",Map.of("CAD",2,"USD",2),Map.of("CAD","1","USD","1.25"),List.of(new FinanceDatabase.StockDefinition("TEST","Test",100)))) {
            var accounts=db.accounts(owner).join();check(accounts.size()==2,"default accounts");primary=accounts.stream().filter(FinanceDatabase.Account::primary).findFirst().orElseThrow().id();
            db.credit(owner,primary,1000,"seed","CREDIT").join();
            check(db.quoteWithdrawal(1,"CAD","USD").join()==1,"withdrawal rounds source cost upward");
            check(db.quoteDeposit(Map.of("CAD",1L),"USD").join()==0,"subminor preview displays zero");
            var usd=db.openAccount(owner,"USD").join();db.transfer(owner,primary,usd.id(),125,"exchange").join();check(db.account(owner,usd.id()).join().balance()==100,"exact conversion");
            expectFailure(()->db.withdrawCash(owner,usd.id(),126,"CAD","too much").join());
            expectFailure(()->db.withdrawCash(owner,usd.id(),1,"CAD","stale",2).join());
            check(db.account(owner,usd.id()).join().balance()==100,"stale quote rolls back");
            check(db.withdrawCash(owner,usd.id(),1,"CAD","cash",1).join().balance()==99,"cash source ceiling");
            check(db.depositCash(owner,usd.id(),Map.of("USD",1L),"cash").join().balance()==100,"cash deposit settlement");
            expectFailure(()->db.debit(owner,primary,10000,"bad","DEBIT").join());check(db.account(owner,primary).join().balance()==875,"rollback");
            expectFailure(()->db.credit(other,primary,1,"bad","CREDIT").join());
            var trading=accounts.stream().filter(a->a.kind().equals("TRADING")).findFirst().orElseThrow();db.transfer(owner,primary,trading.id(),500,"fund").join();db.trade(owner,"TEST",true,3).join();check(db.holdings(owner).join().getFirst().quantity()==3,"buy shares");expectFailure(()->db.trade(owner,"TEST",false,4).join());check(db.account(owner,trading.id()).join().balance()==200,"failed trade atomic");
            var page=db.history(owner,primary,0,2).join();check(page.size()==2,"page size");check(db.history(owner,primary,page.getLast().sequence(),100).join().stream().allMatch(e->e.sequence()<page.getLast().sequence()),"keyset pagination");
            var legacy=UUID.randomUUID();db.importLegacy(Map.of(legacy,42L),Map.of(legacy,List.of(new FinanceDatabase.LegacyEntry("old",1,"CREDIT",2,42,"legacy"),new FinanceDatabase.LegacyEntry("gap",2,"DEBIT",1,40,"legacy gap")))).join();check(db.history(legacy,db.accounts(legacy).join().stream().filter(FinanceDatabase.Account::primary).findFirst().orElseThrow().id(),0,100).join().stream().mapToLong(FinanceDatabase.Entry::delta).sum()==42,"truncated legacy opening reconciles");db.importLegacy(Map.of(legacy,42L)).join();check(db.accounts(legacy).join().stream().filter(FinanceDatabase.Account::primary).findFirst().orElseThrow().balance()==42,"migration idempotent");
            db.closeAccount(owner,usd.id(),primary).join();check(db.accounts(owner).join().size()==2,"closed hidden");var reopened=db.openAccount(owner,"USD").join();db.closeAccount(owner,reopened.id(),null).join();
            db.updateStockPrice("TEST",125).join();check(db.quotes().join().getFirst().price()==125,"price cache invalidated");
            db.updateRates(Map.of("USD","1.5"),"2026-11-02").join();check(db.historicalRates("2026-11-02").join().get("USD").equals("1.5"),"historical rate snapshot");
            expectFailure(()->db.updateRates(Map.of("CAD","2"),"bad").join());
            check(db.exchangeRates().join().get("CAD").equals("1"),"CAD remains the reference rate");
            check(db.history(owner,primary,0,100).join().stream().filter(e->e.operation().equals("TRANSFER"))
                    .allMatch(e->e.memo().contains("CAD/unit=")),"conversion ledger contains rates");
            var imported=db.accounts(legacy).join().stream().filter(FinanceDatabase.Account::primary).findFirst().orElseThrow();
            var legacyEvents=new ArrayList<>(db.history(legacy,imported.id(),0,100).join());Collections.reverse(legacyEvents);
            long running=0;for(var event:legacyEvents){running=Math.addExact(running,event.delta());check(running==event.balance(),"each migrated ledger entry reconciles");}
        }
        try(var db=new FinanceDatabase(file,"CAD",Map.of("CAD",2,"USD",2),Map.of("CAD","1","USD","1.25"),List.of())) {check(db.account(owner,primary).join().balance()==500,"durable reopen");}
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+file);var s=c.createStatement()){expectFailure(()->{try{s.executeUpdate("DELETE FROM ledger");}catch(Exception e){throw new RuntimeException(e);}});}
        accountLifecycleCheck();
        yenPrecisionCheck();
        legacyCurrencyCheck();
        cleanupFailureCheck();
        concurrentPaymentCheck();
        System.out.println("Finance database checks passed");
    }
    private static void accountLifecycleCheck() throws Exception {
        var file=Files.createTempDirectory("economy-account-limits").resolve("finance.sqlite");
        try(var db=new FinanceDatabase(file,"CAD",Map.of("CAD",2,"USD",2,"JPY",0,"GBP",2),
                Map.of("CAD","1","USD","1.25","JPY","0.01","GBP","2"),List.of())) {
            UUID user=UUID.randomUUID(), stranger=UUID.randomUUID();
            var primary=db.accounts(user).join().stream().filter(FinanceDatabase.Account::primary).findFirst().orElseThrow();
            var usd=db.openAccount(user,"USD").join();var yen=db.openAccount(user,"JPY").join();
            expectFailure(()->db.openAccount(user,"GBP").join());
            expectFailure(()->db.openAccount(user,"USD").join());
            expectFailure(()->db.closeAccount(user,primary.id(),usd.id()).join());
            expectFailure(()->db.closeAccount(stranger,usd.id(),primary.id()).join());
            check(db.accounts(user).join().stream().filter(a->a.kind().equals("BANK")).count()==3,"maximum three currency accounts");
            db.closeAccount(user,yen.id(),null).join();
            var gbp=db.openAccount(user,"GBP").join();
            db.credit(user,usd.id(),200,"close fixture","CREDIT").join();
            expectFailure(()->db.closeAccount(user,usd.id(),null).join());
            check(db.account(user,usd.id()).join().balance()==200,"funded close without destination rolls back");
            db.closeAccount(user,usd.id(),gbp.id()).join();
            check(db.account(user,gbp.id()).join().balance()==125,"funded closure converts into chosen destination");
            check(db.accounts(user).join().stream().filter(a->a.kind().equals("BANK")).count()==2,"closed account no longer consumes slot");
            db.openAccount(user,"USD").join();
        }
    }
    private static void yenPrecisionCheck() throws Exception {
        var file=Files.createTempDirectory("economy-yen-check").resolve("finance.sqlite");
        try(var db=new FinanceDatabase(file,"CAD",Map.of("CAD",2,"JPY",0),Map.of("CAD","1","JPY","0.008870"),List.of())) {
            check(db.quoteDeposit(Map.of("JPY",1000L),"CAD").join()==887,"yen has zero decimal digits");
            check(db.quoteWithdrawal(1000,"JPY","CAD").join()==887,"yen cash source cost");
            check(db.quoteDeposit(Map.of("JPY",1L),"CAD").join()==0,"subcent yen preview");
            var user=UUID.randomUUID();var primary=db.accounts(user).join().stream().filter(FinanceDatabase.Account::primary).findFirst().orElseThrow();
            expectFailure(()->db.depositCash(user,primary.id(),Map.of("JPY",1L),"tiny").join());
            check(db.account(user,primary.id()).join().balance()==0,"zero-value deposit rejected atomically");
        }
    }
    private static void legacyCurrencyCheck() throws Exception {
        var file=Files.createTempDirectory("economy-legacy-currency").resolve("finance.sqlite");
        try(var db=new FinanceDatabase(file,"CAD",Map.of("CAD",2,"USD",2),Map.of("CAD","1","USD","1.25"),List.of())) {
            var legacy=UUID.randomUUID();var fresh=UUID.randomUUID();
            db.importLegacy(Map.of(legacy,42L),Map.of(),"USD").join();
            var imported=db.accounts(legacy).join().stream().filter(FinanceDatabase.Account::primary).findFirst().orElseThrow();
            check(imported.currency().equals("USD")&&imported.balance()==42,"legacy currency and units preserved");
            check(db.accounts(fresh).join().stream().filter(FinanceDatabase.Account::primary).findFirst().orElseThrow().currency().equals("CAD"),"new owners use current default currency");
        }
    }
    private static void concurrentPaymentCheck() throws Exception {
        var file=Files.createTempDirectory("economy-concurrent-payments").resolve("finance.sqlite");
        UUID sender=UUID.randomUUID(),recipient=UUID.randomUUID();
        String source,target;
        try(var db=new FinanceDatabase(file,"CAD",Map.of("CAD",2),Map.of("CAD","1"),List.of())) {
            source=db.accounts(sender).join().stream().filter(FinanceDatabase.Account::primary).findFirst().orElseThrow().id();
            target=db.accounts(recipient).join().stream().filter(FinanceDatabase.Account::primary).findFirst().orElseThrow().id();
            db.credit(sender,source,100,"seed","CREDIT").join();
            var submitters=java.util.concurrent.Executors.newFixedThreadPool(4);
            var attempts=new ArrayList<java.util.concurrent.CompletableFuture<Boolean>>();
            try {
                for(int i=0;i<120;i++) {
                    attempts.add(java.util.concurrent.CompletableFuture.supplyAsync(
                            ()->db.pay(sender,recipient,1,"concurrent"),submitters)
                            .thenCompose(future->future).handle((value,error)->error==null));
                }
                java.util.concurrent.CompletableFuture.allOf(attempts.toArray(java.util.concurrent.CompletableFuture[]::new)).join();
                check(attempts.stream().filter(java.util.concurrent.CompletableFuture::join).count()==100,"no overdraft under concurrent submissions");
            } finally {submitters.shutdown();}
            check(db.account(sender,source).join().balance()==0&&db.account(recipient,target).join().balance()==100,"serialized balances conserve money");
        }
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+file);var statement=connection.createStatement();
                var rows=statement.executeQuery("SELECT transaction_id,COUNT(*),SUM(delta) FROM ledger WHERE operation='PAYMENT' GROUP BY transaction_id")) {
            int payments=0;
            while(rows.next()) {
                check(!rows.getString(1).isBlank(),"payment has transaction identity");
                check(rows.getInt(2)==2&&rows.getLong(3)==0,"each payment has two balanced ledger entries");
                payments++;
            }
            check(payments==100,"failed payments leave no partial ledger rows");
        }
    }
    private static void cleanupFailureCheck() throws Exception {
        var file=Files.createTempDirectory("economy-cleanup-check").resolve("finance.sqlite");
        UUID owner=UUID.randomUUID();String account;
        try(var db=new FinanceDatabase(file,"CAD",Map.of("CAD",2),Map.of("CAD","1"),List.of())) {
            account=db.accounts(owner).join().stream().filter(FinanceDatabase.Account::primary).findFirst().orElseThrow().id();
            var field=FinanceDatabase.class.getDeclaredField("connection");field.setAccessible(true);
            var actual=(java.sql.Connection)field.get(db);
            var proxy=(java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(
                    FinanceDatabaseChecks.class.getClassLoader(),new Class<?>[]{java.sql.Connection.class},(object,method,args)->{
                        if(method.getName().equals("setAutoCommit")&&Boolean.TRUE.equals(args[0])) {
                            throw new java.sql.SQLException("Injected post-commit cleanup failure");
                        }
                        try{return method.invoke(actual,args);}
                        catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
                    });
            field.set(db,proxy);
            check(db.credit(owner,account,12,"cleanup test","CREDIT").join().balance()==12,"committed write remains successful on cleanup failure");
            expectFailure(()->db.account(owner,account).join());
        }
        try(var db=new FinanceDatabase(file,"CAD",Map.of("CAD",2),Map.of("CAD","1"),List.of())) {
            check(db.account(owner,account).join().balance()==12,"committed money survives poisoned connection");
        }
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void expectFailure(Runnable work){try{work.run();}catch(RuntimeException expected){return;}throw new AssertionError("Expected failure");}
}
