package fr.atraxterra.dohy.nuclear;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.earth.EarthSites;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.faction.base.NtmDefenses;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Les largages nucleaires : des caisses de matieres de NTM NEXT parachutees
 * dans les zones irradiees de la carte (Tchernobyl, Fukushima).
 *
 * <p><b>Le rythme.</b> Un largage toutes les deux heures environ, quand
 * quelqu'un est en ligne. Il est annonce a tout le serveur cinq minutes avant,
 * avec ses coordonnees (et une boussole d'un clic) : le premier arrive se
 * sert. La caisse descend en parachute, se pose, et disparait une heure plus
 * tard si personne ne l'a videe.
 *
 * <p><b>Le lieu.</b> Un point au hasard d'une zone irradiee, sur la terre
 * ferme, hors des claims ; la radiation est celle de la zone (NTM NEXT), une
 * combinaison est conseillee. Sans carte de la Terre, la caisse tombe a 300 -
 * 1 200 blocs du centre du Monde, loin des claims des joueurs en ligne, et
 * irradie l'endroit ou elle se pose.
 *
 * <p><b>Le contenu.</b> De la matiere brute surtout (uranium, thorium,
 * plutonium et lithium bruts, yellowcake, beryllium), un peu de raffine
 * (lingots, pepites d'uranium 235 et de plutonium 239, radium, polonium,
 * gemmes radioactives), parfois du schrabidium, et de quoi se soigner
 * (RadAway, Rad-X, iode). Sans NTM NEXT, aucun largage.
 *
 * <p>Le rythme et les caisses au sol sont gardes dans
 * {@code <monde>/dohy/largages.json} : un redemarrage ne les perd pas.
 */
@EventBusSubscriber
public final class NuclearDrops {

    static final long INTERVAL_MS = 2L * 3_600_000L;
    private static final long JITTER_MS = 15L * 60_000L;
    static final long WARNING_MS = 5L * 60_000L;
    static final long LIFETIME_MS = 3_600_000L;
    private static final long RETRY_MS = 10L * 60_000L;
    /** La caisse part de si haut au-dessus du sol, et descend de tant par tick. */
    private static final int FALL_HEIGHT = 48;
    private static final double DESCENT = 0.30d;
    private static final int LOAD_TIMEOUT_TICKS = 60 * 20;
    private static final String TAG = "dohy_largage";
    private static final String NAME = "Largage nucleaire";

    private NuclearDrops() {
    }

    /** Un largage annonce, pas encore pose. */
    static final class Drop {
        /** L'identifiant de la zone, vide hors zone (sans carte de la Terre). */
        String site = "";
        /** Son nom dans le chat : « Tchernobyl », vide hors zone. */
        String place = "";
        int x;
        int z;
        /** L'heure de l'arrivee (ms). */
        long land;

        // la chute, une fois commencee
        boolean forced;
        int waited;
        UUID entity;
        BlockPos last;

        /** « Tchernobyl (x 2061, z -4012) » ou « x 523, z -811 ». */
        String where() {
            return (place.isEmpty() ? "" : place + " ") + "(x " + x + ", z " + z + ")";
        }
    }

    /** Une caisse au sol. */
    static final class Crate {
        BlockPos pos = BlockPos.ZERO;
        String place = "";
        long expires;
        boolean opened;

        String of() {
            return place.isEmpty() ? "le largage" : "le largage de " + place;
        }
    }

    /** Une matiere de la caisse : son identifiant chez NTM, son poids, sa quantite. */
    private record Loot(String id, int weight, int min, int max) {
    }

    /** Surtout du brut, un peu de raffine, rarement du schrabidium, et de quoi se soigner. */
    private static final List<Loot> LOOT = List.of(
            new Loot("raw_uranium", 140, 4, 10),
            new Loot("raw_thorium", 100, 3, 8),
            new Loot("powder_yellowcake", 80, 2, 6),
            new Loot("lithium", 70, 2, 5),
            new Loot("raw_plutonium", 30, 1, 3),
            new Loot("ingot_beryllium", 60, 1, 4),
            new Loot("ingot_lead", 60, 4, 10),
            new Loot("ingot_graphite", 60, 4, 8),
            new Loot("ingot_uranium", 80, 2, 5),
            new Loot("ingot_th232", 60, 1, 4),
            new Loot("ingot_u238", 50, 1, 4),
            new Loot("nugget_u235", 45, 1, 4),
            new Loot("ingot_zirconium", 40, 1, 3),
            new Loot("nugget_plutonium", 35, 1, 3),
            new Loot("nugget_pu239", 18, 1, 2),
            new Loot("nugget_ra226", 18, 1, 2),
            new Loot("nugget_polonium", 15, 1, 2),
            new Loot("gem_rad", 25, 1, 2),
            new Loot("nugget_schrabidium", 8, 1, 2),
            new Loot("ingot_schrabidium", 2, 1, 1),
            new Loot("radaway", 30, 1, 2),
            new Loot("radx", 30, 1, 2),
            new Loot("pill_iodine", 20, 1, 3));

    private static long next;
    private static Drop pending;
    private static final List<Crate> CRATES = new ArrayList<>();
    private static String lastSite = "";
    private static boolean loaded;
    private static final RandomSource RANDOM = RandomSource.create();

    // -- Lire l'etat -------------------------------------------------------------------------------

    /** Le monde des largages : celui de la carte de la Terre, sinon le Monde. */
    static ServerLevel level(MinecraftServer server) {
        EarthData earth = EarthData.get();
        ServerLevel level = earth == null ? null : server.getLevel(earth.dimension());
        return level == null ? server.overworld() : level;
    }

    /** L'heure de la prochaine annonce (ms) ; 0 avant le premier demarrage. */
    static long next() {
        return next;
    }

    static Drop pending() {
        return pending;
    }

    static List<Crate> crates() {
        return Collections.unmodifiableList(CRATES);
    }

    /** « dans 1 h 12 », « dans 4 min », « maintenant ». */
    static String in(long millis) {
        if (millis <= 0L) {
            return "maintenant";
        }
        long minutes = Math.max(1L, (millis + 59_999L) / 60_000L);
        if (minutes < 60L) {
            return "dans " + minutes + " min";
        }
        long rest = minutes % 60L;
        return "dans " + minutes / 60L + " h" + (rest == 0L ? "" : " " + (rest < 10L ? "0" : "") + rest);
    }

    static MutableComponent prefix() {
        return Component.literal("[Largage] ").withStyle(ChatFormatting.GREEN);
    }

    private static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(prefix().append(message), false);
    }

    private static Component compassButton() {
        return Component.literal(" [Boussole]").withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)
                .withClickEvent(new ClickEvent.RunCommand("/largage boussole")));
    }

    // -- Le tick -----------------------------------------------------------------------------------

    /** Avant le tick des mondes : la caisse en vol descend lentement, sous son parachute. */
    @SubscribeEvent
    public static void onTickPre(ServerTickEvent.Pre event) {
        Drop drop = pending;
        if (drop == null || drop.entity == null) {
            return;
        }
        Entity entity = level(event.getServer()).getEntity(drop.entity);
        if (entity instanceof FallingBlockEntity crate && !crate.isRemoved()) {
            crate.setDeltaMovement(0.0d, -DESCENT, 0.0d);
            // une chute de plus de 30 secondes la ferait jeter : elle n'est que lente
            crate.time = Math.min(crate.time, 100);
        }
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!loaded) {
            return;
        }
        int tick = server.getTickCount();
        if (pending != null && (pending.forced || System.currentTimeMillis() >= pending.land)) {
            fall(server, pending);
        }
        if (tick % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (pending == null && now >= next && NtmDefenses.present()) {
            if (server.getPlayerList().getPlayerCount() == 0) {
                next = now + RETRY_MS;
            } else {
                announce(server, "", WARNING_MS);
            }
        }
        crates(server, tick, now);
    }

    // -- L'annonce ---------------------------------------------------------------------------------

    /**
     * Annonce un largage dans {@code warning} ms ; {@code site} force la zone
     * (vide : au hasard). Rend le refus, ou {@code null}.
     */
    static String announce(MinecraftServer server, String site, long warning) {
        if (pending != null) {
            return "Un largage est deja annonce";
        }
        if (!NtmDefenses.present()) {
            return "NTM NEXT n'est pas installe : pas de largage";
        }
        ServerLevel level = level(server);
        long now = System.currentTimeMillis();
        Drop drop = choose(server, level, site);
        if (drop == null) {
            next = now + RETRY_MS;
            DoHy.LOGGER.warn("Largage : aucun point de chute trouve, nouvel essai {}", in(RETRY_MS));
            return "Aucun point de chute libre (zones claimees, ou sous l'eau)";
        }
        drop.land = now + warning;
        pending = drop;
        next = drop.land + INTERVAL_MS - JITTER_MS + (long) (RANDOM.nextDouble() * 2.0d * JITTER_MS);
        if (!drop.site.isEmpty()) {
            lastSite = drop.site;
        }
        save(server);
        MutableComponent text = Component.literal("Largage nucleaire " + in(warning) + " : " + drop.where()
                + (drop.site.isEmpty() ? "" : ", zone irradiee : combinaison conseillee") + ".")
                .withStyle(ChatFormatting.YELLOW);
        broadcast(server, text.append(compassButton()));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS,
                    0.7f, 1.3f);
        }
        DoHy.LOGGER.info("Largage annonce : {} {}", drop.where(), in(warning));
        return null;
    }

    /** Un point de chute : une zone irradiee (la meme rarement deux fois de suite), sinon les abords du Monde. */
    private static Drop choose(MinecraftServer server, ServerLevel level, String forced) {
        List<EarthSites.Placed> zones = new ArrayList<>();
        for (EarthSites.Placed placed : EarthSites.placed()) {
            if (placed.site().irradiated() && (forced.isEmpty() || placed.site().id().equals(forced))) {
                zones.add(placed);
            }
        }
        if (!forced.isEmpty() && zones.isEmpty()) {
            return null;
        }
        Collections.shuffle(zones, new java.util.Random(RANDOM.nextLong()));
        if (zones.size() > 1 && zones.getFirst().site().id().equals(lastSite)) {
            zones.add(zones.removeFirst());
        }
        for (EarthSites.Placed zone : zones) {
            for (int attempt = 0; attempt < 24; attempt++) {
                double angle = RANDOM.nextDouble() * Math.PI * 2.0d;
                double distance = Math.sqrt(RANDOM.nextDouble()) * zone.site().radius() * 0.75d;
                int x = (int) Math.round(zone.x() + Math.cos(angle) * distance);
                int z = (int) Math.round(zone.z() + Math.sin(angle) * distance);
                if (dry(level, x, z)) {
                    Drop drop = new Drop();
                    drop.site = zone.site().id();
                    drop.place = zone.site().name();
                    drop.x = x;
                    drop.z = z;
                    return drop;
                }
            }
        }
        return zones.isEmpty() && forced.isEmpty() ? wild(server, level) : null;
    }

    /** Un point de chute dans une zone : terre ferme, a ciel ouvert, hors des claims. */
    private static boolean dry(ServerLevel level, int x, int z) {
        LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
        if (chunk.getData(Factions.CLAIM.get()).claimed()) {
            return false;
        }
        int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
        if (chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) != top || top <= level.getMinY()) {
            return false;
        }
        BlockState ground = chunk.getBlockState(new BlockPos(x, top, z));
        return ground.getFluidState().isEmpty() && !ground.is(BlockTags.LEAVES) && top >= level.getSeaLevel() - 1;
    }

    /**
     * Sans carte de la Terre : un point a 300 - 1 200 blocs du centre du Monde,
     * sur la terre ferme (la hauteur du generateur est sondee sans rien
     * generer), loin des claims des joueurs en ligne.
     */
    private static Drop wild(MinecraftServer server, ServerLevel level) {
        List<double[]> claims = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (FactionApi.has(player)) {
                for (FactionData.Claim claim : FactionApi.data(player).claims()) {
                    claims.add(new double[] {(claim.x() << 4) + 8.0d, (claim.z() << 4) + 8.0d});
                }
            }
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState noise = level.getChunkSource().randomState();
        int sea = level.getSeaLevel();
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0d;
            double distance = 300.0d + RANDOM.nextDouble() * 900.0d;
            int x = Mth.floor(Math.cos(angle) * distance);
            int z = Mth.floor(Math.sin(angle) * distance);
            boolean close = false;
            for (double[] claim : claims) {
                close |= Math.hypot(x - claim[0], z - claim[1]) < 96.0d;
            }
            Holder<Biome> biome = level.getBiome(new BlockPos(x, sea, z));
            if (close || biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)
                    || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) {
                continue;
            }
            if (generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, noise) > sea) {
                Drop drop = new Drop();
                drop.x = x;
                drop.z = z;
                return drop;
            }
        }
        return null;
    }

    // -- La chute ----------------------------------------------------------------------------------

    /** La caisse part, descend, se pose ; le chunk du point de chute est charge pendant ce temps. */
    private static void fall(MinecraftServer server, Drop drop) {
        ServerLevel level = level(server);
        ChunkPos chunkPos = new ChunkPos(drop.x >> 4, drop.z >> 4);
        if (!drop.forced) {
            // rayon 2 : le chunk du point de chute fait tourner ses entites, la caisse avec
            level.getChunkSource().addTicketWithRadius(TicketType.FORCED, chunkPos, 2);
            drop.forced = true;
        }
        if (drop.entity == null) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
            if (chunk == null && ++drop.waited < LOAD_TIMEOUT_TICKS) {
                return;
            }
            if (chunk == null) {
                chunk = level.getChunk(chunkPos.x(), chunkPos.z());
            }
            int ground = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, drop.x & 15, drop.z & 15) + 1;
            int start = Math.min(level.getMaxY() - 4, ground + FALL_HEIGHT);
            BlockState barrel = Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
            FallingBlockEntity crate = FallingBlockEntity.fall(level, new BlockPos(drop.x, start, drop.z), barrel);
            crate.dropItem = false;
            crate.addTag(TAG);
            drop.entity = crate.getUUID();
            drop.last = crate.blockPosition();
            broadcast(server, Component.literal("Le largage descend sur " + (drop.place.isEmpty() ? "x " + drop.x
                    + ", z " + drop.z : drop.place) + " !").withStyle(ChatFormatting.YELLOW));
            level.playSound(null, drop.x + 0.5d, ground, drop.z + 0.5d, SoundEvents.FIREWORK_ROCKET_LAUNCH,
                    SoundSource.AMBIENT, 6.0f, 0.6f);
            DoHy.LOGGER.info("Largage : la caisse part de {} {} {}", drop.x, start, drop.z);
            return;
        }
        Entity entity = level.getEntity(drop.entity);
        if (entity != null && !entity.isRemoved()) {
            drop.last = entity.blockPosition();
            // le parachute : un panache blanc au-dessus de la caisse
            level.sendParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 1.4d, entity.getZ(), 3, 0.45d,
                    0.15d, 0.45d, 0.0d);
            return;
        }
        land(server, level, drop);
    }

    /** La caisse touche le sol : remplie, nommee, annoncee. */
    private static void land(MinecraftServer server, ServerLevel level, Drop drop) {
        BlockPos pos = null;
        BlockPos last = drop.last == null ? new BlockPos(drop.x, level.getMinY(), drop.z) : drop.last;
        for (int dy = 1; dy >= -2 && pos == null; dy--) {
            if (level.getBlockEntity(last.above(dy)) instanceof BarrelBlockEntity) {
                pos = last.above(dy);
            }
        }
        if (pos == null) {
            // posee la ou elle devait tomber, si la chute a mal fini
            LevelChunk chunk = level.getChunk(drop.x >> 4, drop.z >> 4);
            int ground = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, drop.x & 15, drop.z & 15) + 1;
            pos = new BlockPos(drop.x, ground, drop.z);
            level.setBlock(pos, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP),
                    3);
        }
        int stacks = fill(level, pos);
        if (drop.forced) {
            level.getChunkSource().removeTicketWithRadius(TicketType.FORCED, new ChunkPos(drop.x >> 4, drop.z >> 4), 2);
            drop.forced = false;
        }
        Crate crate = new Crate();
        crate.pos = pos;
        crate.place = drop.place;
        crate.expires = System.currentTimeMillis() + LIFETIME_MS;
        CRATES.add(crate);
        pending = null;
        if (drop.site.isEmpty()) {
            irradiate(level, pos);
        }
        firework(level, pos);
        save(server);
        broadcast(server, Component.literal("Largage pose " + (drop.place.isEmpty() ? "" : "a " + drop.place + " ")
                + ": " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ". Il disparait " + in(LIFETIME_MS) + ".")
                .withStyle(ChatFormatting.YELLOW).append(compassButton()));
        DoHy.LOGGER.info("Largage pose en {} ({} piles)", pos.toShortString(), stacks);
    }

    /** Remplit la caisse ; rend le nombre de piles posees. */
    private static int fill(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)) {
            return 0;
        }
        ItemStack named = new ItemStack(Items.BARREL);
        named.set(DataComponents.CUSTOM_NAME, Component.literal(NAME).withStyle(ChatFormatting.DARK_GREEN));
        barrel.applyComponentsFromItemStack(named);
        List<Integer> free = new ArrayList<>();
        for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
            free.add(slot);
        }
        Collections.shuffle(free, new java.util.Random(RANDOM.nextLong()));
        int total = 0;
        for (Loot loot : LOOT) {
            if (item(loot) != null) {
                total += loot.weight();
            }
        }
        int placed = 0;
        int rolls = 8 + RANDOM.nextInt(4);
        for (int roll = 0; roll < rolls && total > 0 && !free.isEmpty(); roll++) {
            int pick = RANDOM.nextInt(total);
            for (Loot loot : LOOT) {
                Item item = item(loot);
                if (item == null) {
                    continue;
                }
                pick -= loot.weight();
                if (pick < 0) {
                    int count = loot.min() + RANDOM.nextInt(loot.max() - loot.min() + 1);
                    barrel.setItem(free.removeLast(),
                            new ItemStack(item, Math.min(count, item.getDefaultMaxStackSize())));
                    placed++;
                    break;
                }
            }
        }
        barrel.setChanged();
        return placed;
    }

    private static Item item(Loot loot) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("hbm", loot.id())).orElse(null);
    }

    /** Hors zone, la caisse irradie l'endroit ou elle se pose (la radiation de NTM NEXT, qui retombe d'elle-meme). */
    private static void irradiate(ServerLevel level, BlockPos pos) {
        try {
            Class<?> system = Class.forName("com.hbm.handler.radiation.RadiationSystemNT");
            Method increment = system.getMethod("incrementRad", ServerLevel.class, BlockPos.class, double.class);
            increment.invoke(null, level, pos, 40.0d);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            DoHy.LOGGER.warn("Largage : radiation de NTM NEXT indisponible ({})", error.toString());
        }
    }

    /** Une fusee verte et jaune au-dessus de la caisse : on la voit de loin. */
    private static void firework(ServerLevel level, BlockPos pos) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(2, List.of(new FireworkExplosion(
                FireworkExplosion.Shape.LARGE_BALL, IntList.of(0x7CFC00, 0xFFE135), IntList.of(0x2E8B57), true,
                true))));
        level.addFreshEntity(new FireworkRocketEntity(level, pos.getX() + 0.5d, pos.getY() + 1.2d, pos.getZ() + 0.5d,
                rocket));
    }

    // -- Les caisses au sol ------------------------------------------------------------------------

    private static void crates(MinecraftServer server, int tick, long now) {
        if (CRATES.isEmpty()) {
            return;
        }
        ServerLevel level = level(server);
        boolean changed = false;
        for (Crate crate : List.copyOf(CRATES)) {
            if (now >= crate.expires) {
                remove(level, crate, true);
                CRATES.remove(crate);
                changed = true;
                String what = Character.toUpperCase(crate.of().charAt(0)) + crate.of().substring(1);
                broadcast(server, Component.literal(what + " s'est desintegre.").withStyle(ChatFormatting.GRAY));
                continue;
            }
            if (level.getChunkSource().getChunkNow(crate.pos.getX() >> 4, crate.pos.getZ() >> 4) == null) {
                continue;
            }
            BlockEntity entity = level.getBlockEntity(crate.pos);
            if (!(entity instanceof BarrelBlockEntity barrel)) {
                // cassee : son contenu est tombe au sol
                CRATES.remove(crate);
                changed = true;
                continue;
            }
            if (crate.opened && barrel.isEmpty()) {
                remove(level, crate, false);
                CRATES.remove(crate);
                changed = true;
                continue;
            }
            level.sendParticles(ParticleTypes.END_ROD, crate.pos.getX() + 0.5d, crate.pos.getY() + 1.5d,
                    crate.pos.getZ() + 0.5d, 4, 0.1d, 1.5d, 0.1d, 0.02d);
            if (tick % 600 == 0 && !crate.opened && nearby(level, crate.pos, 160.0d)) {
                firework(level, crate.pos);
            }
        }
        if (changed) {
            save(server);
        }
    }

    private static boolean nearby(ServerLevel level, BlockPos pos, double range) {
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().closerThan(pos, range)) {
                return true;
            }
        }
        return false;
    }

    /** Retire la caisse ; son contenu disparait avec elle (rien ne tombe). */
    private static void remove(ServerLevel level, Crate crate, boolean load) {
        if (!load && level.getChunkSource().getChunkNow(crate.pos.getX() >> 4, crate.pos.getZ() >> 4) == null) {
            return;
        }
        level.getChunk(crate.pos.getX() >> 4, crate.pos.getZ() >> 4);
        if (level.getBlockEntity(crate.pos) instanceof BarrelBlockEntity barrel) {
            barrel.clearContent();
            level.setBlock(crate.pos, Blocks.AIR.defaultBlockState(), 3);
            level.sendParticles(ParticleTypes.POOF, crate.pos.getX() + 0.5d, crate.pos.getY() + 0.5d,
                    crate.pos.getZ() + 0.5d, 12, 0.3d, 0.3d, 0.3d, 0.02d);
        }
    }

    /** Le premier qui ouvre la caisse : tout le serveur le sait. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onOpen(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || CRATES.isEmpty() || !(event.getEntity() instanceof ServerPlayer player)
                || player.level() != level(player.level().getServer())) {
            return;
        }
        for (Crate crate : CRATES) {
            if (!crate.opened && crate.pos.equals(event.getPos())) {
                crate.opened = true;
                MinecraftServer server = player.level().getServer();
                save(server);
                String who = player.getGameProfile().name()
                        + (FactionApi.has(player) ? " (" + FactionApi.data(player).name() + ")" : "");
                broadcast(server, Component.literal(who + " ouvre " + crate.of() + " !")
                        .withStyle(ChatFormatting.GOLD));
                DoHy.LOGGER.info("Largage : {} ouvert par {}", crate.pos.toShortString(), who);
                return;
            }
        }
    }

    // -- La boussole et l'administration ---------------------------------------------------------

    /** Une boussole vers la caisse au sol la plus recente, ou le point de chute annonce ; le refus, ou {@code null}. */
    static String compass(ServerPlayer player) {
        BlockPos target = null;
        if (!CRATES.isEmpty()) {
            target = CRATES.getLast().pos;
        } else if (pending != null) {
            target = new BlockPos(pending.x, player.level().getSeaLevel(), pending.z);
        }
        if (target == null) {
            return "Aucun largage en cours. Le prochain : " + in(next - System.currentTimeMillis());
        }
        ServerLevel level = level(player.level().getServer());
        ItemStack compass = new ItemStack(Items.COMPASS);
        compass.set(DataComponents.LODESTONE_TRACKER,
                new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), target)), false));
        compass.set(DataComponents.CUSTOM_NAME, Component.literal(NAME).withStyle(ChatFormatting.GREEN));
        if (!player.getInventory().add(compass)) {
            player.drop(compass, false);
        }
        return null;
    }

    /** Toutes les caisses au sol disparaissent maintenant (essais). */
    static int expireAll(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (Crate crate : CRATES) {
            crate.expires = now;
        }
        return CRATES.size();
    }

    // -- Le disque ---------------------------------------------------------------------------------

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("dohy").resolve("largages.json");
    }

    private static JsonArray pos(BlockPos pos) {
        JsonArray out = new JsonArray();
        out.add(pos.getX());
        out.add(pos.getY());
        out.add(pos.getZ());
        return out;
    }

    private static void save(MinecraftServer server) {
        if (!loaded) {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("next", next);
        root.addProperty("last_site", lastSite);
        if (pending != null) {
            JsonObject drop = new JsonObject();
            drop.addProperty("site", pending.site);
            drop.addProperty("place", pending.place);
            drop.addProperty("x", pending.x);
            drop.addProperty("z", pending.z);
            drop.addProperty("land", pending.land);
            root.add("pending", drop);
        }
        JsonArray crates = new JsonArray();
        for (Crate crate : CRATES) {
            JsonObject entry = new JsonObject();
            entry.add("pos", pos(crate.pos));
            entry.addProperty("place", crate.place);
            entry.addProperty("expires", crate.expires);
            entry.addProperty("opened", crate.opened);
            crates.add(entry);
        }
        root.add("crates", crates);
        Path file = file(server);
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling("largages.json.tmp");
            Files.writeString(temporary, root.toString());
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            DoHy.LOGGER.warn("Largages non ecrits", error);
        }
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        pending = null;
        CRATES.clear();
        lastSite = "";
        next = System.currentTimeMillis() + INTERVAL_MS / 2L;
        loaded = true;
        Path file = file(event.getServer());
        if (!Files.exists(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            next = root.get("next").getAsLong();
            lastSite = root.has("last_site") ? root.get("last_site").getAsString() : "";
            if (root.has("pending") && root.get("pending").isJsonObject()) {
                JsonObject entry = root.getAsJsonObject("pending");
                Drop drop = new Drop();
                drop.site = entry.get("site").getAsString();
                drop.place = entry.get("place").getAsString();
                drop.x = entry.get("x").getAsInt();
                drop.z = entry.get("z").getAsInt();
                drop.land = entry.get("land").getAsLong();
                pending = drop;
            }
            for (JsonElement element : root.getAsJsonArray("crates")) {
                JsonObject entry = element.getAsJsonObject();
                JsonArray at = entry.getAsJsonArray("pos");
                Crate crate = new Crate();
                crate.pos = new BlockPos(at.get(0).getAsInt(), at.get(1).getAsInt(), at.get(2).getAsInt());
                crate.place = entry.get("place").getAsString();
                crate.expires = entry.get("expires").getAsLong();
                crate.opened = entry.get("opened").getAsBoolean();
                CRATES.add(crate);
            }
        } catch (IOException | RuntimeException error) {
            DoHy.LOGGER.warn("Largages illisibles", error);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        if (pending != null) {
            if (pending.forced) {
                level(server).getChunkSource().removeTicketWithRadius(TicketType.FORCED,
                        new ChunkPos(pending.x >> 4, pending.z >> 4), 2);
            }
            Entity entity = pending.entity == null ? null : level(server).getEntity(pending.entity);
            if (entity != null) {
                // la caisse en vol repartira au demarrage
                entity.discard();
            }
            pending.forced = false;
            pending.entity = null;
            pending.waited = 0;
        }
        save(server);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        pending = null;
        CRATES.clear();
        loaded = false;
    }
}
