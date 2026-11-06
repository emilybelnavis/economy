package dev.emily.economy.collection;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CollectionCatalog {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "economy");
    public static final DeferredHolder<EntityType<?>, EntityType<CollectionAgent>> AGENT = ENTITIES.register("collection_agent", () ->
        EntityType.Builder.of(CollectionAgent::new, MobCategory.MISC).sized(0.6F, 1.8F)
            .clientTrackingRange(8).updateInterval(3).noSave().noSummon().build("economy:collection_agent"));
    private CollectionCatalog() {}
    public static void initialize(IEventBus bus) {
        ENTITIES.register(bus);
        bus.addListener(CollectionCatalog::attributes);
        NeoForge.EVENT_BUS.addListener(CollectionCatalog::interact);
    }
    private static void interact(PlayerInteractEvent.EntityInteract event) {
        if(!(event.getTarget() instanceof CollectionAgent agent))return;
        event.setCanceled(true);event.setCancellationResult(InteractionResult.CONSUME);
        if(event.getHand()==InteractionHand.MAIN_HAND&&event.getEntity() instanceof ServerPlayer player&&CollectionAgents.canInteract(player,agent))
            CollectionDialogue.open(player,agent);
    }
    private static void attributes(EntityAttributeCreationEvent event) { event.put(AGENT.get(), CollectionAgent.attributes().build()); }
}
