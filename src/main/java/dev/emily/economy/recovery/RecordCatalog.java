package dev.emily.economy.recovery;

import dev.emily.economy.account.AccountCatalog;
import dev.emily.economy.account.AccountLifecycle;
import dev.emily.economy.clock.EconomicRecord;
import dev.emily.economy.foundation.InspectionAudit;
import dev.emily.economy.fx.FxFacts;
import dev.emily.economy.journal.FxSettlement;
import dev.emily.economy.journal.Holds;
import dev.emily.economy.journal.Custody;
import dev.emily.economy.journal.CashIssuance;
import dev.emily.economy.journal.TerminalPolicy;
import dev.emily.economy.journal.Deposits;
import dev.emily.economy.journal.Withdrawals;
import dev.emily.economy.journal.RegisterSales;
import dev.emily.economy.journal.RegisterSaleSettlement;
import dev.emily.economy.journal.DepositReturns;
import dev.emily.economy.journal.MerchantPayments;
import dev.emily.economy.journal.RebaseSettlement;
import dev.emily.economy.storage.LedgerProjection;
import dev.emily.economy.storage.StorageException;
import java.util.*;
import java.util.function.Consumer;

/** Explicit historical decoders. Missing types/versions fail closed; never silently skip facts. */
public final class RecordCatalog {
    public record Key(String type, int version) {}
    private final Map<Key, Consumer<String>> validators;
    public RecordCatalog(Map<Key, Consumer<String>> validators) { this.validators = Map.copyOf(validators); }
    public static RecordCatalog core() {
        var catalog = new RecordCatalog(Map.of(new Key(EconomicRecord.TYPE, 1), EconomicRecord::decode));
        catalog = dev.emily.economy.clock.DeadlineDispatcher.register(InspectionAudit.register(FxFacts.register(catalog)));
        catalog = dev.emily.economy.pricing.ValuationSources.register(dev.emily.economy.pricing.ServicePricing.register(dev.emily.economy.interest.DepositYield.register(catalog)));
        catalog=dev.emily.economy.autopay.Autopay.register(catalog);
        catalog=dev.emily.economy.card.IssuedCards.register(catalog);
        catalog=dev.emily.economy.credit.CreditProfiles.register(catalog);
        catalog=dev.emily.economy.collection.CollectionPresence.register(catalog);
        catalog=dev.emily.economy.credit.CreditReporting.register(catalog);
        catalog=dev.emily.economy.debt.DebtLegacyReconciliation.register(catalog);
        catalog=dev.emily.economy.debt.DebtPayments.register(dev.emily.economy.debt.DebtLifecycle.register(catalog));
        catalog=dev.emily.economy.loan.Loans.register(dev.emily.economy.loan.LoanTreasury.register(dev.emily.economy.loan.LoanDisbursementFx.register(dev.emily.economy.loan.LoanRepaymentFx.register(catalog))));
        catalog=dev.emily.economy.credit.CreditAuditFacts.register(catalog);
        catalog=dev.emily.economy.credit.CreditRepaymentFx.register(dev.emily.economy.foundation.CreditReplacementPricing.register(catalog));
        catalog=dev.emily.economy.credit.CreditTreasury.register(dev.emily.economy.credit.CreditFacilities.register(catalog));
        catalog=catalog.with(new Key(dev.emily.economy.foundation.CardReplacementPricing.FACT,1),dev.emily.economy.foundation.CardReplacementPricing::validateFact);
        return dev.emily.economy.institution.InstitutionCapital.register(dev.emily.economy.institution.BankProviders.register(AccountLifecycle.register(AccountCatalog.register(catalog))))
                .with(new Key("economy:posting", 1), LedgerProjection::validatePosting)
                .with(new Key("economy:posting", 2), LedgerProjection::validatePostingV2)
                .with(new Key("economy:posting", 3), LedgerProjection::validatePostingV3)
                .with(new Key(Holds.FACT, 1), Holds::validateFact)
                .with(new Key(Holds.FACT, 2), Holds::validateFactV2)
                .with(new Key(Custody.FACT, 1), Custody::validateFact)
                .with(new Key(CashIssuance.FACT, 1), CashIssuance::validateFact)
                .with(new Key(TerminalPolicy.FACT, 1), TerminalPolicy::validateFact)
                .with(new Key(dev.emily.economy.journal.TerminalBanking.FACT, 1), dev.emily.economy.journal.TerminalBanking::validateFact)
                .with(new Key(Deposits.FACT, 1), Deposits::validateFact)
                .with(new Key(Deposits.FACT, 2), Deposits::validateFactV2)
                .with(new Key(Deposits.FACT, 3), Deposits::validateFactV3)
                .with(new Key(Withdrawals.FACT, 1), Withdrawals::validateFact)
                .with(new Key(Withdrawals.FACT, 2), Withdrawals::validateFactV2)
                .with(new Key(Withdrawals.FACT, 3), Withdrawals::validateFactV3)
                .with(new Key(RegisterSales.FACT, 1), RegisterSales::validateFact)
                .with(new Key(RegisterSaleSettlement.RETURN, 1), RegisterSaleSettlement::validateReturn)
                .with(new Key(DepositReturns.FACT, 1), DepositReturns::validateFact)
                .with(new Key(MerchantPayments.FACT, 1), MerchantPayments::validateFact)
                .with(new Key(FxSettlement.FACT, 1), FxSettlement::validateFact)
                .with(new Key(FxSettlement.FACT, 2), FxSettlement::validateFactV2)
                .with(new Key(FxSettlement.FACT, 3), FxSettlement::validateFactV3)
                .with(new Key(RebaseSettlement.FACT, 1), RebaseSettlement::validateFact)
                .with(new Key(RebaseSettlement.MAPPING, 1), RebaseSettlement::validateMapping);
    }
    public RecordCatalog with(Key key, Consumer<String> validator) {
        var copy = new HashMap<>(validators); if (copy.putIfAbsent(key, validator) != null) throw new IllegalArgumentException("Duplicate decoder");
        return new RecordCatalog(copy);
    }
    public void validate(String type, int version, String payload) {
        var decoder = validators.get(new Key(type, version));
        if (decoder == null) throw new StorageException(StorageException.Kind.SCHEMA, "Unknown committed record: " + type + " v" + version);
        try { decoder.accept(payload); }
        catch (RuntimeException e) { throw new StorageException(StorageException.Kind.CORRUPTION_IO, "Invalid committed record: " + type, e); }
    }
}
