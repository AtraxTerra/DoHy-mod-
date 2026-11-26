package fr.atraxterra.dohy.casino;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Un bloc de casino (« Entreprise de casino » de NationsGlory) : il est a qui le pose. Les quatre
 * blocs ne conduisent pas le courant, pour que la machine aleatoire ne laisse jamais filer par son
 * corps un signal qu'elle a refuse.
 */
public abstract class CasinoBlock extends BaseEntityBlock {

    protected CasinoBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CasinoBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof CasinoBlockEntity casino) {
            casino.setOwner(player);
        }
    }

    /** « a Dev » : le maitre du bloc, pour les messages. */
    static String owner(CasinoBlockEntity casino) {
        return casino.ownerName().isEmpty() ? "personne" : casino.ownerName();
    }
}
