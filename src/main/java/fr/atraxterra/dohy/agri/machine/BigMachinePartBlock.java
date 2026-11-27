package fr.atraxterra.dohy.agri.machine;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Une partie d'une grande machine ({@link BigMachine}) : une case de la
 * construction autour de sa commande.
 *
 * <p>Elle porte son morceau du modele (un modele de bloc par case, tourne selon
 * {@link #FACING}) et sa boite de collision, et renvoie tout le reste a la
 * commande : les clics, les tuyaux et cables (capacites enregistrees dans
 * {@code DoHyAgri}), l'objet de la pioche du createur. Elle ne laisse rien
 * tomber : casser une partie casse la machine, qui tombe en objet depuis sa
 * commande. Une partie retiree autrement (explosion, commande) fait de meme.
 */
public class BigMachinePartBlock extends Block {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** La case dans la machine : x de 0 a 2, y de 0 a 4, z de 0 a 2. */
    public static final IntegerProperty X = IntegerProperty.create("x", 0, 2);
    public static final IntegerProperty Y = IntegerProperty.create("y", 0, 4);
    public static final IntegerProperty Z = IntegerProperty.create("z", 0, 2);

    public static final MapCodec<BigMachinePartBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Codec.STRING.xmap(BigMachine::valueOf, BigMachine::name).fieldOf("machine")
                    .forGetter(BigMachinePartBlock::machine)
    ).apply(instance, BigMachinePartBlock::new));

    private final BigMachine machine;

    public BigMachinePartBlock(Properties properties, BigMachine machine) {
        super(properties);
        this.machine = machine;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(X, 0).setValue(Y, 0).setValue(Z, 0));
    }

    public BigMachine machine() {
        return machine;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, X, Y, Z);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return machine.shape(state.getValue(FACING), state.getValue(X), state.getValue(Y), state.getValue(Z));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /** La position de la commande de cette partie. */
    public BlockPos controllerPos(BlockPos pos, BlockState state) {
        return machine.controllerOf(pos, state.getValue(FACING), state.getValue(X), state.getValue(Y),
                state.getValue(Z));
    }

    /** La commande, si elle est la et deployee ; sinon {@code null}. */
    private BlockState controllerState(LevelReader level, BlockPos controller) {
        BlockState state = level.getBlockState(controller);
        if (state.is(machine.controller()) && state.hasProperty(BigMachines.FORMED)
                && state.getValue(BigMachines.FORMED)) {
            return state;
        }
        return null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos controller = controllerPos(pos, state);
        BlockState target = controllerState(level, controller);
        if (target == null) {
            return InteractionResult.PASS;
        }
        return target.useItemOn(stack, level, player, hand, hit.withPosition(controller));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        BlockPos controller = controllerPos(pos, state);
        BlockState target = controllerState(level, controller);
        if (target == null) {
            return InteractionResult.PASS;
        }
        return target.useWithoutItem(level, player, hit.withPosition(controller));
    }

    /** Casser une partie, c'est casser la machine : elle tombe depuis sa commande. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockPos controller = controllerPos(pos, state);
            if (controllerState(level, controller) != null) {
                level.destroyBlock(controller, !player.getAbilities().instabuild, player);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Une partie retiree sans le joueur (explosion, commande) : la machine tombe aussi. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        BlockPos controller = controllerPos(pos, state);
        if (controllerState(level, controller) != null) {
            level.destroyBlock(controller, true);
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(machine.controller());
    }
}
