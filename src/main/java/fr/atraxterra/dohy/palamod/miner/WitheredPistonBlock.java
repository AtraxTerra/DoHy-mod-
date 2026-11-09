package fr.atraxterra.dohy.palamod.miner;

import fr.atraxterra.dohy.palamod.dimminer.PalaDimMiner;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.redstone.Orientation;

/**
 * Piston renforce wither.
 *
 * <p><b>Ce n'est pas un piston</b>, malgre son nom : {@code BlockWitheredReinforcedPiston}
 * est un bloc simple, durete 5, oriente sur quatre directions, qui ne pousse rien.
 * Il sert au compresseur a cobblestone ({@link VoidStoneBlockEntity}).
 *
 * <p>Alimente, il change l'obsidienne voisine en obsidienne wither, le cadre du
 * portail de la dimension du Mineur ({@code onNeighborBlockChange}) : une seule
 * par mise a jour, dans l'ordre x+1, x-1, z+1, z-1, jamais au-dessus ni
 * au-dessous.
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

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
                                   Orientation orientation, boolean moving) {
        if (level.isClientSide() || !level.hasNeighborSignal(pos)) {
            return;
        }
        for (Direction direction : new Direction[]{Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH}) {
            BlockPos side = pos.relative(direction);
            if (level.getBlockState(side).is(Blocks.OBSIDIAN)) {
                level.setBlock(side, PalaDimMiner.WITHERED_OBSIDIAN.get().defaultBlockState(), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5f,
                        0.6f + level.getRandom().nextFloat() * 0.25f);
                return;
            }
        }
    }

    /** La face avant regarde le joueur, comme un fourneau ({@code onBlockPlacedBy}, par le lacet). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
