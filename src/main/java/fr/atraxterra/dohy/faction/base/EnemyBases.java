package fr.atraxterra.dohy.faction.base;

import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Les bases ennemies sur demande (lot 22g).
 *
 * <p>En solo, il n'y a pas de faction rivale a piller : les objets de pillage
 * (dynamites, TNT, Unclaim Finder, explorateur de coffre, withers) n'avaient
 * pas de cible. Sur demande, une faction ennemie fictive s'installe <b>a plus
 * de 9 000 blocs</b> de toi et de tous tes claims, dans le Monde, hors de
 * l'ocean, sur un terrain assez plat (la hauteur du generateur est sondee
 * sans rien generer). Ses chunks sont charges en tache de fond, puis la base
 * est batie d'un coup ; une boussole pointe vers elle.
 *
 * <p>Ses claims suivent la regle de Paladium : rien a la main, mais les
 * explosions et les withers y passent. <b>Entrer dans la salle des coffres,
 * c'est l'avoir pillee</b> : de l'ELO, de l'argent a la banque de faction, et
 * les claims tombent, les coffres s'ouvrent. Une base a la fois ; l'abandonner
 * la laisse en place, claimee, sans recompense.
 */
@EventBusSubscriber
public final class EnemyBases {

    /** La distance minimale entre la base et toi, et entre la base et chacun de tes claims. */
    public static final int MIN_DISTANCE = 9_000;
    /** Jusqu'ou au-dela : de 9 000 a 10 500 blocs, plus la demi-emprise. */
    private static final int SPREAD = 1_500;
    private static final int SITE_ATTEMPTS = 64;
    /** Une minute et demie pour charger les chunks ; au-dela, on abandonne. */
    private static final int TIMEOUT_TICKS = 90 * 20;
    private static final int RETRIES = 3;

    static final String GUARD_TAG = "dohy_enemy_guard";

    private static final List<String> NAMES = List.of("Corbeaux", "Vautours", "Rapaces", "Spectres", "Chacals",
            "Hyenes", "Goules", "Cendres", "Ronces", "Crocs", "Fauves", "Ombres", "Brigands", "Pillards", "Maraudeurs",
            "Renegats", "Charognards", "Scorpions", "Frelons", "Sangsues", "Vipers", "Loups Gris", "Lames Noires",
            "Dents Rouges");

    private record Site(int x, int z) {
    }

    private static final class Job {
        final UUID player;
        final EnemyBaseTier tier;
        final String name;
        final Site site;
        final int retries;
        boolean forced;
        int ticks;

        Job(UUID player, EnemyBaseTier tier, String name, Site site, int retries) {
            this.player = player;
            this.tier = tier;
            this.name = name;
            this.site = site;
            this.retries = retries;
        }
    }

    private static final Map<UUID, Job> JOBS = new LinkedHashMap<>();

    private EnemyBases() {
    }

    public static EnemyBase of(ServerPlayer player) {
        return player.getData(Factions.ENEMY_BASE.get());
    }

    public static boolean pending(ServerPlayer player) {
        return JOBS.containsKey(player.getUUID());
    }

    // -- Demande ------------------------------------------------------------------

    /** Lance la generation ; {@code null} si elle part, sinon le refus. */
    public static String request(ServerPlayer player, EnemyBaseTier tier) {
        if (!FactionApi.has(player)) {
            return "Fonde d'abord ta faction";
        }
        if (pending(player)) {
            return "Tes eclaireurs cherchent deja une base";
        }
        EnemyBase current = of(player);
        if (current.active()) {
            return "Tu as deja une base a piller : les " + current.name()
                    + ". Pille-la, ou abandonne-la (/f base abandonner)";
        }
        if (player.level().dimension() != Level.OVERWORLD) {
            return "Demande une base depuis le Monde";
        }
        return start(player, tier, 0);
    }

    private static String start(ServerPlayer player, EnemyBaseTier tier, int retries) {
        ServerLevel level = (ServerLevel) player.level();
        Site site = findSite(level, player, tier);
        if (site == null) {
            return "Aucun site libre a plus de " + MoneyFormat.exact(MIN_DISTANCE)
                    + " blocs : la bordure du monde est trop proche, ou tout est ocean. Reessaie ailleurs";
        }
        String name = NAMES.get(level.getRandom().nextInt(NAMES.size()));
        JOBS.put(player.getUUID(), new Job(player.getUUID(), tier, name, site, retries));
        if (retries == 0) {
            FactionApi.tell(player, "Tes eclaireurs partent reperer une base ennemie (" + tier.label()
                    + ") a plus de " + MoneyFormat.exact(MIN_DISTANCE) + " blocs...", ChatFormatting.GOLD);
        }
        return null;
    }

    /**
     * Un centre de chunk a plus de 9 000 blocs (emprise comprise) du joueur et
     * de ses claims du Monde, dans la bordure, hors ocean, riviere et plage, et
     * le plus plat trouve en 64 essais. Rien n'est genere : la hauteur vient
     * du bruit du generateur.
     */
    private static Site findSite(ServerLevel level, ServerPlayer player, EnemyBaseTier tier) {
        RandomSource random = level.getRandom();
        double ox = player.getX();
        double oz = player.getZ();
        List<double[]> claims = new ArrayList<>();
        for (FactionData.Claim claim : FactionApi.data(player).claims()) {
            if ("minecraft:overworld".equals(claim.dimension())) {
                claims.add(new double[]{(claim.x() << 4) + 8.0d, (claim.z() << 4) + 8.0d});
            }
        }
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState noise = level.getChunkSource().randomState();
        WorldBorder border = level.getWorldBorder();
        int sea = level.getSeaLevel();
        int half = tier.half();
        double minimum = MIN_DISTANCE + half + 16.0d;
        int reach = (tier.claimRadius() + 1) * 16;
        Site best = null;
        int bestSpread = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < SITE_ATTEMPTS; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0d;
            double distance = minimum + random.nextDouble() * SPREAD;
            int cx = (Mth.floor(ox + Math.cos(angle) * distance) >> 4) * 16 + 8;
            int cz = (Mth.floor(oz + Math.sin(angle) * distance) >> 4) * 16 + 8;
            if (squared(cx, cz, ox, oz) < minimum * minimum) {
                continue;
            }
            boolean tooClose = false;
            for (double[] claim : claims) {
                if (squared(cx, cz, claim[0], claim[1]) < minimum * minimum) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose || !border.isWithinBounds(new BlockPos(cx - reach, sea, cz - reach))
                    || !border.isWithinBounds(new BlockPos(cx + reach, sea, cz + reach))) {
                continue;
            }
            Holder<Biome> biome = level.getBiome(new BlockPos(cx, sea, cz));
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER)
                    || biome.is(BiomeTags.IS_BEACH)) {
                continue;
            }
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int[] offset : new int[][]{{0, 0}, {-half, -half}, {half, -half}, {-half, half}, {half, half},
                    {0, -half}, {0, half}, {-half, 0}, {half, 0}}) {
                int y = generator.getBaseHeight(cx + offset[0], cz + offset[1], Heightmap.Types.WORLD_SURFACE_WG,
                        level, noise);
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
            if (min <= sea) {
                continue;
            }
            if (max - min < bestSpread) {
                best = new Site(cx, cz);
                bestSpread = max - min;
                if (bestSpread <= 4) {
                    break;
                }
            }
        }
        return best;
    }

    private static double squared(double x, double z, double ox, double oz) {
        double dx = x - ox;
        double dz = z - oz;
        return dx * dx + dz * dz;
    }

    // -- Chantier -----------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!JOBS.isEmpty()) {
            advance(server, JOBS.values().iterator().next());
        }
        if (server.getTickCount() % 20 == 0) {
            for (ServerPlayer player : server.overworld().players()) {
                EnemyBase base = of(player);
                if (base.active() && !player.isSpectator() && player.getBoundingBox().intersects(base.core())) {
                    loot(player, base);
                }
            }
        }
    }

    private static void forEachChunk(Job job, ChunkAction action) {
        int radius = job.tier.claimRadius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                action.apply((job.site.x() >> 4) + dx, (job.site.z() >> 4) + dz);
            }
        }
    }

    private interface ChunkAction {
        void apply(int x, int z);
    }

    private static void release(ServerLevel level, Job job) {
        forEachChunk(job, (x, z) -> level.setChunkForced(x, z, false));
        JOBS.remove(job.player);
    }

    /** Charge les chunks (sans bloquer : un ticket force), puis bati quand tous sont la. */
    private static void advance(MinecraftServer server, Job job) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayer(job.player);
        if (player == null) {
            release(level, job);
            return;
        }
        if (!job.forced) {
            forEachChunk(job, (x, z) -> level.setChunkForced(x, z, true));
            job.forced = true;
        }
        job.ticks++;
        boolean[] ready = {true};
        forEachChunk(job, (x, z) -> ready[0] &= level.getChunkSource().hasChunk(x, z));
        if (!ready[0]) {
            if (job.ticks > TIMEOUT_TICKS) {
                release(level, job);
                FactionApi.tell(player, "Tes eclaireurs ne sont pas revenus : le monde met trop de temps a se "
                        + "generer. Reessaie.", ChatFormatting.RED);
            }
            return;
        }
        int chunkX = job.site.x() >> 4;
        int chunkZ = job.site.z() >> 4;
        if (EnemyBaseBuilder.overlapsClaim(level, chunkX, chunkZ, job.tier.claimRadius())) {
            release(level, job);
            String refusal = job.retries < RETRIES && player.level() == level ? start(player, job.tier, job.retries + 1)
                    : "Le terrain repere est deja claim. Reessaie.";
            if (refusal != null) {
                FactionApi.tell(player, refusal, ChatFormatting.RED);
            }
            return;
        }
        EnemyBaseBuilder.Result result = new EnemyBaseBuilder(level, level.getRandom(), job.tier, job.name,
                job.site.x(), job.site.z()).build();
        release(level, job);
        EnemyBase base = new EnemyBase(job.name, job.tier.level(), EnemyBase.ACTIVE, result.center(),
                result.coreMin(), result.coreMax(), System.currentTimeMillis());
        player.setData(Factions.ENEMY_BASE.get(), base);
        FactionApi.tell(player, "Base reperee : les " + base.name() + " (" + job.tier.label() + ") en "
                + base.center().getX() + ", " + base.center().getZ() + " — " + where(player, base) + ".",
                ChatFormatting.GOLD);
        FactionApi.tell(player, "Il faudra : " + job.tier.tools() + ". Entre dans leur salle des coffres pour "
                + "piller la base.", ChatFormatting.YELLOW);
        compass(player);
    }

    // -- Pillage --------------------------------------------------------------------

    /** Le joueur est dans la salle des coffres : la base est pillee. */
    private static void loot(ServerPlayer player, EnemyBase base) {
        EnemyBaseTier tier = base.kind();
        player.setData(Factions.ENEMY_BASE.get(), base.looted());
        ServerLevel level = (ServerLevel) player.level();
        int radius = tier.claimRadius();
        int chunkX = base.center().getX() >> 4;
        int chunkZ = base.center().getZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = level.getChunk(chunkX + dx, chunkZ + dz);
                ClaimOwner owner = chunk.getData(Factions.CLAIM.get());
                if (owner.raidable() && owner.faction().equals(base.name())) {
                    chunk.setData(Factions.CLAIM.get(), ClaimOwner.NONE);
                    chunk.markUnsaved();
                }
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS,
                1.0f, 1.0f);
        FactionApi.tell(player, "Base des " + base.name() + " pillee ! Leurs claims tombent : les coffres sont a toi.",
                ChatFormatting.GOLD);
        FactionApi.addElo(player, tier.elo(), "pillage des " + base.name());
        FactionApi.creditBank(player, tier.money(), "Pillage des " + base.name());
        FactionApi.tell(player, "+" + MoneyFormat.exact(tier.money()) + " $ a la banque de faction.",
                ChatFormatting.GREEN);
        FactionApi.stat(player, "raid:base", 1);
        FactionApi.stat(player, "raid:" + tier.key(), 1);
    }

    /** Oublie la base : elle reste claimee, a piller, mais sans recompense. */
    public static String abandon(ServerPlayer player) {
        Job job = JOBS.get(player.getUUID());
        if (job != null) {
            release(((ServerLevel) player.level()).getServer().overworld(), job);
            return null;
        }
        if (!of(player).active()) {
            return "Tu n'as pas de base a piller";
        }
        player.setData(Factions.ENEMY_BASE.get(), EnemyBase.EMPTY);
        return null;
    }

    /** Une boussole qui pointe vers la base (comme une boussole liee a une magnetite). */
    public static String compass(ServerPlayer player) {
        EnemyBase base = of(player);
        if (!base.active()) {
            return "Tu n'as pas de base a piller";
        }
        ItemStack compass = new ItemStack(Items.COMPASS);
        compass.set(DataComponents.LODESTONE_TRACKER,
                new LodestoneTracker(Optional.of(GlobalPos.of(Level.OVERWORLD, base.center())), false));
        compass.set(DataComponents.CUSTOM_NAME, Component.literal("Base des " + base.name())
                .withStyle(ChatFormatting.RED));
        if (!player.getInventory().add(compass)) {
            player.drop(compass, false);
        }
        return null;
    }

    // -- Textes ---------------------------------------------------------------------

    /** La distance et la direction depuis le joueur : « 9 412 blocs au nord-est ». */
    public static String where(ServerPlayer player, EnemyBase base) {
        if (player.level().dimension() != Level.OVERWORLD) {
            return "dans le Monde";
        }
        double dx = base.center().getX() + 0.5d - player.getX();
        double dz = base.center().getZ() + 0.5d - player.getZ();
        long distance = Math.round(Math.sqrt(dx * dx + dz * dz));
        if (distance < 32) {
            return "ici";
        }
        String[] directions = {"nord", "nord-est", "est", "sud-est", "sud", "sud-ouest", "ouest", "nord-ouest"};
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = Math.floorMod((int) Math.round(angle / 45.0d), 8);
        return MoneyFormat.exact(distance) + " blocs au " + directions[sector];
    }
}
