package ca.emilybelnavis.mcmods.economy.client;

import ca.emilybelnavis.mcmods.economy.*;
import com.google.gson.*;
import java.math.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Page-based terminal UI; every monetary value is revalidated by the server. */
public final class BankingScreen extends AbstractContainerScreen<BankingMenu> {
    private static final String[] CURRENCIES = Arrays.stream(BankingConfig.Currency.values()).map(Enum::name).toArray(String[]::new);
    private String signature = "", cashCurrency = "CAD", openCurrency = "CAD", destination = "", amountText = "";
    private String draftMode = "BOTH";
    private final Set<String> accepted = new HashSet<>(), dispensed = new HashSet<>();
    private Button prepareButton, withdrawButton;
    private int denominationPage, detailPage, configPage, historyOffset;
    private boolean configLoaded, currencyInitialized;
    private EditBox amount;
    public BankingScreen(BankingMenu menu, Inventory inv, Component title) {
        super(menu, inv, title); imageWidth=316; imageHeight=236; inventoryLabelX=77; inventoryLabelY=143;
    }
    static String tr(String key,Object... args){return Component.translatable("finance.economy."+key,args).getString();}
    static String date(long timestamp){return HistoryPresentation.utcDate(timestamp);}
    private static String displayMemo(JsonObject entry){
        if(entry.has("displayMemo"))return str(entry,"displayMemo","");
        String memo=str(entry,"memo","");
        String operation=str(entry,"operation","");
        if(operation.equals("CASH_DEPOSIT"))return "Cash Deposit";
        if(operation.equals("CASH_WITHDRAWAL"))return "Cash Withdrawal";
        return memo.isBlank()?switch(operation){case "TRANSFER"->"Account Transfer";case "PAYMENT"->"Payment";case "TRADE"->"Stock Trade";default->"Account Transaction";}:memo;
    }
    static List<Component> historyTooltip(JsonObject entry,String currency){return List.of(
        Component.literal(date(number(entry,"timestamp"))),
        Component.literal(number(entry,"delta")<0?"Debit":"Credit"),
        Component.literal(money(Math.abs(number(entry,"delta")),currency)),
        Component.literal(tr("balance",money(number(entry,"balance"),currency))),
        Component.literal(displayMemo(entry).replace('\n',' ')));}
    static String str(JsonObject o,String key,String fallback) { return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():fallback; }
    static long number(JsonObject o,String key) { try{return o.has(key)?o.get(key).getAsLong():0;}catch(RuntimeException e){return 0;} }
    static boolean flag(JsonObject o,String key) { return o.has(key)&&o.get(key).getAsBoolean(); }
    static JsonArray array(JsonObject o,String key) { return o.has(key)&&o.get(key).isJsonArray()?o.getAsJsonArray(key):new JsonArray(); }
    static JsonObject object(JsonObject o,String key) { return o.has(key)&&o.get(key).isJsonObject()?o.getAsJsonObject(key):new JsonObject(); }
    static int scale(String code) { return code.equals("JPY")?1:100; }
    static String money(long value,String code) { return Money.format(value,code); }
    static String next(String current) { return CURRENCIES[(Arrays.asList(CURRENCIES).indexOf(current)+1)%CURRENCIES.length]; }
    private void send(String action,JsonObject args) { PacketDistributor.sendToServer(new FinanceAction(menu.containerId,action,args.toString())); }
    private void send(String action,String key,String value) { var args=new JsonObject();args.addProperty(key,value);send(action,args); }
    private Button button(String label,int x,int y,int w,int h,Runnable action,boolean sensitive) {
        Button b=addRenderableWidget(new FinanceButton(leftPos+x,topPos+y,w,h,Component.literal(label),ignored->action.run()));
        b.active=!sensitive||!flag(menu.view,"busy");return b;
    }
    private void page(BankingMenu.Page page) { send("page","page",page.name()); }
    @Override protected void init() { super.init(); build(); }
    @Override protected void containerTick() {
        super.containerTick();
        if (!currencyInitialized && menu.view.has("dispensed")) {
            String preferred = str(menu.view,"accountCurrency","CAD");
            String first = null;
            for (var entry : array(menu.view,"denominations")) {
                var denomination = entry.getAsJsonObject();
                if (contains(array(menu.view,"dispensed"),str(denomination,"id",""))) {
                    String code = str(denomination,"currency","CAD");
                    if (first == null) first = code;
                    if (code.equals(preferred)) { first = code; break; }
                }
            }
            cashCurrency = first == null ? preferred : first;
            currencyInitialized = true;
        }
        String page=str(menu.view,"page",menu.page.name());
        try{menu.page=BankingMenu.Page.valueOf(page);}catch(IllegalArgumentException ignored){}
        String current=page+str(menu.view,"selected","")+array(menu.view,"accounts").toString()+flag(menu.view,"busy")+flag(menu.view,"canConfigure")+array(menu.view,"denominations").size()+object(menu.view,"withdrawBills").toString()+object(menu.view,"withdrawCoins").toString()+object(menu.view,"withdrawAdjustments").toString()+flag(menu.view,"withdrawPrepared")+number(menu.view,"withdrawAmount")+number(menu.view,"withdrawCost")+str(menu.view,"withdrawCurrency","");
        if(!current.equals(signature)){signature=current;rebuildWidgets();}
        updateWithdrawalButtons();
    }
    private void build() {
        button(tr("home"),8,32,42,15,()->page(BankingMenu.Page.HOME),true);
        button(tr("deposit"),52,32,49,15,()->page(BankingMenu.Page.DEPOSIT),true);
        button(tr("withdraw"),103,32,58,15,()->page(BankingMenu.Page.WITHDRAW),true);
        button(tr("history"),163,32,47,15,()->page(BankingMenu.Page.HISTORY),true);
        if(menu.teller)button(tr("accounts"),212,32,56,15,()->page(BankingMenu.Page.ACCOUNTS),true);
        if(flag(menu.view,"canConfigure"))button(tr("setup"),270,32,38,15,()->page(BankingMenu.Page.CONFIG),true);
        var accounts=array(menu.view,"accounts");
        button(tr("account_selector",str(menu.view,"accountCurrency","...")),190,12,116,15,()->{
            if(accounts.isEmpty())return;int current=0;
            for(int i=0;i<accounts.size();i++)if(str(accounts.get(i).getAsJsonObject(),"id","").equals(str(menu.view,"selected","")))current=i;
            send("select","account",str(accounts.get((current+1)%accounts.size()).getAsJsonObject(),"id",""));
        },true);
        switch(menu.page){
            case DEPOSIT -> {
                button("<",128,111,20,13,()->{detailPage=Math.max(0,detailPage-1);},false);
                button(">",150,111,20,13,()->{detailPage=Math.min(Math.max(0,(array(menu.view,"detail").size()-1)/6),detailPage+1);},false);
                button(tr("deposit_cash"),188,110,118,15,()->send("deposit",new JsonObject()),true);
            }
            case WITHDRAW -> buildWithdrawal();
            case HISTORY -> {
                button(tr("newest"),165,126,66,15,()->{var a=new JsonObject();a.addProperty("before",0);historyOffset=0;send("history",a);},true);
                button(tr("older"),235,126,71,15,()->{var entries=array(menu.view,"history");if(entries.isEmpty())return;var a=new JsonObject();a.addProperty("before",number(entries.get(entries.size()-1).getAsJsonObject(),"sequence"));historyOffset=0;send("history",a);},true);
            }
            case ACCOUNTS -> buildAccounts();
            case CONFIG -> buildConfig();
            default -> {}
        }
    }
    private static String denominationName(JsonObject denomination){
        var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(str(denomination,"id","minecraft:air")));
        return new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
    }
    private List<JsonObject> denominations(boolean config) {
        var result=new ArrayList<JsonObject>();for(var e:array(menu.view,"denominations")){
            var d=e.getAsJsonObject();if(str(d,"currency","").equals(cashCurrency)&&(config||contains(array(menu.view,"dispensed"),str(d,"id",""))))result.add(d);
        }return result;
    }
    private static boolean contains(JsonArray array,String value){for(var e:array)if(e.getAsString().equals(value))return true;return false;}
    private void currencyCycle(){cashCurrency=next(cashCurrency);denominationPage=0;configPage=0;rebuildWidgets();}
    private long requestedCash(){return new BigDecimal(amountText).multiply(BigDecimal.valueOf(scale(cashCurrency))).longValueExact();}
    private boolean prepared(){
        try{return flag(menu.view,"withdrawPrepared")&&str(menu.view,"withdrawCurrency","").equals(cashCurrency)&&number(menu.view,"withdrawAmount")==requestedCash();}
        catch(RuntimeException error){return false;}
    }
    private void updateWithdrawalButtons(){
        if(menu.page!=BankingMenu.Page.WITHDRAW)return;
        boolean valid=false;try{valid=requestedCash()>0;}catch(RuntimeException ignored){}
        if(prepareButton!=null)prepareButton.active=valid&&!flag(menu.view,"busy");
        if(withdrawButton!=null)withdrawButton.active=prepared()&&!flag(menu.view,"busy");
    }
    private List<JsonObject> bills(){return denominations(false).stream().filter(d->!flag(d,"coin")).toList();}
    private void buildWithdrawal(){
        button(tr("cash_selector",cashCurrency),10,51,87,15,this::currencyCycle,true);
        amount=new FinanceEditBox(font,leftPos+101,topPos+51,98,15,Component.literal(tr("cash_amount")));
        amount.setHint(Component.literal(tr("amount_currency",cashCurrency)));amount.setMaxLength(24);amount.setValue(amountText);
        amount.setResponder(v->{amountText=v;updateWithdrawalButtons();});addRenderableWidget(amount);
        prepareButton=button(tr("prepare_cash"),203,51,102,15,()->{
            try{var args=new JsonObject();args.addProperty("amount",requestedCash());args.addProperty("currency",cashCurrency);send("prepare_withdraw",args);}catch(RuntimeException ignored){}
        },true);
        var list=bills();denominationPage=Math.min(denominationPage,Math.max(0,(list.size()-1)/3));
        for(int row=0;row<3&&denominationPage*3+row<list.size();row++){
            var d=list.get(denominationPage*3+row);String id=str(d,"id","");int y=82+row*13;
            var permissions=object(object(menu.view,"withdrawAdjustments"),id);
            for(int delta:new int[]{-1,1}){
                var control=button(delta<0?"−":"+",delta<0?234:284,y,21,12,()->{
                    var args=new JsonObject();args.addProperty("id",id);args.addProperty("delta",delta);send("adjust_withdraw",args);
                },true);
                control.active=prepared()&&!flag(menu.view,"busy")&&flag(permissions,delta<0?"minus":"plus");
            }
        }
        button("<",10,126,20,15,()->{denominationPage=Math.max(0,denominationPage-1);rebuildWidgets();},false);
        button(">",33,126,20,15,()->{denominationPage=Math.min(Math.max(0,(bills().size()-1)/3),denominationPage+1);rebuildWidgets();},false);
        withdrawButton=button(tr("withdraw"),211,126,94,15,()->send("withdraw",new JsonObject()),true);
        updateWithdrawalButtons();
    }
    private String coinSummary(){
        var values=new ArrayList<String>();for(var entry:object(menu.view,"withdrawCoins").entrySet()){
            long quantity=entry.getValue().getAsLong();if(quantity<=0)continue;
            for(var e:denominations(false))if(str(e,"id","").equals(entry.getKey()))values.add(quantity+" × "+money(number(e,"minor"),cashCurrency));
        }
        return values.isEmpty()?tr("auto_coins_none"):tr("auto_coins",String.join(", ",values));
    }
    private long convert(long value,String from,String to,RoundingMode rounding){
        var rates=object(menu.view,"rates");return BigDecimal.valueOf(value).multiply(new BigDecimal(str(rates,from,"1"))).multiply(BigDecimal.valueOf(scale(to)))
            .divide(BigDecimal.valueOf(scale(from)).multiply(new BigDecimal(str(rates,to,"1"))),0,rounding).longValueExact();
    }
    private List<JsonObject> otherAccounts(){var list=new ArrayList<JsonObject>();for(var e:array(menu.view,"accounts")){var a=e.getAsJsonObject();if(!str(a,"id","").equals(str(menu.view,"selected","")))list.add(a);}return list;}
    private void buildAccounts(){
        button(tr("new_currency",openCurrency),10,54,140,18,()->{openCurrency=next(openCurrency);rebuildWidgets();},true);
        button(tr("open_account"),158,54,147,18,()->send("open","currency",openCurrency),true);
        var others=otherAccounts();if(others.stream().noneMatch(a->str(a,"id","").equals(destination)))destination=others.isEmpty()?"":str(others.get(0),"id","");
        String code=others.stream().filter(a->str(a,"id","").equals(destination)).map(a->str(a,"currency","")).findFirst().orElse(tr("none"));
        button(tr("transfer_to",code),10,79,180,18,()->{for(int i=0;i<others.size();i++)if(str(others.get(i),"id","").equals(destination)){destination=str(others.get((i+1)%others.size()),"id","");break;}rebuildWidgets();},true);
        button(tr("close_selected"),196,79,109,18,()->send("close","destination",destination),true).active=!others.isEmpty()&&!flag(menu.view,"busy");
    }
    private void buildConfig(){
        if(!configLoaded){draftMode=str(menu.view,"mode","BOTH");accepted.clear();for(var e:array(menu.view,"accepted"))accepted.add(e.getAsString());dispensed.clear();for(var e:array(menu.view,"dispensed"))dispensed.add(e.getAsString());configLoaded=menu.view.has("mode");}
        button(tr("mode_selector",tr("mode."+draftMode.toLowerCase(Locale.ROOT))),10,50,295,13,()->{draftMode=switch(draftMode){case "BOTH"->"DEPOSIT";case "DEPOSIT"->"WITHDRAW";default->"BOTH";};rebuildWidgets();},true);
        for(int i=0;i<CURRENCIES.length;i++){String c=CURRENCIES[i];button((accepted.contains(c)?"[x] ":"[ ] ")+c,10+(i%5)*60,65+(i/5)*13,57,12,()->{if(!accepted.remove(c))accepted.add(c);rebuildWidgets();},true);}
        button(tr("dispense_currency",cashCurrency),10,93,222,13,this::currencyCycle,true);
        button("<",258,93,22,13,()->{configPage=Math.max(0,configPage-1);rebuildWidgets();},false);
        button(">",283,93,22,13,()->{configPage=Math.min(Math.max(0,(denominations(true).size()-1)/2),configPage+1);rebuildWidgets();},false);
        var list=denominations(true);for(int row=0;row<2&&configPage*2+row<list.size();row++){
            var d=list.get(configPage*2+row);String id=str(d,"id","");String label=(dispensed.contains(id)?"[x] ":"[ ] ")+money(number(d,"minor"),cashCurrency)+" "+denominationName(d);
            button(font.plainSubstrByWidth(label,226),10,109+row*14,235,13,()->{if(!dispensed.remove(id))dispensed.add(id);rebuildWidgets();},true);
        }
        button(tr("save"),250,126,55,15,()->{var args=new JsonObject();args.addProperty("mode",draftMode);var a=new JsonArray();accepted.stream().sorted().forEach(a::add);args.add("accepted",a);var d=new JsonArray();dispensed.stream().sorted().forEach(d::add);args.add("dispensed",d);send("configure",args);},true);
    }
    private String nativeSummary(){var values=new ArrayList<String>();for(var entry:object(menu.view,"subtotals").entrySet())values.add(money(entry.getValue().getAsLong(),entry.getKey()));return String.join(" + ",values);}
    private void line(GuiGraphics g,String text,int x,int y,int width){g.drawString(font,font.plainSubstrByWidth(text,width),x,y,0xd5e8e3,false);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){
        g.fill(leftPos,topPos,leftPos+316,topPos+236,0xff142326);g.fill(leftPos+4,topPos+4,leftPos+312,topPos+232,0xff101e23);g.fill(leftPos+8,topPos+8,leftPos+308,topPos+29,0xff125752);
        for(var slot:menu.slots)if(slot.isActive()){g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xff335b5a);g.fill(leftPos+slot.x,topPos+slot.y,leftPos+slot.x+16,topPos+slot.y+16,0xff192c31);}
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){
        g.drawString(font,font.plainSubstrByWidth(title.getString(),170),13,14,0xffffff,false);
        String currency=str(menu.view,"accountCurrency","CAD");long balance=0;for(var e:array(menu.view,"accounts"))if(str(e.getAsJsonObject(),"id","").equals(str(menu.view,"selected","")))balance=number(e.getAsJsonObject(),"balance");
        switch(menu.page){
            case HOME->{line(g,tr("balance",money(balance,currency)),12,56,292);line(g,tr("help_navigation"),12,74,292);line(g,menu.teller?tr("help_teller"):tr("help_atm"),12,91,292);line(g,tr("mode_label",tr("mode."+str(menu.view,"mode","BOTH").toLowerCase(Locale.ROOT))),12,108,292);line(g,str(menu.view,"status",""),12,127,292);}
            case DEPOSIT->{var details=array(menu.view,"detail");detailPage=Math.min(detailPage,Math.max(0,(details.size()-1)/6));for(int row=0;row<6&&detailPage*6+row<details.size();row++)line(g,details.get(detailPage*6+row).getAsString(),128,53+row*9,177);line(g,tr("subtotal",nativeSummary()),10,128,296);line(g,tr("deposit_total",money(number(menu.view,"depositTotal"),currency)),10,138,296);}
            case WITHDRAW->{
                line(g,prepared()?tr("cost",money(number(menu.view,"withdrawCost"),currency)):tr("prepare_hint"),10,70,295);
                var list=bills();for(int row=0;row<3&&denominationPage*3+row<list.size();row++){
                    var d=list.get(denominationPage*3+row);String id=str(d,"id","");
                    line(g,money(number(d,"minor"),cashCurrency)+" "+denominationName(d),10,84+row*13,219);
                    g.drawCenteredString(font,prepared()?Long.toString(number(object(menu.view,"withdrawBills"),id)):"—",269,84+row*13,0xd5e8e3);
                }
                line(g,prepared()?coinSummary():tr("prepare_first"),58,130,149);
            }
            case HISTORY->{
                var entries=array(menu.view,"history");historyOffset=Math.min(historyOffset,Math.max(0,entries.size()-6));
                for(int row=0;row<6&&historyOffset+row<entries.size();row++){
                    var t=entries.get(historyOffset+row).getAsJsonObject();
                    line(g,(number(t,"delta")<0?"− ":"+ ")+money(Math.abs(number(t,"delta")),currency)+" | "+displayMemo(t),10,53+row*12,286);
                }
                if(entries.isEmpty())line(g,tr("no_entries"),10,57,286);
                if(entries.size()>6){g.fill(300,53,303,125,0xff253e42);int thumb=72*6/entries.size();int thumbY=53+(72-thumb)*historyOffset/Math.max(1,entries.size()-6);g.fill(300,thumbY,303,thumbY+thumb,0xff55c8b9);}
                line(g,tr("history_scroll",entries.size()),10,130,150);
            }
            case ACCOUNTS->{line(g,tr("selected_balance",money(balance,currency)),10,105,295);try{for(var a:otherAccounts())if(str(a,"id","").equals(destination))line(g,tr("close_preview",money(convert(balance,currency,str(a,"currency","CAD"),RoundingMode.DOWN),str(a,"currency","CAD"))),10,118,295);}catch(RuntimeException ignored){}line(g,str(menu.view,"status",""),10,132,295);}
            case CONFIG->{}
        }
        g.drawString(font,Component.translatable("container.inventory"),245,154,0x91aaa7,false);
        String status=str(menu.view,"status","");
        if(!status.isBlank()){var lines=font.split(Component.literal(status),60);for(int i=0;i<Math.min(7,lines.size());i++)g.drawString(font,lines.get(i),10,154+i*9,0xd5e8e3,false);}
        if(flag(menu.view,"busy"))line(g,tr("processing"),10,234-9,62);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);renderTooltip(g,x,y);
        if(x>=leftPos+8&&x<leftPos+308&&y>=topPos+125&&y<topPos+143){var lines=new ArrayList<Component>();String status=str(menu.view,"status","");if(!status.isBlank())lines.add(Component.literal(status));if(menu.page==BankingMenu.Page.DEPOSIT){for(var e:object(menu.view,"subtotals").entrySet())lines.add(Component.literal(money(e.getValue().getAsLong(),e.getKey())+" → "+money(convert(e.getValue().getAsLong(),e.getKey(),str(menu.view,"accountCurrency","CAD"),RoundingMode.DOWN),str(menu.view,"accountCurrency","CAD"))));lines.add(Component.literal(tr("deposit_total",money(number(menu.view,"depositTotal"),str(menu.view,"accountCurrency","CAD")))));}if(!lines.isEmpty())g.renderComponentTooltip(font,lines,x,y);}
        if(menu.page==BankingMenu.Page.DEPOSIT&&x>=leftPos+128&&x<leftPos+306&&y>=topPos+53&&y<topPos+107){int row=(y-topPos-53)/9;var details=array(menu.view,"detail");int index=detailPage*6+row;if(index<details.size())g.renderComponentTooltip(font,List.of(Component.literal(details.get(index).getAsString())),x,y);}
        if(menu.page==BankingMenu.Page.WITHDRAW&&prepared()&&x>=leftPos+58&&x<leftPos+207&&y>=topPos+126&&y<topPos+143)g.renderComponentTooltip(font,List.of(Component.literal(coinSummary())),x,y);
        if(menu.page==BankingMenu.Page.HISTORY&&x>=leftPos+10&&x<leftPos+298&&y>=topPos+53&&y<topPos+125){int row=historyOffset+(y-topPos-53)/12;var entries=array(menu.view,"history");if(row<entries.size())g.renderComponentTooltip(font,historyTooltip(entries.get(row).getAsJsonObject(),str(menu.view,"accountCurrency","CAD")),x,y);}
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(menu.page==BankingMenu.Page.HISTORY&&x>=leftPos+8&&x<leftPos+308&&y>=topPos+53&&y<topPos+125){
            historyOffset=Math.max(0,Math.min(Math.max(0,array(menu.view,"history").size()-6),historyOffset-(int)Math.signum(dy)));return true;
        }
        return super.mouseScrolled(x,y,dx,dy);
    }

}
