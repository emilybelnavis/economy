package dev.emily.economy.foundation;

import dev.emily.economy.api.*;
import dev.emily.economy.api.Identifiers.*;
import dev.emily.economy.institution.InstitutionCatalog;
import dev.emily.economy.pricing.ServicePricing;
import dev.emily.economy.storage.SqliteStorage;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BankingIntegrationTest {
    @TempDir Path world;
    @Test void versionedIntegrationRejectsInspectionAuthorityAndFutureTrading() throws Exception {
        var services = new ServerServices(world.toRealPath(), new SqliteStorage());
        await(services, services.readiness());
        try {
            BankingIntegration api = services.ledger();
            assertEquals(1, api.version());
            assertTrue(api.collateralCapabilities().fixed());
            assertFalse(api.collateralCapabilities().variable());
            assertFalse(api.collateralCapabilities().secured());
            var owner = new PlayerId(UUID.randomUUID());
            var party = new PartyRef(services.worldId(), new PartyRef.Player(owner));
            var grant = PartyAuthorization.admit(services.worldId(), services.sessionId(), owner, false, party, false).orElseThrow();
            var inspection = PartyAuthorization.admit(services.worldId(), services.sessionId(), new PlayerId(UUID.randomUUID()), true, party, true).orElseThrow();
            assertThrows(IllegalArgumentException.class, () -> api.prime(inspection));
            assertThrows(IllegalArgumentException.class, () -> api.valuation(inspection));
            var bank = new InstitutionCatalog.InstitutionId("economy:bank/crownstone");
            assertThrows(IllegalArgumentException.class, () -> api.feeQuote(inspection, bank, ServicePricing.Service.FX, new Money(EconomyCurrency.CAD, 100)));
            assertThrows(IllegalArgumentException.class, () -> api.feeQuote(grant, bank, ServicePricing.Service.TRADING, new Money(EconomyCurrency.CAD, 100)));
            var branch = await(services, services.ledger().branchContext(grant,
                    new dev.emily.economy.journal.TerminalPolicy.Anchor("minecraft:overworld", 0, 64, 0), null)).orElseThrow();
            assertNull(branch.terminal());
            assertNull(branch.primary());
            var valuation = await(services, api.valuation(grant)).orElseThrow();
            assertFalse(valuation.available());
            assertTrue(await(services, services.ledger().creditFacilities(grant, null, 1)).orElseThrow().isEmpty());
        } finally { assertTrue(services.stop(java.time.Duration.ofSeconds(5))); }
    }
    @Test void debitAndBranchServicesBindTheirCanonicalOwnerExactlyOnce() throws Exception {
        var services = new ServerServices(world.toRealPath(), new SqliteStorage());
        await(services, services.readiness());
        try {
            var owner = new PlayerId(UUID.randomUUID());
            var party = new PartyRef(services.worldId(), new PartyRef.Player(owner));
            var grant = PartyAuthorization.admit(services.worldId(), services.sessionId(), owner, false, party, false).orElseThrow();
            var bank = new InstitutionCatalog.InstitutionId("economy:bank/crownstone");
            var context = new dev.emily.economy.institution.BranchAdmission.Context(services.worldId(), UUID.randomUUID(), owner,
                    dev.emily.economy.institution.BranchAdmission.Action.CARD_ISSUE, bank, null,
                    new AccountId(UUID.randomUUID()), 1, 1, 0);
            var proof = dev.emily.economy.institution.BranchFixtures.proof(context);
            var request = new dev.emily.economy.card.IssuedCards.Request(proof, 0, null, null);
            var issue = assertDoesNotThrow(() -> services.ledger().issueCard(grant, new OperationId(UUID.randomUUID()),
                    new RequestId(UUID.randomUUID()), request));
            assertNotNull(await(services, issue.completion()).outcome());
            var revoke = assertDoesNotThrow(() -> services.ledger().revokeCard(grant, new OperationId(UUID.randomUUID()),
                    new RequestId(UUID.randomUUID()), UUID.randomUUID(), 1));
            assertNotNull(await(services, revoke.completion()).outcome());
            var closeContext = new dev.emily.economy.institution.BranchAdmission.Context(services.worldId(), UUID.randomUUID(), owner,
                    dev.emily.economy.institution.BranchAdmission.Action.CLOSE, bank, null,
                    context.account(), 1, 1, 0);
            var closeProof = dev.emily.economy.institution.BranchFixtures.proof(closeContext);
            var close = new dev.emily.economy.journal.FundedClosure.Instruction(
                    new dev.emily.economy.account.AccountLifecycle.Close(context.account(), 1, null, null), null);
            var closure = assertDoesNotThrow(() -> services.ledger().closeAtBranch(grant, new OperationId(UUID.randomUUID()),
                    new RequestId(UUID.randomUUID()), close, (reads, account) -> Optional.empty(), closeProof));
            assertNotNull(await(services, closure.completion()).outcome());
        } finally { assertTrue(services.stop(java.time.Duration.ofSeconds(5))); }
    }

    private static <T> T await(ServerServices services, CompletionStage<T> stage) throws Exception {
        var future = stage.toCompletableFuture();
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!future.isDone() && System.nanoTime() < end) { services.tick(); Thread.sleep(1); }
        return future.get(1, TimeUnit.SECONDS);
    }
}
