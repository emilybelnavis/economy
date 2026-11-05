package dev.emily.economy.client;

import dev.emily.economy.api.EconomyCurrency;
import dev.emily.economy.banking.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Compact banking pages. Only server-offered accounts and deliveries become controls. */
public final class BankingScreen extends AbstractContainerScreen<BankingMenu> {
    private static final int TEXT = 0xffd5e8e3, MUTED = 0xff91aaa7, TEAL = 0xff55c8b9;
    private String renderedPage = "", destination = "", draft = "", reviewedDraft = "", currency = "CAD";
    private String chooser = "", submittedReview = "";
    private long revision = -1;
    private boolean wasBusy;
    private int scroll;
    private long settingsRevision = -1;
    private String serviceMode = "BOTH";
    private final Set<String> acceptedCurrencies = new TreeSet<>();
    private EditBox input;
    private Button primary;
    private String primaryAction = "";

    public BankingScreen(BankingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 316; imageHeight = 236;
    }
    public static void initialize(IEventBus bus) { bus.addListener(BankingScreen::register); }
    private static void register(RegisterMenuScreensEvent event) { event.register(BankingCatalog.BANKING.get(), BankingScreen::new); }
    private static Component text(String key) { return Component.translatable("screen.economy." + key); }
    private void send(String action, String value) {
        if (!menu.busy() || Set.of("retry", "bank_retry", "cancel").contains(action)) BankingNetwork.action(menu, action, value);
    }
    private Button button(int x, int y, int width, Component label, Runnable press) {
        var result = addRenderableWidget(new FinanceButton(leftPos + x, topPos + y, width, 18, label, ignored -> press.run()));
        result.active = !menu.busy();
        result.setTooltip(Tooltip.create(label));
        return result;
    }
    @Override protected void init() { super.init(); rebuild(); }
    private List<BankingNetwork.Choice> choices(String action) {
        return menu.choices().stream().filter(choice -> choice.action().equals(action)).toList();
    }
    private String selectedLabel() {
        return choices("account_select").stream().filter(BankingNetwork.Choice::selected).map(BankingNetwork.Choice::label)
                .findFirst().orElse("Choose account");
    }
    private String reviewKey() { return draft + "|" + destination; }
    private boolean reviewed() {
        String status = menu.status().toLowerCase(Locale.ROOT);
        return reviewedDraft.equals(reviewKey()) && (status.equals("withdrawal preview") || status.equals("exchange preview")
                || status.contains("then confirm") || status.contains("review the") || status.contains("confirm to")
                || status.contains("ready to confirm") || status.startsWith("preview ready"));
    }
    private void rebuild() {
        if (menu.page().equals("settings") && settingsRevision != menu.revision() && !choices("configure").isEmpty()) {
            var fields = choices("configure").getFirst().value().split(";", -1);
            if (fields.length == 3) {
                serviceMode = fields[0]; acceptedCurrencies.clear();
                if (!fields[1].isBlank()) acceptedCurrencies.addAll(Arrays.asList(fields[1].split(",")));
            }
            settingsRevision = menu.revision();
        }
        menu.setDepositSlotsVisible(chooser.isEmpty());
        clearWidgets(); input = null; primary = null; primaryAction = "";
        String[] pages = homePages();
        if (menu.access() == BankingMenu.Access.WORLD_SETUP) {
            // World denomination selection is separate from opening a player's bank account.
        } else if (menu.access() == BankingMenu.Access.ADMIN) {
            button(10, 32, 136, Component.literal("Terminal settings"), () -> send("settings", ""));
        } else {
            button(10, 32, 80, text("tab.home"), () -> send("home", ""));
            if (!menu.page().equals("home") && menu.allows("accounts"))
                button(100, 32, 106, text("tab.accounts"), () -> send("accounts", ""));
        }
        var destinations = choices("destination");
        if (destinations.stream().noneMatch(choice -> choice.value().equals(destination)))
            destination = destinations.isEmpty() ? "" : destinations.getFirst().value();
        if (!chooser.isEmpty()) {
            buildRows(choices(chooser), true);
            button(10, 188, 92, Component.literal("Back"), () -> { chooser = ""; scroll = 0; rebuild(); });
            return;
        }
        switch (menu.page()) {
            case "home" -> {
                for (int i = 0; i < pages.length; i++) {
                    String page = pages[i];
                    button(10 + (i % 2) * 152, 76 + (i / 2) * 28, 144, text("tab." + page), () -> send(page, ""));
                }
            }
            case "accounts" -> {
                var accounts = choices("account_select");
                if (accounts.isEmpty()) {
                    if (!choices("setup").isEmpty())
                        button(10, 106, 296, text("action.setup"), () -> send("setup", ""));
                } else {
                    buildRows(accounts, false);
                    button(10, 188, 296, text("action.new_account"), () -> send("manage_accounts", ""));
                }
            }
            case "manage" -> {
                var accounts = choices("account_close_preview");
                for (int i = 0; i < accounts.size(); i++) {
                    var account = accounts.get(i);
                    var close = button(204, 74 + i * 21, 102, text("action.account_close_preview"),
                            () -> send("account_close_preview", account.value()));
                    close.active = !menu.busy() && account.selected();
                    if (!account.selected()) close.setTooltip(Tooltip.create(Component.literal("This account cannot be closed here.")));
                }
                var currencies = choices("account_open");
                if (currencies.stream().noneMatch(choice -> choice.value().equals(currency)))
                    currency = currencies.isEmpty() ? "" : currencies.getFirst().value();
                var selector = button(10, 164, 176, Component.literal(currency.isEmpty() ? "No currencies available" : currency + " ▾"),
                        () -> openChooser("account_open"));
                selector.active = !menu.busy() && !currencies.isEmpty();
                var open = button(192, 164, 114, text("action.account_open"), () -> send("account_open", currency));
                open.active = !menu.busy() && !currencies.isEmpty();
            }
            case "branch", "branch_targets", "brands", "branch_products", "cards", "card_funding" -> {
                buildRows(menu.choices(), false);
                button(10, 188, 136, Component.literal(menu.access() == BankingMenu.Access.ADMIN
                        ? "Back to settings" : "Back to branch"), () -> send(menu.access() == BankingMenu.Access.ADMIN
                        ? "settings" : "manage_accounts", ""));
            }
            case "credit_facilities" -> {
                buildRows(menu.choices(), false);
                button(10,188,144,Component.literal("Back to credit profile"),()->send("credit_profile",""));
            }
            case "credit_profile", "credit_history", "credit_facility", "credit_statements" -> {
                int control=0;for(var choice:menu.choices()) {
                    var c=choice;button(10+control++*152,188,144,Component.literal(c.label()),()->send(c.action(),c.value()));
                }
            }
            case "brand_review", "card_review" -> {
                for (var choice : menu.choices())
                    button(160, 188, 146, Component.literal(choice.label()), () -> send(choice.action(), choice.value()));
                button(10, 188, 136, Component.literal(menu.page().equals("card_review")?"Back to cards":"Back to settings"), () -> send(menu.page().equals("card_review")?"cards":"settings", ""));
            }
            case "claims" -> {
                buildRows(choices("collect"), false);
                button(10, 188, 96, text("tab.claims"), () -> send("claims", ""));
                button(210, 188, 96, text("action.claims_next"), () -> send("claims_next", ""));
            }
            case "deposit", "withdraw", "fx" -> {
                boolean deposit = menu.page().equals("deposit"), fx = menu.page().equals("fx");
                button(deposit ? 164 : 10, 74, deposit ? 142 : fx ? 144 : 296,
                        Component.literal((fx ? "From: " : "") + selectedLabel() + " ▾"), () -> openChooser("account_select"));
                if (fx) {
                    String label = destinations.stream().filter(choice -> choice.value().equals(destination))
                            .map(BankingNetwork.Choice::label).findFirst().orElse("Choose account");
                    button(160, 74, 146, Component.literal("To: " + label + " ▾"), () -> openChooser("destination"));
                }
                if (!deposit) amountBox(10, 108, 136, fx ? "Amount (CHF pairs accept cents)" : "Amount to withdraw", fx ? "10" : "60.00");
                primaryAction = menu.page() + "_preview";
                if (fx) primaryAction = "exchange_preview";
                primary = button(deposit ? 184 : 160, deposit ? 188 : 108, deposit ? 122 : 146, Component.empty(), this::performPrimary);
                if (deposit) button(184, 210, 122, text("cancel"), () -> send("cancel", ""));
            }
            case "history" -> {
                button(10, 188, 136, text("action.manage_accounts"), () -> send("manage_accounts", ""));
                button(170, 188, 136, text("action.history_next"), () -> send("history_next", ""));
            }
            case "close" -> {
                button(10, 188, 136, Component.literal("Back to management"), () -> send("manage_accounts", ""));
                for (var choice : choices("account_close_confirm"))
                    button(160, 188, 146, Component.literal(choice.label()), () -> send(choice.action(), choice.value()));
            }
            case "setup" -> {
                if (menu.worldSetup()) {
                    button(10, 166, 136, Component.literal(currency + " ▾"), this::nextCurrency);
                    button(160, 188, 146, Component.literal("Choose currency"), () -> send("setup", currency));
                }
            }
            case "settings" -> {
                button(10, 188, 136, Component.literal("Use terminal"), () -> send("banking", ""));
                if (!choices("brand_preview").isEmpty())
                    button(10, 76, 296, Component.literal("Choose bank brand ▾"), () -> openChooser("brand_preview"));
                if (!choices("configure").isEmpty()) {
                    button(10, 104, 296, Component.literal("Services: " + serviceLabel()), () -> {
                        serviceMode = switch (serviceMode) {
                            case "BOTH" -> "DEPOSIT";
                            case "DEPOSIT" -> "WITHDRAW";
                            case "WITHDRAW" -> "DISABLED";
                            default -> "BOTH";
                        };
                        rebuild();
                    });
                    var save = button(160, 188, 146, text("action.configure"),
                            () -> send("configure", serviceMode + ";" + String.join(",", acceptedCurrencies) + ";all"));
                    save.active = !menu.busy() && (serviceMode.equals("DISABLED") || !acceptedCurrencies.isEmpty());
                }
            }
            case "shop" -> {
                boolean held = minecraft != null && minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty();
                if (held) {
                    amountBox(10, 166, 136, "Price for held items", "10");
                    button(10, 188, 136, text("action.price"), () -> send("price", draft));
                }
                primaryAction = "buy_preview";
                primary = button(160, 188, 146, Component.empty(), this::performPrimary);
            }
            default -> { }
        }
        if (needsRetry()) button(menu.page().equals("deposit") ? 184 : 10, menu.page().equals("deposit") ? 166 : 188, menu.page().equals("deposit") ? 122 : 136, text("retry"),
                () -> send(Set.of("setup", "accounts", "manage", "history", "fx", "settings", "close", "branch", "branch_targets", "brands", "brand_review", "branch_products", "cards", "card_funding", "card_review", "credit_profile", "credit_history", "credit_facilities", "credit_facility", "credit_statements").contains(menu.page()) ? "bank_retry" : "retry", ""));
        updatePrimary();
    }
    private String[] homePages() {
        return switch (menu.access()) {
            case ATM, TELLER -> new String[]{"accounts", "deposit", "withdraw", "fx", "claims", "cards", "credit_profile", "credit_facilities"};
            case KIOSK -> new String[]{"manage_accounts"};
            case CARD -> new String[]{"accounts", "fx", "claims", "cards", "credit_profile", "credit_facilities"};
            case REGISTER -> new String[]{"shop", "claims"};
            case ADMIN -> new String[]{"settings"};
            case WORLD_SETUP -> new String[0];
        };
    }
    private String serviceLabel() {
        return switch (serviceMode) {
            case "BOTH" -> "Deposits and withdrawals";
            case "DEPOSIT" -> "Deposits only";
            case "WITHDRAW" -> "Withdrawals only";
            default -> "Disabled";
        };
    }
    private void nextCurrency() {
        var values = EconomyCurrency.all();
        int index = 0;
        for (int i = 0; i < values.size(); i++) if (values.get(i).code().equals(currency)) index = i;
        currency = values.get((index + 1) % values.size()).code(); rebuild();
    }
    private boolean needsRetry() {
        String status = menu.status().toLowerCase(Locale.ROOT);
        return !menu.worldSetup() && (status.contains("retry") || status.contains("needs checking") || status.contains("pending"));
    }
    private void openChooser(String action) { chooser = action; scroll = 0; rebuild(); }
    private void buildRows(List<BankingNetwork.Choice> rows, boolean selection) {
        scroll = Math.clamp(scroll, 0, Math.max(0, rows.size() - 6));
        for (int i = scroll; i < Math.min(rows.size(), scroll + 6); i++) {
            var choice = rows.get(i);
            String label = (choice.selected() ? "✓ " : "") + (choice.action().equals("collect") ? "Collect: " : "") + choice.label();
            button(10, 74 + (i - scroll) * 18, 286, Component.literal(label), () -> {
                if (choice.action().equals("destination")) {
                    destination = choice.value(); reviewedDraft = "";
                } else if (choice.action().equals("account_open") && chooser.equals("account_open")) {
                    currency = choice.value();
                } else send(choice.action(), choice.value());
                if (selection) { chooser = ""; scroll = 0; rebuild(); }
            });
        }
    }
    private void amountBox(int x, int y, int width, String label, String hint) {
        input = addRenderableWidget(new EditBox(font, leftPos + x, topPos + y, width, 18, Component.literal(label)));
        input.setMaxLength(menu.page().equals("settings") ? 2048 : 32); input.setValue(draft);
        input.setHint(Component.literal(hint)); input.setEditable(!menu.busy());
        input.setResponder(value -> { draft = value; updatePrimary(); });
    }
    private void performPrimary() {
        String action = reviewed() ? primaryAction.replace("_preview", "_confirm") : primaryAction;
        if (action.endsWith("_preview")) submittedReview = reviewKey();
        send(action, menu.page().equals("fx") ? destination + " " + draft : draft);
    }
    private void updatePrimary() {
        if (primary == null) return;
        primary.setMessage(text("action." + (reviewed() ? primaryAction.replace("_preview", "_confirm") : primaryAction)));
        primary.active = !menu.busy() && (menu.page().equals("deposit") || menu.page().equals("shop") || !draft.isBlank())
                && (!menu.page().equals("fx") || !destination.isEmpty())
                && (!Set.of("deposit", "withdraw", "fx").contains(menu.page()) || !choices("account_select").isEmpty());
    }
    @Override protected void containerTick() {
        super.containerTick();
        if (revision != menu.revision() || wasBusy != menu.busy()) {
            if (!renderedPage.equals(menu.page())) { draft = ""; chooser = ""; scroll = 0; reviewedDraft = ""; }
            renderedPage = menu.page(); revision = menu.revision(); wasBusy = menu.busy();
            if (menu.status().toLowerCase(Locale.ROOT).contains("preview") || menu.status().toLowerCase(Locale.ROOT).contains("confirm"))
                reviewedDraft = submittedReview;
            rebuild();
        }
        updatePrimary();
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xff142326);
        g.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xff101e23);
        g.fill(leftPos + 8, topPos + 8, leftPos + imageWidth - 8, topPos + 29, 0xff125752);
        if (chooser.isEmpty() && menu.page().equals("deposit")) for (var slot : menu.slots) if (slot.isActive()) {
            g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xff335b5a);
            g.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xff192c31);
        }
        if (!menu.page().equals("deposit")) g.fill(leftPos + 10, topPos + 209, leftPos + 306, topPos + 210, 0xff335b5a);
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, font.plainSubstrByWidth(title.getString(), 130), 13, 14, 0xffffffff, false);
        String heading = menu.busy() ? text("busy").getString() : menu.worldSetup() ? "World currency" : text("tab." + menu.page()).getString();
        g.drawString(font, font.plainSubstrByWidth(heading, 150), 154, 14, TEAL, false);
        if (!chooser.isEmpty()) { scrollbar(g, choices(chooser).size()); return; }
        boolean deposit = menu.page().equals("deposit");
        int x = deposit ? 164 : 10, y = deposit ? 98 : Set.of("fx", "withdraw").contains(menu.page()) ? 134 : 76;
        int bottom = deposit ? 136 : Set.of("settings", "shop", "setup").contains(menu.page()) ? 154 : 184;
        boolean rowPage = Set.of("accounts", "claims", "branch", "branch_targets", "brands", "branch_products", "cards", "card_funding", "credit_facilities").contains(menu.page());
        if (rowPage && !menu.lines().isEmpty())
            g.drawString(font, font.plainSubstrByWidth(menu.lines().getFirst(), 296), 10, 57, MUTED, false);
        boolean configurationForm = menu.page().equals("settings") && !choices("configure").isEmpty();
        if (menu.page().equals("manage")) {
            var accounts = choices("account_close_preview");
            for (int i = 0; i < accounts.size(); i++)
                g.drawString(font, font.plainSubstrByWidth(accounts.get(i).label(), 186), 10, 79 + i * 21, TEXT, false);
            String label = "";
            g.drawString(font, label, 10, 151, MUTED, false);
        } else if (menu.page().equals("home")) {
            // The home page consists of action buttons.
        } else if (menu.page().equals("history")) {
            g.drawString(font, Component.literal("Transaction History"), 10, 72, TEAL, false);
            boolean hasHeading = !menu.lines().isEmpty() && menu.lines().getFirst().equals("Transaction History");
            if (hasHeading && menu.lines().size() > 1)
                g.drawString(font, font.plainSubstrByWidth(menu.lines().get(1), 296), 10, 87, TEXT, false);
            g.fill(10, 103, 306, 104, 0xff335b5a);
            drawLines(g, hasHeading ? menu.lines().subList(Math.min(2, menu.lines().size()), menu.lines().size())
                    : menu.lines(), 10, 112, 296, 184);
        } else if (menu.page().equals("settings")) {
            if (configurationForm) {
                g.drawString(font, "Cash currency: " + String.join(", ", acceptedCurrencies), 10, 132, MUTED, false);
                g.drawString(font, "Bank binding controls accepted cash.", 10, 146, MUTED, false);
            } else drawLines(g, menu.lines(), 10, 104, 296, 184);
        }
        else if (!rowPage || menu.choices().isEmpty()) drawLines(g, menu.lines(), x, y, 306 - x, bottom);
        if (rowPage) scrollbar(g, menu.choices().size());
        if (input != null) {
            String label = menu.page().equals("fx") ? "Amount (whole source units)" : menu.page().equals("withdraw") ? "Amount in account currency"
                    : menu.page().equals("settings") ? "Cash services" : "Price for held items";
            g.drawString(font, font.plainSubstrByWidth(label, 296), input.getX() - leftPos, input.getY() - topPos - 10, MUTED, false);
        }
        if (deposit) g.drawString(font, Component.translatable("container.inventory"), 12, 134, MUTED, false);
        drawLines(g, List.of(menu.status()), deposit ? 184 : 10, deposit ? 144 : 212, deposit ? 122 : 296, deposit ? (needsRetry() ? 164 : 182) : 232);
    }
    private void drawLines(GuiGraphics g, List<String> lines, int x, int y, int width, int bottom) {
        var wrapped = new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for (var line : lines) wrapped.addAll(font.split(Component.literal(line), width));
        int visible = Math.max(1, (bottom - y) / 10);
        boolean body = y != 212 && y != 144;
        int start = body ? Math.clamp(scroll, 0, Math.max(0, wrapped.size() - visible)) : 0;
        if (body) scroll = start;
        g.enableScissor(leftPos + x, topPos + y, leftPos + x + width, topPos + bottom);
        for (int i = start; i < Math.min(wrapped.size(), start + visible); i++) g.drawString(font, wrapped.get(i), x, y + (i - start) * 10, TEXT, false);
        g.disableScissor();
    }
    private void scrollbar(GuiGraphics g, int count) {
        if (count <= 6) return;
        int thumb = 108 * 6 / count, y = 74 + (108 - thumb) * scroll / Math.max(1, count - 6);
        g.fill(300, 74, 303, 182, 0xff253e42); g.fill(300, y, 303, y + thumb, TEAL);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); renderTooltip(g, mouseX, mouseY);
        if (mouseX >= leftPos + 10 && mouseX < leftPos + 306 && mouseY >= topPos + 211 && mouseY < topPos + 232)
            g.renderComponentTooltip(font, List.of(Component.literal(menu.status())), mouseX, mouseY);
    }
    @Override public void removed() { menu.setDepositSlotsVisible(true); super.removed(); }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= leftPos + 8 && mouseX < leftPos + 308 && mouseY >= topPos + 72 && mouseY < topPos + 184) {
            if (!chooser.isEmpty() || Set.of("accounts", "claims", "branch", "branch_targets", "brands", "branch_products", "cards", "card_funding", "credit_facilities").contains(menu.page())) {
                int count = chooser.isEmpty() ? menu.choices().size() : choices(chooser).size();
                scroll = Math.clamp(scroll + (vertical > 0 ? -1 : 1), 0, Math.max(0, count - 6)); rebuild();
            } else if (!menu.page().equals("deposit") || mouseX >= leftPos + 164) scroll = Math.max(0, scroll + (vertical > 0 ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
}
