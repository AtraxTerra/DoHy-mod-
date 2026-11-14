package fr.atraxterra.dohy.earth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.faction.ClaimOwner;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionData;
import fr.atraxterra.dohy.faction.event.EventRun;
import fr.atraxterra.dohy.spawn.SpawnApi;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Les pays a prendre, sur la carte de la Terre.
 *
 * <p>Chaque pays de la grille est tenu par une des douze nations rivales que
 * le jeu fait vivre — les factions fictives des events. Elles se partagent le
 * monde par regions, tirees de la graine du monde : une nation tient l'Europe,
 * une autre l'Afrique australe, une autre la Russie. Rien de cela n'est
 * sauvegarde ; seuls les pays pris par des joueurs le sont, sur le monde de la
 * carte ({@link Ledger}).
 *
 * <p>Pour prendre un pays, une faction pose sa capitale dans un de ses claims,
 * puis vise un pays : celui de sa capitale d'abord, un voisin de ses terres
 * ensuite. Au prochain event de faction, la nation qui le tient est de la
 * partie ; finir devant elle rapporte des points de conquete, l'ELO gagne a
 * cet event. Le pays tombe quand ses points sont reunis. Chaque pays tenu
 * ajoute un claim.
 */
public final class EarthNations {

    /** La grille en carreaux de huit cases (64 blocs) pour chercher les voisins. */
    private static final int BLOCK_CELLS = 8;
    /** Deux pays sont voisins si leurs terres sont a quatre carreaux l'une de l'autre, 300 blocs environ. */
    private static final int NEAR_BLOCKS = 4;
    /** Ce que cela fait en blocs, pour les messages : un carreau fait 64 blocs. */
    public static final int NEAR_DISTANCE = 300;
    private static final int ROUNDS = 30;

    private static Atlas atlas;

    private EarthNations() {
    }

    // -- Ce qui est sauvegarde ------------------------------------------------------------

    /** Un pays pris par une faction de joueur. */
    public record Held(int country, UUID owner, String faction, long since) {
        static final Codec<Held> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("country").forGetter(Held::country),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Held::owner),
                Codec.STRING.optionalFieldOf("faction", "").forGetter(Held::faction),
                Codec.LONG.optionalFieldOf("since", 0L).forGetter(Held::since)
        ).apply(instance, Held::new));
    }

    /** Les pays pris par des joueurs, notes sur le monde de la carte. */
    public record Ledger(List<Held> held) {
        public static final Ledger EMPTY = new Ledger(List.of());
        public static final MapCodec<Ledger> MAP_CODEC = Held.CODEC.listOf().fieldOf("held")
                .xmap(Ledger::new, Ledger::held);

        public boolean exists() {
            return !held.isEmpty();
        }

        public Held of(int country) {
            for (Held entry : held) {
                if (entry.country() == country) {
                    return entry;
                }
            }
            return null;
        }

        public List<Integer> of(UUID owner) {
            List<Integer> out = new ArrayList<>();
            for (Held entry : held) {
                if (entry.owner().equals(owner)) {
                    out.add(entry.country());
                }
            }
            return out;
        }

        Ledger with(Held entry) {
            List<Held> next = new ArrayList<>(held);
            next.removeIf(other -> other.country() == entry.country());
            next.add(entry);
            return new Ledger(List.copyOf(next));
        }

        Ledger without(int country) {
            List<Held> next = new ArrayList<>(held);
            next.removeIf(other -> other.country() == country);
            return new Ledger(List.copyOf(next));
        }

        Ledger without(UUID owner) {
            List<Held> next = new ArrayList<>(held);
            next.removeIf(other -> other.owner().equals(owner));
            return new Ledger(List.copyOf(next));
        }
    }

    /** Les points de conquete reunis contre un pays. */
    public record Points(int country, long points) {
        static final Codec<Points> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("country").forGetter(Points::country),
                Codec.LONG.fieldOf("points").forGetter(Points::points)
        ).apply(instance, Points::new));
    }

    /**
     * Ce qu'une faction a en cours sur la carte : sa capitale (le pays, et le
     * point ou elle est posee), le pays vise, et ses points pays par pays — on
     * peut changer de cible sans rien perdre.
     */
    public record Nation(int capital, int capitalX, int capitalZ, int target, List<Points> points) {
        public static final Nation EMPTY = new Nation(0, 0, 0, 0, List.of());
        public static final MapCodec<Nation> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.optionalFieldOf("capital", 0).forGetter(Nation::capital),
                Codec.INT.optionalFieldOf("capital_x", 0).forGetter(Nation::capitalX),
                Codec.INT.optionalFieldOf("capital_z", 0).forGetter(Nation::capitalZ),
                Codec.INT.optionalFieldOf("target", 0).forGetter(Nation::target),
                Points.CODEC.listOf().optionalFieldOf("points", List.of()).forGetter(Nation::points)
        ).apply(instance, Nation::new));

        public boolean exists() {
            return capital != 0 || target != 0 || !points.isEmpty();
        }

        public long pointsOn(int country) {
            for (Points entry : points) {
                if (entry.country() == country) {
                    return entry.points();
                }
            }
            return 0L;
        }

        Nation withPoints(int country, long value) {
            List<Points> next = new ArrayList<>(points);
            next.removeIf(entry -> entry.country() == country);
            if (value > 0) {
                next.add(new Points(country, value));
            }
            return new Nation(capital, capitalX, capitalZ, target, List.copyOf(next));
        }

        Nation withTarget(int country) {
            return new Nation(capital, capitalX, capitalZ, country, points);
        }

        Nation withCapital(int country, int x, int z) {
            return new Nation(country, x, z, target, points);
        }
    }

    // -- L'atlas : tailles, voisins, nations rivales ---------------------------------------

    /** Ce qui se deduit de la grille et de la graine, une fois par partie. */
    static final class Atlas {
        final EarthData earth;
        final long seed;
        /** Cases de terre par pays. */
        final int[] cells;
        /** Le cœur de chaque pays : sa case de terre la plus proche de son centre, en blocs. */
        final int[] heartX;
        final int[] heartZ;
        /** Les voisins de chaque pays, a {@link #NEAR_DISTANCE} blocs environ. */
        final BitSet[] near;
        /** La nation rivale qui tient chaque pays, indice dans {@link EventRun#rivalPool()} ; -1 sans terre. */
        final int[] rival;

        Atlas(EarthData earth, long seed, int[] cells, int[] heartX, int[] heartZ, BitSet[] near, int[] rival) {
            this.earth = earth;
            this.seed = seed;
            this.cells = cells;
            this.heartX = heartX;
            this.heartZ = heartZ;
            this.near = near;
            this.rival = rival;
        }

        boolean land(int country) {
            return country > 0 && country < cells.length && cells[country] > 0;
        }
    }

    static void clear() {
        atlas = null;
    }

    private static long seedOf(MinecraftServer server) {
        return server.overworld().getSeed();
    }

    /** L'atlas de cette partie, ou {@code null} sans carte de la Terre. */
    static Atlas atlas(MinecraftServer server) {
        EarthData earth = EarthData.get();
        if (earth == null) {
            return null;
        }
        long seed = seedOf(server);
        Atlas current = atlas;
        if (current == null || current.earth != earth || current.seed != seed) {
            long start = System.nanoTime();
            current = build(earth, seed);
            atlas = current;
            int landed = 0;
            for (int count : current.cells) {
                if (count > 0) {
                    landed++;
                }
            }
            DoHy.LOGGER.info("Pays a prendre : {} pays, {} nations rivales, calcule en {} ms", landed,
                    EventRun.rivalPool().size(), (System.nanoTime() - start) / 1_000_000L);
        }
        return current;
    }

    private static Atlas build(EarthData earth, long seed) {
        EarthData.Grid grid = earth.config().grid();
        int width = grid.width();
        int height = grid.height();
        int slots = earth.countrySlots();
        short[] raw = earth.cells();

        double[] cosLon = new double[width];
        double[] sinLon = new double[width];
        for (int column = 0; column < width; column++) {
            double lon = Math.toRadians(earth.longitude(grid.minX() + (column + 0.5d) * grid.cell()));
            cosLon[column] = Math.cos(lon);
            sinLon[column] = Math.sin(lon);
        }
        double[] cosLat = new double[height];
        double[] sinLat = new double[height];
        for (int row = 0; row < height; row++) {
            double lat = Math.toRadians(earth.latitude(grid.minZ() + (row + 0.5d) * grid.cell()));
            cosLat[row] = Math.cos(lat);
            sinLat[row] = Math.sin(lat);
        }

        // le centre de chaque pays, sur le globe : la moyenne de ses cases de terre
        int[] cells = new int[slots];
        double[][] sum = new double[slots][3];
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int id = land(raw[row * width + column], slots);
                if (id > 0) {
                    cells[id]++;
                    sum[id][0] += cosLat[row] * cosLon[column];
                    sum[id][1] += cosLat[row] * sinLon[column];
                    sum[id][2] += sinLat[row];
                }
            }
        }
        for (double[] vector : sum) {
            normalise(vector);
        }
        // son cœur : la case de terre la plus proche de ce centre (la France est en Bretagne, pas en mer)
        double[] best = new double[slots];
        java.util.Arrays.fill(best, -2.0d);
        int[] heartColumn = new int[slots];
        int[] heartRow = new int[slots];
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int id = land(raw[row * width + column], slots);
                if (id > 0) {
                    double dot = sum[id][0] * cosLat[row] * cosLon[column] + sum[id][1] * cosLat[row] * sinLon[column]
                            + sum[id][2] * sinLat[row];
                    if (dot > best[id]) {
                        best[id] = dot;
                        heartColumn[id] = column;
                        heartRow[id] = row;
                    }
                }
            }
        }
        int[] heartX = new int[slots];
        int[] heartZ = new int[slots];
        double[][] heart = new double[slots][];
        for (int id = 1; id < slots; id++) {
            if (cells[id] > 0) {
                heartX[id] = grid.minX() + heartColumn[id] * grid.cell() + grid.cell() / 2;
                heartZ[id] = grid.minZ() + heartRow[id] * grid.cell() + grid.cell() / 2;
                heart[id] = new double[] {cosLat[heartRow[id]] * cosLon[heartColumn[id]],
                        cosLat[heartRow[id]] * sinLon[heartColumn[id]], sinLat[heartRow[id]]};
            }
        }

        BitSet[] near = neighbours(raw, width, height, slots);
        int[] rival = rivals(cells, heart, seed);
        return new Atlas(earth, seed, cells, heartX, heartZ, near, rival);
    }

    private static int land(short value, int slots) {
        int cell = value & 0xFFFF;
        return cell != 0 && (cell & 0x8000) == 0 && cell < slots ? cell : 0;
    }

    private static void normalise(double[] vector) {
        double length = Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
        if (length > 1.0e-9d) {
            vector[0] /= length;
            vector[1] /= length;
            vector[2] /= length;
        }
    }

    /** Les pays presents dans chaque carreau, puis ceux qu'on trouve a quelques carreaux. */
    private static BitSet[] neighbours(short[] raw, int width, int height, int slots) {
        int blocksWide = (width + BLOCK_CELLS - 1) / BLOCK_CELLS;
        int blocksHigh = (height + BLOCK_CELLS - 1) / BLOCK_CELLS;
        int[][] present = new int[blocksWide * blocksHigh][];
        int[] stamp = new int[slots];
        int[] found = new int[slots];
        int mark = 0;
        for (int by = 0; by < blocksHigh; by++) {
            for (int bx = 0; bx < blocksWide; bx++) {
                mark++;
                int count = 0;
                for (int row = by * BLOCK_CELLS; row < Math.min(height, (by + 1) * BLOCK_CELLS); row++) {
                    for (int column = bx * BLOCK_CELLS; column < Math.min(width, (bx + 1) * BLOCK_CELLS); column++) {
                        int id = land(raw[row * width + column], slots);
                        if (id > 0 && stamp[id] != mark) {
                            stamp[id] = mark;
                            found[count++] = id;
                        }
                    }
                }
                if (count > 0) {
                    present[by * blocksWide + bx] = java.util.Arrays.copyOf(found, count);
                }
            }
        }
        BitSet[] near = new BitSet[slots];
        for (int id = 0; id < slots; id++) {
            near[id] = new BitSet(slots);
        }
        BitSet window = new BitSet(slots);
        for (int by = 0; by < blocksHigh; by++) {
            for (int bx = 0; bx < blocksWide; bx++) {
                int[] here = present[by * blocksWide + bx];
                if (here == null) {
                    continue;
                }
                window.clear();
                for (int dy = -NEAR_BLOCKS; dy <= NEAR_BLOCKS; dy++) {
                    int y = by + dy;
                    if (y < 0 || y >= blocksHigh) {
                        continue;
                    }
                    for (int dx = -NEAR_BLOCKS; dx <= NEAR_BLOCKS; dx++) {
                        int x = bx + dx;
                        if (x < 0 || x >= blocksWide) {
                            continue;
                        }
                        int[] there = present[y * blocksWide + x];
                        if (there != null) {
                            for (int id : there) {
                                window.set(id);
                            }
                        }
                    }
                }
                for (int id : here) {
                    near[id].or(window);
                }
            }
        }
        for (int id = 0; id < slots; id++) {
            near[id].clear(id);
        }
        return near;
    }

    /**
     * Le partage du monde entre les nations rivales : des regions, par une
     * moyenne mobile sur le globe (k-moyennes), chaque pays pese la racine de
     * sa taille. Le depart est tire de la graine : deux mondes ne se partagent
     * pas pareil, un meme monde toujours de la meme facon.
     */
    private static int[] rivals(int[] cells, double[][] heart, long seed) {
        int slots = cells.length;
        int nations = EventRun.rivalPool().size();
        List<Integer> ids = new ArrayList<>();
        for (int id = 1; id < slots; id++) {
            if (cells[id] > 0) {
                ids.add(id);
            }
        }
        int[] rival = new int[slots];
        java.util.Arrays.fill(rival, -1);
        if (ids.isEmpty()) {
            return rival;
        }
        ids.sort(Comparator.comparingInt((Integer id) -> -cells[id]).thenComparingInt(id -> id));
        Random random = new Random(seed ^ 0x5041_5953_4E41_5449L);
        double[] weight = new double[slots];
        for (int id : ids) {
            weight[id] = Math.sqrt(cells[id]);
        }
        int count = Math.min(nations, ids.size());
        double[][] centre = new double[count][];
        centre[0] = heart[ids.get(random.nextInt(Math.min(20, ids.size())))].clone();
        for (int k = 1; k < count; k++) {
            int far = ids.get(0);
            double farthest = -1.0d;
            for (int id : ids) {
                double closest = Double.MAX_VALUE;
                for (int j = 0; j < k; j++) {
                    closest = Math.min(closest, distance(heart[id], centre[j]));
                }
                if (closest * weight[id] > farthest) {
                    farthest = closest * weight[id];
                    far = id;
                }
            }
            centre[k] = heart[far].clone();
        }
        int[] cluster = new int[slots];
        for (int round = 0; round < ROUNDS; round++) {
            for (int id : ids) {
                cluster[id] = nearest(heart[id], centre);
            }
            double[][] next = new double[count][3];
            for (int id : ids) {
                double[] mean = next[cluster[id]];
                mean[0] += heart[id][0] * weight[id];
                mean[1] += heart[id][1] * weight[id];
                mean[2] += heart[id][2] * weight[id];
            }
            for (int k = 0; k < count; k++) {
                if (next[k][0] != 0.0d || next[k][1] != 0.0d || next[k][2] != 0.0d) {
                    normalise(next[k]);
                    centre[k] = next[k];
                }
            }
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < nations; i++) {
            order.add(i);
        }
        Collections.shuffle(order, random);
        for (int id : ids) {
            rival[id] = order.get(nearest(heart[id], centre));
        }
        return rival;
    }

    private static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        double dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz;
    }

    private static int nearest(double[] point, double[][] centre) {
        int best = 0;
        double closest = Double.MAX_VALUE;
        for (int k = 0; k < centre.length; k++) {
            double d = distance(point, centre[k]);
            if (d < closest) {
                closest = d;
                best = k;
            }
        }
        return best;
    }

    // -- Lire l'etat --------------------------------------------------------------------------

    /** Vrai si cette partie a une carte de la Terre : les pays existent. */
    public static boolean active() {
        return EarthData.get() != null;
    }

    private static ServerLevel earthLevel(MinecraftServer server) {
        EarthData earth = EarthData.get();
        return earth == null ? null : server.getLevel(earth.dimension());
    }

    public static Ledger ledger(MinecraftServer server) {
        ServerLevel level = earthLevel(server);
        return level == null ? Ledger.EMPTY : level.getData(DoHyEarth.LEDGER.get());
    }

    private static void save(MinecraftServer server, Ledger ledger) {
        ServerLevel level = earthLevel(server);
        if (level != null) {
            level.setData(DoHyEarth.LEDGER.get(), ledger);
        }
        SpawnApi.paintNations(server);
    }

    public static Nation nation(ServerPlayer player) {
        return player.getData(DoHyEarth.NATION.get());
    }

    private static void save(ServerPlayer player, Nation nation) {
        if (nation.exists()) {
            player.setData(DoHyEarth.NATION.get(), nation);
        } else {
            player.removeData(DoHyEarth.NATION.get());
        }
    }

    /** Les pays tenus par la faction de ce joueur. */
    public static List<Integer> heldBy(ServerPlayer player) {
        if (!active()) {
            return List.of();
        }
        return ledger(player.level().getServer()).of(player.getUUID());
    }

    /** Un claim de plus par pays tenu. */
    public static int claimBonus(ServerPlayer player) {
        return heldBy(player).size();
    }

    /** Qui tient un pays : une faction de joueur, ou une nation rivale. */
    public record Holder(int country, int rival, Held held) {
        public boolean player() {
            return held != null;
        }

        public String name() {
            if (held != null) {
                return held.faction();
            }
            return rival >= 0 ? EventRun.rivalPool().get(rival).name() : "personne";
        }

        public boolean ownedBy(UUID owner) {
            return held != null && held.owner().equals(owner);
        }
    }

    public static Holder holder(MinecraftServer server, int country) {
        Atlas atlas = atlas(server);
        Held held = ledger(server).of(country);
        int rival = atlas == null || country <= 0 || country >= atlas.rival.length ? -1 : atlas.rival[country];
        return new Holder(country, rival, held);
    }

    /** « de Nordheim », « d'Arcanum », « des Corbeaux », « de ta faction ». */
    public static String de(String name) {
        if (name.startsWith("Les ")) {
            return "des " + name.substring(4);
        }
        if (name.startsWith("Le ")) {
            return "du " + name.substring(3);
        }
        if (!name.isEmpty() && "AEIOUYaeiouyÀÂÉÈÊÎÔÛ".indexOf(name.charAt(0)) >= 0) {
            return "d'" + name;
        }
        return "de " + name;
    }

    /** « a Nordheim », « aux Corbeaux », « au Kraken ». */
    public static String a(String name) {
        if (name.startsWith("Les ")) {
            return "aux " + name.substring(4);
        }
        if (name.startsWith("Le ")) {
            return "au " + name.substring(3);
        }
        return "a " + name;
    }

    /** « ta faction », « Nordheim », ou le nom de la faction d'un autre joueur. */
    public static String holderName(ServerPlayer viewer, int country) {
        Holder holder = holder(viewer.level().getServer(), country);
        if (holder.ownedBy(viewer.getUUID())) {
            return "ta faction";
        }
        return holder.name();
    }

    /** Le prix d'un pays en points de conquete : de 200 pour un micro-Etat a quelque 22 000 pour la Russie. */
    public static long cost(MinecraftServer server, int country) {
        Atlas atlas = atlas(server);
        if (atlas == null || !atlas.land(country)) {
            return 0L;
        }
        long raw = Math.round((200.0d + 37.0d * Math.sqrt(atlas.cells[country])) / 100.0d) * 100L;
        return Math.max(200L, raw);
    }

    public static String countryName(int country) {
        EarthData earth = EarthData.get();
        EarthData.Country found = earth == null ? null : earth.country(country);
        return found == null ? "?" : found.name();
    }

    /** Le cœur d'un pays, en blocs : ou pointer une boussole, ou dessiner son nom. */
    public static int[] heart(MinecraftServer server, int country) {
        Atlas atlas = atlas(server);
        return atlas == null || !atlas.land(country) ? null : new int[] {atlas.heartX[country], atlas.heartZ[country]};
    }

    /** Les pays de chaque nation rivale encore a elle. */
    public static List<List<Integer>> rivalHoldings(MinecraftServer server) {
        Atlas atlas = atlas(server);
        int nations = EventRun.rivalPool().size();
        List<List<Integer>> out = new ArrayList<>();
        for (int i = 0; i < nations; i++) {
            out.add(new ArrayList<>());
        }
        if (atlas == null) {
            return out;
        }
        Ledger ledger = ledger(server);
        for (int id = 1; id < atlas.rival.length; id++) {
            if (atlas.rival[id] >= 0 && ledger.of(id) == null) {
                out.get(atlas.rival[id]).add(id);
            }
        }
        for (List<Integer> list : out) {
            list.sort(Comparator.comparingInt((Integer id) -> -atlas.cells[id]));
        }
        return out;
    }

    /** La part de ses pays d'origine que chaque nation rivale tient encore ; 1 sans carte de la Terre. */
    public static double[] rivalShares(MinecraftServer server) {
        int nations = EventRun.rivalPool().size();
        double[] shares = new double[nations];
        java.util.Arrays.fill(shares, 1.0d);
        Atlas atlas = atlas(server);
        if (atlas == null) {
            return shares;
        }
        int[] original = new int[nations];
        int[] kept = new int[nations];
        Ledger ledger = ledger(server);
        for (int id = 1; id < atlas.rival.length; id++) {
            if (atlas.rival[id] >= 0) {
                original[atlas.rival[id]]++;
                if (ledger.of(id) == null) {
                    kept[atlas.rival[id]]++;
                }
            }
        }
        for (int i = 0; i < nations; i++) {
            shares[i] = original[i] == 0 ? 1.0d : (double) kept[i] / original[i];
        }
        return shares;
    }

    /**
     * Des points de conquete offerts sur le pays vise (la notation de la
     * semaine) : le pays tombe s'ils suffisent. Vrai s'ils ont ete donnes.
     */
    public static boolean award(ServerPlayer player, long points, String reason) {
        Nation nation = nation(player);
        int target = nation.target();
        if (!active() || target == 0 || points <= 0 || !FactionApi.has(player)) {
            return false;
        }
        MinecraftServer server = player.level().getServer();
        Holder holder = holder(server, target);
        if (holder.player()) {
            return false;
        }
        long cost = cost(server, target);
        long after = nation.pointsOn(target) + points;
        if (after >= cost) {
            conquer(player, target, holder);
        } else {
            save(player, nation.withPoints(target, after));
            FactionApi.tell(player, reason + " : +" + MoneyFormat.exact(points) + " points de conquete sur "
                    + countryName(target) + " (" + MoneyFormat.exact(after) + " / " + MoneyFormat.exact(cost) + ").",
                    ChatFormatting.GREEN);
        }
        return true;
    }

    /**
     * Ce qu'on peut viser : sans pays, celui de la capitale ; ensuite, les
     * voisins de ses terres, et les iles trop isolees pour avoir un voisin.
     */
    public static boolean reachable(ServerPlayer player, int country) {
        MinecraftServer server = player.level().getServer();
        Atlas atlas = atlas(server);
        if (atlas == null || !atlas.land(country)) {
            return false;
        }
        List<Integer> mine = ledger(server).of(player.getUUID());
        if (mine.isEmpty()) {
            return nation(player).capital() == country;
        }
        if (atlas.near[country].isEmpty()) {
            return true;
        }
        for (int id : mine) {
            if (atlas.near[country].get(id)) {
                return true;
            }
        }
        return false;
    }

    /** Les pays qu'on peut viser maintenant, les plus proches de ses terres d'abord. */
    public static List<Integer> targets(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Atlas atlas = atlas(server);
        List<Integer> out = new ArrayList<>();
        if (atlas == null) {
            return out;
        }
        Ledger ledger = ledger(server);
        for (int id = 1; id < atlas.cells.length; id++) {
            if (atlas.land(id) && ledger.of(id) == null && reachable(player, id)) {
                out.add(id);
            }
        }
        // d'ou l'on part : la capitale, et le cœur de chaque pays tenu
        List<long[]> homes = new ArrayList<>();
        Nation nation = nation(player);
        if (nation.capital() != 0) {
            homes.add(new long[] {nation.capitalX(), nation.capitalZ()});
        }
        for (int id : ledger.of(player.getUUID())) {
            homes.add(new long[] {atlas.heartX[id], atlas.heartZ[id]});
        }
        long[] distance = new long[atlas.cells.length];
        for (int id : out) {
            long best = Long.MAX_VALUE;
            for (long[] home : homes) {
                long dx = atlas.heartX[id] - home[0];
                long dz = atlas.heartZ[id] - home[1];
                best = Math.min(best, dx * dx + dz * dz);
            }
            distance[id] = best;
        }
        out.sort(Comparator.comparingLong((Integer id) -> distance[id]).thenComparingInt(id -> id));
        return out;
    }

    /** Un pays par son code (FRA) ou son nom, sans accents ni majuscules ; {@code null} sinon. */
    public static EarthData.Country lookup(String text) {
        EarthData earth = EarthData.get();
        if (earth == null) {
            return null;
        }
        String wanted = plain(text);
        EarthData.Country byCode = earth.byCode(text.strip().toUpperCase(Locale.ROOT));
        if (byCode != null) {
            return byCode;
        }
        EarthData.Country partial = null;
        for (EarthData.Country country : earth.config().countries()) {
            String name = plain(country.name());
            if (name.equals(wanted)) {
                return country;
            }
            if (partial == null && name.startsWith(wanted)) {
                partial = country;
            }
        }
        return partial;
    }

    private static String plain(String text) {
        return Normalizer.normalize(text.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    // -- Les gestes ---------------------------------------------------------------------------

    private static String precheck(ServerPlayer player) {
        if (!active()) {
            return "Ce monde n'a pas de carte de la Terre";
        }
        if (!FactionApi.has(player)) {
            return "Fonde d'abord ta faction";
        }
        return null;
    }

    /** Pose la capitale dans le claim ou se tient le joueur. */
    public static String setCapital(ServerPlayer player) {
        String refusal = precheck(player);
        if (refusal != null) {
            return refusal;
        }
        EarthData earth = EarthData.get();
        if (!earth.covers(player.level())) {
            return "La capitale se pose sur la carte de la Terre";
        }
        ClaimOwner owner = FactionApi.claimAt(player.level(), player.blockPosition());
        if (!owner.ownedBy(player.getUUID())) {
            return "Tiens-toi dans un de tes claims";
        }
        EarthData.Country country = earth.countryAt(player.getBlockX(), player.getBlockZ());
        if (country == null) {
            return "Pas de pays ici : la capitale se pose a terre";
        }
        MinecraftServer server = player.level().getServer();
        List<Integer> mine = ledger(server).of(player.getUUID());
        Holder holder = holder(server, country.id());
        if (!mine.isEmpty() && !holder.ownedBy(player.getUUID())) {
            return "Ta capitale se deplace dans un pays que tu tiens";
        }
        if (holder.player() && !holder.ownedBy(player.getUUID())) {
            return country.name() + " est aux mains " + de(holder.name()) + ", la faction d'un joueur";
        }
        Nation nation = nation(player).withCapital(country.id(), player.getBlockX(), player.getBlockZ());
        if (mine.isEmpty()) {
            nation = nation.withTarget(country.id());
        }
        save(player, nation);
        SpawnApi.paintNations(server);
        if (mine.isEmpty()) {
            FactionApi.tell(player, "Premier pays a prendre : " + country.name() + ", aux mains " + de(holder.name())
                    + ". Finis devant eux aux events de faction pour reunir "
                    + MoneyFormat.exact(cost(server, country.id())) + " points de conquete.", ChatFormatting.GREEN);
        }
        return null;
    }

    /** Vise un pays. */
    public static String target(ServerPlayer player, int country) {
        String refusal = precheck(player);
        if (refusal != null) {
            return refusal;
        }
        MinecraftServer server = player.level().getServer();
        Atlas atlas = atlas(server);
        if (atlas == null || !atlas.land(country)) {
            return "Ce pays n'a pas de terre sur la carte";
        }
        Holder holder = holder(server, country);
        String name = countryName(country);
        if (holder.ownedBy(player.getUUID())) {
            return name + " est deja a toi";
        }
        if (holder.player()) {
            return name + " est aux mains " + de(holder.name()) + ", la faction d'un joueur : pas de guerre entre joueurs";
        }
        Nation nation = nation(player);
        if (ledger(server).of(player.getUUID()).isEmpty()) {
            if (nation.capital() == 0) {
                return "Pose d'abord ta capitale dans un de tes claims : son pays sera ta premiere cible";
            }
            if (nation.capital() != country) {
                return "Ton premier pays est celui de ta capitale : " + countryName(nation.capital());
            }
        } else if (!reachable(player, country)) {
            return "Trop loin de tes terres : " + name + " (on vise un pays a " + NEAR_DISTANCE + " blocs environ)";
        }
        save(player, nation.withTarget(country));
        return null;
    }

    public static String targetHere(ServerPlayer player) {
        EarthData earth = EarthData.get();
        if (earth == null || !earth.covers(player.level())) {
            return "Tu n'es pas sur la carte de la Terre";
        }
        EarthData.Country country = earth.countryAt(player.getBlockX(), player.getBlockZ());
        if (country == null) {
            return "Pas de pays ici";
        }
        return target(player, country.id());
    }

    /** Renonce au pays vise : ses points restent acquis. */
    public static String dropTarget(ServerPlayer player) {
        Nation nation = nation(player);
        if (nation.target() == 0) {
            return "Tu ne vises aucun pays";
        }
        save(player, nation.withTarget(0));
        return null;
    }

    /** Rend un pays a la nation rivale de sa region. */
    public static String abandon(ServerPlayer player, int country) {
        MinecraftServer server = player.level().getServer();
        Ledger ledger = ledger(server);
        Held held = ledger.of(country);
        if (held == null || !held.owner().equals(player.getUUID())) {
            return "Ce pays n'est pas a toi";
        }
        Nation nation = nation(player);
        if (nation.capital() == country) {
            nation = nation.withCapital(0, 0, 0);
        }
        save(player, nation);
        save(server, ledger.without(country));
        return null;
    }

    /** La faction est dissoute : ses pays retournent aux nations rivales. */
    public static void release(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (!active()) {
            player.removeData(DoHyEarth.NATION.get());
            return;
        }
        player.removeData(DoHyEarth.NATION.get());
        save(server, ledger(server).without(player.getUUID()));
    }

    /** Les capitales posees par les joueurs en ligne : x, z de chacune. */
    public static List<int[]> capitals(MinecraftServer server) {
        List<int[]> out = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Nation nation = nation(player);
            if (nation.capital() != 0 && FactionApi.has(player)) {
                out.add(new int[] {nation.capitalX(), nation.capitalZ()});
            }
        }
        return out;
    }

    // -- Les events de faction ----------------------------------------------------------------

    /** Les nations que le prochain event doit aligner : celles qui tiennent un pays vise par un joueur en ligne. */
    public static List<String> wanted(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        if (!active()) {
            return out;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int target = nation(player).target();
            if (target == 0 || !FactionApi.has(player)) {
                continue;
            }
            Holder holder = holder(server, target);
            if (!holder.player() && holder.rival() >= 0 && !out.contains(holder.name())) {
                out.add(holder.name());
            }
        }
        return out;
    }

    /** Le depart d'un event : chaque joueur qui vise un pays sait contre qui il joue. */
    public static void eventStarted(MinecraftServer server, List<EventRun.Rival> rivals) {
        if (!active()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int target = nation(player).target();
            if (target == 0 || !FactionApi.has(player)) {
                continue;
            }
            Holder holder = holder(server, target);
            boolean present = rivals.stream().anyMatch(rival -> rival.name().equals(holder.name()));
            if (!holder.player() && present) {
                FactionApi.tell(player, "Cible " + countryName(target) + " : finis devant " + holder.name() + " ("
                        + MoneyFormat.exact(nation(player).pointsOn(target)) + " / "
                        + MoneyFormat.exact(cost(server, target)) + " points de conquete).", ChatFormatting.GOLD);
            }
        }
    }

    /**
     * La fin d'un event, pour un joueur en faction : s'il a fini devant la
     * nation qui tient son pays vise, l'ELO qu'il vient de gagner compte en
     * points de conquete.
     *
     * @param ranking les cles du classement, du premier au dernier ({@code p:<uuid>}, {@code r:<rang du rival>})
     * @param rivals  les factions rivales de l'event, dans l'ordre de leurs cles
     * @param mine    la cle du joueur
     * @param earned  l'ELO qu'il vient de gagner ; 0 s'il n'est pas classe
     */
    public static void afterEvent(ServerPlayer player, List<String> ranking, List<EventRun.Rival> rivals, String mine,
                                  long earned) {
        if (!active()) {
            return;
        }
        Nation nation = nation(player);
        int target = nation.target();
        if (target == 0 || !FactionApi.has(player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        Holder holder = holder(server, target);
        String name = countryName(target);
        if (holder.ownedBy(player.getUUID())) {
            save(player, nation.withTarget(0));
            return;
        }
        if (holder.player()) {
            save(player, nation.withTarget(0));
            FactionApi.tell(player, "Cible abandonnee : " + name + " appartient desormais " + a(holder.name()) + ".",
                    ChatFormatting.RED);
            return;
        }
        int rivalKey = -1;
        for (int i = 0; i < rivals.size(); i++) {
            if (rivals.get(i).name().equals(holder.name())) {
                rivalKey = i;
            }
        }
        if (rivalKey < 0) {
            return;
        }
        int place = ranking.indexOf(mine);
        int theirs = ranking.indexOf("r:" + rivalKey);
        long cost = cost(server, target);
        long before = nation.pointsOn(target);
        if (place < 0 || earned <= 0 || (theirs >= 0 && theirs < place)) {
            FactionApi.tell(player, "Tu as fini derriere " + holder.name() + " : " + name + " tient bon ("
                    + MoneyFormat.exact(before) + " / " + MoneyFormat.exact(cost) + ").", ChatFormatting.RED);
            return;
        }
        long after = before + earned;
        if (after < cost) {
            save(player, nation.withPoints(target, after));
            FactionApi.tell(player, "Conquete, " + name + " : +" + MoneyFormat.exact(earned) + " points ("
                    + MoneyFormat.exact(after) + " / " + MoneyFormat.exact(cost) + ").", ChatFormatting.GREEN);
            return;
        }
        conquer(player, target, holder);
    }

    /** Le pays tombe : il passe a la faction du joueur. */
    public static void conquer(ServerPlayer player, int country, Holder from) {
        MinecraftServer server = player.level().getServer();
        FactionData data = FactionApi.data(player);
        save(server, ledger(server).with(new Held(country, player.getUUID(), data.name(), System.currentTimeMillis())));
        Nation nation = nation(player).withPoints(country, 0L);
        if (nation.target() == country) {
            nation = nation.withTarget(0);
        }
        save(player, nation);
        String name = countryName(country);
        if (!from.player() && from.rival() >= 0) {
            // La nation depossedee s'en souviendra (lot 22l).
            fr.atraxterra.dohy.faction.war.WarStrategy.grieve(player, from.rival(), "pays pris : " + name, 25.0d);
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(name)
                .withStyle(ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(
                "Victoire sur " + from.name() + " · +1 claim").withStyle(ChatFormatting.YELLOW)));
        server.getPlayerList().broadcastSystemMessage(FactionApi.prefix().append(Component.literal(
                name + " : " + data.name() + " l'emporte sur " + from.name() + ".").withStyle(ChatFormatting.YELLOW)),
                false);
        FactionApi.stat(player, "earth:countries", 1);
        DoHy.LOGGER.info("Pays a prendre : {} prend {} a {}", data.name(), name, from.name());
    }

    /** Le nom d'une faction a change : les pays qu'elle tient le suivent. */
    public static void renamed(ServerPlayer player) {
        if (!active()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        Ledger ledger = ledger(server);
        String name = FactionApi.data(player).name();
        boolean changed = false;
        List<Held> next = new ArrayList<>();
        for (Held held : ledger.held()) {
            if (held.owner().equals(player.getUUID()) && !held.faction().equals(name)) {
                next.add(new Held(held.country(), held.owner(), name, held.since()));
                changed = true;
            } else {
                next.add(held);
            }
        }
        if (changed) {
            ServerLevel level = earthLevel(server);
            if (level != null) {
                level.setData(DoHyEarth.LEDGER.get(), new Ledger(List.copyOf(next)));
            }
        }
    }
}
