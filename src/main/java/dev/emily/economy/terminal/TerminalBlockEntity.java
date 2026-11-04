package dev.emily.economy.terminal;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Only the physical anchor owns persistent placement identity. */
public final class TerminalBlockEntity extends BlockEntity {
    private UUID generation = UUID.randomUUID();
    private UUID owner;
    private UUID placementOperation = UUID.randomUUID();
    private long policyRevision;
    private boolean valid = true;
    private boolean policyReady;

    public TerminalBlockEntity(BlockPos position, BlockState state) {
        super(TerminalCatalog.BLOCK_ENTITY.get(), position, state);
    }
    public boolean policyReady() { return policyReady; }
    public void setPolicyReady(boolean value) { policyReady = value; }
    public dev.emily.economy.journal.TerminalPolicy.Kind kind() {
        if (getBlockState().getBlock() instanceof WideTerminalBlock wide) return wide.kind();
        return dev.emily.economy.journal.TerminalPolicy.Kind.REGISTER;
    }
    public UUID generation() { return generation; }
    public UUID owner() { return owner; }
    public UUID placementOperation() { return placementOperation; }
    public long policyRevision() { return policyRevision; }
    public void setPolicyRevision(long revision) { policyRevision = revision; setChanged(); }
    public boolean validIdentity() { return valid; }
    public void setOwner(UUID value) { owner = value; setChanged(); }
    public void newPlacement(UUID value) {
        generation = UUID.randomUUID(); placementOperation = UUID.randomUUID(); owner = value;
        policyRevision = 0; valid = true; policyReady = false; setChanged();
    }
    public void invalidateIdentity() { valid = false; policyReady = false; setChanged(); }
    @Override public boolean onlyOpCanSetNbt() { return true; }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server && valid && owner != null)
            TerminalLifecycle.loaded(server, worldPosition, this);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("generation", generation); tag.putUUID("placement_operation", placementOperation);
        if (owner != null) tag.putUUID("owner", owner);
        tag.putLong("policy_revision", policyRevision); tag.putBoolean("valid", valid);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("generation")) generation = tag.getUUID("generation");
        if (tag.hasUUID("placement_operation")) placementOperation = tag.getUUID("placement_operation");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        policyRevision = tag.getLong("policy_revision"); valid = !tag.contains("valid") || tag.getBoolean("valid");
    }
}
