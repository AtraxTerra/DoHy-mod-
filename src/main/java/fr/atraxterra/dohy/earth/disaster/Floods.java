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
 * monte que pres de la rive — la terre qui depasse de l'eau, pas un banc de
 * sable a fleur d'eau —, de proche en proche depuis le bord, et la nappe
 * redescend vers le large en pente douce, un tiers de bloc a la fois : des
 * couches pleines dessous, une eau courante figee au-dessus. Sur la terre, un bloc d'eau ne se pose que sur un sol plein ou
 * sur de l'eau, a cote d'une eau au meme niveau, ou dans un creux d'un bloc a
 * cote d'une eau juste au-dessus.
 *
 * <p>L'eau posee ne coule pas (elle n'est pas reveillee) : elle reste ou on la
 * met, et repart quand la catastrophe passe.
 */
final class Floods {

    /** Jusqu'a combien de blocs de la rive l'eau monte de toute sa hauteur. */
    static final int BAND = 4;
    static final BlockState SOURCE = Blocks.WATER.defaultBlockState();
    /** Le bord en pente : une eau courante aux deux tiers, puis au tiers d'un bloc. */
    private static final BlockState HIGH = flowing(6);
    private static final BlockState LOW = flowing(3);

    private Floods() {
    }

    /** Une eau courante figee de cette hauteur (1 a 7 huitiemes de source). */
    private static BlockState flowing(int amount) {
        return Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 8 - amount);
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

    /** Vrai si ce bloc porte : de la terre ou de la roche, pas une plante, un nenuphar ou de la neige. */
    static boolean land(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.isFaceSturdy(level, pos, Direction.UP);
    }

    /**
     * La distance de la rive : la terre la plus proche qui depasse de l'eau
     * d'origine (un bloc au-dessus de son niveau), en blocs (le plus grand ecart
     * sur x ou z), au plus max ; max + 1 s'il n'y en a pas. Un banc de sable a
     * fleur d'eau n'est pas une rive : la mer ne monte pas en plateau autour.
     */
    static int bank(ServerLevel level, int x, int surface, int z, int max) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int d = 0; d <= max; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dz = -d; dz <= d; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == d && land(level, pos.set(x + dx, surface + 1, z + dz))) {
                        return d;
                    }
                }
            }
        }
        return max + 1;
    }

    /**
     * Vrai si l'eau qui monte ici tient a quelque chose au meme niveau : une eau
     * deja montee, la terre, ou le bord de l'eau (de la terre juste en dessous,
     * a cote). Ainsi la nappe part du rivage et gagne le large, jamais l'inverse.
     */
    static boolean anchored(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            if (level.getFluidState(side).is(FluidTags.WATER) || land(level, side) || land(level, side.below())) {
                return true;
            }
        }
        return false;
    }

    /**
     * L'eau a poser au-dessus de l'eau d'origine, ou null. Jusqu'a BAND blocs de
     * la rive, l'eau monte de rise blocs ; au-dela, la surface redescend d'un
     * tiers de bloc a chaque bloc vers le large (3 blocs de pente par bloc de
     * montee) : des couches pleines dessous, une eau courante figee au-dessus.
     */
    static BlockState over(ServerLevel level, BlockPos pos, int surface, int rise) {
        int k = pos.getY() - surface;
        if (k < 1 || k > rise || !overWater(level, pos, surface) || !free(level, pos)
                || !level.getFluidState(pos.below()).is(FluidTags.WATER) || !anchored(level, pos)) {
            return null;
        }
        int d = bank(level, pos.getX(), surface, pos.getZ(), BAND + 3 * rise);
        if (d <= BAND) {
            return SOURCE;
        }
        // la hauteur de la surface au-dessus du niveau d'origine, en tiers de bloc
        int thirds = 3 * rise - (d - BAND);
        if (thirds <= 0) {
            return null;
        }
        int full = thirds / 3;
        if (k <= full) {
            return SOURCE;
        }
        if (k == full + 1 && thirds % 3 != 0) {
            return thirds % 3 == 2 ? HIGH : LOW;
        }
        return null;
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
