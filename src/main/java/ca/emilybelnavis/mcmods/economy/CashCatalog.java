package ca.emilybelnavis.mcmods.economy;

import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.*;
import ca.emilybelnavis.mcmods.economy.BankingConfig.Currency;

/** Face values are cached from the JSON currency definitions, independent of the textures. */
public final class CashCatalog {
    public record Cash(Item item,Currency currency,long minor,boolean coin) {}
    private record Definition(Currency currency,long minor,boolean coin) {}
    private static final Map<String,Definition> DEFINITIONS=load();
    private static Map<String,Definition> load(){
        var result=new HashMap<String,Definition>();
        for(var e:FinanceDefinitions.cashItems()){
            var d=e.getAsJsonObject();long value=d.get("minor").getAsLong();
            if(value<=0)throw new IllegalStateException("Invalid cash denomination");
            if(result.put(d.get("id").getAsString(),new Definition(Currency.valueOf(d.get("currency").getAsString()),value,d.get("coin").getAsBoolean()))!=null)throw new IllegalStateException("Duplicate cash ID");
        }
        return Map.copyOf(result);
    }
    public static Cash find(ItemStack stack){
        if(stack.isEmpty())return null;
        var d=DEFINITIONS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        return d==null?null:new Cash(stack.getItem(),d.currency(),d.minor(),d.coin());
    }
    public static List<Cash> withdrawals(Currency currency,boolean teller){
        return CollectibleItems.SERIES.get(currency.name().toLowerCase(Locale.ROOT)).stream()
            .map(x->find(new ItemStack(x.get()))).filter(Objects::nonNull).filter(x->teller||!x.coin())
            .filter(x->currency!=Currency.CAD||x.coin()||BuiltInRegistries.ITEM.getKey(x.item()).getPath().endsWith("_horizontal"))
            .sorted(Comparator.comparingLong(Cash::minor)).toList();
    }
    public static List<Cash> all(){
        var result=new ArrayList<Cash>();
        for(var c:Currency.values())for(var item:CollectibleItems.SERIES.get(c.name().toLowerCase(Locale.ROOT))){var cash=find(new ItemStack(item.get()));if(cash!=null)result.add(cash);}
        return List.copyOf(result);
    }
    public static boolean hasCard(net.minecraft.world.entity.player.Player p){
        return CollectibleItems.CARDS.values().stream().anyMatch(c->p.getInventory().contains(new ItemStack(c.get())));
    }
    private CashCatalog(){}
}
