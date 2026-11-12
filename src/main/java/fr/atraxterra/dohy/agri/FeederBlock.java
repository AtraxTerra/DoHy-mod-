package fr.atraxterra.dohy.agri;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La mangeoire et l'abreuvoir : deux bacs bas qu'on remplit a la main.
 *
 * <p>La mangeoire prend n'importe quelle cereale (le tag {@code dohy:cereals},
 * ble compris), une ration par grain. L'abreuvoir prend des seaux d'eau. Un
 * clic a vide dit ce qu'il reste. Le niveau se voit sur le bloc ({@link #FILL}).
 */
public class FeederBlock extends BaseEntityBlock {

    public enum Kind {
        TROUGH, WATER
    }

    public static final int MAX_FILL = 3;
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, MAX_FILL);
    private static final VoxelShape SHAPE = box(0.0d, 0.0d, 0.0d, 16.0d, 8.0d, 16.0d);

    public static final MapCodec<FeederBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Codec.STRING.xmap(Kind::valueOf, Kind::name).fieldOf("kind").forGetter(FeederBlock::kind)
    ).apply(instance, FeederBlock::new));

    private final Kind kind;

    public FeederBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FILL, 0));
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
        builder.add(FILL);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FeederBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || kind != Kind.TROUGH) {
            return null;
        }
        return (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof FeederBlockEntity feeder) {
                feeder.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        boolean fits = kind == Kind.TROUGH ? stack.is(DoHyAgri.CEREALS) : stack.is(Items.WATER_BUCKET);
        if (!fits) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide() || !(level.getBlockEntity(pos) instanceof FeederBlockEntity feeder)) {
            return InteractionResult.SUCCESS;
        }
        AgriConfig.Config config = AgriConfig.config();
        if (kind == Kind.TROUGH) {
            int taken = feeder.add(stack.getCount());
            if (taken <= 0) {
                tell(player, "La mangeoire est pleine.", ChatFormatting.RED);
                return InteractionResult.SUCCESS;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(taken);
            }
            level.playSound(null, pos, SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 0.8f, 1.0f);
        } else {
            if (feeder.capacity() - feeder.amount() < config.waterPerBucket()) {
                tell(player, "L'abreuvoir est plein.", ChatFormatting.RED);
                return InteractionResult.SUCCESS;
            }
            feeder.add(config.waterPerBucket());
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            }
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8f, 1.0f);
        }
        report(player, feeder);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FeederBlockEntity feeder) {
            report(player, feeder);
        }
        return InteractionResult.SUCCESS;
    }

    private void report(Player player, FeederBlockEntity feeder) {
        String text = kind == Kind.TROUGH
                ? "Mangeoire : " + feeder.amount() + " / " + feeder.capacity() + " cereales ("
                + AgriConfig.config().feedRation() + " par bete)  ·  prochaine tournee dans "
                + feeder.minutesUntilFeed() + " min"
                : "Abreuvoir : " + feeder.amount() + " / " + feeder.capacity() + " doses d'eau";
        tell(player, text, ChatFormatting.GRAY);
    }

    private static void tell(Player player, String text, ChatFormatting color) {
        if (player instanceof ServerPlayer server) {
            server.sendSystemMessage(Component.literal(text).withStyle(color), true);
        }
    }
}
