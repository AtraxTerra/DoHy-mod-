package fr.atraxterra.dohy.season;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.mixin.BiomeAccessor;
import fr.atraxterra.dohy.util.RealClock;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.time.LocalDateTime;

/**
 * Les saisons de la carte de la Terre, cote serveur : le calendrier, la mise en
 * service a l'ouverture du monde, l'envoi aux joueurs et la commande
 * {@code /saison}.
 *
 * <h2>Le calendrier</h2>
 *
 * <p>Une saison par semaine reelle, l'annee en quatre semaines (choix du
 * joueur). L'ancre est le lundi 16 novembre 2026 a 5 h, heure de
 * {@link RealClock} : l'hiver du nord s'ouvre alors, puis chaque lundi a 5 h
 * amene la saison suivante. Le temps est celui de l'horloge reelle : les
 * saisons avancent serveur eteint, comme les contrats.
 *
 * <p>Tout le reste lit ce calendrier : la temperature des biomes (mixin), la
 * neige et la glace ({@link SeasonGround}), les cultures ({@link SeasonCrops}),
 * et cote client les couleurs et l'atlas.
 */
@EventBusSubscriber
public final class Seasons {

    /** Une saison par semaine : l'annee dure quatre semaines. */
    public static final long YEAR = 28L * 24L * 3_600_000L;

    /** Decalage d'horloge pour les essais ({@code /saison admin avancer}), jamais sauvegarde. */
    private static volatile long shift;

    private static int ticks;

    private Seasons() {
    }

    /** Le lundi 16 novembre 2026 a 5 h : l'ouverture d'un hiver du nord. */
    static long anchor() {
        return LocalDateTime.of(2026, 11, 16, 5, 0).atZone(RealClock.zone()).toInstant().toEpochMilli();
    }

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(SeasonSync.TYPE, SeasonSync.STREAM_CODEC);
    }

    /** Apres le chargement de la carte (priorite basse : EarthEvents passe d'abord). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        SeasonGround.clear();
        configure();
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        SeasonClimate.SERVER.clear();
        SeasonGround.clear();
        shift = 0L;
    }

    /** Met le contexte du serveur en service si la carte de la Terre est la ; hors service sinon. */
    private static void configure() {
        EarthData earth = EarthData.get();
        if (earth == null) {
            SeasonClimate.SERVER.clear();
            return;
        }
        int west = (int) Math.floor(earth.x(-180.0d));
        int east = (int) Math.ceil(earth.x(180.0d));
        int north = (int) Math.floor(earth.z(85.0d));
        int south = (int) Math.ceil(earth.z(-85.0d));
        SeasonClimate.Frame frame = new SeasonClimate.Frame(
                !"equirectangular".equals(earth.config().projection()), earth.config().blocksPerDegree(),
                earth.config().equatorZ(), Math.min(west, east), Math.max(west, east), Math.min(north, south),
                Math.max(north, south), earth.config().dimension().toString());
        SeasonClimate.SERVER.configure(frame, new SeasonClimate.Clock(anchor() - shift, YEAR), 0L);
        double phase = SeasonClimate.SERVER.phase();
        DoHy.LOGGER.info("Saisons : {} au nord, {} au sud (annee de 4 semaines, une saison par semaine)",
                SeasonMath.name(45.0d, phase), SeasonMath.name(-45.0d, phase));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!SeasonClimate.SERVER.active()) {
            return;
        }
        if (++ticks % 100 == 0) {
            SeasonClimate.SERVER.refresh();
        }
        ServerLevel level = level(event.getServer());
        if (level != null) {
            SeasonGround.tick(level);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player);
        }
    }

    private static void send(ServerPlayer player) {
        SeasonClimate climate = SeasonClimate.SERVER;
        if (climate.active()) {
            PacketDistributor.sendToPlayer(player,
                    SeasonSync.of(climate.frame(), climate.clock(), climate.now()));
        }
    }

    /** La dimension de la carte, ou {@code null} si les saisons ne tournent pas. */
    static ServerLevel level(MinecraftServer server) {
        EarthData earth = EarthData.get();
        return earth == null || !SeasonClimate.SERVER.active() ? null : server.getLevel(earth.dimension());
    }

    /** Vrai si ce monde vit les saisons : la dimension de la carte, saisons en service. */
    static boolean covers(Level level) {
        EarthData earth = EarthData.get();
        return earth != null && SeasonClimate.SERVER.active() && earth.covers(level);
    }

    /** La latitude d'un z sur la carte. */
    static double latitude(double z) {
        SeasonClimate.Frame frame = SeasonClimate.SERVER.frame();
        return frame == null ? 0.0d : frame.latitude(z);
    }

    /** La temperature du biome en ce point, saison comprise. */
    public static float temperature(Level level, BlockPos pos) {
        return ((BiomeAccessor) (Object) level.getBiome(pos).value())
                .dohy$invokeTemperature(pos, level.getSeaLevel());
    }

    // -- La commande ---------------------------------------------------------

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(command());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("saison")
                .executes(context -> show(context.getSource().getPlayerOrException()))
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("avancer")
                                .then(Commands.argument("jours", DoubleArgumentType.doubleArg(-365.0d, 365.0d))
                                        .executes(context -> advance(context.getSource(),
                                                DoubleArgumentType.getDouble(context, "jours")))))
                        .then(Commands.literal("remettre")
                                .executes(context -> advance(context.getSource(), Double.NaN))));
    }

    private static int show(ServerPlayer player) {
        if (!covers(player.level())) {
            player.sendSystemMessage(Component.literal("Les saisons n'existent que sur la carte de la Terre.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        double phase = SeasonClimate.SERVER.phase();
        double latitude = latitude(player.getZ());
        double local = SeasonMath.local(phase, latitude);
        int celsius = SeasonMath.celsius(temperature(player.level(), player.blockPosition()));
        String name = SeasonMath.name(latitude, phase);
        String line;
        String next;
        double left;
        if (SeasonMath.tropical(latitude)) {
            line = name + " (tropiques)";
            // la bascule seche / pluies tombe aux mi-saisons : 0,375 et 0,875 de l'annee locale
            double turn = local < 0.375d ? 0.375d : local < 0.875d ? 0.875d : 1.375d;
            left = turn - local;
            next = SeasonMath.wet(latitude, phase) ? "Saison seche" : "Saison des pluies";
        } else {
            int season = SeasonMath.season(local);
            line = name + " (hemisphere " + (latitude >= 0.0d ? "nord" : "sud") + "), jour "
                    + SeasonMath.day(local, 7) + " sur 7";
            left = SeasonMath.untilNext(local);
            next = SeasonMath.name((season + 1) % 4);
        }
        player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("Il fait " + celsius + " °C ici. " + next + " dans "
                + duration((long) (left * YEAR)) + ".").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int advance(CommandSourceStack source, double days) {
        shift = Double.isNaN(days) ? 0L : shift + (long) (days * 24.0d * 3_600_000.0d);
        configure();
        if (SeasonClimate.SERVER.active()) {
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                send(player);
            }
        }
        double phase = SeasonClimate.SERVER.phase();
        source.sendSuccess(() -> Component.literal("Saisons decalees de " + String.format(java.util.Locale.ROOT,
                "%.2f", shift / 86_400_000.0d) + " j : " + SeasonMath.name(45.0d, phase) + " au nord, "
                + SeasonMath.name(-45.0d, phase) + " au sud."), true);
        return 1;
    }

    /** « 4 j 2 h », « 5 h 12 min », « 25 min ». */
    static String duration(long millis) {
        long minutes = Math.max(0L, millis / 60_000L);
        long days = minutes / 1440L;
        long hours = minutes / 60L % 24L;
        if (days > 0) {
            return days + " j " + hours + " h";
        }
        return hours > 0 ? hours + " h " + String.format("%02d", minutes % 60L) + " min" : minutes + " min";
    }
}
