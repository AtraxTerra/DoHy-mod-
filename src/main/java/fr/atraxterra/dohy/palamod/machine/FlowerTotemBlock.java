package fr.atraxterra.dohy.palamod.machine;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Le totem fleuri de l'Alchimiste ({@code BlockFlowerFarm}, la « flower farm »
 * du wiki).
 *
 * <p>Son apparence est le modele GeckoLib de PalaMod
 * ({@code totem_of_flowery.geo.json}), traduit en modele de bloc par
 * {@code tools/geo_block_model.py} : deux blocs de haut, sans animation. Son
 * visage regarde celui qui le pose ; le bloc n'occupe que sa case, la moitie
 * haute se traverse, comme le totem de fertilite.
 */
public class FlowerTotemBlock extends MachineBlock {

    public static final MapCodec<FlowerTotemBlock> CODEC = simpleCodec(FlowerTotemBlock::new);

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Le socle et le mat, dans la case du bloc. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0d, 0.0d, 1.0d, 15.0d, 1.0d, 15.0d),
            Block.box(4.0d, 1.0d, 4.0d, 12.0d, 16.0d, 12.0d));

    public FlowerTotemBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlowerTotemBlockEntity(pos, state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
