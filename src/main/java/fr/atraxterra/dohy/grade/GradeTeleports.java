package fr.atraxterra.dohy.grade;

import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.mine.MineService;
import fr.atraxterra.dohy.palamod.trixium.CombatTag;
import fr.atraxterra.dohy.spawn.DoHySpawn;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les teleportations des grades : {@code /home}, {@code /rtp}, {@code /minage}, {@code /farmland} et les demandes
 * {@code /tpa}.
 *
 * <p>Comme sur Paladium, on attend trois secondes sans bouger ni prendre de coup avant de partir ;
 * a partir de Legende, on part tout de suite. Jamais en combat, et seulement entre les lieux ou
 * l'on circule librement : l'Overworld, le Nether, l'End, la Terre, le spawn et la Mine. Ni d'un
 * donjon, d'une arene, de la dimension du Mineur ou d'Arrakis : on en sort par leurs propres
 * portes, qui ont leurs regles.
 */
@EventBusSubscriber
public final class GradeTeleports {

    static final int WARMUP_TICKS = 60;
    static final long REQUEST_MILLIS = 60_000L;

    private record Pending(Vec3 start, int due, String what, Runnable action) {
    }

    /** Une demande de teleportation : de qui, et dans quel sens ({@code here} : vers le demandeur). */
    private record Request(UUID from, boolean here, long expires) {
    }

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    /** Par joueur sollicite : les demandes recues, de la plus ancienne a la plus recente. */
    private static final Map<UUID, LinkedHashMap<UUID, Request>> REQUESTS = new ConcurrentHashMap<>();

    private GradeTeleports() {
    }

    // -- Ou l'on circule -----------------------------------------------------------------------

    /** Vrai pour les dimensions ou l'on circule librement. */
    static boolean travelLevel(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        return homeLevel(level) || dimension == DoHySpawn.DIMENSION || dimension == MineService.DIMENSION
                || dimension == fr.atraxterra.dohy.farmland.Farmland.DIMENSION;
    }

    /** Vrai la ou un home peut se poser : pas au spawn, ni a la Mine, remise a neuf toutes les cinq heures. */
    static boolean homeLevel(ServerLevel level) {
        ResourceKey<Level> dimension = level.dimension();
        if (dimension == Level.OVERWORLD || dimension == Level.NETHER || dimension == Level.END) {
            return true;
        }
        EarthData earth = EarthData.get();
        return earth != null && earth.dimension() == dimension;
    }

    /** Le joueur peut-il partir d'ici ? Sinon, il est prevenu. */
    static boolean canLeave(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !travelLevel(level)) {
            tell(player, "On ne se teleporte pas d'ici : sors par la porte du lieu.", ChatFormatting.RED);
            return false;
        }
        if (CombatTag.inCombat(player)) {
            tell(player, "Pas de teleportation en plein combat.", ChatFormatting.RED);
            return false;
        }
        return true;
    }

    // -- L'attente -----------------------------------------------------------------------------

    /**
     * Part apres trois secondes immobile, ou tout de suite a partir de Legende (et en creatif).
     *
     * @param what « au home base », « vers Alex » : la suite de « Teleportation »
     */
    static void schedule(ServerPlayer player, String what, Runnable action) {
        if (!canLeave(player)) {
            return;
        }
        if (Grades.instantTeleport(player) || player.isCreative() || player.isSpectator()) {
            PENDING.remove(player.getUUID());
            action.run();
            return;
        }
        MinecraftServer server = player.level().getServer();
        PENDING.put(player.getUUID(), new Pending(player.position(), server.getTickCount() + WARMUP_TICKS, what,
                action));
        bar(player, "Teleportation " + what + " dans 3 s : ne bouge pas.", ChatFormatting.GOLD);
        tell(player, "Teleportation " + what + " dans 3 secondes, sans bouger. Legende part tout de suite.",
                ChatFormatting.GRAY);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty() && REQUESTS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, LinkedHashMap<UUID, Request>>> it = REQUESTS.entrySet().iterator();
             it.hasNext(); ) {
            Map.Entry<UUID, LinkedHashMap<UUID, Request>> entry = it.next();
            synchronized (entry.getValue()) {
                entry.getValue().values().removeIf(request -> request.expires() < now);
                if (entry.getValue().isEmpty()) {
                    it.remove();
                }
            }
        }
        int tick = server.getTickCount();
        for (Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Pending> entry = it.next();
            Pending pending = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            if (player.position().distanceToSqr(pending.start()) > 0.36d) {
                it.remove();
                bar(player, "Teleportation annulee : tu as bouge.", ChatFormatting.RED);
                continue;
            }
            if (player.hurtTime > 0) {
                it.remove();
                bar(player, "Teleportation annulee : tu as pris un coup.", ChatFormatting.RED);
                continue;
            }
            int left = pending.due() - tick;
            if (left <= 0) {
                it.remove();
                if (canLeave(player)) {
                    pending.action().run();
                }
            } else if (left % 20 == 0) {
                bar(player, "Teleportation " + pending.what() + " dans " + left / 20 + " s : ne bouge pas.",
                        ChatFormatting.GOLD);
            }
        }
    }

    /** Pose le joueur a cote d'un autre, dans sa dimension. */
    static void to(ServerPlayer mover, ServerPlayer target) {
        if (!(target.level() instanceof ServerLevel level) || !travelLevel(level)) {
            tell(mover, target.getName().getString() + " est dans un lieu ou l'on ne se teleporte pas.",
                    ChatFormatting.RED);
            return;
        }
        mover.closeContainer();
        mover.fallDistance = 0.0f;
        mover.teleport(new TeleportTransition(level, target.position(), Vec3.ZERO, target.getYRot(), target.getXRot(),
                TeleportTransition.DO_NOTHING));
    }

    // -- Les demandes --------------------------------------------------------------------------

    /** {@code /tpa} (aller vers la cible) ou {@code /tpahere} (la faire venir). */
    static void request(ServerPlayer from, ServerPlayer target, boolean here) {
        if (from == target) {
            tell(from, "Tu ne peux pas te le demander a toi-meme.", ChatFormatting.RED);
            return;
        }
        if (Grades.data(target).tpaClosed() && Grades.can(target, GradeCommand.TPTOGGLE)) {
            tell(from, target.getName().getString() + " n'accepte pas les demandes de teleportation.",
                    ChatFormatting.RED);
            return;
        }
        LinkedHashMap<UUID, Request> received = REQUESTS.computeIfAbsent(target.getUUID(),
                id -> new LinkedHashMap<>());
        synchronized (received) {
            received.remove(from.getUUID());
            received.put(from.getUUID(), new Request(from.getUUID(), here,
                    System.currentTimeMillis() + REQUEST_MILLIS));
        }
        String name = from.getName().getString();
        tell(from, "Demande envoyee a " + target.getName().getString() + " : elle tient une minute.",
                ChatFormatting.GREEN);
        target.sendSystemMessage(Component.literal(name + (here ? " te demande de le rejoindre. "
                        : " demande a te rejoindre. ")).withStyle(ChatFormatting.YELLOW)
                .append(button("[Accepter]", "/tpaccept " + name, ChatFormatting.GREEN))
                .append(Component.literal(" "))
                .append(button("[Refuser]", "/tpdeny " + name, ChatFormatting.RED)));
    }

    /** Accepte la demande de ce joueur, ou la plus recente. */
    static void accept(ServerPlayer target, ServerPlayer from) {
        Request request = take(target, from);
        if (request == null) {
            tell(target, from == null ? "Aucune demande de teleportation en attente."
                    : "Pas de demande de " + from.getName().getString() + ".", ChatFormatting.RED);
            return;
        }
        ServerPlayer asker = target.level().getServer().getPlayerList().getPlayer(request.from());
        if (asker == null) {
            tell(target, "Ce joueur n'est plus la.", ChatFormatting.RED);
            return;
        }
        tell(target, "Demande de " + asker.getName().getString() + " acceptee.", ChatFormatting.GREEN);
        if (request.here()) {
            schedule(target, "vers " + asker.getName().getString(), () -> to(target, asker));
        } else {
            tell(asker, target.getName().getString() + " a accepte.", ChatFormatting.GREEN);
            schedule(asker, "vers " + target.getName().getString(), () -> to(asker, target));
        }
    }

    static void deny(ServerPlayer target, ServerPlayer from) {
        Request request = take(target, from);
        if (request == null) {
            tell(target, "Aucune demande a refuser.", ChatFormatting.RED);
            return;
        }
        tell(target, "Demande refusee.", ChatFormatting.GRAY);
        ServerPlayer asker = target.level().getServer().getPlayerList().getPlayer(request.from());
        if (asker != null) {
            tell(asker, target.getName().getString() + " a refuse ta demande.", ChatFormatting.RED);
        }
    }

    private static Request take(ServerPlayer target, ServerPlayer from) {
        LinkedHashMap<UUID, Request> received = REQUESTS.get(target.getUUID());
        if (received == null) {
            return null;
        }
        synchronized (received) {
            long now = System.currentTimeMillis();
            received.values().removeIf(request -> request.expires() < now);
            if (from != null) {
                return received.remove(from.getUUID());
            }
            Request latest = null;
            for (Request request : received.values()) {
                latest = request;
            }
            if (latest != null) {
                received.remove(latest.from());
            }
            return latest;
        }
    }

    // -- Les messages --------------------------------------------------------------------------

    static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    static void bar(ServerPlayer player, String text, ChatFormatting color) {
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(text).withStyle(color)));
    }

    static MutableComponent button(String text, String command, ChatFormatting color) {
        return Component.literal(text).withStyle(style -> style.withColor(color)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        REQUESTS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        REQUESTS.clear();
    }
}
