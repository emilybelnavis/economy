package ca.emilybelnavis.mcmods.economy;

/** Standalone checks for the world-currency confirmation security gate. */
public final class CurrencyConfirmationChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void rejectsNull(Runnable action) {
        try { action.run(); throw new AssertionError("Expected null rejection"); }
        catch (NullPointerException expected) {}
    }
    public static void main(String[] args) {
        var gate = new CurrencyChangeConfirmation();
        check(!gate.confirm("alice", "CAD", "USD", 0), "First request must arm");
        check(gate.confirm("alice", "CAD", "USD", 1), "Second identical request must confirm");
        check(!gate.confirm("alice", "CAD", "USD", 2), "Confirmation must be consumed");
        check(gate.confirm("alice", "CAD", "USD", 3), "A new pair must work");
        check(!gate.confirm("alice", "CAD", "EUR", 10), "Actor request must arm");
        check(!gate.confirm("bob", "CAD", "EUR", 11), "Other actor cannot consume confirmation");
        check(gate.confirm("alice", "CAD", "EUR", 12), "Other actor must not replace the original actor request");
        check(gate.confirm("bob", "CAD", "EUR", 13), "Each actor must have its own confirmation");
        check(!gate.confirm("alice", "CAD", "USD", 20), "Source-change fixture must arm");
        check(!gate.confirm("alice", "EUR", "USD", 21), "Changed source must rearm");
        check(gate.confirm("alice", "EUR", "USD", 22), "Repeated changed source must confirm");
        check(!gate.confirm("alice", "CAD", "USD", 30), "Target-change fixture must arm");
        check(!gate.confirm("alice", "CAD", "JPY", 31), "Changed target must rearm");
        check(!gate.confirm("alice", "CAD", "USD", 32), "Returning to old target must also rearm");
        check(gate.confirm("alice", "CAD", "USD", 33), "Repeated target must confirm");
        check(!gate.confirm("alice", "CAD", "USD", 100), "Expiry fixture must arm");
        check(gate.confirm("alice", "CAD", "USD", 1300), "Exact TTL boundary must confirm");
        check(!gate.confirm("alice", "CAD", "USD", 2000), "Expired fixture must arm");
        check(!gate.confirm("alice", "CAD", "USD", 3201), "Expired confirmation must rearm");
        check(gate.confirm("alice", "CAD", "USD", 3202), "Renewed expired confirmation must work");
        check(!gate.confirm("alice", "CAD", "USD", 4000), "Clear fixture must arm");
        check(!gate.confirm("bob", "CAD", "EUR", 4000), "Second clear fixture must arm");
        gate.clear();
        check(!gate.confirm("alice", "CAD", "USD", 4001), "Clear must reset first actor");
        check(!gate.confirm("bob", "CAD", "EUR", 4001), "Clear must reset all actors");
        check(!gate.confirm("alice", "CAD", "USD", 3999), "Backwards ticks must rearm");
        check(gate.confirm("alice", "CAD", "USD", 4000), "New timeline must allow a fresh confirmation");
        gate.clear();
        check(!gate.confirm("alice", "CAD", "USD", Long.MIN_VALUE), "Overflow fixture must arm");
        check(!gate.confirm("alice", "CAD", "USD", Long.MAX_VALUE), "Overflowed elapsed time must not confirm");
        rejectsNull(() -> gate.confirm(null, "CAD", "USD", 0));
        rejectsNull(() -> gate.confirm("alice", null, "USD", 0));
        rejectsNull(() -> gate.confirm("alice", "CAD", null, 0));
        System.out.println("Currency confirmation checks passed");
    }
}
