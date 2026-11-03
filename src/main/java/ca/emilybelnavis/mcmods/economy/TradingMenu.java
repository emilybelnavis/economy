package ca.emilybelnavis.mcmods.economy;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import ca.emilybelnavis.mcmods.economy.storage.FinanceDatabase;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** One CAD trading account per player, settled by the database's FIFO writer. */
public final class TradingMenu extends AbstractContainerMenu {
    private static final Gson JSON=new Gson();
    private final Player owner;
    private final BlockPos pos;
    public JsonObject view=new JsonObject();
    private boolean busy,refreshing,closed;
    private String status="";
    private int ticks;
    public TradingMenu(int id,Inventory inv,RegistryFriendlyByteBuf b){this(id,inv,b.readBlockPos());}
    public TradingMenu(int id,Inventory inv,BlockPos pos){super(Economy.TRADING_MENU.get(),id);owner=inv.player;this.pos=pos.immutable();}
    public static void open(ServerPlayer p,BlockPos pos){
        if(p.isSpectator())return;
        p.openMenu(new SimpleMenuProvider((id,inv,player)->new TradingMenu(id,inv,pos),Component.literal("Stock exchange")),b->b.writeBlockPos(pos));
    }
    @Override public boolean stillValid(Player p){return p==owner&&p.isAlive()&&!p.isSpectator()&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64&&p.level().getBlockState(pos).is(TerminalVariants.STOCK_COMPUTER.get());}
    public void receive(FinanceAction request){
        if(!(owner instanceof ServerPlayer p)||request.menuId()!=containerId||busy||!stillValid(p))return;
        try{
            var args=JsonParser.parseString(request.json()).getAsJsonObject();
            if(request.action().equals("refresh")){refresh();return;}
            var service=FinanceService.get(p.server);CompletableFuture<?> operation;
            switch(request.action()){
                case "buy","sell"->{long quantity=args.get("quantity").getAsBigDecimal().longValueExact();if(quantity<=0)throw new IllegalArgumentException("Enter a positive whole share quantity.");String stock=args.get("stock").getAsString();operation=service.ready().thenCompose(v->service.database.trade(p.getUUID(),stock,request.action().equals("buy"),quantity));}
                case "fund","unfund"->{
                    String bank=args.get("account").getAsString();long amount=args.get("amount").getAsBigDecimal().longValueExact();if(amount<=0)throw new IllegalArgumentException("Enter a positive amount.");
                    operation=service.ready().thenCompose(v->service.database.accounts(p.getUUID())).thenCompose(accounts->{
                        var source=accounts.stream().filter(a->a.kind().equals("BANK")&&a.id().equals(bank)).findFirst().orElseThrow(()->new IllegalArgumentException("Choose a bank account."));
                        var trading=accounts.stream().filter(a->a.kind().equals("TRADING")).findFirst().orElseThrow();
                        return request.action().equals("fund")?service.database.transfer(p.getUUID(),source.id(),trading.id(),amount,"Trading account funding"):service.database.transfer(p.getUUID(),trading.id(),source.id(),amount,"Trading account withdrawal");
                    });
                }
                default->throw new IllegalArgumentException("Unknown trading request.");
            }
            busy=true;view.addProperty("busy",true);send();
            operation.whenComplete((result,error)->p.server.execute(()->{busy=false;status=error==null?"Order settled.":message(error);refresh();}));
        }catch(RuntimeException e){status=message(e);refresh();}
    }
    public void refresh(){
        if(refreshing||closed||!(owner instanceof ServerPlayer p)||!stillValid(p))return;
        refreshing=true;var service=FinanceService.get(p.server);
        service.ready().thenCompose(v->service.database.accounts(p.getUUID())).thenCombine(service.database.quotes(),(accounts,quotes)->{
            var result=new JsonObject();result.add("accounts",JSON.toJsonTree(accounts.stream().filter(a->a.kind().equals("BANK")).toList()));result.add("trading",JSON.toJsonTree(accounts.stream().filter(a->a.kind().equals("TRADING")).findFirst().orElseThrow()));result.add("quotes",JSON.toJsonTree(quotes));return result;
        }).thenCombine(service.database.holdings(p.getUUID()),(result,holdings)->{result.add("holdings",JSON.toJsonTree(holdings));return result;})
          .thenCombine(service.database.orders(p.getUUID(),0,10),(result,orders)->{result.add("orders",JSON.toJsonTree(orders));return result;})
          .whenComplete((result,error)->p.server.execute(()->{refreshing=false;if(closed)return;if(error!=null)status=message(error);else view=result;view.addProperty("busy",busy);view.addProperty("status",status);send();}));
    }
    private static String message(Throwable t){while(t.getCause()!=null)t=t.getCause();return t.getMessage()==null?"Trade failed.":t.getMessage();}
    private void send(){if(!closed&&owner instanceof ServerPlayer p&&p.containerMenu==this)PacketDistributor.sendToPlayer(p,new FinanceSnapshot(containerId,view.toString()));}
    @Override public void broadcastChanges(){super.broadcastChanges();if(++ticks%40==1)refresh();}
    @Override public ItemStack quickMoveStack(Player p,int index){return ItemStack.EMPTY;}
    @Override public void removed(Player p){closed=true;super.removed(p);}
}
