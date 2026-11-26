package fr.atraxterra.dohy.faction.unesco;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le programme UNESCO et les merveilles du monde, gardes sur l'overworld : les monuments inscrits
 * de tous les joueurs, la merveille du mois et les precedentes, et les primes d'un absent.
 */
public final class UnescoData {

    /** Un monument inscrit : sa plaque, sa zone, sa note et ce qu'il a rapporte. */
    public static final class Monument {
        static final Codec<Monument> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(monument -> monument.owner),
                Codec.STRING.optionalFieldOf("owner_name", "").forGetter(monument -> monument.ownerName),
                Codec.STRING.optionalFieldOf("nation", "").forGetter(monument -> monument.nation),
                Codec.STRING.fieldOf("id").forGetter(monument -> monument.id),
                Codec.STRING.fieldOf("name").forGetter(monument -> monument.name),
                Codec.STRING.fieldOf("dimension").forGetter(monument -> monument.dimension),
                Codec.LONG.fieldOf("plaque").forGetter(monument -> monument.plaque),
                Codec.INT.listOf().fieldOf("box").forGetter(monument -> List.of(monument.minX, monument.minY,
                        monument.minZ, monument.maxX, monument.maxY, monument.maxZ)),
                Codec.INT.listOf().fieldOf("criteria").forGetter(monument -> monument.criteria),
                Codec.INT.fieldOf("score").forGetter(monument -> monument.score),
                Codec.INT.fieldOf("weight").forGetter(monument -> monument.weight),
                Codec.LONG.fieldOf("grant").forGetter(monument -> monument.grant),
                Codec.LONG.fieldOf("inscribed").forGetter(monument -> monument.inscribed),
                Codec.INT.optionalFieldOf("built", 0).forGetter(monument -> monument.built),
                Codec.BOOL.optionalFieldOf("guide", false).forGetter(monument -> monument.guide)
        ).apply(instance, Monument::new));

        final UUID owner;
        String ownerName;
        String nation;
        final String id;
        String name;
        final String dimension;
        final long plaque;
        final int minX;
        final int minY;
        final int minZ;
        final int maxX;
        final int maxY;
        final int maxZ;
        /** Taille, details, interieur, style, fonctions. */
        final List<Integer> criteria;
        final int score;
        final int weight;
        final long grant;
        final long inscribed;
        final int built;
        boolean guide;

        Monument(UUID owner, String ownerName, String nation, String id, String name, String dimension, long plaque,
                 List<Integer> box, List<Integer> criteria, int score, int weight, long grant, long inscribed,
                 int built, boolean guide) {
            this.owner = owner;
            this.ownerName = ownerName;
            this.nation = nation;
            this.id = id;
            this.name = name;
            this.dimension = dimension;
            this.plaque = plaque;
            this.minX = box.size() > 0 ? box.get(0) : 0;
            this.minY = box.size() > 1 ? box.get(1) : 0;
            this.minZ = box.size() > 2 ? box.get(2) : 0;
            this.maxX = box.size() > 3 ? box.get(3) : 0;
            this.maxY = box.size() > 4 ? box.get(4) : 0;
            this.maxZ = box.size() > 5 ? box.get(5) : 0;
            this.criteria = List.copyOf(criteria);
            this.score = score;
            this.weight = weight;
            this.grant = grant;
            this.inscribed = inscribed;
            this.built = built;
            this.guide = guide;
        }

        public UUID owner() {
            return owner;
        }

        public String id() {
            return id;
        }

        public String name() {
            return name;
        }

        public String nation() {
            return nation;
        }

        public String dimension() {
            return dimension;
        }

        public BlockPos plaque() {
            return BlockPos.of(plaque);
        }

        public int score() {
            return score;
        }

        public int weight() {
            return weight;
        }

        public long grant() {
            return grant;
        }

        public int built() {
            return built;
        }

        public List<Integer> criteria() {
            return criteria;
        }

        public long inscribed() {
            return inscribed;
        }

        public boolean guide() {
            return guide;
        }

        /** Ce bloc est-il dans la zone du monument ? */
        public boolean contains(String dim, BlockPos pos) {
            return dimension.equals(dim) && pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY
                    && pos.getY() <= maxY && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        /** Les deux zones se touchent-elles ? */
        boolean overlaps(String dim, int x0, int y0, int z0, int x1, int y1, int z1) {
            return dimension.equals(dim) && x0 <= maxX && x1 >= minX && y0 <= maxY && y1 >= minY && z0 <= maxZ
                    && z1 >= minZ;
        }
    }

    /** Une merveille du monde, elue pour un mois. {@code owner} est vide pour une nation rivale. */
    public record Wonder(String name, String nation, String owner, String monument, int month) {
        static final Codec<Wonder> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(Wonder::name),
                Codec.STRING.optionalFieldOf("nation", "").forGetter(Wonder::nation),
                Codec.STRING.optionalFieldOf("owner", "").forGetter(Wonder::owner),
                Codec.STRING.optionalFieldOf("monument", "").forGetter(Wonder::monument),
                Codec.INT.fieldOf("month").forGetter(Wonder::month)
        ).apply(instance, Wonder::new));

        public boolean player() {
            return !owner.isEmpty();
        }
    }

    public static final MapCodec<UnescoData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Monument.CODEC.listOf().optionalFieldOf("monuments", List.of()).forGetter(data -> data.monuments),
            Wonder.CODEC.optionalFieldOf("wonder").forGetter(data -> Optional.ofNullable(data.wonder)),
            Codec.INT.optionalFieldOf("last_election", 0).forGetter(data -> data.lastElection),
            Wonder.CODEC.listOf().optionalFieldOf("history", List.of()).forGetter(data -> data.history),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).optionalFieldOf("owed", Map.of())
                    .forGetter(data -> data.owed)
    ).apply(instance, (monuments, wonder, last, history, owed) -> new UnescoData(monuments, wonder.orElse(null),
            last, history, owed)));

    final List<Monument> monuments;
    Wonder wonder;
    /** Le mois de la derniere election (annee x 100 + mois). */
    int lastElection;
    final List<Wonder> history;
    /** Les primes d'une merveille dont le maitre etait absent, versees a sa connexion. */
    final Map<UUID, Long> owed;

    public UnescoData() {
        this(List.of(), null, 0, List.of(), Map.of());
    }

    private UnescoData(List<Monument> monuments, Wonder wonder, int lastElection, List<Wonder> history,
                       Map<UUID, Long> owed) {
        this.monuments = new ArrayList<>(monuments);
        this.wonder = wonder;
        this.lastElection = lastElection;
        this.history = new ArrayList<>(history);
        this.owed = new HashMap<>(owed);
    }

    boolean isEmpty() {
        return monuments.isEmpty() && wonder == null && lastElection == 0 && history.isEmpty() && owed.isEmpty();
    }

    UnescoData touched() {
        return new UnescoData(monuments, wonder, lastElection, history, owed);
    }

    public List<Monument> monuments() {
        return monuments;
    }

    public Wonder wonder() {
        return wonder;
    }

    public List<Wonder> history() {
        return history;
    }

    /** Les monuments d'un joueur. */
    public List<Monument> of(UUID owner) {
        List<Monument> mine = new ArrayList<>();
        for (Monument monument : monuments) {
            if (monument.owner.equals(owner)) {
                mine.add(monument);
            }
        }
        return mine;
    }

    /** Le monument de cette plaque, ou {@code null}. */
    public Monument at(String dimension, BlockPos plaque) {
        long packed = plaque.asLong();
        for (Monument monument : monuments) {
            if (monument.plaque == packed && monument.dimension.equals(dimension)) {
                return monument;
            }
        }
        return null;
    }

    public Monument byId(String id) {
        for (Monument monument : monuments) {
            if (monument.id.equals(id)) {
                return monument;
            }
        }
        return null;
    }
}
