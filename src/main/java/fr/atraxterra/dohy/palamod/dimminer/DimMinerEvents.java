package fr.atraxterra.dohy.palamod.dimminer;

import fr.atraxterra.dohy.world.PlacedBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Les regles de la dimension ({@code ServerDimMinerEventHandler}).
 *
 * <ul>
 *   <li>aucun monstre : toute entite hostile est refusee ;</li>
 *   <li>pas de degats de chute ;</li>
 *   <li>pas de conteneur pose (coffre, four, entonnoir...) : rien a stocker ;</li>
 *   <li>l'obsidienne wither de l'arrivee ne se casse pas ;</li>
 *   <li>paladium 1 point, paladium vert 5, pepite d'endium 501 : sans assez de
 *       points, le minerai ne se casse pas ;</li>
 *   <li>pendant l'effondrement, ni pose ni casse ;</li>
 *   <li>une explosion ne casse rien.</li>
 * </ul>
 * Le createur et le spectateur y echappent, comme le {@code bypass} de
 * l'original.
 *
 * <p><b>Ecart</b> : « Vous ne pouvez pas mourir » (wiki) n'etait pas dans le
 * code ; sans serveur de repli, une mort renverrait au point de reapparition
 * de l'overworld en laissant la session en plan. La mort est donc evitee : le
 * joueur est soigne et ramene a l'arrivee.
 */
@EventBusSubscriber
public final class DimMinerEvents {

    private DimMinerEvents() {
    }

    private static boolean exempt(ServerPlayer player) {
        return player.isCreative() || player.isSpectator();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DimMinerManager.tick(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DimMinerManager.settle(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DimMinerManager.hideBar(player);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Enemy && DimMinerManager.levelIs(event.getLevel())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && DimMinerManager.inDimension(player)
                && event.getSource().is(DamageTypeTags.IS_FALL)) {
            event.setCanceled(true);
        }
    }

    /** Avant la penalite de mort de DoHy : une mort evitee ne coute rien. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !DimMinerManager.inDimension(player)
                || exempt(player)) {
            return;
        }
        DimMinerData data = DimMinerManager.data(player);
        BlockPos arrival = data.arrival();
        if (arrival == null) {
            return;
        }
        event.setCanceled(true);
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        player.setAirSupply(player.getMaxAirSupply());
        player.fallDistance = 0.0f;
        player.teleport(new TeleportTransition((ServerLevel) player.level(), Vec3.atBottomCenterOf(arrival),
                Vec3.ZERO, player.getYRot(), 0.0f, TeleportTransition.DO_NOTHING));
        DimMinerManager.message(player, ChatFormatting.GOLD, "saved");
    }

    /** Avant les contrats et le suivi des blocs poses : une pose refusee ne compte nulle part. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !DimMinerManager.levelIs(player.level())
                || exempt(player)) {
            return;
        }
        if (!DimMinerManager.data(player).isActive()) {
            event.setCanceled(true);
            DimMinerManager.message(player, ChatFormatting.RED, "closing");
        } else if (event.getLevel().getBlockEntity(event.getPos()) instanceof Container) {
            event.setCanceled(true);
            DimMinerManager.message(player, ChatFormatting.RED, "no_container");
        }
    }

    /** Les refus, avant les autres ecouteurs. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !DimMinerManager.levelIs(player.level())
                || exempt(player)) {
            return;
        }
        DimMinerData data = DimMinerManager.data(player);
        if (!data.isActive()) {
            event.setCanceled(true);
            DimMinerManager.message(player, ChatFormatting.RED, "closing");
        } else if (event.getState().is(PalaDimMiner.WITHERED_OBSIDIAN.get())) {
            event.setCanceled(true);
            DimMinerManager.message(player, ChatFormatting.RED, "unbreakable");
        } else if (DimMinerManager.cost(event.getState()) > data.points()
                && !PlacedBlocks.isPlayerPlaced(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            DimMinerManager.message(player, ChatFormatting.RED, "not_enough_points");
        }
    }

    /**
     * Le decompte, une fois la casse acquise : les points des minerais, le
     * total et les minerais suivis. Un bloc pose par un joueur ne compte pas.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBroken(BreakBlockEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)
                || !DimMinerManager.levelIs(player.level()) || exempt(player)
                || PlacedBlocks.isPlayerPlaced(event.getLevel(), event.getPos())) {
            return;
        }
        DimMinerData data = DimMinerManager.data(player);
        long cost = DimMinerManager.cost(event.getState());
        // Un outil de zone casse ses blocs voisins entre le controle et ici :
        // ils ont pu vider les points. On redemande.
        if (cost > data.points()) {
            event.setCanceled(true);
            DimMinerManager.message(player, ChatFormatting.RED, "not_enough_points");
            return;
        }
        data.spend(cost);
        data.mined(DimMinerManager.trackedKey(event.getState()));
    }

    /**
     * <b>Ecart</b> : une explosion n'y casse aucun bloc. Sans quoi la TNT
     * abattrait les minerais sans payer de points, et la pepite d'endium ne
     * serait plus unique par session.
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (DimMinerManager.levelIs(event.getLevel())) {
            event.getAffectedBlocks().clear();
        }
    }

    /** {@code /dimminer quitter} : sortir sans attendre la fin, comme {@code /lobby}. */
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dimminer")
                .then(Commands.literal("quitter").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    if (!DimMinerManager.inDimension(player)) {
                        DimMinerManager.message(player, ChatFormatting.RED, "not_inside");
                        return 0;
                    }
                    DimMinerManager.leave(player, false);
                    return 1;
                })));
    }
}
