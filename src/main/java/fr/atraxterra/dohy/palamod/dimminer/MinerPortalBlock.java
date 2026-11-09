package fr.atraxterra.dohy.palamod.dimminer;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Le portail du Mineur ({@code BlockMinerPortal}).
 *
 * <p>Incassable, traversable, il eclaire a 11 et grésille de particules. Dans
 * l'overworld, y entrer ouvre l'ecran d'entree de la dimension ; dans la
 * dimension du Mineur, il ramene au portail par lequel on est venu. Il
 * disparait si son cadre ne tient plus.
 */
public class MinerPortalBlock extends BaseEntityBlock {

    public static final MapCodec<MinerPortalBlock> CODEC = simpleCodec(MinerPortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    private static final VoxelShape X_SHAPE = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape Z_SHAPE = Block.box(6, 0, 0, 10, 16, 16);

    public MinerPortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MinerPortalBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
    }

    /**
     * {@code NoCheckSize} : un cadre incomplet referme le portail, et une nappe
     * trouee aussi ({@code portalBlockCount < width * height}).
     */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighbor,
                                     RandomSource random) {
        Direction.Axis axis = state.getValue(AXIS);
        boolean across = direction.getAxis() != axis && direction.getAxis().isHorizontal();
        if (!across && !neighbor.is(this) && level instanceof Level real) {
            MinerPortalShape shape = MinerPortalShape.find(real, pos, axis, false);
            if (shape == null || !shape.isComplete()) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighbor, random);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effects, boolean intersects) {
        if (!level.isClientSide() && entity instanceof ServerPlayer player) {
            DimMinerManager.touchPortal(player, pos);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(100) == 0) {
            level.playLocalSound(pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d,
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.5f,
                    0.8f + random.nextFloat() * 0.4f, false);
        }
        for (int i = 0; i < 4; i++) {
            double x = pos.getX() + 0.5d + (random.nextDouble() - 0.5d) * 5.0d;
            double y = pos.getY() + 0.5d + (random.nextDouble() - 0.5d) * 5.0d;
            double z = pos.getZ() + 0.5d + (random.nextDouble() - 0.5d) * 5.0d;
            level.addParticle(random.nextBoolean() ? ParticleTypes.PORTAL : ParticleTypes.MYCELIUM, x, y, z,
                    0.0d, 0.0d, 0.0d);
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return ItemStack.EMPTY;
    }
}
