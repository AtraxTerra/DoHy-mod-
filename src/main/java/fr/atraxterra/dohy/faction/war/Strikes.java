package fr.atraxterra.dohy.faction.war;

import fr.atraxterra.dohy.DoHy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les frappes de missiles en cours, et ce qui les relie au monde : le tick, le
 * guidage des missiles, ce qui ne doit pas tomber au sol, le travail des
 * joueurs dans les zones qui attendent leur retour, les deconnexions et
 * l'arret du serveur.
 *
 * <p><b>Quand une nation tire.</b> Devant une base imprenable (le
 * planificateur du siege ne trouve aucune entree), une nation qui a de quoi
 * payer un missile tire au lieu de lever le siege. Chaque frappe sur toi
 * monte d'un cran : explosif, explosif lourd, missile de rupture (perforant
 * pour une doctrine de siege, infernal pour une doctrine de choc), puis ogive
 * tactique et missile nucleaire. Une nation d'honneur ou prudente s'arrete
 * avant le nucleaire, sauf si elle te hait ; toutes redescendent d'un cran
 * quand leur tresor ne suit pas.
 */
@EventBusSubscriber
public final class Strikes {

    private static final Map<UUID, Strike> ACTIVE = new LinkedHashMap<>();
    private static boolean orphansDone = true;

    private Strikes() {
    }

    /** La frappe en cours contre ce joueur, ou {@code null}. */
    static Strike of(UUID player) {
        for (Strike strike : ACTIVE.values()) {
            if (strike.player.equals(player) && !strike.finished()) {
                return strike;
            }
        }
        return null;
    }

    public static boolean striking(UUID player) {
        return of(player) != null;
    }

    /**
     * Une base imprenable : la nation tire, si NTM est la et qu'elle a de quoi
     * payer. Rend la frappe, ou {@code null} (elle levera le siege).
     */
    static Strike plan(ServerPlayer player, WarData.Relation relation, ServerLevel level, BlockPos vault) {
        if (!NtmArsenal.ready() || of(player.getUUID()) != null) {
            return null;
        }
        NtmArsenal.Warhead warhead = choose(relation, false);
        return warhead == null ? null : start(player, relation.nation, level, vault, warhead, warhead.cost);
    }

    static Strike start(ServerPlayer player, int nation, ServerLevel level, BlockPos vault,
                        NtmArsenal.Warhead warhead, long cost) {
        Strike strike = new Strike(player, nation, level, vault, warhead, cost);
        ACTIVE.put(strike.id, strike);
        return strike;
    }

    /** La charge de la prochaine frappe : un cran de plus a chaque fois, selon son caractere et son tresor. */
    static NtmArsenal.Warhead choose(WarData.Relation relation, boolean free) {
        NationTraits traits = relation.traits();
        int done = relation.memory().strikes();
        double rage = relation.hostility() / 100.0d + 0.5d * traits.aggression() + 0.3d * traits.grudge();
        int rank = Math.min(done, 4);
        if (rank >= 3 && (traits.honor() >= 0.8d || traits.caution() >= 0.7d) && rage < 1.15d) {
            rank = 2;
        }
        NationTraits.Doctrine doctrine = traits.doctrine();
        NtmArsenal.Warhead warhead = switch (rank) {
            case 0 -> doctrine == NationTraits.Doctrine.CHOC ? NtmArsenal.Warhead.INCENDIARY
                    : NtmArsenal.Warhead.GENERIC;
            case 1 -> doctrine == NationTraits.Doctrine.CHOC ? NtmArsenal.Warhead.INCENDIARY_STRONG
                    : NtmArsenal.Warhead.STRONG;
            case 2 -> doctrine == NationTraits.Doctrine.SIEGE ? NtmArsenal.Warhead.DRILL
                    : doctrine == NationTraits.Doctrine.CHOC ? NtmArsenal.Warhead.INFERNO : NtmArsenal.Warhead.BURST;
            case 3 -> NtmArsenal.Warhead.TACTICAL;
            default -> NtmArsenal.Warhead.STRATEGIC;
        };
        while (warhead != null && (!NtmArsenal.available(warhead) || !free && relation.treasury() < warhead.cost)) {
            warhead = warhead.cheaper();
        }
        return warhead;
    }

    // -- Le monde ----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!orphansDone) {
            orphansDone = true;
            for (StrikeTerrain.Shelter shelter : StrikeTerrain.orphans()) {
                int count = shelter.putBack(server);
                StrikeTerrain.adopted(shelter);
                DoHy.LOGGER.info("Guerre : abri {} repose apres un arret du serveur ({} blocs)", shelter.key, count);
            }
        }
        if (!ACTIVE.isEmpty()) {
            for (Strike strike : List.copyOf(ACTIVE.values())) {
                try {
                    strike.tick();
                } catch (RuntimeException failure) {
                    DoHy.LOGGER.error("Guerre : la frappe {} contre {} a echoue, elle est arretee",
                            strike.traits.name(), strike.playerName, failure);
                    strike.abort("rate", strike.target());
                }
                if (strike.finished()) {
                    ACTIVE.remove(strike.id);
                }
            }
        }
        StrikeTerrain.tick(server);
    }

    /** Avant que les missiles ne bougent : le guidage, et l'impact s'ils touchent dans ce tick. */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Pre event) {
        if (ACTIVE.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (Strike strike : List.copyOf(ACTIVE.values())) {
            if (strike.level == level && strike.phase == Strike.Phase.FLIGHT) {
                try {
                    strike.guide();
                } catch (RuntimeException failure) {
                    DoHy.LOGGER.error("Guerre : guidage du missile {} en echec", strike.traits.name(), failure);
                    strike.abort("rate", strike.target());
                }
            }
        }
    }

    /**
     * Ce que l'explosion fait tomber (une torche dont le mur saute, un bloc
     * qui ne tient plus) ne tombe pas : le terrain reviendra. Ce qu'un joueur
     * casse dans une zone relevee y restera casse.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBlockDrops(BlockDropsEvent event) {
        ServerLevel level = event.getLevel();
        BlockPos pos = event.getPos();
        if (event.getBreaker() instanceof Player) {
            StrikeTerrain.touch(level, pos);
            return;
        }
        if (ACTIVE.isEmpty() || event.getBlockEntity() != null) {
            return;
        }
        for (Strike strike : ACTIVE.values()) {
            if (strike.blasting(level, pos)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /** Ce qu'on pose dans une zone relevee y restera quand le terrain reviendra. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        StrikeTerrain.touch(level, event.getPos());
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            for (BlockSnapshot snapshot : multi.getReplacedBlockSnapshots()) {
                StrikeTerrain.touch(level, snapshot.getPos());
            }
        }
    }

    /** La TNT d'un joueur dans une zone relevee : ce qu'elle casse reste casse. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getExplosion().getIndirectSourceEntity() instanceof Player)) {
            return;
        }
        for (BlockPos pos : event.getAffectedBlocks()) {
            StrikeTerrain.touch(level, pos);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        // Un missile ou une explosion d'une frappe coupee par un arret ne repart pas tout seul.
        if (event.loadedFromDisk() && entity.entityTags().contains(Strike.TAG)) {
            event.setCanceled(true);
            return;
        }
        if (ACTIVE.isEmpty() || !(entity instanceof ItemEntity item)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (Strike strike : ACTIVE.values()) {
            if (strike.irradiating(level, item.position())) {
                strike.guard(item);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Strike strike = of(player.getUUID());
            if (strike != null && strike.pending()) {
                DoHy.LOGGER.info("Guerre : {} se deconnecte, la frappe {} est annulee", strike.playerName,
                        strike.traits.de());
                strike.abort("annule", player);
                ACTIVE.remove(strike.id);
            }
        }
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        ACTIVE.clear();
        StrikeTerrain.start(event.getServer());
        orphansDone = false;
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (Strike strike : List.copyOf(ACTIVE.values())) {
            ServerPlayer target = strike.target();
            if (strike.pending() && target != null) {
                target.sendSystemMessage(net.minecraft.network.chat.Component.literal("Le serveur s'arrete : le tir "
                        + strike.traits.de() + " est annule.").withStyle(ChatFormatting.GRAY));
            }
            strike.abort(strike.pending() ? "annule" : strike.result, target);
        }
        ACTIVE.clear();
        StrikeTerrain.stop(event.getServer());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        orphansDone = true;
    }
}
