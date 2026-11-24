package fr.atraxterra.dohy.palamod.unlock;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La graine de racinier ({@code BlockRootSeeds}, Fermier 50) : elle pousse sur
 * de la terre labouree en trois stades ({@code root_seeds_0} a {@code 2}),
 * a la lumiere (9 au moins), comme une culture (taux de 25).
 *
 * <p>Au quatrieme stade, le racinier sort ({@code setBlocks}, puis
 * {@code TileEntityRoot.spawnStructure}) : le feuillage a la place de la
 * graine, trois buches <b>sous</b> lui — la terre labouree comprise — et le
 * bout de la racine en dessous. La racine descend ensuite toute seule
 * ({@link RootLeavesBlockEntity}).
 */
public class RootSeedsBlock extends VegetationBlock implements BonemealableBlock {

    public static final MapCodec<RootSeedsBlock> CODEC = simpleCodec(RootSeedsBlock::new);
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 2);

    private static final VoxelShape SHAPE = Block.box(0.0d, 0.0d, 0.0d, 16.0d, 4.0d, 16.0d);

    public RootSeedsBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends VegetationBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.FARMLAND);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getRawBrightness(pos, 0) < 9) {
            return;
        }
        if (random.nextInt((int) (25.0f / growthRate(level, pos)) + 1) == 0) {
            advance(level, pos, state);
        }
    }

    /** Un stade de plus ; apres le dernier, le racinier sort. */
    private static void advance(ServerLevel level, BlockPos pos, BlockState state) {
        int age = state.getValue(AGE);
        if (age < 2) {
            level.setBlock(pos, state.setValue(AGE, age + 1), 2);
        } else {
            RootLeavesBlockEntity.sprout(level, pos);
        }
    }

    /**
     * Le taux de pousse d'une culture de 1.7.10 ({@code BlockCrops.func_149864_n}) :
     * 1, plus chaque terre labouree des neuf sous la plante (3 si humide), les
     * huit voisines comptant pour un quart.
     */
    static float growthRate(BlockGetter level, BlockPos pos) {
        float rate = 1.0f;
        BlockPos below = pos.below();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockState soil = level.getBlockState(below.offset(dx, 0, dz));
                if (!soil.is(Blocks.FARMLAND)) {
                    continue;
                }
                float value = soil.getValue(BlockStateProperties.MOISTURE) > 0 ? 3.0f : 1.0f;
                rate += dx == 0 && dz == 0 ? value : value / 4.0f;
            }
        }
        return rate;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        advance(level, pos, state);
    }
}
