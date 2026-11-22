package fr.atraxterra.dohy.underground;

import fr.atraxterra.dohy.faction.enterprise.Line;
import fr.atraxterra.dohy.palamod.PalaCropBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Une plante des affaires illegales (lot 24c) : cannabis, coca, pavot. Huit stades comme le
 * ble, mais elle ne pousse qu'a la latitude de sa drogue ({@link Line#growsAt}) : le cannabis
 * presque partout, la coca sous les tropiques, le pavot entre 15 et 45 degres. Sans carte de la
 * Terre, la latitude se lit sur la temperature du biome ({@link Latitudes}).
 */
public class DrugCropBlock extends PalaCropBlock {

    private final Line line;

    public DrugCropBlock(Properties properties, Line line) {
        super(properties, 0.8f);
        this.line = line;
    }

    public Line line() {
        return line;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (Latitudes.grows(line, level, pos)) {
            super.randomTick(state, level, pos, random);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return Latitudes.grows(line, level, pos) && super.isValidBonemealTarget(level, pos, state);
    }
}
