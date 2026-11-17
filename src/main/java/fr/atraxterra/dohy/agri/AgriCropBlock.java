package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.palamod.PalaCropBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Une culture de cereale : huit stades, comme le ble, avec une cadence propre
 * ({@link PalaCropBlock} saute une part des ticks de croissance).
 *
 * <p>Hors de son climat, une cereale naturelle ne pousse pas et la poudre d'os
 * n'y fait rien. La semence refuse deja d'y etre plantee ({@link SeedItem}) ;
 * ce second verrou couvre tout ce qui pose un bloc sans passer par elle.
 */
public class AgriCropBlock extends PalaCropBlock {

    /**
     * Le contour des cereales hautes ({@link Cereal#tall()}), stade par stade : il suit la
     * plante, qui depasse le bloc a partir du cinquieme stade (jusqu'a pres de deux blocs).
     */
    private static final VoxelShape[] TALL_SHAPES = shapes(4, 6, 9, 14, 22, 26, 30, 31);

    private final Cereal cereal;

    public AgriCropBlock(Properties properties, Cereal cereal) {
        super(properties, cereal.growth());
        this.cereal = cereal;
    }

    public Cereal cereal() {
        return cereal;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (cereal.growsAt(level, pos)) {
            super.randomTick(state, level, pos, random);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return cereal.growsAt(level, pos) && super.isValidBonemealTarget(level, pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (cereal.tall()) {
            return TALL_SHAPES[state.getValue(BlockStateProperties.AGE_7)];
        }
        return super.getShape(state, level, pos, context);
    }

    private static VoxelShape[] shapes(int... heights) {
        VoxelShape[] shapes = new VoxelShape[heights.length];
        for (int age = 0; age < heights.length; age++) {
            shapes[age] = Block.box(0, 0, 0, 16, heights[age], 16);
        }
        return shapes;
    }
}
