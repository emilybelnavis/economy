package dev.emily.economy.card;

import dev.emily.economy.institution.InstitutionCatalog;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Issued debit appearances only. An item is a lookup key, never an account or borrowing grant. */
public final class CardCatalog {
    public record Entry(String id, InstitutionCatalog.InstitutionId issuer, int tier, DeferredItem<? extends Item> item) {}
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("economy");
    private static final Map<String,Entry> ENTRIES=new LinkedHashMap<>();
    static {
        for(var bank:InstitutionCatalog.banks()) {
            if(bank.tier1())register(bank,1);
            if(bank.tier2())register(bank,2);
        }
        for(var design:CreditCardDesigns.all()) {
            var bank=InstitutionCatalog.require(design.issuer());
            var item=ITEMS.register(design.id().substring("economy:".length()),()->new CreditItem(new Item.Properties().stacksTo(1),bank,design.network(),design.tier()));
            ENTRIES.put(design.id(),new Entry(design.id(),design.issuer(),design.tier(),item));
        }
    }
    private CardCatalog() {}
    private static void register(InstitutionCatalog.Entry bank,int tier) {
        String id=id(bank.id(),tier).substring("economy:".length());
        var item=ITEMS.register(id,()->new DebitItem(new Item.Properties().stacksTo(1),bank,tier));
        ENTRIES.put("economy:"+id,new Entry("economy:"+id,bank.id(),tier,item));
    }
    public static String id(InstitutionCatalog.InstitutionId issuer,int tier) {
        return CardDesigns.id(issuer,tier);
    }
    public static List<Entry> all(){return List.copyOf(ENTRIES.values());}
    public static Optional<Entry> byId(String id){return Optional.ofNullable(ENTRIES.get(id));}
    public static Optional<Entry> find(ItemStack stack) {
        if(stack.isEmpty())return Optional.empty();
        return byId(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }
    public static void initialize(IEventBus bus){ITEMS.register(bus);}
    private static final class CreditItem extends Item {
        private final InstitutionCatalog.Entry issuer;
        private final String network;
        private final int tier;
        CreditItem(Properties properties,InstitutionCatalog.Entry issuer,String network,int tier){super(properties);this.issuer=issuer;this.network=network;this.tier=tier;}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
            lines.add(Component.literal(issuer.name()+" · "+network.substring("economy:".length())+" Credit · Tier "+tier));
            lines.add(Component.translatable("item.economy.issued_card.lookup"));
            lines.add(Component.translatable("item.economy.issued_card.expiry"));
        }
    }
    private static final class DebitItem extends Item {
        private final InstitutionCatalog.Entry issuer;
        private final int tier;
        DebitItem(Properties properties,InstitutionCatalog.Entry issuer,int tier){super(properties);this.issuer=issuer;this.tier=tier;}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
            lines.add(Component.literal(issuer.name()+" · Debit · Tier "+tier));
            lines.add(Component.translatable("item.economy.issued_card.lookup"));
            lines.add(Component.translatable("item.economy.issued_card.expiry"));
        }
    }
}
