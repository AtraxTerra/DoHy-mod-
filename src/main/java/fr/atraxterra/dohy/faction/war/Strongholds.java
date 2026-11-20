package fr.atraxterra.dohy.faction.war;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthNations;
import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.Factions;
import fr.atraxterra.dohy.faction.base.BaseWorks;
import fr.atraxterra.dohy.faction.base.EnemyBaseStyle;
import fr.atraxterra.dohy.faction.base.EnemyBaseTier;
import fr.atraxterra.dohy.faction.base.NtmDefenses;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La riposte : en guerre, la nation tient une place forte, et tes missiles de
 * NTM peuvent la raser.
 *
 * <p><b>La place forte.</b> Une nation qui te fait la guerre batit une base
 * comme les bases ennemies (enceinte, miradors, gardes, tourelles et mines de
 * NTM, salle forte) a 1 200 - 2 000 blocs de toi et de tes claims, dans le
 * Monde, loin des autres joueurs. Sa taille suit son armee (avant-poste, base,
 * forteresse), son genre sa doctrine. Ses coordonnees sont dans l'ecran de la
 * guerre ; un clic, ou {@code /guerre placeforte}, donne une boussole.
 *
 * <p><b>La raser.</b> Un missile de NTM (une rampe de lancement, un
 * designateur sur ses coordonnees) qui tombe sur elle est repere ; apres
 * l'impact, ce qui reste de sa structure (au-dessus du sol) et des murs de sa
 * salle forte est compte, a plusieurs reprises (le cratere d'une bombe
 * nucleaire se creuse en quelques minutes). Sous {@value #RAZED_AT} % de sa
 * structure, ou les murs de sa salle forte sous {@value #VAULT_AT} %, elle est
 * rasee. Entrer dans sa salle forte la fait tomber aussi (« pillee »).
 *
 * <p><b>Ce qu'elle perd</b> ({@link WarStrategy#razed}) : 30 % de son tresor
 * pour ta banque de faction, la moitie de son armee, de la crainte, une treve
 * de sept jours ; sur la carte de la Terre, un de ses pays passe a ta faction
 * (+1 claim). Ses claims tombent, ses tourelles s'eteignent, et cinq minutes
 * plus tard la place forte s'efface : son carre de claims revient tel qu'il
 * etait, crateres compris. Tombee pendant que tu es deconnecte (ou pendant un
 * assaut, une frappe), la victoire est comptee a ton retour (a la fin).
 *
 * <p>La guerre finie autrement (paix, treve, tribut), la nation demonte sa
 * place forte, sans rien laisser.
 */
@EventBusSubscriber
public final class Strongholds {

    /** La place forte se pose entre 1 200 et 2 000 blocs de toi et de chacun de tes claims. */
    static final int MIN_DISTANCE = 1_200;
    static final int SPREAD = 800;
    /** Et a distance des autres joueurs en ligne et de leurs claims : ses tourelles tirent sur tout le monde. */
    private static final int OTHERS = 160;
    /** Rasee sous ce pourcentage de sa structure, ou des murs de sa salle forte. */
    static final int RAZED_AT = 60;
    static final int VAULT_AT = 70;
    /** Les comptes apres un impact, en ticks : l'explosion, puis le cratere nucleaire qui se creuse. */
    private static final int[] CHECKS = {2, 100, 600, 1_200, 2_400, 3_600, 6_000};
    /** Un missile a moins de cette distance : la place forte se charge pour l'accueillir. */
    private static final double APPROACH = 320.0d;
    /** La structure se compte jusqu'au sommet des mats et des miradors. */
    private static final int ABOVE = 14;
    private static final int SITE_ATTEMPTS = 48;
    private static final long SEARCH_BUDGET = 6_000_000L;
    private static final long BUILD_BUDGET = 10_000_000L;
    private static final int TIMEOUT_TICKS = 180 * 20;
    private static final int RETRIES = 3;
    private static final long RETRY_MS = 30L * 60_000L;

    private Strongholds() {
    }

    // -- La place forte ----------------------------------------------------------------------------

    /** Une place forte batie, gardee dans {@code <monde>/dohy/places-fortes.json}. */
    static final class Fort {
        UUID player;
        String playerName = "";
        int nation;
        String name = "";
        int tier = 1;
        String style = EnemyBaseStyle.SOVIET.key();
        BlockPos center = BlockPos.ZERO;
        BlockPos coreMin = BlockPos.ZERO;
        BlockPos coreMax = BlockPos.ZERO;
        int baseline;
        int vaultBaseline;
        int integrity = 100;
        int vault = 100;
        long created;
        /** Tombee, la victoire pas encore comptee (joueur deconnecte, assaut ou frappe en cours). */
        boolean reward;
        String how = "";

        /** Les comptes a faire (tick du serveur), et le chargement de ses chunks. */
        final List<Integer> checks = new ArrayList<>();
        int loadedUntil;
        int warned = 100;

        EnemyBaseTier kind() {
            return EnemyBaseTier.byLevel(tier);
        }

        EnemyBaseStyle genre() {
            EnemyBaseStyle found = EnemyBaseStyle.byKey(style);
            return found == null ? EnemyBaseStyle.SOVIET : found;
        }

        NationTraits traits() {
            return NationTraits.of(nation);
        }

        /** « La place forte de Nordheim ». */
        String title() {
            return "La place forte " + traits().de();
        }

        AABB core() {
            return new AABB(coreMin.getX(), coreMin.getY(), coreMin.getZ(), coreMax.getX() + 1.0d,
                    coreMax.getY() + 1.0d, coreMax.getZ() + 1.0d);
        }

        /** Dans son emprise (et son glacis), en x et z ? */
        boolean covers(double x, double z) {
            double reach = kind().half() + 24.0d;
            return Math.abs(x - center.getX() - 0.5d) <= reach && Math.abs(z - center.getZ() - 0.5d) <= reach;
        }

        UUID ticket() {
            return UUID.nameUUIDFromBytes(("dohy:place_forte:" + player).getBytes());
        }
    }

    /** Un chantier : la recherche du site, le chargement des chunks, puis la base. */
    private static final class Job {
        final UUID player;
        final String playerName;
        final int nation;
        final EnemyBaseTier tier;
        final EnemyBaseStyle style;
        final String name;
        final String guard;
        final double ox;
        final double oz;
        final int min;
        final int spread;
        /** Tes claims (loin d'eux), puis les autres joueurs en ligne et leurs claims (a distance). */
        final List<double[]> claims = new ArrayList<>();
        final List<double[]> others = new ArrayList<>();
        final int retries;
        int attempts;
        int[] best;
        int bestSpread = Integer.MAX_VALUE;
        int[] site;
        boolean forced;
        int ticks;
        BaseWorks works;

        Job(UUID player, String playerName, int nation, EnemyBaseTier tier, EnemyBaseStyle style, String name,
            String guard, double ox, double oz, int min, int spread, int retries) {
            this.player = player;
            this.playerName = playerName;
            this.nation = nation;
            this.tier = tier;
            this.style = style;
            this.name = name;
            this.guard = guard;
            this.ox = ox;
            this.oz = oz;
            this.min = min;
            this.spread = spread;
            this.retries = retries;
        }

        /** Le meme chantier, sur un autre site. */
        Job again() {
            Job next = new Job(player, playerName, nation, tier, style, name, guard, ox, oz, min, spread,
                    retries + 1);
            next.claims.addAll(claims);
            next.others.addAll(others);
            return next;
        }
    }

    private static final Map<UUID, Fort> FORTS = new LinkedHashMap<>();
    private static final Map<UUID, Long> RETRY = new HashMap<>();
    /** Les missiles de NTM des joueurs en vol dans le Monde, et leur derniere position. */
    private static final Map<UUID, Vec3> MISSILES = new LinkedHashMap<>();
    /**
     * Les places fortes tombees, le temps que leur terrain revienne : les
     * tourelles d'un chunk qui dormait a leur chute s'eteignent en se chargeant
     * (le tick du serveur ou l'on cesse d'y veiller).
     */
    private static final Map<BlockPos, int[]> FALLEN = new LinkedHashMap<>();
    private static Job job;
    private static boolean loaded;

    /** La place forte de la nation qui fait la guerre a ce joueur, debout ; {@code null} sinon. */
    static Fort of(UUID player) {
        Fort fort = FORTS.get(player);
        return fort == null || fort.reward ? null : fort;
    }

    /** Un chantier est-il en cours pour ce joueur ? */
    static boolean building(UUID player) {
        return job != null && job.player.equals(player);
    }

    /** Une place forte contre ce joueur, debout, en chantier, ou tombee et pas encore comptee ? */
    static boolean standing(UUID player) {
        return FORTS.containsKey(player) || building(player);
    }

    // -- La commande d'une place forte ---------------------------------------------------------------

    /**
     * La nation batit sa place forte ; {@code null} si le chantier part, sinon
     * le refus. {@code distance} : 0 pour la distance ordinaire (1 200 - 2 000
     * blocs), sinon celle-ci (administration, essais).
     */
    static String order(ServerPlayer player, WarData.Relation relation, int distance) {
        if (!FactionApi.has(player)) {
            return "Il faut une faction";
        }
        if (FORTS.containsKey(player.getUUID())) {
            return "Une place forte se dresse deja contre toi";
        }
        if (job != null) {
            return "Un chantier est deja en cours";
        }
        MinecraftServer server = player.level().getServer();
        ServerLevel world = server.overworld();
        double ox;
        double oz;
        Optional<Map.Entry<ServerLevel, BlockPos>> vault = WarStrategy.vault(player, WarStrategy.data(player));
        if (vault.isPresent() && vault.get().getKey() == world) {
            ox = vault.get().getValue().getX();
            oz = vault.get().getValue().getZ();
        } else if (player.level() == world) {
            ox = player.getX();
            oz = player.getZ();
        } else {
            return "La place forte se batit dans le Monde : reviens-y";
        }
        NationTraits traits = relation.traits();
        // Une coalition batit plus grand : l'allie y met la moitie de son armee (lot 22l-2).
        WarData.Relation ally = WarStrategy.comrade(WarStrategy.data(player), relation, System.currentTimeMillis());
        int strength = relation.army() + (ally == null ? 0 : ally.army() / 2);
        EnemyBaseTier tier = strength < 55 ? EnemyBaseTier.OUTPOST
                : strength < 90 ? EnemyBaseTier.BASE : EnemyBaseTier.FORTRESS;
        EnemyBaseStyle style = switch (traits.doctrine()) {
            case SIEGE -> EnemyBaseStyle.BUNKER;
            case CHOC -> EnemyBaseStyle.SOVIET;
            case ARCHERS -> EnemyBaseStyle.RADAR;
            default -> EnemyBaseStyle.SILO;
        };
        // Le nom de ses claims : « Place forte de Nordheim » (le message d'un claim ennemi le reprend tel quel).
        String name = "Place forte " + traits.de();
        int min = distance > 0 ? distance : MIN_DISTANCE;
        int spread = distance > 0 ? Math.max(32, distance / 4) : SPREAD;
        Job next = new Job(player.getUUID(), player.getGameProfile().name(), relation.nation, tier, style, name,
                "Garde " + traits.de(), ox, oz, min, spread, 0);
        for (FactionData.Claim claim : FactionApi.data(player).claims()) {
            if ("minecraft:overworld".equals(claim.dimension())) {
                next.claims.add(new double[] {(claim.x() << 4) + 8.0d, (claim.z() << 4) + 8.0d});
            }
        }
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == player) {
                continue;
            }
            if (other.level() == world) {
                next.others.add(new double[] {other.getX(), other.getZ()});
            }
            if (FactionApi.has(other)) {
                for (FactionData.Claim claim : FactionApi.data(other).claims()) {
                    if ("minecraft:overworld".equals(claim.dimension())) {
                        next.others.add(new double[] {(claim.x() << 4) + 8.0d, (claim.z() << 4) + 8.0d});
                    }
                }
            }
        }
        for (Fort fort : FORTS.values()) {
            next.others.add(new double[] {fort.center.getX(), fort.center.getZ()});
        }
        job = next;
        String text = switch (traits.voice()) {
            case "brutale" -> "Nous plantons une place forte face a toi. Viens la prendre, si tu l'oses.";
            case "courtoise" -> "Nous etablissons une place forte, a bonne distance. La guerre a ses regles.";
            case "mielleuse" -> "Nous nous installons pas loin... juste pour garder un oeil sur toi.";
            default -> "Construction d'une place forte : en cours.";
        };
        WarStrategy.say(player, relation, text, ChatFormatting.RED);
        DoHy.LOGGER.info("Guerre : {} commande une place forte ({} {}) contre {}", traits.name(), tier.key(),
                style.key(), player.getGameProfile().name());
        return null;
    }

    // -- Le tick ---------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (job != null) {
            try {
                advance(server, job);
            } catch (RuntimeException failure) {
                DoHy.LOGGER.error("Guerre : le chantier de la place forte a echoue", failure);
                release(server.overworld(), job);
                job = null;
            }
        }
        if (tick % 100 == 0) {
            survey(server);
        }
        ServerLevel world = server.overworld();
        if (tick % 40 == 0 && !FALLEN.isEmpty()) {
            FALLEN.entrySet().removeIf(entry -> entry.getValue()[1] < tick);
            for (Map.Entry<BlockPos, int[]> entry : FALLEN.entrySet()) {
                BaseWorks.turrets(world, entry.getKey(), entry.getValue()[0], false);
            }
        }
        if (FORTS.isEmpty()) {
            MISSILES.clear();
            return;
        }
        if (tick % 20 == 0) {
            for (Fort fort : List.copyOf(FORTS.values())) {
                ServerPlayer owner = server.getPlayerList().getPlayer(fort.player);
                if (owner == null) {
                    continue;
                }
                if (fort.reward) {
                    if (!busy(owner)) {
                        reward(owner, fort);
                    }
                } else if (owner.level() == world && !owner.isSpectator()
                        && owner.getBoundingBox().intersects(fort.core())) {
                    raze(server, fort, "pillage");
                }
            }
        }
        if (tick % 40 == 0 && NtmDefenses.present()) {
            for (Fort fort : FORTS.values()) {
                if (!fort.reward) {
                    BaseWorks.turrets(world, fort.center, fort.kind().claimRadius(), true);
                }
            }
        }
        track(server, world, tick);
        for (Fort fort : List.copyOf(FORTS.values())) {
            check(server, world, fort, tick);
        }
    }

    /** Un assaut ou une frappe en cours : la victoire attendra la fin. */
    private static boolean busy(ServerPlayer player) {
        return Sieges.of(player.getUUID()) != null || Strikes.striking(player.getUUID());
    }

    /** Toutes les cinq secondes : une guerre sans place forte en commande une, une place forte sans guerre se demonte. */
    private static void survey(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Fort fort = FORTS.get(player.getUUID());
            if (fort != null && fort.reward) {
                continue;
            }
            Optional<WarData.Relation> war = FactionApi.has(player)
                    ? WarStrategy.data(player).conflict().filter(r -> r.stance() == WarData.Stance.GUERRE)
                    : Optional.empty();
            if (fort != null && (war.isEmpty() || war.get().nation != fort.nation)) {
                dismantle(server, fort, "fin de la guerre");
            } else if (fort == null && war.isPresent() && job == null
                    && RETRY.getOrDefault(player.getUUID(), 0L) <= now && player.level() == server.overworld()) {
                RETRY.put(player.getUUID(), now + RETRY_MS);
                order(player, war.get(), 0);
            }
        }
    }

    // -- Le chantier -------------------------------------------------------------------------------

    private static void advance(MinecraftServer server, Job current) {
        ServerLevel level = server.overworld();
        ServerPlayer player = server.getPlayerList().getPlayer(current.player);
        if (current.works == null) {
            if (player == null) {
                release(level, current);
                job = null;
                return;
            }
            if (current.site == null) {
                if (search(level, current, SEARCH_BUDGET)) {
                    current.site = current.best;
                    if (current.site == null) {
                        job = null;
                        DoHy.LOGGER.info("Guerre : pas de site pour la place forte contre {}", current.playerName);
                    }
                }
                return;
            }
            if (!current.forced) {
                forEachChunk(current, (x, z) -> level.getChunkSource().addTicketWithRadius(TicketType.FORCED,
                        new ChunkPos(x, z), 0));
                current.forced = true;
            }
            current.ticks++;
            boolean[] ready = {true};
            forEachChunk(current, (x, z) -> ready[0] &= level.getChunkSource().getChunkNow(x, z) != null);
            if (!ready[0]) {
                if (current.ticks > TIMEOUT_TICKS) {
                    release(level, current);
                    job = null;
                }
                return;
            }
            int reach = (2 * current.tier.claimRadius() + 1) * 16;
            if (BaseWorks.overlapsClaim(level, current.site[0] >> 4, current.site[1] >> 4,
                    current.tier.claimRadius())
                    || BaseWorks.regeneratingNear(current.site[0], current.site[1], reach)) {
                release(level, current);
                job = current.retries < RETRIES ? current.again() : null;
                return;
            }
            current.works = new BaseWorks(level, current.tier, current.style, current.name, current.guard,
                    current.site[0], current.site[1]);
            return;
        }
        if (current.works.work(BUILD_BUDGET)) {
            finish(server, current, player);
        }
    }

    /** Le chantier est fini : la place forte est comptee, gardee, annoncee. */
    private static void finish(MinecraftServer server, Job current, ServerPlayer player) {
        ServerLevel level = server.overworld();
        Fort fort = new Fort();
        fort.player = current.player;
        fort.playerName = current.playerName;
        fort.nation = current.nation;
        fort.name = current.name;
        fort.tier = current.tier.level();
        fort.style = current.style.key();
        fort.center = current.works.center();
        fort.coreMin = current.works.coreMin();
        fort.coreMax = current.works.coreMax();
        fort.created = System.currentTimeMillis();
        fort.baseline = Math.max(1, census(level, structure(fort)));
        fort.vaultBaseline = Math.max(1, census(level, shell(fort)));
        release(level, current);
        job = null;
        FORTS.put(fort.player, fort);
        save(server);
        DoHy.LOGGER.info("Guerre : place forte {} ({} {}) en {} contre {} : {} blocs de structure, {} aux murs de la "
                        + "salle forte, {} gardes, {} tourelles", fort.traits().de(), current.tier.key(),
                current.style.key(), fort.center.toShortString(), fort.playerName, fort.baseline, fort.vaultBaseline,
                current.works.guards(), current.works.turrets());
        // La guerre a pu finir pendant le chantier : survey() la demontera.
        if (player != null) {
            deliver(player, fort);
        }
    }

    private interface ChunkAction {
        void apply(int x, int z);
    }

    private static void forEachChunk(Job current, ChunkAction action) {
        int radius = current.tier.claimRadius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                action.apply((current.site[0] >> 4) + dx, (current.site[1] >> 4) + dz);
            }
        }
    }

    private static void release(ServerLevel level, Job current) {
        if (current.forced) {
            forEachChunk(current, (x, z) -> level.getChunkSource().removeTicketWithRadius(TicketType.FORCED,
                    new ChunkPos(x, z), 0));
            current.forced = false;
        }
    }

    private static double squared(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return dx * dx + dz * dz;
    }

    /**
     * Cherche un site pendant {@code budget} ns : loin de toi et de tes claims,
     * a distance des autres joueurs, sur la terre ferme, le plus plat possible
     * (la hauteur du generateur est sondee sans rien generer). Vrai quand la
     * recherche est finie.
     */
    private static boolean search(ServerLevel level, Job current, long budget) {
        long end = System.nanoTime() + budget;
        RandomSource random = level.getRandom();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState noise = level.getChunkSource().randomState();
        WorldBorder border = level.getWorldBorder();
        int sea = level.getSeaLevel();
        int half = current.tier.half();
        double minimum = current.min + half + 16.0d;
        double apart = (current.tier.claimRadius() + 1) * 16.0d + OTHERS;
        int reach = (current.tier.claimRadius() + 1) * 16;
        do {
            if (current.attempts++ >= SITE_ATTEMPTS) {
                return true;
            }
            double angle = random.nextDouble() * Math.PI * 2.0d;
            double distance = minimum + random.nextDouble() * current.spread;
            int cx = (Mth.floor(current.ox + Math.cos(angle) * distance) >> 4) * 16 + 8;
            int cz = (Mth.floor(current.oz + Math.sin(angle) * distance) >> 4) * 16 + 8;
            boolean close = false;
            for (double[] claim : current.claims) {
                if (squared(cx, cz, claim[0], claim[1]) < minimum * minimum) {
                    close = true;
                    break;
                }
            }
            for (int i = 0; !close && i < current.others.size(); i++) {
                double[] other = current.others.get(i);
                close = squared(cx, cz, other[0], other[1]) < apart * apart;
            }
            if (close || !border.isWithinBounds(new BlockPos(cx - reach, sea, cz - reach))
                    || !border.isWithinBounds(new BlockPos(cx + reach, sea, cz + reach))) {
                continue;
            }
            Holder<Biome> biome = level.getBiome(new BlockPos(cx, sea, cz));
            if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_RIVER)
                    || biome.is(BiomeTags.IS_BEACH)) {
                continue;
            }
            int lowest = Integer.MAX_VALUE;
            int highest = Integer.MIN_VALUE;
            for (int[] offset : new int[][] {{0, 0}, {-half, -half}, {half, -half}, {-half, half}, {half, half}}) {
                int y = generator.getBaseHeight(cx + offset[0], cz + offset[1], Heightmap.Types.WORLD_SURFACE_WG,
                        level, noise);
                lowest = Math.min(lowest, y);
                highest = Math.max(highest, y);
            }
            if (lowest > sea && highest - lowest < current.bestSpread) {
                current.best = new int[] {cx, cz};
                current.bestSpread = highest - lowest;
                if (current.bestSpread <= 6) {
                    return true;
                }
            }
        } while (System.nanoTime() < end);
        return false;
    }

    private static void deliver(ServerPlayer player, Fort fort) {
        FactionApi.tell(player, fort.title() + " est debout : " + fort.genre().label() + " ("
                + fort.kind().label().toLowerCase(java.util.Locale.ROOT) + ") en " + coordinates(fort) + ", "
                + where(player, fort.center) + ".", ChatFormatting.GOLD);
        FactionApi.tell(player, "Rase-la avec tes missiles NTM (designateur sur ces coordonnees), ou entre dans sa "
                + "salle forte : elle reculera.", ChatFormatting.YELLOW);
        compass(player);
    }

    static String coordinates(Fort fort) {
        return fort.center.getX() + " " + fort.center.getY() + " " + fort.center.getZ();
    }

    /** « 1 640 blocs au nord-est » depuis le joueur. */
    static String where(ServerPlayer player, BlockPos center) {
        if (player.level().dimension() != Level.OVERWORLD) {
            return "dans le Monde";
        }
        double dx = center.getX() + 0.5d - player.getX();
        double dz = center.getZ() + 0.5d - player.getZ();
        long distance = Math.round(Math.sqrt(dx * dx + dz * dz));
        String[] directions = {"au nord", "au nord-est", "a l'est", "au sud-est", "au sud", "au sud-ouest",
                "a l'ouest", "au nord-ouest"};
        int sector = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dx, -dz)) / 45.0d), 8);
        return MoneyFormat.exact(distance) + " blocs " + directions[sector];
    }

    /** Une boussole qui pointe vers la place forte ; {@code null}, ou le refus. */
    static String compass(ServerPlayer player) {
        Fort fort = of(player.getUUID());
        if (fort == null) {
            return building(player.getUUID()) ? "Leur place forte se batit encore" : "Aucune place forte a viser";
        }
        ItemStack compass = new ItemStack(Items.COMPASS);
        compass.set(DataComponents.LODESTONE_TRACKER,
                new LodestoneTracker(Optional.of(GlobalPos.of(Level.OVERWORLD, fort.center)), false));
        compass.set(DataComponents.CUSTOM_NAME, Component.literal(fort.title()).withStyle(ChatFormatting.RED));
        if (!player.getInventory().add(compass)) {
            player.drop(compass, false);
        }
        return null;
    }

    // -- Les missiles ------------------------------------------------------------------------------

    /** Un missile de NTM dans le Monde : suivi tant qu'une place forte attend (ceux des frappes sont ecartes). */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (FORTS.isEmpty() || event.getLevel().isClientSide() || event.getLevel().dimension() != Level.OVERWORLD) {
            return;
        }
        Entity entity = event.getEntity();
        if (NtmArsenal.isMissile(entity) && !entity.entityTags().contains(Strike.TAG)) {
            MISSILES.put(entity.getUUID(), entity.position());
        }
    }

    /**
     * Les missiles suivis : en approche d'une place forte, elle se charge ;
     * disparus au-dessus d'elle, c'est un impact, et ce qui reste d'elle sera
     * compte.
     */
    private static void track(MinecraftServer server, ServerLevel world, int tick) {
        if (MISSILES.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Vec3>> iterator = MISSILES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Vec3> entry = iterator.next();
            Entity missile = world.getEntity(entry.getKey());
            if (missile != null && !missile.isRemoved()) {
                if (missile.entityTags().contains(Strike.TAG)) {
                    // Un missile d'une frappe : marque juste apres son apparition.
                    iterator.remove();
                    continue;
                }
                entry.setValue(missile.position());
                for (Fort fort : FORTS.values()) {
                    if (!fort.reward && Math.hypot(missile.getX() - fort.center.getX(),
                            missile.getZ() - fort.center.getZ()) < APPROACH) {
                        load(world, fort, tick + 1_200);
                    }
                }
                continue;
            }
            iterator.remove();
            Vec3 last = entry.getValue();
            for (Fort fort : List.copyOf(FORTS.values())) {
                if (!fort.reward && fort.covers(last.x, last.z)) {
                    impact(server, world, fort, tick);
                    break;
                }
            }
        }
    }

    private static void impact(MinecraftServer server, ServerLevel world, Fort fort, int tick) {
        fort.checks.clear();
        for (int delay : CHECKS) {
            fort.checks.add(tick + delay);
        }
        load(world, fort, tick + CHECKS[CHECKS.length - 1] + 200);
        ServerPlayer owner = server.getPlayerList().getPlayer(fort.player);
        if (owner != null) {
            FactionApi.tell(owner, "Impact sur " + Character.toLowerCase(fort.title().charAt(0))
                    + fort.title().substring(1) + " !", ChatFormatting.GOLD);
        }
        DoHy.LOGGER.info("Guerre : impact de missile sur la place forte {} (contre {})", fort.traits().de(),
                fort.playerName);
    }

    /** Charge ses chunks (et les fait tourner : un cratere nucleaire se creuse) jusqu'au tick {@code until}. */
    private static void load(ServerLevel world, Fort fort, int until) {
        if (fort.loadedUntil <= 0) {
            int radius = fort.kind().claimRadius();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    DoHyWar.TICKETS.forceChunk(world, fort.ticket(), (fort.center.getX() >> 4) + dx,
                            (fort.center.getZ() >> 4) + dz, true, true);
                }
            }
        }
        fort.loadedUntil = Math.max(fort.loadedUntil, until);
    }

    private static void unload(ServerLevel world, Fort fort) {
        if (fort.loadedUntil <= 0) {
            return;
        }
        int radius = fort.kind().claimRadius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                DoHyWar.TICKETS.forceChunk(world, fort.ticket(), (fort.center.getX() >> 4) + dx,
                        (fort.center.getZ() >> 4) + dz, false, true);
            }
        }
        fort.loadedUntil = 0;
    }

    /** Les comptes dus : ce qui reste de sa structure et des murs de sa salle forte. */
    private static void check(MinecraftServer server, ServerLevel world, Fort fort, int tick) {
        if (fort.loadedUntil > 0 && fort.loadedUntil < tick && fort.checks.isEmpty()) {
            unload(world, fort);
        }
        if (fort.reward || fort.checks.isEmpty() || fort.checks.getFirst() > tick) {
            return;
        }
        int structure = census(world, structure(fort));
        int shell = census(world, shell(fort));
        if (structure < 0 || shell < 0) {
            // Pas encore charge : on recompte un peu plus tard.
            fort.checks.set(0, tick + 20);
            load(world, fort, tick + 600);
            return;
        }
        fort.checks.removeIf(due -> due <= tick);
        fort.integrity = Math.min(100, (int) Math.round(100.0d * structure / fort.baseline));
        fort.vault = Math.min(100, (int) Math.round(100.0d * shell / fort.vaultBaseline));
        save(server);
        DoHy.LOGGER.info("Guerre : place forte {} : structure {} % ({} blocs), salle forte {} % ({} blocs)",
                fort.traits().de(), fort.integrity, structure, fort.vault, shell);
        if (fort.integrity < RAZED_AT || fort.vault < VAULT_AT) {
            raze(server, fort, "missiles");
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(fort.player);
        int worst = Math.min(fort.integrity, fort.vault);
        if (owner != null && worst <= fort.warned - 5) {
            fort.warned = worst;
            FactionApi.tell(owner, fort.title() + " : structure " + fort.integrity + " %, salle forte "
                    + fort.vault + " %. Rasee sous " + RAZED_AT + " % (" + VAULT_AT + " % pour la salle forte).",
                    ChatFormatting.YELLOW);
        }
    }

    /** Sa structure : l'emprise, du sol au sommet de ses miradors. */
    private static AABB structure(Fort fort) {
        int half = fort.kind().half();
        return new AABB(fort.center.getX() - half, fort.center.getY(), fort.center.getZ() - half,
                fort.center.getX() + half, fort.center.getY() + fort.kind().clearHeight() + ABOVE,
                fort.center.getZ() + half);
    }

    /** Les murs de sa salle forte (deux couches) et ce qu'elle contient. */
    private static AABB shell(Fort fort) {
        return new AABB(fort.coreMin.getX() - 2, fort.coreMin.getY() - 2, fort.coreMin.getZ() - 2,
                fort.coreMax.getX() + 2, fort.coreMax.getY() + 2, fort.coreMax.getZ() + 2);
    }

    /**
     * Les blocs pleins (ni air, ni liquide, ni feu) de la boite, bornes
     * comprises ; -1 si un de ses chunks n'est pas charge.
     */
    static int census(ServerLevel level, AABB box) {
        int minX = (int) Math.floor(box.minX);
        int minY = Math.max(level.getMinY(), (int) Math.floor(box.minY));
        int minZ = (int) Math.floor(box.minZ);
        int maxX = (int) Math.floor(box.maxX);
        int maxY = Math.min(level.getMaxY(), (int) Math.floor(box.maxY));
        int maxZ = (int) Math.floor(box.maxZ);
        int count = 0;
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    return -1;
                }
                int x0 = Math.max(minX, cx << 4) - (cx << 4);
                int x1 = Math.min(maxX, (cx << 4) + 15) - (cx << 4);
                int z0 = Math.max(minZ, cz << 4) - (cz << 4);
                int z1 = Math.min(maxZ, (cz << 4) + 15) - (cz << 4);
                for (int index = chunk.getSectionIndex(minY); index <= chunk.getSectionIndex(maxY); index++) {
                    if (index < 0 || index >= chunk.getSectionsCount()) {
                        continue;
                    }
                    LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir()) {
                        continue;
                    }
                    int y0 = chunk.getSectionYFromSectionIndex(index) << 4;
                    int from = Math.max(minY, y0) - y0;
                    int to = Math.min(maxY, y0 + 15) - y0;
                    for (int y = from; y <= to; y++) {
                        for (int z = z0; z <= z1; z++) {
                            for (int x = x0; x <= x1; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (!state.isAir() && state.getFluidState().isEmpty()
                                        && !state.is(BlockTags.FIRE)) {
                                    count++;
                                }
                            }
                        }
                    }
                }
            }
        }
        return count;
    }

    // -- La chute ----------------------------------------------------------------------------------

    /**
     * Ses claims tombent, ses tourelles s'eteignent ; son terrain revient dans
     * cinq minutes. Les chunks qui dorment ne sont pas charges pour autant : le
     * retour du terrain fera tomber leurs claims, et leurs tourelles
     * s'eteindront en se chargeant.
     */
    private static void fall(MinecraftServer server, Fort fort) {
        ServerLevel world = server.overworld();
        int radius = fort.kind().claimRadius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk chunk = world.getChunkSource().getChunkNow((fort.center.getX() >> 4) + dx,
                        (fort.center.getZ() >> 4) + dz);
                ClaimOwner owner = chunk == null ? ClaimOwner.NONE : chunk.getData(Factions.CLAIM.get());
                if (owner.raidable() && owner.faction().equals(fort.name)) {
                    chunk.setData(Factions.CLAIM.get(), ClaimOwner.NONE);
                    chunk.markUnsaved();
                }
            }
        }
        BaseWorks.turrets(world, fort.center, radius, false);
        FALLEN.put(fort.center, new int[] {radius, server.getTickCount() + 2 * 5 * 60 * 20 + 6_000});
        if (!BaseWorks.regen(server, fort.player, fort.name, fort.title(), fort.center)) {
            DoHy.LOGGER.warn("Guerre : la place forte {} n'a pas de releve du terrain : elle reste en place",
                    fort.traits().de());
        }
        unload(world, fort);
        fort.checks.clear();
    }

    /** Rasee (ou pillee) : elle recule, tu prends ta part. */
    private static void raze(MinecraftServer server, Fort fort, String how) {
        fall(server, fort);
        fort.how = how;
        fort.reward = true;
        save(server);
        NationTraits traits = fort.traits();
        ServerPlayer owner = server.getPlayerList().getPlayer(fort.player);
        String faction = owner != null && FactionApi.has(owner) ? FactionApi.data(owner).name() : fort.playerName;
        server.getPlayerList().broadcastSystemMessage(FactionApi.prefix().append(Component.literal(
                fort.title() + " tombe sous les coups " + EarthNations.de(faction) + ".")
                .withStyle(ChatFormatting.YELLOW)), false);
        DoHy.LOGGER.info("Guerre : place forte {} {} par {} (structure {} %, salle forte {} %)", traits.de(),
                "pillage".equals(how) ? "pillee" : "rasee", fort.playerName, fort.integrity, fort.vault);
        if (owner != null) {
            if (busy(owner)) {
                FactionApi.tell(owner, fort.title() + " est tombee : ta victoire sera comptee a la fin de l'assaut.",
                        ChatFormatting.GOLD);
            } else {
                reward(owner, fort);
            }
        }
    }

    private static void reward(ServerPlayer player, Fort fort) {
        FORTS.remove(fort.player);
        RETRY.put(fort.player, System.currentTimeMillis() + RETRY_MS);
        save(player.level().getServer());
        if (!FactionApi.has(player)) {
            FactionApi.tell(player, fort.title() + " est tombee, mais sans faction, il n'y a rien a prendre.",
                    ChatFormatting.GRAY);
            return;
        }
        NationTraits traits = fort.traits();
        boolean pillage = "pillage".equals(fort.how);
        WarStrategy.Spoils spoils = WarStrategy.razed(player, fort.nation, fort.how, fort.kind(),
                forfeit(player, fort.nation));
        int country = spoils.country();
        long total = spoils.loot() + spoils.returned();
        // Un pays pris ou rendu a deja son titre a l'ecran ; sinon, celui de la victoire.
        boolean titled = country > 0 || spoils.recovered().stream().anyMatch(name -> !name.startsWith("la mine"));
        if (!titled) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(pillage ? "PLACE FORTE PILLEE"
                    : "PLACE FORTE RASEE").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(
                    traits.verb("recule", "reculent") + " \u00B7 +" + MoneyFormat.exact(total) + " $")
                    .withStyle(ChatFormatting.YELLOW)));
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 1.0f, 1.0f);
        FactionApi.tell(player, fort.title() + (pillage ? " est pillee" : " est rasee") + " : +"
                + MoneyFormat.exact(total) + " $ a ta banque"
                + (spoils.returned() > 0L ? " (dont " + MoneyFormat.exact(spoils.returned())
                + " $ qu'on t'avait pris)" : "") + ", " + spoils.lost() + " soldats perdus pour "
                + "elle, treve de 7 jours" + (country > 0 ? ", " + EarthNations.countryName(country)
                + " est a toi (+1 claim)." : "."), ChatFormatting.GOLD);
        if (!spoils.recovered().isEmpty()) {
            FactionApi.tell(player, "Te reviennent : " + String.join(", ", spoils.recovered()) + ".",
                    ChatFormatting.GOLD);
        }
        FactionApi.tell(player, "Dans 5 minutes, elle s'efface : le terrain reprend ses droits.",
                ChatFormatting.YELLOW);
    }

    /**
     * Le pays qu'elle perd sur la carte de la Terre : le sien le plus proche de
     * tes terres parmi ceux que tu peux viser, sinon le plus petit qui lui
     * reste. 0 sans carte, ou si elle n'en a plus.
     */
    static int forfeit(ServerPlayer player, int nation) {
        if (!EarthNations.active()) {
            return 0;
        }
        MinecraftServer server = player.level().getServer();
        for (int country : EarthNations.targets(player)) {
            if (EarthNations.holder(server, country).rival() == nation) {
                return country;
            }
        }
        List<List<Integer>> holdings = EarthNations.rivalHoldings(server);
        return nation < holdings.size() && !holdings.get(nation).isEmpty() ? holdings.get(nation).getLast() : 0;
    }

    /** La guerre est finie autrement : la nation plie bagage, sans rien laisser. */
    private static void dismantle(MinecraftServer server, Fort fort, String why) {
        fall(server, fort);
        FORTS.remove(fort.player);
        save(server);
        ServerPlayer owner = server.getPlayerList().getPlayer(fort.player);
        if (owner != null) {
            FactionApi.tell(owner, fort.traits().verb("demonte", "demontent") + " sa place forte : dans 5 minutes, "
                    + "le terrain reprend ses droits.", ChatFormatting.GRAY);
        }
        DoHy.LOGGER.info("Guerre : place forte {} demontee ({})", fort.traits().de(), why);
    }

    /** Avant le reste de la guerre ({@link Sieges}) : la victoire comptee en ton absence, ou un rappel. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Fort fort = FORTS.get(player.getUUID());
        if (fort == null) {
            return;
        }
        if (fort.reward) {
            if (!busy(player)) {
                reward(player, fort);
            }
        } else {
            FactionApi.tell(player, fort.title() + " t'attend en " + coordinates(fort) + " (structure "
                    + fort.integrity + " %).", ChatFormatting.GOLD);
        }
    }

    // -- L'administration --------------------------------------------------------------------------

    /** Rase la place forte tout de suite, comme sous les missiles (essais) ; {@code null}, ou le refus. */
    static String forceRaze(ServerPlayer player) {
        Fort fort = of(player.getUUID());
        if (fort == null) {
            return "Aucune place forte debout";
        }
        raze(player.level().getServer(), fort, "missiles");
        return null;
    }

    /** Compte ce qui reste de la place forte tout de suite (essais) ; {@code null}, ou le refus. */
    static String recount(ServerPlayer player) {
        Fort fort = of(player.getUUID());
        if (fort == null) {
            return "Aucune place forte debout";
        }
        MinecraftServer server = player.level().getServer();
        impact(server, server.overworld(), fort, server.getTickCount());
        return null;
    }

    // -- Le disque ---------------------------------------------------------------------------------

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("dohy").resolve("places-fortes.json");
    }

    private static JsonArray pos(BlockPos pos) {
        JsonArray out = new JsonArray();
        out.add(pos.getX());
        out.add(pos.getY());
        out.add(pos.getZ());
        return out;
    }

    private static BlockPos pos(JsonElement element) {
        JsonArray array = element.getAsJsonArray();
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }

    private static void save(MinecraftServer server) {
        if (!loaded) {
            return;
        }
        JsonArray list = new JsonArray();
        for (Fort fort : FORTS.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("player", fort.player.toString());
            entry.addProperty("player_name", fort.playerName);
            entry.addProperty("nation", fort.nation);
            entry.addProperty("name", fort.name);
            entry.addProperty("tier", fort.tier);
            entry.addProperty("style", fort.style);
            entry.add("center", pos(fort.center));
            entry.add("core_min", pos(fort.coreMin));
            entry.add("core_max", pos(fort.coreMax));
            entry.addProperty("baseline", fort.baseline);
            entry.addProperty("vault_baseline", fort.vaultBaseline);
            entry.addProperty("integrity", fort.integrity);
            entry.addProperty("vault", fort.vault);
            entry.addProperty("created", fort.created);
            entry.addProperty("reward", fort.reward);
            entry.addProperty("how", fort.how);
            list.add(entry);
        }
        Path file = file(server);
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling("places-fortes.json.tmp");
            Files.writeString(temporary, list.toString());
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            DoHy.LOGGER.warn("Guerre : places fortes non ecrites", error);
        }
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        FORTS.clear();
        MISSILES.clear();
        RETRY.clear();
        FALLEN.clear();
        job = null;
        loaded = true;
        Path file = file(event.getServer());
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (JsonElement element : JsonParser.parseString(Files.readString(file)).getAsJsonArray()) {
                JsonObject entry = element.getAsJsonObject();
                Fort fort = new Fort();
                fort.player = UUID.fromString(entry.get("player").getAsString());
                fort.playerName = entry.get("player_name").getAsString();
                fort.nation = entry.get("nation").getAsInt();
                fort.name = entry.get("name").getAsString();
                fort.tier = entry.get("tier").getAsInt();
                fort.style = entry.get("style").getAsString();
                fort.center = pos(entry.get("center"));
                fort.coreMin = pos(entry.get("core_min"));
                fort.coreMax = pos(entry.get("core_max"));
                fort.baseline = Math.max(1, entry.get("baseline").getAsInt());
                fort.vaultBaseline = Math.max(1, entry.get("vault_baseline").getAsInt());
                fort.integrity = entry.get("integrity").getAsInt();
                fort.vault = entry.get("vault").getAsInt();
                fort.created = entry.get("created").getAsLong();
                fort.reward = entry.get("reward").getAsBoolean();
                fort.how = entry.get("how").getAsString();
                FORTS.put(fort.player, fort);
            }
        } catch (IOException | RuntimeException error) {
            DoHy.LOGGER.warn("Guerre : places fortes illisibles", error);
        }
        if (!FORTS.isEmpty()) {
            DoHy.LOGGER.info("Guerre : {} place(s) forte(s) de nations en place", FORTS.size());
        }
    }

    /** Avant {@code EnemyBaseRegen} : le releve du terrain d'un chantier fini a l'arret doit etre ecrit. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        if (job != null && job.works != null) {
            // Un chantier entame est fini d'un coup : une base a moitie batie, sans releve, resterait la.
            try {
                while (!job.works.work(1_000_000_000L)) {
                    Thread.onSpinWait();
                }
                finish(server, job, null);
            } catch (RuntimeException failure) {
                DoHy.LOGGER.error("Guerre : le chantier de la place forte n'a pas pu finir a l'arret", failure);
            }
        }
        if (job != null) {
            release(server.overworld(), job);
            job = null;
        }
        save(server);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        FORTS.clear();
        MISSILES.clear();
        RETRY.clear();
        FALLEN.clear();
        job = null;
        loaded = false;
    }
}
