package fr.atraxterra.dohy.arrakis;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.BlockPlaceContext;
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
 * Le piege a vent : un mat a ailettes sur une citerne. L'air d'Arrakis, meme sec, porte
 * un peu d'eau ; les ailettes froides la condensent, elle ruisselle dans la citerne. Une
 * gorgee toutes les {@value #NIGHT_SECONDS} secondes la nuit, quand l'air fraichit, toutes
 * les {@value #DAY_SECONDS} le jour ; seize au plus. Il lui faut le ciel : couvert, il ne
 * recolte rien.
 *
 * <p>Toutes les cinq secondes, la rosee avance d'un cran le jour, de trois la nuit ; a
 * douze crans, une gorgee tombe dans la citerne. Le jour et la nuit se lisent a chaque
 * fois : le soir venu, il accelere tout de suite.
 *
 * <ul>
 *   <li><b>une gourde</b> : elle se remplit de ce qu'il y a ;</li>
 *   <li><b>une bouteille vide</b> : une gorgee, en bouteille d'eau ;</li>
 *   <li><b>main vide</b> : ce qu'il contient ; accroupi, on boit une gorgee a meme le robinet.</li>
 * </ul>
 *
 * <p>L'eau est dans le bloc (son etat) : casse, il la perd.
 */
public class WindtrapBlock extends Block {

    public static final int CAPACITY = 16;
    public static final IntegerProperty WATER = IntegerProperty.create("water", 0, CAPACITY);
    /** La rosee qui s'amasse vers la prochaine gorgee, en crans. */
    public static final IntegerProperty DEW = IntegerProperty.create("dew", 0, 11);
    private static final int DROP = 12;
    private static final int TICK = 5 * 20;
    private static final int NIGHT_DEW = 3;
    private static final int DAY_DEW = 1;
    static final int NIGHT_SECONDS = DROP / NIGHT_DEW * TICK / 20;
    static final int DAY_SECONDS = DROP / DAY_DEW * TICK / 20;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1.0d, 0.0d, 1.0d, 15.0d, 11.0d, 15.0d),
            Block.box(2.0d, 11.0d, 2.0d, 14.0d, 24.0d, 14.0d));

    public WindtrapBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(WATER, 0).setValue(DEW, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(WindtrapBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATER, DEW);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Il depasse d'un bloc : il lui faut la place au-dessus. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos above = context.getClickedPos().above();
        if (!context.getLevel().getBlockState(above).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState();
    }

    // -- La recolte --------------------------------------------------------------------------

    /** Vrai s'il voit le ciel : ses ailettes prennent le vent. */
    static boolean open(Level level, BlockPos pos) {
        return level.canSeeSky(pos.above(2));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!old.is(this) && level instanceof ServerLevel server) {
            server.scheduleTick(pos, this, TICK);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int water = state.getValue(WATER);
        if (water < CAPACITY && open(level, pos)) {
            int dew = state.getValue(DEW) + (level.isDarkOutside() ? NIGHT_DEW : DAY_DEW);
            if (dew >= DROP) {
                level.setBlock(pos, state.setValue(WATER, water + 1).setValue(DEW, dew - DROP), Block.UPDATE_CLIENTS);
            } else {
                // la rosee ne se voit pas : le client n'a pas a l'apprendre
                level.setBlock(pos, state.setValue(DEW, dew), Block.UPDATE_INVISIBLE);
            }
        }
        level.scheduleTick(pos, this, TICK);
    }

    /** Un filet de securite : s'il a perdu son rendez-vous (monde edite), il le reprend. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, TICK);
        }
    }

    /** Quand il y a de l'eau, une goutte perle sous les ailettes. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(WATER) > 0 && random.nextInt(4) == 0) {
            double a = random.nextDouble() * Math.PI * 2.0d;
            level.addParticle(ParticleTypes.DRIPPING_WATER, pos.getX() + 0.5d + Math.cos(a) * 0.36d,
                    pos.getY() + 0.74d, pos.getZ() + 0.5d + Math.sin(a) * 0.36d, 0.0d, 0.0d, 0.0d);
        }
    }

    // -- Tirer l'eau ---------------------------------------------------------------------------

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        boolean canteen = stack.getItem() instanceof CanteenItem;
        boolean bottle = stack.is(Items.GLASS_BOTTLE);
        if (!canteen && !bottle) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        int water = state.getValue(WATER);
        if (level.isClientSide()) {
            return water > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        ServerPlayer server = (ServerPlayer) player;
        if (water <= 0) {
            server.sendSystemMessage(Component.literal("La citerne est vide : reviens plus tard (la nuit, elle se"
                    + " remplit plus vite).").withStyle(ChatFormatting.GOLD), true);
            return InteractionResult.FAIL;
        }
        int taken;
        if (canteen) {
            int room = CanteenItem.capacity(stack) - CanteenItem.water(stack);
            taken = Math.min(room, water);
            if (taken <= 0) {
                server.sendSystemMessage(Component.literal("Ta gourde est deja pleine.")
                        .withStyle(ChatFormatting.AQUA), true);
                return InteractionResult.FAIL;
            }
            CanteenItem.setWater(stack, CanteenItem.water(stack) + taken);
            server.sendSystemMessage(Component.literal("Gourde : " + CanteenItem.water(stack) + " / "
                    + CanteenItem.capacity(stack) + " gorgees").withStyle(ChatFormatting.AQUA), true);
        } else {
            taken = 1;
            ItemStack filled = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, filled));
        }
        level.setBlock(pos, state.setValue(WATER, water - taken), Block.UPDATE_CLIENTS);
        level.playSound(null, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d, SoundEvents.BOTTLE_FILL,
                SoundSource.BLOCKS, 1.0f, 0.9f);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        int water = state.getValue(WATER);
        if (player.isShiftKeyDown() && water > 0 && Hydration.thirsty(player)) {
            level.setBlock(pos, state.setValue(WATER, water - 1), Block.UPDATE_CLIENTS);
            Hydration.drink(server, Hydration.SIP);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK,
                    SoundSource.PLAYERS, 0.8f, 1.0f);
            return InteractionResult.SUCCESS;
        }
        String rate = !open(level, pos) ? "a l'abri du ciel, il ne recolte rien"
                : level.isDarkOutside() ? "une gorgee toutes les " + NIGHT_SECONDS + " s (nuit)"
                : "une gorgee toutes les " + DAY_SECONDS + " s (jour)";
        server.sendSystemMessage(Component.literal("Piege a vent : " + water + " / " + CAPACITY + " gorgees  \u00B7  "
                + rate).withStyle(ChatFormatting.AQUA), true);
        return InteractionResult.SUCCESS;
    }
}
