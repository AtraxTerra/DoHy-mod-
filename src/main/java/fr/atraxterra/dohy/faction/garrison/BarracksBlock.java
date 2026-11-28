package fr.atraxterra.dohy.faction.garrison;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * La caserne : un corps de garde creneele, sa porte tournee vers celui qui la pose, un fanion sur
 * le toit. Une par joueur, dans ses claims ; sa garnison y loge et s'y poste. Un clic ouvre la
 * garnison.
 */
public class BarracksBlock extends BaseEntityBlock {

    public static final MapCodec<BarracksBlock> CODEC = simpleCodec(BarracksBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BarracksBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Hors de ses claims, ou une deuxieme caserne : la pose est refusee, l'objet reste en main. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            String refusal = Garrisons.barracksRefusal(player, context.getLevel(), context.getClickedPos());
            if (refusal != null) {
                player.sendSystemMessage(Component.literal(refusal).withStyle(ChatFormatting.RED));
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BarracksBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof BarracksBlockEntity barracks) {
            barracks.setOwner(player);
            Garrisons.placeBarracks(player, level, pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof BarracksBlockEntity barracks) {
            if (barracks.isOwner(server)) {
                Garrisons.adopt(server, level, pos);
                GarrisonMenu.INSTANCE.open(server);
            } else {
                server.sendSystemMessage(Component.literal(barracks.ownerName().isEmpty() ? "Une caserne sans maitre."
                        : "La caserne de " + barracks.ownerName() + ".").withStyle(ChatFormatting.GRAY));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
