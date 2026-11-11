package fr.atraxterra.dohy.palamod.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Le bloc du totem de fertilite.
 *
 * <p>Il herite de {@link MachineBlock} pour son ticker, mais pas pour la
 * restitution du contenu : le socle laisse tomber l'inventaire d'un
 * {@link MachineBlockEntity}, et le totem n'en a pas — il porte une seule pile
 * de carburant. C'est elle qu'on rend ici, parce que des lingots de paladium
 * perdus en deplacant un totem seraient une perte seche (principe 4 de
 * {@code DESIGN.md}).
 *
 * <p><b>Son apparence</b> est le mat sculpte de l'original ({@code ModelTotem}),
 * rendu par un modele de bloc : deux blocs de haut, trois tetes et deux ailes.
 * Deux proprietes le servent. {@link #FACING} : son visage regarde celui qui
 * le pose. {@link #CHARGE} : le visage luit de la couleur du carburant, comme
 * dans {@code RenderTotem} — eteint a vide, puis orange, jaune, vert a mesure
 * que la pile de lingots grossit. Le bloc n'occupe que sa case : la moitie
 * haute du mat se traverse.
 */
public class TotemBlock extends MachineBlock {

    public static final MapCodec<TotemBlock> CODEC = simpleCodec(TotemBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 0 : sans carburant ; 1 a 3 : la pile de lingots, par tiers. */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 3);

    /** Le socle et le mat, dans la case du bloc. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0d, 0.0d, 1.0d, 15.0d, 1.0d, 15.0d),
            Block.box(4.0d, 1.0d, 4.0d, 12.0d, 16.0d, 12.0d));

    public TotemBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CHARGE, 0));
    }

    /** Le niveau de charge d'une pile de {@code count} lingots. */
    public static int chargeOf(int count) {
        if (count <= 0) {
            return 0;
        }
        return count <= 21 ? 1 : count <= 42 ? 2 : 3;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CHARGE);
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
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TotemBlockEntity(pos, state);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof TotemBlockEntity totem
                && !totem.fuel().isEmpty()) {
            Block.popResource(level, pos, totem.fuel().copy());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
