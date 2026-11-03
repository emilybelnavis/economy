package ca.emilybelnavis.mcmods.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Teller banking and decorative trading equipment share the same two by two footprint. */
public final class WorkstationBlock extends WideMachineBlock {
    private final boolean teller;

    public WorkstationBlock(boolean teller, Properties properties) {
        super(properties);
        this.teller = teller;
    }

    public boolean isTeller() { return teller; }

    private void interact(Player player, BlockPos pos) {
        if (player instanceof ServerPlayer p) {
            if (teller) BankingMenu.open(p, pos, true, player.getMainHandItem().isEmpty() && TerminalSettings.canConfigure(p, pos));
            else TradingMenu.open(p, pos);
        }
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack held, BlockState state,
            Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        interact(player, pos);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level,
            BlockPos pos, Player player, BlockHitResult hit) {
        interact(player, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return MachineShapes.workstation(teller, state);
    }
}
