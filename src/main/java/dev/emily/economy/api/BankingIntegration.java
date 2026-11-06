package dev.emily.economy.api;

import dev.emily.economy.foundation.PartyAuthorization;
import dev.emily.economy.institution.InstitutionCatalog;
import dev.emily.economy.pricing.*;
import dev.emily.economy.loan.Loans;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/** Version-one server integration contract. Grants come from authenticated server context. */
public interface BankingIntegration {
    int VERSION = 1;
    CompletionStage<Optional<PricingPolicy.Policy>> prime(PartyAuthorization.Grant grant);
    CompletionStage<Optional<WealthValuation.Snapshot>> valuation(PartyAuthorization.Grant grant);
    CompletionStage<Optional<ServicePricing.Quote>> feeQuote(PartyAuthorization.Grant grant,
            InstitutionCatalog.InstitutionId institution, ServicePricing.Service service, Money principal);
    /** Capture pricing/revisions from server context, then confirm using a sealed physical proof. */
    CompletionStage<Optional<dev.emily.economy.foundation.LedgerService.CreditDrawQuote>> quoteCreditDraw(
            PartyAuthorization.Grant grant, java.util.UUID token, dev.emily.economy.journal.TerminalPolicy.Anchor anchor,
            java.util.UUID generation, Identifiers.AccountId funding, long amount, boolean purchase);
    /** Advances credit an owned BANK account. Physical cash remains a separately confirmed A5 withdrawal. */
    dev.emily.economy.journal.JournalService.Submission drawCredit(PartyAuthorization.Grant grant,
            Identifiers.OperationId operation, Identifiers.RequestId attempt,
            dev.emily.economy.credit.CreditFacilities.DrawRequest request, dev.emily.economy.credit.CreditDrawAdmission.Proof proof);
    /** Install only as the register COMMIT composer, with its exact frozen canonical credit binding. */
    dev.emily.economy.journal.RegisterSales.SettlementComposer creditCheckout(PartyAuthorization.Grant grant,
            dev.emily.economy.credit.CreditFacilities.DrawRequest request, dev.emily.economy.credit.CreditDrawAdmission.Proof proof);
    default int version() { return VERSION; }
    default Loans.Capabilities collateralCapabilities() { return Loans.capabilities(); }
}
