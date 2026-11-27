package fr.atraxterra.dohy.palamod.unlock;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Le totem en endium ({@code BlockEndiumTotem}, Chasseur 100) : le coeur du
 * rituel d'endium. Le wiki : « Bloc uniquement utile pour les sacrifices a
 * endium. Apres le sacrifice, le bloc est detruit. »
 *
 * <p>Le deroulement est dans {@link EndiumTotemBlockEntity} ; ce bloc ne fait
 * que recevoir le clic qui, le sacrifice fait, revele le coffre.
 */
public class EndiumTotemBlock extends BaseEntityBlock {

    public static final MapCodec<EndiumTotemBlock> CODEC = simpleCodec(EndiumTotemBlock::new);

    public EndiumTotemBlock(Properties properties) {
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
        return new EndiumTotemBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != PalaUnlocks.ENDIUM_TOTEM_TYPE.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> ((EndiumTotemBlockEntity) entity).serverTick();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof EndiumTotemBlockEntity totem)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            totem.click(player);
        }
        return InteractionResult.SUCCESS;
    }
}
