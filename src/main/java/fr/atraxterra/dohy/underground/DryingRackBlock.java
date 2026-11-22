package fr.atraxterra.dohy.underground;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Le sechoir (lot 24c), l'equipement de production du cannabis : on y accroche quatre
 * feuilles, deux minutes plus tard elles donnent trois portions d'herbe sechee. Sans machine ni
 * energie, mais une seule fournee a la fois : le laboratoire va plus vite, il coute plus.
 */
public class DryingRackBlock extends Block {

    /** 0 : vide, 1 : ca seche, 2 : sec. */
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 0, 2);
    static final int LEAVES = 4;
    static final int HERB = 3;
    static final int TICKS = 2_400;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1.0d, 0.0d, 1.0d, 3.0d, 15.0d, 3.0d),
            Block.box(13.0d, 0.0d, 1.0d, 15.0d, 15.0d, 3.0d), Block.box(1.0d, 0.0d, 13.0d, 3.0d, 15.0d, 15.0d),
            Block.box(13.0d, 0.0d, 13.0d, 15.0d, 15.0d, 15.0d), Block.box(1.0d, 13.0d, 1.0d, 15.0d, 15.0d, 15.0d),
            Block.box(3.0d, 5.0d, 3.0d, 13.0d, 12.0d, 13.0d));

    public DryingRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(STAGE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(DryingRackBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(STAGE) != 0 || !stack.is(DoHyUnderground.CANNABIS_LEAF.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (stack.getCount() < LEAVES) {
            tell(player, "Il faut " + LEAVES + " feuilles de cannabis pour une fournee.", ChatFormatting.RED);
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(LEAVES);
            }
            server.setBlock(pos, state.setValue(STAGE, 1), Block.UPDATE_ALL);
            server.scheduleTick(pos, this, TICKS);
            server.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.8f, 1.0f);
            tell(player, "Les feuilles sechent : reviens dans deux minutes.", ChatFormatting.GRAY);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        switch (state.getValue(STAGE)) {
            case 2 -> {
                server.setBlock(pos, state.setValue(STAGE, 0), Block.UPDATE_ALL);
                ItemStack herb = new ItemStack(DoHyUnderground.DRIED_HERB.get(), HERB);
                if (!player.getInventory().add(herb) || !herb.isEmpty()) {
                    player.drop(herb, false);
                }
                server.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 0.8f, 1.2f);
            }
            case 1 -> tell(player, "Ca seche encore.", ChatFormatting.GRAY);
            default -> tell(player, "Accroche " + LEAVES + " feuilles de cannabis pour les faire secher.",
                    ChatFormatting.GRAY);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(STAGE) == 1) {
            level.setBlock(pos, state.setValue(STAGE, 2), Block.UPDATE_ALL);
        }
    }

    /** Un filet de securite : une fournee qui a perdu son rendez-vous le reprend. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(STAGE) == 1 && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, TICKS);
        }
    }

    /** Casse pendant une fournee, il rend les feuilles ; sec, l'herbe. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative()) {
            int stage = state.getValue(STAGE);
            ItemStack drop = stage == 1 ? new ItemStack(DoHyUnderground.CANNABIS_LEAF.get(), LEAVES)
                    : stage == 2 ? new ItemStack(DoHyUnderground.DRIED_HERB.get(), HERB) : ItemStack.EMPTY;
            if (!drop.isEmpty()) {
                Block.popResource(level, pos, drop);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    private static void tell(Player player, String text, ChatFormatting color) {
        if (player instanceof ServerPlayer server) {
            server.sendSystemMessage(Component.literal(text).withStyle(color), true);
        }
    }
}
