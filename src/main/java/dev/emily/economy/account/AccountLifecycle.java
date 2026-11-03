package dev.emily.economy.account;

import dev.emily.economy.api.AccountIdentity;
import dev.emily.economy.api.AccountIdentity.Purpose;
import dev.emily.economy.api.AccountIdentity.Status;
import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.api.Identifiers.AccountId;
import dev.emily.economy.api.PartyRef;
import dev.emily.economy.journal.Journal;
import dev.emily.economy.journal.JournalEngine;
import dev.emily.economy.recovery.RecordCatalog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Trusted worker registrations. A5 owns player-facing teller admission. */
public final class AccountLifecycle {
    public static final Journal.Kind OPEN = new Journal.Kind("economy:accounts", "economy:open-bank");
    public static final Journal.Kind CLOSE = new Journal.Kind("economy:accounts", "economy:close-bank");
    private static final String FACT = "economy:bank-lifecycle";

    private AccountLifecycle() {}

    public record Open(EconomyCurrency currency, long expectedPrimaryRevision) {
        public Open {
            Objects.requireNonNull(currency);
            requireRevision(expectedPrimaryRevision);
        }

        public Journal.CanonicalRequest request() {
            return new Journal.CanonicalRequest(1, Map.of("currency", currency.code(),
                    "primary_revision", Long.toString(expectedPrimaryRevision)));
        }
    }

    public record Close(AccountId source, long expectedRevision, AccountId destination, Long expectedDestinationRevision) {
        public Close {
            Objects.requireNonNull(source);
            requireRevision(expectedRevision);
            if ((destination == null) != (expectedDestinationRevision == null))
                throw new IllegalArgumentException("Destination and revision must be supplied together");
            if (expectedDestinationRevision != null) requireRevision(expectedDestinationRevision);
        }

        public Journal.CanonicalRequest request() {
            var fields = new HashMap<String, String>();
            fields.put("source", source.value().toString());
            fields.put("source_revision", Long.toString(expectedRevision));
            if (destination != null) {
                fields.put("destination", destination.value().toString());
                fields.put("destination_revision", expectedDestinationRevision.toString());
            }
            return new Journal.CanonicalRequest(1, fields);
        }
    }

    /** Read the authoritative ledger and claims in the same worker snapshot. Empty means unavailable. */
    @FunctionalInterface
    public interface ClosureReader {
        Optional<ClosureState> read(JournalEngine.ReadView reads, AccountIdentity source);
    }

    public record ClosureState(long postedMinorUnits, boolean activeHold, boolean restricted, boolean unresolvedObligation) {
        public ClosureState {
            if (postedMinorUnits < 0) throw new IllegalArgumentException("Negative BANK balance");
        }
    }

    public enum Eligibility {
        EMPTY, FUNDED, ACCOUNT_UNAVAILABLE, ACCOUNT_STALE, ACCOUNT_PROTECTED, REVISION_EXHAUSTED,
        DENOMINATION_PENDING, CLOSURE_STATE_UNAVAILABLE, ACTIVE_HOLD, RESTRICTED_FUNDS, UNRESOLVED_OBLIGATION,
        DESTINATION_UNAVAILABLE, DESTINATION_STALE
    }

    public static RecordCatalog register(RecordCatalog catalog) {
        return catalog.with(new RecordCatalog.Key(FACT, 1), AccountLifecycle::validateFact);
    }

    public static JournalEngine.Registration opening(PartyRef owner, Open open) {
        return owned(owner, open.request(), (intent, reads) -> {
            if (AccountCatalog.currentDenomination(reads) == null) return reject("DENOMINATION_PENDING");
            var primary = AccountCatalog.primary(reads, owner).orElse(null);
            if (primary == null) return reject("PRIMARY_REQUIRED");
            if (primary.revision() != open.expectedPrimaryRevision()) return reject("ACCOUNT_STALE");
            var banks = AccountCatalog.openBanks(reads, owner);
            if (banks.stream().anyMatch(bank -> bank.currency() == open.currency())) return reject("BANK_CURRENCY_EXISTS");
            if (banks.size() >= AccountIdentity.MAX_OPEN_BANK_ACCOUNTS) return reject("BANK_CAP_REACHED");

            var account = new AccountIdentity(new AccountId(UUID.randomUUID()), owner, Purpose.BANK, open.currency(),
                    Status.OPEN, 1, false);
            return commit("opened", account, account.revision(), "BANK_OPENED", tx -> AccountCatalog.insert(tx, account));
        });
    }

    public static JournalEngine.Registration closing(PartyRef owner, Close close, ClosureReader reader) {
        Objects.requireNonNull(reader);
        return owned(owner, close.request(), (intent, reads) -> {
            var source = AccountCatalog.find(reads, owner, close.source()).orElse(null);
            var eligibility = eligibility(reads, owner, close, reader, source);
            if (eligibility == Eligibility.FUNDED) return reject("FUNDED_SETTLEMENT_UNAVAILABLE");
            if (eligibility != Eligibility.EMPTY) return reject(eligibility.name());

            long revision = source.revision() + 1;
            return commit("closed", source, revision, "BANK_CLOSED", tx -> {
                int changed = tx.execute("""
                        UPDATE account_catalog SET status='CLOSED',revision=?
                        WHERE account_id=? AND owner_id=? AND status='OPEN' AND revision=?
                        """, List.of(Long.toString(revision), source.id().value().toString(),
                        AccountCatalog.ownerId(owner), Long.toString(close.expectedRevision())));
                if (changed != 1) throw new IllegalStateException("Authorized closure target changed");
            });
        });
    }

    /** A4 rechecks this contract with authoritative inputs in the transaction that would settle closure. */
    public static Eligibility eligibility(JournalEngine.ReadView reads, PartyRef owner, Close close, ClosureReader reader) {
        return eligibility(reads, owner, close, reader, AccountCatalog.find(reads, owner, close.source()).orElse(null));
    }

    private static Eligibility eligibility(JournalEngine.ReadView reads, PartyRef owner, Close close,
                                           ClosureReader reader, AccountIdentity source) {
        if (!openBank(source)) return Eligibility.ACCOUNT_UNAVAILABLE;
        if (source.revision() != close.expectedRevision()) return Eligibility.ACCOUNT_STALE;
        if (source.primary()) return Eligibility.ACCOUNT_PROTECTED;
        var denomination = AccountCatalog.currentDenomination(reads);
        if (denomination == null) return Eligibility.DENOMINATION_PENDING;
        if (source.currency() == denomination) return Eligibility.ACCOUNT_PROTECTED;
        if (source.revision() == Long.MAX_VALUE) return Eligibility.REVISION_EXHAUSTED;

        var state = reader.read(reads, source).orElse(null);
        if (state == null) return Eligibility.CLOSURE_STATE_UNAVAILABLE;
        if (state.activeHold()) return Eligibility.ACTIVE_HOLD;
        if (state.restricted()) return Eligibility.RESTRICTED_FUNDS;
        if (state.unresolvedObligation()) return Eligibility.UNRESOLVED_OBLIGATION;

        if (close.destination() == null)
            return state.postedMinorUnits() == 0 ? Eligibility.EMPTY : Eligibility.DESTINATION_UNAVAILABLE;
        if (close.source().equals(close.destination())) return Eligibility.DESTINATION_UNAVAILABLE;
        var destination = AccountCatalog.find(reads, owner, close.destination()).orElse(null);
        if (!openBank(destination)) return Eligibility.DESTINATION_UNAVAILABLE;
        if (destination.revision() != close.expectedDestinationRevision()) return Eligibility.DESTINATION_STALE;
        return state.postedMinorUnits() == 0 ? Eligibility.EMPTY : Eligibility.FUNDED;
    }

    private static boolean openBank(AccountIdentity account) {
        return account != null && account.purpose() == Purpose.BANK && account.status() == Status.OPEN;
    }

    private static JournalEngine.Registration owned(PartyRef owner, Journal.CanonicalRequest request,
                                                     JournalEngine.Handler handler) {
        if (!(owner.owner() instanceof PartyRef.Player player)) throw new IllegalArgumentException("Player owner required");
        var fields = new HashMap<>(request.fields());
        fields.put("economy:owner", Journal.actorKey(player.id()));
        return new JournalEngine.Registration(request.version(), (intent, reads) -> {
            if (!player.id().equals(intent.actor()) || !fields.equals(intent.request().fields()))
                throw new IllegalArgumentException("Mismatched trusted bank lifecycle request");
            return handler.prepare(intent, reads);
        });
    }

    private static JournalEngine.Reject reject(String code) {
        return new JournalEngine.Reject(new Journal.Result(1, code, ""));
    }

    private static JournalEngine.Commit commit(String action, AccountIdentity account, long revision, String code,
                                               JournalEngine.Projection projection) {
        String id = account.id().value().toString();
        String payload = String.join("|", action, id, AccountCatalog.ownerId(account.owner()),
                account.currency().code(), Long.toString(revision));
        return new JournalEngine.Commit(List.of(new Journal.Record(FACT, 1, payload)), new Journal.Result(1, code, id),
                projection);
    }

    private static void validateFact(String payload) {
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 5) throw new IllegalArgumentException("Bank lifecycle fact");
        AccountCatalog.requireCanonicalUuid(parts[1]);
        AccountCatalog.requireCanonicalUuid(parts[2]);
        EconomyCurrency.fromCode(parts[3]);
        long revision = Long.parseLong(parts[4]);
        boolean validRevision = switch (parts[0]) {
            case "opened" -> revision == 1;
            case "closed" -> revision >= 2;
            default -> false;
        };
        if (!validRevision || !Long.toString(revision).equals(parts[4]))
            throw new IllegalArgumentException("Bank lifecycle action or revision");
    }

    private static void requireRevision(long revision) {
        if (revision < 1) throw new IllegalArgumentException("Account revision");
    }
}
