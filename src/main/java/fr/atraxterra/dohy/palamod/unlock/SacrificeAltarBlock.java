package fr.atraxterra.dohy.palamod.unlock;

import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.palamod.hunter.CaptureStoneItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * L'autel de sacrifice ({@code BlockSacrificeHotel}, Chasseur 100) : quatre
 * autels, a trois blocs du totem en endium, portent les sacrifies du rituel.
 *
 * <p>Dans PalaMod, un sacrifie est un joueur debout sur l'autel
 * ({@code getPlayerOfHotel}) : il en faut quatre, et ils meurent a la fin.
 *
 * <p><b>Adaptation solo.</b> L'autel accepte aussi une <b>pierre de capture
 * pleine</b>, posee d'un clic droit : la creature qu'elle tient est
 * l'offrande, et la pierre part avec le sacrifice. Accroupi, main vide, on
 * reprend sa pierre tant que le rituel n'a pas commence. Un autel qui porte
 * une offrande prend ses textures « actives ».
 */
public class SacrificeAltarBlock extends BaseEntityBlock {

    public static final MapCodec<SacrificeAltarBlock> CODEC = simpleCodec(SacrificeAltarBlock::new);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public SacrificeAltarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SacrificeAltarBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof CaptureStoneItem) || !CaptureStoneItem.isFull(stack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(level.getBlockEntity(pos) instanceof SacrificeAltarBlockEntity altar)) {
            return InteractionResult.PASS;
        }
        if (!altar.offering().isEmpty()) {
            tell(player, Component.translatable("block.palamod.sacrifice_hotel.taken"));
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            altar.offer(stack.copyWithCount(1));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            server.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0f, 0.8f);
            tell(player, Component.translatable("block.palamod.sacrifice_hotel.offered"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof SacrificeAltarBlockEntity altar)
                || altar.offering().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (EndiumRitual.runningAround(level, pos)) {
            tell(player, Component.translatable("block.palamod.sacrifice_hotel.locked"));
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel) {
            ItemStack stone = altar.take();
            if (!player.getInventory().add(stone)) {
                player.drop(stone, false);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SacrificeAltarBlockEntity altar
                && !altar.offering().isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5d, pos.getY() + 1.0d, pos.getZ() + 0.5d, altar.take());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Une offrande pose des ames au-dessus de l'autel. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(ACTIVE) && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SOUL, pos.getX() + 0.2d + random.nextDouble() * 0.6d, pos.getY() + 1.05d,
                    pos.getZ() + 0.2d + random.nextDouble() * 0.6d, 0.0d, 0.03d, 0.0d);
        }
    }

    static void tell(Player player, Component message) {
        if (player instanceof ServerPlayer server) {
            server.connection.send(new ClientboundSetActionBarTextPacket(message.copy().withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
    }
}
