package fr.atraxterra.dohy.faction.marks;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.collection.CollectionEvents;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import fr.atraxterra.dohy.util.RealClock;
import fr.atraxterra.dohy.world.PlacedBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A vos marques (« Java | A vos marques » du wiki de Paladium) : un event
 * quotidien de farm. Pendant une manche de 30 minutes, chaque joueur doit
 * atteindre un objectif : miner, tuer des creatures, couper du bois ou pecher
 * une certaine quantite ({@link MarksGoal}). Chaque joueur qui l'atteint
 * rapporte 7 ELO a sa nation, 70 au plus par nation et par jour. L'objectif et
 * le compte a rebours restent affiches (une barre en haut de l'ecran, dans les
 * mondes ou l'objectif compte) ; dans la deuxieme variante, la manche aux
 * points, la barre compte des points.
 *
 * <p>Le calendrier et la rotation des objectifs : {@link MarksRound}. Une
 * manche d'essai se lance par {@code /f event marques lancer <objectif>}.
 */
@EventBusSubscriber
public final class Marks {

    /** L'ELO d'un objectif atteint. */
    public static final long ELO = 7L;
    /** L'ELO au plus par nation et par jour. */
    public static final long DAILY_CAP = 70L;

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, DoHy.MOD_ID);

    public static final Supplier<AttachmentType<MarksData>> DATA = ATTACHMENTS.register("marks",
            () -> AttachmentType.builder(MarksData::new)
                    .serialize(MarksData.MAP_CODEC, data -> !data.isEmpty())
                    .copyOnDeath()
                    .build());

    /** La manche suivie : celle dont le depart a ete annonce. */
    private static MarksRound current;
    /** Une manche d'essai, qui passe avant le calendrier tant qu'elle dure. */
    private static MarksRound forced;
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private Marks() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static MarksData data(ServerPlayer player) {
        return player.getData(DATA.get());
    }

    static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    static ZonedDateTime clock() {
        return ZonedDateTime.now(RealClock.zone());
    }

    /** Le jour du plafond d'ELO, a minuit heure de {@link RealClock}. */
    static long today() {
        return RealClock.dayKey(0);
    }

    /** La manche en cours, ou {@code null}. */
    public static MarksRound active() {
        if (forced != null) {
            if (now() < forced.end()) {
                return forced;
            }
            forced = null;
        }
        return MarksRound.scheduledAt(clock());
    }

    /** Le prochain depart prevu. */
    public static ZonedDateTime next() {
        return MarksRound.nextStart(clock());
    }

    /** « 12:34 ». */
    static String clockText(long seconds) {
        long left = Math.max(0L, seconds);
        return left / 60L + ":" + String.format("%02d", left % 60L);
    }

    static String dimension(ServerPlayer player) {
        return player.level().dimension().identifier().toString();
    }

    static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal("[A vos marques] " + text).withStyle(color));
    }

    private static void sound(ServerPlayer player, Holder<SoundEvent> sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(sound, SoundSource.MASTER, player.getX(), player.getY(),
                player.getZ(), volume, pitch, player.getRandom().nextLong()));
    }

    // -- Le calendrier --------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 7) {
            return;
        }
        MarksRound round = active();
        long was = current == null ? 0L : current.id();
        long is = round == null ? 0L : round.id();
        if (was != is) {
            if (current != null) {
                finish(server, current);
            }
            current = round;
            if (round != null) {
                begin(server, round);
            }
        }
        if (round != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                refresh(player, round);
            }
        }
    }

    /** Le depart (ou, apres un redemarrage, la reprise) d'une manche. */
    static void begin(MinecraftServer server, MarksRound round) {
        boolean late = now() > round.start() + 60L;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            announce(player, round);
            if (!late) {
                sound(player, SoundEvents.NOTE_BLOCK_BELL, 1.0f, 1.2f);
            }
            if (round.shows(dimension(player))) {
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("A vos marques !")
                        .withStyle(ChatFormatting.GOLD)));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(round.objective()
                        + " en 30 min").withStyle(ChatFormatting.YELLOW)));
            }
        }
        DoHy.LOGGER.info("A vos marques : manche {} ({})", round.objective(), round.forced() ? "essai" : "prevue");
    }

    /** L'objectif de la manche, ou il compte et ce qu'il rapporte. */
    static void announce(ServerPlayer player, MarksRound round) {
        boolean late = now() > round.start() + 60L;
        String text = (late ? "Manche en cours, encore " + secondsLeft(round) / 60L + " min : "
                : "Partez ! 30 min : ") + round.objective().toLowerCase();
        String where = round.points() ? "Toutes les actions comptent (" + MarksRound.scale() + ")."
                : "Dans " + round.goal().where() + ".";
        tell(player, text + ". " + where + " +" + ELO + " ELO pour ta nation.", ChatFormatting.GOLD);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        MarksRound round = active();
        if (round != null && event.getEntity() instanceof ServerPlayer player && !data(player).done(round)) {
            announce(player, round);
        }
    }

    /** La fin d'une manche : le bilan de ceux qui n'ont pas fini, et plus de barres. */
    static void finish(MinecraftServer server, MarksRound round) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            MarksData data = data(player);
            long count = data.count(round);
            if (count > 0L && !data.done(round)) {
                tell(player, "Temps ecoule : " + MoneyFormat.exact(count) + " / " + MoneyFormat.exact(round.target())
                        + " " + (round.points() ? "points" : round.goal().unit()) + ".", ChatFormatting.YELLOW);
            }
        }
        for (ServerBossEvent bar : BARS.values()) {
            bar.removeAllPlayers();
        }
        BARS.clear();
    }

    // -- La barre ---------------------------------------------------------------------------------

    /** La barre du joueur : objectif, compteur et compte a rebours, dans les mondes ou ca compte. */
    static void refresh(ServerPlayer player, MarksRound round) {
        if (!round.shows(dimension(player))) {
            ServerBossEvent bar = BARS.remove(player.getUUID());
            if (bar != null) {
                bar.removeAllPlayers();
            }
            return;
        }
        MarksData data = data(player);
        long count = data.count(round);
        boolean done = data.done(round);
        String left = clockText(round.end() - now());
        String unit = round.points() ? "points" : round.goal().unit();
        String name = done ? "A vos marques · objectif atteint · " + left
                : "A vos marques · " + (round.points() ? "" : round.goal().label() + " ")
                + MoneyFormat.exact(count) + " / " + MoneyFormat.exact(round.target()) + " " + unit + " · " + left;
        ServerBossEvent bar = BARS.computeIfAbsent(player.getUUID(), id -> new ServerBossEvent(UUID.randomUUID(),
                Component.literal(name), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10));
        bar.setName(Component.literal(name));
        bar.setColor(done ? BossEvent.BossBarColor.GREEN : BossEvent.BossBarColor.YELLOW);
        bar.setProgress(done ? 1.0f : Math.min(1.0f, (float) count / (float) Math.max(1L, round.target())));
        if (!bar.getPlayers().contains(player)) {
            bar.removeAllPlayers();
            bar.addPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerBossEvent bar = BARS.remove(event.getEntity().getUUID());
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    // -- Les compteurs --------------------------------------------------------------------------

    /** Une action : {@code amount} blocs, buches, creatures ou prises. */
    public static void progress(ServerPlayer player, MarksGoal action, long amount) {
        MarksRound round = active();
        if (round == null || amount <= 0L || !round.counts(action) || player.isCreative() || player.isSpectator()
                || !action.worlds().contains(dimension(player))) {
            return;
        }
        MarksData data = data(player);
        data.enter(round);
        if (data.done) {
            return;
        }
        long gain = round.points() ? amount * action.points() : amount;
        data.count = Math.min(round.target(), data.count + gain);
        if (data.count >= round.target()) {
            complete(player, data, round);
        }
        refresh(player, round);
    }

    /** L'objectif atteint : 7 ELO pour la nation, dans la limite de 70 par jour. */
    static void complete(ServerPlayer player, MarksData data, MarksRound round) {
        data.done = true;
        data.wins++;
        FactionApi.stat(player, "marks:won", 1L);
        long today = today();
        if (data.day != today) {
            data.day = today;
            data.eloToday = 0L;
        }
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Objectif atteint !")
                .withStyle(ChatFormatting.GREEN)));
        sound(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PLAYER_LEVELUP), 0.8f, 1.2f);
        String done = "Objectif atteint : " + round.objective().toLowerCase();
        if (!FactionApi.has(player)) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Sans nation, pas d'ELO")
                    .withStyle(ChatFormatting.YELLOW)));
            tell(player, done + ". Fonde ou rejoins une nation pour que ca lui rapporte de l'ELO.",
                    ChatFormatting.GREEN);
            return;
        }
        long gain = Math.min(ELO, DAILY_CAP - data.eloToday);
        if (gain <= 0L) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Plafond du jour atteint")
                    .withStyle(ChatFormatting.YELLOW)));
            tell(player, done + ". Ta nation a deja ses " + DAILY_CAP + " ELO du jour.", ChatFormatting.GREEN);
            return;
        }
        data.eloToday += gain;
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("+" + gain
                + " ELO pour ta nation").withStyle(ChatFormatting.YELLOW)));
        tell(player, done + ". Aujourd'hui : " + data.eloToday + " / " + DAILY_CAP + " ELO.", ChatFormatting.GREEN);
        FactionApi.addElo(player, gain, "A vos marques");
    }

    /** Miner, couper du bois : les blocs qui tombent d'un joueur, pas ceux qu'il avait poses. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player)) {
            return;
        }
        MarksRound round = active();
        if (round == null) {
            return;
        }
        BlockState state = event.getState();
        MarksGoal action = state.is(BlockTags.LOGS) ? MarksGoal.WOOD
                : state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? MarksGoal.MINE : null;
        if (action == null || !round.counts(action)) {
            return;
        }
        if (PlacedBlocks.wasPlayerPlaced(event) && !state.is(CollectionEvents.PLACEMENT_EXEMPT)) {
            return;
        }
        progress(player, action, 1L);
    }

    /** Tuer : une creature qu'un joueur abat, ou qui meurt de ses coups. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob)) {
            return;
        }
        ServerPlayer player = event.getSource().getEntity() instanceof ServerPlayer direct ? direct
                : event.getEntity().getLastHurtByMob() instanceof ServerPlayer indirect ? indirect : null;
        if (player != null) {
            progress(player, MarksGoal.KILL, 1L);
        }
    }

    /** Pecher a la canne du jeu ; le mini-jeu des cannes de PalaMod passe par {@link #fished}. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFished(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            long count = 0L;
            for (ItemStack stack : event.getDrops()) {
                count += stack.getCount();
            }
            progress(player, MarksGoal.FISH, Math.max(1L, count));
        }
    }

    /** Une prise du mini-jeu de peche. */
    public static void fished(ServerPlayer player, int count) {
        progress(player, MarksGoal.FISH, Math.max(1, count));
    }

    // -- L'essai --------------------------------------------------------------------------------

    /**
     * Lance une manche d'essai de 30 minutes, maintenant : {@code mine}, {@code bois},
     * {@code creatures}, {@code peche} ou {@code points}. Rend {@code null} si c'est parti.
     */
    public static String force(MinecraftServer server, String key) {
        boolean points = "points".equals(key);
        MarksGoal goal = points ? MarksGoal.MINE : MarksGoal.byKey(key);
        if (goal == null) {
            return "Objectif inconnu : mine, bois, creatures, peche ou points.";
        }
        forced = MarksRound.forced(now(), goal, points);
        return null;
    }

    /** Arrete la manche d'essai. Rend {@code null} si elle est arretee. */
    public static String stopForced() {
        if (forced == null) {
            return "Aucune manche d'essai en cours.";
        }
        forced = null;
        return null;
    }

    /** Pour l'ecran des events : ce qu'il reste de la manche, en secondes. */
    public static long secondsLeft(MarksRound round) {
        return Math.max(0L, round.end() - now());
    }

    /** L'ELO gagne aujourd'hui par ce joueur. */
    public static long eloToday(ServerPlayer player) {
        return data(player).eloOn(today());
    }

    /** Toutes les barres effacees (arret du serveur). */
    static void clearBars() {
        for (Iterator<ServerBossEvent> it = BARS.values().iterator(); it.hasNext(); ) {
            it.next().removeAllPlayers();
            it.remove();
        }
        current = null;
    }

    @SubscribeEvent
    public static void onStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        clearBars();
        forced = null;
    }
}
