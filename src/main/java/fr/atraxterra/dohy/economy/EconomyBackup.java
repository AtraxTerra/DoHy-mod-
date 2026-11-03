package fr.atraxterra.dohy.economy;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import fr.atraxterra.dohy.DoHy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Sauvegardes de secours de l'economie.
 *
 * <p>La source de verite reste l'attachment, persiste par NeoForge avec les
 * donnees du joueur. Ce service ecrit en plus un instantane JSON lisible, en
 * trois exemplaires glissants, toutes les cinq minutes et a l'arret du serveur.
 *
 * <p>Ce n'est pas du zele : en solo, une corruption de sauvegarde sans copie de
 * secours est une partie morte. Et comme ce mod fait du solde le resultat de
 * dizaines d'heures de jeu, le cout d'une telle perte n'est pas celui d'un
 * inventaire a refaire. L'instantane est en lecture seule du point de vue du
 * jeu : il sert a restaurer a la main, pas a etre recharge automatiquement.
 */
@EventBusSubscriber(modid = DoHy.MOD_ID)
public final class EconomyBackup {

    /** 20 ticks par seconde, soit cinq minutes. */
    private static final int INTERVAL_TICKS = 20 * 60 * 5;

    private static final int COPIES = 3;
    private static final String FOLDER = "dohy";
    private static final String PREFIX = "economy-backup-";

    private static int tickCounter;

    private EconomyBackup() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        snapshot(event.getServer(), "periodique");
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        snapshot(event.getServer(), "arret");
    }

    /**
     * Ecrit un instantane et fait glisser les copies precedentes.
     *
     * <p>N'echoue jamais vers l'appelant : une sauvegarde de secours qui fait
     * tomber le serveur serait pire que son absence.
     */
    public static void snapshot(MinecraftServer server, String reason) {
        try {
            if (server.getPlayerList().getPlayers().isEmpty()) {
                return;
            }

            Path folder = server.getWorldPath(LevelResource.ROOT).resolve(FOLDER);
            Files.createDirectories(folder);

            // Glissement : 2 -> 3, 1 -> 2, puis ecriture de 1.
            for (int index = COPIES - 1; index >= 1; index--) {
                Path from = folder.resolve(PREFIX + index + ".json");
                if (Files.exists(from)) {
                    Files.move(from, folder.resolve(PREFIX + (index + 1) + ".json"),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }

            Files.writeString(folder.resolve(PREFIX + "1.json"), render(server, reason));
        } catch (Throwable error) {
            DoHy.LOGGER.error("Echec de la sauvegarde de secours de l'economie", error);
        }
    }

    private static String render(MinecraftServer server, String reason) {
        JsonObject root = new JsonObject();
        root.addProperty("reason", reason);
        root.addProperty("saved_at", System.currentTimeMillis());
        root.addProperty("game_time", server.overworld().getGameTime());

        JsonArray players = new JsonArray();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("uuid", player.getUUID().toString());
            entry.addProperty("name", player.getGameProfile().getName());
            PlayerEconomy.CODEC
                    .encodeStart(JsonOps.INSTANCE, Economy.of(player))
                    .resultOrPartial(message -> DoHy.LOGGER.warn("Encodage partiel de l'economie : {}", message))
                    .ifPresent(encoded -> entry.add("economy", encoded));
            players.add(entry);
        }
        root.add("players", players);

        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }
}
