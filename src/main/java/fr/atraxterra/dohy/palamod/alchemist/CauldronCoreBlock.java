package fr.atraxterra.dohy.palamod.alchemist;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Coeur du chaudron ({@code BlockCauldronCore}). Pose au centre d'un plancher
 * et d'un anneau complets ({@link CauldronStructure}), il les assemble : la
 * foudre tombe sur lui, et le chaudron est forme.
 *
 * <p><b>Ecart</b> : l'original ne verifiait qu'a la pose ; ici, un clic droit
 * main vide sur un coeur pose trop tot refait la verification.
 */
public class CauldronCoreBlock extends BaseEntityBlock {

    public static final MapCodec<CauldronCoreBlock> CODEC = simpleCodec(CauldronCoreBlock::new);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public CauldronCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CauldronCoreBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof CauldronCoreBlockEntity core) {
                core.serverTick();
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server) {
            tryForm(server, pos, state, placer instanceof ServerPlayer player ? player : null);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            if (state.getValue(FORMED)) {
                AlchemistXp.actionBar(serverPlayer, Component.translatable("message.palamod.cauldron.formed")
                        .withStyle(ChatFormatting.GREEN));
            } else {
                tryForm(server, pos, state, serverPlayer);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** {@code func_72921_c(x, y, z, 1, 2)} puis l'eclair : le chaudron est forme. */
    static void tryForm(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        if (!CauldronStructure.isComplete(level, pos)) {
            if (player != null) {
                AlchemistXp.actionBar(player, Component.translatable("message.palamod.cauldron.incomplete")
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }
        level.setBlock(pos, state.setValue(FORMED, true), Block.UPDATE_ALL);
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            // Un vrai eclair dans l'original ; ici pour l'image, sans feu ni degats.
            bolt.snapTo(pos.getX() + 0.5d, pos.getY() + 1.0d, pos.getZ() + 0.5d, 0.0f, 0.0f);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        if (player != null) {
            AlchemistXp.actionBar(player, Component.translatable("message.palamod.cauldron.formed")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /** Un coeur forme qui disparait vide son bassin ({@code unmount}). */
    static boolean isFormed(BlockState state) {
        return state.hasProperty(FORMED) && state.getValue(FORMED);
    }
}
