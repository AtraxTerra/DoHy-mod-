package fr.atraxterra.dohy.palamod.trixium;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le nuage pose par la fabrique ({@code BlockCloud}).
 *
 * <p>A la pose ({@code onBlockPlacedBy}), il recoit la duree de la fabrique, en
 * ticks ; pose par autre chose qu'une fabrique, il disparait aussitot.
 */
public class CloudBlock extends BaseEntityBlock {

    public static final MapCodec<CloudBlock> CODEC = simpleCodec(CloudBlock::new);

    public CloudBlock(Properties properties) {
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
        return new CloudBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }
        if (stack.getItem() instanceof CloudItem && level.getBlockEntity(pos) instanceof CloudBlockEntity cloud) {
            cloud.setRemaining(CloudItem.duration(stack) * 20);
        } else {
            level.removeBlock(pos, false);
        }
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof CloudBlockEntity cloud) {
                cloud.serverTick();
            }
        };
    }
}
