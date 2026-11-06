package ca.emilybelnavis.mcmods.economy.card;

import ca.emilybelnavis.mcmods.economy.institution.InstitutionCatalog;
import java.util.*;

/** Pure approved resource identities, usable by worker validation without bootstrapping Minecraft registries. */
public final class CardDesigns {
    public record Design(String id,InstitutionCatalog.InstitutionId issuer,int tier) {}
    private CardDesigns() {}
    public static String id(InstitutionCatalog.InstitutionId issuer,int tier) {
        var bank=InstitutionCatalog.require(issuer);
        if(tier==1?!bank.tier1():tier!=2||!bank.tier2())throw new IllegalArgumentException("CARD_PRODUCT");
        return "economy:issued_"+issuer.key().substring("economy:bank/".length())+"_debit_t"+tier;
    }
    public static List<Design> all() {
        var result=new ArrayList<Design>();
        for(var bank:InstitutionCatalog.banks()) {
            if(bank.tier1())result.add(new Design(id(bank.id(),1),bank.id(),1));
            if(bank.tier2())result.add(new Design(id(bank.id(),2),bank.id(),2));
        }
        return List.copyOf(result);
    }
    /** One appearance per issuer; tier-suffixed identities remain valid for existing cards. */
    public static List<Design> collectibles() {
        var issuers = new LinkedHashMap<InstitutionCatalog.InstitutionId, Design>();
        for (var design : all()) issuers.putIfAbsent(design.issuer(), design);
        return List.copyOf(issuers.values());
    }
    public static Optional<Design> byId(String id){return all().stream().filter(design->design.id().equals(id)).findFirst();}
}
