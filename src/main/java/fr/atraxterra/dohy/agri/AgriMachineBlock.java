package fr.atraxterra.dohy.agri;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Les trois machines electriques de l'agriculture : le moulin, la machine a
 * pain et le biogenerateur. Un seul bloc, trois roles — elles ne different que
 * par ce que fait leur entite ({@link AgriMachineBlockEntity}) et par l'ecran
 * qu'un clic ouvre.
 */
public class AgriMachineBlock extends BaseEntityBlock {

    public enum Kind {
        /** Moud les cereales en farine. */
        MILL,
        /** Cuit les pains. */
        OVEN,
        /** Brule des cereales pour alimenter les deux autres. */
        BIOGEN
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public static final MapCodec<AgriMachineBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(AgriMachineBlock::kind)
    ).apply(instance, AgriMachineBlock::new));

    private final Kind kind;

    public AgriMachineBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
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
        return new AgriMachineBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof AgriMachineBlockEntity machine) {
                machine.serverTick();
            }
        };
    }

    /** Le biogenerateur se charge une cereale a la main ; les deux autres n'ont rien a recevoir ainsi. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (kind != Kind.BIOGEN || !stack.is(DoHyAgri.CEREALS)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof AgriMachineBlockEntity machine)) {
            return InteractionResult.SUCCESS;
        }
        int taken = machine.addFuel(stack.getCount());
        if (taken <= 0) {
            tell(player, "Le biogenerateur est plein.", ChatFormatting.RED);
            return InteractionResult.SUCCESS;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(taken);
        }
        level.playSound(null, pos, SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 0.8f, 0.8f);
        tell(player, machine.fuelReport(), ChatFormatting.GRAY);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(player instanceof ServerPlayer server)
                || !(level.getBlockEntity(pos) instanceof AgriMachineBlockEntity machine)) {
            return InteractionResult.SUCCESS;
        }
        switch (kind) {
            case MILL -> MillMenu.INSTANCE.openAt(server, pos);
            case OVEN -> BreadMachineMenu.INSTANCE.openAt(server, pos);
            case BIOGEN -> tell(player, machine.fuelReport(), ChatFormatting.GRAY);
        }
        return InteractionResult.SUCCESS;
    }

    private static void tell(Player player, String text, ChatFormatting color) {
        if (player instanceof ServerPlayer server) {
            server.sendSystemMessage(Component.literal(text).withStyle(color), true);
        }
    }
}
