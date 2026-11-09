package fr.atraxterra.dohy.palamod.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les parties en cours, et l'entree et la sortie des joueurs.
 *
 * <p>Les parties ne vivent qu'en memoire : un arret du serveur les termine
 * (chacun est ramene, rien n'est livre), et celui qui se reconnecte dans la
 * dimension sans partie est renvoye a son point d'entree. Les regions, elles,
 * ne servent qu'une fois — leur compteur est garde sur l'overworld — pour
 * qu'une salle laissee par un arret brutal ne gene jamais une partie suivante.
 */
public final class DungeonManager {

    /** Le groupe : le chef et jusqu'a trois joueurs a 6 blocs. */
    public static final int MAX_PARTY = 4;
    private static final double PARTY_RADIUS = 6.0d;
    /** L'ecart entre deux regions, le long de +x. */
    private static final int REGION_SPACING = 1024;
    private static final int ROOM_Y = 64;
    public static final int MAX_PALIER = 100;

    private static final Map<Integer, DungeonRun> RUNS = new HashMap<>();
    private static final Map<UUID, DungeonRun> BY_PLAYER = new HashMap<>();
    /** Monstre de partie -> sa partie. */
    private static final Map<UUID, DungeonRun> BY_MOB = new HashMap<>();
    /** Monstre de partie -> la part des degats qu'il encaisse (boss au plafond de vie). */
    private static final Map<UUID, Float> TAKEN = new HashMap<>();

    private DungeonManager() {
    }

    static void message(ServerPlayer player, ChatFormatting color, String key, Object... args) {
        player.sendSystemMessage(Component.translatable("message.palamod.dungeon." + key, args).withStyle(color));
    }

    public static boolean levelIs(Level level) {
        return level.dimension() == PalaDungeon.DIMENSION;
    }

    public static boolean inDimension(Entity entity) {
        return levelIs(entity.level());
    }

    // -- Progression ----------------------------------------------------------------

    public static int cleared(ServerPlayer player, Dungeon dungeon) {
        return player.getData(PalaDungeon.PROGRESS.get()).getOrDefault(dungeon.key(), 0);
    }

    static void recordCleared(ServerPlayer player, Dungeon dungeon, int palier) {
        Map<String, Integer> progress = new HashMap<>(player.getData(PalaDungeon.PROGRESS.get()));
        if (progress.getOrDefault(dungeon.key(), 0) < palier) {
            progress.put(dungeon.key(), palier);
            player.setData(PalaDungeon.PROGRESS.get(), progress);
        }
    }

    // -- Parties --------------------------------------------------------------------

    public static DungeonRun runOf(ServerPlayer player) {
        return BY_PLAYER.get(player.getUUID());
    }

    static DungeonRun runOfMob(UUID mob) {
        return BY_MOB.get(mob);
    }

    static float takenFactor(UUID mob) {
        return TAKEN.getOrDefault(mob, 1.0f);
    }

    static void trackMob(UUID mob, DungeonRun run, float taken) {
        BY_MOB.put(mob, run);
        if (taken < 1.0f) {
            TAKEN.put(mob, taken);
        }
    }

    static void untrackMob(UUID mob) {
        BY_MOB.remove(mob);
        TAKEN.remove(mob);
    }

    /** La partie dont la salle contient cette position, pour les monstres nes en route. */
    static DungeonRun runAt(Level level, double x, double z) {
        if (!levelIs(level)) {
            return null;
        }
        for (DungeonRun run : RUNS.values()) {
            if (run.room != null && run.insideRoom(new net.minecraft.world.phys.Vec3(x, 0.0d, z))) {
                return run;
            }
        }
        return null;
    }

    /** Les joueurs que le chef emmene : lui, puis les plus proches. */
    static List<ServerPlayer> party(ServerPlayer leader) {
        List<ServerPlayer> party = new ArrayList<>();
        party.add(leader);
        List<ServerPlayer> nearby = new ArrayList<>();
        for (ServerPlayer other : ((ServerLevel) leader.level()).players()) {
            if (other != leader && other.isAlive() && !other.isSpectator() && other.distanceTo(leader) <= PARTY_RADIUS
                    && !BY_PLAYER.containsKey(other.getUUID()) && !inDimension(other)) {
                nearby.add(other);
            }
        }
        nearby.sort((a, b) -> Double.compare(a.distanceToSqr(leader), b.distanceToSqr(leader)));
        for (ServerPlayer other : nearby) {
            if (party.size() >= MAX_PARTY) {
                break;
            }
            party.add(other);
        }
        return party;
    }

    /**
     * Lance un palier. La cle du chef est consommee a l'entree (le wiki :
     * « seul le leader du groupe doit posseder une cle »).
     */
    static boolean start(ServerPlayer leader, Dungeon dungeon, int palier, InteractionHand hand) {
        if (BY_PLAYER.containsKey(leader.getUUID()) || inDimension(leader)) {
            message(leader, ChatFormatting.RED, "already");
            return false;
        }
        if (palier < 1 || palier > MAX_PALIER) {
            return false;
        }
        int cleared = cleared(leader, dungeon);
        if (palier > cleared + 1) {
            message(leader, ChatFormatting.RED, "locked", cleared + 1);
            return false;
        }
        ItemStack key = leader.getItemInHand(hand);
        if (!key.is(PalaDungeon.key(dungeon))) {
            message(leader, ChatFormatting.RED, "no_key", dungeon.display());
            return false;
        }
        MinecraftServer server = leader.level().getServer();
        ServerLevel level = server.getLevel(PalaDungeon.DIMENSION);
        if (level == null) {
            message(leader, ChatFormatting.RED, "unavailable");
            return false;
        }
        if (!leader.isCreative()) {
            key.shrink(1);
        }
        List<ServerPlayer> party = party(leader);
        ServerLevel overworld = server.overworld();
        int region = overworld.getData(PalaDungeon.NEXT_REGION.get());
        overworld.setData(PalaDungeon.NEXT_REGION.get(), region + 1);
        BlockPos origin = new BlockPos((region + 1) * REGION_SPACING, ROOM_Y, 0);
        DungeonRun run = new DungeonRun(region, level, dungeon, palier, leader, party, origin);
        RUNS.put(run.id, run);
        List<Component> names = new ArrayList<>();
        for (ServerPlayer member : party) {
            BY_PLAYER.put(member.getUUID(), run);
            member.setData(PalaDungeon.RETURN.get(), ReturnPoint.of(member));
            member.closeContainer();
            names.add(member.getDisplayName());
        }
        run.begin(party);
        Component list = Component.empty();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                list = list.copy().append(Component.literal(", "));
            }
            list = list.copy().append(names.get(i));
        }
        run.broadcast(ChatFormatting.GOLD, "start", dungeon.display(), palier, run.band().label());
        run.broadcast(ChatFormatting.GRAY, "party", list);
        return true;
    }

    /** Ramene un joueur a son point d'entree, et l'oublie. */
    static void sendBack(ServerPlayer player) {
        BY_PLAYER.remove(player.getUUID());
        ReturnPoint point = player.getData(PalaDungeon.RETURN.get());
        player.setData(PalaDungeon.RETURN.get(), ReturnPoint.NONE);
        player.setHealth(Math.max(player.getHealth(), 1.0f));
        player.clearFire();
        player.fallDistance = 0.0f;
        if (point.isNone()) {
            ReturnPoint.NONE.send(player);
        } else {
            point.send(player);
        }
    }

    /** Fin d'une partie, reussie ou non. */
    static void finish(DungeonRun run) {
        run.cleanup();
        RUNS.remove(run.id);
        BY_PLAYER.values().removeIf(candidate -> candidate == run);
        BY_MOB.entrySet().removeIf(entry -> {
            if (entry.getValue() == run) {
                TAKEN.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    /** Un joueur quitte sa partie. */
    static void leave(ServerPlayer player, DungeonRun.Leave reason) {
        DungeonRun run = BY_PLAYER.get(player.getUUID());
        if (run == null) {
            return;
        }
        run.leave(player, reason);
        BY_PLAYER.remove(player.getUUID());
    }

    static void tick(MinecraftServer server) {
        if (RUNS.isEmpty()) {
            return;
        }
        for (DungeonRun run : new ArrayList<>(RUNS.values())) {
            // Un joueur sorti de la dimension par un autre chemin (commande,
            // portail) a fui la partie.
            for (UUID uuid : new ArrayList<>(run.members)) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null && !inDimension(player)) {
                    leave(player, DungeonRun.Leave.ESCAPED);
                }
            }
            if (RUNS.containsKey(run.id)) {
                run.tick();
            }
        }
    }

    /** Un joueur connecte dans la dimension sans partie y est renvoye a son entree. */
    static void settle(ServerPlayer player) {
        if (inDimension(player) && !BY_PLAYER.containsKey(player.getUUID())) {
            message(player, ChatFormatting.YELLOW, "returned");
            sendBack(player);
        }
    }

    /** Arret du serveur : tout le monde dehors, rien n'est livre. */
    static void shutdown() {
        for (DungeonRun run : new ArrayList<>(RUNS.values())) {
            for (UUID uuid : new ArrayList<>(run.members)) {
                ServerPlayer player = run.level().getServer().getPlayerList().getPlayer(uuid);
                if (player != null) {
                    try {
                        sendBack(player);
                    } catch (RuntimeException ignored) {
                        // Le joueur sera renvoye a sa prochaine connexion.
                    }
                }
            }
            run.members.clear();
            run.cleanup();
        }
        RUNS.clear();
        BY_PLAYER.clear();
        BY_MOB.clear();
        TAKEN.clear();
    }
}
