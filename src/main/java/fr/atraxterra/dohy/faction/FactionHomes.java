package fr.atraxterra.dohy.faction;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les F homes : poses dans un claim, rejoints apres quelques secondes immobile
 * (10 s, puis 5, 3 et 0 avec la branche Globale de l'arbre).
 */
public final class FactionHomes {

    private record Pending(FactionData.Home home, Vec3 start, int ticks) {
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private FactionHomes() {
    }

    /** Pose le home numero {@code index} (0 pour le premier) ou se tient le joueur. */
    public static String set(ServerPlayer player, int index) {
        FactionData data = FactionApi.data(player);
        if (!data.exists()) {
            return "Tu n'as pas de faction";
        }
        int homes = FactionPerks.of(data).homes();
        if (homes <= 0) {
            return "Les F homes se debloquent au palier 5 de la branche Globale";
        }
        if (index < 0 || index >= homes) {
            return "Tu as " + homes + " home(s) : choisis entre 1 et " + homes;
        }
        if (!FactionApi.claimAt(player.level(), player.blockPosition()).ownedBy(player.getUUID())) {
            return "Un home se pose dans un de tes claims";
        }
        FactionData.Home home = new FactionData.Home(FactionApi.dimension(player.level()), player.getX(),
                player.getY(), player.getZ(), player.getYRot());
        while (data.homes.size() <= index) {
            data.homes.add(home);
        }
        data.homes.set(index, home);
        return null;
    }

    public static String go(ServerPlayer player, int index) {
        FactionData data = FactionApi.data(player);
        if (!data.exists()) {
            return "Tu n'as pas de faction";
        }
        if (index < 0 || index >= data.homes.size()) {
            return "Ce home n'est pas pose";
        }
        FactionPerks perks = FactionPerks.of(data);
        if (index >= perks.homes()) {
            return "Ce home n'est plus debloque";
        }
        FactionData.Home home = data.homes.get(index);
        if (perks.homeDelay() <= 0) {
            teleport(player, home);
            return null;
        }
        PENDING.put(player.getUUID(), new Pending(home, player.position(), perks.homeDelay() * 20));
        FactionApi.tell(player, "Teleportation dans " + perks.homeDelay() + " s, ne bouge pas.", ChatFormatting.YELLOW);
        return null;
    }

    static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Pending>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Pending> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            Pending pending = entry.getValue();
            if (player == null) {
                iterator.remove();
                continue;
            }
            if (player.position().distanceToSqr(pending.start()) > 0.25d) {
                FactionApi.tell(player, "Teleportation annulee : tu as bouge.", ChatFormatting.RED);
                iterator.remove();
                continue;
            }
            if (pending.ticks() <= 1) {
                iterator.remove();
                teleport(player, pending.home());
            } else {
                entry.setValue(new Pending(pending.home(), pending.start(), pending.ticks() - 1));
            }
        }
    }

    private static void teleport(ServerPlayer player, FactionData.Home home) {
        Identifier id = Identifier.tryParse(home.dimension());
        ServerLevel level = id == null ? null
                : player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (level == null) {
            FactionApi.tell(player, "La dimension du home n'existe plus.", ChatFormatting.RED);
            return;
        }
        player.teleport(new TeleportTransition(level, new Vec3(home.x(), home.y(), home.z()), Vec3.ZERO, home.yaw(),
                player.getXRot(), TeleportTransition.DO_NOTHING));
        FactionApi.tell(player, "Bienvenue chez toi.", ChatFormatting.GREEN);
    }
}
