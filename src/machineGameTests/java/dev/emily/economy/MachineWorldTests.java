package dev.emily.economy;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-world regression checks; every mutation stays within the 16 by 8 by 16 fixture. */
@GameTestHolder("economy")
@PrefixGameTestTemplate(false)
public final class MachineWorldTests {
    private static final BlockPos ROOT = new BlockPos(6, 2, 6);
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void placementAndObstruction(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (WideMachineBlock block : machines(helper)) for (Direction facing : FACINGS) {
            reset(helper);
            place(helper, player, block, facing, ROOT, true);
            assertMachine(helper, block, facing, ROOT);
            reset(helper);
            BlockPos obstacle = cell(helper, ROOT, facing, true, true);
            helper.getLevel().setBlock(obstacle, Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
            place(helper, player, block, facing, ROOT, false);
            helper.assertTrue(helper.getLevel().getBlockState(obstacle).is(Blocks.OBSIDIAN), "Placement overwrote obstruction");
            for (boolean right : new boolean[]{false, true}) for (boolean upper : new boolean[]{false, true}) {
                if (!(right && upper)) helper.assertTrue(helper.getLevel().getBlockState(cell(helper, ROOT, facing, right, upper)).isAir(),
                    "Rejected placement left a machine tile");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void survivalLootFromEveryPart(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (WideMachineBlock block : machines(helper)) for (Direction facing : FACINGS)
            for (boolean right : new boolean[]{false, true}) for (boolean upper : new boolean[]{false, true}) {
                reset(helper);
                place(helper, player, block, facing, ROOT, true);
                BlockPos selected = cell(helper, ROOT, facing, right, upper);
                BlockState state = helper.getLevel().getBlockState(selected);
                ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
                player.setItemInHand(InteractionHand.MAIN_HAND, tool);
                helper.assertTrue(tool.isCorrectToolForDrops(state), "Fixture tool cannot harvest machine");
                // Match the server's destruction order: remove first, then invoke the player loot path.
                helper.getLevel().destroyBlock(selected, false, player);
                block.playerDestroy(helper.getLevel(), player, selected, state, null, tool);
                assertGone(helper, ROOT, facing);
                assertDrops(helper, block, 1);
            }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void creativeRemovalAndAdjacentIsolation(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (WideMachineBlock block : machines(helper)) for (Direction facing : FACINGS)
            for (boolean right : new boolean[]{false, true}) for (boolean upper : new boolean[]{false, true}) {
                reset(helper);
                BlockPos adjacent = ROOT.relative(facing.getClockWise(), 2);
                place(helper, player, block, facing, ROOT, true);
                place(helper, player, block, facing, adjacent, true);
                // Creative destruction removes the selected state and never calls playerDestroy.
                helper.getLevel().setBlock(cell(helper, ROOT, facing, right, upper), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                assertGone(helper, ROOT, facing);
                assertMachine(helper, block, facing, adjacent);
                assertDrops(helper, block, 0);
            }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void supportLossDropsOneItem(GameTestHelper helper) {
        supportCase(helper, machines(helper), helper.makeMockPlayer(GameType.SURVIVAL), 0);
    }

    private static void supportCase(GameTestHelper helper, List<WideMachineBlock> blocks, Player player, int index) {
        if (index == blocks.size()) {
            helper.succeed();
            return;
        }
        reset(helper);
        WideMachineBlock block = blocks.get(index);
        Direction facing = FACINGS[index % FACINGS.length];
        place(helper, player, block, facing, ROOT, true);
        // Alternate the two supporting cells; both must independently support the footprint.
        BlockPos floor = cell(helper, ROOT, facing, index % 2 == 0, false).below();
        helper.getLevel().setBlock(floor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(3, () -> {
            assertGone(helper, ROOT, facing);
            assertDrops(helper, block, 1);
            supportCase(helper, blocks, player, index + 1);
        });
    }

    private static List<WideMachineBlock> machines(GameTestHelper helper) {
        List<WideMachineBlock> blocks = BuiltInRegistries.BLOCK.stream()
            .filter(block -> block instanceof WideMachineBlock)
            .map(block -> (WideMachineBlock) block)
            .sorted(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString())).toList();
        helper.assertTrue(blocks.size() == 19, "Expected all 19 machine types, got " + blocks.size());
        return blocks;
    }

    private static void place(GameTestHelper helper, Player player, WideMachineBlock block, Direction facing,
                              BlockPos localRoot, boolean expectedSuccess) {
        ItemStack stack = new ItemStack(block.asItem(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setYRot(facing.getOpposite().toYRot());
        player.setXRot(0);
        BlockPos floor = helper.absolutePos(localRoot).below();
        player.setPos(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 3.5);
        BlockHitResult hit = new BlockHitResult(new Vec3(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5),
            Direction.UP, floor, false);
        helper.assertTrue(block.asItem() instanceof BlockItem, "Machine item is not a BlockItem");
        boolean placed = ((BlockItem) block.asItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit)).consumesAction();
        helper.assertTrue(placed == expectedSuccess, "Unexpected placement result for " + BuiltInRegistries.BLOCK.getKey(block) + " facing " + facing);
        helper.assertTrue(stack.getCount() == (expectedSuccess ? 1 : 2), "Placement consumed wrong number of items");
    }

    private static BlockPos cell(GameTestHelper helper, BlockPos localRoot, Direction facing, boolean right, boolean upper) {
        return WideMachineLayout.cell(helper.absolutePos(localRoot), facing, right, upper);
    }

    private static void assertMachine(GameTestHelper helper, WideMachineBlock block, Direction facing, BlockPos root) {
        for (boolean right : new boolean[]{false, true}) for (boolean upper : new boolean[]{false, true}) {
            BlockState state = helper.getLevel().getBlockState(cell(helper, root, facing, right, upper));
            helper.assertTrue(state.is(block), "Missing machine part");
            helper.assertTrue(state.getValue(WideMachineBlock.FACING) == facing, "Wrong facing");
            helper.assertTrue(state.getValue(WideMachineBlock.RIGHT) == right, "Wrong column");
            helper.assertTrue(state.getValue(WideMachineBlock.HALF) == (upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER), "Wrong half");
        }
    }

    private static void assertGone(GameTestHelper helper, BlockPos root, Direction facing) {
        for (boolean right : new boolean[]{false, true}) for (boolean upper : new boolean[]{false, true})
            helper.assertTrue(helper.getLevel().getBlockState(cell(helper, root, facing, right, upper)).isAir(), "Machine part survived removal");
    }

    private static List<ItemEntity> drops(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(Vec3.atLowerCornerOf(origin), Vec3.atLowerCornerOf(origin.offset(16, 8, 16))), entity -> entity.isAlive());
    }

    private static void assertDrops(GameTestHelper helper, WideMachineBlock block, int expected) {
        int total = 0;
        for (ItemEntity item : drops(helper)) {
            helper.assertTrue(item.getItem().is(block.asItem()), "Unexpected dropped item " + item.getItem());
            total += item.getItem().getCount();
        }
        helper.assertTrue(total == expected, "Expected " + expected + " machine items, got " + total);
    }

    private static void reset(GameTestHelper helper) {
        drops(helper).forEach(ItemEntity::discard);
        for (int x = 2; x <= 12; x++) for (int z = 2; z <= 12; z++) {
            for (int y = 2; y <= 4; y++) helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)),
                Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        drops(helper).forEach(ItemEntity::discard);
    }
}
