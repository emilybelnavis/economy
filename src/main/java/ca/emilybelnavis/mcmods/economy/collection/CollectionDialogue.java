package ca.emilybelnavis.mcmods.economy.collection;

import ca.emilybelnavis.mcmods.economy.Economy;
import ca.emilybelnavis.mcmods.economy.api.*;
import ca.emilybelnavis.mcmods.economy.api.Identifiers.*;
import ca.emilybelnavis.mcmods.economy.debt.*;
import ca.emilybelnavis.mcmods.economy.foundation.*;
import ca.emilybelnavis.mcmods.economy.journal.*;
import ca.emilybelnavis.mcmods.economy.banking.BankingSaves;
import ca.emilybelnavis.mcmods.economy.institution.InstitutionCatalog;
import net.minecraft.nbt.CompoundTag;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Server-owned case/funding choices and original payment intents for a live debtor dialogue. */
public final class CollectionDialogue {
    private static final String RECOVERY="economy_collection_payment";
    private static final Map<CollectionMenu,State> STATES=new WeakHashMap<>();
    private static final Map<MinecraftServer,Map<UUID,Pending>> PENDING=new WeakHashMap<>();
    private CollectionDialogue() {}
    private static final class State {
        final ServerServices services;
        final UUID serverSession;
        final Map<UUID,DebtRecords.Case> offeredCases=new LinkedHashMap<>();
        final Map<UUID,AccountIdentity> offeredFunding=new LinkedHashMap<>();
        UUID nextCase;
        String nextFunding;
        DebtRecords.Case selected;
        AccountIdentity funding;
        DebtPayments.Quote nativeQuote;
        DebtPayments.FxQuote fxQuote;
        State(ServerServices services){this.services=services;serverSession=services.sessionId();}
        void clearQuote(){nativeQuote=null;fxQuote=null;}
    }
    private static final class Pending {
        final UUID serverSession;
        final OperationId operation=new OperationId(UUID.randomUUID());
        final DebtPayments.Quote nativeQuote;
        final DebtPayments.FxQuote fxQuote;
        final EconomyCurrency currency;
        boolean inFlight,markerSaved;
        Pending(State state){serverSession=state.serverSession;nativeQuote=state.nativeQuote;fxQuote=state.fxQuote;currency=state.selected.currency();}
        long amount(){return nativeQuote!=null?nativeQuote.amountMinor():fxQuote.payment().amountMinor();}
    }
    private static final Map<MinecraftServer,Integer> RECOVERY_CURSORS=new IdentityHashMap<>();
    private static final Map<MinecraftServer,Set<UUID>> RECOVERY_READS=new IdentityHashMap<>();
    /** Resolve saved receipts even when full settlement removed the last representative. */
    public static void tick(MinecraftServer server) {
        var services=Economy.serverServices(server);var players=server.getPlayerList().getPlayers();
        if(services==null||services.state()!=ServerServices.State.READY||players.isEmpty())return;
        int cursor=RECOVERY_CURSORS.getOrDefault(server,0);RECOVERY_CURSORS.put(server,cursor+1);
        var player=players.get(Math.floorMod(cursor,players.size()));var pending=pending(player);
        if(player.containerMenu instanceof CollectionMenu||pending!=null&&pending.inFlight||!player.getPersistentData().contains(RECOVERY))return;
        var marker=player.getPersistentData().getCompound(RECOVERY);
        if(!marker.hasUUID("world")||!marker.hasUUID("operation")||!marker.getUUID("world").equals(services.worldId().value()))return;
        var reads=RECOVERY_READS.computeIfAbsent(server,s->new HashSet<>());if(!reads.add(player.getUUID()))return;
        var owner=new PartyRef(services.worldId(),new PartyRef.Player(new PlayerId(player.getUUID())));
        var granted=PartyAuthorization.player(player,Economy::serverServices,owner);
        if(granted.isEmpty()){reads.remove(player.getUUID());return;}
        var operation=new OperationId(marker.getUUID("operation"));
        services.ledger().lookupCollectionPaymentReceipt(granted.orElseThrow(),operation).whenComplete((found,error)->server.execute(()->{
            reads.remove(player.getUUID());
            if(error!=null||Economy.serverServices(server)!=services||server.getPlayerList().getPlayer(player.getUUID())!=player
                    ||found.isPresent()&&found.orElseThrow().status()==Journal.Status.UNKNOWN)return;
            var current=player.getPersistentData().getCompound(RECOVERY);
            if(!current.hasUUID("operation")||!current.getUUID("operation").equals(operation.value()))return;
            var book=PENDING.get(server);if(book!=null)book.remove(player.getUUID(),pending);
            clearMarker(player,operation);
            player.displayClientMessage(Component.literal(found.isPresent()&&found.orElseThrow().status()==Journal.Status.COMMITTED
                ?"Saved collection payment receipt recovered.":"Saved payment resolved. Review current balances before another payment."),false);
        }));
    }
    public static void open(ServerPlayer player,CollectionAgent entity){
        if(!CollectionAgents.canInteract(player,entity))return;
        var services=Economy.serverServices(player.server);if(services==null||services.state()!=ServerServices.State.READY)return;
        var holder=new CollectionMenu[1];UUID session=UUID.randomUUID();
        player.openMenu(new SimpleMenuProvider((id,inventory,p)->holder[0]=new CollectionMenu(id,inventory,entity.getUUID(),entity.presentation(),session),Component.literal("Waystone Collections")),b->holder[0].writeOpening(b));
        if(holder[0]==null)return;
        var state=new State(services);STATES.put(holder[0],state);
        var pending=pending(player);
        if(pending!=null&&pending.serverSession.equals(state.serverSession))pendingPage(holder[0],pending,"Recover the original payment before starting another.");
        else if(player.getPersistentData().contains(RECOVERY))recoverMarker(player,holder[0],state);
        else cases(player,holder[0],state,null);
    }
    public static void closed(CollectionMenu menu){STATES.remove(menu);}
    public static void stop(MinecraftServer server){RECOVERY_CURSORS.remove(server);RECOVERY_READS.remove(server);
        PENDING.remove(server);
        STATES.entrySet().removeIf(entry->entry.getKey().player() instanceof ServerPlayer player&&player.server==server);
    }
    private static Pending pending(ServerPlayer player){return PENDING.computeIfAbsent(player.server,server->new HashMap<>()).get(player.getUUID());}
    private static PartyAuthorization.Grant grant(ServerPlayer player,State state){
        if(Economy.serverServices(player.server)!=state.services||!state.services.sessionId().equals(state.serverSession))throw new IllegalArgumentException("Collection session changed");
        var world=state.services.worldId();if(world==null)throw new IllegalArgumentException("Collection world unavailable");
        return PartyAuthorization.player(player,Economy::serverServices,new PartyRef(world,new PartyRef.Player(new PlayerId(player.getUUID())))).orElseThrow();
    }
    private static boolean live(ServerPlayer player,CollectionMenu menu,State state){return STATES.get(menu)==state&&Economy.serverServices(player.server)==state.services&&state.services.sessionId().equals(state.serverSession)&&menu.valid(player);}
    public static void action(ServerPlayer player,CollectionMenu menu,String action,String value){
        var state=STATES.get(menu);if(state==null||!live(player,menu,state))return;
        if(action.equals("exit")){player.closeContainer();return;}
        var pending=pending(player);
        if(pending!=null){if(action.equals("retry"))submit(player,menu,state,pending);return;}
        try {
            switch(action){
                case "retry"->recoverMarker(player,menu,state);
                case "cases"->cases(player,menu,state,null);
                case "cases_next"->cases(player,menu,state,state.nextCase);
                case "funding"->funding(player,menu,state,null);
                case "funding_next"->funding(player,menu,state,state.nextFunding);
                case "select_case"->{state.selected=state.offeredCases.get(UUID.fromString(value));state.clearQuote();payment(menu,state,"Choose payment amount or full balance.");}
                case "select_funding"->{state.funding=state.offeredFunding.get(UUID.fromString(value));state.clearQuote();payment(menu,state,"Funding selected. Preview before confirming.");}
                case "preview_partial"->preview(player,menu,state,parseAmount(value,state.selected.currency()));
                case "preview_full"->preview(player,menu,state,state.selected.debtMinor());
                case "confirm"->confirm(player,menu,state);
                default->{}
            }
        }catch(RuntimeException unavailable){failure(menu,"Selection unavailable. Refresh cases and funding, then try again.");}
    }
    private static void cases(ServerPlayer player,CollectionMenu menu,State state,UUID after){
        state.clearQuote();menu.publish("cases",List.of(),"Loading eligible cases",true,List.of());
        state.services.ledger().collectionAgentState(grant(player,state),after,4).whenComplete((found,error)->player.server.execute(()->{
            if(!live(player,menu,state))return;
            if(error!=null||found.isEmpty()){failure(menu,"Cases unavailable while financial work is pending.");return;}
            if(menu.managed()&&(found.orElseThrow().state().phase()!=CollectionPresence.Phase.LIVE||!found.orElseThrow().state().lease().equals(menu.presentation()))){player.closeContainer();return;}
            state.nextCase=found.orElseThrow().nextCursor();state.offeredCases.clear();
            var lines=new ArrayList<String>();var choices=new ArrayList<CollectionNetwork.Choice>();long now=System.currentTimeMillis();
            for(var entry:found.orElseThrow().cases()){
                var debt=entry;if(!DebtLifecycle.demands(debt,now))continue;state.offeredCases.put(debt.id(),debt);
                lines.add(debt.kind()+" / "+creditor(debt.creditor()));lines.add("Remaining "+money(debt.currency(),debt.debtMinor()));
                choices.add(choice("select_case",debt.id().toString(),debt.kind()+" · "+money(debt.currency(),debt.debtMinor()),state.selected!=null&&state.selected.id().equals(debt.id())));
            }
            if(lines.isEmpty())lines.add("No eligible cases on this page.");
            if(state.nextCase!=null)choices.add(choice("cases_next","","Next cases",false));
            choices.add(choice("funding","","Funding accounts",false));
            menu.publish("cases",lines,"Select the balance to discuss.",false,choices);
        }));
    }
    private static void funding(ServerPlayer player,CollectionMenu menu,State state,String cursor){
        state.clearQuote();menu.publish("funding",List.of(),"Loading owned BANK funding",true,List.of());
        state.services.listOwnedAccounts(grant(player,state),cursor,4).whenComplete((found,error)->player.server.execute(()->{
            if(!live(player,menu,state))return;
            if(error!=null||found.isEmpty()){failure(menu,"Funding unavailable while financial work is pending.");return;}
            state.nextFunding=found.orElseThrow().nextCursor();state.offeredFunding.clear();var choices=new ArrayList<CollectionNetwork.Choice>();
            for(var account:found.orElseThrow().accounts())if(account.status()==AccountIdentity.Status.OPEN&&account.purpose()==AccountIdentity.Purpose.BANK){
                state.offeredFunding.put(account.id().value(),account);
                choices.add(choice("select_funding",account.id().value().toString(),account.currency().code()+" · "+(account.primary()?"Primary":"BANK")+" · "+account.id().value().toString().substring(0,8),state.funding!=null&&state.funding.id().equals(account.id())));
            }
            if(state.nextFunding!=null)choices.add(choice("funding_next","","Next funding accounts",false));
            choices.add(choice("cases","","Collection cases",false));
            menu.publish("funding",List.of("Choose an owned open BANK account.","A different currency requires an explicit FX review."),"No debit occurs when choosing funding.",false,choices);
        }));
    }
    private static void payment(CollectionMenu menu,State state,String status){
        var lines=new ArrayList<String>();var choices=new ArrayList<CollectionNetwork.Choice>();
        if(state.selected!=null){lines.add(state.selected.kind()+" / "+creditor(state.selected.creditor()));lines.add("Debt "+money(state.selected.currency(),state.selected.debtMinor()));}
        if(state.funding!=null)lines.add("Funding "+state.funding.currency().code()+" / "+state.funding.id().value().toString().substring(0,8));
        choices.add(choice("cases","","Choose case",false));choices.add(choice("funding","","Choose funding",false));
        if(state.selected!=null&&state.funding!=null){choices.add(choice("preview_partial","","Preview partial payment",false));choices.add(choice("preview_full","","Preview full balance",false));}
        menu.publish("payment",lines,status,false,choices);
    }
    public static long parseAmount(String text,EconomyCurrency currency){
        if(text==null||!text.matches("(?:0|[1-9][0-9]{0,18})(?:\\.[0-9]{1,2})?"))throw new IllegalArgumentException("Positive decimal amount required");
        long minor=new BigDecimal(text).multiply(BigDecimal.valueOf(currency.minorUnitsPerMajor())).longValueExact();
        if(minor<=0)throw new IllegalArgumentException("Positive representable amount required");return minor;
    }
    private static void preview(ServerPlayer player,CollectionMenu menu,State state,long amount){
        if(state.selected==null||state.funding==null||amount>state.selected.debtMinor()||!DebtLifecycle.demands(state.selected,System.currentTimeMillis()))throw new IllegalArgumentException("Collection selection stale");
        state.clearQuote();menu.publish("payment",List.of(),"Preparing payment review",true,List.of());
        if(state.funding.currency()==state.selected.currency()) {
            state.services.ledger().quoteCollectionPayment(grant(player,state),state.selected.id(),state.funding.id(),amount).whenComplete((found,error)->player.server.execute(()->{
                if(!live(player,menu,state))return;
                if(error!=null||found.isEmpty()){payment(menu,state,"Quote unavailable. Refresh case and funding.");return;}
                var quote=found.orElseThrow();
                if(quote.caseRevision()!=state.selected.revision()||quote.fundingRevision()!=state.funding.revision()){
                    state.selected=null;state.funding=null;cases(player,menu,state,null);return;
                }
                state.nativeQuote=quote;review(menu,state);
            }));
        }else {
            state.services.ledger().quoteCollectionPaymentFx(grant(player,state),state.selected.id(),state.funding.id(),amount).whenComplete((found,error)->player.server.execute(()->{
                if(!live(player,menu,state))return;
                if(error!=null){payment(menu,state,"FX quote unavailable. Refresh case and funding.");return;}
                if(found.payment().caseRevision()!=state.selected.revision()||found.payment().fundingRevision()!=state.funding.revision()){
                    state.selected=null;state.funding=null;cases(player,menu,state,null);return;
                }
                state.fxQuote=found;review(menu,state);
            }));
        }
    }
    private static long expires(State state){return state.nativeQuote!=null?state.nativeQuote.expiresAt():Math.min(state.fxQuote.payment().expiresAt(),state.fxQuote.fx().expires().toEpochMilli());}
    private static void review(CollectionMenu menu,State state){
        var lines=new ArrayList<String>();lines.add("Current creditor · "+creditor(state.selected.creditor()));
        if(state.nativeQuote!=null){lines.add("Pay "+money(state.selected.currency(),state.nativeQuote.amountMinor()));lines.add("Fee 0 "+state.selected.currency().code());}
        else {
            var fx=state.fxQuote.fx();lines.add("Debt receives "+money(state.selected.currency(),state.fxQuote.payment().amountMinor()));
            lines.add("Converted debit "+money(fx.debit().currency(),fx.debit().minorUnits()));lines.add("Separate FX fee "+money(fx.fee().currency(),fx.fee().minorUnits()));
            lines.add("Total funding debit "+money(fx.debit().currency(),Math.addExact(fx.debit().minorUnits(),fx.fee().minorUnits())));
        }
        lines.add("Quote expires "+java.time.Instant.ofEpochMilli(expires(state)));lines.add("Positive partial payment renews the collection clock.");
        lines.add("Full payment settles the selected case.");
        menu.publish("review",lines,"Review the exact debit, then confirm.",false,List.of(choice("confirm","","Confirm payment",false),choice("cases","","Choose case",false),choice("funding","","Choose funding",false)));
    }
    private static void confirm(ServerPlayer player,CollectionMenu menu,State state){
        if(state.nativeQuote==null&&state.fxQuote==null)throw new IllegalArgumentException("No reviewed quote");
        if(System.currentTimeMillis()>=expires(state)){state.clearQuote();payment(menu,state,"Quote expired. Preview again.");return;}
        var pendingByPlayer=PENDING.computeIfAbsent(player.server,server->new HashMap<>());
        if(pendingByPlayer.size()>=256&&!pendingByPlayer.containsKey(player.getUUID())){failure(menu,"Payment recovery is busy. Try again later.");return;}
        var pending=new Pending(state);pendingByPlayer.put(player.getUUID(),pending);state.clearQuote();submit(player,menu,state,pending);
    }
    private static void pendingPage(CollectionMenu menu,Pending pending,String status){
        menu.publish("pending",List.of("Original payment "+money(pending.currency,pending.amount()),"Closing this dialogue does not cancel a committed payment."),status,pending.inFlight,List.of(choice("retry","","Recover original payment",false)));
    }
    private static void submit(ServerPlayer player,CollectionMenu menu,State state,Pending pending){
        if(!pending.serverSession.equals(state.serverSession)){failure(menu,"Payment belongs to an earlier server session. Refresh current debt.");return;}
        if(pending.inFlight){pendingPage(menu,pending,"Original payment is still processing.");return;}
        pending.inFlight=true;pendingPage(menu,pending,"Submitting the original payment.");
        try {
            var grant=grant(player,state);
            if(!pending.markerSaved) {
                var marker=new CompoundTag();marker.putUUID("world",grant.target().world().value());marker.putUUID("operation",pending.operation.value());
                player.getPersistentData().put(RECOVERY,marker);
                BankingSaves.player(player,grant.target().world(),pending.operation.value());pending.markerSaved=true;
            }
            if(!live(player,menu,state)){pending.inFlight=false;return;}
            var attempt=new RequestId(UUID.randomUUID());
            var submission=pending.nativeQuote!=null?state.services.ledger().repayCollection(grant,pending.operation,attempt,pending.nativeQuote)
                :state.services.ledger().repayCollectionFx(grant,pending.operation,attempt,pending.fxQuote);
            submission.completion().whenComplete((delivery,error)->player.server.execute(()->{
                pending.inFlight=false;
                if(error!=null||delivery.outcome().status()==Journal.Status.UNKNOWN||delivery.outcome().status()==Journal.Status.NOT_ACCEPTED){
                    if(live(player,menu,state))pendingPage(menu,pending,"Outcome pending. Recover this original payment before another.");return;
                }
                var book=PENDING.get(player.server);if(book!=null)book.remove(player.getUUID(),pending);
                clearMarker(player,pending.operation);
                if(player.server.getPlayerList().getPlayer(player.getUUID())==player&&Economy.serverServices(player.server)==state.services)
                    player.displayClientMessage(Component.literal(delivery.outcome().status()==Journal.Status.COMMITTED
                        ?"Collection payment recorded · "+money(pending.currency,pending.amount())
                        :"Collection payment rejected. Refresh the balance before another preview."),false);
                CollectionAgents.refresh(player);
                if(!live(player,menu,state))return;
                if(delivery.outcome().status()==Journal.Status.COMMITTED)cases(player,menu,state,null);
                else payment(menu,state,"Payment rejected. Refresh balances and obtain a new preview.");
            }));
        }catch(RuntimeException unavailable){pending.inFlight=false;pendingPage(menu,pending,"Payment unavailable. Recover the original request when ready.");}
    }
    private static void recoverMarker(ServerPlayer player,CollectionMenu menu,State state) {
        var marker=player.getPersistentData().getCompound(RECOVERY);
        if(!marker.hasUUID("world")||!marker.hasUUID("operation")||!marker.getUUID("world").equals(state.services.worldId().value())) {
            menu.publish("pending",List.of("Saved payment evidence needs review."),"Payment recovery unavailable for this world.",false,List.of());return;
        }
        var operation=new OperationId(marker.getUUID("operation"));
        menu.publish("pending",List.of("Checking the original payment receipt."),"Waiting for authoritative recovery",true,List.of());
        state.services.ledger().lookupCollectionPaymentReceipt(grant(player,state),operation).whenComplete((found,error)->player.server.execute(()->{
            if(!live(player,menu,state))return;
            if(error!=null){menu.publish("pending",List.of("The saved original request remains protected."),"Receipt unavailable. Retry after financial catch-up.",false,List.of(choice("retry","","Recover original payment",false)));return;}
            if(found.isPresent()&&found.orElseThrow().status()==Journal.Status.UNKNOWN){menu.publish("pending",List.of("The original outcome is still uncertain."),"Recover the original payment before another.",false,List.of(choice("retry","","Recover original payment",false)));return;}
            clearMarker(player,operation);
            if(found.isPresent())player.displayClientMessage(Component.literal(found.orElseThrow().status()==Journal.Status.COMMITTED?"Previous collection payment recorded.":"Previous collection payment rejected."),false);
            else player.displayClientMessage(Component.literal("No committed original payment was found. Review the current balance before a new preview."),false);
            cases(player,menu,state,null);
        }));
    }
    private static void clearMarker(ServerPlayer player,OperationId operation) {
        var marker=player.getPersistentData().getCompound(RECOVERY);
        if(!marker.hasUUID("operation")||!marker.getUUID("operation").equals(operation.value()))return;
        player.getPersistentData().remove(RECOVERY);
        if(player.server.getPlayerList().getPlayer(player.getUUID())==player)player.server.getPlayerList().save(player);
    }
    private static String creditor(InstitutionCatalog.InstitutionId id){return InstitutionCatalog.find(id).map(InstitutionCatalog.Entry::name).orElse("Original lender");}
    private static CollectionNetwork.Choice choice(String action,String value,String label,boolean selected){return new CollectionNetwork.Choice(action,value,label,selected);}
    private static String money(EconomyCurrency currency,long amount){return CreditReport.money(currency,amount);}
    private static void failure(CollectionMenu menu,String reason){menu.publish("payment",List.of(),reason,false,List.of(choice("cases","","Refresh cases",false),choice("funding","","Funding accounts",false)));}
}
