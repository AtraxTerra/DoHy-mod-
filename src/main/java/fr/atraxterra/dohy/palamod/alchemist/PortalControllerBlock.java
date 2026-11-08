package fr.atraxterra.dohy.palamod.alchemist;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Bloc de portail ({@code BlockAlchemistPortal}) : le controleur d'un portail
 * d'Alchimiste. Clic droit avec la cle de son metal : elle s'insere ; main vide :
 * elle ressort, avec la seve qu'elle a accumulee.
 */
public class PortalControllerBlock extends BaseEntityBlock {

    public static final MapCodec<PortalControllerBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            PortalTier.CODEC.fieldOf("tier").forGetter(PortalControllerBlock::tier),
            propertiesCodec()
    ).apply(instance, PortalControllerBlock::new));

    private final PortalTier tier;

    public PortalControllerBlock(PortalTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public PortalTier tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortalControllerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof PortalControllerBlockEntity controller) {
                controller.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof PortalKeyItem key)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer server
                && level.getBlockEntity(pos) instanceof PortalControllerBlockEntity controller) {
            if (key.tier() != tier) {
                AlchemistXp.actionBar(server, Component.translatable("message.palamod.portal.wrong_key")
                        .withStyle(ChatFormatting.RED));
            } else if (!controller.insert(server, stack)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer server
                && level.getBlockEntity(pos) instanceof PortalControllerBlockEntity controller) {
            controller.removeKey(server);
        }
        return InteractionResult.SUCCESS;
    }
}
