package dev.emily.economy.banking;

import java.util.UUID;

/** Standalone assertions: run with java -ea; no test framework required. */
public final class LedgerTest {
    public static void main(String[] args) {
        Ledger ledger = new Ledger();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        ledger.credit(a, 500, "start");
        ledger.transfer(a, b, 120, "payment");
        assert ledger.balance(a) == 380 && ledger.balance(b) == 120;
        assert ledger.history(a).getLast().id().equals(ledger.history(b).getLast().id());
        expect(IllegalStateException.class, () -> ledger.debit(a, 381, "too much"));
        assert ledger.balance(a) == 380;
        ledger.credit(b, Long.MAX_VALUE - 120, "limit");
        int historySize = ledger.history(a).size();
        expect(IllegalArgumentException.class, () -> ledger.transfer(a, b, 1, "overflow"));
        assert ledger.balance(a) == 380 && ledger.balance(b) == Long.MAX_VALUE;
        assert ledger.history(a).size() == historySize;
        expect(IllegalArgumentException.class, () -> ledger.credit(a, 0, "zero"));
        expect(IllegalArgumentException.class, () -> ledger.transfer(a, a, 1, "self"));
        for (int i = 0; i < 110; i++) ledger.credit(a, 1, "entry " + i);
        assert ledger.history(a).size() == 100;
        assert ledger.history(a).getFirst().memo().equals("entry 10");
        expect(UnsupportedOperationException.class, () -> ledger.history(a).clear());
        System.out.println("Ledger tests passed");
    }
    private static void expect(Class<? extends Throwable> type, Runnable operation) {
        try { operation.run(); }
        catch (Throwable error) { if (type.isInstance(error)) return; throw new AssertionError(error); }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
