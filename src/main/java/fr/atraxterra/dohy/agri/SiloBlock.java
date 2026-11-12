package fr.atraxterra.dohy.agri;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Le silo : le grenier de la ferme. Un tracteur qui passe a cote y vide sa
 * tremie ; un clic l'ouvre ({@link SiloMenu}), un clic avec une cereale en
 * main l'y depose. Le stock est dans son entite ({@link SiloBlockEntity}).
 */
public class SiloBlock extends BaseEntityBlock {

    public static final MapCodec<SiloBlock> CODEC = simpleCodec(SiloBlock::new);

    public SiloBlock(Properties properties) {
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
        return new SiloBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(DoHyAgri.CEREALS)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(player instanceof ServerPlayer server) || !(level.getBlockEntity(pos) instanceof SiloBlockEntity silo)) {
            return InteractionResult.SUCCESS;
        }
        int taken = silo.deposit(stack.getItem(), stack.getCount());
        if (taken <= 0) {
            server.sendSystemMessage(Component.literal("Le silo est plein.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(taken);
        }
        level.playSound(null, pos, SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 0.8f, 0.8f);
        server.sendSystemMessage(Component.literal("Silo : " + silo.total() + " / " + SiloBlockEntity.capacity()
                + " cereales.").withStyle(ChatFormatting.GRAY), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof SiloBlockEntity) {
            SiloMenu.INSTANCE.openAt(server, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
