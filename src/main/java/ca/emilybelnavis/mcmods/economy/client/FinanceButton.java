package ca.emilybelnavis.mcmods.economy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Flat terminal control matching the dark/teal economy interface. */
public final class FinanceButton extends Button {
    public FinanceButton(int x, int y, int width, int height, Component label, OnPress press) {
        super(x, y, width, height, label, press, DEFAULT_NARRATION);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int edge = !active ? 0xff344344 : isHoveredOrFocused() ? 0xff79e3d3 : 0xff288f84;
        int fill = !active ? 0xff141d20 : isHoveredOrFocused() ? 0xff173c3c : 0xff0b171b;
        graphics.fill(getX(), getY(), getX() + width, getY() + height, edge);
        graphics.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, fill);
        if (isFocused()) graphics.fill(getX() + 2, getY() + height - 2, getX() + width - 2, getY() + height - 1, 0xffb5fff1);
        graphics.enableScissor(getX() + 2, getY() + 1, getX() + width - 2, getY() + height - 1);
        try { renderString(graphics, Minecraft.getInstance().font, !active ? 0xff687f80 : 0xffe4f4ef); }
        finally { graphics.disableScissor(); }
    }
}
