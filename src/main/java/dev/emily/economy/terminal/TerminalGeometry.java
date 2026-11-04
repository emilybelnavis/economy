package dev.emily.economy.terminal;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/** Four cells, ordered lower-left, lower-right, upper-left, upper-right from the front. */
public final class TerminalGeometry {
    private TerminalGeometry() {}
    public static BlockPos cell(BlockPos anchor, Direction facing, int cell) {
        if (cell < 0 || cell > 3 || !facing.getAxis().isHorizontal()) throw new IllegalArgumentException("Terminal cell");
        return anchor.relative(facing.getCounterClockWise(), cell & 1).above(cell >> 1);
    }
    public static List<BlockPos> cells(BlockPos anchor, Direction facing) {
        return List.of(cell(anchor, facing, 0), cell(anchor, facing, 1), cell(anchor, facing, 2), cell(anchor, facing, 3));
    }
    public static BlockPos anchor(BlockPos position, BlockState state) {
        if (!(state.getBlock() instanceof WideTerminalBlock)) return position.immutable();
        int cell = state.getValue(WideTerminalBlock.CELL);
        return position.relative(state.getValue(WideTerminalBlock.FACING).getClockWise(), cell & 1).below(cell >> 1);
    }
    public static BlockPos resolveAnchor(LevelReader level, BlockPos position) {
        return anchor(position, level.getBlockState(position));
    }
    public static boolean matches(LevelReader level, BlockPos anchor, UUID generation) {
        if (!level.hasChunkAt(anchor) || !(level.getBlockEntity(anchor) instanceof TerminalBlockEntity entity)
                || !entity.generation().equals(generation) || !entity.validIdentity()) return false;
        var state = level.getBlockState(anchor);
        if (state.getBlock() instanceof RegisterBlock) return true;
        if (!(state.getBlock() instanceof WideTerminalBlock) || state.getValue(WideTerminalBlock.CELL) != 0) return false;
        var facing = state.getValue(WideTerminalBlock.FACING);
        for (int cell = 0; cell < 4; cell++) {
            var position = cell(anchor, facing, cell);
            if (!level.hasChunkAt(position)) return false;
            var part = level.getBlockState(position);
            if (part.getBlock() != state.getBlock() || part.getValue(WideTerminalBlock.FACING) != facing
                    || part.getValue(WideTerminalBlock.CELL) != cell) return false;
        }
        return true;
    }
}
