package ca.emilybelnavis.mcmods.economy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Only requests: account IDs, quantities and choices are revalidated on the server. */
public record FinanceAction(int menuId,String action,String json) implements CustomPacketPayload {
    public static final Type<FinanceAction> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Economy.ID,"finance_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,FinanceAction> CODEC=new StreamCodec<>() {
        public FinanceAction decode(RegistryFriendlyByteBuf b){return new FinanceAction(b.readVarInt(),b.readUtf(32),b.readUtf(16384));}
        public void encode(RegistryFriendlyByteBuf b,FinanceAction p){b.writeVarInt(p.menuId);b.writeUtf(p.action,32);b.writeUtf(p.json,16384);}
    };
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
