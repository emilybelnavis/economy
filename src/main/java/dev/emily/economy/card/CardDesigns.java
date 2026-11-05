package dev.emily.economy.card;

import dev.emily.economy.institution.InstitutionCatalog;
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
    public static Optional<Design> byId(String id){return all().stream().filter(design->design.id().equals(id)).findFirst();}
}
