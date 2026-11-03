package ca.emilybelnavis.mcmods.economy;
import net.minecraft.network.chat.Component;
final class Messages {
    static net.minecraft.network.chat.MutableComponent error(String message) {
        if (message == null) return Component.translatable("message.economy.failed");
        return switch(message) {
        case "Only the shop owner can configure this register." -> Component.translatable("message.economy.error_0");
        case "Sneak-use the register holding the product stack first." -> Component.translatable("message.economy.error_1");
        case "Price must be positive." -> Component.translatable("message.economy.error_2");
        case "Shop is not configured. Owner: sneak-use with the product, then /economy shop price <units>." -> Component.translatable("message.economy.error_3");
        case "No stock container below the register." -> Component.translatable("message.economy.error_4");
        case "Shop is out of stock." -> Component.translatable("message.economy.error_5");
        case "Make room in your inventory first." -> Component.translatable("message.economy.error_6");
        case "Look at your cash register within five blocks." -> Component.translatable("message.economy.error_7");
        case "Stand within five blocks of an ATM." -> Component.translatable("message.economy.error_8");
        case "No currency in your main inventory." -> Component.translatable("message.economy.error_9");
        case "Unknown denomination." -> Component.translatable("message.economy.error_10");
        case "An empty main inventory slot is required." -> Component.translatable("message.economy.error_11");
        case "Amount must be positive" -> Component.translatable("message.economy.error_12");
        case "Memo must be non-null and at most 256 characters" -> Component.translatable("message.economy.error_13");
        case "Balance would overflow" -> Component.translatable("message.economy.error_14");
        case "Insufficient funds" -> Component.translatable("message.economy.error_15");
        case "Accounts must differ" -> Component.translatable("message.economy.error_16");
        case "Economy access requires the server thread" -> Component.translatable("message.economy.error_17");
        case "Unsupported economy data version" -> Component.translatable("message.economy.error_18");
        case "Invalid or duplicate persisted economy account" -> Component.translatable("message.economy.error_19");
        case "Invalid persisted economy memo" -> Component.translatable("message.economy.error_20");
        case "Invalid transaction amounts" -> Component.translatable("message.economy.error_21");
        case "[Balance]" -> Component.translatable("message.economy.error_22");
        case "[History]" -> Component.translatable("message.economy.error_23");
        case "[Deposit carried currency]" -> Component.translatable("message.economy.error_24");
        default -> Component.literal(message);
        };
    }
}
