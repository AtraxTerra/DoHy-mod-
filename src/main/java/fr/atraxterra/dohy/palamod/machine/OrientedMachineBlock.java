package fr.atraxterra.dohy.palamod.machine;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Une machine a face avant, allumee ou eteinte : la Palafurnace et la Palaforge.
 *
 * <p>PalaMod enregistrait deux blocs par machine ({@code forge} et
 * {@code lit_forge}) et basculait de l'un a l'autre ; en 26.2 c'est une
 * propriete d'etat, {@code lit}, comme pour le fourneau vanilla. La face avant
 * regarde le joueur a la pose. Aucune des deux n'eclaire : l'original ne
 * declarait pas de lumiere pour la version allumee.
 */
public abstract class OrientedMachineBlock extends MachineBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    protected OrientedMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
