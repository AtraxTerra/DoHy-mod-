package fr.atraxterra.dohy.agri;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * L'entite de bloc de la machine a OGM : elle ne garde rien. Elle existe pour
 * son rendu, le fouet du malaxeur qui tourne au-dessus de la cuve
 * ({@code BigMachineRenderer}).
 */
public class GmoMachineBlockEntity extends BlockEntity {

    public GmoMachineBlockEntity(BlockPos pos, BlockState state) {
        super(DoHyAgri.GMO_TYPE.get(), pos, state);
    }
}
