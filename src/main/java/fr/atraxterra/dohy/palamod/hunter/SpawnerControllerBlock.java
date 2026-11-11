package fr.atraxterra.dohy.palamod.hunter;

import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.palamod.machine.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le controleur de spawners ({@code BlockSpawnerController}) : pose au sommet
 * d'une structure, il fait travailler les spawners customises qu'elle abrite
 * ({@link SpawnerControllerBlockEntity}). Roche, durete 2.
 */
public class SpawnerControllerBlock extends MachineBlock {

    public static final MapCodec<SpawnerControllerBlock> CODEC = simpleCodec(SpawnerControllerBlock::new);

    public SpawnerControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpawnerControllerBlockEntity(pos, state);
    }
}
