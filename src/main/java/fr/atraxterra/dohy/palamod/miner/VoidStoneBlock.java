package fr.atraxterra.dohy.palamod.miner;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * La void stone posee : le coeur du compresseur a cobblestone.
 *
 * <p>{@code BlockVoidStone} : durete 5, aucun drop par la table de butin. A la
 * casse par un joueur, elle rend une Voidstone minage avec sa reserve restante —
 * meme en creatif, comme l'original. Une explosion, elle, fait tout perdre :
 * c'est le cas en 1.7.10, et c'est conserve.
 */
public class VoidStoneBlock extends BaseEntityBlock {

    public static final MapCodec<VoidStoneBlock> CODEC = simpleCodec(VoidStoneBlock::new);

    public VoidStoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VoidStoneBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof VoidStoneBlockEntity voidStone) {
                voidStone.serverTick();
            }
        };
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VoidStoneBlockEntity voidStone) {
            ItemStack minage = new ItemStack(PalaMiner.VOIDSTONE_MINAGE.get());
            VoidstoneMinageItem.setStone(minage, voidStone.stone());
            Block.popResource(level, pos, minage);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
