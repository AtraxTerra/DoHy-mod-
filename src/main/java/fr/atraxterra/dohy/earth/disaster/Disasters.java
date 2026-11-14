package fr.atraxterra.dohy.earth.disaster;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.season.SeasonClimate;
import fr.atraxterra.dohy.season.SeasonMath;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Les catastrophes de la Terre : seismes, eruptions, ouragans et pluies de
 * mousson, la ou et quand le monde reel les connait.
 *
 * <p><b>Ou.</b> Les seismes frappent les zones sismiques (le Japon, le Chili,
 * la Californie, la Turquie...), les eruptions les 27 volcans actifs de la
 * carte, les ouragans naissent dans les six bassins cycloniques et suivent
 * leur route, la mousson arrose l'Inde, l'Asie du Sud-Est, la Chine du Sud,
 * l'Afrique de l'Ouest et le nord de l'Australie.
 *
 * <p><b>Quand.</b> Toutes les vingt secondes, le jeu tire au sort. Un joueur
 * dans une zone sismique y attend un seisme toutes les 45 minutes en moyenne ;
 * a moins de 400 blocs d'un volcan, une eruption toutes les 75 minutes ; dans
 * une region de mousson pendant sa saison des pluies, une averse toutes les 25
 * minutes. Chaque bassin, pendant sa saison (la fin de l'ete et l'automne de
 * son hemisphere), lance une tempete toutes les 80 minutes ; si un joueur est
 * dans sa region, deux fois sur trois elle passe sur lui. Loin des joueurs, des
 * seismes et des eruptions lointains font les nouvelles.
 *
 * <p><b>Les degats</b> ne se font que pres des joueurs et reviennent avec le
 * terrain ({@link DisasterTerrain}). La lave ne met le feu a rien autour des
 * volcans. Pendant la saison des pluies, les cultures des regions de mousson
 * poussent plus vite.
 */
@EventBusSubscriber
public final class Disasters {

    private static final List<Disaster> ACTIVE = new ArrayList<>();
    /** La derniere catastrophe de chaque famille, finie ou non. */
    static final Map<DisasterKind, Disaster> LAST = new java.util.EnumMap<>(DisasterKind.class);
    private static final Map<String, Long> COOLDOWN = new HashMap<>();
    private static final Map<UUID, DisasterNet.Feel> FELT = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static int nextId = 1;
    private static int nameIndex;
    private static int ticks;
    private static int lastSync;

    private Disasters() {
    }

    public static MutableComponent prefix() {
        return Component.literal("[Terre] ").withStyle(ChatFormatting.DARK_AQUA);
    }

    // -- Le cycle du serveur ------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        DisasterConfig.load(event.getServer());
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        DisasterTerrain.start(event.getServer());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        // les catastrophes en cours s'arretent : leur terrain reviendra comme prevu
        for (Disaster disaster : ACTIVE) {
            if (!disaster.done) {
                disaster.finish(event.getServer());
            }
        }
        ACTIVE.clear();
        DisasterTerrain.stop();
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
        LAST.clear();
        COOLDOWN.clear();
        FELT.clear();
        LocalWeather.clear();
        Volcanoes.clear();
        DisasterConfig.clear();
        ticks = 0;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && EarthData.get() != null) {
            PacketDistributor.sendToPlayer(player, sync());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LocalWeather.forget(event.getEntity().getUUID());
        FELT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        EarthData earth = EarthData.get();
        if (earth != null && event.getLevel() instanceof ServerLevel level && earth.covers(level)
                && event.getChunk() instanceof LevelChunk chunk) {
            Volcanoes.arrived(chunk);
        }
    }

    /** Le monde de la carte, ou {@code null}. */
    private static ServerLevel level(MinecraftServer server) {
        EarthData earth = EarthData.get();
        return earth == null ? null : server.getLevel(earth.dimension());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = level(server);
        if (level == null) {
            return;
        }
        ticks++;
        Volcanoes.tick(level);
        for (Disaster disaster : new ArrayList<>(ACTIVE)) {
            try {
                disaster.tick(level);
            } catch (RuntimeException error) {
                DoHy.LOGGER.error("Catastrophe {} interrompue", disaster.name, error);
                disaster.finish(server);
            }
        }
        ACTIVE.removeIf(disaster -> disaster.done);
        int every = DisasterConfig.settings().checkSeconds() * 20;
        if (ticks % every == 0) {
            schedule(level);
        }
        if (ticks % 4 == 0) {
            feel(level);
        }
        if (ticks % 20 == 0) {
            broadcast(level);
        }
        LocalWeather.tick(server, level);
        DisasterTerrain.tick(server);
    }

    // -- Le tirage -----------------------------------------------------------------------------

    private static double chance(double meanMinutes) {
        return DisasterConfig.settings().checkSeconds() / (meanMinutes * 60.0d);
    }

    private static boolean ready(String key, long minutes) {
        Long last = COOLDOWN.get(key);
        return last == null || System.currentTimeMillis() - last >= minutes * 60_000L;
    }

    private static void mark(String key) {
        COOLDOWN.put(key, System.currentTimeMillis());
    }

    private static boolean activeNear(DisasterKind kind, double x, double z, double range) {
        for (Disaster disaster : ACTIVE) {
            if (disaster.kind == kind && Math.hypot(disaster.x - x, disaster.z - z) <= range) {
                return true;
            }
        }
        return false;
    }

    private static boolean full() {
        return ACTIVE.size() >= DisasterConfig.settings().maxActive();
    }

    private static void schedule(ServerLevel level) {
        if (!SeasonClimate.SERVER.active() || DisasterConfig.data().volcanoes().isEmpty()
                && DisasterConfig.data().zones().isEmpty()) {
            return;
        }
        DisasterConfig.Settings settings = DisasterConfig.settings();
        double phase = SeasonClimate.SERVER.phase();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || full()) {
                continue;
            }
            DisasterConfig.PlacedZone zone = DisasterConfig.zoneAt(player.getX(), player.getZ());
            if (zone != null && ready("zone:" + zone.zone().id(), 8) && !activeNear(DisasterKind.QUAKE,
                    player.getX(), player.getZ(), 600.0d) && RANDOM.nextDouble() < chance(settings.quakeMinutes())) {
                quake(level, zone, player, Double.NaN, Double.NaN);
            }
            DisasterConfig.PlacedVolcano volcano = DisasterConfig.nearestVolcano(player.getX(), player.getZ());
            if (volcano != null && volcano.distance(player.getX(), player.getZ()) <= settings.eruptionNearBlocks()
                    && ready("volcan:" + volcano.volcano().id(), 45) && eruptionAt(volcano) == null
                    && RANDOM.nextDouble() < chance(settings.eruptionMinutes())) {
                eruption(level, volcano, settings.eruptionWarningSeconds() * 20);
            }
            DisasterConfig.PlacedMonsoon monsoon = DisasterConfig.monsoonAt(player.getX(), player.getZ());
            if (monsoon != null && wet(monsoon, phase) && ready("mousson:" + player.getUUID(), 6)
                    && !activeNear(DisasterKind.MONSOON, player.getX(), player.getZ(), 200.0d)
                    && RANDOM.nextDouble() < chance(settings.monsoonMinutes())) {
                monsoon(level, monsoon, player.getX(), player.getZ());
                mark("mousson:" + player.getUUID());
            }
        }
        EarthData earth = EarthData.get();
        for (DisasterConfig.Basin basin : DisasterConfig.data().basins()) {
            if (full() || !basin.inSeason(SeasonMath.local(phase, basin.referenceLatitude()))
                    || stormOf(basin) != null || RANDOM.nextDouble() >= chance(settings.hurricaneMinutes())) {
                continue;
            }
            ServerPlayer target = null;
            List<ServerPlayer> inside = new ArrayList<>();
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && basin.covers(earth.latitude(player.getZ()),
                        earth.longitude(player.getX()))) {
                    inside.add(player);
                }
            }
            if (!inside.isEmpty() && RANDOM.nextDouble() < settings.hurricaneTargetShare()) {
                target = inside.get(RANDOM.nextInt(inside.size()));
            }
            hurricane(level, basin, target, 0, 300 + RANDOM.nextInt(180));
        }
        // les nouvelles lointaines
        if (!full() && !DisasterConfig.placed().zones().isEmpty()
                && RANDOM.nextDouble() < chance(settings.quakeNewsMinutes())) {
            DisasterConfig.PlacedZone zone = weighted(DisasterConfig.placed().zones());
            if (zone != null && ready("zone:" + zone.zone().id(), 8)) {
                quake(level, zone, null, Double.NaN, Double.NaN);
            }
        }
        if (!full() && !DisasterConfig.placed().volcanoes().isEmpty()
                && RANDOM.nextDouble() < chance(settings.eruptionNewsMinutes())) {
            List<DisasterConfig.PlacedVolcano> volcanoes = DisasterConfig.placed().volcanoes();
            DisasterConfig.PlacedVolcano volcano = volcanoes.get(RANDOM.nextInt(volcanoes.size()));
            if (ready("volcan:" + volcano.volcano().id(), 45) && eruptionAt(volcano) == null) {
                eruption(level, volcano, settings.eruptionWarningSeconds() * 20);
            }
        }
    }

    private static DisasterConfig.PlacedZone weighted(List<DisasterConfig.PlacedZone> zones) {
        double total = 0.0d;
        for (DisasterConfig.PlacedZone zone : zones) {
            total += zone.zone().weight();
        }
        double roll = RANDOM.nextDouble() * total;
        for (DisasterConfig.PlacedZone zone : zones) {
            roll -= zone.zone().weight();
            if (roll <= 0.0d) {
                return zone;
            }
        }
        return zones.isEmpty() ? null : zones.get(zones.size() - 1);
    }

    /** La saison des pluies d'une region de mousson : le coeur de la moitie chaude de son annee. */
    static boolean wet(DisasterConfig.PlacedMonsoon monsoon, double phase) {
        return SeasonMath.chill(SeasonMath.local(phase, monsoon.monsoon().latitude())) < -0.3d;
    }

    private static Eruption eruptionAt(DisasterConfig.PlacedVolcano volcano) {
        for (Disaster disaster : ACTIVE) {
            if (disaster instanceof Eruption eruption && eruption.volcano.volcano().id().equals(volcano.volcano().id())) {
                return eruption;
            }
        }
        return null;
    }

    private static Hurricane stormOf(DisasterConfig.Basin basin) {
        for (Disaster disaster : ACTIVE) {
            if (disaster instanceof Hurricane hurricane && hurricane.basin.id().equals(basin.id())) {
                return hurricane;
            }
        }
        return null;
    }

    // -- Les departs ---------------------------------------------------------------------------

    /** La magnitude d'un seisme : surtout des 5 et des 6, rarement plus (loi de Gutenberg-Richter adoucie). */
    private static double magnitude(double max) {
        double roll = Math.max(1.0e-6d, RANDOM.nextDouble());
        return Math.min(max, 5.0d - Math.log10(roll) / 0.6d);
    }

    static Quake quake(ServerLevel level, DisasterConfig.PlacedZone zone, ServerPlayer player, double magnitude,
                       double distance) {
        EarthData earth = EarthData.get();
        double m = Double.isNaN(magnitude) ? magnitude(zone == null ? 8.0d : zone.zone().maxMagnitude()) : magnitude;
        m = Math.round(m * 10.0d) / 10.0d;
        double damage = Math.min(40.0d, 8.0d + 8.0d * (m - 5.0d));
        double x;
        double z;
        double angle = RANDOM.nextDouble() * Math.PI;
        if (player != null) {
            if (Double.isNaN(distance)) {
                double r = Math.min(damage + 24.0d, damage * 0.5d + 8.0d + RANDOM.nextDouble() * 16.0d);
                double a = RANDOM.nextDouble() * Math.PI * 2.0d;
                x = player.getX() + Math.cos(a) * r;
                z = player.getZ() + Math.sin(a) * r;
            } else {
                // devant le joueur, la faille en travers de son regard
                Vec3 look = player.getLookAngle();
                double norm = Math.max(0.01d, Math.hypot(look.x, look.z));
                x = player.getX() + look.x / norm * distance;
                z = player.getZ() + look.z / norm * distance;
                angle = Math.atan2(look.z, look.x) + Math.PI / 2.0d;
            }
        } else {
            double[] point = zone.along(RANDOM.nextDouble());
            double spread = zone.half() * 0.5d;
            x = point[0] + (RANDOM.nextDouble() - 0.5d) * spread;
            z = point[1] + (RANDOM.nextDouble() - 0.5d) * spread;
        }
        EarthData.Country country = earth == null ? null : earth.countryAt((int) x, (int) z);
        String zoneName = zone == null ? "" : zone.zone().name();
        String place = country != null ? country.name() : zoneName.isEmpty() ? "en mer" : zoneName;
        Quake quake = new Quake(nextId++, m, zoneName, place, x, z, angle, RANDOM);
        if (zone != null) {
            mark("zone:" + zone.zone().id());
        }
        quake.begin(level);
        add(quake);
        DoHy.LOGGER.info("Seisme M{} ({}) en {} {}", Quake.magnitude(m), place, (int) x, (int) z);
        return quake;
    }

    static Eruption eruption(ServerLevel level, DisasterConfig.PlacedVolcano volcano, int warningTicks) {
        EarthData earth = EarthData.get();
        EarthData.Country country = earth == null || volcano.volcano().country().isEmpty() ? null
                : earth.byCode(volcano.volcano().country());
        Eruption eruption = new Eruption(nextId++, volcano, country == null ? "" : country.name(), warningTicks,
                RANDOM);
        mark("volcan:" + volcano.volcano().id());
        eruption.begin(level);
        add(eruption);
        DoHy.LOGGER.info("Eruption {} : alerte de {} s", volcano.volcano().name(), warningTicks / 20);
        return eruption;
    }

    static Hurricane hurricane(ServerLevel level, DisasterConfig.Basin basin, ServerPlayer target, int category,
                               int passSeconds) {
        EarthData earth = EarthData.get();
        int cat = category > 0 ? category : category();
        List<String> names = DisasterConfig.data().names();
        String stormName = names.isEmpty() ? "Alice" : names.get(nameIndex++ % names.size());
        Hurricane hurricane = Hurricane.create(nextId++, earth, basin, stormName, cat, target, passSeconds,
                1800.0d + (target == null ? 0.0d : passSeconds * 20.0d * Hurricane.SPEED), RANDOM);
        hurricane.begin(level, target, passSeconds);
        add(hurricane);
        DoHy.LOGGER.info("{} (categorie {}) dans le bassin {}{}", hurricane.name, cat, basin.name(),
                target == null ? "" : ", sur " + target.getGameProfile().name());
        return hurricane;
    }

    private static void add(Disaster disaster) {
        ACTIVE.add(disaster);
        LAST.put(disaster.kind, disaster);
    }

    /** La categorie d'une tempete : 1 trois fois sur dix, 5 une fois sur dix. */
    private static int category() {
        int roll = RANDOM.nextInt(100);
        return roll < 30 ? 1 : roll < 55 ? 2 : roll < 75 ? 3 : roll < 90 ? 4 : 5;
    }

    static Monsoon monsoon(ServerLevel level, DisasterConfig.PlacedMonsoon region, double x, double z) {
        Monsoon monsoon = new Monsoon(nextId++, region, x, z, RANDOM);
        monsoon.begin(level);
        add(monsoon);
        DoHy.LOGGER.info("Pluies de mousson ({}) en {} {}", region.monsoon().name(), (int) x, (int) z);
        return monsoon;
    }

    // -- Ce que les joueurs ressentent ------------------------------------------------------

    /** La meteo locale voulue pour ce joueur (la plus forte des tempetes ou il est), ou {@code null}. */
    static float[] weather(ServerPlayer player) {
        float[] best = null;
        for (Disaster disaster : ACTIVE) {
            float[] wanted = disaster.weather(player);
            if (wanted != null && (best == null || wanted[0] + wanted[1] > best[0] + best[1]
                    || disaster instanceof Hurricane)) {
                best = wanted;
                if (disaster instanceof Hurricane) {
                    break;
                }
            }
        }
        return best;
    }

    private static void feel(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            float shake = 0.0f;
            float ash = 0.0f;
            double windX = 0.0d;
            double windZ = 0.0d;
            for (Disaster disaster : ACTIVE) {
                shake = Math.max(shake, disaster.shake(player));
                ash = Math.max(ash, disaster.ash(player));
                if (disaster instanceof Hurricane hurricane) {
                    double[] wind = hurricane.playerWind(player);
                    if (wind != null) {
                        windX += wind[0];
                        windZ += wind[1];
                    }
                }
            }
            DisasterNet.Feel feel = new DisasterNet.Feel(quantize(shake), quantize(ash), (float) windX,
                    (float) windZ);
            DisasterNet.Feel before = FELT.get(player.getUUID());
            boolean calm = feel.shake() == 0.0f && feel.ash() == 0.0f && feel.windX() == 0.0f
                    && feel.windZ() == 0.0f;
            if (before == null && calm) {
                continue;
            }
            if (!feel.equals(before)) {
                PacketDistributor.sendToPlayer(player, feel);
                if (calm) {
                    FELT.remove(player.getUUID());
                } else {
                    FELT.put(player.getUUID(), feel);
                }
            }
        }
    }

    private static float quantize(float value) {
        return Math.round(value * 40.0f) / 40.0f;
    }

    private static DisasterNet.Sync sync() {
        List<DisasterNet.Entry> entries = new ArrayList<>();
        for (Disaster disaster : ACTIVE) {
            if (!disaster.done) {
                entries.add(disaster.entry());
            }
        }
        return new DisasterNet.Sync(entries);
    }

    private static void broadcast(ServerLevel level) {
        DisasterNet.Sync sync = sync();
        int hash = 1;
        for (DisasterNet.Entry entry : sync.entries()) {
            hash = 31 * hash + java.util.Objects.hash(entry.id(), entry.x() / 4, entry.z() / 4, entry.level(),
                    entry.phase(), entry.seconds() / 10, entry.radius());
        }
        if (hash == lastSync) {
            return;
        }
        lastSync = hash;
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, sync);
        }
    }

    // -- La lave et les cultures ------------------------------------------------------------

    /** Autour des volcans et des eruptions, la lave ne met le feu a rien. */
    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getNewState().getBlock() instanceof BaseFireBlock)) {
            return;
        }
        EarthData earth = EarthData.get();
        if (earth == null || !(event.getLevel() instanceof ServerLevel level) || !earth.covers(level)) {
            return;
        }
        BlockPos pos = event.getPos();
        DisasterConfig.PlacedVolcano volcano = DisasterConfig.nearestVolcano(pos.getX(), pos.getZ());
        if (volcano != null && volcano.distance(pos.getX(), pos.getZ()) <= Volcanoes.RADIUS + 48) {
            event.setNewState(event.getOriginalState());
            event.setCanceled(true);
        }
    }

    /** Pendant la saison des pluies, les cultures des regions de mousson poussent plus vite (dehors). */
    @SubscribeEvent
    public static void onCropGrow(CropGrowEvent.Post event) {
        EarthData earth = EarthData.get();
        if (earth == null || !(event.getLevel() instanceof ServerLevel level) || !earth.covers(level)
                || !SeasonClimate.SERVER.active()) {
            return;
        }
        BlockPos pos = event.getPos();
        DisasterConfig.PlacedMonsoon monsoon = DisasterConfig.monsoonAt(pos.getX(), pos.getZ());
        if (monsoon == null || !wet(monsoon, SeasonClimate.SERVER.phase()) || !level.canSeeSky(pos)
                || level.getRandom().nextFloat() >= 0.35f) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
            level.setBlock(pos, crop.getStateForAge(crop.getAge(state) + 1), net.minecraft.world.level.block.Block
                    .UPDATE_CLIENTS);
        }
    }

    // -- Les mots ------------------------------------------------------------------------------

    /** « au nord-est », « a l'est » : du point vers l'autre ; le nord vers les z negatifs. */
    static String direction(double fromX, double fromZ, double toX, double toZ) {
        double angle = Math.toDegrees(Math.atan2(toX - fromX, -(toZ - fromZ)));
        String[] names = {"au nord", "au nord-est", "a l'est", "au sud-est", "au sud", "au sud-ouest", "a l'ouest",
                "au nord-ouest"};
        return names[Math.floorMod((int) Math.round(angle / 45.0d), 8)];
    }

    /** Le cap d'une route : « nord-ouest », « ouest-nord-ouest »... */
    static String heading(double fromX, double fromZ, double toX, double toZ) {
        double angle = Math.toDegrees(Math.atan2(toX - fromX, -(toZ - fromZ)));
        String[] names = {"nord", "nord-nord-est", "nord-est", "est-nord-est", "est", "est-sud-est", "sud-est",
                "sud-sud-est", "sud", "sud-sud-ouest", "sud-ouest", "ouest-sud-ouest", "ouest", "ouest-nord-ouest",
                "nord-ouest", "nord-nord-ouest"};
        return names[Math.floorMod((int) Math.round(angle / 22.5d), 16)];
    }

    /** « 2 300 ». */
    static String blocks(int count) {
        return MoneyFormat.exact(count);
    }

    /** « 5 min », « 1 min 30 s », « 45 s ». */
    static String duration(int seconds) {
        if (seconds < 60) {
            return seconds + " s";
        }
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return rest == 0 || minutes >= 10 ? minutes + " min" : minutes + " min " + rest + " s";
    }

    /** Le delai du retour du terrain : « 3 h ». */
    static String hours() {
        double hours = DisasterConfig.settings().restoreHours();
        if (hours < 1.0d) {
            return Math.max(1, (int) Math.round(hours * 60.0d)) + " min";
        }
        return (hours == Math.floor(hours) ? String.valueOf((int) hours)
                : String.format(Locale.ROOT, "%.1f", hours).replace('.', ',')) + " h";
    }

    // -- La commande ---------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(command("catastrophes"));
        event.getDispatcher().register(command("catastrophe"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return Commands.literal(name)
                .executes(context -> list(context.getSource().getPlayerOrException()))
                .then(Commands.literal("volcans").executes(context -> volcanoes(context.getSource()
                        .getPlayerOrException())))
                .then(Commands.literal("admin")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("seisme")
                                .executes(context -> adminQuake(context, Double.NaN, Double.NaN))
                                .then(Commands.argument("magnitude", DoubleArgumentType.doubleArg(4.0d, 9.5d))
                                        .executes(context -> adminQuake(context,
                                                DoubleArgumentType.getDouble(context, "magnitude"), Double.NaN))
                                        .then(Commands.argument("distance", DoubleArgumentType.doubleArg(4.0d, 200.0d))
                                                .executes(context -> adminQuake(context,
                                                        DoubleArgumentType.getDouble(context, "magnitude"),
                                                        DoubleArgumentType.getDouble(context, "distance"))))))
                        .then(Commands.literal("eruption")
                                .executes(context -> adminEruption(context, null, -1))
                                .then(Commands.argument("volcan", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                DisasterConfig.data().volcanoes().stream()
                                                        .map(DisasterConfig.Volcano::id), builder))
                                        .executes(context -> adminEruption(context,
                                                StringArgumentType.getString(context, "volcan"), -1))
                                        .then(Commands.argument("alerte", IntegerArgumentType.integer(0, 3600))
                                                .executes(context -> adminEruption(context,
                                                        StringArgumentType.getString(context, "volcan"),
                                                        IntegerArgumentType.getInteger(context, "alerte"))))))
                        .then(Commands.literal("ouragan")
                                .executes(context -> adminHurricane(context, 0, 60))
                                .then(Commands.argument("categorie", IntegerArgumentType.integer(1, 5))
                                        .executes(context -> adminHurricane(context,
                                                IntegerArgumentType.getInteger(context, "categorie"), 60))
                                        .then(Commands.argument("secondes", IntegerArgumentType.integer(5, 1800))
                                                .executes(context -> adminHurricane(context,
                                                        IntegerArgumentType.getInteger(context, "categorie"),
                                                        IntegerArgumentType.getInteger(context, "secondes"))))))
                        .then(Commands.literal("mousson").executes(Disasters::adminMonsoon))
                        .then(Commands.literal("fin").executes(context -> {
                            int count = 0;
                            for (Disaster disaster : new ArrayList<>(ACTIVE)) {
                                disaster.finish(context.getSource().getServer());
                                count++;
                            }
                            ACTIVE.clear();
                            int ended = count;
                            context.getSource().sendSuccess(() -> Component.literal(ended
                                    + " catastrophe(s) arretee(s)."), true);
                            return count;
                        }))
                        .then(Commands.literal("retour").executes(context -> {
                            int count = DisasterTerrain.hurry();
                            context.getSource().sendSuccess(() -> Component.literal(count
                                    + " terrain(s) reviennent maintenant."), true);
                            return count;
                        })));
    }

    private static ServerLevel earthLevel(ServerPlayer player) {
        EarthData earth = EarthData.get();
        if (earth == null || !earth.covers(player.level()) || !(player.level() instanceof ServerLevel level)) {
            player.sendSystemMessage(Component.literal("Les catastrophes n'existent que sur la carte de la Terre.")
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        return level;
    }

    private static int list(ServerPlayer player) {
        if (earthLevel(player) == null) {
            return 0;
        }
        if (ACTIVE.isEmpty()) {
            player.sendSystemMessage(prefix().append(Component.literal("Aucune catastrophe en cours.")
                    .withStyle(ChatFormatting.GRAY)));
        }
        for (Disaster disaster : ACTIVE) {
            DisasterNet.Entry entry = disaster.entry();
            String state = switch (entry.kind()) {
                case DisasterNet.ERUPTION -> entry.phase() == 0 ? "eruption dans " + duration(entry.seconds())
                        : entry.phase() == 1 ? "en eruption" : "la lave refroidit";
                case DisasterNet.HURRICANE -> "categorie " + entry.level();
                default -> "encore " + duration(entry.seconds());
            };
            double distance = Math.hypot(disaster.x - player.getX(), disaster.z - player.getZ());
            player.sendSystemMessage(prefix().append(Component.literal(disaster.name + " - " + disaster.place + " ("
                    + state + ") : " + blocks((int) distance) + " blocs " + direction(player.getX(), player.getZ(),
                    disaster.x, disaster.z) + ".").withStyle(distance <= disaster.radius() ? ChatFormatting.RED
                    : ChatFormatting.GOLD)));
        }
        Disaster quake = LAST.get(DisasterKind.QUAKE);
        if (quake != null && quake.done) {
            long ago = (System.currentTimeMillis() - quake.started) / 1000L;
            player.sendSystemMessage(prefix().append(Component.literal("Dernier seisme : " + quake.name + " ("
                    + quake.place + "), il y a " + duration((int) ago) + ".").withStyle(ChatFormatting.GRAY)));
        }
        int pending = DisasterTerrain.pending();
        if (pending > 0) {
            long next = DisasterTerrain.nextDue() - System.currentTimeMillis();
            player.sendSystemMessage(prefix().append(Component.literal(pending + " terrain(s) abime(s) en attente de "
                    + "retour" + (next > 0 ? ", le prochain dans " + duration((int) (next / 1000L)) : "") + ".")
                    .withStyle(ChatFormatting.GRAY)));
        }
        return 1;
    }

    private static int volcanoes(ServerPlayer player) {
        if (earthLevel(player) == null) {
            return 0;
        }
        List<DisasterConfig.PlacedVolcano> sorted = new ArrayList<>(DisasterConfig.placed().volcanoes());
        sorted.sort(java.util.Comparator.comparingDouble(v -> v.distance(player.getX(), player.getZ())));
        for (int i = 0; i < Math.min(6, sorted.size()); i++) {
            DisasterConfig.PlacedVolcano volcano = sorted.get(i);
            double distance = volcano.distance(player.getX(), player.getZ());
            player.sendSystemMessage(prefix().append(Component.literal(volcano.volcano().name() + " : "
                    + blocks((int) distance) + " blocs " + direction(player.getX(), player.getZ(), volcano.x(),
                    volcano.z()) + (eruptionAt(volcano) != null ? " (en activite)" : "") + ".")
                    .withStyle(ChatFormatting.GOLD)));
        }
        return 1;
    }

    private static int adminQuake(CommandContext<CommandSourceStack> context, double magnitude, double distance)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = earthLevel(player);
        if (level == null) {
            return 0;
        }
        DisasterConfig.PlacedZone zone = DisasterConfig.zoneAt(player.getX(), player.getZ());
        Quake quake = quake(level, zone, player, Double.isNaN(magnitude) ? 6.5d : magnitude, distance);
        context.getSource().sendSuccess(() -> Component.literal(quake.name + " declenche a "
                + (int) quake.distance(player) + " blocs."), true);
        return 1;
    }

    private static int adminEruption(CommandContext<CommandSourceStack> context, String id, int warning)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = earthLevel(player);
        if (level == null) {
            return 0;
        }
        DisasterConfig.PlacedVolcano volcano = id == null ? DisasterConfig.nearestVolcano(player.getX(),
                player.getZ()) : DisasterConfig.volcano(id);
        if (volcano == null) {
            context.getSource().sendFailure(Component.literal("Volcan inconnu."));
            return 0;
        }
        Eruption running = eruptionAt(volcano);
        if (running != null) {
            context.getSource().sendFailure(Component.literal(running.name + " est deja en cours."));
            return 0;
        }
        int ticks = (warning < 0 ? DisasterConfig.settings().eruptionWarningSeconds() : warning) * 20;
        Eruption eruption = eruption(level, volcano, ticks);
        context.getSource().sendSuccess(() -> Component.literal(eruption.name + " declenchee ("
                + blocks((int) eruption.distance(player)) + " blocs)."), true);
        return 1;
    }

    private static int adminHurricane(CommandContext<CommandSourceStack> context, int category, int seconds)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = earthLevel(player);
        if (level == null) {
            return 0;
        }
        EarthData earth = EarthData.get();
        double latitude = earth.latitude(player.getZ());
        double longitude = earth.longitude(player.getX());
        DisasterConfig.Basin basin = null;
        for (DisasterConfig.Basin candidate : DisasterConfig.data().basins()) {
            if (candidate.covers(latitude, longitude)) {
                basin = candidate;
                break;
            }
        }
        if (basin == null && !DisasterConfig.data().basins().isEmpty()) {
            // hors des bassins (un essai) : celui de l'hemisphere du joueur
            for (DisasterConfig.Basin candidate : DisasterConfig.data().basins()) {
                if (candidate.southern() == latitude < 0.0d) {
                    basin = candidate;
                    break;
                }
            }
        }
        if (basin == null) {
            context.getSource().sendFailure(Component.literal("Aucun bassin cyclonique dans les donnees."));
            return 0;
        }
        Hurricane hurricane = hurricane(level, basin, player, category, seconds);
        context.getSource().sendSuccess(() -> Component.literal(hurricane.name + " (categorie " + hurricane.category
                + ") passera sur toi dans " + duration(seconds) + "."), true);
        return 1;
    }

    private static int adminMonsoon(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = earthLevel(player);
        if (level == null) {
            return 0;
        }
        DisasterConfig.PlacedMonsoon region = DisasterConfig.monsoonAt(player.getX(), player.getZ());
        if (region == null) {
            List<DisasterConfig.PlacedMonsoon> regions = DisasterConfig.placed().monsoons();
            if (regions.isEmpty()) {
                context.getSource().sendFailure(Component.literal("Aucune region de mousson dans les donnees."));
                return 0;
            }
            region = regions.get(0);
        }
        Monsoon monsoon = monsoon(level, region, player.getX(), player.getZ());
        context.getSource().sendSuccess(() -> Component.literal(monsoon.name + " declenchees ici."), true);
        return 1;
    }
}
