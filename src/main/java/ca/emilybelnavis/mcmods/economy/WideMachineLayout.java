package ca.emilybelnavis.mcmods.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Pure coordinate rules shared by placement, cleanup, and geometry checks. */
public final class WideMachineLayout {
    private WideMachineLayout() {}

    public static BlockPos cell(BlockPos anchor, Direction facing, boolean right, boolean upper) {
        requireHorizontal(facing);
        return anchor.relative(facing.getClockWise(), right ? 1 : 0).above(upper ? 1 : 0);
    }

    public static BlockPos anchor(BlockPos cell, Direction facing, boolean right, boolean upper) {
        requireHorizontal(facing);
        return cell.relative(facing.getClockWise(), right ? -1 : 0).below(upper ? 1 : 0);
    }

    public static boolean belongsTo(BlockPos cell, Direction facing, boolean right, boolean upper,
                                    BlockPos expectedAnchor, Direction expectedFacing) {
        return facing == expectedFacing && anchor(cell, facing, right, upper).equals(expectedAnchor);
    }

    private static void requireHorizontal(Direction facing) {
        if (facing.getAxis().isVertical()) throw new IllegalArgumentException("Machine facing must be horizontal");
    }
}
