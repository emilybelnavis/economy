package dev.emily.economy;

import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;

/** A two by two machine with one loot-bearing lower-left anchor. */
public abstract class WideMachineBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    protected WideMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
            .setValue(HALF, DoubleBlockHalf.LOWER).setValue(RIGHT, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, RIGHT);
    }

    public static BlockPos anchor(BlockPos pos, BlockState state) {
        return WideMachineLayout.anchor(pos, state.getValue(FACING), state.getValue(RIGHT),
            state.getValue(HALF) == DoubleBlockHalf.UPPER);
    }

    private BlockState part(BlockState state, boolean right, boolean upper) {
        return state.setValue(RIGHT, right).setValue(HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
    }

    private boolean matches(BlockState candidate, BlockPos pos, BlockPos anchor, Direction facing,
                            boolean right, boolean upper) {
        return candidate.is(this) && candidate.getValue(RIGHT) == right
            && candidate.getValue(HALF) == (upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER)
            && WideMachineLayout.belongsTo(pos, candidate.getValue(FACING), right, upper, anchor, facing);
    }

    private boolean supported(LevelReader level, BlockPos anchor, Direction facing) {
        for (boolean right : new boolean[]{false, true}) {
            BlockPos floor = WideMachineLayout.cell(anchor, facing, right, false).below();
            if (!level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) return false;
        }
        return true;
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos anchor = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        for (boolean right : new boolean[]{false, true}) for (boolean upper : new boolean[]{false, true}) {
            BlockPos cell = WideMachineLayout.cell(anchor, facing, right, upper);
            if (!level.hasChunkAt(cell) || level.isOutsideBuildHeight(cell) || !level.getWorldBorder().isWithinBounds(cell)
                || !level.getBlockState(cell).canBeReplaced(context)) return null;
        }
        return supported(level, anchor, facing) ? defaultBlockState().setValue(FACING, facing) : null;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        // Publish every tile before notifying adjacent blocks; updateShape also defers validation.
        for (boolean right : new boolean[]{false, true}) for (boolean upper : new boolean[]{false, true}) {
            if (right || upper) level.setBlock(WideMachineLayout.cell(pos, facing, right, upper),
                part(state, right, upper), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        for (boolean right : new boolean[]{false, true}) for (boolean upper : new boolean[]{false, true}) {
            BlockPos cell = WideMachineLayout.cell(pos, facing, right, upper);
            level.updateNeighborsAt(cell, this);
            level.scheduleTick(cell, this, 1);
        }
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return footprintLoaded(level, anchor(pos, state), state.getValue(FACING))
            && supported(level, anchor(pos, state), state.getValue(FACING));
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        level.scheduleTick(pos, this, 1);
        return state;
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                             BlockPos neighborPos, boolean moving) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    private boolean footprintLoaded(LevelReader level, BlockPos root, Direction facing) {
        for (boolean right : new boolean[]{false, true}) for (boolean upper : new boolean[]{false, true}) {
            BlockPos cell = WideMachineLayout.cell(root, facing, right, upper);
            if (!level.hasChunkAt(cell) || (!upper && !level.hasChunkAt(cell.below()))) return false;
        }
        return true;
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos root = anchor(pos, state);
        Direction facing = state.getValue(FACING);
        // A neighboring chunk being unloaded is not a missing tile. Never force it to load.
        if (!footprintLoaded(level, root, facing)) {
            level.scheduleTick(pos, this, 20);
            return;
        }
        boolean complete = supported(level, root, facing);
        for (boolean right : new boolean[]{false, true}) for (boolean upper : new boolean[]{false, true}) {
            BlockPos cell = WideMachineLayout.cell(root, facing, right, upper);
            complete &= matches(level.getBlockState(cell), cell, root, facing, right, upper);
        }
        if (!complete) {
            if (matches(level.getBlockState(root), root, root, facing, false, false)) level.destroyBlock(root, true);
            else level.removeBlock(pos, false);
        } else {
            // Keep a persisted check per tile: cleanup may skip tiles in an unloaded chunk.
            // Once both chunks return, those tiles detect their missing anchor without loot.
            level.scheduleTick(pos, this, 40);
        }
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!level.isClientSide && !replacement.is(this)) {
            BlockPos root = anchor(pos, state);
            Direction facing = state.getValue(FACING);
            for (boolean right : new boolean[]{false, true}) for (boolean upper : new boolean[]{false, true}) {
                BlockPos cell = WideMachineLayout.cell(root, facing, right, upper);
                // Leave unloaded tiles for their persisted validation tick instead of loading chunks.
                if (!cell.equals(pos) && level.hasChunkAt(cell)
                    && matches(level.getBlockState(cell), cell, root, facing, right, upper))
                    level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                                        BlockEntity blockEntity, ItemStack tool) {
        // Vanilla already checked the tool and game mode. Use canonical loot conditions from any tile.
        super.playerDestroy(level, player, pos, part(state, false, false), blockEntity, tool);
    }

    @Override protected void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion,
                                            BiConsumer<ItemStack, BlockPos> drops) {
        BlockPos root = anchor(pos, state);
        if (!level.hasChunkAt(root)) {
            level.removeBlock(pos, false);
            return;
        }
        BlockState rootState = level.getBlockState(root);
        if (matches(rootState, root, root, state.getValue(FACING), false, false))
            super.onExplosionHit(rootState, level, root, explosion, drops);
        else if (level.getBlockState(pos).is(this)) level.removeBlock(pos, false);
    }

    @Override public PushReaction getPistonPushReaction(BlockState state) { return PushReaction.BLOCK; }
    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state.rotate(mirror.getRotation(state.getValue(FACING)));
        // Reflections reverse the footprint's handedness as well as its facing.
        return mirror == Mirror.NONE ? mirrored : mirrored.setValue(RIGHT, !state.getValue(RIGHT));
    }
}
