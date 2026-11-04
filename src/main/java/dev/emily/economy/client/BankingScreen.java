package dev.emily.economy.client;

import dev.emily.economy.banking.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client-only dark/teal presentation of bounded server snapshots. No client-side economic decisions. */
public final class BankingScreen extends AbstractContainerScreen<BankingMenu> {
    private static final int PANEL = 0xff17272e, INNER = 0xff101d24, TEAL = 0xff4fc4b2, TEXT = 0xffd9ece7;
    private record ActionButton(Button button, String action) {}
    private final List<ActionButton> actions = new ArrayList<>();
    private EditBox input;
    private String renderedPage = "";
    private int scroll;

    public BankingScreen(BankingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 390; imageHeight = 244;
    }
    public static void initialize(IEventBus bus) { bus.addListener(BankingScreen::register); }
    private static void register(RegisterMenuScreensEvent event) { event.register(BankingCatalog.BANKING.get(), BankingScreen::new); }
    @Override protected void init() {
        super.init(); actions.clear();
        String[] pages = {"setup", "accounts", "history", "fx", "deposit", "withdraw", "shop", "claims", "settings"};
        for (int index = 0; index < pages.length; index++) {
            String page = pages[index]; int column = index < 5 ? index : index - 5;
            int width = index < 5 ? 72 : 90, y = index < 5 ? 20 : 41;
            var tab = addRenderableWidget(Button.builder(text("tab." + page), button -> send(page, ""))
                    .bounds(leftPos + 12 + column * width, topPos + y, width - 2, 18).build());
            if ((menu.remote() && Set.of("deposit", "withdraw", "shop", "settings").contains(page))
                    || (menu.worldSetup() && !page.equals("setup"))) tab.active = false;
        }
        input = addRenderableWidget(new EditBox(font, leftPos + 190, topPos + 144, 188, 18, text("input")));
        input.setMaxLength(2048); input.setValue("CAD");
        for (int index = 0; index < 3; index++) {
            final int selected = index;
            var button = addRenderableWidget(Button.builder(Component.empty(), ignored -> primary(selected))
                    .bounds(leftPos + 190 + index * 63, topPos + 198, 61, 18).build());
            actions.add(new ActionButton(button, ""));
        }
        addRenderableWidget(Button.builder(text("retry"), button -> send(Set.of("setup", "accounts", "history", "fx", "settings").contains(menu.page()) ? "bank_retry" : "retry", ""))
                .bounds(leftPos + 190, topPos + 220, 92, 18).build());
        addRenderableWidget(Button.builder(text("cancel"), button -> send("cancel", ""))
                .bounds(leftPos + 286, topPos + 220, 92, 18).build());
        updatePage();
    }
    private static Component text(String key) { return Component.translatable("screen.economy." + key); }
    private void send(String action, String value) {
        if (menu.busy() && !action.equals("retry") && !action.equals("bank_retry") && !action.equals("cancel")) return;
        BankingNetwork.action(menu, action, value);
    }
    private String[] pageActions() {
        return switch (menu.page()) {
            case "setup" -> new String[]{"setup"};
            case "accounts" -> new String[]{"account_open", "account_select", "accounts_next"};
            case "history" -> new String[]{"history_next"};
            case "fx" -> new String[]{"exchange_preview", "exchange_confirm"};
            case "deposit" -> new String[]{"deposit_preview", "deposit_confirm"};
            case "withdraw" -> new String[]{"withdraw_preview", "withdraw_confirm"};
            case "shop" -> new String[]{"buy_preview", "buy_confirm", "price"};
            case "claims" -> new String[]{"collect", "claims_next"};
            case "settings" -> new String[]{"configure"};
            default -> new String[0];
        };
    }
    private void primary(int index) {
        var allowed = pageActions();
        if (index < allowed.length) send(allowed[index], input.getValue());
    }
    private void updatePage() {
        if (!renderedPage.equals(menu.page())) {
            renderedPage = menu.page(); scroll = 0;
            input.setValue(switch (renderedPage) {
                case "fx" -> "2 10";
                case "withdraw" -> "CAD 60";
                case "shop" -> "1";
                case "settings" -> "BOTH;CAD;all";
                case "claims" -> "1";
                case "history" -> "";
                default -> "CAD";
            });
        }
        var allowed = pageActions();
        for (int index = 0; index < actions.size(); index++) {
            var button = actions.get(index).button();
            button.visible = index < allowed.length;
            button.active = !menu.busy();
            if (index < allowed.length) button.setMessage(text("action." + allowed[index]));
        }
        input.setEditable(!menu.busy());
    }
    @Override protected void containerTick() { super.containerTick(); updatePage(); }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, TEAL);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        graphics.fill(leftPos + 8, topPos + 66, leftPos + 382, topPos + 134, INNER);
        if (menu.page().equals("deposit")) for (int index = 0; index < 24; index++) {
            int x = leftPos + 12 + (index % 8) * 18, y = topPos + 74 + (index / 8) * 18;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xff34525a);
            graphics.fill(x, y, x + 16, y + 16, INNER);
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) slot(graphics, 12 + column * 18, 144 + row * 18);
        for (int column = 0; column < 9; column++) slot(graphics, 12 + column * 18, 202);
    }
    private void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(leftPos + x - 1, topPos + y - 1, leftPos + x + 17, topPos + y + 17, 0xff34525a);
        graphics.fill(leftPos + x, topPos + y, leftPos + x + 16, topPos + y + 16, INNER);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 7, TEXT, false);
        graphics.drawString(font, menu.busy() ? text("busy") : text("tab." + menu.page()), 190, 7, TEAL, false);
        int textX = menu.page().equals("deposit") ? 164 : 12;
        int textWidth = 378 - textX;
        graphics.enableScissor(leftPos + textX, topPos + 70, leftPos + 378, topPos + 132);
        var display = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (var line : menu.lines()) display.addAll(font.split(Component.literal(line), textWidth - 6));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, display.size() - 6)));
        for (int index = scroll; index < display.size() && index < scroll + 6; index++)
            graphics.drawString(font, display.get(index), textX + 2, 72 + (index - scroll) * 10, TEXT, false);
        graphics.disableScissor();
        graphics.drawString(font, text("input"), 190, 133, 0xff9abeb8, false);
        graphics.enableScissor(leftPos + 190, topPos + 166, leftPos + 378, topPos + 194);
        graphics.drawWordWrap(font, Component.literal(menu.status()), 190, 166, 186, TEXT);
        graphics.disableScissor();
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= leftPos + 8 && mouseX < leftPos + 382 && mouseY >= topPos + 66 && mouseY < topPos + 134) {
            scroll += vertical > 0 ? -1 : 1; return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
}
