package fr.atraxterra.dohy.faction.unesco;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.event.EventRun;
import fr.atraxterra.dohy.util.MoneyFormat;
import fr.atraxterra.dohy.util.RealClock;
import fr.atraxterra.dohy.world.PlacedBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Le programme UNESCO et les merveilles du monde (NationsGlory, « UNESCO et Merveilles »).
 *
 * <p><b>L'UNESCO.</b> Sur NationsGlory, un pays depose un dossier pour un de ses batiments : d'une
 * taille raisonnable, detaille (facade et interieur), coherent avec son style, interessant pour le
 * jeu de role, une fois sa recherche « Generale » au niveau 10. Inscrit, il rapporte une subvention
 * (12 500 $ au plus par pays), une protection contre les missiles et les destructions (sauf
 * antimatiere et matiere rouge), un warp devant lui, un PNJ, et 200 de puissance. Cinq batiments au
 * plus ; un grand batiment peut compter pour cinq. Pas de F home dedans.
 *
 * <p>En solo, l'equipe qui juge le dossier est le mod : une plaque UNESCO posee dans le batiment
 * l'arpente (33 x 33 blocs, de 6 sous la plaque a 34 au-dessus) et le note sur 100 :
 * <ul>
 *   <li><b>taille</b> (25) : les blocs construits, ceux qu'un joueur a poses ou qui ne viennent pas
 *       du terrain ; 1 500 pour tout, 300 au moins ;</li>
 *   <li><b>details</b> (20) : escaliers, dalles, murets, barrieres, vitres, portes, trappes,
 *       lumieres, bannieres... 15 % des blocs pour tout ;</li>
 *   <li><b>interieur</b> (15) : l'air abrite sous un toit, 40 % des blocs pour tout ;</li>
 *   <li><b>style</b> (25) : une palette tenue (les six blocs les plus poses font de 55 a 92 %) et
 *       assez riche (20 sortes pour tout) ;</li>
 *   <li><b>fonctions</b> (15) : ce qui fait vivre le lieu (coffres, fours, lits, lutrins, machines...),
 *       six sortes pour tout.</li>
 * </ul>
 * Il faut 60. Le monument compte pour un emplacement par tranche de 2 500 blocs (cinq au plus), sur
 * cinq emplacements par nation, et touche 2 500 $ par emplacement, versees a la banque nationale.
 * Sa zone ne se casse plus aux explosions (sauf celles des armes de NTM) ni par d'autres que son
 * maitre ; on ne peut pas y poser de F home ; il compte a la notation (Territoire, un point par
 * emplacement) ; un warp et un guide sont a la plaque.
 *
 * <p><b>Les merveilles.</b> Une merveille du monde par serveur, elue par le conseil de l'ONU : ici,
 * chaque mois, le 1er a 21 h, entre les monuments inscrits et ceux des nations rivales. La merveille
 * rapporte 25 000 $ a sa nation, trois points de plus a la notation et un halo sur sa plaque.
 */
@EventBusSubscriber
public final class Unesco {

    public static final int MAX_WEIGHT = 5;
    public static final long GRANT = 2_500L;
    public static final int PASS = 60;
    public static final int MIN_BLOCKS = 300;
    public static final int LEVEL = 10;
    public static final long WONDER_BONUS = 25_000L;
    public static final int WONDER_POINTS = 3;
    static final int RADIUS = 16;
    static final int BELOW = 6;
    static final int ABOVE = 34;
    static final long COOLDOWN = 60L;
    static final int ELECTION_HOUR = 21;
    static final String GUIDE_TAG = "dohy_unesco_guide";
    static final String GUIDE_PREFIX = "dohy_unesco:";

    private static final String[] WORKS = {"Le Grand Phare", "La Citadelle", "Les Jardins suspendus", "Le Colosse",
            "Le Mausolee", "La Tour d'ivoire", "Le Temple du Soleil", "L'Observatoire", "La Grande Bibliotheque",
            "Le Palais des Glaces"};
    private static final Map<UUID, Long> LAST_SUBMIT = new HashMap<>();

    private Unesco() {
    }

    public static UnescoData data(MinecraftServer server) {
        return server.overworld().getData(DoHyUnesco.DATA.get());
    }

    static void save(MinecraftServer server, UnescoData data) {
        server.overworld().setData(DoHyUnesco.DATA.get(), data.touched());
    }

    static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    static void tell(ServerPlayer player, String text, ChatFormatting color) {
        player.sendSystemMessage(Component.literal("[UNESCO] " + text).withStyle(color));
    }

    static ServerLevel level(MinecraftServer server, String dimension) {
        try {
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension)));
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    // -- L'arpentage ----------------------------------------------------------------------------

    /** Ce que la plaque a releve du batiment, et la note qu'elle en tire. */
    public record Survey(int built, int details, int roofed, int distinct, double topShare, int functions,
                         int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                         int size, int detail, int interior, int style, int function) {
        public int total() {
            return size + detail + interior + style + function;
        }

        /** Un emplacement par tranche de 2 500 blocs, cinq au plus. */
        public int weight() {
            return Math.min(MAX_WEIGHT, 1 + built / 2500);
        }

        public List<Integer> criteria() {
            return List.of(size, detail, interior, style, function);
        }
    }

    /** Le terrain : ce qui n'est pas construit, a moins qu'un joueur ne l'ait pose. */
    static boolean natural(BlockState state) {
        Block block = state.getBlock();
        return !state.getFluidState().isEmpty() || state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.SAND) || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.LOGS) || state.is(BlockTags.REPLACEABLE)
                || state.is(BlockTags.ICE) || state.is(net.neoforged.neoforge.common.Tags.Blocks.ORES)
                || block == Blocks.GRAVEL
                || block == Blocks.CLAY || block == Blocks.BEDROCK || block == Blocks.SANDSTONE
                || block == Blocks.RED_SANDSTONE || block == Blocks.SNOW_BLOCK || block == Blocks.SNOW
                || block == Blocks.MOSS_BLOCK || block == Blocks.MUD;
    }

    /** Un detail : ce qui sculpte une facade ou meuble un interieur. */
    static boolean detail(BlockState state) {
        return state.is(BlockTags.STAIRS) || state.is(BlockTags.SLABS) || state.is(BlockTags.WALLS)
                || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.DOORS) || state.is(BlockTags.BUTTONS) || state.is(BlockTags.PRESSURE_PLATES)
                || state.is(BlockTags.BANNERS) || state.is(BlockTags.CANDLES) || state.is(BlockTags.FLOWER_POTS)
                || state.is(BlockTags.ALL_SIGNS) || state.is(BlockTags.BEDS) || state.is(BlockTags.WOOL_CARPETS)
                || state.getBlock() instanceof IronBarsBlock || state.getLightEmission() > 0;
    }

    /** Une fonction : ce qui fait vivre le lieu. */
    static boolean function(BlockState state) {
        Block block = state.getBlock();
        return state.hasBlockEntity() || block == Blocks.CRAFTING_TABLE || block == Blocks.BOOKSHELF
                || block == Blocks.CARTOGRAPHY_TABLE || block == Blocks.FLETCHING_TABLE
                || block == Blocks.SMITHING_TABLE || block == Blocks.LOOM || block == Blocks.STONECUTTER
                || block == Blocks.GRINDSTONE || block == Blocks.COMPOSTER || block == Blocks.CAULDRON
                || block == Blocks.NOTE_BLOCK || state.is(BlockTags.ANVIL);
    }

    /** Arpente le batiment autour de la plaque. */
    public static Survey survey(ServerLevel level, BlockPos plaque) {
        int width = 2 * RADIUS + 1;
        int height = BELOW + ABOVE + 1;
        BitSet built = new BitSet(width * width * height);
        Map<Block, Integer> counts = new HashMap<>();
        Set<Block> functions = new HashSet<>();
        int total = 0;
        int details = 0;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int x = plaque.getX() + dx;
                int z = plaque.getZ() + dz;
                if (!level.isLoaded(pos.set(x, plaque.getY(), z))) {
                    continue;
                }
                for (int dy = -BELOW; dy <= ABOVE; dy++) {
                    int y = plaque.getY() + dy;
                    if (y < level.getMinY() || y > level.getMaxY()) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || (natural(state) && !PlacedBlocks.isPlayerPlaced(level, pos))) {
                        continue;
                    }
                    built.set(index(dx, dy, dz));
                    total++;
                    counts.merge(state.getBlock(), 1, Integer::sum);
                    if (detail(state)) {
                        details++;
                    }
                    if (function(state) && !state.is(DoHyUnesco.PLAQUE.get())) {
                        functions.add(state.getBlock());
                    }
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
                }
            }
        }
        int roofed = 0;
        if (total > 0) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int roof = Integer.MIN_VALUE;
                    for (int y = maxY; y >= minY; y--) {
                        int index = index(x - plaque.getX(), y - plaque.getY(), z - plaque.getZ());
                        if (built.get(index)) {
                            roof = y;
                        } else if (roof != Integer.MIN_VALUE && roof - y <= 8
                                && level.getBlockState(pos.set(x, y, z)).isAir()) {
                            roofed++;
                        }
                    }
                }
            }
        }
        List<Integer> sorted = new ArrayList<>(counts.values());
        sorted.sort(Comparator.reverseOrder());
        int top = 0;
        for (int i = 0; i < Math.min(6, sorted.size()); i++) {
            top += sorted.get(i);
        }
        double share = total == 0 ? 0.0d : (double) top / total;
        double coherence;
        if (share < 0.40d) {
            coherence = 0.4d;
        } else if (share < 0.55d) {
            coherence = 0.4d + (share - 0.40d) / 0.15d * 0.6d;
        } else if (share <= 0.92d) {
            coherence = 1.0d;
        } else {
            coherence = Math.max(0.4d, 1.0d - (share - 0.92d) / 0.08d * 0.6d);
        }
        int size = (int) Math.round(25.0d * Math.min(1.0d, total / 1500.0d));
        int detail = (int) Math.round(20.0d * Math.min(1.0d, details / Math.max(1.0d, total * 0.15d)));
        int interior = (int) Math.round(15.0d * Math.min(1.0d, roofed / Math.max(1.0d, total * 0.40d)));
        int style = total == 0 ? 0 : (int) Math.round(15.0d * coherence)
                + (int) Math.round(10.0d * Math.min(1.0d, counts.size() / 20.0d));
        int function = (int) Math.round(15.0d * Math.min(1.0d, functions.size() / 6.0d));
        if (total == 0) {
            minX = minY = minZ = maxX = maxY = maxZ = 0;
        }
        return new Survey(total, details, roofed, counts.size(), share, functions.size(), minX, minY, minZ, maxX,
                maxY, maxZ, size, detail, interior, style, function);
    }

    private static int index(int dx, int dy, int dz) {
        int width = 2 * RADIUS + 1;
        return (dx + RADIUS) + (dz + RADIUS) * width + (dy + BELOW) * width * width;
    }

    // -- Le dossier -----------------------------------------------------------------------------

    static int used(UnescoData data, UUID owner) {
        int used = 0;
        for (UnescoData.Monument monument : data.of(owner)) {
            used += monument.weight;
        }
        return used;
    }

    /** Ce qu'il manque pour deposer un dossier a cette plaque, ou {@code null}. */
    public static String cannotSubmit(ServerPlayer player, BlockPos plaque) {
        FactionData nation = FactionApi.data(player);
        if (!nation.exists()) {
            return "Il faut une nation pour inscrire un monument.";
        }
        int level = fr.atraxterra.dohy.faction.bcc.Bccs.nationLevel(nation);
        if (level < LEVEL) {
            return "Ta nation doit atteindre le niveau " + LEVEL + " (" + level + " paliers d'arbre).";
        }
        if (!FactionApi.claimAt(player.level(), plaque).ownedBy(player.getUUID())) {
            return "La plaque doit etre dans un de tes claims.";
        }
        UnescoData data = data(player.level().getServer());
        if (data.at(FactionApi.dimension(player.level()), plaque) != null) {
            return "Ce monument est deja inscrit.";
        }
        if (used(data, player.getUUID()) >= MAX_WEIGHT) {
            return "Tes " + MAX_WEIGHT + " emplacements UNESCO sont pris.";
        }
        long last = LAST_SUBMIT.getOrDefault(player.getUUID(), 0L);
        if (now() - last < COOLDOWN) {
            return "Le jury examine encore ton dernier dossier : reviens dans " + (COOLDOWN - (now() - last)) + " s.";
        }
        return null;
    }

    /** Depose le dossier : arpentage, note, et inscription a 60. Rend le refus, ou {@code null}. */
    public static String submit(ServerPlayer player, BlockPos plaque) {
        String refusal = cannotSubmit(player, plaque);
        if (refusal != null) {
            return refusal;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (!(level.getBlockEntity(plaque) instanceof UnescoPlaqueBlockEntity entity)) {
            return "La plaque a disparu.";
        }
        LAST_SUBMIT.put(player.getUUID(), now());
        Survey survey = survey(level, plaque);
        tell(player, "Dossier examine : " + survey.built() + " blocs construits, " + survey.details() + " details, "
                + survey.roofed() + " d'interieur, " + survey.distinct() + " sortes, " + survey.functions()
                + " fonctions.", ChatFormatting.GRAY);
        tell(player, "Taille " + survey.size() + "/25 · details " + survey.detail() + "/20 · interieur "
                + survey.interior() + "/15 · style " + survey.style() + "/25 · fonctions " + survey.function()
                + "/15 : " + survey.total() + "/100.", ChatFormatting.YELLOW);
        if (survey.built() < MIN_BLOCKS) {
            return "Refuse : trop petit (" + survey.built() + " blocs construits, il en faut " + MIN_BLOCKS + ").";
        }
        if (survey.total() < PASS) {
            return "Refuse : " + survey.total() + "/100, il faut " + PASS + ". " + advice(survey);
        }
        MinecraftServer server = level.getServer();
        UnescoData data = data(server);
        String dimension = FactionApi.dimension(level);
        for (UnescoData.Monument other : data.monuments()) {
            if (other.overlaps(dimension, survey.minX(), survey.minY(), survey.minZ(), survey.maxX(), survey.maxY(),
                    survey.maxZ())) {
                return "Refuse : ce batiment empiete sur " + other.name + ", deja inscrit.";
            }
        }
        int weight = Math.min(survey.weight(), MAX_WEIGHT - used(data, player.getUUID()));
        long grant = GRANT * weight;
        FactionData nation = FactionApi.data(player);
        String name = entity.name().isEmpty() ? "Monument de " + nation.name() : entity.name();
        UnescoData.Monument monument = new UnescoData.Monument(player.getUUID(), player.getGameProfile().name(),
                nation.name(), UUID.randomUUID().toString(), name, dimension, plaque.asLong(),
                List.of(survey.minX(), survey.minY(), survey.minZ(), survey.maxX(), survey.maxY(), survey.maxZ()),
                survey.criteria(), survey.total(), weight, grant, now(), survey.built(), false);
        data.monuments.add(monument);
        save(server, data);
        FactionApi.creditBank(player, grant, "subvention UNESCO");
        FactionApi.stat(player, "unesco:inscribed", 1L);
        tell(player, name + " est inscrit au patrimoine mondial : " + survey.total() + "/100, " + weight
                + " emplacement" + (weight > 1 ? "s" : "") + ", " + MoneyFormat.exact(grant)
                + " $ de subvention a la banque nationale. Sa zone est protegee.", ChatFormatting.GREEN);
        level.playSound(null, plaque, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8f, 1.0f);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, plaque.getX() + 0.5d, plaque.getY() + 1.2d,
                plaque.getZ() + 0.5d, 40, 0.4d, 0.4d, 0.4d, 0.2d);
        DoHy.LOGGER.info("UNESCO : {} inscrit ({}/100, {} emplacement(s))", name, survey.total(), weight);
        return null;
    }

    /** Ce qui ferait gagner le plus de points. */
    static String advice(Survey survey) {
        String best;
        double gap = 25.0d - survey.size();
        best = "plus grand";
        if (20.0d - survey.detail() > gap) {
            gap = 20.0d - survey.detail();
            best = "plus de details (escaliers, dalles, vitres, lumieres...)";
        }
        if (15.0d - survey.interior() > gap) {
            gap = 15.0d - survey.interior();
            best = "un vrai interieur, sous un toit";
        }
        if (25.0d - survey.style() > gap) {
            gap = 25.0d - survey.style();
            best = survey.topShare() > 0.92d ? "plus de sortes de blocs" : "une palette plus tenue";
        }
        if (15.0d - survey.function() > gap) {
            best = "plus de fonctions (coffres, fours, lits, lutrins...)";
        }
        return "Le plus utile : " + best + ".";
    }

    /** Retire un monument du programme. Rend le refus, ou {@code null}. */
    public static String delist(ServerPlayer player, String id) {
        MinecraftServer server = player.level().getServer();
        UnescoData data = data(server);
        UnescoData.Monument monument = data.byId(id);
        if (monument == null || !monument.owner.equals(player.getUUID())) {
            return "Ce monument n'est pas a toi.";
        }
        remove(server, data, monument);
        tell(player, monument.name + " quitte le programme UNESCO (sa subvention reste acquise).",
                ChatFormatting.YELLOW);
        return null;
    }

    private static void remove(MinecraftServer server, UnescoData data, UnescoData.Monument monument) {
        data.monuments.remove(monument);
        if (data.wonder != null && monument.id.equals(data.wonder.monument())) {
            data.wonder = new UnescoData.Wonder(data.wonder.name() + " (detruite)", data.wonder.nation(), "",
                    "", data.wonder.month());
        }
        save(server, data);
        dismissGuide(server, monument);
    }

    /** La plaque est cassee : son monument quitte le programme. */
    static void plaqueRemoved(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();
        UnescoData data = data(server);
        UnescoData.Monument monument = data.at(FactionApi.dimension(level), pos);
        if (monument == null) {
            return;
        }
        remove(server, data, monument);
        ServerPlayer owner = server.getPlayerList().getPlayer(monument.owner);
        if (owner != null) {
            tell(owner, "La plaque de " + monument.name + " est cassee : il quitte le programme UNESCO.",
                    ChatFormatting.RED);
        }
    }

    /** Un autre que le maitre clique la plaque. */
    static void describe(ServerPlayer player, ServerLevel level, BlockPos pos, UnescoPlaqueBlockEntity plaque) {
        UnescoData.Monument monument = data(level.getServer()).at(FactionApi.dimension(level), pos);
        if (monument == null) {
            tell(player, "Plaque UNESCO de " + plaque.ownerName() + " : pas encore de monument inscrit.",
                    ChatFormatting.GOLD);
            return;
        }
        tell(player, monument.name + ", patrimoine mondial de " + monument.nation + " : " + monument.score
                + "/100." + (isWonder(level.getServer(), monument) ? " Merveille du monde !" : ""),
                ChatFormatting.GOLD);
    }

    /** Le warp du monument : devant sa plaque. Rend le refus, ou {@code null}. */
    public static String teleport(ServerPlayer player, String id) {
        MinecraftServer server = player.level().getServer();
        UnescoData.Monument monument = data(server).byId(id);
        if (monument == null) {
            return "Ce monument n'est plus inscrit.";
        }
        ServerLevel level = level(server, monument.dimension);
        if (level == null) {
            return "Le monde de ce monument est introuvable.";
        }
        BlockPos plaque = monument.plaque();
        BlockState state = level.getBlockState(plaque);
        Direction front = state.getBlock() instanceof UnescoPlaqueBlock ? state.getValue(UnescoPlaqueBlock.FACING)
                : Direction.SOUTH;
        BlockPos spot = plaque.relative(front, 2);
        player.closeContainer();
        player.teleport(new TeleportTransition(level, Vec3.atBottomCenterOf(spot), Vec3.ZERO,
                front.getOpposite().toYRot(), 0.0f, TeleportTransition.DO_NOTHING));
        tell(player, "Bienvenue a " + monument.name + ".", ChatFormatting.GOLD);
        return null;
    }

    /** Un guide devant la plaque, ou plus de guide. Rend le refus, ou {@code null}. */
    public static String toggleGuide(ServerPlayer player, String id) {
        MinecraftServer server = player.level().getServer();
        UnescoData data = data(server);
        UnescoData.Monument monument = data.byId(id);
        if (monument == null || !monument.owner.equals(player.getUUID())) {
            return "Ce monument n'est pas a toi.";
        }
        monument.guide = !monument.guide;
        save(server, data);
        if (monument.guide) {
            ServerLevel level = level(server, monument.dimension);
            if (level == null || !level.isLoaded(monument.plaque())) {
                return "Le guide viendra quand le monument sera charge.";
            }
            summonGuide(level, monument);
            tell(player, "Un guide accueille les visiteurs de " + monument.name + ".", ChatFormatting.GREEN);
        } else {
            dismissGuide(server, monument);
            tell(player, "Le guide de " + monument.name + " s'en va.", ChatFormatting.YELLOW);
        }
        return null;
    }

    static void summonGuide(ServerLevel level, UnescoData.Monument monument) {
        BlockPos plaque = monument.plaque();
        BlockState state = level.getBlockState(plaque);
        Direction front = state.getBlock() instanceof UnescoPlaqueBlock ? state.getValue(UnescoPlaqueBlock.FACING)
                : Direction.SOUTH;
        BlockPos spot = plaque.relative(front).relative(front.getClockWise());
        Mob guide = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        if (guide == null) {
            return;
        }
        guide.snapTo(spot.getX() + 0.5d, spot.getY(), spot.getZ() + 0.5d, front.toYRot(), 0.0f);
        guide.setNoAi(true);
        guide.setInvulnerable(true);
        guide.setSilent(true);
        guide.setPersistenceRequired();
        guide.addTag(GUIDE_TAG);
        guide.addTag(GUIDE_PREFIX + monument.id);
        guide.setCustomName(Component.literal("Guide · " + monument.name));
        guide.setCustomNameVisible(true);
        level.addFreshEntity(guide);
    }

    static void dismissGuide(MinecraftServer server, UnescoData.Monument monument) {
        ServerLevel level = level(server, monument.dimension);
        if (level == null) {
            return;
        }
        for (Entity entity : level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(monument.plaque())
                .inflate(8.0d), entity -> entity.entityTags().contains(GUIDE_PREFIX + monument.id))) {
            entity.discard();
        }
    }

    // -- Ce que ca rapporte ---------------------------------------------------------------------

    /** Le monument dont la zone tient ce bloc, ou {@code null}. */
    public static UnescoData.Monument inMonument(Level level, BlockPos pos) {
        if (level.getServer() == null) {
            return null;
        }
        String dimension = FactionApi.dimension(level);
        for (UnescoData.Monument monument : data(level.getServer()).monuments()) {
            if (monument.contains(dimension, pos)) {
                return monument;
            }
        }
        return null;
    }

    static boolean isWonder(MinecraftServer server, UnescoData.Monument monument) {
        UnescoData.Wonder wonder = data(server).wonder();
        return wonder != null && monument.id.equals(wonder.monument());
    }

    /** Les points de prestige a la notation : un par emplacement, trois de plus pour la merveille. */
    public static int prestige(ServerPlayer player) {
        UnescoData data = data(player.level().getServer());
        int points = Math.min(MAX_WEIGHT, used(data, player.getUUID()));
        UnescoData.Wonder wonder = data.wonder();
        if (wonder != null && wonder.owner().equals(player.getUUID().toString())) {
            points += WONDER_POINTS;
        }
        return points;
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<UnescoData.Monument> monuments = data(level.getServer()).monuments();
        if (monuments.isEmpty()) {
            return;
        }
        String dimension = FactionApi.dimension(level);
        event.getAffectedBlocks().removeIf(pos -> {
            for (UnescoData.Monument monument : monuments) {
                if (monument.contains(dimension, pos)) {
                    return true;
                }
            }
            return false;
        });
    }

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()) {
            return;
        }
        UnescoData.Monument monument = inMonument(player.level(), event.getPos());
        if (monument != null && !monument.owner.equals(player.getUUID())) {
            event.setCanceled(true);
            tell(player, monument.name + " est classe au patrimoine mondial : seul son maitre y touche.",
                    ChatFormatting.RED);
        }
    }

    // -- Les guides -----------------------------------------------------------------------------

    /** Un guide dont le monument n'est plus inscrit, ou n'a plus de guide, ne revient pas. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(event.getLevel() instanceof ServerLevel level) || !entity.entityTags().contains(GUIDE_TAG)) {
            return;
        }
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(GUIDE_PREFIX)) {
                UnescoData.Monument monument = data(level.getServer()).byId(tag.substring(GUIDE_PREFIX.length()));
                if (monument == null || !monument.guide) {
                    event.setCanceled(true);
                }
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!target.entityTags().contains(GUIDE_TAG) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        for (String tag : target.entityTags()) {
            if (tag.startsWith(GUIDE_PREFIX)) {
                UnescoData.Monument monument = data(player.level().getServer())
                        .byId(tag.substring(GUIDE_PREFIX.length()));
                if (monument != null) {
                    ZonedDateTime when = Instant.ofEpochSecond(monument.inscribed).atZone(RealClock.zone());
                    tell(player, "Bienvenue a " + monument.name + ", tresor de " + monument.nation
                            + ", inscrit le " + when.getDayOfMonth() + "/" + when.getMonthValue() + "/"
                            + when.getYear() + " avec " + monument.score + "/100."
                            + (isWonder(player.level().getServer(), monument) ? " C'est la merveille du monde !"
                            : ""), ChatFormatting.GOLD);
                }
                return;
            }
        }
    }

    // -- Les merveilles -------------------------------------------------------------------------

    /** Le mois d'une date : annee x 100 + mois. */
    static int month(ZonedDateTime time) {
        return time.getYear() * 100 + time.getMonthValue();
    }

    /** L'election de ce mois : le 1er, a 21 h. */
    static ZonedDateTime electionOf(ZonedDateTime time) {
        return time.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).withHour(ELECTION_HOUR);
    }

    /** La prochaine election. */
    public static ZonedDateTime nextElection() {
        ZonedDateTime now = ZonedDateTime.now(RealClock.zone());
        ZonedDateTime election = electionOf(now);
        return election.isAfter(now) ? election : electionOf(now.plusMonths(1L).withDayOfMonth(1));
    }

    /** Les candidats des nations rivales ce mois-ci : un nom d'oeuvre, une nation, une note. */
    record Rival(String name, String nation, int score) {
    }

    static List<Rival> rivals(int month) {
        RandomSource random = RandomSource.create(month * 7919L + 13L);
        List<EventRun.Rival> pool = new ArrayList<>(EventRun.rivalPool());
        List<Rival> rivals = new ArrayList<>();
        for (int i = 0; i < 5 && !pool.isEmpty(); i++) {
            EventRun.Rival nation = pool.remove(random.nextInt(pool.size()));
            rivals.add(new Rival(WORKS[random.nextInt(WORKS.length)], nation.name(), 62 + random.nextInt(31)));
        }
        return rivals;
    }

    /** Le conseil vote : la note de chaque candidat, plus son poids dans les couloirs (0 a 12). */
    static void elect(MinecraftServer server, UnescoData data, int month) {
        RandomSource random = RandomSource.create(month * 31L + 7L);
        String bestName = null;
        String bestNation = "";
        UnescoData.Monument bestMonument = null;
        int bestVote = Integer.MIN_VALUE;
        for (Rival rival : rivals(month)) {
            int vote = rival.score() + random.nextInt(13);
            if (vote > bestVote) {
                bestVote = vote;
                bestName = rival.name();
                bestNation = rival.nation();
                bestMonument = null;
            }
        }
        for (UnescoData.Monument monument : data.monuments()) {
            int vote = monument.score + random.nextInt(13);
            if (vote > bestVote) {
                bestVote = vote;
                bestName = monument.name;
                bestNation = monument.nation;
                bestMonument = monument;
            }
        }
        data.lastElection = month;
        if (bestName == null) {
            return;
        }
        data.wonder = new UnescoData.Wonder(bestName, bestNation,
                bestMonument == null ? "" : bestMonument.owner.toString(),
                bestMonument == null ? "" : bestMonument.id, month);
        data.history.add(0, data.wonder);
        while (data.history.size() > 6) {
            data.history.remove(data.history.size() - 1);
        }
        server.getPlayerList().broadcastSystemMessage(Component.literal("[UNESCO] Le conseil de l'ONU elit la "
                + "merveille du monde du mois : " + bestName + " (" + bestNation + ").").withStyle(ChatFormatting.GOLD),
                false);
        if (bestMonument != null) {
            ServerPlayer owner = server.getPlayerList().getPlayer(bestMonument.owner);
            if (owner != null) {
                FactionApi.creditBank(owner, WONDER_BONUS, "merveille du monde");
                tell(owner, bestMonument.name + " est la merveille du monde ! +" + MoneyFormat.exact(WONDER_BONUS)
                        + " $ a la banque nationale.", ChatFormatting.GREEN);
            } else {
                data.owed.merge(bestMonument.owner, WONDER_BONUS, Long::sum);
            }
        }
        DoHy.LOGGER.info("UNESCO : merveille du mois {} : {} ({})", month, bestName, bestNation);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        if (tick % 40 == 17) {
            halo(server);
        }
        if (tick % 400 != 23) {
            return;
        }
        UnescoData data = data(server);
        ZonedDateTime now = ZonedDateTime.now(RealClock.zone());
        int month = month(now);
        if (data.lastElection == 0 || data.lastElection < month && !now.isBefore(electionOf(now))) {
            elect(server, data, data.lastElection == 0 && now.isBefore(electionOf(now))
                    ? month(now.minusMonths(1L)) : month);
            save(server, data);
        }
        // Les guides des monuments charges, s'ils manquent.
        for (UnescoData.Monument monument : data.monuments()) {
            if (!monument.guide) {
                continue;
            }
            ServerLevel level = level(server, monument.dimension);
            if (level == null || !level.isLoaded(monument.plaque())) {
                continue;
            }
            boolean present = !level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(monument.plaque())
                    .inflate(8.0d), entity -> entity.entityTags().contains(GUIDE_PREFIX + monument.id)).isEmpty();
            if (!present) {
                summonGuide(level, monument);
            }
        }
    }

    /** Le halo de la merveille du monde, quand sa plaque est chargee. */
    static void halo(MinecraftServer server) {
        UnescoData data = data(server);
        UnescoData.Wonder wonder = data.wonder();
        if (wonder == null || !wonder.player()) {
            return;
        }
        UnescoData.Monument monument = data.byId(wonder.monument());
        ServerLevel level = monument == null ? null : level(server, monument.dimension);
        if (level == null || !level.isLoaded(monument.plaque())) {
            return;
        }
        BlockPos plaque = monument.plaque();
        level.sendParticles(ParticleTypes.END_ROD, plaque.getX() + 0.5d, plaque.getY() + 1.4d, plaque.getZ() + 0.5d,
                3, 0.3d, 0.4d, 0.3d, 0.01d);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        UnescoData data = data(server);
        Long owed = data.owed.remove(player.getUUID());
        if (owed != null && owed > 0L) {
            save(server, data);
            FactionApi.creditBank(player, owed, "merveille du monde");
            tell(player, "Ton monument a ete elu merveille du monde en ton absence : +" + MoneyFormat.exact(owed)
                    + " $ a la banque nationale.", ChatFormatting.GREEN);
        }
    }

    /** L'election tout de suite (commande d'operateur). */
    public static String electNow(MinecraftServer server) {
        UnescoData data = data(server);
        elect(server, data, month(ZonedDateTime.now(RealClock.zone())));
        save(server, data);
        return null;
    }

    /** Les monuments d'un joueur, tries par note. */
    public static List<UnescoData.Monument> monuments(ServerPlayer player) {
        List<UnescoData.Monument> mine = data(player.level().getServer()).of(player.getUUID());
        mine.sort(Comparator.comparingInt(UnescoData.Monument::score).reversed());
        return mine;
    }
}
