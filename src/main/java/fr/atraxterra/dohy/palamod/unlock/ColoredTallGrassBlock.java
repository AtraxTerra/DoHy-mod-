package fr.atraxterra.dohy.palamod.unlock;

import com.mojang.serialization.MapCodec;
import fr.atraxterra.dohy.palamod.PalaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Les hautes herbes colorees ({@code BlockColoredTallGrass} de PalaJobs,
 * Fermier 55) : une herbe haute teinte, pour decorer — le wiki : « uniquement
 * utiles dans la decoration ». Elle tient sur la terre, l'herbe, la terre
 * labouree et les herbes colorees.
 */
public class ColoredTallGrassBlock extends VegetationBlock {

    public static final MapCodec<ColoredTallGrassBlock> CODEC = simpleCodec(ColoredTallGrassBlock::new);

    /** Les herbes colorees, sur lesquelles elle pousse aussi. */
    public static final TagKey<Block> COLORED_GRASS = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(PalaMod.NAMESPACE, "colored_grass"));

    private static final VoxelShape SHAPE = Block.box(2.0d, 0.0d, 2.0d, 14.0d, 13.0d, 14.0d);

    public ColoredTallGrassBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends VegetationBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND) || state.is(COLORED_GRASS);
    }
}
