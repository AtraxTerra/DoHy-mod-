package fr.atraxterra.dohy.palamod.defense;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le brouilleur de coordonnees ({@code BlockCoordinateJammer}).
 *
 * <p>Sur Paladium, il affiche de fausses coordonnees aux joueurs proches
 * (entre 1 000 et 25 000 blocs a cote des vraies) : on ne retrouve pas une base
 * en lisant son F3. <b>En solo, il brouille les monstres</b> : a 16 blocs, un
 * monstre qui vise un joueur a plus de 6 blocs le perd de vue. Une base
 * brouillee ne se fait reperer que de pres.
 */
public class CoordinateJammerBlock extends BaseEntityBlock {

    public static final MapCodec<CoordinateJammerBlock> CODEC = simpleCodec(CoordinateJammerBlock::new);

    public CoordinateJammerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoordinateJammerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof CoordinateJammerBlockEntity jammer) {
                CoordinateJammerBlockEntity.tick(tickLevel, pos, tickState, jammer);
            }
        };
    }
}
