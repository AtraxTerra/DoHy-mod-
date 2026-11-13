package fr.atraxterra.dohy.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Les deux images du globe, une case de la grille par pixel : la carte
 * politique (mers, pays et frontieres), visible d'avance sous le terrain
 * explore, et le calque des pays tenus (ta faction en or, les nations rivales
 * a leurs couleurs, ta cible en jaune), pose par-dessus.
 */
final class AtlasGlobe {

    static final Identifier BASE = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "atlas/globe");
    static final Identifier HOLDERS = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "atlas/globe_holders");

    private static final int SEA = 0xFF1C3550;
    private static final int COAST = 0xFF2A4F72;
    /** Les teintes d'une carte politique, sourdes : le terrain explore passe devant. */
    private static final int[] LAND = {0xFF8FA36E, 0xFF9DB57A, 0xFFC9B87A, 0xFFC4987A, 0xFFAE9CC2, 0xFF86AFC0,
            0xFFC99A9A, 0xFFA3B98A, 0xFFB5A889, 0xFF9AA8B5, 0xFFB79FB0, 0xFF9FB59F, 0xFFC0AE90, 0xFF8FA0B0,
            0xFFB8B07E, 0xFFA49480};
    /** Les douze nations rivales. */
    static final int[] NATIONS = {0xFFE05252, 0xFF4F8FE0, 0xFF52C46B, 0xFFB65BD9, 0xFFE0A030, 0xFF30C5C5,
            0xFFE070B0, 0xFF8C6E4A, 0xFF7A7AE6, 0xFF9CC23A, 0xFFE07A3C, 0xFF5A5A5A};
    static final int MINE = 0xFFFFC400;
    static final int OTHER_PLAYER = 0xFFFF55FF;
    static final int TARGET = 0xFFFFFF55;

    private static DynamicTexture base;
    private static DynamicTexture holders;
    private static int baseVersion = -1;
    private static int holdersVersion = -1;
    private static int width;
    private static int height;

    private AtlasGlobe() {
    }

    static int width() {
        return width;
    }

    static int height() {
        return height;
    }

    /** Refait les images si le serveur a envoye du neuf ; vrai si le globe est pret. */
    static boolean ready() {
        AtlasData.Globe globe = AtlasData.globe();
        if (globe == null) {
            release();
            return false;
        }
        if (baseVersion != AtlasData.globeVersion() || base == null) {
            build(globe);
            baseVersion = AtlasData.globeVersion();
            holdersVersion = -1;
        }
        if (holdersVersion != AtlasData.nationsVersion()) {
            paintHolders(globe);
            holdersVersion = AtlasData.nationsVersion();
        }
        return true;
    }

    private static void build(AtlasData.Globe globe) {
        release();
        width = globe.width();
        height = globe.height();
        NativeImage image = new NativeImage(width, height, false);
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int cell = globe.cells()[row * width + column] & 0xFFFF;
                int id = cell & 0x7FFF;
                int color;
                if (id == 0) {
                    color = SEA;
                } else if ((cell & 0x8000) != 0) {
                    color = COAST;
                } else {
                    EarthAtlas.Country country = globe.countries().get(id);
                    color = LAND[(country == null ? id : country.color() * 7 + id % 3) & 15];
                    if (border(globe, column, row, id)) {
                        color = AtlasScanner.shaded(color, 0.55d);
                    }
                }
                image.setPixel(column, row, color);
            }
        }
        base = new DynamicTexture(() -> "dohy atlas globe", image);
        Minecraft.getInstance().getTextureManager().register(BASE, base);
        holders = new DynamicTexture(() -> "dohy atlas globe holders", new NativeImage(width, height, true));
        Minecraft.getInstance().getTextureManager().register(HOLDERS, holders);
    }

    /** Une case de terre dont le voisin de droite ou du bas est un autre pays. */
    private static boolean border(AtlasData.Globe globe, int column, int row, int id) {
        for (int[] step : new int[][]{{1, 0}, {0, 1}}) {
            int c = column + step[0];
            int r = row + step[1];
            if (c >= globe.width() || r >= globe.height()) {
                continue;
            }
            int other = globe.cells()[r * globe.width() + c] & 0xFFFF;
            if ((other & 0x8000) == 0 && (other & 0x7FFF) != 0 && (other & 0x7FFF) != id) {
                return true;
            }
        }
        return false;
    }

    private static void paintHolders(AtlasData.Globe globe) {
        if (holders == null) {
            return;
        }
        NativeImage image = holders.getPixels();
        EarthAtlas.Nations nations = AtlasData.nations();
        int target = nations == null ? 0 : nations.target();
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int cell = globe.cells()[row * width + column] & 0xFFFF;
                int id = cell & 0x7FFF;
                int color = 0;
                if (id != 0 && (cell & 0x8000) == 0) {
                    if (id == target) {
                        color = TARGET;
                    } else {
                        EarthAtlas.Owner owner = AtlasData.owner(id);
                        if (owner != null) {
                            color = switch (owner.kind()) {
                                case 1 -> MINE;
                                case 2 -> OTHER_PLAYER;
                                default -> NATIONS[Math.floorMod(owner.nation(), NATIONS.length)];
                            };
                        }
                    }
                }
                image.setPixel(column, row, color == 0 ? 0 : (color & 0x00FFFFFF) | 0x60000000);
            }
        }
        holders.upload();
    }

    static void release() {
        if (base != null) {
            Minecraft.getInstance().getTextureManager().release(BASE);
            base = null;
        }
        if (holders != null) {
            Minecraft.getInstance().getTextureManager().release(HOLDERS);
            holders = null;
        }
        baseVersion = -1;
        holdersVersion = -1;
    }
}
