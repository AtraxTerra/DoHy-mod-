package fr.atraxterra.dohy.earth.disaster;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;

/**
 * L'eau qui monte, pour la maree de tempete et la crue de mousson : une nappe
 * d'un seul tenant, sans cube d'eau qui flotte.
 *
 * <p>Au-dessus de l'eau d'origine (la mer, la riviere, le lac), le niveau ne
 * monte que pres de la rive, et le bord vers le large descend en marches puis
 * en pente : une eau courante figee, a mi-hauteur. Sur la terre, un bloc d'eau
 * ne se pose que sur un sol plein ou sur de l'eau, a cote d'une eau au meme
 * niveau, ou dans un creux d'un bloc a cote d'une eau juste au-dessus.
 *
 * <p>L'eau posee ne coule pas (elle n'est pas reveillee) : elle reste ou on la
 * met, et repart quand la catastrophe passe.
 */
final class Floods {

    /** Jusqu'a combien de blocs de la rive monte la premiere marche. */
    static final int BAND = 3;
    static final BlockState SOURCE = Blocks.WATER.defaultBlockState();
    /** Le bord en pente : une eau courante a mi-hauteur. */
    static final BlockState EDGE = Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 4);

    private Floods() {
    }

    /** Vrai si l'eau peut prendre cette place : de l'air ou une plante, sans eau. */
    static boolean free(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return Disaster.soft(state) && state.getFluidState().isEmpty();
    }

    /** Vrai si ce bloc est une eau pleine (une source, pas un bord en pente). */
    static boolean source(ServerLevel level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        return fluid.is(FluidTags.WATER) && fluid.isSource();
    }

    /** Vrai si la colonne a de l'eau a la hauteur d'origine : on est au-dessus de la mer ou de la riviere. */
    static boolean overWater(ServerLevel level, BlockPos pos, int surface) {
        return pos.getY() > surface && level.getFluidState(new BlockPos(pos.getX(), surface, pos.getZ()))
                .is(FluidTags.WATER);
    }

    /**
     * La distance de la rive : la terre la plus proche, a la hauteur de l'eau
     * d'origine, en blocs (le plus grand ecart sur x ou z), au plus max ; max + 1
     * s'il n'y en a pas.
     */
    static int bank(ServerLevel level, int x, int surface, int z, int max) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int d = 0; d <= max; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != d) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos.set(x + dx, surface, z + dz));
                    if (!state.isAir() && state.getFluidState().isEmpty()) {
                        return d;
                    }
                }
            }
        }
        return max + 1;
    }

    /**
     * L'eau a poser au-dessus de l'eau d'origine, ou null. La marche k (1 au
     * premier bloc au-dessus du niveau d'origine) va jusqu'a BAND + rise - k blocs
     * de la rive, et son bord est en pente : la nappe descend vers le large.
     */
    static BlockState over(ServerLevel level, BlockPos pos, int surface, int rise) {
        int k = pos.getY() - surface;
        if (k < 1 || k > rise || !overWater(level, pos, surface) || !free(level, pos)
                || !source(level, pos.below())) {
            return null;
        }
        int reach = BAND + rise - k;
        int d = bank(level, pos.getX(), surface, pos.getZ(), reach + 1);
        return d <= reach ? SOURCE : d == reach + 1 ? EDGE : null;
    }

    /**
     * L'eau a poser sur la terre, ou null : sur un sol plein ou sur de l'eau, a
     * cote d'une eau au meme niveau, ou dans un creux d'un bloc a cote d'une eau
     * juste au-dessus (l'eau s'y deverse).
     */
    static BlockState beside(ServerLevel level, BlockPos pos) {
        if (!free(level, pos)) {
            return null;
        }
        BlockPos below = pos.below();
        if (!source(level, below) && !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return null;
        }
        // l'eau d'a cote, juste au-dessus, ne s'y deverse que si rien ne lui barre le passage
        boolean open = free(level, pos.above());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            if (source(level, side) || open && source(level, side.above())) {
                return SOURCE;
            }
        }
        return null;
    }
}
