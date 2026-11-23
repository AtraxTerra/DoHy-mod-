package fr.atraxterra.dohy.client.atlas;

import fr.atraxterra.dohy.atlas.WorldTiles;
import fr.atraxterra.dohy.client.ui.Anim;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La carte revelee : l'atlas demande au serveur ({@link WorldTiles}) les dalles du monde qu'il voit
 * et qu'il n'a pas encore recues, les plus proches du centre d'abord, quelques-unes a la fois, et les
 * range dans ses dalles « revelees », dessinees sous le terrain qu'il a lui-meme explore.
 *
 * <p>Une dalle n'est demandee qu'une fois par partie (le serveur relit ses regions modifiees) ; une
 * demande restee sans reponse se refait apres un moment.
 */
final class AtlasRemote {

    /** Les demandes en vol, au plus. */
    private static final int IN_FLIGHT = 6;
    /** Une demande sans reponse se refait apres ce temps, en secondes (une dalle d'ensemble est longue). */
    private static final double TIMEOUT = 120.0d;

    private record Key(String dimension, int level, int tx, int tz) {
    }

    private static final Map<Key, Double> ASKED = new HashMap<>();
    private static final Set<Key> RECEIVED = new HashSet<>();

    private AtlasRemote() {
    }

    static void clear() {
        ASKED.clear();
        RECEIVED.clear();
    }

    /** Le niveau d'une dalle du client : 0 fine, 1 moyenne, 2 d'ensemble. */
    static int level(AtlasTiles tiles) {
        return tiles.scale() >= AtlasTiles.OVERVIEW ? WorldTiles.OVERVIEW
                : tiles.scale() >= AtlasTiles.MIDDLE ? WorldTiles.MIDDLE : WorldTiles.FINE;
    }

    /** Les dalles de ce niveau que montre la vue : demande celles que le serveur n'a pas encore envoyees. */
    static void want(String dimension, AtlasTiles tiles, AtlasView.Frame frame) {
        // Le serveur a parle de claims : il porte ce mod, donc l'atlas du serveur.
        if (!AtlasClaims.known() || Minecraft.getInstance().getConnection() == null) {
            return;
        }
        double now = Anim.now();
        ASKED.values().removeIf(asked -> now - asked > TIMEOUT);
        int slots = IN_FLIGHT - ASKED.size();
        if (slots <= 0) {
            return;
        }
        int level = level(tiles);
        int span = tiles.span();
        int fromX = (int) Math.floor(frame.worldX(frame.x) / span);
        int toX = (int) Math.floor(frame.worldX(frame.x + frame.width) / span);
        int fromZ = (int) Math.floor(frame.worldZ(frame.y) / span);
        int toZ = (int) Math.floor(frame.worldZ(frame.y + frame.height) / span);
        if ((long) (toX - fromX + 1) * (toZ - fromZ + 1) > 400) {
            return;
        }
        List<Key> wanted = new ArrayList<>();
        for (int tz = fromZ; tz <= toZ; tz++) {
            for (int tx = fromX; tx <= toX; tx++) {
                Key key = new Key(dimension, level, tx, tz);
                if (!RECEIVED.contains(key) && !ASKED.containsKey(key)) {
                    wanted.add(key);
                }
            }
        }
        double centerX = frame.centerX / span - 0.5d;
        double centerZ = frame.centerZ / span - 0.5d;
        wanted.sort(Comparator.comparingDouble(key -> Math.hypot(key.tx() - centerX, key.tz() - centerZ)));
        for (int index = 0; index < wanted.size() && index < slots; index++) {
            Key key = wanted.get(index);
            ASKED.put(key, now);
            ClientPacketDistributor.sendToServer(new WorldTiles.TileRequest(key.dimension(), key.level(), key.tx(),
                    key.tz()));
        }
    }

    /** Une dalle du serveur : elle remplace la dalle revelee du meme endroit. */
    static void accept(WorldTiles.TileData data) {
        Key key = new Key(data.dimension(), data.level(), data.tx(), data.tz());
        ASKED.remove(key);
        RECEIVED.add(key);
        Minecraft minecraft = Minecraft.getInstance();
        AtlasMap map = AtlasClient.map();
        if (map == null || minecraft.level == null
                || !minecraft.level.dimension().identifier().toString().equals(data.dimension())) {
            return;
        }
        int[] pixels = data.pixels();
        if (pixels == null) {
            return;
        }
        AtlasTiles tiles = switch (data.level()) {
            case WorldTiles.FINE -> map.revealFine;
            case WorldTiles.MIDDLE -> map.revealMiddle;
            default -> map.revealOverview;
        };
        tiles.replace(data.tx(), data.tz(), pixels);
    }
}
