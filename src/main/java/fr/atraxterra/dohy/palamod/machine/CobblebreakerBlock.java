package fr.atraxterra.dohy.palamod.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Le bloc du cobblebreaker.
 *
 * <p><b>Le palier est une propriete d'etat, pas seulement une donnee de la
 * machine.</b> C'est ce qui fait changer la texture quand on l'ameliore : le
 * joueur voit de l'exterieur si sa machine est au palier titane ou paladium,
 * sans rien ouvrir. Ce n'est pas cosmetique — c'est ce qui remplace la barre de
 * progression qu'un mod sans code client ne peut pas dessiner (voir
 * {@link MachineBlockEntity}).
 *
 * <p>L'etat et la machine portent donc tous deux le palier. {@link PalaMachineEvents}
 * les met a jour ensemble au moment de l'amelioration ; aucun autre chemin ne
 * peut les faire diverger.
 */
public class CobblebreakerBlock extends MachineBlock {

    public static final EnumProperty<CobblebreakerTier> TIER =
            EnumProperty.create("tier", CobblebreakerTier.class);

    public static final MapCodec<CobblebreakerBlock> CODEC = simpleCodec(CobblebreakerBlock::new);

    public CobblebreakerBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(TIER, CobblebreakerTier.DEFAULT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(TIER);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CobblebreakerBlockEntity(pos, state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
