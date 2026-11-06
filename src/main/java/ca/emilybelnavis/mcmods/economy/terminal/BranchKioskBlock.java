package ca.emilybelnavis.mcmods.economy.terminal;

import com.mojang.serialization.MapCodec;
import ca.emilybelnavis.mcmods.economy.journal.TerminalPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One-wide, two-high civilian branch kiosk; all authority stays in its lower anchor. */
public final class BranchKioskBlock extends WideTerminalBlock {
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 1);

    public BranchKioskBlock(BlockBehaviour.Properties properties) {
        super(properties, TerminalPolicy.Kind.KIOSK);
    }
    @Override protected MapCodec<? extends Block> codec() { return simpleCodec(BranchKioskBlock::new); }
    @Override public IntegerProperty cellProperty() { return CELL; }
    @Override public int cellCount() { return 2; }
    @Override public BlockPos cellPosition(BlockPos anchor, Direction facing, int cell) {
        if (cell < 0 || cell >= cellCount() || !facing.getAxis().isHorizontal())
            throw new IllegalArgumentException("Kiosk cell");
        return anchor.above(cell);
    }
    @Override public BlockPos anchorPosition(BlockPos position, BlockState state) {
        return position.below(state.getValue(CELL));
    }
    @Override protected boolean hasSupport(LevelReader level, BlockPos anchor, Direction facing) {
        var floor = anchor.below();
        return level.hasChunkAt(floor) && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        // A conservative collision envelope encloses the narrower header/pedestal model.
        return Block.box(2, 0, 2, 14, 16, 14);
    }
}
