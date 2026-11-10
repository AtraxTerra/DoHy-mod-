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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
 * est batie par etapes, 15 ms par tick ; une boussole pointe vers elle.
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
    /** Trois minutes pour charger (et generer) les chunks ; au-dela, on abandonne. */
    private static final int TIMEOUT_TICKS = 180 * 20;
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
        final Search search;
        final int retries;
        Site site;
        boolean forced;
        int ticks;
        EnemyBaseBuilder builder;

        Job(UUID player, EnemyBaseTier tier, String name, Search search, int retries) {
            this.player = player;
            this.tier = tier;
            this.name = name;
            this.search = search;
            this.retries = retries;
        }
    }

    /**
     * La recherche d'un site, etalee sur plusieurs ticks : chaque essai sonde
     * cinq hauteurs dans le bruit du generateur, ce qui coute quelques
     * millisecondes. Elle s'arrete au premier terrain assez plat, ou apres 64
     * essais sur le plus plat trouve.
     */
    private static final class Search {
        final EnemyBaseTier tier;
        final double ox;
        final double oz;
        final List<double[]> claims;
        int attempts;
        Site best;
        int bestSpread = Integer.MAX_VALUE;

        Search(ServerPlayer player, EnemyBaseTier tier) {
            this.tier = tier;
            this.ox = player.getX();
            this.oz = player.getZ();
            this.claims = new ArrayList<>();
            for (FactionData.Claim claim : FactionApi.data(player).claims()) {
                if ("minecraft:overworld".equals(claim.dimension())) {
                    claims.add(new double[]{(claim.x() << 4) + 8.0d, (claim.z() << 4) + 8.0d});
                }
            }
        }

        /** Essaie des sites pendant {@code budget} ns (un au moins) ; vrai quand la recherche est finie. */
        boolean step(ServerLevel level, long budget) {
            long end = System.nanoTime() + budget;
            RandomSource random = level.getRandom();
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            RandomState noise = level.getChunkSource().randomState();
            WorldBorder border = level.getWorldBorder();
            int sea = level.getSeaLevel();
            int half = tier.half();
            double minimum = MIN_DISTANCE + half + 16.0d;
            int reach = (tier.claimRadius() + 1) * 16;
            do {
                if (attempts++ >= SITE_ATTEMPTS) {
                    return true;
                }
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
                for (int[] offset : new int[][]{{0, 0}, {-half, -half}, {half, -half}, {-half, half}, {half, half}}) {
                    int y = generator.getBaseHeight(cx + offset[0], cz + offset[1], Heightmap.Types.WORLD_SURFACE_WG,
                            level, noise);
                    min = Math.min(min, y);
                    max = Math.max(max, y);
                }
                if (min > sea && max - min < bestSpread) {
                    best = new Site(cx, cz);
                    bestSpread = max - min;
                    if (bestSpread <= 6) {
                        return true;
                    }
                }
            } while (System.nanoTime() < end);
            return false;
        }
    }

    private static final Map<UUID, Job> JOBS = new LinkedHashMap<>();
    /** Les bases finies pendant que leur joueur etait deconnecte : remises a sa connexion. */
    private static final Map<UUID, EnemyBase> READY = new LinkedHashMap<>();
    /** Le chantier tourne au plus 15 ms par tick, la recherche du site 8 ms. */
    private static final long BUILD_BUDGET = 15_000_000L;
    private static final long SEARCH_BUDGET = 8_000_000L;

    private EnemyBases() {
    }

    public static EnemyBase of(ServerPlayer player) {
        return player.getData(Factions.ENEMY_BASE.get());
    }

    public static boolean pending(ServerPlayer player) {
        return JOBS.containsKey(player.getUUID()) || READY.containsKey(player.getUUID());
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
        String name = NAMES.get(player.getRandom().nextInt(NAMES.size()));
        JOBS.put(player.getUUID(), new Job(player.getUUID(), tier, name, new Search(player, tier), retries));
        if (retries == 0) {
            FactionApi.tell(player, "Tes eclaireurs partent reperer une base ennemie (" + tier.label()
                    + ") a plus de " + MoneyFormat.exact(MIN_DISTANCE) + " blocs...", ChatFormatting.GOLD);
        }
        return null;
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
        if (job.forced) {
            forEachChunk(job, (x, z) -> level.setChunkForced(x, z, false));
        }
        JOBS.remove(job.player);
    }

    /**
     * Charge les chunks (sans bloquer : un ticket force), puis bati la base par
     * etapes, quelques millisecondes par tick. Un joueur deconnecte avant le
     * chantier annule la demande ; pendant, le chantier va a son terme et la
     * base l'attend a sa connexion.
     */
    private static void advance(MinecraftServer server, Job job) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayer(job.player);
        if (job.builder == null) {
            if (player == null) {
                release(level, job);
                return;
            }
            if (job.site == null) {
                if (job.search.step(level, SEARCH_BUDGET)) {
                    job.site = job.search.best;
                    if (job.site == null) {
                        JOBS.remove(job.player);
                        FactionApi.tell(player, "Aucun site libre a plus de " + MoneyFormat.exact(MIN_DISTANCE)
                                + " blocs : la bordure du monde est trop proche, ou tout est ocean. Reessaie ailleurs.",
                                ChatFormatting.RED);
                    }
                }
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
            if (EnemyBaseBuilder.overlapsClaim(level, job.site.x() >> 4, job.site.z() >> 4, job.tier.claimRadius())) {
                release(level, job);
                String refusal = job.retries < RETRIES && player.level() == level
                        ? start(player, job.tier, job.retries + 1) : "Le terrain repere est deja claim. Reessaie.";
                if (refusal != null) {
                    FactionApi.tell(player, refusal, ChatFormatting.RED);
                }
                return;
            }
            job.builder = new EnemyBaseBuilder(level, level.getRandom(), job.tier, job.name, job.site.x(),
                    job.site.z());
            job.builder.plan();
            FactionApi.tell(player, "Terrain trouve : les " + job.name + " batissent leur " + job.tier.label()
                    .toLowerCase() + "...", ChatFormatting.GOLD);
            return;
        }
        if (!job.builder.work(BUILD_BUDGET)) {
            return;
        }
        EnemyBaseBuilder.Result result = job.builder.result();
        release(level, job);
        EnemyBase base = new EnemyBase(job.name, job.tier.level(), EnemyBase.ACTIVE, result.center(),
                result.coreMin(), result.coreMax(), System.currentTimeMillis());
        if (player == null) {
            READY.put(job.player, base);
            return;
        }
        deliver(player, base);
    }

    private static void deliver(ServerPlayer player, EnemyBase base) {
        player.setData(Factions.ENEMY_BASE.get(), base);
        FactionApi.tell(player, "Base reperee : les " + base.name() + " (" + base.kind().label() + ") en "
                + base.center().getX() + ", " + base.center().getZ() + " — " + where(player, base) + ".",
                ChatFormatting.GOLD);
        FactionApi.tell(player, "Il faudra : " + base.kind().tools() + ". Entre dans la salle des coffres de leur "
                + "donjon pour piller la base.", ChatFormatting.YELLOW);
        compass(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            EnemyBase base = READY.remove(player.getUUID());
            if (base != null) {
                deliver(player, base);
            }
        }
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
        if (job != null && job.builder != null) {
            return "Leur base se batit encore : patience";
        }
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
