package dev.emily.economy.foundation;

import dev.emily.economy.api.*;
import dev.emily.economy.api.Identifiers.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static dev.emily.economy.api.EconomyCurrency.*;
import static org.junit.jupiter.api.Assertions.*;

class BankingAccountManagementTest {
    private final PartyRef owner = new PartyRef(new WorldId(UUID.randomUUID()), new PartyRef.Player(new PlayerId(UUID.randomUUID())));

    @Test void openingOffersOnlyCurrenciesWithoutAnOpenBankAccount() {
        var primary = bank(CAD, true, AccountIdentity.Status.OPEN);
        var closed = bank(USD, false, AccountIdentity.Status.CLOSED);
        var trading = new AccountIdentity(new AccountId(UUID.randomUUID()), owner, AccountIdentity.Purpose.TRADING,
                null, AccountIdentity.Status.OPEN, 1, false);
        var available = BankingAccounts.availableCurrencies(List.of(primary, closed, trading));
        assertFalse(available.contains(CAD));
        assertTrue(available.contains(USD), "A closed currency account can be reopened under a new identity");
        assertEquals(EconomyCurrency.all().size() - 1, available.size());
        assertThrows(UnsupportedOperationException.class, () -> available.add(CAD));
    }

    @Test void primaryAccountCountsTowardTheThreeAccountCap() {
        var primary = bank(CAD, true, AccountIdentity.Status.OPEN);
        var usd = bank(USD, false, AccountIdentity.Status.OPEN);
        var euro = bank(EUR, false, AccountIdentity.Status.OPEN);
        assertFalse(BankingAccounts.availableCurrencies(List.of(primary, usd)).isEmpty());
        assertTrue(BankingAccounts.availableCurrencies(List.of(primary, usd, euro)).isEmpty());
        assertTrue(BankingAccounts.availableCurrencies(List.of(primary, usd, euro, bank(GBP, false, AccountIdentity.Status.OPEN))).isEmpty());
    }

    @Test void closureControlsProtectPrimaryWorldCurrencyAndReservedFundsAndRemainReadOnlyAwayFromTellers() {
        var usd = bank(USD, false, AccountIdentity.Status.OPEN);
        var balance = new AccountFunds(USD, 123400, 0, 0);
        assertTrue(BankingAccounts.mayClose(usd, CAD, balance, true));
        assertFalse(BankingAccounts.mayClose(usd, CAD, balance, false));
        assertFalse(BankingAccounts.mayClose(usd, USD, balance, true));
        assertFalse(BankingAccounts.mayClose(bank(CAD, true, AccountIdentity.Status.OPEN), USD, new AccountFunds(CAD, 100, 0, 0), true));
        assertFalse(BankingAccounts.mayClose(bank(USD, false, AccountIdentity.Status.CLOSED), CAD, balance, true));
        assertFalse(BankingAccounts.mayClose(usd, CAD, new AccountFunds(USD, 0, 100, 0), true));
        assertFalse(BankingAccounts.mayClose(usd, CAD, new AccountFunds(USD, 0, 0, 100), true));
    }

    private AccountIdentity bank(EconomyCurrency currency, boolean primary, AccountIdentity.Status status) {
        return new AccountIdentity(new AccountId(UUID.randomUUID()), owner, AccountIdentity.Purpose.BANK, currency, status, 1, primary);
    }
}
