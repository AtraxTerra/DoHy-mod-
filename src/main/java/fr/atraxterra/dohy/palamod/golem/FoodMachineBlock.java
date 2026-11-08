package fr.atraxterra.dohy.palamod.golem;

import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.palamod.machine.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * {@code BlockFoodMachine} : un cube surmonte d'un couvercle de 10 x 4 x 10
 * ({@code FoodMachineModel}), sans orientation.
 */
public class FoodMachineBlock extends MachineBlock {

    public static final MapCodec<FoodMachineBlock> CODEC = simpleCodec(FoodMachineBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 16, 16), Block.box(3, 16, 3, 13, 20, 13));

    public FoodMachineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FoodMachineBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
