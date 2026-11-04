package dev.emily.economy.journal;

import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.api.Money;
import dev.emily.economy.storage.LedgerProjection;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static dev.emily.economy.journal.PostingBatch.Role.*;

/** Trusted worker-side settlement registration for the server's journal service. */
public final class Settlement {
    private Settlement() {}

    @FunctionalInterface
    public interface AuthorityResolver {
        /** Authorizes all legs against live state. Return null to deny. */
        PostingBatch.Authority resolve(Journal.Intent intent, List<PostingBatch.Leg> legs,
                                       JournalEngine.ReadView reads);
    }

    private record Account(UUID id, EconomyCurrency currency, PostingBatch.Role role) {}

    public static UUID issuanceAccount(EconomyCurrency currency) {
        return systemAccount("issuance", currency);
    }

    public static UUID clearingAccount(EconomyCurrency currency) {
        return systemAccount("clearing", currency);
    }

    public static UUID feeAccount(EconomyCurrency currency) {
        return systemAccount("fee", currency);
    }

    private static UUID systemAccount(String purpose, EconomyCurrency currency) {
        return UUID.nameUUIDFromBytes(("economy:" + purpose + ":" + currency.name()).getBytes(StandardCharsets.UTF_8));
    }

    public static Journal.CanonicalRequest request(List<PostingBatch.Leg> legs) {
        var entries = new StringJoiner("\n");
        for (var leg : legs) {
            entries.add(leg.account() + "|" + leg.role().name() + "|" + leg.accountCurrency().name()
                    + "|" + leg.amount().minorUnits());
            if (leg.amount().currency() != leg.accountCurrency()) throw new IllegalArgumentException("Posting currency");
        }
        return new Journal.CanonicalRequest(1, Map.of("postings", entries.toString()));
    }

    public static JournalEngine.Registration registration(AuthorityResolver resolver) {
        Objects.requireNonNull(resolver);
        return new JournalEngine.Registration(1, (intent, reads) -> prepare(intent, reads, resolver));
    }

    private static JournalEngine.Decision prepare(Journal.Intent intent, JournalEngine.ReadView reads,
                                                  AuthorityResolver resolver) {
        List<PostingBatch.Leg> legs;
        try {
            legs = decode(intent.request());
        } catch (IllegalArgumentException malformed) {
            return reject("POSTING_REQUEST");
        }
        var authority = resolver.resolve(intent, legs, reads);
        return prepare(reads, legs, authority, null);
    }

    static JournalEngine.Decision prepare(JournalEngine.ReadView reads, List<PostingBatch.Leg> legs,
                                          PostingBatch.Authority authority, Holds.Hold captured) {
        return prepare(reads, legs, authority, captured, Map.of());
    }

    static JournalEngine.Decision prepareRebase(JournalEngine.ReadView reads, List<PostingBatch.Leg> legs,
                                                Map<UUID, EconomyCurrency> newCurrencies) {
        var authority = legs.stream().anyMatch(leg -> leg.role() == CLEARING)
                ? PostingBatch.Authority.FX : PostingBatch.Authority.TRANSFER;
        return prepare(reads, legs, authority, null, Map.copyOf(newCurrencies));
    }

    private static JournalEngine.Decision prepare(JournalEngine.ReadView reads, List<PostingBatch.Leg> legs,
                                                   PostingBatch.Authority authority, Holds.Hold captured,
                                                   Map<UUID, EconomyCurrency> newCurrencies) {
        var decision = PostingBatch.prepare(legs, authority);
        if (!(decision instanceof JournalEngine.Commit commit)) return decision;

        var deltas = new LinkedHashMap<Account, Long>();
        var balances = new LinkedHashMap<Account, Long>();
        try {
            for (var leg : legs) {
                var account = new Account(leg.account(), leg.accountCurrency(), leg.role());
                if (!validRole(account)) {
                    return reject("POSTING_ROLE");
                }
                deltas.merge(account, leg.amount().minorUnits(), Math::addExact);
            }
            for (var entry : deltas.entrySet()) {
                var account = entry.getKey();
                boolean ordinary = account.role() == ACCOUNT;
                boolean rebasedCredit = entry.getValue() > 0 && newCurrencies.get(account.id()) == account.currency();
                if (ordinary && !rebasedCredit && !openBank(reads, account.id(), account.currency())) {
                    return reject("POSTING_ACCOUNT");
                }
                long next = Math.addExact(posted(reads, account.id(), account.currency()), entry.getValue());
                if (ordinary) {
                    long reserved = Holds.reserved(reads, account.id(), account.currency());
                    if (captured != null && captured.account().equals(account.id())
                            && captured.amount().currency() == account.currency()) {
                        reserved = Math.subtractExact(reserved, captured.amount().minorUnits());
                    }
                    if (next < reserved) return reject("INSUFFICIENT_FUNDS");
                }
                balances.put(account, next);
            }
        } catch (ArithmeticException overflow) {
            return reject("POSTING_OVERFLOW");
        }
        return new JournalEngine.Commit(commit.records(), commit.result(), transaction -> {
            for (var entry : balances.entrySet()) {
                var account = entry.getKey();
                LedgerProjection.write(transaction, account.id(), account.currency(), entry.getValue());
            }
        });
    }

    private static List<PostingBatch.Leg> decode(Journal.CanonicalRequest request) {
        String payload = request.fields().get("postings");
        if (payload == null) throw new IllegalArgumentException("Missing postings");
        var legs = new ArrayList<PostingBatch.Leg>();
        for (String line : payload.split("\n", -1)) {
            String[] fields = line.split("\\|", -1);
            if (fields.length != 4) throw new IllegalArgumentException("Posting fields");
            var currency = EconomyCurrency.valueOf(fields[2]);
            legs.add(new PostingBatch.Leg(UUID.fromString(fields[0]), PostingBatch.Role.valueOf(fields[1]),
                    currency, new Money(currency, Long.parseLong(fields[3]))));
        }
        if (!request(legs).equals(request)) throw new IllegalArgumentException("Noncanonical postings");
        return List.copyOf(legs);
    }

    private static boolean validRole(Account account) {
        var id = account.id();
        var currency = account.currency();
        return switch (account.role()) {
            case ISSUANCE -> id.equals(issuanceAccount(currency));
            case CLEARING -> id.equals(clearingAccount(currency));
            case FEE -> id.equals(feeAccount(currency));
            case ACCOUNT -> !id.equals(issuanceAccount(currency))
                    && !id.equals(clearingAccount(currency)) && !id.equals(feeAccount(currency));
        };
    }

    static boolean openBank(JournalEngine.ReadView reads, UUID account, EconomyCurrency currency) {
        return !reads.query("""
                SELECT account_id FROM account_catalog
                WHERE account_id=? AND currency=? AND purpose='BANK' AND status='OPEN'
                """, List.of(account.toString(), currency.name()), 1).isEmpty();
    }

    public static long posted(JournalEngine.ReadView reads, UUID account, EconomyCurrency currency) {
        var rows = reads.query("""
                SELECT minor_units FROM ledger_balances WHERE account_id=? AND currency=?
                """, List.of(account.toString(), currency.name()), 1);
        return rows.isEmpty() ? 0 : Long.parseLong(rows.getFirst().get("minor_units"));
    }

    private static JournalEngine.Reject reject(String code) {
        return new JournalEngine.Reject(new Journal.Result(1, code, ""));
    }
}
