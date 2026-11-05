package dev.emily.economy.banking;

import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.journal.WithdrawalPlanner;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BankingBoundaryTest {
    private static final UUID SESSION = UUID.randomUUID();

    @Test void issuanceAndReplacementRequireKioskButLostReportingAndClaimsRemainOwnerActions() {
        for(var access:List.of(BankingMenu.Access.ATM,BankingMenu.Access.TELLER,BankingMenu.Access.CARD)) {
            assertTrue(access.allows("cards"));assertTrue(access.allows("card_lost"));
            assertFalse(access.allows("card_preview"));assertFalse(access.allows("card_confirm"));
        }
        assertTrue(BankingMenu.Access.KIOSK.allows("card_preview"));
        assertTrue(BankingMenu.Access.KIOSK.allows("card_confirm"));
        assertTrue(BankingMenu.Access.KIOSK.allows("collect"));
        assertFalse(BankingMenu.Access.ADMIN.allows("card_confirm"));
        assertFalse(BankingMenu.Access.REGISTER.allows("card_lost"));
        assertFalse(BankingMenu.Access.CARD.allows("credit_apply"));
    }

    @Test void creditProfileIsReadOnlyAndDoesNotExposeScoringOrUnderwritingMutations() {
        for(var access:List.of(BankingMenu.Access.ATM,BankingMenu.Access.TELLER,BankingMenu.Access.KIOSK,BankingMenu.Access.CARD)) {
            assertTrue(access.allows("credit_profile"));assertTrue(access.allows("credit_history"));assertTrue(access.allows("credit_next"));
            assertFalse(access.allows("score_recalculate"));assertFalse(access.allows("credit_establish"));assertFalse(access.allows("loan_apply"));
        }
        assertFalse(BankingMenu.Access.REGISTER.allows("credit_profile"));
        assertFalse(BankingMenu.Access.ADMIN.allows("credit_history"));
        assertThrows(IllegalArgumentException.class,()->new BankingNetwork.Action(1,SESSION,0,"score_recalculate",""));
    }

    @Test void creditFacilityDiagnosticsRemainOwnerReadActionsOnBankingSurfaces() {
        var reads=List.of("credit_facilities","credit_facilities_next","credit_facility","credit_statements","credit_statements_next");
        for(var action:reads) {
            assertTrue(BankingNetwork.ACTIONS.contains(action));
            assertDoesNotThrow(()->new BankingNetwork.Action(1,SESSION,0,action,""));
            for(var access:List.of(BankingMenu.Access.ATM,BankingMenu.Access.TELLER,BankingMenu.Access.KIOSK,BankingMenu.Access.CARD))assertTrue(access.allows(action),access+" "+action);
            for(var access:List.of(BankingMenu.Access.REGISTER,BankingMenu.Access.ADMIN,BankingMenu.Access.WORLD_SETUP))assertFalse(access.allows(action),access+" "+action);
        }
        for(var mutation:List.of("credit_limit_edit","credit_balance_edit","credit_recalculate","credit_draw","credit_repay"))assertFalse(BankingNetwork.ACTIONS.contains(mutation));
    }

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

    @Test void terminalModesExposeOnlyTheirOwnServerAuthorizedActions() {
        assertTrue(BankingMenu.Access.ATM.allows("withdraw_confirm"));
        assertTrue(BankingMenu.Access.ATM.allows("home"));
        assertTrue(BankingMenu.Access.TELLER.allows("manage_accounts"));
        assertTrue(BankingMenu.Access.CARD.allows("manage_accounts"));
        assertFalse(BankingMenu.Access.REGISTER.allows("manage_accounts"));
        assertFalse(BankingMenu.Access.WORLD_SETUP.allows("home"));
        assertFalse(BankingMenu.Access.TELLER.allows("account_close_preview"));
        assertFalse(BankingMenu.Access.TELLER.allows("account_close_confirm"));
        assertFalse(BankingMenu.Access.ATM.allows("account_close_confirm"));
        assertFalse(BankingMenu.Access.CARD.allows("account_close_confirm"));
        assertFalse(BankingMenu.Access.REGISTER.allows("account_close_confirm"));
        assertFalse(BankingMenu.Access.ATM.allows("shop"));
        assertFalse(BankingMenu.Access.TELLER.allows("settings"));
        assertTrue(BankingMenu.Access.REGISTER.allows("buy_confirm"));
        assertFalse(BankingMenu.Access.REGISTER.allows("deposit"));
        assertTrue(BankingMenu.Access.ADMIN.allows("configure"));
        assertTrue(BankingMenu.Access.ADMIN.allows("banking"));
        assertFalse(BankingMenu.Access.CARD.allows("configure"));
        assertFalse(BankingMenu.Access.ADMIN.allows("withdraw"));
        assertFalse(BankingMenu.Access.CARD.allows("deposit"));
    }

    @Test void branchLifecycleAndBrandingStayInTheirSeparateServerModes() {
        var lifecycle = List.of("branch_open", "branch_assign", "branch_switch", "branch_accept", "branch_close");
        for (String action : lifecycle) {
            assertTrue(BankingMenu.Access.KIOSK.allows(action), action);
            for (var access : List.of(BankingMenu.Access.ATM, BankingMenu.Access.TELLER, BankingMenu.Access.CARD,
                    BankingMenu.Access.REGISTER, BankingMenu.Access.ADMIN, BankingMenu.Access.WORLD_SETUP))
                assertFalse(access.allows(action), access + " " + action);
        }
        for (String action : List.of("brand_preview", "brand_confirm")) {
            assertTrue(BankingMenu.Access.ADMIN.allows(action), action);
            for (var access : BankingMenu.Access.values()) if (access != BankingMenu.Access.ADMIN)
                assertFalse(access.allows(action), access + " " + action);
        }
        for (String action : List.of("deposit", "deposit_confirm", "withdraw", "withdraw_confirm", "fx", "exchange_confirm",
                "configure", "account_open", "account_close_preview"))
            assertFalse(BankingMenu.Access.KIOSK.allows(action), action);
        for (String action : List.of("account_open", "account_close_preview", "account_close_confirm"))
            for (var access : List.of(BankingMenu.Access.ATM, BankingMenu.Access.TELLER, BankingMenu.Access.CARD))
                assertFalse(access.allows(action), access + " " + action);
        assertTrue(BankingMenu.Access.KIOSK.allows("account_close_confirm"));
        assertTrue(BankingMenu.Access.KIOSK.allows("branch_products"));
    }

    @Test void unsupportedProductsHaveNoFakeMutationPacketOrAccessGrant() {
        for (String action : List.of("card_replace", "credit_apply", "loan_apply", "subprime_apply", "issue_card")) {
            assertFalse(BankingNetwork.ACTIONS.contains(action), action);
            assertThrows(IllegalArgumentException.class, () -> new BankingNetwork.Action(1, SESSION, 0, action, ""));
            for (var access : BankingMenu.Access.values()) assertFalse(access.allows(action), access + " " + action);
        }
    }

    @Test void placerCanConfigureWithoutOperatorPrivileges() {
        UUID owner = UUID.randomUUID(), stranger = UUID.randomUUID();
        assertTrue(BankingController.mayConfigure(owner, owner, false));
        assertFalse(BankingController.mayConfigure(owner, stranger, false));
        assertTrue(BankingController.mayConfigure(owner, stranger, true));
        assertFalse(BankingController.mayConfigure(null, stranger, false));
    }

    @Test void normalPresentationHidesInternalIdentifiersAndProtocols() {
        var id = UUID.randomUUID();
        assertEquals(List.of("1. CAD primary — open", "History for CAD", "12.50 CAD", "1. Register purchase"), BankingPresentation.lines(List.of(
                "1. CAD primary — open", "History for CAD " + id, "#42 12.50 CAD", "Transaction " + id,
                "1. " + id + " — register-goods-v1")));
        assertEquals("Outcome needs checking. Use Retry.", BankingPresentation.status("UNKNOWN — retry preserves operation " + id));
        assertEquals("Register configured.", BankingPresentation.status("REGISTER_CONFIGURED"));
    }

    @Test void transactionHistoryHeadingSurvivesIdentifierFiltering() {
        assertEquals(List.of("Transaction History", "Balance: 50.00 CAD", "12.50 CAD"), BankingPresentation.lines(
                List.of("Transaction History", "Balance: 50.00 CAD", "#42 12.50 CAD", "Transaction " + UUID.randomUUID())));
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
        String many = String.join(",", IntStream.range(0, 132).mapToObj(index -> "economy:bill_" + index + "=1").toList());
        assertThrows(IllegalArgumentException.class, () -> BankingWithdrawals.parse("CAD 60;" + many));
    }

    @Test void offeredControlsAreBoundedAndFrozenWithTheirSnapshot() {
        var choice = new BankingNetwork.Choice("account_select", UUID.randomUUID() + "|history", "CAD primary account", true);
        var offered = new ArrayList<>(List.of(choice));
        var snapshot = new BankingNetwork.Snapshot(1, SESSION, 1, "accounts", List.of(), "Ready", false, offered);
        offered.clear();
        assertEquals(List.of(choice), snapshot.choices());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.choices().clear());
        assertThrows(IllegalArgumentException.class, () -> new BankingNetwork.Choice("issue_cash", "", "Forged", false));
        assertThrows(IllegalArgumentException.class, () -> new BankingNetwork.Choice("collect", "x".repeat(129), "Items", false));
        assertThrows(IllegalArgumentException.class, () -> new BankingNetwork.Snapshot(1, SESSION, 1, "accounts", List.of(),
                "Ready", false, Collections.nCopies(33, choice)));
        assertDoesNotThrow(() -> new BankingNetwork.Choice("destination", UUID.randomUUID().toString(), "EUR account", false));
    }

    private static BankingNetwork.Snapshot snapshot(String page, List<String> lines, String status) {
        return new BankingNetwork.Snapshot(1, SESSION, 1, page, lines, status, false);
    }
}
