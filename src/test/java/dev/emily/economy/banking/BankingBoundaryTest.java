package dev.emily.economy.banking;

import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.journal.WithdrawalPlanner;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BankingBoundaryTest {
    private static final UUID SESSION = UUID.randomUUID();

    @Test void actionBoundsCountUtf8BytesAndRejectForgedIdentityFields() {
        assertDoesNotThrow(() -> new BankingNetwork.Action(1, SESSION, 0, "withdraw_preview", "é".repeat(1024)));
        assertThrows(IllegalArgumentException.class,
                () -> new BankingNetwork.Action(1, SESSION, 0, "withdraw_preview", "é".repeat(1025)));
        assertThrows(IllegalArgumentException.class,
                () -> new BankingNetwork.Action(1, SESSION, 0, "deposit_confirm", "😀".repeat(513)));
        assertThrows(IllegalArgumentException.class,
                () -> new BankingNetwork.Action(1, SESSION, 0, "issue_cash", "CAD 100"));
        assertThrows(IllegalArgumentException.class,
                () -> new BankingNetwork.Action(-1, SESSION, 0, "accounts", ""));
        assertThrows(IllegalArgumentException.class,
                () -> new BankingNetwork.Action(101, SESSION, 0, "accounts", ""));
        assertThrows(IllegalArgumentException.class,
                () -> new BankingNetwork.Action(1, SESSION, -1, "accounts", ""));
    }

    @Test void snapshotRejectsAggregateOverflowEvenWhenEachLineFits() {
        String line = "界".repeat(250); // 250 Java characters, 750 UTF-8 bytes per valid line.
        assertDoesNotThrow(() -> snapshot("history", Collections.nCopies(16, line), ""));
        assertThrows(IllegalArgumentException.class,
                () -> snapshot("history", Collections.nCopies(17, line), ""));
        assertThrows(IllegalArgumentException.class,
                () -> snapshot("history", Collections.nCopies(33, "small"), ""));
        assertThrows(IllegalArgumentException.class,
                () -> snapshot("../history", List.of("small"), ""));
        assertThrows(IllegalArgumentException.class,
                () -> snapshot("account details", List.of("small"), ""));
        assertThrows(IllegalArgumentException.class,
                () -> snapshot("history", List.of("small"), "x".repeat(257)));
    }

    @Test void snapshotFreezesTheAuthoritativeRowsBeforePublication() {
        var rows = new ArrayList<>(List.of("original operation"));
        var snapshot = snapshot("history", rows, "Ready");
        rows.set(0, "later unrelated operation");
        assertEquals(List.of("original operation"), snapshot.lines());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.lines().add("forged row"));
    }

    @Test void withdrawalAmountMustBeExactlyRepresentableInItsCurrency() {
        assertEquals(75, BankingWithdrawals.parse("CAD 0.75").cash().minorUnits());
        assertEquals(EconomyCurrency.CAD, BankingWithdrawals.parse("cad 0.75").cash().currency());
        assertEquals(1, BankingWithdrawals.parse("JPY 1.0").cash().minorUnits());
        assertThrows(ArithmeticException.class, () -> BankingWithdrawals.parse("CAD 0.001"));
        assertThrows(ArithmeticException.class, () -> BankingWithdrawals.parse("JPY 0.5"));
        assertThrows(IllegalArgumentException.class, () -> BankingWithdrawals.parse("CAD -1"));
        assertThrows(IllegalArgumentException.class, () -> BankingWithdrawals.parse("CAD 0"));
        assertThrows(IllegalArgumentException.class, () -> BankingWithdrawals.parse("INVALID 10"));
        assertThrows(ArithmeticException.class, () -> BankingWithdrawals.parse("CAD 9223372036854775807"));
    }

    @Test void explicitEmptyEditsRemainDifferentFromAutomaticPlanning() {
        assertNull(BankingWithdrawals.parse("CAD 1").edits());
        assertEquals(Map.of(), BankingWithdrawals.parse("CAD 1;").edits());
        assertEquals(Map.of("economy:cad_bill_20", 3), BankingWithdrawals.parse("CAD 60;economy:cad_bill_20=3").edits());
        assertThrows(UnsupportedOperationException.class,
                () -> BankingWithdrawals.parse("CAD 60;economy:cad_bill_20=3").edits().put("economy:cad_bill_50", 1));
    }

    @Test void parserDoesNotGrantAuthorityToCoinOrForeignBillEdits() {
        var coinEdit = BankingWithdrawals.parse("CAD 1;economy:cad_coin_100=1");
        var coin = new WithdrawalPlanner.Denomination("economy:cad_coin_100", EconomyCurrency.CAD, 100, false);
        assertEquals(1, coinEdit.edits().get("economy:cad_coin_100"));
        assertThrows(IllegalArgumentException.class, () -> WithdrawalPlanner.edited(EconomyCurrency.CAD, 100,
                false, List.of(coin), coinEdit.edits()));
        var foreignEdit = BankingWithdrawals.parse("CAD 5;economy:eur_bill_5=1");
        var cadBill = new WithdrawalPlanner.Denomination("economy:cad_bill_5", EconomyCurrency.CAD, 500, true);
        assertThrows(IllegalArgumentException.class, () -> WithdrawalPlanner.edited(EconomyCurrency.CAD, 500,
                true, List.of(cadBill), foreignEdit.edits()));
    }

    @Test void adversarialBillEditsRejectBeforePlannerWork() {
        assertThrows(IllegalArgumentException.class, () -> BankingWithdrawals.parse("CAD 60;economy:cad_bill_20=-1"));
        assertThrows(IllegalArgumentException.class,
                () -> BankingWithdrawals.parse("CAD 60;economy:cad_bill_20=1,economy:cad_bill_20=2"));
        assertThrows(IllegalArgumentException.class, () -> BankingWithdrawals.parse("CAD 60;economy:cad_bill_20=2305"));
        assertThrows(IllegalArgumentException.class, () -> BankingWithdrawals.parse("CAD 60;economy:cad_bill_20=2147483648"));
        assertThrows(IllegalArgumentException.class, () -> BankingWithdrawals.parse("CAD 60;economy:cad_bill_20=3;extra"));
        String many = String.join(",", IntStream.range(0, 119).mapToObj(index -> "economy:bill_" + index + "=1").toList());
        assertThrows(IllegalArgumentException.class, () -> BankingWithdrawals.parse("CAD 60;" + many));
    }

    private static BankingNetwork.Snapshot snapshot(String page, List<String> lines, String status) {
        return new BankingNetwork.Snapshot(1, SESSION, 1, page, lines, status, false);
    }
}
