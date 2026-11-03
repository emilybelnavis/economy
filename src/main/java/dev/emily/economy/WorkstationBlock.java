package dev.emily.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Decorative two by two equipment with no account or inventory state. */
public final class WorkstationBlock extends WideMachineBlock {
    private final boolean teller;

    public WorkstationBlock(boolean teller, Properties properties) {
        super(properties);
        this.teller = teller;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return MachineShapes.workstation(teller, state);
    }
}
