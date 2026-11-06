package dev.emily.economy.institution;

import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.api.Identifiers.WorldId;
import dev.emily.economy.journal.JournalEngine;
import dev.emily.economy.storage.FoundationStore;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Trusted accounting identities, deliberately separate from customer BANK accounts. */
public final class FinancialAccounts {
    private FinancialAccounts() {}
    public enum Role { CASH, RESERVATION, RECEIVABLE, LIABILITY, PAYABLE, INCOME, EXPENSE, EQUITY, ROUNDING, ALLOWANCE;
        public boolean spendable() { return this == CASH; }
    }
    public record Account(UUID id, WorldId world, InstitutionCatalog.InstitutionId owner, Role role,
                          EconomyCurrency currency) {
        public Account { Objects.requireNonNull(id); Objects.requireNonNull(world); Objects.requireNonNull(owner);
            Objects.requireNonNull(role); Objects.requireNonNull(currency);
            if (!id.equals(FinancialAccounts.id(world, owner, role, currency))) throw new IllegalArgumentException("Financial account identity");
        }
    }
    public static UUID id(WorldId world, InstitutionCatalog.InstitutionId owner, Role role, EconomyCurrency currency) {
        return UUID.nameUUIDFromBytes(("economy:financial-account:v1|" + world.value() + "|" + owner.key()
                + "|" + role.name() + "|" + currency.name()).getBytes(StandardCharsets.UTF_8));
    }
    public static Account account(WorldId world, InstitutionCatalog.InstitutionId owner, Role role, EconomyCurrency currency) {
        return new Account(id(world, owner, role, currency), world, owner, role, currency);
    }
    public static Optional<Account> find(JournalEngine.ReadView reads, UUID id) {
        var rows = reads.query("SELECT account_id,world_id,institution_key,role,currency FROM institution_financial_accounts WHERE account_id=?",
                List.of(id.toString()), 1);
        if (rows.isEmpty()) return Optional.empty();
        var row = rows.getFirst();
        return Optional.of(new Account(UUID.fromString(row.get("account_id")), new WorldId(UUID.fromString(row.get("world_id"))),
                new InstitutionCatalog.InstitutionId(row.get("institution_key")), Role.valueOf(row.get("role")),
                EconomyCurrency.valueOf(row.get("currency"))));
    }
    /** Claims, income and equity never contribute to spendable bank liquidity. */
    public static long spendableCash(JournalEngine.ReadView reads, WorldId world,
                                     InstitutionCatalog.InstitutionId owner, EconomyCurrency currency) {
        var account = account(world, owner, Role.CASH, currency);
        if (find(reads, account.id()).isEmpty()) return 0;
        long cash = dev.emily.economy.journal.Settlement.posted(reads, account.id(), currency);
        if (cash < 0) throw new IllegalStateException("Negative institution cash");
        return cash;
    }

    public static void register(FoundationStore.Transaction tx, Account account) {
        tx.execute("INSERT OR IGNORE INTO institution_financial_accounts(account_id,world_id,institution_key,role,currency) VALUES(?,?,?,?,?)",
                List.of(account.id().toString(), account.world().value().toString(), account.owner().key(), account.role().name(), account.currency().name()));
    }
}
