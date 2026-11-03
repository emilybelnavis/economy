package ca.emilybelnavis.mcmods.economy.client;

import ca.emilybelnavis.mcmods.economy.*;
import com.google.gson.*;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import static ca.emilybelnavis.mcmods.economy.client.BankingScreen.*;

/** Searchable exchange terminal with separate bank funding and trading balances. */
public final class TradingScreen extends AbstractContainerScreen<TradingMenu> {
    private String stock="", bank="", query="", quantity="1", amount="", signature="", localStatus="";
    private int quotePage, orderPage;
    private EditBox search, quantityBox, amountBox;
    public TradingScreen(TradingMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=316;imageHeight=236;inventoryLabelY=1000;}
    private void send(String action,JsonObject args){PacketDistributor.sendToServer(new FinanceAction(menu.containerId,action,args.toString()));}
    private Button button(String label,int x,int y,int width,int height,Runnable action,boolean sensitive){
        var b=addRenderableWidget(Button.builder(Component.literal(label),ignored->action.run()).bounds(leftPos+x,topPos+y,width,height).build());b.active=!sensitive||!flag(menu.view,"busy");return b;
    }
    private EditBox edit(String label,String value,int x,int y,int width,java.util.function.Consumer<String> changed){
        var box=new EditBox(font,leftPos+x,topPos+y,width,16,Component.literal(label));box.setHint(Component.literal(label));box.setMaxLength(48);box.setValue(value);box.setResponder(changed);addRenderableWidget(box);return box;
    }
    @Override protected void init(){super.init();build();}
    @Override protected void containerTick(){super.containerTick();String next=array(menu.view,"accounts").toString()+array(menu.view,"quotes").toString()+flag(menu.view,"busy");if(!next.equals(signature)){signature=next;rebuildWidgets();}}
    private List<JsonObject> quotes(){var list=new ArrayList<JsonObject>();String q=query.toLowerCase(Locale.ROOT);for(var e:array(menu.view,"quotes")){var quote=e.getAsJsonObject();if((str(quote,"id","")+" "+str(quote,"name","")).toLowerCase(Locale.ROOT).contains(q))list.add(quote);}return list;}
    private JsonObject selected(){for(var e:array(menu.view,"quotes")){var q=e.getAsJsonObject();if(str(q,"id","").equals(stock))return q;}return new JsonObject();}
    private JsonObject account(){for(var e:array(menu.view,"accounts")){var a=e.getAsJsonObject();if(str(a,"id","").equals(bank))return a;}return new JsonObject();}
    private void build(){
        var accounts=array(menu.view,"accounts");if(!accounts.isEmpty()&&account().size()==0)bank=str(accounts.get(0).getAsJsonObject(),"id","");
        var filtered=quotes();if(stock.isEmpty()&&!filtered.isEmpty())stock=str(filtered.get(0),"id","");quotePage=Math.min(quotePage,Math.max(0,(filtered.size()-1)/3));
        search=edit(tr("search_stocks"),query,10,33,210,v->{query=v;quotePage=0;});
        button(tr("search"),225,33,48,16,()->{rebuildWidgets();setFocused(search);},false);
        button("↻",279,33,26,16,()->send("refresh",new JsonObject()),true);
        for(int row=0;row<3&&quotePage*3+row<filtered.size();row++){
            var quote=filtered.get(quotePage*3+row);String id=str(quote,"id","");String label=(id.equals(stock)?"> ":"")+str(quote,"name","");
            button(font.plainSubstrByWidth(label,222),10,54+row*14,230,13,()->{stock=id;rebuildWidgets();},true);
        }
        button("<",243,99,28,14,()->{quotePage=Math.max(0,quotePage-1);rebuildWidgets();},false);
        button(">",277,99,28,14,()->{quotePage=Math.min(Math.max(0,(quotes().size()-1)/3),quotePage+1);rebuildWidgets();},false);
        quantityBox=edit(tr("share_quantity"),quantity,10,132,65,v->quantity=v);
        button(tr("buy"),83,132,62,17,()->trade(true),true);button(tr("sell"),151,132,62,17,()->trade(false),true);
        button(tr("bank_selector",str(account(),"currency","...")),10,156,106,17,()->{
            if(accounts.isEmpty())return;int current=0;for(int i=0;i<accounts.size();i++)if(str(accounts.get(i).getAsJsonObject(),"id","").equals(bank))current=i;
            bank=str(accounts.get((current+1)%accounts.size()).getAsJsonObject(),"id","");rebuildWidgets();
        },true);
        amountBox=edit(tr("funding_amount"),amount,121,156,67,v->amount=v);
        button(tr("fund"),194,156,52,17,()->fund(true),true);button(tr("unfund"),251,156,54,17,()->fund(false),true);
        button("<",251,181,24,13,()->{orderPage=Math.max(0,orderPage-1);},false);
        button(">",280,181,25,13,()->{orderPage=Math.min(Math.max(0,(array(menu.view,"orders").size()-1)/3),orderPage+1);},false);
    }
    private void trade(boolean buy){
        try{long n=Long.parseLong(quantity);if(n<=0||stock.isEmpty())throw new IllegalArgumentException();var args=new JsonObject();args.addProperty("stock",stock);args.addProperty("quantity",n);send(buy?"buy":"sell",args);localStatus="";}catch(RuntimeException error){localStatus=tr("invalid_shares");}
    }
    private void fund(boolean fund){
        try{String source=fund?str(account(),"currency","CAD"):"CAD";long minor=new BigDecimal(amount).multiply(BigDecimal.valueOf(scale(source))).longValueExact();if(minor<=0||bank.isEmpty())throw new IllegalArgumentException();var args=new JsonObject();args.addProperty("account",bank);args.addProperty("amount",minor);send(fund?"fund":"unfund",args);localStatus="";}catch(RuntimeException error){localStatus=tr("invalid_funding");}
    }
    private void line(GuiGraphics g,String value,int x,int y,int width){g.drawString(font,font.plainSubstrByWidth(value,width),x,y,0x183b3a,false);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(leftPos,topPos,leftPos+316,topPos+236,0xff142326);g.fill(leftPos+4,topPos+4,leftPos+312,topPos+232,0xffe2e9e6);g.fill(leftPos+8,topPos+8,leftPos+308,topPos+28,0xff197d7a);}
    @Override protected void renderLabels(GuiGraphics g,int x,int y){
        g.drawString(font,font.plainSubstrByWidth(title.getString(),139),12,14,0xffffff,false);
        g.drawString(font,font.plainSubstrByWidth(tr("trading_balance",money(number(object(menu.view,"trading"),"balance"),"CAD")),150),157,14,0xffffff,false);
        var list=quotes();for(int row=0;row<3&&quotePage*3+row<list.size();row++)line(g,money(number(list.get(quotePage*3+row),"price"),"CAD"),244,57+row*14,61);
        line(g,tr("page",quotePage+1,Math.max(1,(list.size()+2)/3)),10,102,220);
        long held=0;for(var e:array(menu.view,"holdings"))if(str(e.getAsJsonObject(),"stock","").equals(stock))held=number(e.getAsJsonObject(),"quantity");
        line(g,tr("stock_summary",stock,money(number(selected(),"price"),"CAD"),held),10,118,295);
        try{line(g,money(Math.multiplyExact(Long.parseLong(quantity),number(selected(),"price")),"CAD"),219,136,86);}catch(RuntimeException ignored){}
        line(g,tr("funding_help",money(number(account(),"balance"),str(account(),"currency","CAD"))),10,176,295);
        line(g,tr("recent_orders"),10,188,232);
        var orders=array(menu.view,"orders");orderPage=Math.min(orderPage,Math.max(0,(orders.size()-1)/3));for(int row=0;row<3&&orderPage*3+row<orders.size();row++){
            var order=orders.get(orderPage*3+row).getAsJsonObject();line(g,"#"+number(order,"id")+" "+str(order,"side","")+" "+number(order,"quantity")+" "+str(order,"stock","")+" "+str(order,"status",""),10,199+row*10,295);
        }
        line(g,flag(menu.view,"busy")?tr("processing"):localStatus.isEmpty()?str(menu.view,"status",""):localStatus,10,225,295);
    }
    private List<Component> orderTooltip(JsonObject order){return List.of(
        Component.literal(tr("order",number(order,"id"))),
        Component.literal(tr("order_stock",str(order,"stock",""))),
        Component.literal(tr("order_side",str(order,"side",""))),
        Component.literal(tr("order_quantity",number(order,"quantity"))),
        Component.literal(tr("order_price",money(number(order,"price"),"CAD"))),
        Component.literal(tr("order_status",str(order,"status",""))));}
    @Override public void render(GuiGraphics g,int x,int y,float partial){super.render(g,x,y,partial);renderTooltip(g,x,y);
        if(x>=leftPos+8&&x<leftPos+308&&y>=topPos+225&&y<topPos+236){String text=localStatus.isEmpty()?str(menu.view,"status",""):localStatus;if(!text.isEmpty())g.renderComponentTooltip(font,List.of(Component.literal(text)),x,y);}
        if(x>=leftPos+10&&x<leftPos+306&&y>=topPos+199&&y<topPos+229){int row=(y-topPos-199)/10;var orders=array(menu.view,"orders");if(orderPage*3+row<orders.size())g.renderComponentTooltip(font,orderTooltip(orders.get(orderPage*3+row).getAsJsonObject()),x,y);}
    }
}
