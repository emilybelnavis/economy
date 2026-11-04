package dev.emily.economy.banking;

import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Bounded transport. Actor identity and original financial operations never come from packet fields. */
public final class BankingNetwork {
    public static final Set<String> ACTIONS = Set.of("setup", "accounts", "accounts_next", "account_select", "account_open",
            "history", "history_next", "fx", "exchange_preview", "exchange_confirm", "bank_retry", "deposit",
            "deposit_preview", "deposit_confirm", "withdraw", "withdraw_preview", "withdraw_confirm", "shop",
            "buy_preview", "buy_confirm", "claims", "claims_next", "collect", "settings", "configure", "price", "retry", "cancel");
    private BankingNetwork() {}
    public record Action(int menuId, UUID session, long revision, String action, String value) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("economy", "banking_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = new StreamCodec<>() {
            @Override public Action decode(RegistryFriendlyByteBuf buffer) {
                return new Action(buffer.readVarInt(), buffer.readUUID(), buffer.readVarLong(), buffer.readUtf(24), buffer.readUtf(2048));
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, Action value) {
                buffer.writeVarInt(value.menuId()); buffer.writeUUID(value.session()); buffer.writeVarLong(value.revision());
                buffer.writeUtf(value.action(), 24); buffer.writeUtf(value.value(), 2048);
            }
        };
        public Action {
            identity(menuId, session, revision);
            if (!ACTIONS.contains(action)) throw new IllegalArgumentException("BANKING_ACTION");
            text(value, 2048, 2048);
        }
        @Override public Type<Action> type() { return TYPE; }
    }
    public record Snapshot(int menuId, UUID session, long revision, String page, List<String> lines, String status,
                           boolean busy) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("economy", "banking_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = new StreamCodec<>() {
            @Override public Snapshot decode(RegistryFriendlyByteBuf buffer) {
                int menu = buffer.readVarInt(); UUID session = buffer.readUUID(); long revision = buffer.readVarLong();
                String page = buffer.readUtf(24); int count = buffer.readVarInt();
                if (count < 0 || count > 32) throw new IllegalArgumentException("BANKING_SNAPSHOT_LINES");
                var lines = new ArrayList<String>(count);
                for (int index = 0; index < count; index++) lines.add(buffer.readUtf(256));
                return new Snapshot(menu, session, revision, page, lines, buffer.readUtf(256), buffer.readBoolean());
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, Snapshot value) {
                buffer.writeVarInt(value.menuId()); buffer.writeUUID(value.session()); buffer.writeVarLong(value.revision());
                buffer.writeUtf(value.page(), 24); buffer.writeVarInt(value.lines().size());
                for (var line : value.lines()) buffer.writeUtf(line, 256);
                buffer.writeUtf(value.status(), 256); buffer.writeBoolean(value.busy());
            }
        };
        public Snapshot {
            identity(menuId, session, revision);
            if (page == null || !page.matches("[a-z_]{1,24}")) throw new IllegalArgumentException("BANKING_PAGE");
            Objects.requireNonNull(lines); if (lines.size() > 32) throw new IllegalArgumentException("BANKING_SNAPSHOT_LINES");
            int bytes = 0;
            for (var line : lines) { text(line, 256, 1024); bytes += line.getBytes(StandardCharsets.UTF_8).length; }
            text(status, 256, 1024); bytes += status.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > 12_000) throw new IllegalArgumentException("BANKING_SNAPSHOT_BUDGET");
            lines = List.copyOf(lines);
        }
        @Override public Type<Snapshot> type() { return TYPE; }
    }
    private static void identity(int menuId, UUID session, long revision) {
        if (menuId < 0 || menuId > 100 || revision < 0) throw new IllegalArgumentException("BANKING_SESSION");
        Objects.requireNonNull(session);
    }
    private static void text(String value, int characters, int bytes) {
        if (value == null || value.length() > characters || value.getBytes(StandardCharsets.UTF_8).length > bytes)
            throw new IllegalArgumentException("BANKING_TEXT_BOUNDS");
    }
    public static void initialize(IEventBus bus) { bus.addListener(BankingNetwork::register); }
    private static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Action.TYPE, Action.CODEC, (request, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof BankingMenu menu
                    && menu.containerId == request.menuId()
                    && menu.acceptAction(player, request.session(), request.revision(), request.action()))
                BankingController.action(player, menu, request.action(), request.value());
        }));
        registrar.playToClient(Snapshot.TYPE, Snapshot.CODEC, (snapshot, context) -> context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof BankingMenu menu && menu.containerId == snapshot.menuId()
                    && menu.session().equals(snapshot.session()))
                menu.applyClientSnapshot(snapshot.revision(), snapshot.page(), snapshot.lines(), snapshot.status(), snapshot.busy());
        }));
    }
    public static void send(ServerPlayer player, Snapshot snapshot) { PacketDistributor.sendToPlayer(player, snapshot); }
    public static void action(BankingMenu menu, String action, String value) {
        PacketDistributor.sendToServer(new Action(menu.containerId, menu.session(), menu.revision(), action, value));
    }
}
