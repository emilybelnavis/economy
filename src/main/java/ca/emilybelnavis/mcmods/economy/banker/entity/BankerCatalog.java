package ca.emilybelnavis.mcmods.economy.banker.entity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.*;
public final class BankerCatalog {
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(Registries.ENTITY_TYPE,"economy");
    public static final DeferredHolder<EntityType<?>,EntityType<BankerEntity>> BANKER=TYPES.register("banker",()->EntityType.Builder.of(BankerEntity::new,MobCategory.MISC).sized(0.6F,1.95F).clientTrackingRange(8).updateInterval(3).build("economy:banker"));
    private BankerCatalog(){}
    public static void initialize(IEventBus bus){TYPES.register(bus);bus.addListener((EntityAttributeCreationEvent event)->event.put(BANKER.get(),BankerEntity.attributes().build()));}
}
