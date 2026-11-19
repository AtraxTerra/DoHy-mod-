package fr.atraxterra.dohy.palamod.throne;

import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.job.Job;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Un trone de metier ({@code BlockThroneBase}).
 *
 * <p>Le modele est dessine par le rendu de la block entity (il fait pres de
 * trois blocs de haut) ; le bloc ne porte que son orientation. Le verrou du
 * niveau 95 n'est pas teste ici : {@code JobGateEvents} refuse deja la pose et
 * le clic droit, comme pour tous les objets de la table des verrous.
 */
public class ThroneBlock extends BaseEntityBlock {

    /** Ou regarde le trone : vers celui qui l'a pose. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** La hauteur de l'assise, en blocs (le coussin culmine a 9 pixels). */
    static final double SEAT_HEIGHT = 9.0d / 16.0d;

    /**
     * {@code setBlockBounds(0, 0.5, 0, 1, 2, 1)} : le corps du trone, au-dessus de
     * l'assise. En 26.2 une forme qui monte a 2 blocs ne se vise pas au-dela du
     * premier ; la boite part donc du sol et s'arrete a une barriere et demie,
     * comme une cloture : on ne marche pas sur le dossier.
     */
    private static final VoxelShape COLLISION = box(0.0d, 0.0d, 0.0d, 16.0d, 24.0d, 16.0d);
    private static final VoxelShape OUTLINE = box(0.0d, 0.0d, 0.0d, 16.0d, 16.0d, 16.0d);

    private final Job job;

    public ThroneBlock(Job job, Properties properties) {
        super(properties);
        this.job = job;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Job job() {
        return job;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(properties -> new ThroneBlock(job, properties));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return COLLISION;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThroneBlockEntity(pos, state);
    }

    /**
     * Le premier qui le pose y grave son nom ({@code onBlockPlacedBy}) : un trone
     * qui porte deja un nom le garde.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof ThroneBlockEntity throne && throne.owner().isEmpty()) {
            throne.setOwner(player.getName().getString());
        }
    }

    /** {@code sitOnBlock(world, x, y, z, player, 0.6)} : on s'assoit, si la place est libre. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer server) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        if (server.getVehicle() instanceof ThroneSeat) {
            return InteractionResult.PASS;
        }
        boolean taken = !serverLevel.getEntitiesOfClass(ThroneSeat.class, new AABB(pos)).isEmpty();
        if (taken) {
            server.sendSystemMessage(Component.translatable("message.palamod.throne.taken")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.CONSUME;
        }
        ThroneSeat seat = new ThroneSeat(PalaThrones.SEAT.get(), serverLevel);
        seat.seatAt(pos, state.getValue(FACING));
        serverLevel.addFreshEntity(seat);
        if (!server.startRiding(seat)) {
            seat.discard();
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }
}
