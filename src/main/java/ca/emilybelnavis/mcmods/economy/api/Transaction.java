package ca.emilybelnavis.mcmods.economy.api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** An immutable account entry. Amount is positive; balance is the balance after this entry. */
public record Transaction(UUID id, long timestampMillis, Type type, long amount,
                          long balance, Optional<UUID> counterparty, String memo) {
    public Transaction {
        Objects.requireNonNull(id);
        Objects.requireNonNull(type);
        Objects.requireNonNull(counterparty);
        Objects.requireNonNull(memo);
        if (amount <= 0 || balance < 0) throw new IllegalArgumentException("Invalid transaction amounts");
    }

    public enum Type { CREDIT, DEBIT, TRANSFER_IN, TRANSFER_OUT }
}
