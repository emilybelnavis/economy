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
import static ca.emilybelnavis.mcmods.economy.client.BankingScreen.*;

/** Scrollable stock list and selected-stock chart, with separate bank funding. */
public final class TradingScreen extends AbstractContainerScreen<TradingMenu> {
    private static final int VISIBLE_QUOTES=9,LIST_TOP=72,LIST_BOTTOM=189,ROW_HEIGHT=13;
    private static final int RATE_TOP=108,RATE_BOTTOM=188,VISIBLE_RATES=8;
    private static final String[] CURRENCIES=Arrays.stream(BankingConfig.Currency.values()).map(Enum::name).toArray(String[]::new);
    private String stock="",requestedStock="",bank="",query="",quantity="1",amount="",signature="",localStatus="";
    private String page="STOCKS",serverPage="",rateFrom="",rateTo="",serverPair="";
    private int quoteOffset,rateOffset;
    private boolean filterDirty;
    private EditBox search,quantityBox,amountBox;
    public TradingScreen(TradingMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=316;imageHeight=236;inventoryLabelY=1000;}
    private String currency(){return str(menu.view,"currency","CAD");}
    private void send(String action,JsonObject args){PacketDistributor.sendToServer(new FinanceAction(menu.containerId,action,args.toString()));}
    private Button button(String label,int x,int y,int width,int height,Runnable action,boolean sensitive){
        var control=addRenderableWidget(new FinanceButton(leftPos+x,topPos+y,width,height,Component.literal(label),ignored->action.run()));
        control.active=!sensitive||!flag(menu.view,"busy");return control;
    }
    private EditBox edit(String label,String value,int x,int y,int width,java.util.function.Consumer<String> changed){
        var box=new FinanceEditBox(font,leftPos+x,topPos+y,width,16,Component.literal(label));
        box.setHint(Component.literal(label));box.setMaxLength(48);box.setValue(value);box.setResponder(changed);box.setEditable(!flag(menu.view,"busy"));addRenderableWidget(box);return box;
    }
    @Override protected void init(){super.init();build();}
    @Override protected void containerTick(){
        super.containerTick();
        String incomingPage=str(menu.view,"page","STOCKS");
        if(!incomingPage.equals(serverPage)){serverPage=incomingPage;page=incomingPage;}
        String pair=str(menu.view,"rateFrom","")+":"+str(menu.view,"rateTo","");
        if(!pair.equals(serverPair)&&menu.view.has("rateFrom")&&menu.view.has("rateTo")){
            serverPair=pair;rateFrom=str(menu.view,"rateFrom",currency());rateTo=str(menu.view,"rateTo",currency().equals("USD")?"CAD":"USD");
        }
        String next=page+pair+array(menu.view,"rateHistory").toString()+str(menu.view,"rateCurrent","")+flag(menu.view,"rateHasMore")+str(menu.view,"rateBefore","")+array(menu.view,"accounts").toString()+array(menu.view,"quotes").toString()+flag(menu.view,"busy")+currency()+str(menu.view,"selectedStock","")+array(menu.view,"chart").toString()+array(menu.view,"orders").toString();
        if(!next.equals(signature)||filterDirty){
            boolean searching=search!=null&&search.isFocused();int cursor=searching?search.getCursorPosition():0;
            signature=next;filterDirty=false;rebuildWidgets();
            if(searching&&page.equals("STOCKS")){setFocused(search);search.setCursorPosition(Math.min(cursor,query.length()));}
        }
        if(page.equals("STOCKS")&&!stock.isEmpty()&&!stock.equals(requestedStock)&&!flag(menu.view,"busy"))requestStock(stock);
    }
    private void requestStock(String id){requestedStock=id;var args=new JsonObject();args.addProperty("stock",id);send("stock",args);}
    private List<JsonObject> quotes(){
        var list=new ArrayList<JsonObject>();String q=query.toLowerCase(Locale.ROOT);
        for(var e:array(menu.view,"quotes")){var quote=e.getAsJsonObject();if((str(quote,"id","")+" "+str(quote,"name","")).toLowerCase(Locale.ROOT).contains(q))list.add(quote);}
        return list;
    }
    private JsonObject selected(){for(var e:array(menu.view,"quotes")){var q=e.getAsJsonObject();if(str(q,"id","").equals(stock))return q;}return new JsonObject();}
    private JsonObject account(){for(var e:array(menu.view,"accounts")){var a=e.getAsJsonObject();if(str(a,"id","").equals(bank))return a;}return new JsonObject();}
    private long held(){for(var e:array(menu.view,"holdings"))if(str(e.getAsJsonObject(),"stock","").equals(stock))return number(e.getAsJsonObject(),"quantity");return 0;}
    private void switchPage(String next){page=next;localStatus="";var args=new JsonObject();args.addProperty("page",next);send("page",args);rebuildWidgets();}
    private void build(){
        search=null;quantityBox=null;amountBox=null;
        button(tr("stocks_tab"),10,31,143,16,()->switchPage("STOCKS"),true).active=!page.equals("STOCKS")&&!flag(menu.view,"busy");
        button(tr("rates_tab"),158,31,147,16,()->switchPage("RATES"),true).active=!page.equals("RATES")&&!flag(menu.view,"busy");
        if(page.equals("RATES")){buildRates();return;}
        var accounts=array(menu.view,"accounts");if(!accounts.isEmpty()&&account().size()==0)bank=str(accounts.get(0).getAsJsonObject(),"id","");
        var filtered=quotes();if(stock.isEmpty()&&!filtered.isEmpty())stock=str(filtered.get(0),"id","");
        quoteOffset=Math.min(quoteOffset,Math.max(0,filtered.size()-VISIBLE_QUOTES));
        search=edit(tr("search_stocks"),query,10,51,262,v->{query=v;quoteOffset=0;filterDirty=true;});
        button("↻",279,51,26,16,()->{requestedStock="";send("refresh",new JsonObject());},true).setTooltip(Tooltip.create(Component.literal(tr("refresh"))));
        for(int row=0;row<VISIBLE_QUOTES&&quoteOffset+row<filtered.size();row++){
            var quote=filtered.get(quoteOffset+row);String id=str(quote,"id","");
            String label=(id.equals(stock)?"› ":"")+str(quote,"name","");
            var control=button(font.plainSubstrByWidth(label,119),10,LIST_TOP+row*ROW_HEIGHT,129,12,()->{stock=id;localStatus="";requestedStock="";rebuildWidgets();},true);
            control.setTooltip(Tooltip.create(Component.literal(str(quote,"name","")+" · "+money(number(quote,"price"),currency()))));
        }
        var orderLines=new ArrayList<String>();orderLines.add(tr("recent_orders"));
        for(var entry:array(menu.view,"orders")){
            var order=entry.getAsJsonObject();orderLines.add("#"+number(order,"id")+" "+str(order,"side","")+" "+number(order,"quantity")+" "+str(order,"stock","")+" · "+money(number(order,"price"),str(order,"currency",currency()))+" · "+str(order,"status",""));
        }
        if(orderLines.size()==1)orderLines.add(tr("no_entries"));
        button(tr("orders"),250,149,55,12,()->localStatus=String.join(" | ",orderLines),false).setTooltip(Tooltip.create(Component.literal(String.join("\n",orderLines))));
        quantityBox=edit(tr("share_quantity"),quantity,150,162,45,v->quantity=v);
        button(tr("buy"),200,162,49,16,()->trade(true),true).active=!stock.isEmpty()&&!flag(menu.view,"busy");
        button(tr("sell"),254,162,51,16,()->trade(false),true).active=!stock.isEmpty()&&!flag(menu.view,"busy");
        button(tr("bank_selector",str(account(),"currency","...")),10,197,91,16,()->{
            if(accounts.isEmpty())return;int current=0;for(int i=0;i<accounts.size();i++)if(str(accounts.get(i).getAsJsonObject(),"id","").equals(bank))current=i;
            bank=str(accounts.get((current+1)%accounts.size()).getAsJsonObject(),"id","");rebuildWidgets();
        },true);
        amountBox=edit(tr("amount_short"),amount,105,197,83,v->amount=v);
        amountBox.setTooltip(Tooltip.create(Component.literal(tr("funding_units",str(account(),"currency",currency()),currency()))));
        button(tr("fund"),194,197,52,16,()->fund(true),true);
        button(tr("unfund"),251,197,54,16,()->fund(false),true);
    }
    private void trade(boolean buy){
        try{long n=Long.parseLong(quantity);if(n<=0||stock.isEmpty())throw new IllegalArgumentException();var args=new JsonObject();args.addProperty("stock",stock);args.addProperty("quantity",n);send(buy?"buy":"sell",args);localStatus="";}catch(RuntimeException error){localStatus=tr("invalid_shares");}
    }
    private void fund(boolean fund){
        try{String source=fund?str(account(),"currency",currency()):currency();long minor=new BigDecimal(amount).multiply(BigDecimal.valueOf(scale(source))).longValueExact();if(minor<=0||bank.isEmpty())throw new IllegalArgumentException();var args=new JsonObject();args.addProperty("account",bank);args.addProperty("amount",minor);send(fund?"fund":"unfund",args);localStatus="";}catch(RuntimeException error){localStatus=tr("invalid_funding");}
    }
    private void line(GuiGraphics g,String value,int x,int y,int width){g.drawString(font,font.plainSubstrByWidth(value,width),x,y,0xd5e8e3,false);}
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){
        g.fill(leftPos,topPos,leftPos+316,topPos+236,0xff142326);g.fill(leftPos+4,topPos+4,leftPos+312,topPos+232,0xff101e23);g.fill(leftPos+8,topPos+8,leftPos+308,topPos+28,0xff125752);
        if(page.equals("STOCKS")){
            g.fill(leftPos+145,topPos+LIST_TOP,leftPos+146,topPos+LIST_BOTTOM,0xff335b5a);
            g.fill(leftPos+10,topPos+193,leftPos+306,topPos+194,0xff335b5a);
        }
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){
        g.drawString(font,font.plainSubstrByWidth(title.getString(),130),12,14,0xffffff,false);
        g.drawString(font,font.plainSubstrByWidth(tr("trading_balance",money(number(object(menu.view,"trading"),"balance"),currency())),160),145,14,0xffffff,false);
        if(page.equals("RATES")){renderRates(g);renderStatus(g);return;}
        var list=quotes();
        if(list.isEmpty())line(g,tr("no_stocks"),12,76,126);
        if(list.size()>VISIBLE_QUOTES){int thumb=Math.max(12,117*VISIBLE_QUOTES/list.size());int yy=LIST_TOP+(117-thumb)*quoteOffset/Math.max(1,list.size()-VISIBLE_QUOTES);g.fill(141,LIST_TOP,143,LIST_BOTTOM,0xff253e42);g.fill(141,yy,143,yy+thumb,0xff55c8b9);}
        if(!stock.isEmpty()){
            line(g,str(selected(),"name",tr("select_stock")),150,72,155);
            line(g,tr("share_price",money(number(selected(),"price"),currency())),150,84,155);
            chart(g);
            line(g,tr("shares_held",held()),150,151,95);
            try{line(g,tr("trade_total",money(Math.multiplyExact(Long.parseLong(quantity),number(selected(),"price")),currency())),150,181,155);}catch(RuntimeException ignored){}
        }else line(g,tr("select_stock"),150,80,155);
        line(g,tr("funding_help",money(number(account(),"balance"),str(account(),"currency",currency())),currency()),10,215,295);
        renderStatus(g);
    }
    private void renderStatus(GuiGraphics g){line(g,flag(menu.view,"busy")?tr("processing"):localStatus.isEmpty()?str(menu.view,"status",""):localStatus,10,225,295);}
    private String nextCurrency(String current,String other){
        int index=Arrays.asList(CURRENCIES).indexOf(current);
        for(int n=1;n<=CURRENCIES.length;n++){String candidate=CURRENCIES[(Math.max(0,index)+n)%CURRENCIES.length];if(!candidate.equals(other))return candidate;}
        return current;
    }
    private void requestPair(){rateOffset=0;localStatus="";var args=new JsonObject();args.addProperty("from",rateFrom);args.addProperty("to",rateTo);send("rates_pair",args);rebuildWidgets();}
    private void requestHistory(String before){rateOffset=0;var args=new JsonObject();args.addProperty("before",before);send("rates_history",args);}
    private List<JsonObject> rateRows(){
        var rows=new ArrayList<JsonObject>();
        if(!str(menu.view,"rateFrom","").equals(rateFrom)||!str(menu.view,"rateTo","").equals(rateTo))return rows;
        for(var e:array(menu.view,"rateHistory"))rows.add(e.getAsJsonObject());
        rows.sort(Comparator.comparing((JsonObject row)->str(row,"date","")).reversed());return rows;
    }
    private static String ratio(String value){try{return new BigDecimal(value).stripTrailingZeros().toPlainString();}catch(RuntimeException ignored){return "—";}}
    private String currentRate(){
        String current=str(menu.view,"rateCurrent","");if(!current.isBlank())return current;
        var rates=object(menu.view,"rates");
        try{return new BigDecimal(str(rates,rateFrom,"")).divide(new BigDecimal(str(rates,rateTo,"")),12,RoundingMode.HALF_UP).toPlainString();}
        catch(RuntimeException ignored){return "";}
    }
    private void buildRates(){
        if(rateFrom.isEmpty())rateFrom=currency();if(rateTo.isEmpty()||rateTo.equals(rateFrom))rateTo=rateFrom.equals("USD")?"CAD":"USD";
        button(tr("rate_from",rateFrom),10,53,143,16,()->{rateFrom=nextCurrency(rateFrom,rateTo);requestPair();},true);
        button(tr("rate_to",rateTo),158,53,147,16,()->{rateTo=nextCurrency(rateTo,rateFrom);requestPair();},true);
        button(tr("newest"),165,199,66,16,()->requestHistory(""),true);
        var rows=rateRows();String fallback=rows.isEmpty()?"":str(rows.getLast(),"date","");
        String cursor=str(menu.view,"rateBefore",fallback);String before=cursor.isBlank()?fallback:cursor;
        button(tr("older"),236,199,69,16,()->requestHistory(before),true).active=!rows.isEmpty()&&flag(menu.view,"rateHasMore")&&!flag(menu.view,"busy");
    }
    private void renderRates(GuiGraphics g){
        var rows=rateRows();boolean matches=str(menu.view,"rateFrom","").equals(rateFrom)&&str(menu.view,"rateTo","").equals(rateTo);
        String current=matches?currentRate():"";
        if(!current.isBlank())line(g,tr("rate_current",rateFrom,ratio(current),rateTo),10,74,295);
        else if(!rows.isEmpty())line(g,tr("rate_latest",rateFrom,ratio(str(rows.getFirst(),"rate","")),rateTo),10,74,295);
        else line(g,matches?tr("rates_empty"):tr("rates_loading"),10,74,295);
        line(g,tr("rates_offline"),10,87,295);
        line(g,tr("rate_date"),12,99,116);line(g,tr("rate_column",rateTo,rateFrom),139,99,156);
        rateOffset=Math.min(rateOffset,Math.max(0,rows.size()-VISIBLE_RATES));
        for(int row=0;row<VISIBLE_RATES&&rateOffset+row<rows.size();row++){
            int y=RATE_TOP+row*10;var entry=rows.get(rateOffset+row);
            if(row%2==0)g.fill(10,y-1,298,y+9,0xff172b30);
            line(g,str(entry,"date",""),12,y,118);line(g,ratio(str(entry,"rate","")),139,y,155);
        }
        if(rows.isEmpty())line(g,matches?tr("rates_no_dates"):tr("rates_loading"),12,RATE_TOP+8,283);
        if(rows.size()>VISIBLE_RATES){int thumb=80*VISIBLE_RATES/rows.size(),yy=RATE_TOP+(80-thumb)*rateOffset/Math.max(1,rows.size()-VISIBLE_RATES);g.fill(301,RATE_TOP,304,RATE_BOTTOM,0xff253e42);g.fill(301,yy,304,yy+thumb,0xff55c8b9);}
        line(g,tr("rates_recorded_only"),10,189,295);
        line(g,tr("history_scroll",rows.size()),10,202,150);
    }
    private List<JsonObject> chartPoints(){
        var points=new ArrayList<JsonObject>();if(!str(menu.view,"selectedStock","").equals(stock))return points;
        for(var e:array(menu.view,"chart"))points.add(e.getAsJsonObject());points.sort(Comparator.comparingLong(p->number(p,"day")));
        if(points.isEmpty())return points;
        long end=Math.max(number(menu.view,"currentDay"),number(points.getLast(),"day"));return points.stream().filter(p->number(p,"day")>=end-13&&number(p,"day")<=end).toList();
    }
    private void chart(GuiGraphics g){
        final int x=150,y=97,w=155,h=39;g.fill(x,y,x+w,y+h,0xff091519);
        g.fill(x,y+h-1,x+w,y+h,0xff3a6261);g.fill(x,y,x+1,y+h,0xff3a6261);
        var points=chartPoints();
        if(points.isEmpty()){line(g,str(menu.view,"selectedStock","").equals(stock)?tr("chart_empty"):tr("chart_loading"),x+5,y+15,w-10);return;}
        long end=Math.max(number(menu.view,"currentDay"),number(points.getLast(),"day")),start=Math.max(0,end-13);
        long min=points.stream().mapToLong(p->number(p,"price")).min().orElse(0),max=points.stream().mapToLong(p->number(p,"price")).max().orElse(min);
        int previousX=0,previousY=0;long previousDay=Long.MIN_VALUE;
        for(var point:points){long day=number(point,"day"),price=number(point,"price");
            int px=x+4+(int)((day-start)*(w-9)/Math.max(1,end-start));
            int py=max==min?y+h/2:y+h-7-(int)(((double)price-min)/(max-min)*(h-14));
            if(previousDay==day-1)segment(g,previousX,previousY,px,py,0xff55d5bf);
            g.fill(px-1,py-1,px+2,py+2,0xffa3ffdf);previousX=px;previousY=py;previousDay=day;
        }
        String upper=font.plainSubstrByWidth(money(max,currency()),w-6),lower=font.plainSubstrByWidth(money(min,currency()),w-6);
        g.drawString(font,upper,x+w-font.width(upper)-3,y+3,0x8ab5ad,false);
        if(max!=min)g.drawString(font,lower,x+w-font.width(lower)-3,y+h-11,0x8ab5ad,false);
        line(g,tr("chart_days",start,end),x,138,w);
    }
    private static void segment(GuiGraphics g,int x0,int y0,int x1,int y1,int color){
        int dx=Math.abs(x1-x0),sx=x0<x1?1:-1,dy=-Math.abs(y1-y0),sy=y0<y1?1:-1,error=dx+dy;
        while(true){g.fill(x0,y0,x0+1,y0+1,color);if(x0==x1&&y0==y1)break;int e=2*error;if(e>=dy){error+=dy;x0+=sx;}if(e<=dx){error+=dx;y0+=sy;}}
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(page.equals("RATES")&&x>=leftPos+10&&x<leftPos+306&&y>=topPos+RATE_TOP&&y<topPos+RATE_BOTTOM){
            rateOffset=Math.max(0,Math.min(Math.max(0,rateRows().size()-VISIBLE_RATES),rateOffset-(int)Math.signum(dy)));return true;
        }
        if(page.equals("STOCKS")&&x>=leftPos+10&&x<leftPos+144&&y>=topPos+LIST_TOP&&y<topPos+LIST_BOTTOM){
            quoteOffset=Math.max(0,Math.min(Math.max(0,quotes().size()-VISIBLE_QUOTES),quoteOffset-(int)Math.signum(dy)));rebuildWidgets();return true;
        }
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override public void render(GuiGraphics g,int x,int y,float partial){
        super.render(g,x,y,partial);renderTooltip(g,x,y);
        if(x>=leftPos+8&&x<leftPos+308&&y>=topPos+225&&y<topPos+236){String text=localStatus.isEmpty()?str(menu.view,"status",""):localStatus;if(!text.isEmpty())g.renderComponentTooltip(font,List.of(Component.literal(text)),x,y);}
        if(page.equals("RATES")&&x>=leftPos+10&&x<leftPos+298&&y>=topPos+RATE_TOP&&y<topPos+RATE_BOTTOM){
            int index=rateOffset+(y-topPos-RATE_TOP)/10;var rows=rateRows();if(index<rows.size()){
                var row=rows.get(index);g.renderComponentTooltip(font,List.of(Component.literal(str(row,"date","")),Component.literal("1 "+rateFrom+" = "+ratio(str(row,"rate",""))+" "+rateTo)),x,y);
            }
        }
        if(page.equals("STOCKS")&&x>=leftPos+150&&x<leftPos+305&&y>=topPos+97&&y<topPos+136){
            var points=chartPoints();if(!points.isEmpty()){
                long end=Math.max(number(menu.view,"currentDay"),number(points.getLast(),"day")),start=Math.max(0,end-13);long target=start+Math.round((x-leftPos-154)*(double)Math.max(1,end-start)/146);
                JsonObject nearest=points.stream().min(Comparator.comparingLong(p->Math.abs(number(p,"day")-target))).orElseThrow();
                g.renderComponentTooltip(font,List.of(Component.literal(str(selected(),"name","")),Component.literal(tr("chart_day",number(nearest,"day"))),Component.literal(money(number(nearest,"price"),currency()))),x,y);
            }
        }
    }
}
