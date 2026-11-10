package fr.atraxterra.dohy.palamod.defense;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Les enclumes en amethyste, en titane et en paladium ({@code BlockAnvil} de
 * PalaMod).
 *
 * <p>Releve au bytecode : une enclume qu'on ne mine pas (durete -1) et qu'on
 * retire a l'outil magique ; une enclume ordinaire au clic droit ; et un
 * <b>bloc de mur</b> : {@code canEntityDestroy} compte les coups des withers
 * ({@code anvil_damage}) et ne cede qu'apres 1, 2 ou 3 coups selon le metal.
 *
 * <p>Ici les coups sont l'etat {@link #HITS} du bloc — ils se voient au
 * debogage et survivent au redemarrage sans table a part. L'enclume ne s'abime
 * pas a l'usage ({@link DefenseAnvilMenu}) : elle n'a pas de version ebrechee.
 */
public class DefenseAnvilBlock extends Block {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty HITS = IntegerProperty.create("hits", 0, 2);

    private static final VoxelShape BASE = Block.box(2.0d, 0.0d, 2.0d, 14.0d, 4.0d, 14.0d);
    private static final VoxelShape X_AXIS = Shapes.or(BASE, Block.box(3.0d, 4.0d, 4.0d, 13.0d, 5.0d, 12.0d),
            Block.box(4.0d, 5.0d, 6.0d, 12.0d, 10.0d, 10.0d), Block.box(0.0d, 10.0d, 3.0d, 16.0d, 16.0d, 13.0d));
    private static final VoxelShape Z_AXIS = Shapes.or(BASE, Block.box(4.0d, 4.0d, 3.0d, 12.0d, 5.0d, 13.0d),
            Block.box(6.0d, 5.0d, 4.0d, 10.0d, 10.0d, 12.0d), Block.box(3.0d, 10.0d, 0.0d, 13.0d, 16.0d, 16.0d));
    private static final Component TITLE = Component.translatable("container.repair");

    private final int resistance;

    /** @param resistance les coups de wither encaisses avant de ceder : 0, 1 ou 2 */
    public DefenseAnvilBlock(int resistance, Properties properties) {
        super(properties);
        this.resistance = resistance;
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(HITS, 0));
    }

    public int resistance() {
        return resistance;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(properties -> new DefenseAnvilBlock(resistance, properties));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HITS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? X_AXIS : Z_AXIS;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** L'outil magique passe a l'objet : c'est lui qui retire l'enclume. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof MagicalToolItem) {
            return InteractionResult.PASS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide()) {
            player.openMenu(new SimpleMenuProvider((id, inventory, opener) ->
                    new DefenseAnvilMenu(id, inventory, ContainerLevelAccess.create(level, pos)), TITLE));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean dropFromExplosion(Explosion explosion) {
        return false;
    }
}
