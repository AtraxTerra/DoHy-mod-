package fr.atraxterra.dohy.palamod.unlock;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Le feuillage du racinier ({@code BlockRootLeaves}) : il porte la racine
 * ({@link RootLeavesBlockEntity}) et dit ou elle en est — vert tant qu'elle
 * descend, rouge quand elle est arretee ({@code isBlocked} : {@code _off}).
 *
 * <p>Arretee avant la bedrock, c'est qu'il y a de l'obsidienne dessous : c'est
 * ainsi que le racinier trouve une base enterree. Casse, il retire tout le
 * racinier, buches et racine, et laisse un puits ({@code breakBlock}). Il ne
 * lache rien.
 */
public class RootLeavesBlock extends BaseEntityBlock {

    public static final MapCodec<RootLeavesBlock> CODEC = simpleCodec(RootLeavesBlock::new);
    public static final BooleanProperty BLOCKED = BooleanProperty.create("blocked");

    public RootLeavesBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BLOCKED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BLOCKED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RootLeavesBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != PalaUnlocks.ROOT_LEAVES_TYPE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> ((RootLeavesBlockEntity) entity).serverTick();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            RootLeavesBlockEntity.clearBelow(level, pos);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
