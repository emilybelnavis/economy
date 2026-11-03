package ca.emilybelnavis.mcmods.economy;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded server-owned view model; no client-supplied balances are trusted. */
public record FinanceSnapshot(int menuId, String json) implements CustomPacketPayload {
    public static final Type<FinanceSnapshot> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Economy.ID,"finance_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf,FinanceSnapshot> CODEC=new StreamCodec<>() {
        public FinanceSnapshot decode(RegistryFriendlyByteBuf b) { return new FinanceSnapshot(b.readVarInt(),b.readUtf(131072)); }
        public void encode(RegistryFriendlyByteBuf b,FinanceSnapshot p) { b.writeVarInt(p.menuId);b.writeUtf(p.json,131072); }
    };
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
