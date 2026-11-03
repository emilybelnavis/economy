package ca.emilybelnavis.mcmods.economy;

import com.google.gson.*;
import java.math.*;
import java.time.LocalDate;
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

/** Server-owned trading and recorded exchange-rate views; all database reads are asynchronous. */
public final class TradingMenu extends AbstractContainerMenu {
    private static final Gson JSON=new Gson();
    private final Player owner;
    private final BlockPos pos;
    public JsonObject view=new JsonObject();
    private boolean busy,refreshing,closed,ratePairInitialized;
    private String status="",selectedStock="",page="STOCKS",rateFrom="CAD",rateTo="USD",rateBefore="";
    private int ticks,revision;
    public TradingMenu(int id,Inventory inv,RegistryFriendlyByteBuf b){this(id,inv,b.readBlockPos());}
    public TradingMenu(int id,Inventory inv,BlockPos pos){super(Economy.TRADING_MENU.get(),id);owner=inv.player;this.pos=pos.immutable();}
    public static void open(ServerPlayer p,BlockPos pos){
        if(p.isSpectator())return;
        p.openMenu(new SimpleMenuProvider((id,inv,player)->new TradingMenu(id,inv,pos),Component.literal("Stock exchange")),b->b.writeBlockPos(pos));
    }
    @Override public boolean stillValid(Player p){return p==owner&&p.isAlive()&&!p.isSpectator()&&p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64&&p.level().getBlockState(pos).is(TerminalVariants.STOCK_COMPUTER.get());}
    public void receive(FinanceAction request){
        if(!(owner instanceof ServerPlayer p)||request.menuId()!=containerId||busy||!stillValid(p)||FinanceService.get(p.server).changingCurrency())return;
        try{
            var args=JsonParser.parseString(request.json()).getAsJsonObject();
            switch(request.action()) {
                case "refresh" -> {refresh();return;}
                case "page" -> {
                    String next=args.get("page").getAsString();
                    if(!next.equals("STOCKS")&&!next.equals("RATES"))throw new IllegalArgumentException("Unknown exchange page.");
                    page=next;revision++;refresh();return;
                }
                case "rates_pair" -> {
                    String from=BankingConfig.Currency.valueOf(args.get("from").getAsString()).name();
                    String to=BankingConfig.Currency.valueOf(args.get("to").getAsString()).name();
                    rateFrom=from;rateTo=to;rateBefore="";ratePairInitialized=true;page="RATES";revision++;refresh();return;
                }
                case "rates_history" -> {
                    String cursor=args.get("before").getAsString();
                    if(!cursor.isBlank())cursor=LocalDate.parse(cursor).toString();
                    rateBefore=cursor;page="RATES";revision++;refresh();return;
                }
                case "stock" -> {
                    String stock=args.get("stock").getAsString();
                    if(FinanceDefinitions.stocks().stream().noneMatch(s->s.id().equals(stock)))throw new IllegalArgumentException("Unknown stock");
                    selectedStock=stock;revision++;refresh();return;
                }
                default -> {}
            }
            var service=FinanceService.get(p.server);CompletableFuture<?> operation;
            switch(request.action()){
                case "buy","sell"->{
                    long quantity=args.get("quantity").getAsBigDecimal().longValueExact();
                    if(quantity<=0)throw new IllegalArgumentException("Enter a positive whole share quantity.");
                    String stock=args.get("stock").getAsString();
                    operation=service.ready().thenCompose(v->service.database.trade(p.getUUID(),stock,request.action().equals("buy"),quantity));
                }
                case "fund","unfund"->{
                    String bank=args.get("account").getAsString();long amount=args.get("amount").getAsBigDecimal().longValueExact();
                    if(amount<=0)throw new IllegalArgumentException("Enter a positive amount.");
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
        var service=FinanceService.get(p.server);
        if(!ratePairInitialized&&service.marketCurrency()!=null){
            rateFrom=service.marketCurrency();rateTo=rateFrom.equals("USD")?"CAD":"USD";ratePairInitialized=true;
        }
        refreshing=true;
        String selection=selectedStock,requestedPage=page,from=rateFrom,to=rateTo,before=rateBefore;int version=revision;
        long day=Math.max(0,p.server.overworld().getDayTime()/24000);
        CompletableFuture<JsonObject> base=service.ready().thenCompose(v->service.database.accounts(p.getUUID())).thenCombine(service.database.marketCurrency(),(accounts,currency)->{
            var result=new JsonObject();
            result.add("accounts",JSON.toJsonTree(accounts.stream().filter(a->a.kind().equals("BANK")).toList()));
            result.add("trading",JSON.toJsonTree(accounts.stream().filter(a->a.kind().equals("TRADING")).findFirst().orElseThrow()));
            result.addProperty("currency",currency);result.addProperty("page",requestedPage);
            result.addProperty("rateFrom",from);result.addProperty("rateTo",to);
            return result;
        }).thenCombine(service.database.exchangeRates(),(result,rates)->{
            BigDecimal current=new BigDecimal(rates.get(from)).divide(new BigDecimal(rates.get(to)),12,RoundingMode.HALF_UP);
            result.addProperty("rateCurrent",current.stripTrailingZeros().toPlainString());return result;
        });
        CompletableFuture<JsonObject> data;
        if(requestedPage.equals("RATES")){
            data=base.thenCompose(result->service.database.exchangeRateHistory(from,to,before,16).thenApply(points->{
                var visible=points.stream().limit(15).toList();result.add("rateHistory",JSON.toJsonTree(visible));
                result.addProperty("rateHasMore",points.size()>15);
                result.addProperty("rateBefore",visible.isEmpty()?"":visible.getLast().date());return result;
            }));
        }else{
            data=base.thenCombine(service.database.quotes(),(result,quotes)->{
                result.add("quotes",JSON.toJsonTree(quotes));
                String stock=quotes.stream().anyMatch(q->q.id().equals(selection))?selection:quotes.isEmpty()?"":quotes.getFirst().id();
                result.addProperty("selectedStock",stock);result.addProperty("currentDay",day);return result;
            }).thenCombine(service.database.holdings(p.getUUID()),(result,holdings)->{result.add("holdings",JSON.toJsonTree(holdings));return result;})
              .thenCombine(service.database.orders(p.getUUID(),0,10),(result,orders)->{result.add("orders",JSON.toJsonTree(orders));return result;})
              .thenCompose(result->service.database.stockHistory(result.get("selectedStock").getAsString(),day).thenApply(chart->{result.add("chart",JSON.toJsonTree(chart));return result;}));
        }
        data.whenComplete((result,error)->p.server.execute(()->{
            refreshing=false;if(closed)return;if(version!=revision){refresh();return;}
            if(error!=null)status=message(error);else view=result;
            view.addProperty("busy",busy);view.addProperty("status",status);send();
        }));
    }
    private static String message(Throwable t){while(t.getCause()!=null)t=t.getCause();return t.getMessage()==null?"Trade failed.":t.getMessage();}
    private void send(){if(!closed&&owner instanceof ServerPlayer p&&p.containerMenu==this)PacketDistributor.sendToPlayer(p,new FinanceSnapshot(containerId,view.toString()));}
    @Override public void broadcastChanges(){super.broadcastChanges();if(++ticks%40==1)refresh();}
    @Override public ItemStack quickMoveStack(Player p,int index){return ItemStack.EMPTY;}
    @Override public void removed(Player p){closed=true;super.removed(p);}
}
