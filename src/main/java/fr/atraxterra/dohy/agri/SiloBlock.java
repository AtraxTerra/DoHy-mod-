package fr.atraxterra.dohy.agri;

import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.agri.machine.BigMachine;
import fr.atraxterra.dohy.agri.machine.BigMachines;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Le silo : le grenier de la ferme. Un tracteur qui passe a cote y vide sa
 * tremie ; un clic l'ouvre ({@link SiloMenu}), un clic avec une cereale en
 * main l'y depose. Le stock est dans son entite ({@link SiloBlockEntity}).
 *
 * <p>C'est une grande machine ({@link BigMachine#SILO}) : une tour de 3 x 3 et
 * 5 de haut, dont ce bloc est la commande, au centre du socle.
 */
public class SiloBlock extends BaseEntityBlock {

    public static final MapCodec<SiloBlock> CODEC = simpleCodec(SiloBlock::new);

    public SiloBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BigMachines.FACING, Direction.NORTH)
                .setValue(BigMachines.FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BigMachines.FACING, BigMachines.FORMED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return BigMachines.placement(BigMachine.SILO, defaultBlockState(), context);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        BigMachines.onPlace(BigMachine.SILO, state, level, pos, oldState);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        BigMachines.onRemove(BigMachine.SILO, state, level, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BigMachines.shape(BigMachine.SILO, state, Shapes.block());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(BigMachines.FACING, rotation.rotate(state.getValue(BigMachines.FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(BigMachines.FACING)));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SiloBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(DoHyAgri.CEREALS)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!(player instanceof ServerPlayer server) || !(level.getBlockEntity(pos) instanceof SiloBlockEntity silo)) {
            return InteractionResult.SUCCESS;
        }
        int taken = silo.deposit(stack.getItem(), stack.getCount());
        if (taken <= 0) {
            server.sendSystemMessage(Component.literal("Le silo est plein.").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(taken);
        }
        level.playSound(null, pos, SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 0.8f, 0.8f);
        server.sendSystemMessage(Component.literal("Silo : " + silo.total() + " / " + SiloBlockEntity.capacity()
                + " cereales.").withStyle(ChatFormatting.GRAY), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        BigMachines.unfold(BigMachine.SILO, state, level, pos, player);
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof SiloBlockEntity) {
            SiloMenu.INSTANCE.openAt(server, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
