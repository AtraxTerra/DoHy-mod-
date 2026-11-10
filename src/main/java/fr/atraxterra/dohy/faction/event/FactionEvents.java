package fr.atraxterra.dohy.faction.event;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.palamod.boss.BossKind;
import fr.atraxterra.dohy.palamod.boss.EventBoss;
import fr.atraxterra.dohy.palamod.boss.RivalFighter;
import fr.atraxterra.dohy.palamod.boss.VolcaniaDragon;
import fr.atraxterra.dohy.palamod.dungeon.ReturnPoint;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Le calendrier des events de faction et l'event en cours (lot 22f).
 *
 * <p>Toutes les secondes, chaque event pret regarde son prochain depart, a
 * l'heure du serveur : annonce a 10, 5 et 1 minute, puis depart a la minute
 * pile. Un event ne se lance que si au moins un joueur en faction est
 * connecte — personne pour le voir, personne pour le gagner. Un seul event a la
 * fois, comme le boss unique de Paladium.
 *
 * <p>On rejoint l'arene par l'ecran des events ou {@code /f event rejoindre} ;
 * on en revient par {@code /f event quitter}, a la fin de l'event, ou en
 * mourant. <b>Dans la Warzone, on garde son inventaire</b> : en solo, l'arene
 * n'est ouverte que le temps de l'event, et ce qui y tombe y resterait.
 */
@EventBusSubscriber
public final class FactionEvents {

    /** L'etiquette des sbires du boss, que les rivaux attaquent et que la fin efface. */
    public static final String MINION_TAG = "palamod_boss_minion";

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final int[] WARNINGS = {10, 5, 1};

    private static EventRun active;
    private static final Set<String> said = new HashSet<>();
    private static final Map<UUID, List<ItemStack>> kept = new HashMap<>();

    private FactionEvents() {
    }

    public static EventRun active() {
        return active != null && !active.done() ? active : null;
    }

    /** L'event de ce nom s'il est en cours ; sinon {@code null}, et ses rivaux s'effacent. */
    public static EventRun run(UUID id) {
        EventRun run = active();
        return run != null && run.id().equals(id) ? run : null;
    }

    /** Le combat de boss de ce nom s'il est en cours ; sinon {@code null}, et son boss s'efface. */
    public static BossFight fight(UUID id) {
        return run(id) instanceof BossFight fight ? fight : null;
    }

    public static ZonedDateTime now() {
        return ZonedDateTime.now(ZONE);
    }

    // -- Calendrier ------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (active != null) {
            active.tick(server);
            if (active.done()) {
                active = null;
            }
        }
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        ZonedDateTime now = now().truncatedTo(ChronoUnit.MINUTES);
        for (FactionEventKind kind : FactionEventKind.values()) {
            if (!kind.ready()) {
                continue;
            }
            ZonedDateTime next = kind.next(now);
            long minutes = Duration.between(now, next).toMinutes();
            String key = kind.id() + "@" + next.toEpochSecond();
            for (int warning : WARNINGS) {
                if (minutes == warning && said.add(key + "-" + warning)) {
                    broadcast(server, Component.literal("[Event] " + kind.label() + " dans " + warning + " min. ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal("/f event").withStyle(ChatFormatting.YELLOW)));
                }
            }
            if (minutes == 0 && said.add(key + "-0")) {
                String result = start(server, kind, null);
                if (result != null) {
                    DoHy.LOGGER.info("Event {} non lance : {}", kind.id(), result);
                }
            }
        }
    }

    /** Le boss du creneau : ils tournent, deux par jour. */
    static BossKind bossOfTheSlot(ZonedDateTime time) {
        long slot = time.toLocalDate().toEpochDay() * 2L + (time.getHour() < 12 ? 0 : 1);
        return BossKind.values()[(int) Math.floorMod(slot, BossKind.values().length)];
    }

    /**
     * Lance un event maintenant. Rend {@code null} si c'est parti, sinon la
     * raison. {@code boss} choisit le boss (commande d'operateur) ; {@code null}
     * prend celui du creneau.
     */
    public static String start(MinecraftServer server, FactionEventKind kind, BossKind boss) {
        if (active() != null) {
            return "Un event est deja en cours.";
        }
        if (!kind.ready()) {
            return kind.label() + " arrive dans un prochain lot.";
        }
        boolean anyone = server.getPlayerList().getPlayers().stream().anyMatch(FactionApi::has);
        if (!anyone) {
            return "Aucun joueur en faction connecte.";
        }
        EventRun run;
        String subtitle;
        switch (kind) {
            case BOSS -> {
                BossKind chosen = boss != null ? boss : bossOfTheSlot(now());
                run = BossFight.start(server, chosen);
                subtitle = chosen.label() + " est apparu dans l'arene";
            }
            case KOTH -> {
                run = KothRun.start(server);
                subtitle = "Prends la colline et tiens-la 30 min";
            }
            case TOTEM -> {
                run = TotemRun.start(server);
                subtitle = "Casse le totem, depose ses blocs";
            }
            case EGG_HUNT -> {
                run = EggHuntRun.start(server);
                subtitle = "Volcania survole son ile";
            }
            default -> {
                return kind.label() + " arrive dans un prochain lot.";
            }
        }
        if (run == null) {
            return "La Warzone est introuvable.";
        }
        active = run;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!FactionApi.has(player)) {
                continue;
            }
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Event " + kind.label())
                    .withStyle(ChatFormatting.GOLD)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(subtitle)
                    .withStyle(ChatFormatting.RED)));
        }
        broadcast(server, Component.literal("[Event] " + kind.label() + " : " + subtitle.toLowerCase() + " ! ")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal("/f event rejoindre").withStyle(ChatFormatting.YELLOW)));
        return null;
    }

    /** Arrete l'event en cours (commande d'operateur). */
    public static String stop(MinecraftServer server) {
        EventRun run = active();
        if (run == null) {
            return "Aucun event en cours.";
        }
        ServerLevel level = EventArena.level(server);
        if (level != null) {
            run.stop(level, "L'event a ete arrete.");
            run.finish(level);
        }
        active = null;
        return null;
    }

    // -- Aller et retour -------------------------------------------------------

    public static boolean inWarzone(ServerPlayer player) {
        return player.level().dimension() == EventArena.DIMENSION;
    }

    /** Rejoint l'arene de l'event en cours. */
    public static String join(ServerPlayer player) {
        EventRun run = active();
        if (run == null || run.ended()) {
            return "Aucun event en cours.";
        }
        if (!FactionApi.has(player)) {
            return "Il te faut une faction.";
        }
        ServerLevel arena = EventArena.level(player.level().getServer());
        if (arena == null) {
            return "La Warzone est introuvable.";
        }
        if (!inWarzone(player)) {
            player.setData(Factions.EVENT_RETURN, ReturnPoint.of(player));
        }
        Vec3 entry = run.entry();
        player.teleport(new TeleportTransition(arena, entry, Vec3.ZERO, 180.0f, run.entryPitch(),
                TeleportTransition.DO_NOTHING));
        player.sendSystemMessage(Component.literal("Bienvenue dans l'arene. Tu gardes ton inventaire ici. ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("/f event quitter").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" pour repartir.").withStyle(ChatFormatting.GRAY)));
        return null;
    }

    public static String leave(ServerPlayer player) {
        if (!inWarzone(player)) {
            return "Tu n'es pas dans la Warzone.";
        }
        if (active() instanceof EggHuntRun hunt) {
            hunt.onLeave(player);
        }
        sendBack(player);
        return null;
    }

    /** {@code /f event lacher} : poser l'oeuf de l'Egg Hunt. */
    public static String dropEgg(ServerPlayer player) {
        if (!(active() instanceof EggHuntRun hunt)) {
            return "Il n'y a pas d'oeuf a lacher.";
        }
        return hunt.drop(player);
    }

    /** Ramene le joueur d'ou il venait, ou au point d'apparition du monde. */
    static void sendBack(ServerPlayer player) {
        ReturnPoint back = player.getData(Factions.EVENT_RETURN);
        player.removeData(Factions.EVENT_RETURN);
        if (back.isNone() || back.dimension().equals(EventArena.DIMENSION.identifier().toString())) {
            ServerLevel overworld = player.level().getServer().overworld();
            Vec3 spawn = Vec3.atBottomCenterOf(overworld.getRespawnData().pos());
            player.teleport(new TeleportTransition(overworld, spawn, Vec3.ZERO, 0.0f, 0.0f,
                    TeleportTransition.DO_NOTHING));
            return;
        }
        back.send(player);
    }

    static void broadcast(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (FactionApi.has(player)) {
                player.sendSystemMessage(message);
            }
        }
    }

    // -- Evenements du jeu -----------------------------------------------------

    /** Connexion dans la Warzone sans event : retour. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (inWarzone(player) && active() == null) {
                sendBack(player);
            } else if (active() != null && !active().ended() && FactionApi.has(player)) {
                player.sendSystemMessage(Component.literal("[Event] " + active().title()
                        + " : c'est en cours. ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("/f event rejoindre").withStyle(ChatFormatting.YELLOW)));
            }
        }
    }

    /** Mort dans la Warzone : l'inventaire est mis de cote, rien ne tombe. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && inWarzone(player) && !event.isCanceled()) {
            Inventory inventory = player.getInventory();
            List<ItemStack> copy = new ArrayList<>(inventory.getContainerSize());
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                copy.add(inventory.getItem(i).copy());
            }
            kept.put(player.getUUID(), copy);
            inventory.clearContent();
            if (active() instanceof KothRun koth) {
                koth.died(player);
            }
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        List<ItemStack> copy = kept.remove(player.getUUID());
        if (copy == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < copy.size() && i < inventory.getContainerSize(); i++) {
            inventory.setItem(i, copy.get(i));
        }
        player.removeData(Factions.EVENT_RETURN);
    }

    /**
     * Dans la Warzone, on ne casse que ce qu'on a pose pendant l'event (et les
     * toiles d'Arachna). Le totem, lui, se casse par le haut : {@link TotemRun}.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !inWarzone(player)) {
            return;
        }
        EventRun run = active();
        if (run instanceof TotemRun totem && totem.onBreak(player, event.getPos())) {
            event.setCanceled(true);
            return;
        }
        if (run instanceof EggHuntRun hunt && hunt.isEgg(event.getPos())) {
            event.setCanceled(true);
            hunt.tryPickup(player);
            return;
        }
        if (run != null && run.breakable(event.getPos())) {
            run.forget(event.getPos());
            return;
        }
        if (!player.isCreative()) {
            event.setCanceled(true);
        }
    }

    /** On pose pour se proteger (contre le Belier de Tobalt, par exemple) ; tout part a la fin. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !inWarzone(player) || player.isCreative()) {
            return;
        }
        EventRun run = active();
        if (run == null || run.ended()) {
            event.setCanceled(true);
            return;
        }
        run.placed(event.getPos());
    }

    /** Une explosion dans la Warzone n'abime pas les arenes. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension() != EventArena.DIMENSION) {
            return;
        }
        EventRun run = active();
        event.getAffectedBlocks().removeIf(pos -> run == null || !run.breakable(pos));
    }

    /**
     * L'oeuf de l'Egg Hunt se prend d'un clic, gauche ou droit. L'oeuf de dragon
     * vanilla se teleporte quand on le touche : dans la Warzone, jamais.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        touchEgg(event.getEntity(), event.getPos(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        touchEgg(event.getEntity(), event.getPos(), event);
    }

    private static void touchEgg(Player who, BlockPos pos, net.neoforged.bus.api.ICancellableEvent event) {
        if (!(who instanceof ServerPlayer player) || !inWarzone(player)
                || !player.level().getBlockState(pos).is(Blocks.DRAGON_EGG)) {
            return;
        }
        event.setCanceled(true);
        if (active() instanceof EggHuntRun hunt && hunt.isEgg(pos)) {
            hunt.tryPickup(player);
        }
    }

    /** Le porteur de l'oeuf meurt : son tueur le lui vole, sinon l'oeuf retombe. */
    @SubscribeEvent
    public static void onAnyDeath(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level && level.dimension() == EventArena.DIMENSION
                && active() instanceof EggHuntRun hunt) {
            hunt.onDeath(level, event.getEntity(), event.getSource().getEntity());
        }
    }

    /** Dans la Warzone, aucun monstre ne casse de bloc : Volcania traverse l'ile sans l'abimer. */
    @SubscribeEvent
    public static void onGrief(EntityMobGriefingEvent event) {
        if (event.getEntity().level().dimension() == EventArena.DIMENSION) {
            event.setCanGrief(false);
        }
    }

    /**
     * La branche Defense de l'arbre : « -5 a -20 % de degats en event ». Le
     * PalaMod la donne contre les raids ; il n'y en a pas en solo, elle vaut donc
     * contre les boss, Volcania, leurs sbires et les combattants rivaux.
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !inWarzone(player) || !FactionApi.has(player)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        boolean eventFoe = source instanceof EventBoss || source instanceof VolcaniaDragon
                || source instanceof RivalFighter
                || (source != null && source.entityTags().contains(MINION_TAG));
        if (eventFoe) {
            event.setAmount(event.getAmount() * FactionApi.perks(player).raidDamage());
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        active = null;
        said.clear();
    }
}
