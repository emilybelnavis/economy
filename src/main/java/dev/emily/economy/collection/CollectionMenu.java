package dev.emily.economy.collection;

import dev.emily.economy.Economy;
import dev.emily.economy.foundation.ServerServices;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Server-issued presentation identity. This menu never owns items or financial state. */
public final class CollectionMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final UUID entity,presentation,session;
    private long revision,lastActionTick=-10;
    private String page="cases",status="Loading collection cases";
    private List<String> lines=List.of();
    private List<CollectionNetwork.Choice> choices=List.of();
    private boolean busy=true;
    public CollectionMenu(int id,Inventory inventory,UUID entity,UUID presentation,UUID session){
        super(CollectionMenuCatalog.COLLECTION.get(),id);this.inventory=inventory;this.entity=entity;this.presentation=presentation;this.session=session;
    }
    static CollectionMenu fromNetwork(int id,Inventory inventory,RegistryFriendlyByteBuf buffer){return new CollectionMenu(id,inventory,buffer.readUUID(),buffer.readUUID(),buffer.readUUID());}
    public void writeOpening(RegistryFriendlyByteBuf buffer){buffer.writeUUID(entity);buffer.writeUUID(presentation);buffer.writeUUID(session);}
    public Player player(){return inventory.player;}
    public UUID session(){return session;}
    public UUID entityId(){return entity;}
    public UUID presentation(){return presentation;}
    public long revision(){return revision;}
    public String page(){return page;}
    public String status(){return status;}
    public List<String> lines(){return lines;}
    public List<CollectionNetwork.Choice> choices(){return choices;}
    public boolean busy(){return busy;}
    public boolean valid(ServerPlayer player){
        if(player!=inventory.player||player.containerMenu!=this||!player.isAlive()||player.server.getPlayerList().getPlayer(player.getUUID())!=player)return false;
        var services=Economy.serverServices(player.server);var found=player.serverLevel().getEntity(entity);
        return services!=null&&services.state()==ServerServices.State.READY&&found instanceof CollectionAgent agent&&presentation.equals(agent.presentation())
            &&player.getUUID().equals(agent.debtor())&&CollectionAgents.canInteract(player,agent);
    }
    public boolean accept(ServerPlayer player,CollectionNetwork.Action action){
        if(!identityMatches(containerId,session,revision,action)||!valid(player))return false;
        if(busy&&!Set.of("exit","retry").contains(action.action()))return false;
        if(!offered(choices,action.action(),action.value()))return false;
        long tick=player.server.getTickCount();if(tick-lastActionTick<2)return false;lastActionTick=tick;return true;
    }
    public static boolean identityMatches(int menu,UUID session,long revision,CollectionNetwork.Action action){
        return menu==action.menuId()&&session.equals(action.session())&&revision==action.revision();
    }
    public static boolean offered(List<CollectionNetwork.Choice> choices,String action,String value){
        if(action.equals("exit"))return value.isEmpty();
        return choices.stream().anyMatch(c->c.action().equals(action)&&(c.value().equals(value)||action.equals("preview_partial")&&c.value().isEmpty()));
    }
    public void publish(String page,List<String> lines,String status,boolean busy,List<CollectionNetwork.Choice> choices){
        var snapshot=new CollectionNetwork.Snapshot(containerId,session,Math.addExact(revision,1),page,lines,status,busy,choices);
        apply(snapshot);if(inventory.player instanceof ServerPlayer player)CollectionNetwork.send(player,snapshot);
    }
    public void apply(CollectionNetwork.Snapshot snapshot){
        if(snapshot.menuId()!=containerId||!session.equals(snapshot.session())||snapshot.revision()<=revision)return;revision=snapshot.revision();page=snapshot.page();lines=snapshot.lines();status=snapshot.status();busy=snapshot.busy();choices=snapshot.choices();
    }
    public boolean stillValid(Player player){return player instanceof ServerPlayer p?valid(p):player==inventory.player;}
    public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    public void clicked(int slot,int button,ClickType type,Player player){}
    public boolean canDragTo(Slot slot){return false;}
    public void removed(Player player){CollectionDialogue.closed(this);super.removed(player);}
}
