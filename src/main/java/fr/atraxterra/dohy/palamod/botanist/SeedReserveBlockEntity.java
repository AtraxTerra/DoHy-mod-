package fr.atraxterra.dohy.palamod.botanist;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * La reserve posee : rien a garder (la reserve est au joueur), elle ne sert qu'a son rendu
 * ({@code TileEntityBotanistReserve}, qui ne joue que son animation d'attente).
 */
public class SeedReserveBlockEntity extends BlockEntity {

    public SeedReserveBlockEntity(BlockPos pos, BlockState state) {
        super(PalaBotanist.SEED_RESERVE_TYPE.get(), pos, state);
    }
}
