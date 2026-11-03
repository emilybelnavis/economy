package ca.emilybelnavis.mcmods.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Every tile opens the same server-owned ATM interface. */
public final class AtmBlock extends WideMachineBlock {
    public AtmBlock(Properties properties) { super(properties); }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return MachineShapes.atm(state);
    }
    private void interact(Player player, BlockPos pos) { if(player instanceof ServerPlayer serverPlayer) BankingMenu.open(serverPlayer, pos, false, player.getMainHandItem().isEmpty() && TerminalSettings.canConfigure(serverPlayer, pos)); }
    @Override protected ItemInteractionResult useItemOn(ItemStack held,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(hand!=InteractionHand.MAIN_HAND) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        interact(player, pos);return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        interact(player, pos);return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
