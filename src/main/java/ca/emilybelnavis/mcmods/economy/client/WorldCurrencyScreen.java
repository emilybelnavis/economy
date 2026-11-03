package ca.emilybelnavis.mcmods.economy.client;

import ca.emilybelnavis.mcmods.economy.*;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Explicit first-world choice; ordinary close keys cannot bypass setup. */
public final class WorldCurrencyScreen extends AbstractContainerScreen<WorldCurrencyMenu> {
    private BankingConfig.Currency selected;
    public WorldCurrencyScreen(WorldCurrencyMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=316;imageHeight=236;}
    @Override protected void init(){
        super.init();
        int index=0;
        for(var currency:BankingConfig.Currency.values()){
            final var choice=currency;int n=index++;
            addRenderableWidget(new FinanceButton(leftPos+14+(n%2)*148,topPos+73+(n/2)*22,140,19,
                Component.literal((selected==choice?"✓ ":"")+choice.name()+"  "+Money.format(choice==BankingConfig.Currency.JPY?123:12300,choice.name())),button->{selected=choice;rebuildWidgets();}));
        }
        var confirm=addRenderableWidget(new FinanceButton(leftPos+67,topPos+190,182,23,Component.translatable("finance.economy.world_currency_confirm"),button->{
            if(selected==null||menu.busy)return;
            menu.busy=true;var args=new JsonObject();args.addProperty("currency",selected.name());
            PacketDistributor.sendToServer(new FinanceAction(menu.containerId,"world_select",args.toString()));
        }));
        confirm.active=selected!=null&&!menu.busy;
    }
    @Override public boolean shouldCloseOnEsc(){return false;}
    @Override public void onClose(){}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==256||minecraft.options.keyInventory.matches(key,scan))return true;
        return super.keyPressed(key,scan,modifiers);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){
        g.fill(leftPos,topPos,leftPos+316,topPos+236,0xff071316);
        g.fill(leftPos+1,topPos+1,leftPos+315,topPos+235,0xff14272a);
        g.fill(leftPos+8,topPos+8,leftPos+308,topPos+30,0xff167b75);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){
        g.drawString(font,title,14,15,0xffe4f4ef,false);
        g.drawWordWrap(font,menu.error.isBlank()?Component.translatable("finance.economy.world_currency_intro"):Component.literal(menu.error),14,39,288,menu.error.isBlank()?0xffd5e8e1:0xffffb6ab);
        g.drawString(font,Component.translatable("finance.economy.world_currency_warning"),14,218,0xffe5c585,false);
    }
}
