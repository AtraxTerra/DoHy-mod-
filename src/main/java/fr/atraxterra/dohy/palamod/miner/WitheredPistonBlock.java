package fr.atraxterra.dohy.palamod.miner;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Piston renforce wither.
 *
 * <p><b>Ce n'est pas un piston</b>, malgre son nom : {@code BlockWitheredReinforcedPiston}
 * est un bloc simple, durete 5, oriente sur quatre directions, qui ne pousse rien.
 * Il sert au compresseur a cobblestone ({@link VoidStoneBlockEntity}).
 *
 * <p>Dans PalaMod, alimente, il change aussi l'obsidienne voisine en obsidienne
 * wither, le cadre du portail de la dimension de minage. Cette dimension n'est
 * pas portee, l'obsidienne wither non plus : ce comportement n'a rien a
 * transformer et n'est pas reproduit.
 *
 * <p>Il ne se fabrique pas : il tombe des pistons pris dans l'explosion
 * d'apparition d'un Wither ({@link WitheredPistonEvents}).
 */
public class WitheredPistonBlock extends HorizontalDirectionalBlock {

    public static final MapCodec<WitheredPistonBlock> CODEC = simpleCodec(WitheredPistonBlock::new);

    public WitheredPistonBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** La face avant regarde le joueur, comme un fourneau ({@code onBlockPlacedBy}, par le lacet). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
