package fr.atraxterra.dohy.faction.absence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.Factions;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * La base qui tourne sans toi : les claims d'une faction restent charges, et
 * tout y travaille — machines, fours, entonnoirs, champs, golem — que le joueur
 * soit loin, hors ligne ou que le serveur tourne seul la nuit.
 *
 * <p>Tous les claims, jusqu'a {@link #CAP} chunks ; au-dela, ceux qui ont le
 * plus de machines et de coffres (les blocs a donnees du chunk, comptes chaque
 * fois que le chunk est charge pendant que le joueur est la). Les chunks sont
 * tenus par un ticket de NeoForge qui fait tourner le chunk comme si un joueur
 * etait a cote : entites, blocs a donnees et ticks aleatoires.
 *
 * <p>La liste vit sur le monde principal, pas sur le joueur : un joueur hors
 * ligne n'est pas charge, et c'est justement la qu'il faut savoir quoi tenir. Au
 * demarrage du serveur, les anciens tickets sont jetes et la liste est reposee.
 * Une base dont le joueur n'est pas revenu depuis {@link #ASLEEP_DAYS} jours
 * s'endort ; elle repart a sa connexion.
 */
public final class KeptBases {

    /** Les chunks tenus au plus, par faction (choix du joueur : 64). */
    public static final int CAP = 64;
    /** Une base dont le joueur n'est pas revenu depuis ce delai cesse d'etre tenue. */
    public static final int ASLEEP_DAYS = 30;

    /** Un claim : sa dimension, sa place, ses machines et coffres a la derniere visite, et s'il est tenu. */
    public record Spot(String dimension, int x, int z, int boxes, boolean kept) {
        static final Codec<Spot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("dimension").forGetter(Spot::dimension),
                Codec.INT.fieldOf("x").forGetter(Spot::x),
                Codec.INT.fieldOf("z").forGetter(Spot::z),
                Codec.INT.optionalFieldOf("boxes", 0).forGetter(Spot::boxes),
                Codec.BOOL.optionalFieldOf("kept", false).forGetter(Spot::kept)
        ).apply(instance, Spot::new));

        boolean same(Spot other) {
            return x == other.x && z == other.z && dimension.equals(other.dimension);
        }
    }

    /** La base d'un joueur : le nom de sa faction, son dernier passage, et ses claims. */
    public record Base(UUID owner, String name, long seen, boolean enabled, List<Spot> spots) {
        static final Codec<Base> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Base::owner),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Base::name),
                Codec.LONG.optionalFieldOf("seen", 0L).forGetter(Base::seen),
                Codec.BOOL.optionalFieldOf("enabled", true).forGetter(Base::enabled),
                Spot.CODEC.listOf().optionalFieldOf("spots", List.of()).forGetter(Base::spots)
        ).apply(instance, Base::new));

        public int kept() {
            int count = 0;
            for (Spot spot : spots) {
                if (spot.kept()) {
                    count++;
                }
            }
            return count;
        }

        public int boxes() {
            int count = 0;
            for (Spot spot : spots) {
                if (spot.kept()) {
                    count += spot.boxes();
                }
            }
            return count;
        }

        boolean awake(long now) {
            return enabled && now - seen < ASLEEP_DAYS * 86_400_000L;
        }
    }

    public record State(List<Base> bases) {
        public static final State EMPTY = new State(List.of());
        public static final MapCodec<State> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Base.CODEC.listOf().optionalFieldOf("bases", List.of()).forGetter(State::bases)
        ).apply(instance, State::new));

        public boolean exists() {
            return !bases.isEmpty();
        }
    }

    /** Les chunks tenus en ce moment, par joueur : ce que le ticket a recu. */
    private static final Map<UUID, Set<Spot>> HELD = new HashMap<>();
    private static volatile boolean any;
    /** Quand le serveur a demarre : avant, la base ne tournait pas. */
    private static long started;

    /** Le ticket de la base : le chunk tourne comme si un joueur etait la. */
    static final TicketController CONTROLLER = new TicketController(
            Identifier.fromNamespaceAndPath(DoHy.MOD_ID, "base"), KeptBases::validate);


    private KeptBases() {
    }

    /** Au chargement des tickets enregistres : on les jette tous, la liste du monde les repose. */
    private static void validate(ServerLevel level, TicketHelper helper) {
        for (UUID owner : List.copyOf(helper.getEntityTickets().keySet())) {
            helper.removeAllTickets(owner);
            // ce qu'on croyait tenu ne l'est plus : la prochaine remise a niveau le reposera
            HELD.remove(owner);
        }
        for (net.minecraft.core.BlockPos pos : List.copyOf(helper.getBlockTickets().keySet())) {
            helper.removeAllTickets(pos);
        }
    }

    /** Chaque minute : chaque base reprend exactement ses chunks (rien ne bouge si tout est deja la). */
    static void reconcile(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (Base base : state(server).bases()) {
            if (base.awake(now)) {
                hold(server, base);
            } else if (HELD.containsKey(base.owner())) {
                DoHy.LOGGER.info("Base de {} endormie : {} jours sans son joueur", base.name(), ASLEEP_DAYS);
                release(server, base.owner());
            }
        }
    }

    /** Quand le serveur a demarre, en millisecondes. */
    public static long started() {
        return started;
    }

    /** Vrai si au moins une base est tenue : le serveur ne se met alors pas en pause, vide. */
    public static boolean any() {
        return any;
    }

    public static State state(MinecraftServer server) {
        return server.overworld().getData(Factions.KEPT_BASES.get());
    }

    private static void save(MinecraftServer server, List<Base> bases) {
        server.overworld().setData(Factions.KEPT_BASES.get(), new State(List.copyOf(bases)));
    }

    public static Base of(MinecraftServer server, UUID owner) {
        for (Base base : state(server).bases()) {
            if (base.owner().equals(owner)) {
                return base;
            }
        }
        return null;
    }

    /** Les chunks tenus de ce joueur, charges ou non. */
    public static List<Spot> keptOf(MinecraftServer server, UUID owner) {
        Base base = of(server, owner);
        List<Spot> out = new ArrayList<>();
        if (base != null && base.awake(System.currentTimeMillis())) {
            for (Spot spot : base.spots()) {
                if (spot.kept()) {
                    out.add(spot);
                }
            }
        }
        return out;
    }

    static ServerLevel level(MinecraftServer server, String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /** Au demarrage : la liste du monde reprend ses chunks. */
    static void start(MinecraftServer server) {
        HELD.clear();
        started = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        int bases = 0;
        int chunks = 0;
        for (Base base : state(server).bases()) {
            if (base.awake(now)) {
                hold(server, base);
                bases++;
                chunks += base.kept();
            }
        }
        DoHy.LOGGER.info("Bases qui tournent sans leur joueur : {} base(s), {} chunk(s) tenus", bases, chunks);
    }

    static void stop() {
        HELD.clear();
        any = false;
    }

    /**
     * Le joueur est la : ses claims, leurs machines et coffres comptes la ou ils
     * sont charges, les {@link #CAP} plus actifs tenus. Rend la base a jour.
     */
    public static Base refresh(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        FactionData data = FactionApi.data(player);
        UUID owner = player.getUUID();
        List<Base> bases = new ArrayList<>(state(server).bases());
        Base before = null;
        int index = -1;
        for (int i = 0; i < bases.size(); i++) {
            if (bases.get(i).owner().equals(owner)) {
                before = bases.get(i);
                index = i;
            }
        }
        if (!data.exists() || data.claims().isEmpty()) {
            if (index >= 0) {
                bases.remove(index);
                save(server, bases);
            }
            release(server, owner);
            return null;
        }
        // les machines et coffres de chaque claim : comptes s'il est charge, sinon ceux de la derniere fois
        List<Spot> spots = new ArrayList<>();
        for (FactionData.Claim claim : data.claims()) {
            int boxes = 0;
            if (before != null) {
                for (Spot old : before.spots()) {
                    if (old.x() == claim.x() && old.z() == claim.z() && old.dimension().equals(claim.dimension())) {
                        boxes = old.boxes();
                    }
                }
            }
            ServerLevel level = level(server, claim.dimension());
            LevelChunk chunk = level == null ? null : level.getChunkSource().getChunkNow(claim.x(), claim.z());
            if (chunk != null) {
                boxes = chunk.getBlockEntities().size();
            }
            spots.add(new Spot(claim.dimension(), claim.x(), claim.z(), boxes, false));
        }
        // au-dela du plafond, les plus actifs ; a egalite, l'ordre des claims (les premiers poses)
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < spots.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt((Integer i) -> -spots.get(i).boxes()).thenComparingInt(i -> i));
        Set<Integer> chosen = new HashSet<>(order.subList(0, Math.min(CAP, order.size())));
        for (int i = 0; i < spots.size(); i++) {
            Spot spot = spots.get(i);
            spots.set(i, new Spot(spot.dimension(), spot.x(), spot.z(), spot.boxes(), chosen.contains(i)));
        }
        boolean enabled = before == null || before.enabled();
        Base base = new Base(owner, data.name(), System.currentTimeMillis(), enabled, List.copyOf(spots));
        if (index >= 0) {
            bases.set(index, base);
        } else {
            bases.add(base);
        }
        save(server, bases);
        hold(server, base);
        return base;
    }

    /** Allume ou eteint la base de ce joueur ; rend le nouvel etat. */
    public static boolean toggle(ServerPlayer player, boolean enabled) {
        MinecraftServer server = player.level().getServer();
        List<Base> bases = new ArrayList<>(state(server).bases());
        for (int i = 0; i < bases.size(); i++) {
            Base base = bases.get(i);
            if (base.owner().equals(player.getUUID())) {
                bases.set(i, new Base(base.owner(), base.name(), base.seen(), enabled, base.spots()));
                save(server, bases);
            }
        }
        if (!enabled) {
            release(server, player.getUUID());
            return false;
        }
        refresh(player);
        return true;
    }

    /** Fait coller les tickets a la base : ajoute les nouveaux chunks tenus, lache les autres. */
    private static void hold(MinecraftServer server, Base base) {
        UUID owner = base.owner();
        Set<Spot> held = HELD.computeIfAbsent(owner, key -> new HashSet<>());
        Set<Spot> wanted = new HashSet<>();
        if (base.awake(System.currentTimeMillis())) {
            for (Spot spot : base.spots()) {
                if (spot.kept()) {
                    wanted.add(new Spot(spot.dimension(), spot.x(), spot.z(), 0, true));
                }
            }
        }
        for (Spot spot : List.copyOf(held)) {
            if (!wanted.contains(spot)) {
                ServerLevel level = level(server, spot.dimension());
                if (level != null) {
                    CONTROLLER.forceChunk(level, owner, spot.x(), spot.z(), false, true);
                }
                held.remove(spot);
            }
        }
        for (Spot spot : wanted) {
            if (!held.contains(spot)) {
                ServerLevel level = level(server, spot.dimension());
                if (level != null && CONTROLLER.forceChunk(level, owner, spot.x(), spot.z(), true, true)) {
                    held.add(spot);
                }
            }
        }
        if (held.isEmpty()) {
            HELD.remove(owner);
        }
        any = !HELD.isEmpty();
    }

    private static void release(MinecraftServer server, UUID owner) {
        Set<Spot> held = HELD.remove(owner);
        if (held != null) {
            for (Spot spot : held) {
                ServerLevel level = level(server, spot.dimension());
                if (level != null) {
                    CONTROLLER.forceChunk(level, owner, spot.x(), spot.z(), false, true);
                }
            }
        }
        any = !HELD.isEmpty();
    }

    /** Temporaire : une base d'essai, sans joueur, qui tient un chunk. */
    public static void testKeep(MinecraftServer server, String dimension, int x, int z) {
        UUID owner = new UUID(0L, 1L);
        List<Base> bases = new ArrayList<>(state(server).bases());
        bases.removeIf(base -> base.owner().equals(owner));
        Base base = new Base(owner, "Essai", System.currentTimeMillis(), true,
                List.of(new Spot(dimension, x, z, 0, true)));
        bases.add(base);
        save(server, bases);
        hold(server, base);
        DoHy.LOGGER.info("Base d'essai : chunk {} {} tenu, base tenue {}", x, z, any);
    }

    /** Une signature des claims : un claim pose ou rendu la change. */
    static int signature(FactionData data) {
        if (!data.exists()) {
            return 0;
        }
        int hash = data.claims().size();
        for (FactionData.Claim claim : data.claims()) {
            hash = hash * 31 + claim.dimension().hashCode();
            hash = hash * 31 + claim.x();
            hash = hash * 31 + claim.z();
        }
        return hash == 0 ? 1 : hash;
    }
}
