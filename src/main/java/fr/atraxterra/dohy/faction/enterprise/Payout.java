package fr.atraxterra.dohy.faction.enterprise;

import fr.atraxterra.dohy.DoHy;
import fr.atraxterra.dohy.earth.EarthData;
import fr.atraxterra.dohy.earth.EarthNations;
import fr.atraxterra.dohy.earth.disaster.Disasters;
import fr.atraxterra.dohy.earth.mine.EarthMines;
import fr.atraxterra.dohy.earth.mine.MineLedger;
import fr.atraxterra.dohy.faction.FactionApi;
import fr.atraxterra.dohy.faction.FactionVault;
import fr.atraxterra.dohy.faction.FactionWeekly;
import fr.atraxterra.dohy.faction.Nation;
import fr.atraxterra.dohy.faction.org.Cadre;
import fr.atraxterra.dohy.faction.org.Diplomacy;
import fr.atraxterra.dohy.faction.org.Organisations;
import fr.atraxterra.dohy.faction.org.Post;
import fr.atraxterra.dohy.faction.org.Regime;
import fr.atraxterra.dohy.faction.org.Treaty;
import fr.atraxterra.dohy.faction.war.NationTraits;
import fr.atraxterra.dohy.faction.war.WarData;
import fr.atraxterra.dohy.faction.war.WarStrategy;
import fr.atraxterra.dohy.season.Seasons;
import fr.atraxterra.dohy.ui.Toasts;
import fr.atraxterra.dohy.util.MoneyFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ce que les entreprises font chaque heure (lot 24b), rattrape hors ligne une semaine au plus,
 * comme les mines de la Terre : les plantations poussent, les ateliers montent leurs caisses,
 * les laboratoires transforment, les entreprises legales rapportent de l'argent propre, les
 * affaires illegales de l'argent sale, les reseaux et les trafics vendent, les facades et les
 * casinos blanchissent, les frais tombent, et la police passe parfois.
 *
 * <p>Une heure se simule sur une copie des stocks et de l'argent sale ({@link Sim}) : la meme
 * simulation donne l'apercu des menus, sans descente ni effet.
 */
final class Payout {

    private Payout() {
    }

    static final long HOUR = Enterprises.HOUR_MS;
    static final long DAY = Enterprises.DAY_MS;
    /** Les descentes : chance par heure = SCALE x risque^2,5 (1 % par heure vers 60 de risque). */
    static final double RAID_SCALE = 5.0e-7d;
    static final int RAIDS_PER_BATCH = 2;
    static final long CLOSED_RAID = 24L * HOUR;
    static final long CLOSED_SABOTAGE = 12L * HOUR;
    static final long CLOSED_STRIKE = 6L * HOUR;
    /** Une descente saisit au plus cette part de l'argent sale, et jamais plus que ce plafond. */
    static final double DIRTY_SEIZURE = 0.15d;
    static final long DIRTY_SEIZURE_CAP = 250_000L;
    /** Le petit revenu propre d'une societe ecran, par heure au niveau 1. */
    static final double FACADE_REVENUE = 300.0d;
    /** Ce qu'un casino blanchit par heure au niveau 1. */
    static final double CASINO_LAUNDER = 1_500.0d;
    static final double CASINO_FEE = 0.20d;

    /**
     * Un evenement a dire au joueur a la fin du versement : la ligne du chat et du journal, et
     * le titre et la ligne courte de sa notification (pas de notification sans titre).
     */
    record Event(String text, int color, String title, String line) {
        Event(String text, int color) {
            this(text, color, "", "");
        }
    }

    /** Ce qui fait tourner une entreprise : multiplicateur de production, frais par heure, risque, et pourquoi. */
    record Factors(double output, double cost, double risk, int influence, List<String> notes) {
    }

    /** Ce que la nation est pour le pays d'une entreprise. */
    record HolderView(boolean own, int nation, Treaty.Type treaty, boolean rival, boolean war) {
        static final HolderView NONE = new HolderView(false, -1, null, false, false);
    }

    /** Une heure de comptes. */
    record Hour(double gross, double costs, double clean, double dirty, double laundered, double seized) {
    }

    // -- Le monde : ce qui ne bouge pas pendant un versement --------------------------------------

    static final class World {
        final ServerPlayer player;
        final MinecraftServer server;
        final Holdings holdings;
        final long now;
        final Regime regime;
        final double treasurer;
        final double operations;
        final Influence.Context influence;
        final Set<Integer> struck;
        final Set<Integer> mined;
        final boolean atWar;
        final int transport;
        final int routes;
        final long capacity;
        final boolean warehouse;
        final Map<String, Cadre> managers = new HashMap<>();
        final Map<Integer, Region> regions = new HashMap<>();
        final Map<Integer, HolderView> holders = new HashMap<>();
        final Map<String, Factors> factors = new HashMap<>();
        final long iron;

        World(ServerPlayer player, Holdings holdings, long now) {
            this.player = player;
            this.server = player.level().getServer();
            this.holdings = holdings;
            this.now = now;
            this.regime = Organisations.regime(player);
            this.treasurer = Organisations.strength(player, Post.TRESORIER);
            this.operations = Organisations.strength(player, Post.OPERATIONS);
            this.influence = new Influence.Context(player, holdings);
            this.struck = Disasters.struckCountries();
            this.mined = new HashSet<>();
            if (EarthMines.active()) {
                for (MineLedger.Concession concession : EarthMines.of(player)) {
                    var body = EarthMines.body(concession.mine());
                    if (body != null) {
                        mined.add(EarthMines.country(body));
                    }
                }
            }
            this.atWar = Nation.of(player).conflict()
                    .filter(relation -> relation.stance() == WarData.Stance.GUERRE).isPresent();
            int transportLevels = 0;
            long storage = Enterprises.BASE_CAPACITY;
            boolean anyWarehouse = false;
            Set<Integer> countries = new HashSet<>();
            for (Enterprise enterprise : holdings.enterprises) {
                if (enterprise.kind() == Sector.TRANSPORT && !enterprise.closed(now)) {
                    transportLevels += enterprise.level();
                }
                if (enterprise.kind() == Sector.ENTREPOT) {
                    anyWarehouse = true;
                    storage += Math.round(Sector.ENTREPOT.base() * enterprise.level()
                            * (enterprise.has(Upgrade.EQUIPEMENT) ? 1.25d : 1.0d));
                }
                countries.add(enterprise.country());
            }
            this.transport = transportLevels;
            this.routes = countries.size();
            this.capacity = storage;
            this.warehouse = anyWarehouse;
            for (Cadre cadre : Organisations.cadres(player)) {
                if (!cadre.manages().isEmpty()) {
                    managers.put(cadre.manages(), cadre);
                }
            }
            this.iron = Enterprises.ironIn(FactionVault.of(player));
        }

        Region region(int country) {
            return regions.computeIfAbsent(country, id -> Region.of(server, id));
        }

        Syndicate syndicate(Enterprise enterprise) {
            return Syndicate.in(region(enterprise.country()));
        }

        HolderView holder(int country) {
            return holders.computeIfAbsent(country, this::readHolder);
        }

        private HolderView readHolder(int country) {
            if (country <= 0 || EarthData.get() == null) {
                return HolderView.NONE;
            }
            EarthNations.Holder holder = EarthNations.holder(server, country);
            if (holder.ownedBy(player.getUUID())) {
                return new HolderView(true, -1, null, false, false);
            }
            int nation = holder.rival();
            if (holder.player() || nation < 0 || nation >= NationTraits.count()) {
                return HolderView.NONE;
            }
            Treaty treaty = Diplomacy.treaty(player, nation);
            return new HolderView(false, nation, treaty == null ? null : treaty.kind(), Diplomacy.rival(player, nation),
                    WarStrategy.relationOf(player, nation).stance() == WarData.Stance.GUERRE);
        }

        /** Le statut avec l'organisation, et si elle sert la nation par un accord ou une alliance. */
        Holdings.Status status(Syndicate syndicate) {
            return holdings.standing(syndicate).state();
        }

        boolean deal(Syndicate syndicate) {
            Holdings.Status status = status(syndicate);
            return status == Holdings.Status.ACCORD || status == Holdings.Status.ALLIANCE;
        }

        boolean allied(Syndicate syndicate) {
            return status(syndicate) == Holdings.Status.ALLIANCE;
        }

        boolean weakened(Syndicate syndicate) {
            return holdings.standing(syndicate).weakened() > now;
        }

        /** Ce que vaut le gerant de l'entreprise, de 0 (aucun) a 1,25. */
        double manager(Enterprise enterprise) {
            Cadre cadre = managers.get(enterprise.id());
            return cadre == null ? 0.0d : Enterprises.managerStrength(cadre);
        }

        Factors factors(Enterprise enterprise) {
            return factors.computeIfAbsent(enterprise.id(), id -> compute(enterprise));
        }

        private Factors compute(Enterprise enterprise) {
            Sector sector = enterprise.kind();
            List<String> notes = new ArrayList<>();
            double output = Enterprises.OUTPUT[enterprise.level() - 1];
            notes.add("Niveau " + enterprise.level() + " : " + times(output));
            int max = enterprise.maxStaff();
            if (max > 0 && enterprise.staff() < max) {
                double staffing = 0.3d + 0.7d * enterprise.staff() / max;
                output *= staffing;
                notes.add("Employes " + enterprise.staff() + " / " + max + " : " + percent(staffing));
            }
            if (enterprise.has(Upgrade.EQUIPEMENT)) {
                output *= 1.2d;
                notes.add(Upgrade.EQUIPEMENT.label(sector) + " : +20 %");
            }
            if (enterprise.has(Upgrade.FORMATION)) {
                output *= 1.1d;
                notes.add(Upgrade.FORMATION.label(sector) + " : +10 %");
            }
            double manager = manager(enterprise);
            if (manager > 0.0d) {
                output *= 1.0d + 0.2d * manager;
                notes.add("Gerant : " + percent(1.0d + 0.2d * manager));
            }
            double regimeFactor = sector.legal() ? regime.legal() : regime.illegal();
            if (regimeFactor != 1.0d) {
                output *= regimeFactor;
                notes.add(regime.label() + " : " + percent(regimeFactor));
            }
            int influenceValue = influence.read(enterprise.country()).value();
            if (sector.role() != Sector.Role.STORE) {
                double influenceFactor = Influence.revenue(sector, influenceValue);
                output *= influenceFactor;
                notes.add("Influence " + influenceValue + " : " + percent(influenceFactor));
            }
            if (sector.seasonal()) {
                double growth = Seasons.growthAt(enterprise.z());
                if (growth < 0.999d) {
                    output *= growth;
                    notes.add(Seasons.seasonAt(enterprise.z()) + " : " + percent(growth));
                }
            }
            HolderView holder = holder(enterprise.country());
            if (struck.contains(enterprise.country())) {
                double hit = sector.physical() ? 0.5d : 0.8d;
                if (sector.legal() && enterprise.has(Upgrade.SECURITE)) {
                    hit = 1.0d - (1.0d - hit) / 2.0d;
                }
                output *= hit;
                notes.add("Catastrophe dans le pays : " + percent(hit));
            }
            if (sector.legal()) {
                if (holder.own()) {
                    output *= 1.10d;
                    notes.add("Ton pays : +10 %");
                } else if (holder.treaty() == Treaty.Type.ALLIANCE) {
                    output *= 1.20d;
                    notes.add("Alliance avec le pays : +20 %");
                } else if (holder.treaty() == Treaty.Type.ACCORD) {
                    output *= 1.15d;
                    notes.add("Accord commercial : +15 %");
                }
                if (holder.rival()) {
                    output *= 0.85d;
                    notes.add("Pays d'une rivale : -15 %");
                }
                if (holder.war()) {
                    double hit = enterprise.has(Upgrade.SECURITE) ? 0.8d : 0.6d;
                    output *= hit;
                    notes.add("En guerre avec le pays : " + percent(hit));
                }
            } else if (holder.war()) {
                output *= 1.1d;
                notes.add("Pays en guerre : +10 %");
            }
            if (sector == Sector.CARRIERE && mined.contains(enterprise.country())) {
                output *= 1.2d;
                notes.add("Une de tes mines dans le pays : +20 %");
            }
            if ((sector == Sector.USINE || sector == Sector.COMMERCE) && transport > 0) {
                double logistics = 1.0d + logistics();
                output *= logistics;
                notes.add("Tes transports : " + percent(logistics));
            }
            if (sector == Sector.RACKET) {
                double grip = influenceValue / 50.0d;
                output *= grip;
                notes.add("Emprise sur le pays : " + percent(grip));
            }
            if (sector == Sector.CONTREBANDE && routes > 1) {
                double ways = 1.0d + Math.min(1.0d, 0.1d * (routes - 1));
                output *= ways;
                notes.add(routes + " pays, autant de routes : " + percent(ways));
            }
            Syndicate syndicate = syndicate(enterprise);
            if (!sector.legal()) {
                if (syndicate != null) {
                    double turf = switch (status(syndicate)) {
                        case NEUTRE -> weakened(syndicate) ? 1.1d : 0.9d;
                        case ACCORD -> weakened(syndicate) ? 1.1d : 1.0d;
                        case ALLIANCE -> 1.15d;
                        case GUERRE -> 0.6d;
                    };
                    if (turf != 1.0d) {
                        output *= turf;
                        notes.add("Territoire " + syndicate.of() + " : " + percent(turf));
                    }
                }
                Syndicate partner = switch (sector) {
                    case CONTREBANDE -> Syndicate.DOCKS;
                    case RECEL -> Syndicate.CONDOR;
                    case RESEAU -> Syndicate.CARAVANE;
                    default -> null;
                };
                if (partner != null && deal(partner)) {
                    double boost = allied(partner) ? 1.35d : 1.25d;
                    output *= boost;
                    notes.add("Accord " + partner.of() + " : " + percent(boost));
                }
            }

            double cost = sector.cost() * Enterprises.COSTS[enterprise.level() - 1];
            if (enterprise.has(Upgrade.LOGISTIQUE)) {
                cost *= 0.8d;
            }
            double wages = Enterprises.WAGE * enterprise.staff() * (enterprise.has(Upgrade.FORMATION) ? 0.75d : 1.0d);
            cost = (cost + wages) * (1.0d - Math.min(0.20d, 0.16d * treasurer));

            double risk = 0.0d;
            if (!sector.legal()) {
                Line line = enterprise.drug();
                risk = sector.risk() + (line == null ? 0.0d : line.risk()) + 3.0d * (enterprise.level() - 1)
                        + 0.5d * enterprise.staff();
                risk *= regime.risk();
                if (holder.own()) {
                    risk *= 0.75d;
                } else if (holder.treaty() == Treaty.Type.ALLIANCE) {
                    risk *= 0.9d;
                }
                if (holder.rival()) {
                    risk *= 1.1d;
                }
                if (holder.war()) {
                    risk *= 1.2d;
                }
                risk *= Influence.risk(influenceValue);
                risk *= 1.0d - Math.min(0.30d, 0.24d * operations);
                risk *= 1.0d - 0.2d * Math.min(1.0d, manager);
                if (enterprise.has(Upgrade.SECURITE)) {
                    risk *= 0.75d;
                }
                if (sector == Sector.CONTREBANDE) {
                    risk *= 1.0d - Math.min(0.30d, 0.05d * transport);
                }
                if (syndicate != null && status(syndicate) == Holdings.Status.GUERRE) {
                    risk += 15.0d;
                }
                risk += Math.min(15.0d, holdings.dirty / 200_000.0d * 5.0d);
                risk = Math.max(0.0d, Math.min(95.0d, risk));
            }
            return new Factors(output, cost, risk, influenceValue, notes);
        }

        /** Ce que les transports apportent aux usines et aux commerces : 3 % par niveau, 25 % au plus. */
        double logistics() {
            return Math.min(0.25d, 0.03d * transport);
        }
    }

    // -- La simulation d'une heure ----------------------------------------------------------------

    /** L'etat qui bouge pendant un versement : les stocks, l'argent sale, ce qui a ete gagne et perdu. */
    static final class Sim {
        final Map<String, Double> stock = new HashMap<>();
        double dirty;
        double clean;
        double dirtyEarned;
        double costs;
        double laundered;
        double fees;
        double seized;
        double supplies;
        double sold;
        double lost;
        double ironUsed;
        int raids;
        final Map<String, Double> earned = new HashMap<>();
        final Map<String, Long> closures = new HashMap<>();
        final Set<String> raided = new HashSet<>();
        final List<Event> events = new ArrayList<>();

        Sim(Holdings holdings) {
            for (Map.Entry<String, Long> entry : holdings.stock.entrySet()) {
                stock.put(entry.getKey(), (double) entry.getValue());
            }
            dirty = holdings.dirty;
        }

        double stock(String key) {
            return stock.getOrDefault(key, 0.0d);
        }

        void add(String key, double amount) {
            stock.put(key, Math.max(0.0d, stock(key) + amount));
        }

        void earn(Enterprise enterprise, double amount) {
            earned.merge(enterprise.id(), amount, Double::sum);
        }

        boolean closed(Enterprise enterprise, long time) {
            Long until = closures.get(enterprise.id());
            return enterprise.closed(time) || until != null && until > time;
        }

        void close(Enterprise enterprise, long until) {
            closures.merge(enterprise.id(), until, Math::max);
        }
    }

    static final Line[] BY_STREET = sortedByStreet();

    private static Line[] sortedByStreet() {
        Line[] lines = Line.values().clone();
        java.util.Arrays.sort(lines, (a, b) -> Long.compare(b.street(), a.street()));
        return lines;
    }

    /** Une heure qui se termine a {@code time}. {@code live} : la police passe, sinon c'est un apercu. */
    static Hour hour(World world, Sim sim, long time, boolean live, RandomSource random) {
        Holdings holdings = world.holdings;
        List<Enterprise> open = new ArrayList<>();
        List<Enterprise> running = new ArrayList<>();
        for (Enterprise enterprise : holdings.enterprises) {
            // une entreprise ouverte pendant l'heure ne compte qu'a l'heure suivante
            if (enterprise.founded() > time - HOUR) {
                continue;
            }
            running.add(enterprise);
            if (!sim.closed(enterprise, time)) {
                open.add(enterprise);
            }
        }
        double clean = 0.0d;
        double dirty = 0.0d;
        Map<Region, Double> regionDirty = new EnumMap<>(Region.class);
        double startSeized = sim.seized;
        double startLaundered = sim.laundered;

        // 1. ce qui pousse et ce qui se monte
        for (Enterprise enterprise : open) {
            Factors factors = world.factors(enterprise);
            Sector sector = enterprise.kind();
            if (sector.role() == Sector.Role.GROW) {
                Line line = enterprise.drug();
                if (line != null && line.grows()) {
                    sim.add(line.rawKey(), line.yield() * factors.output());
                }
            } else if (sector.role() == Sector.Role.ARMS) {
                double crates = sector.base() * factors.output();
                double fromIron = Math.max(0.0d, Math.min(crates,
                        (world.iron - sim.ironUsed) / Enterprises.IRON_PER_CRATE));
                sim.ironUsed += fromIron * Enterprises.IRON_PER_CRATE;
                double rest = crates - fromIron;
                double bought = Math.max(0.0d, Math.min(rest, sim.dirty / Enterprises.CRATE_PARTS));
                sim.dirty -= bought * Enterprises.CRATE_PARTS;
                sim.supplies += bought * Enterprises.CRATE_PARTS;
                sim.add(Enterprises.CRATES, fromIron + bought);
            }
        }

        // 2. les laboratoires
        for (Enterprise enterprise : open) {
            if (enterprise.kind().role() != Sector.Role.LAB) {
                continue;
            }
            Line line = enterprise.drug();
            if (line == null) {
                continue;
            }
            double capacity = enterprise.kind().base() * world.factors(enterprise).output();
            double used = Math.min(capacity, sim.stock(line.rawKey()));
            sim.add(line.rawKey(), -used);
            double missing = capacity - used;
            double bought = 0.0d;
            long price = Enterprises.rawPrice(world, line);
            if (missing > 0.0d && price > 0L) {
                bought = Math.max(0.0d, Math.min(missing, sim.dirty / price));
                sim.dirty -= bought * price;
                sim.supplies += bought * price;
            }
            sim.add(line.productKey(), used + bought);
        }

        // 3. ce qui rapporte directement
        double facadeLeft = 0.0d;
        List<double[]> facades = new ArrayList<>();
        double casinoCapacity = 0.0d;
        boolean valcorte = world.deal(Syndicate.VALCORTE);
        for (Enterprise enterprise : open) {
            Factors factors = world.factors(enterprise);
            Sector sector = enterprise.kind();
            double amount = 0.0d;
            switch (sector.role()) {
                case REVENUE -> {
                    amount = sector.base() * factors.output();
                    clean += amount;
                }
                case SMUGGLE, RACKET, FENCE -> {
                    amount = sector.base() * factors.output();
                    dirty += amount;
                }
                case CASINO -> {
                    amount = sector.base() * factors.output();
                    dirty += amount;
                    casinoCapacity += CASINO_LAUNDER * factors.output() * (valcorte ? 1.25d : 1.0d);
                }
                case LAUNDER -> {
                    amount = FACADE_REVENUE * factors.output();
                    clean += amount;
                    double capacity = sector.base() * factors.output();
                    facades.add(new double[] {capacity, Enterprises.facadeFee(enterprise)});
                    facadeLeft += capacity;
                }
                default -> {
                }
            }
            if (amount > 0.0d) {
                sim.earn(enterprise, amount);
                if (!sector.legal()) {
                    Region region = world.region(enterprise.country());
                    if (region != null && sector.role() != Sector.Role.LAUNDER) {
                        regionDirty.merge(region, amount, Double::sum);
                    }
                }
            }
        }

        // 4. les ventes
        for (Enterprise enterprise : open) {
            Sector sector = enterprise.kind();
            if (sector.role() == Sector.Role.SELL) {
                double capacity = sector.base() * world.factors(enterprise).output();
                double revenue = 0.0d;
                for (Line line : BY_STREET) {
                    if (capacity <= 0.0d) {
                        break;
                    }
                    double units = Math.min(capacity, sim.stock(line.productKey()));
                    if (units <= 0.0d) {
                        continue;
                    }
                    sim.add(line.productKey(), -units);
                    revenue += units * line.street();
                    sim.sold += units;
                    capacity -= units;
                }
                if (capacity > 0.0d && world.deal(Syndicate.RECIFS)) {
                    Line best = Line.HEROINE;
                    long price = Enterprises.wholesale(world, best);
                    double units = Math.max(0.0d, Math.min(capacity, sim.dirty / price));
                    sim.dirty -= units * price;
                    sim.supplies += units * price;
                    revenue += units * best.street();
                    sim.sold += units;
                }
                if (revenue > 0.0d) {
                    dirty += revenue;
                    sim.earn(enterprise, revenue);
                    Region region = world.region(enterprise.country());
                    if (region != null) {
                        regionDirty.merge(region, revenue, Double::sum);
                    }
                }
            } else if (sector.role() == Sector.Role.ARMS_SELL) {
                double capacity = sector.base() * world.factors(enterprise).output();
                double street = Enterprises.CRATE_STREET * (world.atWar ? 1.4d : 1.0d);
                double units = Math.min(capacity, sim.stock(Enterprises.CRATES));
                sim.add(Enterprises.CRATES, -units);
                double revenue = units * street;
                double missing = capacity - units;
                if (missing > 0.0d && world.deal(Syndicate.LOTUS)) {
                    long price = Math.round(Enterprises.CRATE_SUPPLY * (world.allied(Syndicate.LOTUS) ? 0.85d : 1.0d));
                    double bought = Math.max(0.0d, Math.min(missing, sim.dirty / price));
                    sim.dirty -= bought * price;
                    sim.supplies += bought * price;
                    revenue += bought * street;
                    units += bought;
                }
                sim.sold += units;
                if (revenue > 0.0d) {
                    dirty += revenue;
                    sim.earn(enterprise, revenue);
                    Region region = world.region(enterprise.country());
                    if (region != null) {
                        regionDirty.merge(region, revenue, Double::sum);
                    }
                }
            }
        }

        // 5. la place dans les stocks : le surplus se revend a un fournisseur, ou se perd
        for (String key : List.copyOf(sim.stock.keySet())) {
            double excess = sim.stock(key) - world.capacity;
            if (excess <= 0.0d) {
                continue;
            }
            sim.add(key, -excess);
            double unit = Enterprises.buyback(world, key);
            if (unit > 0.0d) {
                dirty += excess * unit;
                sim.sold += excess;
            } else {
                sim.lost += excess;
            }
        }

        // 6. la part des allies
        for (Map.Entry<Region, Double> entry : regionDirty.entrySet()) {
            Syndicate syndicate = Syndicate.in(entry.getKey());
            if (syndicate != null && world.allied(syndicate)) {
                dirty -= entry.getValue() * 0.05d;
            }
        }

        // 7. les frais
        double costs = 0.0d;
        for (Enterprise enterprise : running) {
            double cost = world.factors(enterprise).cost();
            costs += sim.closed(enterprise, time) ? cost * 0.5d : cost;
        }

        // 8. le blanchiment
        sim.dirty += Math.max(0.0d, dirty);
        facades.sort((a, b) -> Double.compare(a[1], b[1]));
        for (double[] facade : facades) {
            double amount = Math.min(facade[0], sim.dirty);
            if (amount <= 0.0d) {
                break;
            }
            sim.dirty -= amount;
            sim.laundered += amount * (1.0d - facade[1]);
            sim.fees += amount * facade[1];
        }
        if (casinoCapacity > 0.0d && sim.dirty > 0.0d) {
            double fee = valcorte ? CASINO_FEE - 0.05d : CASINO_FEE;
            double amount = Math.min(casinoCapacity, sim.dirty);
            sim.dirty -= amount;
            sim.laundered += amount * (1.0d - fee);
            sim.fees += amount * fee;
        }

        // 9. la police
        if (live) {
            for (Enterprise enterprise : open) {
                if (enterprise.legal() || sim.raids >= RAIDS_PER_BATCH || sim.raided.contains(enterprise.id())) {
                    continue;
                }
                double risk = world.factors(enterprise).risk();
                double chance = RAID_SCALE * Math.pow(risk, 2.5d);
                if (random.nextDouble() < chance) {
                    raid(world, sim, enterprise, time);
                }
            }
        }

        sim.clean += clean;
        sim.dirtyEarned += Math.max(0.0d, dirty);
        sim.costs += costs;
        double gross = clean + Math.max(0.0d, dirty);
        return new Hour(gross, costs, clean, Math.max(0.0d, dirty), sim.laundered - startLaundered,
                sim.seized - startSeized);
    }

    /** Une descente de police : l'entreprise ferme, une part de ses stocks et de l'argent sale est saisie. */
    static void raid(World world, Sim sim, Enterprise enterprise, long time) {
        sim.raids++;
        sim.raided.add(enterprise.id());
        sim.close(enterprise, time + CLOSED_RAID);
        double share = world.warehouse ? 0.25d : 0.5d;
        double units = 0.0d;
        Sector sector = enterprise.kind();
        Line line = enterprise.drug();
        switch (sector.role()) {
            case GROW -> {
                if (line != null) {
                    units = sim.stock(line.rawKey()) * share;
                    sim.add(line.rawKey(), -units);
                }
            }
            case LAB -> {
                if (line != null) {
                    units = sim.stock(line.productKey()) * share;
                    sim.add(line.productKey(), -units);
                }
            }
            case SELL -> {
                for (Line any : Line.values()) {
                    double taken = sim.stock(any.productKey()) * share * 0.6d;
                    sim.add(any.productKey(), -taken);
                    units += taken;
                }
            }
            case ARMS, ARMS_SELL -> {
                units = sim.stock(Enterprises.CRATES) * share;
                sim.add(Enterprises.CRATES, -units);
            }
            default -> {
            }
        }
        double money = Math.min(sim.dirty * DIRTY_SEIZURE, DIRTY_SEIZURE_CAP);
        sim.dirty -= money;
        sim.seized += money;
        String where = Enterprises.place(enterprise);
        StringBuilder text = new StringBuilder("Descente de police a ").append(enterprise.name()).append(where)
                .append(" : fermee 24 h");
        if (units >= 1.0d) {
            text.append(", ").append(MoneyFormat.exact(Math.round(units))).append(" unite(s) saisie(s)");
        }
        if (money >= 1.0d) {
            text.append(", ").append(MoneyFormat.exact(Math.round(money))).append(" $ d'argent sale saisis");
        }
        sim.events.add(new Event(text.toString(), 0xFFEF3826, "Descente de police", enterprise.name()
                + " fermee 24 h"));
    }

    // -- Le versement -----------------------------------------------------------------------------

    /**
     * Fait tourner les entreprises depuis la derniere heure payee, heure pleine par heure pleine,
     * une semaine au plus. {@code login} : il revient, le message dit ce qui s'est passe.
     */
    static void run(ServerPlayer player, boolean login) {
        if (!FactionApi.has(player)) {
            return;
        }
        Holdings holdings = Enterprises.of(player);
        if (holdings.enterprises.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (holdings.paidUntil <= 0L) {
            holdings.paidUntil = now;
            return;
        }
        long elapsed = now - holdings.paidUntil;
        if (elapsed < HOUR) {
            return;
        }
        long due = elapsed / HOUR;
        int hours = (int) Math.min(due, Enterprises.MAX_HOURS);
        long start = due > Enterprises.MAX_HOURS ? now - elapsed % HOUR - hours * HOUR : holdings.paidUntil;
        World world = new World(player, holdings, now);
        Sim sim = new Sim(holdings);
        RandomSource random = player.getRandom();
        // les journees des organisations suivent les heures rattrapees : une semaine au plus, elles aussi
        holdings.dayTick = holdings.dayTick <= 0L ? start : Math.max(holdings.dayTick, start - DAY);
        for (int index = 0; index < hours; index++) {
            long time = start + (index + 1L) * HOUR;
            Hour hour = hour(world, sim, time, true, random);
            holdings.hour(Math.round(hour.gross()), Math.round(hour.costs()));
            holdings.book(day(time), Math.round(hour.clean()), Math.round(hour.dirty()), Math.round(hour.costs()),
                    Math.round(hour.laundered()), Math.round(hour.seized()));
            while (time - holdings.dayTick >= DAY) {
                holdings.dayTick += DAY;
                Syndicates.daily(world, sim, holdings.dayTick, random);
            }
        }
        holdings.paidUntil = start + hours * HOUR;
        settle(player, world, sim, hours, login, now);
    }

    /** Une journee de comptes, a l'heure de Paris. */
    static long day(long time) {
        return Instant.ofEpochMilli(time).atZone(FactionWeekly.zone()).toLocalDate().toEpochDay();
    }

    /** Reporte la simulation sur les affaires : la banque, l'argent sale, les stocks, les fermetures, le fer. */
    static void settle(ServerPlayer player, World world, Sim sim, int hours, boolean login, long now) {
        Holdings holdings = world.holdings;
        long clean = Math.round(sim.clean);
        long laundered = Math.round(sim.laundered);
        long costs = Math.round(sim.costs);
        long net = clean + laundered - costs;
        boolean strike = false;
        if (net >= 0L) {
            FactionApi.creditBank(player, net, "Entreprises (" + hours + " h)");
        } else {
            long deficit = -net;
            long bank = FactionApi.data(player).bank();
            long fromBank = Math.min(bank, deficit);
            if (fromBank > 0L) {
                FactionApi.seize(player, fromBank, "Frais des entreprises (" + hours + " h)");
            }
            long rest = deficit - fromBank;
            long fromDirty = (long) Math.min(rest, Math.floor(sim.dirty));
            sim.dirty -= fromDirty;
            rest -= fromDirty;
            if (rest > 0L) {
                strike = true;
            }
        }
        holdings.dirty = Math.max(0L, Math.round(sim.dirty));
        holdings.stock.clear();
        for (Map.Entry<String, Double> entry : sim.stock.entrySet()) {
            long units = Math.round(entry.getValue());
            if (units > 0L) {
                holdings.stock.put(entry.getKey(), units);
            }
        }
        for (Enterprise enterprise : List.copyOf(holdings.enterprises)) {
            Enterprise next = enterprise;
            Long until = sim.closures.get(enterprise.id());
            if (until != null) {
                next = next.closedUntil(until);
            }
            if (strike) {
                next = next.closedUntil(now + CLOSED_STRIKE);
            }
            Double earned = sim.earned.get(enterprise.id());
            if (earned != null) {
                next = next.earning(Math.round(earned));
            }
            if (next != enterprise) {
                holdings.replace(next);
            }
        }
        int iron = (int) Math.round(sim.ironUsed);
        if (iron > 0) {
            Enterprises.consumeIron(player, iron);
        }
        for (Event event : sim.events) {
            holdings.log(now, event.text());
        }
        if (strike) {
            String text = "Faute de quoi payer les frais, tes employes cessent le travail 6 h";
            holdings.log(now, text);
            sim.events.add(new Event(text, 0xFFEF3826, "Greve", "Frais impayes : 6 h d'arret"));
        }
        holdings.report = new Holdings.Report(now, hours, clean, Math.round(sim.dirtyEarned), laundered, costs,
                sim.raids, Math.round(sim.sold), Math.round(sim.lost));
        DoHy.LOGGER.info("Entreprises de {} : {} h, {} $ propres, {} $ sales, {} $ blanchis, {} $ de frais, {} descente(s)",
                player.getGameProfile().name(), hours, clean, Math.round(sim.dirtyEarned), laundered, costs,
                sim.raids);
        tell(player, holdings, hours, login, net);
        for (Event event : sim.events) {
            player.sendSystemMessage(Component.literal("[Entreprises] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(event.text() + ".").withStyle(chatColor(event.color()))));
            if (!event.title().isEmpty()) {
                Toasts.send(player, new ItemStack(Items.IRON_BARS), "ENTREPRISES", event.title(), event.line(),
                        event.color(), "enterprise_event");
            }
        }
    }

    private static void tell(ServerPlayer player, Holdings holdings, int hours, boolean login, long net) {
        Holdings.Report report = holdings.report;
        if (login) {
            StringBuilder text = new StringBuilder("Pendant ton absence (").append(hours).append(" h), tes entreprises ont ")
                    .append(net >= 0L ? "verse " : "coute ").append(MoneyFormat.exact(Math.abs(net)))
                    .append(" $ a la banque nationale");
            if (report.laundered() > 0L) {
                text.append(" (dont ").append(MoneyFormat.exact(report.laundered())).append(" $ blanchis)");
            }
            if (report.dirty() > 0L) {
                text.append(", et rapporte ").append(MoneyFormat.exact(report.dirty())).append(" $ d'argent sale");
            }
            text.append('.');
            player.sendSystemMessage(Component.literal("[Entreprises] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(text.toString()).withStyle(net >= 0L ? ChatFormatting.GREEN
                            : ChatFormatting.YELLOW)));
            return;
        }
        String line = (net >= 0L ? "+" : "-") + MoneyFormat.exact(Math.abs(net)) + " $ a la banque"
                + (report.dirty() > 0L ? " · sale +" + MoneyFormat.exact(report.dirty()) + " $" : "");
        Toasts.send(player, new ItemStack(Items.GOLD_INGOT), "ENTREPRISES", "Versement de l'heure", line,
                net >= 0L ? 0xFF22E558 : 0xFFFAC400, "enterprise_hour");
    }

    // -- L'apercu des menus -----------------------------------------------------------------------

    /** Ce que rapporterait la prochaine heure, sans police, avec les stocks d'aujourd'hui. */
    record Preview(double clean, double dirty, double costs, double laundered, double fees, double supplies,
                   double sold, double lost, Map<String, Double> earned) {
        static final Preview NONE = new Preview(0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, Map.of());

        double net() {
            return clean + laundered - costs;
        }
    }

    static Preview preview(ServerPlayer player) {
        Holdings holdings = Enterprises.of(player);
        if (!FactionApi.has(player) || holdings.enterprises.isEmpty()) {
            return Preview.NONE;
        }
        long now = System.currentTimeMillis();
        World world = new World(player, holdings, now);
        Sim sim = new Sim(holdings);
        hour(world, sim, now + HOUR, false, player.getRandom());
        return new Preview(sim.clean, sim.dirtyEarned, sim.costs, sim.laundered, sim.fees, sim.supplies, sim.sold,
                sim.lost, Map.copyOf(sim.earned));
    }

    // -- Les petits outils ------------------------------------------------------------------------

    /** « x2,60 » */
    static String times(double value) {
        return String.format(Locale.FRANCE, "x%.2f", value);
    }

    /** « +20 % », « -15 % ». */
    static String percent(double factor) {
        long value = Math.round((factor - 1.0d) * 100.0d);
        return (value >= 0 ? "+" : "") + value + " %";
    }

    /** Une couleur de chat depuis une couleur ARGB. */
    static net.minecraft.network.chat.Style chatColor(int argb) {
        return net.minecraft.network.chat.Style.EMPTY.withColor(argb & 0xFFFFFF);
    }
}
