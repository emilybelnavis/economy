package ca.emilybelnavis.mcmods.economy;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import ca.emilybelnavis.mcmods.economy.BankingConfig.Currency;
import ca.emilybelnavis.mcmods.economy.storage.FinanceDatabase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Transient inventory escrow on the tick thread; all monetary settlement is asynchronous. */
public final class BankingMenu extends AbstractContainerMenu {
    public enum Page { HOME, DEPOSIT, WITHDRAW, HISTORY, ACCOUNTS, CONFIG }
    private static final Gson JSON=new Gson();
    private final SimpleContainer deposit=new SimpleContainer(24);
    private final Player owner;
    private final BlockPos pos;
    public final boolean teller;
    public Page page;
    public JsonObject view=new JsonObject();
    private List<FinanceDatabase.Account> accounts=List.of();
    private String selected="";
    private boolean busy, refreshing, closed;
    private String status="";
    private long beforeSequence;
    private int ticks;
    private int revision;
    private int withdrawalRevision;
    private WithdrawalPlanner.Plan withdrawalPlan;
    private Currency withdrawalCurrency;
    private String withdrawalAccount="";
    private long withdrawalCost;
    private TerminalSettings.Settings withdrawalSettings;
    private Map<String,Map<String,Boolean>> withdrawalAdjustments=Map.of();
    private record PlannedWithdrawal(WithdrawalPlanner.Plan plan,Map<String,Map<String,Boolean>> adjustments) {}
    private record QuotedWithdrawal(PlannedWithdrawal planned,long cost) {}


    public BankingMenu(int id,Inventory inv,RegistryFriendlyByteBuf b){this(id,inv,b.readBlockPos(),b.readBoolean(),b.readBoolean());}
    public BankingMenu(int id,Inventory inv,BlockPos pos,boolean teller,boolean configure){
        super(Economy.BANKING_MENU.get(),id);owner=inv.player;this.pos=pos.immutable();this.teller=teller;page=configure?Page.CONFIG:Page.HOME;
        for(int row=0;row<4;row++)for(int col=0;col<6;col++)addSlot(new Slot(deposit,col+row*6,11+col*18,53+row*18){
            @Override public boolean isActive(){return page==Page.DEPOSIT;}
            @Override public boolean mayPlace(ItemStack stack){return !busy&&page==Page.DEPOSIT&&accepts(stack);}
            @Override public boolean mayPickup(Player p){return !busy;}
        });
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inv,col+row*9+9,77+col*18,154+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inv,col,77+col*18,212));
    }
    public static void open(ServerPlayer p,BlockPos pos,boolean teller){open(p,pos,teller,false);}
    public static void open(ServerPlayer p,BlockPos pos,boolean teller,boolean configure){
        if(p.isSpectator()||(!configure&&!CashCatalog.hasCard(p))||(configure&&!TerminalSettings.canConfigure(p,pos))){
            p.displayClientMessage(Component.translatable("banking.economy.card_required"),true);return;
        }
        p.openMenu(new SimpleMenuProvider((id,inv,player)->new BankingMenu(id,inv,pos,teller,configure),Component.translatable(teller?"banking.economy.teller":"banking.economy.atm")),b->{b.writeBlockPos(pos);b.writeBoolean(teller);b.writeBoolean(configure);});
    }
    private boolean near(Player p){
        if(p!=owner||!p.isAlive()||p.isSpectator()||p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64)return false;
        var block=p.level().getBlockState(pos).getBlock();return teller?block==TerminalVariants.BANK_TELLER.get():block instanceof AtmBlock;
    }
    @Override public boolean stillValid(Player p){return near(p)&&(CashCatalog.hasCard(p)||(page==Page.CONFIG&&p instanceof ServerPlayer sp&&TerminalSettings.canConfigure(sp,pos)));}
    private TerminalSettings.Settings settings(){return TerminalSettings.get(((ServerPlayer)owner).serverLevel(),pos);}
    private boolean accepts(ItemStack stack){
        var cash=CashCatalog.find(stack);if(cash==null||(!teller&&cash.coin()))return false;
        if(owner.level().isClientSide){
            if(!view.has("accepted"))return false;
            for(var e:view.getAsJsonArray("accepted"))if(e.getAsString().equals(cash.currency().name()))return true;
            return false;
        }
        return settings().permitsDeposit()&&settings().accepts(cash);
    }
    @Override public void clicked(int slot,int button,ClickType type,Player p){if(!busy&&stillValid(p))super.clicked(slot,button,type,p);}
    public void receive(FinanceAction action){
        if(!(owner instanceof ServerPlayer p)||action.menuId()!=containerId||busy||!stillValid(p)||FinanceService.get(p.server).changingCurrency())return;
        try{
            JsonObject args=JsonParser.parseString(action.json()).getAsJsonObject();
            if(action.action().equals("page")){
                Page next=Page.valueOf(args.get("page").getAsString());
                if(next==Page.CONFIG&&!TerminalSettings.canConfigure(p,pos))throw new IllegalArgumentException("Only the placer or an operator can configure this terminal.");
                if(next!=Page.CONFIG&&!CashCatalog.hasCard(p))throw new IllegalArgumentException("A bank or credit card is required.");
                if(next==Page.ACCOUNTS&&!teller)throw new IllegalArgumentException("Visit a bank teller to manage accounts.");
                if(page==Page.DEPOSIT&&next!=page)clearContainer(p,deposit);
                invalidateWithdrawal();page=next;beforeSequence=0;revision++;refresh();return;
            }
            if(action.action().equals("select")){
                String id=args.get("account").getAsString();if(accounts.stream().noneMatch(a->a.id().equals(id)&&a.kind().equals("BANK")))throw new IllegalArgumentException("Choose your bank account.");
                invalidateWithdrawal();selected=id;beforeSequence=0;revision++;refresh();return;
            }
            if(action.action().equals("configure")){configure(p,args);return;}
            if(!CashCatalog.hasCard(p))throw new IllegalArgumentException("A bank or credit card is required.");
            switch(action.action()){
                case "deposit"->deposit(p);
                case "prepare_withdraw"->prepareWithdrawal(p,args);
                case "adjust_withdraw"->adjustWithdrawal(p,args);
                case "withdraw"->withdraw(p,args);
                case "open"->{if(!teller)throw new IllegalArgumentException("Visit a bank teller.");run(p,db->db.openAccount(p.getUUID(),args.get("currency").getAsString()),"Account opened.");}
                case "close"->{if(!teller)throw new IllegalArgumentException("Visit a bank teller.");String destination=args.has("destination")?args.get("destination").getAsString():null;run(p,db->db.closeAccount(p.getUUID(),selected,destination),"Account closed; remaining funds transferred.");}
                case "history"->{beforeSequence=args.get("before").getAsLong();refresh();}
                case "refresh"->refresh();
                default->throw new IllegalArgumentException("Unknown banking request.");
            }
        }catch(RuntimeException e){status=message(e);refresh();}
    }
    private void configure(ServerPlayer p,JsonObject args){
        if(page!=Page.CONFIG||!TerminalSettings.canConfigure(p,pos))throw new IllegalArgumentException("Configuration permission denied.");
        var accepted=EnumSet.noneOf(Currency.class);for(var e:args.getAsJsonArray("accepted"))accepted.add(Currency.valueOf(e.getAsString()));
        var dispensed=new HashSet<String>();for(var e:args.getAsJsonArray("dispensed"))dispensed.add(e.getAsString());
        if(!TerminalSettings.configure(p,pos,new TerminalSettings.Settings(settings().owner(),TerminalSettings.Mode.valueOf(args.get("mode").getAsString()),accepted,dispensed)))throw new IllegalArgumentException("Configuration permission denied.");
        invalidateWithdrawal();status="Terminal configuration saved.";refresh();
    }
    private FinanceDatabase.Account selectedAccount(){return accounts.stream().filter(a->a.id().equals(selected)&&a.kind().equals("BANK")).findFirst().orElseThrow(()->new IllegalArgumentException("Choose an account."));}
    private Map<String,Long> totals(){
        var totals=new TreeMap<String,Long>();for(int i=0;i<24;i++){
            var stack=deposit.getItem(i);if(stack.isEmpty())continue;
            if(!accepts(stack))throw new IllegalArgumentException("This terminal does not accept that cash.");
            var cash=CashCatalog.find(stack);totals.merge(cash.currency().name(),Math.multiplyExact(cash.minor(),stack.getCount()),Math::addExact);
        }return Map.copyOf(totals);
    }
    private interface Request<T>{CompletableFuture<T> run(FinanceDatabase db);}
    private <T> void run(ServerPlayer p,Request<T> request,String success){
        busy=true;sendBusy();var service=FinanceService.get(p.server);
        service.ready().thenCompose(v->request.run(service.database)).whenComplete((result,error)->p.server.execute(()->{busy=false;status=error==null?success:message(error);refresh();}));
    }
    private void deposit(ServerPlayer p){
        if(page!=Page.DEPOSIT||!settings().permitsDeposit())throw new IllegalArgumentException("Deposits are disabled here.");
        String account=selectedAccount().id();var amounts=totals();if(amounts.isEmpty())throw new IllegalArgumentException("Place cash in the grid first.");
        var escrow=new ArrayList<ItemStack>();for(int i=0;i<24;i++){escrow.add(deposit.getItem(i).copy());deposit.setItem(i,ItemStack.EMPTY);}
        busy=true;sendBusy();var service=FinanceService.get(p.server);
        service.ready().thenCompose(v->service.database.depositCash(p.getUUID(),account,amounts,"Terminal cash deposit")).whenComplete((result,error)->p.server.execute(()->{
            busy=false;
            if(error!=null){for(int i=0;i<escrow.size();i++){var stack=escrow.get(i);if(!closed&&deposit.getItem(i).isEmpty())deposit.setItem(i,stack);else deliver(p,stack);}status=message(error);}
            else status="Deposit committed: "+BankingConfig.format(result.balance(),Currency.valueOf(result.currency()))+" account balance.";
            refresh();
        }));
    }
    /** Parent page/account/configuration changes must invalidate any previously quoted cash plan. */
    private void invalidateWithdrawal(){
        withdrawalRevision++;
        withdrawalPlan=null;withdrawalCurrency=null;withdrawalAccount="";withdrawalCost=0;
        withdrawalSettings=null;withdrawalAdjustments=Map.of();
    }
    /** Adds only immutable server-authoritative withdrawal state to a normal finance snapshot. */
    private void appendWithdrawalView(JsonObject result){
        result.addProperty("withdrawPrepared",withdrawalPlan!=null);
        result.addProperty("withdrawAmount",withdrawalPlan==null?0:withdrawalPlan.amount());
        result.addProperty("withdrawCurrency",withdrawalCurrency==null?"":withdrawalCurrency.name());
        result.addProperty("withdrawCost",withdrawalCost);
        result.add("withdrawBills",JSON.toJsonTree(withdrawalPlan==null?Map.of():withdrawalPlan.bills()));
        result.add("withdrawCoins",JSON.toJsonTree(withdrawalPlan==null?Map.of():withdrawalPlan.coins()));
        result.add("withdrawAdjustments",JSON.toJsonTree(withdrawalAdjustments));
    }
    private List<WithdrawalPlanner.Denomination> withdrawalDenominations(Currency currency,TerminalSettings.Settings setting){
        return CashCatalog.all().stream().filter(cash->cash.currency()==currency&&(teller||!cash.coin())&&setting.dispenses(cash))
            .map(cash->new WithdrawalPlanner.Denomination(BuiltInRegistries.ITEM.getKey(cash.item()).toString(),cash.minor(),cash.coin())).toList();
    }
    private static PlannedWithdrawal withAdjustments(WithdrawalPlanner.Plan plan,List<WithdrawalPlanner.Denomination> denominations,boolean teller){
        var adjustments=new TreeMap<String,Map<String,Boolean>>();
        for(var denomination:denominations)if(!denomination.coin()){
            boolean plus=false,minus=false;
            if(denomination.minor()<=plan.amount())try{WithdrawalPlanner.adjust(plan.amount(),denominations,plan,denomination.id(),1,teller);plus=true;}catch(IllegalArgumentException ignored){}
            if(plan.bills().getOrDefault(denomination.id(),0)>0)try{WithdrawalPlanner.adjust(plan.amount(),denominations,plan,denomination.id(),-1,teller);minus=true;}catch(IllegalArgumentException ignored){}
            adjustments.put(denomination.id(),Map.of("plus",plus,"minus",minus));
        }
        return new PlannedWithdrawal(plan,Map.copyOf(adjustments));
    }
    private void requireWithdrawal(){
        if(page!=Page.WITHDRAW||!settings().permitsWithdraw())throw new IllegalArgumentException("Withdrawals are disabled here.");
    }
    private void prepareWithdrawal(ServerPlayer p,JsonObject args){
        requireWithdrawal();
        long amount=args.get("amount").getAsBigDecimal().longValueExact();Currency currency=Currency.valueOf(args.get("currency").getAsString());
        var account=selectedAccount();var setting=settings();var denominations=withdrawalDenominations(currency,setting);
        invalidateWithdrawal();
        quoteWithdrawal(p,currency,account,setting,CompletableFuture.supplyAsync(()->withAdjustments(WithdrawalPlanner.plan(amount,denominations,teller),denominations,teller)));
    }
    private void adjustWithdrawal(ServerPlayer p,JsonObject args){
        requireWithdrawal();validatePreparedWithdrawal();
        var current=withdrawalPlan;var currency=withdrawalCurrency;var account=selectedAccount();var setting=settings();
        var denominations=withdrawalDenominations(currency,setting);String id=args.get("id").getAsString();
        Integer exact=args.has("quantity")?args.get("quantity").getAsBigDecimal().intValueExact():null;
        int direction=exact==null?args.get("delta").getAsBigDecimal().intValueExact():0;
        quoteWithdrawal(p,currency,account,setting,CompletableFuture.supplyAsync(()->{
            var adjusted=exact==null?WithdrawalPlanner.adjust(current.amount(),denominations,current,id,direction,teller)
                :WithdrawalPlanner.planWithBills(current.amount(),denominations,Map.of(id,exact),teller);
            return withAdjustments(adjusted,denominations,teller);
        }));
    }
    private void quoteWithdrawal(ServerPlayer p,Currency currency,FinanceDatabase.Account account,TerminalSettings.Settings setting,CompletableFuture<PlannedWithdrawal> planning){
        int requestRevision=withdrawalRevision;
        busy=true;sendBusy();var service=FinanceService.get(p.server);
        planning.thenCombine(service.ready(),(planned,ignored)->planned)
            .thenCompose(planned->service.database.quoteWithdrawal(planned.plan().amount(),currency.name(),account.currency()).thenApply(cost->new QuotedWithdrawal(planned,cost)))
            .whenComplete((quoted,error)->p.server.execute(()->{
                busy=false;
                if(error!=null){status=message(error);}
                else if(requestRevision==withdrawalRevision&&!closed&&near(p)&&selected.equals(account.id())&&settings().equals(setting)){
                    withdrawalPlan=quoted.planned().plan();withdrawalCurrency=currency;withdrawalAccount=account.id();
                    withdrawalSettings=setting;withdrawalCost=quoted.cost();withdrawalAdjustments=quoted.planned().adjustments();
                    status="Cash combination prepared. Review the account debit before withdrawing.";
                }else invalidateWithdrawal();
                appendWithdrawalView(view);sendBusy();refresh();
            }));
    }
    private void validatePreparedWithdrawal(){
        if(withdrawalPlan==null||!selected.equals(withdrawalAccount)||!settings().equals(withdrawalSettings)){
            invalidateWithdrawal();throw new IllegalArgumentException("Enter an amount and prepare a cash combination first.");
        }
    }
    private void withdraw(ServerPlayer p,JsonObject args){
        requireWithdrawal();validatePreparedWithdrawal();
        // The client cannot choose coin quantities, bypass the smallest-bill rule, or change the quoted debit.
        var account=selectedAccount();var plan=withdrawalPlan;String currency=withdrawalCurrency.name();long expected=withdrawalCost;
        var outputs=new ArrayList<ItemStack>();var counts=new TreeMap<String,Integer>(plan.bills());counts.putAll(plan.coins());
        for(var entry:counts.entrySet()){
            var item=BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(entry.getKey()));int quantity=entry.getValue();
            var cash=CashCatalog.find(new ItemStack(item));
            if(cash==null||!settings().dispenses(cash)||(!teller&&cash.coin())){invalidateWithdrawal();throw new IllegalArgumentException("The terminal cash configuration changed. Prepare a new combination.");}
            while(quantity>0){int n=Math.min(quantity,item.getDefaultMaxStackSize());outputs.add(new ItemStack(item,n));quantity-=n;}
        }
        if(!fits(p,outputs))throw new IllegalArgumentException("Make room in your inventory first.");
        busy=true;sendBusy();var service=FinanceService.get(p.server);
        service.ready().thenCompose(v->service.database.withdrawCash(p.getUUID(),account.id(),plan.amount(),currency,"Terminal cash withdrawal",expected))
            .whenComplete((result,error)->p.server.execute(()->{
                busy=false;
                if(error==null){invalidateWithdrawal();outputs.forEach(stack->deliver(p,stack));status="Withdrawal committed.";}
                else status=message(error);
                appendWithdrawalView(view);sendBusy();refresh();
            }));
    }
    private static boolean fits(Player p,List<ItemStack> items){
        var planned=new ArrayList<ItemStack>();for(int i=0;i<36;i++)planned.add(p.getInventory().getItem(i).copy());
        for(var source:items){int left=source.getCount();for(int pass=0;pass<2;pass++)for(int i=0;i<36&&left>0;i++){
            var stack=planned.get(i);if(pass==0&&!stack.isEmpty()&&ItemStack.isSameItemSameComponents(stack,source)){int add=Math.min(left,stack.getMaxStackSize()-stack.getCount());stack.grow(add);left-=add;}
            else if(pass==1&&stack.isEmpty()){int add=Math.min(left,source.getMaxStackSize());planned.set(i,source.copyWithCount(add));left-=add;}
        }if(left>0)return false;}return true;
    }
    private static void deliver(ServerPlayer p,ItemStack stack){if(stack.isEmpty())return;p.getInventory().add(stack);if(!stack.isEmpty())p.drop(stack,false);p.getInventory().setChanged();}
    private static String message(Throwable error){while(error.getCause()!=null)error=error.getCause();return error.getMessage()==null?"Transaction failed.":error.getMessage();}
    private void sendBusy(){view.addProperty("busy",busy);view.addProperty("status",status);send();}
    private void send(){if(!closed&&owner instanceof ServerPlayer p&&p.containerMenu==this)PacketDistributor.sendToPlayer(p,new FinanceSnapshot(containerId,view.toString()));}
    public void refresh(){
        if(closed||refreshing||!(owner instanceof ServerPlayer p)||!near(p))return;
        refreshing=true;var service=FinanceService.get(p.server);var setting=settings();Map<String,Long> nativeTotals;
        try{nativeTotals=totals();}catch(RuntimeException e){nativeTotals=Map.of();}
        final var amounts=nativeTotals;var detail=new ArrayList<String>();var itemCounts=new TreeMap<String,Integer>();
        for(int i=0;i<24;i++){var stack=deposit.getItem(i);if(!stack.isEmpty())itemCounts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),Math::addExact);}
        for(var e:itemCounts.entrySet()){var cash=CashCatalog.find(new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(e.getKey()))));if(cash!=null)detail.add(e.getValue()+" x "+e.getKey().replace("economy:","").replace('_',' ')+" = "+BankingConfig.format(Math.multiplyExact(cash.minor(),e.getValue()),cash.currency()));}
        long cursor=beforeSequence;String selectedAtStart=selected;int version=revision;
        service.ready().thenCompose(v->service.database.accounts(p.getUUID())).thenCompose(list->{
            var bank=list.stream().filter(a->a.kind().equals("BANK")).toList();
            String id=bank.stream().anyMatch(a->a.id().equals(selectedAtStart))?selectedAtStart:bank.stream().filter(FinanceDatabase.Account::primary).findFirst().orElseThrow().id();
            var chosen=bank.stream().filter(a->a.id().equals(id)).findFirst().orElseThrow();
            return service.database.displayHistory(p.getUUID(),id,cursor,15).thenCombine(service.database.exchangeRates(),(history,rates)->{
                var result=new JsonObject();result.add("accounts",JSON.toJsonTree(bank));result.addProperty("selected",id);var entries=new JsonArray();
                for(var entry:history){var row=JSON.toJsonTree(entry).getAsJsonObject();row.addProperty("displayMemo",HistoryPresentation.memo(entry,chosen.currency()));entries.add(row);}
                result.add("history",entries);result.add("rates",JSON.toJsonTree(rates));
                result.addProperty("accountCurrency",chosen.currency());return result;
            }).thenCombine((amounts.isEmpty()?CompletableFuture.completedFuture(0L):service.database.quoteDeposit(amounts,chosen.currency())),(result,total)->{result.addProperty("depositTotal",total);return result;});
        }).whenComplete((result,error)->p.server.execute(()->{
            refreshing=false;if(closed)return;if(version!=revision){refresh();return;}
            if(error!=null){status=message(error);sendBusy();return;}
            accounts=Arrays.asList(JSON.fromJson(result.get("accounts"),FinanceDatabase.Account[].class));selected=result.get("selected").getAsString();
            result.addProperty("page",page.name());result.addProperty("busy",busy);result.addProperty("status",status);result.addProperty("teller",teller);result.addProperty("canConfigure",TerminalSettings.canConfigure(p,pos));
            result.addProperty("mode",setting.mode().name());result.add("accepted",JSON.toJsonTree(setting.accepted()));result.add("dispensed",JSON.toJsonTree(setting.dispensedItems()));
            result.add("detail",JSON.toJsonTree(detail));result.add("subtotals",JSON.toJsonTree(amounts));
            var denoms=new JsonArray();for(var cash:CashCatalog.all())if((teller||!cash.coin())){
                var d=new JsonObject();d.addProperty("id",BuiltInRegistries.ITEM.getKey(cash.item()).toString());d.addProperty("currency",cash.currency().name());d.addProperty("minor",cash.minor());d.addProperty("coin",cash.coin());denoms.add(d);
            }result.add("denominations",denoms);appendWithdrawalView(result);view=result;send();
        }));
    }
    @Override public void broadcastChanges(){super.broadcastChanges();if(++ticks%20==1)refresh();}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(busy||!stillValid(p))return ItemStack.EMPTY;Slot slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var copy=stack.copy();
        if(index<24){if(!moveItemStackTo(stack,24,slots.size(),true))return ItemStack.EMPTY;}
        else if(page!=Page.DEPOSIT||!accepts(stack)||!moveItemStackTo(stack,0,24,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(p,stack);refresh();return copy;
    }
    @Override public void removed(Player p){closed=true;super.removed(p);if(!p.level().isClientSide)clearContainer(p,deposit);}
}
