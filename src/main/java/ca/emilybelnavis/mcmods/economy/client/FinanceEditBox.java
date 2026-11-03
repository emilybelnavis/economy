package ca.emilybelnavis.mcmods.economy.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Native editing and narration inside a clipped terminal input. */
public final class FinanceEditBox extends EditBox {
    private static final int INSET=4;
    public FinanceEditBox(Font font,int x,int y,int width,int height,Component label) {
        super(font,x,y,width,height,label);
        setBordered(false);setTextColor(0xffe4f4ef);setTextColorUneditable(0xff728b8c);
    }
    @Override public int getInnerWidth(){return Math.max(0,super.getInnerWidth()-INSET*2);}
    @Override public void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        if(!isVisible())return;
        int x=getX(),y=getY(),width=getWidth(),height=getHeight();
        graphics.fill(x,y,x+width,y+height,isFocused()?0xff79e3d3:0xff355e5e);
        graphics.fill(x+1,y+1,x+width-1,y+height-1,0xff081417);
        // Native unbordered EditBox does not clip hints/suggestions to its inner width.
        // The pose adds padding without changing layout or focus/hit-test bounds.
        graphics.enableScissor(x+INSET,y+1,x+width-INSET,y+height-1);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(INSET,(height-8)/2,0);
            super.renderWidget(graphics,mouseX,mouseY,partialTick);
        } finally {
            graphics.pose().popPose();graphics.disableScissor();
        }
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button) {
        if(!isMouseOver(mouseX,mouseY))return super.mouseClicked(mouseX,mouseY,button);
        // Translate the caret's horizontal coordinate by the same inset as rendering.
        return super.mouseClicked(Math.max(getX(),Math.min(mouseX-INSET,getX()+getWidth()-1)),mouseY,button);
    }
}
