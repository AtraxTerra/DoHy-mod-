package fr.atraxterra.dohy.palamod.nature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
 * Buisson a XP ({@code BlockXPBush}, {@code BaseBlockBush}) : il pousse en
 * quatre etapes ({@code meta} 0, 4, 8, 12), une chance sur 20 par tick
 * aleatoire, sans lumiere — on le trouve sous terre. Mur, un clic (gauche ou
 * droit) cueille une baie d'XP et le ramene a l'etape precedente.
 */
public class XpBushBlock extends Block {

    public static final MapCodec<XpBushBlock> CODEC = simpleCodec(XpBushBlock::new);
    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);

    /** {@code nextInt(20) == 0}. */
    private static final int GROWTH_CHANCE = 20;

    private static final VoxelShape SMALL = box(4.0d, 0.0d, 4.0d, 12.0d, 8.0d, 12.0d);
    private static final VoxelShape MEDIUM = box(2.0d, 0.0d, 2.0d, 14.0d, 12.0d, 14.0d);
    private static final VoxelShape LARGE = box(1.0d, 0.0d, 1.0d, 15.0d, 15.0d, 15.0d);

    public XpBushBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AGE)) {
            case 0 -> SMALL;
            case 1 -> MEDIUM;
            default -> LARGE;
        };
    }

    /** {@code func_149668_a} : un buisson adulte est un bloc plein qu'on ne traverse pas. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return state.getValue(AGE) >= 2 ? Shapes.block() : getShape(state, level, pos, context);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(AGE) < 3;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(AGE) < 3 && random.nextInt(GROWTH_CHANCE) == 0) {
            level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), Block.UPDATE_ALL);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        return harvest(state, level, pos, player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        harvest(state, level, pos, player);
    }

    /** {@code harvest} : une baie, et le buisson perd une etape. */
    private static boolean harvest(BlockState state, Level level, BlockPos pos, Player player) {
        if (state.getValue(AGE) < 3) {
            return false;
        }
        if (level.isClientSide()) {
            return true;
        }
        level.setBlock(pos, state.setValue(AGE, 2), Block.UPDATE_ALL);
        ItemStack berry = new ItemStack(PalaNature.XP_BERRY.get());
        if (!player.getInventory().add(berry)) {
            player.drop(berry, false);
        }
        level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0f, 1.0f);
        return true;
    }
}
