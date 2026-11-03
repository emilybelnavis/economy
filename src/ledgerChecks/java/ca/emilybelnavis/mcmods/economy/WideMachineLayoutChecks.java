package ca.emilybelnavis.mcmods.economy;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Standalone checks: no level, registries, or Minecraft client required. */
public final class WideMachineLayoutChecks {
    public static void main(String[] args) {
        int checks = 0;
        Direction[] facings = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        Direction[] across = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
        BlockPos root = new BlockPos(-37, 63, 19);
        for (int i = 0; i < facings.length; i++) {
            Direction facing = facings[i];
            Set<BlockPos> cells = new HashSet<>();
            for (boolean right : new boolean[]{false, true}) for (boolean upper : new boolean[]{false, true}) {
                BlockPos cell = WideMachineLayout.cell(root, facing, right, upper);
                require(cell.equals(root.relative(across[i], right ? 1 : 0).above(upper ? 1 : 0)), "orientation");
                require(WideMachineLayout.anchor(cell, facing, right, upper).equals(root), "inverse");
                require(WideMachineLayout.belongsTo(cell, facing, right, upper, root, facing), "owner");
                require(!WideMachineLayout.belongsTo(cell, facing, right, upper, root.relative(across[i], 2), facing), "adjacent owner");
                require(!WideMachineLayout.belongsTo(cell, facing, right, upper, root, facing.getOpposite()), "opposite facing");
                // Mislabelled halves/columns must never be treated as the original machine's tile.
                require(!WideMachineLayout.belongsTo(cell, facing, !right, upper, root, facing), "column mismatch");
                require(!WideMachineLayout.belongsTo(cell, facing, right, !upper, root, facing), "half mismatch");
                require(cells.add(cell), "distinct tiles");
                checks += 8;
            }
            require(cells.size() == 4, "four cells");
            BlockPos nextRoot = root.relative(across[i], 2);
            for (boolean right : new boolean[]{false, true}) for (boolean upper : new boolean[]{false, true}) {
                BlockPos next = WideMachineLayout.cell(nextRoot, facing, right, upper);
                require(!cells.contains(next), "adjacent footprints overlap");
                require(!WideMachineLayout.belongsTo(next, facing, right, upper, root, facing), "cleanup crosses boundary");
                checks += 2;
            }
            checks++;
        }
        for (Direction vertical : new Direction[]{Direction.UP, Direction.DOWN}) {
            try {
                WideMachineLayout.cell(root, vertical, false, false);
                throw new AssertionError("vertical facing accepted");
            } catch (IllegalArgumentException expected) { checks++; }
        }
        System.out.println("Wide machine layout checks passed: " + checks);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
