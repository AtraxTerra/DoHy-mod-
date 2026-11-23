package fr.atraxterra.dohy.atlas;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.List;

/**
 * Le terrain d'un chunk lu dans son NBT (un fichier de region, par {@link ChunkNbt}), colonne par colonne, comme le releve
 * du client le fait des chunks recus : la couleur de carte du bloc du dessus (jusqu'a quatre blocs
 * plus bas s'il n'en a pas), l'eau plus sombre quand elle est profonde ; sous un plafond (le Nether),
 * le premier sol sous la place libre. La pente se teinte ensuite, avec le voisin du nord.
 *
 * <p>Les sections se decodent a la main (leur palette et leurs indices tasses dans des entiers longs,
 * dans l'ordre y, z, x) : rien n'est charge dans le monde, et tout tourne hors du fil du serveur.
 */
final class ChunkColors {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** Sous un plafond, on cherche le sol a partir de cette hauteur, comme le releve du client. */
    private static final int CEILING_START = 120;

    /** Une section de 16 blocs de haut : sa palette et ses indices ({@code null} pour un seul etat). */
    private record Section(BlockState[] palette, long[] data, int bits) {

        BlockState get(int x, int y, int z) {
            if (data == null || palette.length == 1) {
                return palette[0];
            }
            int index = (y << 8) | (z << 4) | x;
            int perLong = 64 / bits;
            int word = index / perLong;
            if (word >= data.length) {
                return palette[0];
            }
            int value = (int) ((data[word] >>> ((index % perLong) * bits)) & ((1L << bits) - 1L));
            return value < palette.length ? palette[value] : palette[0];
        }
    }

    private ChunkColors() {
    }

    /**
     * Ecrit la couleur (0 : inconnue) et la hauteur de chaque colonne (indice {@code x + z * 16}).
     *
     * @return faux si le chunk n'a pas de terrain lisible
     */
    static boolean decode(List<ChunkNbt.Section> list, boolean ceiling, int[] colors, int[] heights) {
        if (list.isEmpty()) {
            return false;
        }
        Section[] sections = new Section[64];
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (ChunkNbt.Section section : list) {
            min = Math.min(min, section.y());
            max = Math.max(max, section.y());
        }
        if (max - min >= sections.length) {
            return false;
        }
        int offset = min;
        boolean any = false;
        for (ChunkNbt.Section section : list) {
            BlockState[] read = section.palette();
            boolean empty = true;
            for (BlockState state : read) {
                empty &= state.isAir();
            }
            if (empty) {
                continue;
            }
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(read.length - 1));
            sections[section.y() - offset] = new Section(read, section.data(), bits);
            any = true;
        }
        if (!any) {
            return false;
        }
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int column = x + z * 16;
                int top = ceiling ? floor(sections, offset, min, max, x, z) : surface(sections, offset, min, max, x, z);
                heights[column] = top;
                colors[column] = top == Integer.MIN_VALUE ? 0 : color(sections, offset, x, top, z);
            }
        }
        return true;
    }

    private static BlockState get(Section[] sections, int offset, int x, int y, int z) {
        int index = (y >> 4) - offset;
        if (index < 0 || index >= sections.length) {
            return AIR;
        }
        Section section = sections[index];
        return section == null ? AIR : section.get(x, y & 15, z);
    }

    /** Le bloc le plus haut qui n'est pas de l'air (la hauteur du dessus du monde, au sens du jeu). */
    private static int surface(Section[] sections, int offset, int min, int max, int x, int z) {
        for (int sy = max; sy >= min; sy--) {
            Section section = sections[sy - offset];
            if (section == null) {
                continue;
            }
            for (int ly = 15; ly >= 0; ly--) {
                if (!section.get(x, ly, z).isAir()) {
                    return sy * 16 + ly;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Sous un plafond : le premier sol sous de la place libre, en partant de y = 120. */
    private static int floor(Section[] sections, int offset, int min, int max, int x, int z) {
        int y = Math.min(max * 16 + 15, CEILING_START);
        boolean air = false;
        for (; y > min * 16; y--) {
            if (get(sections, offset, x, y, z).isAir()) {
                air = true;
            } else if (air) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static int color(Section[] sections, int offset, int x, int y, int z) {
        for (int step = 0; step < 4; step++, y--) {
            BlockState state = get(sections, offset, x, y, z);
            if (state.getFluidState().is(FluidTags.WATER)) {
                int depth = 1;
                while (depth < MapShade.WATER_DEPTH
                        && get(sections, offset, x, y - depth, z).getFluidState().is(FluidTags.WATER)) {
                    depth++;
                }
                return MapShade.water(depth);
            }
            MapColor color = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (color != MapColor.NONE && color.col != 0) {
                return 0xFF000000 | color.col;
            }
        }
        return 0;
    }
}
