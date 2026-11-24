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
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

/**
 * La graine d'herbe coloree ({@code BlockColoredGrassSeeds} de PalaJobs, Fermier 45).
 *
 * <p>Le wiki : « Pour transformer la terre, il faut planter la graine sur de la
 * terre bechee et patienter. Il faudra mettre de la lumiere pour que les
 * graines poussent. La terre coloree n'est pas recuperable. » Le code : sur
 * terre labouree, a la lumiere (9 au moins), la graine pousse comme une
 * culture (taux de 25) ; quand elle pousse, la terre devient herbe coloree de
 * sa couleur et la graine disparait. La poudre d'os la fait pousser.
 */
public class ColoredGrassSeedBlock extends VegetationBlock implements BonemealableBlock {

    public static final MapCodec<ColoredGrassSeedBlock> CODEC = simpleCodec(properties ->
            new ColoredGrassSeedBlock(GrassColor.GREEN, () -> Blocks.GRASS_BLOCK, properties));

    /** {@code setBlockBounds(0.5 - 0.4, 0, 0.5 - 0.4, 0.5 + 0.4, 0.8 * 0.5, 0.5 + 0.4)}. */
    private static final VoxelShape SHAPE = Block.box(1.6d, 0.0d, 1.6d, 14.4d, 6.4d, 14.4d);

    private final GrassColor color;
    private final Supplier<Block> grass;

    public ColoredGrassSeedBlock(GrassColor color, Supplier<Block> grass, Properties properties) {
        super(properties);
        this.color = color;
        this.grass = grass;
    }

    public GrassColor color() {
        return color;
    }

    @Override
    protected MapCodec<? extends VegetationBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Seulement sur de la terre labouree ({@code canPlaceBlockOn : BlockFarmland}). */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(Blocks.FARMLAND);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    /** A la lumiere, une chance sur (25 / taux + 1) a chaque tick aleatoire, comme une culture. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getRawBrightness(pos, 0) < 9) {
            return;
        }
        float rate = RootSeedsBlock.growthRate(level, pos);
        if (random.nextInt((int) (25.0f / rate) + 1) == 0) {
            grow(level, pos);
        }
    }

    /**
     * La graine s'en va, puis la terre devient herbe coloree. Dans cet ordre :
     * la terre changee d'abord, la graine ne tiendrait plus et tomberait en
     * objet — une graine rendue a chaque pousse.
     */
    private void grow(ServerLevel level, BlockPos pos) {
        level.removeBlock(pos, false);
        level.setBlock(pos.below(), grass.get().defaultBlockState(), 3);
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
        grow(level, pos);
    }
}
