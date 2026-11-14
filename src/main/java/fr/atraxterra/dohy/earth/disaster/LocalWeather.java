package fr.atraxterra.dohy.earth.disaster;

import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * La meteo locale : dans le jeu, la pluie et l'orage sont les memes dans tout
 * le monde. Un ouragan ou une averse de mousson n'arrosent pourtant que leur
 * region : chaque joueur qui s'y trouve recoit sa propre pluie et son propre
 * orage, et lui seul. Le jeu envoie a tous la meteo du monde quand elle
 * change ; la meteo locale passe apres, dans le meme tick, et c'est elle que
 * le joueur voit.
 *
 * <p>Les niveaux montent et descendent en douceur (deux pour cent par tick),
 * comme ceux du jeu. Hors d'une tempete, le joueur retrouve la meteo du monde
 * et plus rien ne lui est envoye en propre.
 */
final class LocalWeather {

    private static final float STEP = 0.02f;

    /** Ce que chaque joueur a recu en propre : pluie, orage. */
    private static final Map<UUID, float[]> SENT = new HashMap<>();
    private static float lastRain = -1.0f;
    private static float lastThunder = -1.0f;
    private static boolean lastRaining;

    private LocalWeather() {
    }

    static void clear() {
        SENT.clear();
        lastRain = -1.0f;
        lastThunder = -1.0f;
    }

    static void forget(UUID player) {
        SENT.remove(player);
    }

    /** A la fin de chaque tick du serveur. */
    static void tick(MinecraftServer server, ServerLevel level) {
        float globalRain = level.getRainLevel(1.0f);
        float globalThunder = level.getThunderLevel(1.0f);
        boolean raining = level.isRaining();
        // le jeu a-t-il envoye la meteo du monde a tout le monde ce tick-ci ?
        boolean broadcast = globalRain != lastRain || globalThunder != lastThunder || raining != lastRaining;
        lastRain = globalRain;
        lastThunder = globalThunder;
        lastRaining = raining;
        for (ServerPlayer player : level.players()) {
            float[] storm = Disasters.weather(player);
            float[] sent = SENT.get(player.getUUID());
            if (storm == null && sent == null) {
                continue;
            }
            // dans une tempete, sa meteo a elle, meme plus claire que celle du monde (l'oeil d'un ouragan)
            float wantRain = storm == null ? globalRain : storm[0];
            float wantThunder = storm == null ? globalThunder : storm[1];
            float[] current = sent == null ? new float[] {globalRain, globalThunder} : sent;
            float rain = approach(current[0], wantRain);
            float thunder = approach(current[1], wantThunder);
            boolean changed = rain != current[0] || thunder != current[1];
            if (changed || broadcast || sent == null) {
                if (sent == null && rain > 0.0f) {
                    // le client ne dessine la pluie que s'il la croit en cours
                    player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING,
                            0.0f));
                }
                player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE,
                        rain));
                player.connection.send(new ClientboundGameEventPacket(
                        ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunder));
            }
            if (storm == null && rain == globalRain && thunder == globalThunder) {
                // revenu a la meteo du monde : plus rien en propre
                SENT.remove(player.getUUID());
                if (!raining) {
                    player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING,
                            0.0f));
                    player.connection.send(new ClientboundGameEventPacket(
                            ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, globalRain));
                    player.connection.send(new ClientboundGameEventPacket(
                            ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, globalThunder));
                }
            } else {
                SENT.put(player.getUUID(), new float[] {rain, thunder});
            }
        }
        // un joueur parti dans une autre dimension : le jeu lui a renvoye la meteo de celle-ci
        SENT.keySet().removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            return player == null || player.level() != level;
        });
    }

    private static float approach(float from, float to) {
        if (Math.abs(to - from) <= STEP) {
            return to;
        }
        return from + Math.signum(to - from) * STEP;
    }
}
