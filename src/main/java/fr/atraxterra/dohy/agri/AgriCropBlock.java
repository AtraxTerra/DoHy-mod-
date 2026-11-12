package fr.atraxterra.dohy.agri;

import fr.atraxterra.dohy.palamod.PalaCropBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Une culture de cereale : huit stades, comme le ble, avec une cadence propre
 * ({@link PalaCropBlock} saute une part des ticks de croissance).
 *
 * <p>Hors de son climat, une cereale naturelle ne pousse pas et la poudre d'os
 * n'y fait rien. La semence refuse deja d'y etre plantee ({@link SeedItem}) ;
 * ce second verrou couvre tout ce qui pose un bloc sans passer par elle.
 */
public class AgriCropBlock extends PalaCropBlock {

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
}
