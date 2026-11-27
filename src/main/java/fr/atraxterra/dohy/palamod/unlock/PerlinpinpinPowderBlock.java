package fr.atraxterra.dohy.palamod.unlock;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La poudre de perlimpinpin ({@code BlockPerlinpinpinPower}, Chasseur 100) :
 * une trainee posee au sol, haute d'un seizieme, qui trace le contour du
 * rituel d'endium (quarante poudres).
 *
 * <p>Comme dans PalaMod, son dessin suit ses voisines : une ligne quand elle
 * en touche deux en face, une croix sinon ({@code cross}, {@code line_vertical},
 * {@code line_horizontal}, {@code cross_default} seule). Pendant le rituel,
 * elle est « active » et lache des particules ({@code setPerlinpinpinActive}).
 */
public class PerlinpinpinPowderBlock extends Block {

    public static final MapCodec<PerlinpinpinPowderBlock> CODEC = simpleCodec(PerlinpinpinPowderBlock::new);

    /** Le dessin de la trainee. */
    public enum Trace implements StringRepresentable {
        ALONE("alone"),
        CROSS("cross"),
        NORTH_SOUTH("north_south"),
        EAST_WEST("east_west");

        private final String name;

        Trace(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Trace> TRACE = EnumProperty.create("trace", Trace.class);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /** Un seizieme de haut ({@code setBlockBounds(0, 0, 0, 1, 0.0625, 1)}). */
    private static final VoxelShape SHAPE = Block.box(0.0d, 0.0d, 0.0d, 16.0d, 1.0d, 16.0d);

    private static final DustParticleOptions SPARK = new DustParticleOptions(0xF0F0FF, 0.9f);

    public PerlinpinpinPowderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TRACE, Trace.ALONE).setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TRACE, ACTIVE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(TRACE, trace(context.getLevel(), context.getClickedPos()));
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState,
                                     RandomSource random) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return direction.getAxis().isHorizontal() ? state.setValue(TRACE, trace(level, pos)) : state;
    }

    /** Le dessin d'apres les voisines : deux en face, une ligne ; aucune, seule ; sinon, une croix. */
    static Trace trace(BlockGetter level, BlockPos pos) {
        boolean north = isPowder(level, pos.north());
        boolean south = isPowder(level, pos.south());
        boolean east = isPowder(level, pos.east());
        boolean west = isPowder(level, pos.west());
        boolean alongZ = north || south;
        boolean alongX = east || west;
        if (!alongZ && !alongX) {
            return Trace.ALONE;
        }
        if (alongZ && !alongX) {
            return Trace.NORTH_SOUTH;
        }
        if (alongX && !alongZ) {
            return Trace.EAST_WEST;
        }
        return Trace.CROSS;
    }

    private static boolean isPowder(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof PerlinpinpinPowderBlock;
    }

    /** Active : des etincelles blanches, comme la poussiere de redstone allumee. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE) || random.nextInt(3) != 0) {
            return;
        }
        level.addParticle(SPARK, pos.getX() + random.nextDouble(), pos.getY() + 0.1d,
                pos.getZ() + random.nextDouble(), 0.0d, 0.04d, 0.0d);
    }
}
