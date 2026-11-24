package fr.atraxterra.dohy.palamod.unlock;

import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.palamod.machine.OrientedMachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * La table d'alchimiste ({@code BlockAlchemyCreator}, Alchimiste 15) : une
 * table a pieds chargee de fioles ({@code ModelAlchemyCreator}), qui fabrique
 * les potions et les fleches moddees ({@link AlchemyCreatorBlockEntity}).
 */
public class AlchemyCreatorBlock extends OrientedMachineBlock {

    public static final MapCodec<AlchemyCreatorBlock> CODEC = simpleCodec(AlchemyCreatorBlock::new);

    /** Le plateau et ses pieds : un peu moins qu'un bloc plein. */
    private static final VoxelShape SHAPE = Block.box(0.0d, 0.0d, 0.0d, 16.0d, 15.0d, 16.0d);

    public AlchemyCreatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AlchemyCreatorBlockEntity(pos, state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
