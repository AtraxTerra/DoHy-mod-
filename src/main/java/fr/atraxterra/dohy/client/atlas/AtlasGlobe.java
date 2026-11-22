package fr.atraxterra.dohy.client.atlas;

import com.mojang.blaze3d.platform.NativeImage;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Les deux images du globe, une case de la grille par pixel : la carte
 * politique (mers, pays et frontieres), visible d'avance sous le terrain
 * explore, et le calque des pays tenus (ta nation en or, les nations rivales
 * a leurs couleurs, ta cible en jaune), pose par-dessus.
 *
 * <p>Vues de loin, elles passent par leurs niveaux de detail et un filtrage
 * lisse ; de pres, {@link GlobeTiles} les redessine en courbes.
 */
final class AtlasGlobe {

    static final Identifier BASE = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "atlas/globe");
    static final Identifier HOLDERS = Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "atlas/globe_holders");

    static final int SEA = 0xFF1C3550;
    static final int COAST = 0xFF2A4F72;
    /** Le filet clair des cotes, vu de pres. */
    static final int SHORE = 0xFF7FA9CF;
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
    /** Les niveaux de detail des deux images. */
    private static final int LEVELS = 7;
    /** Les identifiants de pays tiennent sur 15 bits. */
    private static final int IDS = 0x8000;

    private static MapTexture base;
    private static MapTexture holders;
    private static int baseVersion = -1;
    private static int holdersVersion = -1;
    private static int width;
    private static int height;
    private static int[] landColors = new int[IDS];
    private static int[] holderColors = new int[IDS];

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
        boolean nations = holdersVersion != AtlasData.nationsVersion();
        if (nations) {
            holderColors = holderColors(globe);
            holdersVersion = AtlasData.nationsVersion();
        }
        GlobeTiles.sync(landColors, holderColors, globe.cells(), globe.width(), globe.height(), baseVersion,
                holdersVersion);
        if (nations) {
            // Calcule a part : l'ancien calque reste affiche le temps du nouveau.
            GlobeTiles.whole(AtlasGlobe::install);
        }
        return true;
    }

    /** Vrai des que l'image entiere des pays tenus existe. */
    static boolean holdersReady() {
        return holders != null;
    }

    /** La couleur de terre d'un pays. */
    private static int land(AtlasData.Globe globe, int id) {
        EarthAtlas.Country country = globe.countries().get(id);
        return LAND[(country == null ? id : country.color() * 7 + id % 3) & 15];
    }

    private static void build(AtlasData.Globe globe) {
        release();
        width = globe.width();
        height = globe.height();
        landColors = new int[IDS];
        for (int id = 1; id < IDS; id++) {
            if (globe.countries().containsKey(id)) {
                landColors[id] = land(globe, id);
            } else {
                landColors[id] = LAND[id & 15];
            }
        }
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
                    color = landColors[id];
                    if (border(globe, column, row, id)) {
                        color = AtlasScanner.shaded(color, 0.55d);
                    }
                }
                image.setPixel(column, row, color);
            }
        }
        base = new MapTexture("dohy atlas globe", width, height, LEVELS, MapTexture.smooth());
        base.upload(image, null);
        image.close();
        Minecraft.getInstance().getTextureManager().register(BASE, base);
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

    /** La couleur de qui tient ce pays (0 si personne) : ta cible, ta nation, un joueur, une nation. */
    private static int holderColor(int id, int target) {
        if (id == target) {
            return TARGET;
        }
        EarthAtlas.Owner owner = AtlasData.owner(id);
        if (owner == null) {
            return 0;
        }
        return switch (owner.kind()) {
            case 1 -> MINE;
            case 2 -> OTHER_PLAYER;
            default -> NATIONS[Math.floorMod(owner.nation(), NATIONS.length)];
        };
    }

    private static int[] holderColors(AtlasData.Globe globe) {
        EarthAtlas.Nations nations = AtlasData.nations();
        int target = nations == null ? 0 : nations.target();
        int[] colors = new int[IDS];
        for (int id = 1; id < IDS; id++) {
            if (globe.countries().containsKey(id)) {
                colors[id] = holderColor(id, target);
            }
        }
        return colors;
    }

    /** Pose l'image entiere des pays tenus calculee a part, si elle est encore d'actualite. */
    private static void install(int version, NativeImage[] levels) {
        try {
            if (version != holdersVersion || base == null || levels.length == 0
                    || levels[0].getWidth() != width || levels[0].getHeight() != height) {
                return;
            }
            MapTexture texture = new MapTexture("dohy atlas globe holders", width, height, levels.length,
                    MapTexture.smooth());
            texture.uploadLevels(levels);
            // L'enregistrement remplace (et ferme) l'ancienne image.
            Minecraft.getInstance().getTextureManager().register(HOLDERS, texture);
            holders = texture;
        } finally {
            for (NativeImage level : levels) {
                level.close();
            }
        }
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
        GlobeTiles.clear();
        baseVersion = -1;
        holdersVersion = -1;
    }
}
