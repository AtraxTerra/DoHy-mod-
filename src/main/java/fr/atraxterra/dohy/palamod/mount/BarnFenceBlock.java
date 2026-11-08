package fr.atraxterra.dohy.palamod.mount;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La barriere d'enclos ({@code BlockBarnFence}) : une barriere qui se relie
 * aux autres, mais dont la boite de collision ne fait qu'un bloc de haut au
 * lieu d'un et demi. Le Dancarok, qui franchit une marche d'un bloc, entre et
 * sort de son enclos.
 */
public class BarnFenceBlock extends FenceBlock {

    public BarnFenceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }
}
