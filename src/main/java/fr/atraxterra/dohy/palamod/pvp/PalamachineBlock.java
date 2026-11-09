package fr.atraxterra.dohy.palamod.pvp;

import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.palamod.machine.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * La palamachine ({@code BlockPaladiumMachine}) : un cube, cotes et dessus a
 * part, sans orientation. Voir {@link PalamachineBlockEntity}.
 */
public class PalamachineBlock extends MachineBlock {

    public static final MapCodec<PalamachineBlock> CODEC = simpleCodec(PalamachineBlock::new);

    public PalamachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PalamachineBlockEntity(pos, state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
