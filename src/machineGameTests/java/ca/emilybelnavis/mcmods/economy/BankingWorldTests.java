package ca.emilybelnavis.mcmods.economy;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.function.*;
import com.mojang.authlib.GameProfile;
import ca.emilybelnavis.mcmods.economy.BankingConfig.Currency;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("economy")
@PrefixGameTestTemplate(false)
public final class BankingWorldTests {
    private static ItemStack cash(String id, int count) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("economy", id)), count);
    }
    private static FakePlayer player(GameTestHelper h, String name) {
        return new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }
    private static BlockPos terminal(GameTestHelper h, FakePlayer p, boolean teller) {
        BlockPos root=h.absolutePos(new BlockPos(6,2,6));
        var block=teller?TerminalVariants.BANK_TELLER.get():Economy.ATM.get();
        for(boolean right:new boolean[]{false,true})
            h.getLevel().setBlock(WideMachineLayout.cell(root,Direction.NORTH,right,false).below(),Blocks.STONE.defaultBlockState(),2);
        for(boolean right:new boolean[]{false,true})for(boolean upper:new boolean[]{false,true})
            h.getLevel().setBlock(WideMachineLayout.cell(root,Direction.NORTH,right,upper),block.defaultBlockState()
                .setValue(WideMachineBlock.FACING,Direction.NORTH).setValue(WideMachineBlock.RIGHT,right)
                .setValue(WideMachineBlock.HALF,upper?DoubleBlockHalf.UPPER:DoubleBlockHalf.LOWER),2);
        TerminalSettings.placed(h.getLevel(),root,p.getUUID());
        p.setPos(root.getX()+.5,root.getY(),root.getZ()-.5);
        p.getInventory().setItem(0,cash("harbor_bank_card",1));
        return root;
    }
    private static BankingMenu menu(FakePlayer p,BlockPos pos,boolean teller) {
        var menu=new BankingMenu(1,p.getInventory(),pos,teller,false);
        p.containerMenu=menu;menu.page=BankingMenu.Page.DEPOSIT;return menu;
    }
    private static void safely(GameTestHelper h,Runnable action) {
        try {action.run();}catch(Throwable error){h.fail(error.toString());}
    }
    private static <T> void after(GameTestHelper h,CompletionStage<T> future,Consumer<T> action) {
        future.whenComplete((value,error)->h.getLevel().getServer().execute(()->h.runAfterDelay(1,()->safely(h,()->{
            if(error!=null)h.fail(FinanceService.unwrap(error).toString());else action.accept(value);
        }))));
    }
    private static void until(GameTestHelper h,BooleanSupplier condition,int remaining,Runnable action) {
        safely(h,()->{
            if(condition.getAsBoolean())action.run();
            else if(remaining<=0)h.fail("Timed out waiting for asynchronous finance view");
            else h.runAfterDelay(1,()->until(h,condition,remaining-1,action));
        });
    }
    @GameTest(template="empty",timeoutTicks=30000)
    public static void cashTransactions(GameTestHelper h) {
        var p=player(h,"BankTest");var pos=terminal(h,p,false);var menu=menu(p,pos,false);
        var service=FinanceService.get(h.getLevel().getServer());
        after(h,service.ready(),ignored->{
            h.assertTrue(menu.slots.size()==60,"Expected 24 deposit and 36 player slots");
            h.assertTrue(!menu.getSlot(0).mayPlace(cash("cad_coin_100",1)),"ATM accepts coins");
            p.getInventory().setItem(1,cash("cad_coin_100",2));
            h.assertTrue(menu.quickMoveStack(p,52).isEmpty()&&p.getInventory().getItem(1).getCount()==2,"Shift-click moved ATM coins");
            menu.refresh();
            until(h,()->menu.view.has("accounts"),20000,()->{
                menu.getSlot(0).set(cash("usd_bill_10",2));menu.getSlot(1).set(cash("usd_bill_10",1));
                menu.getSlot(23).set(cash("cad_bill_5",2));
                long expected=BankingConfig.convert(3000,Currency.USD,Currency.CAD)+1000;
                menu.receive(new FinanceAction(1,"deposit","{}"));
                until(h,()->menu.view.has("status")&&menu.view.get("status").getAsString().startsWith("Deposit committed:"),20000,()->{
                    after(h,service.balance(p.getUUID()),balance->{
                        h.assertTrue(balance==expected,"Mixed multi-stack deposit credited wrong amount");
                        h.assertTrue(menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().isEmpty()&&menu.getSlot(23).getItem().isEmpty(),"Committed deposit retained cash");
                        after(h,service.history(p.getUUID()),history->{
                            h.assertTrue(history.size()==1,"Grouped deposit must produce one ledger entry");
                            menu.getSlot(0).set(cash("cad_bill_5",1));p.getInventory().setItem(0,ItemStack.EMPTY);
                            menu.receive(new FinanceAction(1,"deposit","{}"));
                            h.assertTrue(menu.getSlot(0).getItem().getCount()==1,"No-card request consumed cash");
                            p.getInventory().setItem(0,cash("harbor_bank_card",1));p.setPos(pos.getX()+20,pos.getY(),pos.getZ());
                            menu.receive(new FinanceAction(1,"deposit","{}"));
                            h.assertTrue(menu.getSlot(0).getItem().getCount()==1,"Remote request consumed cash");
                            p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()-.5);menu.removed(p);
                            h.assertTrue(p.getInventory().contains(cash("cad_bill_5",1)),"Closing lost unsubmitted grid cash");
                            after(h,service.balance(p.getUUID()),unchanged->{h.assertTrue(unchanged==expected,"Rejected request changed balance");h.succeed();});
                        });
                    });
                });
            });
        });
    }
    private static FinanceAction withdrawal(long amount,int fives,int tens) {
        return new FinanceAction(1,"withdraw","{\"amount\":"+amount+",\"items\":{\"economy:cad_bill_5_horizontal\":"+fives+",\"economy:cad_bill_10_horizontal\":"+tens+"}}");
    }
    private static int itemCount(FakePlayer p,String id) {
        int count=0;var item=cash(id,1).getItem();
        for(int i=0;i<36;i++)if(p.getInventory().getItem(i).is(item))count+=p.getInventory().getItem(i).getCount();
        return count;
    }
    private static void status(GameTestHelper h,BankingMenu menu,String text,Runnable next) {
        until(h,()->menu.view.has("status")&&menu.view.get("status").getAsString().contains(text)
            &&menu.view.has("busy")&&!menu.view.get("busy").getAsBoolean(),20000,next);
    }
    @GameTest(template="empty",timeoutTicks=30000)
    public static void withdrawalQuantitiesAndRejections(GameTestHelper h) {
        var p=player(h,"WithdrawTest");var pos=terminal(h,p,false);var menu=menu(p,pos,false);
        menu.page=BankingMenu.Page.WITHDRAW;
        var service=FinanceService.get(h.getLevel().getServer());
        after(h,service.credit(p.getUUID(),3000,"Withdrawal fixture"),ignored->{
            menu.refresh();
            until(h,()->menu.view.has("accounts"),20000,()->{
                String primary=menu.view.getAsJsonArray("accounts").asList().stream().map(e->e.getAsJsonObject())
                    .filter(a->a.get("primary").getAsBoolean()).findFirst().orElseThrow().get("id").getAsString();
                menu.receive(new FinanceAction(1,"select","{\"account\":\""+primary+"\"}"));
                until(h,()->menu.view.get("selected").getAsString().equals(primary),20000,()->{
                    menu.receive(withdrawal(2000,2,1));
                    status(h,menu,"Withdrawal committed.",()->after(h,service.balance(p.getUUID()),balance->{
                        h.assertTrue(balance==1000,"Selected account debit must be exactly CAD 20.00");
                        h.assertTrue(itemCount(p,"cad_bill_5_horizontal")==2&&itemCount(p,"cad_bill_10_horizontal")==1,"Withdrawal quantities were not delivered exactly");
                        menu.receive(withdrawal(501,1,0));
                        status(h,menu,"must match the withdrawal amount",()->{
                            h.assertTrue(itemCount(p,"cad_bill_5_horizontal")==2,"Mismatched amount delivered cash");
                            menu.receive(withdrawal(1500,3,0));
                            status(h,menu,"Insufficient funds",()->{
                                h.assertTrue(itemCount(p,"cad_bill_5_horizontal")==2,"Insufficient funds delivered cash");
                                // Leave room for only one of two requested bills: partial delivery must not occur.
                                for(int i=1;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
                                p.getInventory().setItem(1,cash("cad_bill_5_horizontal",63));
                                menu.receive(withdrawal(1000,2,0));
                                status(h,menu,"Make room",()->{
                                    h.assertTrue(itemCount(p,"cad_bill_5_horizontal")==63,"Capacity rejection partially delivered cash");
                                    p.getInventory().setItem(1,ItemStack.EMPTY);
                                    menu.receive(new FinanceAction(1,"withdraw","{\"amount\":100,\"items\":{\"economy:cad_coin_100\":1}}"));
                                    status(h,menu,"not dispensed here",()->{
                                        h.assertTrue(itemCount(p,"cad_coin_100")==0,"ATM dispensed a forbidden coin");
                                        var existing=TerminalSettings.get(h.getLevel(),pos);
                                        h.assertTrue(TerminalSettings.configure(p,pos,new TerminalSettings.Settings(p.getUUID(),TerminalSettings.Mode.DEPOSIT,existing.accepted(),existing.dispensedItems())),"Owner could not disable withdrawals");
                                        menu.receive(withdrawal(500,1,0));
                                        status(h,menu,"Withdrawals are disabled",()->after(h,service.balance(p.getUUID()),unchanged->{
                                            h.assertTrue(unchanged==1000,"Rejected withdrawals changed account balance");
                                            h.assertTrue(itemCount(p,"cad_bill_5_horizontal")==0,"Disabled withdrawal delivered bills");
                                            after(h,service.history(p.getUUID()),history->{h.assertTrue(history.size()==2,"Rejected withdrawals wrote ledger entries");menu.removed(p);h.succeed();});
                                        }));
                                    });
                                });
                            });
                        });
                    }));
                });
            });
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void tellerAndOwnerPermissions(GameTestHelper h) {
        var owner=player(h,"TellerOwner");var pos=terminal(h,owner,true);var menu=menu(owner,pos,true);
        h.assertTrue(menu.getSlot(23).mayPlace(cash("cad_coin_25",4)),"Teller rejects coin deposits");
        h.assertTrue(TerminalSettings.canConfigure(owner,pos),"Placer cannot configure terminal");
        h.assertTrue(TerminalSettings.configure(owner,pos,new TerminalSettings.Settings(owner.getUUID(),TerminalSettings.Mode.BOTH,Set.of(Currency.USD),Set.of("economy:usd_bill_10"))),"Owner whitelist update failed");
        h.assertTrue(menu.getSlot(0).mayPlace(cash("usd_bill_10",1))&&!menu.getSlot(0).mayPlace(cash("cad_bill_5",1)),"Accepted-currency whitelist ignored");
        var settings=new TerminalSettings.Settings(owner.getUUID(),TerminalSettings.Mode.WITHDRAW,Set.of(Currency.USD),Set.of("economy:usd_bill_10"));
        h.assertTrue(TerminalSettings.configure(owner,pos,settings),"Owner configuration failed");
        h.assertTrue(!menu.getSlot(0).mayPlace(cash("usd_bill_10",1)),"Withdrawal-only terminal accepts deposits");
        var other=player(h,"TellerStranger");
        h.assertTrue(!TerminalSettings.canConfigure(other,pos)&&!TerminalSettings.configure(other,pos,new TerminalSettings.Settings(other.getUUID(),TerminalSettings.Mode.BOTH,Set.of(Currency.CAD),Set.of())),"Nonowner configured terminal");
        h.assertTrue(TerminalSettings.get(h.getLevel(),pos).mode()==TerminalSettings.Mode.WITHDRAW,"Denied configuration changed mode");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void conversionsAndCatalog(GameTestHelper h) {
        var defs=FinanceDefinitions.cashItems();h.assertTrue(defs.size()==118,"Expected 118 cash definitions");
        var ids=new HashSet<String>();
        for(var element:defs){var d=element.getAsJsonObject();var id=d.get("id").getAsString();ids.add(id);
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id));
            var c=CashCatalog.find(new ItemStack(item));
            h.assertTrue(c!=null&&c.minor()==d.get("minor").getAsLong()&&c.currency().name().equals(d.get("currency").getAsString())&&c.coin()==d.get("coin").getAsBoolean(),"Cash definition mismatch: "+id);
        }
        h.assertTrue(ids.size()==118&&CashCatalog.all().size()==118,"Duplicate or missing registered cash");
        for(Currency c:Currency.values()){
            h.assertTrue(BankingConfig.convert(c.scale(),c,c)==c.scale(),"Same currency changed value");
            h.assertTrue(CashCatalog.withdrawals(c,false).stream().noneMatch(CashCatalog.Cash::coin),"ATM offers coins");
        }
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{
            var action=new FinanceAction(42,"deposit","{}");FinanceAction.CODEC.encode(buffer,action);
            h.assertTrue(action.equals(FinanceAction.CODEC.decode(buffer)),"Finance action codec changed request");
            buffer.clear();var snapshot=new FinanceSnapshot(42,"{\"status\":\"ready\",\"accounts\":[]}");FinanceSnapshot.CODEC.encode(buffer,snapshot);
            h.assertTrue(snapshot.equals(FinanceSnapshot.CODEC.decode(buffer)),"Finance snapshot codec changed response");
            buffer.clear();boolean rejected=false;
            try{FinanceAction.CODEC.encode(buffer,new FinanceAction(42,"deposit","x".repeat(16385)));}catch(RuntimeException expected){rejected=true;}
            h.assertTrue(rejected,"Oversized action was not bounded");
            buffer.clear();rejected=false;
            try{FinanceSnapshot.CODEC.encode(buffer,new FinanceSnapshot(42,"x".repeat(131073)));}catch(RuntimeException expected){rejected=true;}
            h.assertTrue(rejected,"Oversized snapshot was not bounded");
        }finally{buffer.release();}
        h.succeed();
    }
}
