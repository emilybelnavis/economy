package ca.emilybelnavis.mcmods.economy.storage;

import java.nio.file.*;
import java.sql.*;
import java.math.*;
import java.util.*;
import java.util.concurrent.*;

/** All JDBC access, including initialization, is confined to one FIFO writer thread. */
public final class FinanceDatabase implements AutoCloseable {
    public record Account(String id, String owner, String currency, String kind, boolean primary, long balance) {}
    public record Entry(long sequence, String operation, String account, long timestamp, long delta, long balance, String memo, String transactionId) {}
    public record LegacyEntry(String id, long timestamp, String type, long amount, long balance, String memo) {}
    public record StockDefinition(String id, String name, long baseCadMinor) {}
    public record Quote(String id, String name, long price) {}
    public record Holding(String stock, long quantity) {}
    public record Order(long id, String owner, String stock, String side, long quantity, long price, String status) {}
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "economy-finance"); t.setDaemon(true); return t; });
    private final String defaultCurrency;
    private final Map<String,Integer> scales;
    private Connection connection;
    private SQLException poisoned;
    private String transactionId;
    private CompletableFuture<Void> initialization;
    private final Set<String> changedOwners = new HashSet<>();
    private final Set<String> changedHoldings = new HashSet<>();
    private volatile boolean closed;
    private final Map<String,List<Account>> accountCache = lru();
    private final Map<String,List<Holding>> holdingCache = lru();
    private final Map<String,BigDecimal> rates = new HashMap<>();
    private List<Quote> quoteCache;
    private final Map<String, Order> activeOrders = new HashMap<>();
    private static <K,V> Map<K,V> lru() { return new LinkedHashMap<>(32,.75f,true) { protected boolean removeEldestEntry(Map.Entry<K,V> e) { return size()>256; } }; }
    @FunctionalInterface private interface Work<T> { T run() throws Exception; }
    /** currencyScales contains decimal digits (CAD=2, JPY=0), rather than minor-unit multipliers. */
    public FinanceDatabase(Path file, String defaultCurrency, Map<String,Integer> currencyScales, Map<String,String> cadRates, List<StockDefinition> stocks) {
        this.defaultCurrency=defaultCurrency; this.scales=Map.copyOf(currencyScales);
        if (!scales.containsKey(defaultCurrency) || !scales.containsKey("CAD")) throw new IllegalArgumentException("Missing currency scale");
        if (scales.values().stream().anyMatch(digits -> digits < 0 || digits > 9)) throw new IllegalArgumentException("Currency precision must be decimal digits");
        initialization=submit(() -> { if(file.toAbsolutePath().getParent()!=null) Files.createDirectories(file.toAbsolutePath().getParent());
            Class.forName("org.sqlite.JDBC");
            connection=DriverManager.getConnection("jdbc:sqlite:"+file.toAbsolutePath());
            for(String pragma: List.of("journal_mode=WAL","synchronous=FULL","foreign_keys=ON","busy_timeout=5000")) execute("PRAGMA "+pragma);
            schema();
            transaction(() -> { for(var e:cadRates.entrySet()) { validateRate(e.getKey(),e.getValue()); update("INSERT OR IGNORE INTO exchange_rates(currency,rate) VALUES(?,?)",e.getKey(),e.getValue()); }
                update("INSERT OR IGNORE INTO exchange_rates(currency,rate) VALUES('CAD','1')");
                for(var s:stocks) { if(s.baseCadMinor()<=0) throw new IllegalArgumentException("Invalid stock price"); update("INSERT OR IGNORE INTO stocks(id,name) VALUES(?,?)",s.id(),s.name()); update("INSERT OR IGNORE INTO stock_prices(stock,price) VALUES(?,?)",s.id(),s.baseCadMinor()); } return null; });
            try(var s=connection.createStatement();var r=s.executeQuery("SELECT currency,rate FROM exchange_rates")) { while(r.next()) { validateRate(r.getString(1),r.getString(2)); rates.put(r.getString(1),new BigDecimal(r.getString(2))); } } return null;
        });
    }
    private <T> CompletableFuture<T> submit(Work<T> work) { synchronized(this) { if(closed) return CompletableFuture.failedFuture(new IllegalStateException("Database closed")); var ready=initialization; return CompletableFuture.supplyAsync(() -> { try { if(ready!=null) ready.join(); if(poisoned!=null) throw new SQLException("Finance database unavailable after connection failure",poisoned); return work.run(); } catch(Exception e) { throw new CompletionException(e); } },executor); } }
    private <T> T transaction(Work<T> work) throws Exception {
        connection.setAutoCommit(false);
        transactionId = UUID.randomUUID().toString();
        changedOwners.clear();
        changedHoldings.clear();
        T value;
        try {
            value = work.run();
            connection.commit();
        } catch (Exception failure) {
            boolean rolledBack = false;
            try {
                connection.rollback();
                rolledBack = true;
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
                poison(rollbackFailure);
            }
            if (rolledBack) {
                try { connection.setAutoCommit(true); }
                catch (SQLException resetFailure) { failure.addSuppressed(resetFailure); poison(resetFailure); }
            }
            throw failure;
        }
        // Commit succeeded. Never turn a later connection cleanup failure into a rejected payment.
        changedOwners.forEach(accountCache::remove);
        changedHoldings.forEach(holdingCache::remove);
        try { connection.setAutoCommit(true); }
        catch (SQLException resetFailure) { poison(resetFailure); }
        return value;
    }
    private void poison(SQLException failure) {
        poisoned = failure;
        System.err.println("[Economy] Finance connection disabled after cleanup failure: " + failure.getMessage());
        try { connection.close(); }
        catch (SQLException closeFailure) { failure.addSuppressed(closeFailure); }
    }
    private void execute(String sql) throws SQLException { try(var s=connection.createStatement()) { s.execute(sql); } }
    private int update(String sql,Object... args) throws SQLException { try(var p=connection.prepareStatement(sql)) { bind(p,args); return p.executeUpdate(); } }
    private static void bind(PreparedStatement p,Object... args) throws SQLException { for(int i=0;i<args.length;i++) p.setObject(i+1,args[i]); }
    private void schema() throws SQLException {
        for(String sql:List.of(
            "CREATE TABLE IF NOT EXISTS metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS accounts(id TEXT PRIMARY KEY,owner TEXT NOT NULL,currency TEXT NOT NULL,kind TEXT NOT NULL,is_primary INTEGER NOT NULL,balance INTEGER NOT NULL CHECK(balance>=0))",
            "CREATE UNIQUE INDEX IF NOT EXISTS active_currency ON accounts(owner,currency,kind) WHERE kind != 'CLOSED'",
            "CREATE UNIQUE INDEX IF NOT EXISTS primary_account ON accounts(owner) WHERE is_primary=1",
            "CREATE UNIQUE INDEX IF NOT EXISTS single_trading ON accounts(owner,kind) WHERE kind='TRADING'",
            "CREATE INDEX IF NOT EXISTS owner_accounts ON accounts(owner)",
            "CREATE TABLE IF NOT EXISTS ledger(sequence INTEGER PRIMARY KEY AUTOINCREMENT,operation TEXT NOT NULL,account TEXT NOT NULL REFERENCES accounts(id),timestamp INTEGER NOT NULL,delta INTEGER NOT NULL,balance INTEGER NOT NULL,memo TEXT NOT NULL,transaction_id TEXT NOT NULL DEFAULT '')",
            "CREATE INDEX IF NOT EXISTS ledger_account_time ON ledger(account,timestamp DESC,sequence DESC)",
            "CREATE INDEX IF NOT EXISTS ledger_account_sequence ON ledger(account,sequence DESC)",
            "CREATE TRIGGER IF NOT EXISTS ledger_no_update BEFORE UPDATE ON ledger BEGIN SELECT RAISE(ABORT,'Ledger is append only'); END",
            "CREATE TRIGGER IF NOT EXISTS ledger_no_delete BEFORE DELETE ON ledger BEGIN SELECT RAISE(ABORT,'Ledger is append only'); END",
            "CREATE TABLE IF NOT EXISTS exchange_rates(currency TEXT PRIMARY KEY,rate TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS historical_rates(date TEXT NOT NULL,currency TEXT NOT NULL,rate TEXT NOT NULL,PRIMARY KEY(date,currency))",
            "CREATE TABLE IF NOT EXISTS stocks(id TEXT PRIMARY KEY,name TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS stock_prices(stock TEXT PRIMARY KEY REFERENCES stocks(id),price INTEGER NOT NULL CHECK(price>0))",
            "CREATE TABLE IF NOT EXISTS stock_price_history(sequence INTEGER PRIMARY KEY AUTOINCREMENT,stock TEXT NOT NULL REFERENCES stocks(id),price INTEGER NOT NULL CHECK(price>0),timestamp INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS holdings(owner TEXT NOT NULL,stock TEXT NOT NULL REFERENCES stocks(id),quantity INTEGER NOT NULL CHECK(quantity>=0),PRIMARY KEY(owner,stock))",
            "CREATE TABLE IF NOT EXISTS orders(id INTEGER PRIMARY KEY AUTOINCREMENT,owner TEXT NOT NULL,stock TEXT NOT NULL REFERENCES stocks(id),side TEXT NOT NULL,quantity INTEGER NOT NULL,price INTEGER NOT NULL,status TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS owner_orders ON orders(owner,id DESC)",
            "CREATE TABLE IF NOT EXISTS trades(id INTEGER PRIMARY KEY AUTOINCREMENT,order_id INTEGER NOT NULL REFERENCES orders(id),account TEXT NOT NULL REFERENCES accounts(id),quantity INTEGER NOT NULL,price INTEGER NOT NULL,timestamp INTEGER NOT NULL)")) execute(sql);
        boolean hasTransactionId = false;
        try (var statement = connection.createStatement(); var columns = statement.executeQuery("PRAGMA table_info(ledger)")) {
            while (columns.next()) {
                if (columns.getString("name").equals("transaction_id")) hasTransactionId = true;
            }
        }
        if (!hasTransactionId) execute("ALTER TABLE ledger ADD COLUMN transaction_id TEXT NOT NULL DEFAULT ''");
        execute("CREATE INDEX IF NOT EXISTS ledger_transaction ON ledger(transaction_id)");
    }
    private void currency(String code) { if(!scales.containsKey(code)) throw new IllegalArgumentException("Unknown currency: "+code); }
    private void validateRate(String code, String rate) {
        currency(code);
        if (rate.length() > 128) throw new IllegalArgumentException("Exchange rate is too long");
        BigDecimal value = new BigDecimal(rate);
        if (value.signum() <= 0) throw new IllegalArgumentException("Rate must be positive");
        if (code.equals("CAD") && value.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("CAD exchange rate must equal 1");
        }
    }
    private static String auditMemo(String memo, String detail) {
        String text = Objects.requireNonNullElse(memo, "");
        if (text.length() > 256) text = text.substring(0, 256);
        if (detail.length() > 8192) throw new IllegalArgumentException("Conversion audit exceeds supported size");
        return text + " [" + detail + "]";
    }
    private String rateDescription(String currency) {
        BigDecimal rate = rates.get(currency);
        if (rate == null) throw new IllegalArgumentException("Missing exchange rate");
        return currency + " CAD/unit=" + rate.toString();
    }
    private static Account readAccount(ResultSet r) throws SQLException { return new Account(r.getString("id"),r.getString("owner"),r.getString("currency"),r.getString("kind"),r.getBoolean("is_primary"),r.getLong("balance")); }
    private Account owned(UUID owner,String id) throws SQLException { try(var p=connection.prepareStatement("SELECT * FROM accounts WHERE id=? AND owner=? AND kind != 'CLOSED'")) { bind(p,id,owner.toString()); try(var r=p.executeQuery()) { if(!r.next()) throw new IllegalArgumentException("Account not found"); return readAccount(r); } } }
    private void ensure(UUID owner) throws SQLException { create(owner,defaultCurrency,"BANK",true); create(owner,"CAD","TRADING",false); }
    private void create(UUID owner,String currency,String kind,boolean primary) throws SQLException { changedOwners.add(owner.toString()); update("INSERT OR IGNORE INTO accounts(id,owner,currency,kind,is_primary,balance) VALUES(?,?,?,?,?,0)",UUID.randomUUID().toString(),owner.toString(),currency,kind,primary?1:0); }
    private List<Account> list(UUID owner) throws SQLException { var result=new ArrayList<Account>(); try(var p=connection.prepareStatement("SELECT * FROM accounts WHERE owner=? AND kind != 'CLOSED' ORDER BY is_primary DESC,kind,currency")) { bind(p,owner.toString()); try(var r=p.executeQuery()) { while(r.next()) result.add(readAccount(r)); } } return List.copyOf(result); }
    private Account kind(UUID owner,String kind) throws SQLException { return list(owner).stream().filter(a -> kind.equals("PRIMARY")?a.primary():a.kind().equals(kind)).findFirst().orElseThrow(); }
    public CompletableFuture<Void> ready() { return initialization; }
    public CompletableFuture<List<Account>> accounts(UUID owner) { return submit(() -> { var cached=accountCache.get(owner.toString()); if(cached!=null)return cached; transaction(() -> {ensure(owner);return null;}); var result=list(owner);accountCache.put(owner.toString(),result);return result; }); }
    public CompletableFuture<Account> account(UUID owner,String id) { return submit(() -> owned(owner,id)); }
    public CompletableFuture<Account> openAccount(UUID owner,String currency) { return submit(() -> transaction(() -> { currency(currency);ensure(owner); var all=list(owner);if(all.stream().filter(a->a.kind().equals("BANK")).count()>=3)throw new IllegalArgumentException("Maximum three bank accounts"); if(all.stream().anyMatch(a->a.kind().equals("BANK")&&a.currency().equals(currency)))throw new IllegalArgumentException("Currency account already exists");create(owner,currency,"BANK",false);return list(owner).stream().filter(a->a.kind().equals("BANK")&&a.currency().equals(currency)).findFirst().orElseThrow(); })); }
    private long convert(long amount, String from, String to) {
        if (from.equals(to)) return amount;
        BigDecimal sourceRate = rates.get(from);
        BigDecimal targetRate = rates.get(to);
        if (sourceRate == null || targetRate == null) throw new IllegalArgumentException("Missing exchange rate");
        // Round exactly once, after changing both the currency and the minor-unit scale.
        BigDecimal numerator = BigDecimal.valueOf(amount).movePointLeft(scales.get(from))
                .multiply(sourceRate).movePointRight(scales.get(to));
        return numerator.divide(targetRate, 0, RoundingMode.FLOOR).longValueExact();
    }
    private Account change(Account a,long delta,String memo,String operation) throws SQLException { changedOwners.add(a.owner()); long balance=Math.addExact(a.balance(),delta);if(balance<0)throw new IllegalArgumentException("Insufficient funds");update("UPDATE accounts SET balance=? WHERE id=?",balance,a.id());update("INSERT INTO ledger(transaction_id,operation,account,timestamp,delta,balance,memo) VALUES(?,?,?,?,?,?,?)", transactionId,operation,a.id(),System.currentTimeMillis(),delta,balance,Objects.requireNonNullElse(memo,""));return new Account(a.id(),a.owner(),a.currency(),a.kind(),a.primary(),balance); }
    private static void positive(long amount) { if(amount<=0)throw new IllegalArgumentException("Amount must be positive"); }
    public CompletableFuture<Account> credit(UUID owner,String account,long amount,String memo,String operation) { return submit(() -> transaction(() -> {positive(amount);return change(owned(owner,account),amount,memo,operation);})); }
    public CompletableFuture<Account> debit(UUID owner,String account,long amount,String memo,String operation) { return submit(() -> transaction(() -> {positive(amount);return change(owned(owner,account),Math.negateExact(amount),memo,operation);})); }
    private Account move(Account source, Account destination, long amount, String memo, String operation) throws SQLException {
        positive(amount);
        if (source.id().equals(destination.id())) throw new IllegalArgumentException("Choose different accounts");
        long credit = convert(amount, source.currency(), destination.currency());
        if (credit <= 0) throw new IllegalArgumentException("Amount rounds to zero");
        String audit = auditMemo(memo, "FX source=" + source.id() + "; destination=" + destination.id() + "; debit=" + amount + " " + source.currency() + " minor; credit="
                + credit + " " + destination.currency() + " minor; " + rateDescription(source.currency())
                + "; " + rateDescription(destination.currency()) + "; credit rounding=FLOOR");
        change(source, -amount, audit, operation);
        return change(destination, credit, audit, operation);
    }
    public CompletableFuture<Account> transfer(UUID owner,String source,String destination,long amount,String memo) { return submit(() -> transaction(() -> move(owned(owner,source),owned(owner,destination),amount,memo,"TRANSFER"))); }
    public CompletableFuture<Account> pay(UUID sender,UUID recipient,long amount,String memo) { return submit(() -> transaction(() -> {ensure(sender);ensure(recipient);return move(kind(sender,"PRIMARY"),kind(recipient,"PRIMARY"),amount,memo,"PAYMENT");})); }
    public CompletableFuture<Account> closeAccount(UUID owner, String source, String destination) {
        return submit(() -> transaction(() -> {
            changedOwners.add(owner.toString());
            Account account = owned(owner, source);
            long bankCount = list(owner).stream().filter(a -> a.kind().equals("BANK")).count();
            if (account.primary() || account.currency().equals(defaultCurrency)
                    || !account.kind().equals("BANK") || bankCount <= 1) {
                throw new IllegalArgumentException("Cannot close this account");
            }
            Account target = destination == null || destination.isBlank()
                    ? kind(owner, "PRIMARY") : owned(owner, destination);
            if (!target.kind().equals("BANK") || target.id().equals(source)) {
                throw new IllegalArgumentException("Choose another bank account");
            }
            if (account.balance() > 0) {
                if (destination == null || destination.isBlank()) {
                    throw new IllegalArgumentException("Choose a destination for the remaining balance");
                }
                target = move(account, target, account.balance(), "Account closure", "CLOSE");
            }
            // Retain the account row because immutable ledger entries reference it.
            update("UPDATE accounts SET kind='CLOSED',is_primary=0 WHERE id=?", source);
            return target;
        }));
    }
    private long withdrawalCost(long outputMinor, String outputCurrency, String accountCurrency) {
        positive(outputMinor);
        currency(outputCurrency);
        currency(accountCurrency);
        BigDecimal outputRate = rates.get(outputCurrency);
        BigDecimal sourceRate = rates.get(accountCurrency);
        if (outputRate == null || sourceRate == null) throw new IllegalArgumentException("Missing exchange rate");
        BigDecimal numerator = BigDecimal.valueOf(outputMinor).movePointLeft(scales.get(outputCurrency))
                .multiply(outputRate).movePointRight(scales.get(accountCurrency));
        return numerator.divide(sourceRate, 0, RoundingMode.CEILING).longValueExact();
    }
    private long depositValue(Map<String, Long> nativeTotals, String accountCurrency) {
        long total = 0;
        for (var entry : nativeTotals.entrySet()) {
            positive(entry.getValue());
            currency(entry.getKey());
            total = Math.addExact(total, convert(entry.getValue(), entry.getKey(), accountCurrency));
        }
        return total;
    }
    /** Previews use the same serialized rate snapshot as financial writes. */
    public CompletableFuture<Long> quoteWithdrawal(long outputMinor, String outputCurrency, String accountCurrency) {
        return submit(() -> withdrawalCost(outputMinor, outputCurrency, accountCurrency));
    }
    public CompletableFuture<Long> quoteDeposit(Map<String, Long> nativeTotals, String accountCurrency) {
        var copy = Map.copyOf(nativeTotals);
        return submit(() -> { currency(accountCurrency); return depositValue(copy, accountCurrency); });
    }
    public CompletableFuture<Account> withdrawCash(UUID owner, String account, long outputMinor,
                                                   String outputCurrency, String memo) {
        return withdrawCashInternal(owner, account, outputMinor, outputCurrency, memo, null);
    }
    /** Reject a stale preview inside the same transaction that would debit the account. */
    public CompletableFuture<Account> withdrawCash(UUID owner, String account, long outputMinor,
                                                   String outputCurrency, String memo, long expectedDebit) {
        return withdrawCashInternal(owner, account, outputMinor, outputCurrency, memo, expectedDebit);
    }
    private CompletableFuture<Account> withdrawCashInternal(UUID owner, String account, long outputMinor,
                                                             String outputCurrency, String memo, Long expectedDebit) {
        return submit(() -> transaction(() -> {
            Account source = owned(owner, account);
            long cost = withdrawalCost(outputMinor, outputCurrency, source.currency());
            if (expectedDebit != null && cost != expectedDebit) {
                throw new IllegalArgumentException("Exchange rate changed; review withdrawal");
            }
            String audit = auditMemo(memo, "Cash debit=" + cost + " " + source.currency()
                    + " minor; output=" + outputMinor + " " + outputCurrency + " minor; "
                    + rateDescription(source.currency()) + "; " + rateDescription(outputCurrency)
                    + "; source rounding=CEILING");
            return change(source, Math.negateExact(cost), audit, "CASH_WITHDRAWAL");
        }));
    }
    public CompletableFuture<Account> depositCash(UUID owner, String account, Map<String, Long> nativeTotals, String memo) {
        var copy = Map.copyOf(nativeTotals);
        return submit(() -> transaction(() -> {
            Account target = owned(owner, account);
            long total = depositValue(copy, target.currency());
            positive(total);
            StringBuilder detail = new StringBuilder("Cash deposit credit=").append(total).append(' ')
                    .append(target.currency()).append(" minor; ").append(rateDescription(target.currency()));
            for (var entry : new TreeMap<>(copy).entrySet()) {
                detail.append("; native=").append(entry.getValue()).append(' ').append(entry.getKey())
                        .append(" minor; ").append(rateDescription(entry.getKey()));
            }
            detail.append("; each currency rounding=FLOOR");
            return change(target, total, auditMemo(memo, detail.toString()), "CASH_DEPOSIT");
        }));
    }
    public CompletableFuture<Void> updateStockPrice(String stock, long price) {
        return submit(() -> {
            positive(price);
            transaction(() -> {
                if (update("UPDATE stock_prices SET price=? WHERE stock=?", price, stock) != 1) {
                    throw new IllegalArgumentException("Unknown stock");
                }
                update("INSERT INTO stock_price_history(stock,price,timestamp) VALUES(?,?,?)", stock, price, System.currentTimeMillis());
                return null;
            });
            quoteCache = null;
            return null;
        });
    }
    public CompletableFuture<Map<String,String>> historicalRates(String date) {
        return submit(() -> {
            var result = new TreeMap<String,String>();
            try (var query = connection.prepareStatement("SELECT currency,rate FROM historical_rates WHERE date=?")) {
                bind(query, date);
                try (var rows = query.executeQuery()) {
                    while (rows.next()) result.put(rows.getString(1), rows.getString(2));
                }
            }
            return Map.copyOf(result);
        });
    }
    public CompletableFuture<List<Entry>> history(UUID owner,String account,long beforeSequence,int limit) { return submit(() -> {owned(owner,account);var result=new ArrayList<Entry>();try(var p=connection.prepareStatement("SELECT * FROM ledger WHERE account=? AND sequence<? ORDER BY sequence DESC LIMIT ?")) {bind(p,account,beforeSequence<=0?Long.MAX_VALUE:beforeSequence,Math.max(1,Math.min(100,limit)));try(var r=p.executeQuery()){while(r.next())result.add(new Entry(r.getLong("sequence"),r.getString("operation"),account,r.getLong("timestamp"),r.getLong("delta"),r.getLong("balance"),r.getString("memo"),r.getString("transaction_id")));}}return List.copyOf(result);}); }
    public CompletableFuture<List<Order>> orders(UUID owner,long beforeId,int limit) { return submit(() -> {
        var result=new ArrayList<Order>();try(var p=connection.prepareStatement("SELECT * FROM orders WHERE owner=? AND id<? ORDER BY id DESC LIMIT ?")) {bind(p,owner.toString(),beforeId<=0?Long.MAX_VALUE:beforeId,Math.max(1,Math.min(100,limit)));try(var r=p.executeQuery()){while(r.next())result.add(new Order(r.getLong("id"),r.getString("owner"),r.getString("stock"),r.getString("side"),r.getLong("quantity"),r.getLong("price"),r.getString("status")));}}return List.copyOf(result);
    }); }
    public CompletableFuture<Map<String,String>> exchangeRates() { return submit(() -> {var result=new TreeMap<String,String>();rates.forEach((k,v)->result.put(k,v.toPlainString()));return Map.copyOf(result);}); }
    public CompletableFuture<Long> convertAmount(long amount,String from,String to) { return submit(() -> {positive(amount);currency(from);currency(to);return convert(amount,from,to);}); }
    public CompletableFuture<List<Quote>> quotes() {return submit(this::quoteList);}
    private List<Quote> quoteList() throws SQLException {if(quoteCache==null){var list=new ArrayList<Quote>();try(var s=connection.createStatement();var r=s.executeQuery("SELECT s.id,s.name,p.price FROM stocks s JOIN stock_prices p ON s.id=p.stock ORDER BY s.id")){while(r.next())list.add(new Quote(r.getString(1),r.getString(2),r.getLong(3)));}quoteCache=List.copyOf(list);}return quoteCache;}
    private long quantity(UUID owner,String stock) throws SQLException {try(var p=connection.prepareStatement("SELECT quantity FROM holdings WHERE owner=? AND stock=?")){bind(p,owner.toString(),stock);try(var r=p.executeQuery()){return r.next()?r.getLong(1):0;}}}
    public CompletableFuture<List<Holding>> holdings(UUID owner) {return submit(() -> {var cached=holdingCache.get(owner.toString());if(cached!=null)return cached;var list=new ArrayList<Holding>();try(var p=connection.prepareStatement("SELECT stock,quantity FROM holdings WHERE owner=? AND quantity>0 ORDER BY stock")){bind(p,owner.toString());try(var r=p.executeQuery()){while(r.next())list.add(new Holding(r.getString(1),r.getLong(2)));}}var result=List.copyOf(list);holdingCache.put(owner.toString(),result);return result;});}
    public CompletableFuture<Order> trade(UUID owner, String stock, boolean buy, long quantity) {
        return submit(() -> {
            String request = UUID.randomUUID().toString();
            try {
                Order settled = transaction(() -> {
                    positive(quantity);
                    ensure(owner);
                    changedHoldings.add(owner.toString());
                    Quote quote = quoteList().stream().filter(q -> q.id().equals(stock)).findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Unknown stock"));
                    long total = Math.multiplyExact(quantity, quote.price());
                    long previous = quantity(owner, stock);
                    long next = buy ? Math.addExact(previous, quantity) : Math.subtractExact(previous, quantity);
                    if (next < 0) throw new IllegalArgumentException("Insufficient shares");
                    Account account = kind(owner, "TRADING");
                    update("INSERT INTO orders(owner,stock,side,quantity,price,status) VALUES(?,?,?,?,?,'ACTIVE')",
                            owner.toString(), stock, buy ? "BUY" : "SELL", quantity, quote.price());
                    long id;
                    try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT last_insert_rowid()")) {
                        rows.next();
                        id = rows.getLong(1);
                    }
                    activeOrders.put(request, new Order(id, owner.toString(), stock, buy ? "BUY" : "SELL", quantity, quote.price(), "ACTIVE"));
                    change(account, buy ? -total : total, (buy ? "BUY" : "SELL") + " order=" + id + " owner=" + owner + " stock=" + stock
                            + " quantity=" + quantity + " price=" + quote.price() + " CAD minor total=" + total, "TRADE");
                    update("INSERT INTO holdings(owner,stock,quantity) VALUES(?,?,?) ON CONFLICT(owner,stock) DO UPDATE SET quantity=excluded.quantity",
                            owner.toString(), stock, next);
                    update("INSERT INTO trades(order_id,account,quantity,price,timestamp) VALUES(?,?,?,?,?)",
                            id, account.id(), quantity, quote.price(), System.currentTimeMillis());
                    update("UPDATE orders SET status='SETTLED' WHERE id=?", id);
                    return new Order(id, owner.toString(), stock, buy ? "BUY" : "SELL", quantity, quote.price(), "SETTLED");
                });
                activeOrders.remove(request); // Settlement is visible only after commit.
                return settled;
            } catch (Exception failure) {
                activeOrders.remove(request);
                throw failure;
            }
        });
    }
    public CompletableFuture<Void> updateRates(Map<String,String> newRates,String date) {var copy=Map.copyOf(newRates);return submit(() -> {for(var e:copy.entrySet())validateRate(e.getKey(),e.getValue());transaction(() -> {for(var e:copy.entrySet()){update("INSERT INTO exchange_rates(currency,rate) VALUES(?,?) ON CONFLICT(currency) DO UPDATE SET rate=excluded.rate",e.getKey(),e.getValue());update("INSERT INTO historical_rates(date,currency,rate) VALUES(?,?,?) ON CONFLICT(date,currency) DO UPDATE SET rate=excluded.rate",date,e.getKey(),e.getValue());}return null;});copy.forEach((k,v)->rates.put(k,new BigDecimal(v)));return null;});}
    private static long legacyDelta(LegacyEntry entry) {
        String type = entry.type().toUpperCase(Locale.ROOT);
        long magnitude = entry.amount() == Long.MIN_VALUE ? Math.negateExact(entry.amount()) : Math.abs(entry.amount());
        boolean debit = type.contains("WITHDRAW") || type.contains("DEBIT") || type.contains("SEND")
                || type.contains("PAYMENT_SENT") || type.contains("TRANSFER_OUT");
        return debit ? Math.negateExact(magnitude) : magnitude;
    }
    public CompletableFuture<Void> evict(UUID owner) { return submit(() -> {accountCache.remove(owner.toString());holdingCache.remove(owner.toString());return null;}); }
    public CompletableFuture<Void> importLegacy(Map<UUID,Long> balances) { return importLegacy(balances,Map.of()); }
    public CompletableFuture<Void> importLegacy(Map<UUID,Long> balances, Map<UUID,List<LegacyEntry>> histories) {
        return importLegacy(balances, histories, defaultCurrency);
    }
    public CompletableFuture<Void> importLegacy(Map<UUID,Long> balances, Map<UUID,List<LegacyEntry>> histories, String legacyCurrency) {
        var copy=Map.copyOf(balances);var events=new HashMap<UUID,List<LegacyEntry>>();histories.forEach((k,v)->events.put(k,List.copyOf(v)));
        return submit(() -> transaction(() -> {
            try(var s=connection.createStatement();var r=s.executeQuery("SELECT 1 FROM metadata WHERE key='legacy_import_v1'")){if(r.next())return null;}
            currency(legacyCurrency);
            var owners=new HashSet<>(copy.keySet());owners.addAll(events.keySet());
            for(var owner:owners){long balance=copy.getOrDefault(owner,0L);if(balance<0)throw new IllegalArgumentException("Negative legacy balance");ensure(owner);var account=kind(owner,"PRIMARY");
                if (list(owner).stream().anyMatch(a -> a.balance() != 0)) {
                    throw new IllegalArgumentException("Cannot import legacy balances into funded accounts");
                }
                try (var query = connection.prepareStatement("SELECT 1 FROM ledger WHERE account IN (SELECT id FROM accounts WHERE owner=?) LIMIT 1")) {
                    bind(query, owner.toString());
                    try (var rows = query.executeQuery()) {
                        if (rows.next()) throw new IllegalArgumentException("Cannot import legacy balances into accounts with existing history");
                    }
                }
                if (!account.currency().equals(legacyCurrency)) {
                    if (list(owner).stream().anyMatch(a -> a.kind().equals("BANK") && a.currency().equals(legacyCurrency))) {
                        throw new IllegalArgumentException("Legacy currency conflicts with an existing bank account");
                    }
                    update("UPDATE accounts SET currency=? WHERE id=?", legacyCurrency, account.id());
                    account = owned(owner, account.id());
                }
                update("UPDATE accounts SET balance=? WHERE id=?",balance,account.id());
                var entries=new ArrayList<>(events.getOrDefault(owner,List.of()));entries.sort(Comparator.comparingLong(LegacyEntry::timestamp));
                if (!entries.isEmpty()) {
                    LegacyEntry first = entries.getFirst();
                    long opening = Math.subtractExact(first.balance(), legacyDelta(first));
                    if (opening < 0) throw new IllegalArgumentException("Invalid legacy opening balance");
                    if (opening > 0) {
                        update("INSERT INTO ledger(transaction_id,operation,account,timestamp,delta,balance,memo) VALUES(?,?,?,?,?,?,?)", transactionId,
                                "MIGRATION_OPENING", account.id(), first.timestamp(), opening, opening,
                                "Opening balance before retained legacy history");
                    }
                    long previous = opening;
                    for (LegacyEntry event : entries) {
                        if (event.balance() < 0) throw new IllegalArgumentException("Negative legacy entry balance");
                        long before = Math.subtractExact(event.balance(), legacyDelta(event));
                        if (before < 0) throw new IllegalArgumentException("Invalid legacy entry balance");
                        if (before != previous) {
                            update("INSERT INTO ledger(transaction_id,operation,account,timestamp,delta,balance,memo) VALUES(?,?,?,?,?,?,?)", transactionId,
                                    "MIGRATION_HISTORY_GAP", account.id(), event.timestamp(), Math.subtractExact(before, previous),
                                    before, "Reconcile omitted events between retained legacy history entries");
                        }
                        update("INSERT INTO ledger(transaction_id,operation,account,timestamp,delta,balance,memo) VALUES(?,?,?,?,?,?,?)", transactionId,
                                "LEGACY:" + event.id() + ":" + account.id() + ":" + event.type(), account.id(),
                                event.timestamp(), legacyDelta(event), event.balance(), Objects.requireNonNullElse(event.memo(), ""));
                        previous = event.balance();
                    }
                    long retainedBalance = entries.getLast().balance();
                    if (retainedBalance != balance) {
                        update("INSERT INTO ledger(transaction_id,operation,account,timestamp,delta,balance,memo) VALUES(?,?,?,?,?,?,?)", transactionId,
                                "MIGRATION_RECONCILIATION", account.id(), entries.getLast().timestamp(),
                                Math.subtractExact(balance, retainedBalance), balance,
                                "Reconcile retained legacy history to authoritative saved balance");
                    }
                }
                if(entries.isEmpty()&&balance>0)update("INSERT INTO ledger(transaction_id,operation,account,timestamp,delta,balance,memo) VALUES(?,?,?,?,?,?,?)", transactionId,"MIGRATION",account.id(),System.currentTimeMillis(),balance,balance,"Legacy balance import");
            }update("INSERT INTO metadata(key,value) VALUES('legacy_import_v1','complete')");return null;
        }));
    }
    /** Blocking drain for shutdown only; never call from a server tick or this writer thread. */
    @Override public void close() {synchronized(this){if(closed)return;closed=true;executor.execute(()->{try{if(connection!=null)connection.close();}catch(SQLException e){throw new CompletionException(e);}});executor.shutdown();}try{if(!executor.awaitTermination(30,TimeUnit.SECONDS))throw new IllegalStateException("Database close timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
}
