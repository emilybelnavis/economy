package ca.emilybelnavis.mcmods.economy;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Original card and cash items, with contextual banking tooltips. */
public final class DecorativeItem extends Item {
    public DecorativeItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        var cash = CashCatalog.find(stack);
        tooltip.add(cash == null ? Component.translatable("banking.economy.card_tooltip") :
            Component.translatable(cash.coin() ? "banking.economy.coin_tooltip" : "banking.economy.bill_tooltip",
                BankingConfig.format(cash.minor(), cash.currency())));
    }
}
