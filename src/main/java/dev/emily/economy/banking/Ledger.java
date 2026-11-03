package dev.emily.economy.banking;

import dev.emily.economy.api.Transaction;
import java.util.*;

/** Arithmetic and atomic validation independent of Minecraft persistence. */
final class Ledger {
    static final int HISTORY_LIMIT = 100;
    final Map<UUID, Long> balances = new HashMap<>();
    final Map<UUID, ArrayDeque<Transaction>> histories = new HashMap<>();

    long balance(UUID account) { return balances.getOrDefault(Objects.requireNonNull(account), 0L); }
    List<Transaction> history(UUID account) {
        Objects.requireNonNull(account);
        var entries = histories.get(account);
        return entries == null ? List.of() : List.copyOf(entries);
    }
    private static void validate(long amount, String memo) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (memo == null || memo.length() > 256) throw new IllegalArgumentException("Memo must be non-null and at most 256 characters");
    }
    private static long add(long balance, long amount) {
        try { return Math.addExact(balance, amount); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("Balance would overflow", e); }
    }
    void credit(UUID account, long amount, String memo) {
        validate(amount, memo);
        long result = add(balance(account), amount);
        var entry = new Transaction(UUID.randomUUID(), System.currentTimeMillis(), Transaction.Type.CREDIT,
                amount, result, Optional.empty(), memo);
        balances.put(account, result);
        append(account, entry);
    }
    void debit(UUID account, long amount, String memo) {
        validate(amount, memo);
        long previous = balance(account);
        if (previous < amount) throw new IllegalStateException("Insufficient funds");
        long result = Math.subtractExact(previous, amount);
        var entry = new Transaction(UUID.randomUUID(), System.currentTimeMillis(), Transaction.Type.DEBIT,
                amount, result, Optional.empty(), memo);
        balances.put(account, result);
        append(account, entry);
    }
    void transfer(UUID from, UUID to, long amount, String memo) {
        validate(amount, memo);
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        if (from.equals(to)) throw new IllegalArgumentException("Accounts must differ");
        long source = balance(from);
        if (source < amount) throw new IllegalStateException("Insufficient funds");
        long destination = add(balance(to), amount);
        long remaining = Math.subtractExact(source, amount);
        UUID id = UUID.randomUUID();
        long time = System.currentTimeMillis();
        var outgoing = new Transaction(id, time, Transaction.Type.TRANSFER_OUT, amount, remaining, Optional.of(to), memo);
        var incoming = new Transaction(id, time, Transaction.Type.TRANSFER_IN, amount, destination, Optional.of(from), memo);
        // Both balances and both entries have been validated before any mutation.
        balances.put(from, remaining);
        balances.put(to, destination);
        append(from, outgoing);
        append(to, incoming);
    }
    void append(UUID account, Transaction entry) {
        var entries = histories.computeIfAbsent(account, key -> new ArrayDeque<>());
        if (entries.size() == HISTORY_LIMIT) entries.removeFirst();
        entries.addLast(entry);
    }
}
